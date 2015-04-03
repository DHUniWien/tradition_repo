package net.stemmaweb.rest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.stream.XMLStreamException;

import net.stemmaweb.model.DuplicateModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.model.TextInfoModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.services.Neo4JToDotParser;
import net.stemmaweb.services.Neo4JToGraphMLParser;

import org.eclipse.persistence.exceptions.DatabaseException;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.graphdb.traversal.Traverser;
import org.neo4j.graphdb.traversal.Uniqueness;

import Exceptions.DataBaseException;

import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;

/**
 * 
 * @author ramona, sevi, joel
 *
 **/

@Path("/tradition")
public class Tradition implements IResource {

	GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();

	/**
	 * 
	 * @param textInfo
	 *            in JSON Format
	 * @return OK on success or an ERROR as JSON
	 */
	@POST
	@Path("{textId}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response changeOwnerOfATradition(TextInfoModel textInfo, @PathParam("textId") String textId) {

		User user = new User();
		if (!user.checkUserExists(textInfo.getOwnerId())) {
			return Response.status(Response.Status.NOT_FOUND).entity("Error: A user with this id does not exist")
					.build();
		}

		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);

		ExecutionEngine engine = new ExecutionEngine(db);
		try (Transaction tx = db.beginTx()) {
			ExecutionResult result = engine.execute("match (textId:TRADITION {id:'" + textId + "'}) return textId");
			Iterator<Node> nodes = result.columnAs("textId");

			if (nodes.hasNext()) {
				// Remove the old ownership
				String removeRelationQuery = "MATCH (tradition:TRADITION {id: '" + textId + "'}) "
						+ "MATCH tradition<-[r:NORMAL]-(:USER) DELETE r";
				result = engine.execute(removeRelationQuery);
				System.out.println(result.dumpToString());

				// Add the new ownership
				String createNewRelationQuery = "MATCH(user:USER {id:'" + textInfo.getOwnerId() + "'}) "
						+ "MATCH(tradition: TRADITION {id:'" + textId + "'}) " + "SET tradition.name = '"
						+ textInfo.getName() + "' " + "SET tradition.public = '" + textInfo.getIsPublic() + "' "
						+ "CREATE (tradition)<-[r:NORMAL]-(user) RETURN r, tradition";
				result = engine.execute(createNewRelationQuery);
				System.out.println(result.dumpToString());

			} else {
				// Tradition no found
				return Response.status(Response.Status.NOT_FOUND).entity("Tradition not found").build();
			}

			tx.success();
		} catch (Exception e) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
		} finally {
			db.shutdown();
		}
		return Response.status(Response.Status.OK).entity(textInfo).build();
	}

	private Traverser getReading(final Node reading, GraphDatabaseService db) {
		TraversalDescription td = db.traversalDescription().breadthFirst()
				.relationships(ERelations.NORMAL, Direction.OUTGOING).evaluator(Evaluators.excludeStartPosition());
		return td.traverse(reading);
	}

	/**
	 * Returns a single reading in a specific tradition.
	 * 
	 * @param tradId
	 * @param readId
	 * @return
	 */
	@GET
	@Path("reading/{tradId}/{readId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getReading(@PathParam("tradId") String tradId, @PathParam("readId") long readId) {
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);

		ReadingModel reading = null;

		Node startNode = null;
		try {
			DatabaseService service = new DatabaseService(db);
			startNode = service.getStartNode(tradId);
		} catch (DataBaseException e) {
			return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
		}

		try (Transaction tx = db.beginTx()) {
			if (startNode.getId()==readId) {
				reading = Reading.readingModelFromNode(startNode);
			} else {
				Traverser traverser = getReading(startNode, db);
				for (org.neo4j.graphdb.Path path : traverser) {
					long id = path.endNode().getId();
					if (id==readId) {
						reading = Reading.readingModelFromNode(path.endNode());
						break;
					}
				}
			}
			tx.success();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			db.shutdown();
		}

		if (reading == null)
			return Response.status(Status.NOT_FOUND).entity("no reading with this id found").build();

		return Response.ok(reading).build();
	}
	
	@GET
	@Path("all")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllTraditions()
	{
		List<TraditionModel> traditionList = new ArrayList<TraditionModel>();
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		ExecutionEngine engine = new ExecutionEngine(db);
		
		try (Transaction tx = db.beginTx()) 
		{
			ExecutionResult result = engine.execute("match (u:USER)-[:NORMAL]->(n:TRADITION) return n");
			Iterator<Node> traditions = result.columnAs("n");
			while(traditions.hasNext())
			{
				Node trad = traditions.next();
				TraditionModel tradModel = new TraditionModel();
				if(trad.hasProperty("id"))
					tradModel.setId(trad.getProperty("id").toString());
				if(trad.hasProperty("dg1"))	
					tradModel.setName(trad.getProperty("dg1").toString());
				traditionList.add(tradModel);
			}
			tx.success();
		} catch (Exception e) {
			e.printStackTrace();
			db.shutdown();
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		} finally {
			db.shutdown();
		}
		
		return Response.ok().entity(traditionList).build();
	}

	/**
	 * Duplicates a reading in a specific tradition. Opposite of merge
	 * 
	 * @param tradId
	 * @param duplicateModel
	 * @return
	 */
	@POST
	@Path("duplicate/{tradId}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response duplicateReading(@PathParam("tradId") String tradId,
			DuplicateModel duplicateModel) {
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);

		Node startNode = null;
		try {
			DatabaseService service = new DatabaseService(db);
			startNode = service.getStartNode(tradId);
		} catch (DataBaseException e) {
			return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
		}

		try (Transaction tx = db.beginTx()) {
			boolean foundReadings = false;
			List<Long> readings = duplicateModel.getReadings();
			Traverser traverser = getReading(startNode, db);
			for (org.neo4j.graphdb.Path path : traverser) {
				long id = path.endNode().getId();
				if (readings.contains(id)) {
					duplicateReading(duplicateModel.getWitnesses(), db, path);

					readings.remove(id);
					if (readings.isEmpty()) {
						foundReadings = true;
						break;
					}
				}
			}
			if (!foundReadings)
				return Response.status(Status.NOT_FOUND).entity("no reading with this ids found: " + readings).build();

			tx.success();
		} catch (Exception e) {
			return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
		} finally {
			db.shutdown();
		}
		return Response.ok("Successfully duplicated readings").build();
	}

	private void duplicateReading(List<String> witnesses,
			GraphDatabaseService db, org.neo4j.graphdb.Path path) throws DatabaseException {
		Node addedReading = db.createNode();
		Node originalReading = path.endNode();

		System.out.println(addedReading.getId());

		// copy reading
		Reading.copyReadingProperties(originalReading, addedReading);

		// test if there are witnesses to be duplicated for which no witnesses
		// in the readings relationships exist
		List<String> allWitnesses = new LinkedList<String>();
		String[] currentWitnesses;
		Iterable<Relationship> rels = originalReading.getRelationships(ERelations.NORMAL);
		for (Relationship relationship : rels) {
			currentWitnesses = (String[]) relationship.getProperty("lexemes");
			for (String currentWitness : currentWitnesses)
				if (!allWitnesses.contains(currentWitness))
					allWitnesses.add(currentWitness);

		}
//		for (String newWitness : witnesses)
//			if (!allWitnesses.contains(newWitness))
//				throw new DataBaseException("The node to be duplicated has to be part of the new witnesses");

		// add witnesses to relationships
		// Incoming
		rels = originalReading.getRelationships(ERelations.NORMAL, Direction.INCOMING);
		for (Relationship originalRelationship : rels)
			handleRelationships(witnesses, originalRelationship, originalRelationship.getStartNode(), addedReading);
		// Outgoing
		rels = originalReading.getRelationships(ERelations.NORMAL, Direction.OUTGOING);
		for (Relationship originalRelationship : rels)
			handleRelationships(witnesses, originalRelationship, addedReading, originalRelationship.getEndNode());
	}

	private void handleRelationships(List<String> newWitnesses, Relationship originalRelationship, Node originNode,
			Node targetNode) throws DataBaseException {
		List<String> oldWitnesses = Arrays.asList((String[]) originalRelationship.getProperty("lexemes"));

		for (String oldWitness : oldWitnesses)
			if (newWitnesses.contains(oldWitness))
				oldWitnesses.remove(oldWitness);

		// if (oldWitnesses.isEmpty())
		// throw new
		// DataBaseException("The node to be duplicated has to have at least one witness.");

		originalRelationship.setProperty("lexemes", oldWitnesses.toArray(new String[oldWitnesses.size()]));
		Relationship addedRelationship = originNode.createRelationshipTo(targetNode, ERelations.NORMAL);
		addedRelationship.setProperty("lexemes", newWitnesses.toArray(new String[newWitnesses.size()]));
	}

	/**
	 * Merges two readings into one single reading in a specific tradition.
	 * Opposite of duplicate
	 * 
	 * @param tradId
	 * @param firstReadId
	 * @param secondReadId
	 * @return
	 */
	@POST
	@Path("merge/{tradId}/{firstReadId}/{secondReadId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response mergeReadings(@PathParam("tradId") String tradId, @PathParam("firstReadId") long firstReadId,
			@PathParam("secondReadId") long secondReadId) {
		Node firstReading = null;
		Node secondReading = null;

		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);

		Node startNode = null;
		try {
			DatabaseService service = new DatabaseService(db);
			startNode = service.getStartNode(tradId);
		} catch (DataBaseException e) {
			return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
		}

		try (Transaction tx = db.beginTx()) {
			boolean foundReadings = false;
			Traverser traverser = getReading(startNode, db);
			for (org.neo4j.graphdb.Path path : traverser) {
				long id = path.endNode().getId();
				if (id==firstReadId)
					firstReading = path.endNode();
				if (id==secondReadId)
					secondReading = path.endNode();
				if (firstReading != null && secondReading != null) {
					foundReadings = true;
					break;
				}
			}
			if (!foundReadings)
				return Response.status(Status.NOT_FOUND).entity("no readings with this ids found").build();

			if (!firstReading.getProperty("dn15").toString()
					.equalsIgnoreCase(secondReading.getProperty("dn15").toString()))
				return Response.status(Status.INTERNAL_SERVER_ERROR)
						.entity("Readings to be merged do not contain the same text").build();

			// merging of readings happens here
			copyRelationshipProperties(firstReading, secondReading, Direction.INCOMING);
			copyRelationshipProperties(firstReading, secondReading, Direction.OUTGOING);
			secondReading.delete();

			tx.success();
		} catch (Exception e) {
			return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
		} finally {
			db.shutdown();
		}

		return Response.ok("Successfully merged readings").build();
	}

	private void copyRelationshipProperties(Node firstReading, Node secondReading, Direction direction) {
		Relationship firstRel = firstReading.getSingleRelationship(ERelations.NORMAL, direction);
		Relationship secondRel = secondReading.getSingleRelationship(ERelations.NORMAL, direction);
		String[] firstWitnesses = (String[]) firstRel.getProperty("lexemes");
		String[] secondWitnesses = (String[]) secondRel.getProperty("lexemes");
		String[] combinedWitnesses = new String[firstWitnesses.length + secondWitnesses.length];
		for(int i = 0; i < firstWitnesses.length; i++)
			combinedWitnesses[i] = firstWitnesses[i];
		for(int i = 0; i < secondWitnesses.length; i++)
			combinedWitnesses[firstWitnesses.length + i] = secondWitnesses[i];
		firstRel.setProperty("lexemes", combinedWitnesses);
		secondRel.delete();
	}

	/**
	 * Splits up a single reading into several ones in a specific tradition.
	 * Opposite of compress
	 * 
	 * @param tradId
	 * @param readId
	 * @return
	 */
	@POST
	@Path("split/{tradId}/{readId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response splitReading(@PathParam("tradId") String tradId, @PathParam("readId") long readId) {

		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);

		Node startNode = null;
		try {
			DatabaseService service = new DatabaseService(db);
			startNode = service.getStartNode(tradId);
		} catch (DataBaseException e) {
			System.out.println(e.getMessage());
			System.out.println("databaseexception");
			return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
		}

		try (Transaction tx = db.beginTx()) {
			boolean foundReading = false;
			Traverser traverser = getReading(startNode, db);
			for (org.neo4j.graphdb.Path path : traverser) {
				long id = path.endNode().getId();
				if (id==readId) {
					// splitting of reading happens here
					Node originalReading = path.endNode();

					String oldText = originalReading.getProperty("dn15").toString();
					String[] splittedWords = oldText.split("\\s+");
					if (splittedWords.length < 2)
						return Response.status(Status.INTERNAL_SERVER_ERROR)
								.entity("A reading to be splitted has to contain at least 2 words").build();
					originalReading.setProperty("dn15", splittedWords[0]);

					Node previousReading = originalReading;
					Node newReading = null;

					for (int i = 1; i < splittedWords.length; i++) {
						newReading = db.createNode();

						Iterable<Relationship> rels = previousReading.getRelationships(ERelations.NORMAL,
								Direction.OUTGOING);
						for (Relationship relationship : rels) {
							newReading.createRelationshipTo(relationship.getEndNode(), ERelations.NORMAL);
							relationship.delete();
						}

						Reading.copyReadingProperties(previousReading, newReading);
						newReading.setProperty("dn15", splittedWords[i]);
						Long previousRank = (Long) previousReading.getProperty("dn14");
						newReading.setProperty("dn14", previousRank + 1);

						previousReading.createRelationshipTo(newReading, ERelations.NORMAL);

						previousReading = newReading;
					}

					foundReading = true;
					break;
				}
			}
			if (!foundReading) {
				System.out.println("if");
				return Response.status(Status.NOT_FOUND).entity("no reading with this id found").build();
			}
			tx.success();
		} catch (Exception e) {
			System.out.println("catch");
			return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
		} finally {
			db.shutdown();
		}
		return Response.ok("Successfully split up reading").build();
	}

	@GET
	@Path("witness/{tradId}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllWitnesses(@PathParam("tradId") String tradId) {

		ArrayList<WitnessModel> witlist = new ArrayList<WitnessModel>();

		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);

		ExecutionEngine engine = new ExecutionEngine(db);

		try (Transaction tx = db.beginTx()) {
			Node traditionNode = null;
			Iterable<Relationship> relationships = null;
			Node startNode = null;

			try {
				traditionNode = getTraditionNode(tradId, engine);
				relationships = getRelationships(traditionNode);
				// startNode = getStartNode(relationships);
			} catch (DataBaseException e) {
				return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
			}

			startNode = null;
			try {
				DatabaseService service = new DatabaseService(db);
				startNode = service.getStartNode(tradId);
			} catch (DataBaseException e) {
				return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
			}

			relationships = startNode.getRelationships(Direction.OUTGOING);

			if (relationships == null)
				return Response.status(Status.NOT_FOUND).entity("start node not found").build();

			for (Relationship rel : relationships) {
				for (String id : ((String[]) rel.getProperty("lexemes"))) {
					WitnessModel witM = new WitnessModel();
					witM.setId(id);

					witlist.add(witM);
				}
			}

			tx.success();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			db.shutdown();
		}

		return Response.ok(witlist).build();
	}

	@GET
	@Path("relation/{tradId}/relationships")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllRelationships(@PathParam("tradId") String tradId) {

		ArrayList<RelationshipModel> relList = new ArrayList<RelationshipModel>();

		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);

		ExecutionEngine engine = new ExecutionEngine(db);

		try (Transaction tx = db.beginTx()) {

			ExecutionResult result = engine.execute("match (n:TRADITION {id:'"+ tradId +"'})-[:NORMAL]->(s:WORD) return s");
			Iterator<Node> nodes = result.columnAs("s");
			Node startNode = nodes.next();
			
			for (Node node : db.traversalDescription().depthFirst()
					.relationships(ERelations.NORMAL,Direction.OUTGOING)
					.uniqueness(Uniqueness.NODE_GLOBAL)
					.traverse(startNode).nodes()) {
				
				Iterable<Relationship> rels = node.getRelationships(ERelations.RELATIONSHIP,Direction.OUTGOING);
				for(Relationship rel : rels)
				{
					RelationshipModel relMod = new RelationshipModel();
	
					if (rel.getStartNode() != null)
						relMod.setSource(String.valueOf(rel.getStartNode().getId()));
					if (rel.getEndNode() != null)
						relMod.setTarget(String.valueOf(rel.getEndNode().getId()));
					relMod.setId(String.valueOf(rel.getId()));
					if (rel.hasProperty("de0"))
						relMod.setDe0(rel.getProperty("de0").toString());
					if (rel.hasProperty("de1"))
						relMod.setDe1(rel.getProperty("de1").toString());
					if (rel.hasProperty("de2"))
						relMod.setDe2(rel.getProperty("de2").toString());
					if (rel.hasProperty("de3"))
						relMod.setDe3(rel.getProperty("de3").toString());
					if (rel.hasProperty("de4"))
						relMod.setDe4(rel.getProperty("de4").toString());
					if (rel.hasProperty("de5"))
						relMod.setDe5(rel.getProperty("de5").toString());
					if (rel.hasProperty("de6"))
						relMod.setDe6(rel.getProperty("de6").toString());
					if (rel.hasProperty("de7"))
						relMod.setDe7(rel.getProperty("de7").toString());
					if (rel.hasProperty("de8"))
						relMod.setDe8(rel.getProperty("de8").toString());
					if (rel.hasProperty("de9"))
						relMod.setDe9(rel.getProperty("de9").toString());
					if (rel.hasProperty("de10"))
						relMod.setDe10(rel.getProperty("de10").toString());
					if (rel.hasProperty("de11"))
						relMod.setDe11(rel.getProperty("de11").toString());
					if (rel.hasProperty("de12"))
						relMod.setDe12(rel.getProperty("de12").toString());
	
					relList.add(relMod);
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			db.shutdown();
		}
		return Response.ok(relList).build();
	}

	/**
	 * Helper method for getting all outgoing relationships of a node
	 * 
	 * @param traditionNode
	 * @return
	 * @throws DataBaseException
	 */
	private Iterable<Relationship> getRelationships(Node traditionNode) throws DataBaseException {
		Iterable<Relationship> relations = traditionNode.getRelationships(Direction.OUTGOING);

		if (relations == null)
			throw new DataBaseException("relationships not found");
		return relations;
	}

	/**
	 * Helper method for getting the tradition node with a given tradition id
	 * 
	 * @param tradId
	 * @param engine
	 * @return
	 * @throws DataBaseException
	 */
	private Node getTraditionNode(String tradId, ExecutionEngine engine) throws DataBaseException {
		ExecutionResult result = engine.execute("match (n:TRADITION {id: '" + tradId + "'}) return n");
		Iterator<Node> nodes = result.columnAs("n");

		if (!nodes.hasNext())
			throw new DataBaseException("tradition node not found");

		return nodes.next();
	}

	/**
	 * Returns GraphML file from specified tradition owned by user
	 * 
	 * @param userId
	 * @param traditionName
	 * @return XML data
	 */
	@GET
	@Path("get/{tradId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getTradition(@PathParam("tradId") String tradId) {
		Neo4JToGraphMLParser parser = new Neo4JToGraphMLParser();
		return parser.parseNeo4J(tradId);
	}

	/**
	 * Imports a tradition by given GraphML file and meta data
	 *
	 * @return String that will be returned as a text/plain response.
	 * @throws XMLStreamException
	 */
	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("new")
	public Response importGraphMl(@FormDataParam("name") String name, @FormDataParam("language") String language,
			@FormDataParam("public") String is_public, @FormDataParam("userId") String userId,
			@FormDataParam("file") InputStream uploadedInputStream,
			@FormDataParam("file") FormDataContentDisposition fileDetail) throws IOException, XMLStreamException {

		User user = new User();
		if (!user.checkUserExists(userId)) {
			return Response.status(Response.Status.CONFLICT).entity("Error: No user with this id exists").build();
		}

		// Boolean is_public_bool = is_public.equals("on")? true : false;
		String uploadedFileLocation = "upload/" + fileDetail.getFileName();

		// save it
		writeToFile(uploadedInputStream, uploadedFileLocation);

		GraphMLToNeo4JParser parser = new GraphMLToNeo4JParser();
		Response resp = parser.parseGraphML(uploadedFileLocation, userId);
		// The prefix will always be some sort of '12_', to make sure that all
		// nodes are unique

		deleteFile(uploadedFileLocation);

		return resp;
	}

	/**
	 * Helper method for writing stream into a given location
	 * 
	 * @param uploadedInputStream
	 * @param uploadedFileLocation
	 */
	private void writeToFile(InputStream uploadedInputStream, String uploadedFileLocation) {

		try {
			OutputStream out = new FileOutputStream(new File(uploadedFileLocation));
			int read = 0;
			byte[] bytes = new byte[1024];

			out = new FileOutputStream(new File(uploadedFileLocation));
			while ((read = uploadedInputStream.read(bytes)) != -1) {
				out.write(bytes, 0, read);
			}
			out.flush();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	@GET
	@Path("readings/{tradId}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllReadingsOfTradition(@PathParam("tradId") String tradId) {

		ArrayList<ReadingModel> readList = new ArrayList<ReadingModel>();
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		ExecutionEngine engine = new ExecutionEngine(db);

		try (Transaction tx = db.beginTx()) {
			Node traditionNode = null;
			Node startNode = null;
			ExecutionResult result = engine.execute("match (n:TRADITION {id: '" + tradId + "'}) return n");
			Iterator<Node> nodes = result.columnAs("n");

			if (!nodes.hasNext())
				return Response.status(Status.NOT_FOUND).entity("trad node not found").build();

			traditionNode = nodes.next();

			Iterable<Relationship> rels = traditionNode.getRelationships(Direction.OUTGOING);

			if (rels == null)
				return Response.status(Status.NOT_FOUND).entity("rels not found").build();

			Iterator<Relationship> relIt = rels.iterator();

			while (relIt.hasNext()) {
				Relationship rel = relIt.next();
				startNode = rel.getEndNode();
				if (startNode != null && startNode.hasProperty("text")) {
					if (startNode.getProperty("text").equals("#START#")) {
						rels = startNode.getRelationships(Direction.OUTGOING);
						break;
					}
				}
			}

			if (rels == null)
				return Response.status(Status.NOT_FOUND).entity("start node not found").build();

			TraversalDescription td = db.traversalDescription().breadthFirst()
					.relationships(ERelations.NORMAL, Direction.OUTGOING).evaluator(Evaluators.excludeStartPosition());

			Traverser traverser = td.traverse(startNode);
			for (org.neo4j.graphdb.Path path : traverser) {
				Node nd = path.endNode();
				ReadingModel rm = Reading.readingModelFromNode(nd);
				readList.add(rm);
			}

			tx.success();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			db.shutdown();
		}
		// return Response.status(Status.NOT_FOUND).build();

		return Response.ok(readList).build();
	}

	/**
	 * Helper method for deleting a file by given name
	 * 
	 * @param filename
	 */
	private void deleteFile(String filename) {
		File file = new File(filename);
		file.delete();
	}

	/**
	 * Returns GraphML file from specified tradition owned by user
	 * 
	 * @param userId
	 * @param traditionName
	 * @return XML data
	 */
	@GET
	@Path("getdot/{tradId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getDot(@PathParam("tradId") String tradId) {
		
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		Neo4JToDotParser parser = new Neo4JToDotParser(db);
		Response resp = parser.parseNeo4J(tradId);
		
		String filename = "upload/" + "output.dot";
		
		String everything = "";
		try(BufferedReader br = new BufferedReader(new FileReader(filename))) {
	        StringBuilder sb = new StringBuilder();
	        String line = br.readLine();

	        while (line != null) {
	            sb.append(line);
	            sb.append(System.lineSeparator());
	            line = br.readLine();
	        }
	        everything = sb.toString();
	    } catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return Response.ok(everything).build();
	}
}
