package net.stemmaweb.rest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;

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

import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.model.TextInfoModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.services.Neo4JToGraphMLParser;

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
	public Response create(TextInfoModel textInfo, @PathParam("textId") String textId) {

		User user = new User();
		if (!user.checkUserExists(textInfo.getOwnerId())) {
			return Response.status(Response.Status.CONFLICT).entity("Error: A user with this id does not exist")
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
				return Response.status(Response.Status.NOT_FOUND).build();
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
				.relationships(Relations.NORMAL, Direction.OUTGOING).evaluator(Evaluators.excludeStartPosition());
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
	public Response getReading(@PathParam("tradId") String tradId, @PathParam("readId") String readId) {
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
			if (startNode.getProperty("dn1").toString().equals(readId)) {
				reading = Reading.readingModelFromNode(startNode);
			} else {
				Traverser traverser = getReading(startNode, db);
				for (org.neo4j.graphdb.Path path : traverser) {
					String id = (String) path.endNode().getProperty("dn1");
					if (id.equals(readId)) {
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

	/**
	 * Duplicates a reading in a specific tradition.
	 * 
	 * @param tradId
	 * @param readId
	 * @param firstWitnesses
	 * @param secondWitnesses
	 * @return
	 */
	@GET
	@Path("duplicate/{tradId}/{readId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response duplicateReading(@PathParam("tradId") String tradId, @PathParam("readId") String readId,
			@PathParam("firstWitnesses") String firstWitnesses, @PathParam("secondWitnesses") String secondWitnesses) {
		String addedReadingId = tradId + "_" + readId + ".5";

		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);

		Node startNode = null;
		try {
			DatabaseService service = new DatabaseService(db);
			startNode = service.getStartNode(tradId);
		} catch (DataBaseException e) {
			return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
		}

		try (Transaction tx = db.beginTx()) {
			boolean foundReading = false;
			Traverser traverser = getReading(startNode, db);
			for (org.neo4j.graphdb.Path path : traverser) {
				String id = (String) path.endNode().getProperty("dn1");
				if (id.equals(readId)) {
					duplicateReading(firstWitnesses, secondWitnesses, addedReadingId, db, path);

					foundReading = true;
					break;
				}
			}
			if (!foundReading)
				return Response.status(Status.NOT_FOUND).entity("no reading with this id found").build();

			tx.success();
		} catch (Exception e) {
			return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
		} finally {
			db.shutdown();
		}
		return Response.ok("Successfully duplicated reading").build();
	}

	private void duplicateReading(String firstWitnesses, String secondWitnesses, String addedReadingId,
			GraphDatabaseService db, org.neo4j.graphdb.Path path) {
		Node originalReading;
		Node addedReading;
		// duplicating of reading happens here
		addedReading = db.createNode();
		originalReading = path.endNode();

		// copy reading
		addedReading.addLabel(Nodes.WORD);
		addedReading.setProperty("dn15", originalReading.getProperty("dn15"));
		addedReading.setProperty("dn1", addedReadingId);
		addedReading.setProperty("dn14", originalReading.getProperty("dn14"));
		addedReading.setProperty("dn11", originalReading.getProperty("dn11"));
		addedReading.setProperty("dn2", originalReading.getProperty("dn2"));
		//addedReading.setProperty("dn99", originalReading.getProperty("dn99"));

		// add witnesses to relationships
		// Outgoing
		Iterable<Relationship> rels = originalReading.getRelationships(Relations.NORMAL, Direction.OUTGOING);
		for (Relationship relationship : rels) {
			relationship.setProperty("lexemes", firstWitnesses);
			Node targetNode = relationship.getEndNode();
			Relationship addedRelationship = addedReading.createRelationshipTo(targetNode, Relations.NORMAL);
			addedRelationship.setProperty("lexemes", secondWitnesses);
		}
		// Incoming
		rels = originalReading.getRelationships(Relations.NORMAL, Direction.INCOMING);
		for (Relationship relationship : rels) {
			relationship.setProperty("lexemes", firstWitnesses);
			Node originNode = relationship.getStartNode();
			Relationship addedRelationship = originNode.createRelationshipTo(addedReading, Relations.NORMAL);
			addedRelationship.setProperty("lexemes", secondWitnesses);
		}
	}

	/**
	 * Merges two readings into one single reading in a specific tradition.
	 * 
	 * @param tradId
	 * @param firstReadId
	 * @param secondReadId
	 * @return
	 */
	@GET
	@Path("merge/{tradId}/{firstReadId}/{secondReadId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response mergeReadings(@PathParam("tradId") String tradId, @PathParam("firstReadId") String firstReadId,
			@PathParam("secondReadId") String secondReadId) {
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
				String id = (String) path.endNode().getProperty("dn1");
				if (id.equals(firstReadId))
					firstReading = path.endNode();
				if (id.equals(secondReadId))
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
						.entity("readings to be merged do not contain the same text").build();

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
		Relationship firstRel = firstReading.getSingleRelationship(Relations.NORMAL, direction);
		Relationship secondRel = secondReading.getSingleRelationship(Relations.NORMAL, direction);
		firstRel.setProperty("lexemes", firstRel.getProperty("lexemes").toString()
				+ secondRel.getProperty("lexemes").toString());
		secondRel.delete();
	}

	/**
	 * Splits up a reading into two readings in a specific tradition.
	 * 
	 * @param tradId
	 * @param readId
	 * @return
	 */
	@GET
	@Path("split/{tradId}/{readId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response splitReading(@PathParam("tradId") String tradId, @PathParam("readId") String readId) {
		// TODO: API yet unclear: thus just set as variables for the moment:
		// (Maybe we can get the texts using a small algorithm)
		String originalReadingText = "bla";
		String addedReadingText = "blub";
		String addedReadingId = tradId + "_" + readId + ".5";

		Node originalReading = null;
		Node addedReading = null;

		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);

		Node startNode = null;
		try {
			DatabaseService service = new DatabaseService(db);
			startNode = service.getStartNode(tradId);
		} catch (DataBaseException e) {
			return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
		}

		try (Transaction tx = db.beginTx()) {
			boolean foundReading = false;
			Traverser traverser = getReading(startNode, db);
			for (org.neo4j.graphdb.Path path : traverser) {
				String id = (String) path.endNode().getProperty("dn1");
				if (id.equals(readId)) {
					// splitting of reading happens here
					addedReading = db.createNode();
					originalReading = path.endNode();

					Iterable<Relationship> rels = originalReading
							.getRelationships(Relations.NORMAL, Direction.OUTGOING);
					for (Relationship relationship : rels) {
						addedReading.createRelationshipTo(relationship.getEndNode(), Relations.NORMAL);
						relationship.delete();
					}

					addedReading.addLabel(Nodes.WORD);
					addedReading.setProperty("dn15", addedReadingText);
					addedReading.setProperty("dn1", addedReadingId);
					// set Rank to Rank of original reading and add .5
					addedReading.setProperty("dn14", originalReading.getProperty("dn14") + ".5");
					addedReading.setProperty("dn11", originalReading.getProperty("dn11"));
					addedReading.setProperty("dn2", originalReading.getProperty("dn2"));
					//addedReading.setProperty("dn99", originalReading.getProperty("dn99"));

					originalReading.createRelationshipTo(addedReading, Relations.NORMAL);
					originalReading.setProperty("dn15", originalReadingText);

					foundReading = true;
					break;
				}
			}
			if (!foundReading)
				return Response.status(Status.NOT_FOUND).entity("no reading with this id found").build();

			tx.success();
		} catch (Exception e) {
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

			// This Block could not be replaced by the method getStartNode() but
			// yet unclear why.
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
			
		ExecutionResult result = engine.execute("match (n:WORD)-[r:RELATIONSHIP]-(w) where n.id =~ '"+ tradId +"_.*"+ "' return r");
		Iterator<Relationship> rels = result.columnAs("r");
				
				if (!rels.hasNext())
				{
					db.shutdown();
					throw new DataBaseException("no relationships found");
				}
				
				while(rels.hasNext())
				{
					Relationship rel = rels.next();
					RelationshipModel relMod = new RelationshipModel();
				
					if(rel.hasProperty("source"))
						relMod.setSource(rel.getProperty("source").toString());
					if(rel.hasProperty("target"))
						relMod.setTarget(rel.getProperty("target").toString());
					if(rel.hasProperty("id"))
						relMod.setId(rel.getProperty("id").toString());
					if(rel.hasProperty("de0"))
						relMod.setDe0(rel.getProperty("de0").toString());
					if(rel.hasProperty("de1"))
						relMod.setDe1(rel.getProperty("de1").toString());
					if(rel.hasProperty("de2"))
						relMod.setDe2(rel.getProperty("de2").toString());
					if(rel.hasProperty("de3"))
						relMod.setDe3(rel.getProperty("de3").toString());
					if(rel.hasProperty("de4"))
						relMod.setDe4(rel.getProperty("de4").toString());
					if(rel.hasProperty("de5"))
						relMod.setDe5(rel.getProperty("de5").toString());
					if(rel.hasProperty("de6"))
						relMod.setDe6(rel.getProperty("de6").toString());
					if(rel.hasProperty("de7"))
						relMod.setDe7(rel.getProperty("de7").toString());
					if(rel.hasProperty("de8"))
						relMod.setDe8(rel.getProperty("de8").toString());
					if(rel.hasProperty("de9"))
						relMod.setDe9(rel.getProperty("de9").toString());
					if(rel.hasProperty("de10"))
						relMod.setDe10(rel.getProperty("de10").toString());
					if(rel.hasProperty("de11"))
						relMod.setDe11(rel.getProperty("de11").toString());
					if(rel.hasProperty("de12"))
						relMod.setDe12(rel.getProperty("de12").toString());
										
					relList.add(relMod);
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
					.relationships(Relations.NORMAL, Direction.OUTGOING).evaluator(Evaluators.excludeStartPosition());

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
}
