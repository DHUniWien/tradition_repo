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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.stream.XMLStreamException;

import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.model.TraditionMetadataModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.services.Neo4JToDotParser;
import net.stemmaweb.services.Neo4JToGraphMLParser;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Uniqueness;

import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;


/**
 * 
 * Comprises all the api calls related to a tradition.
 *
 */
@Path("/tradition")
public class Tradition implements IResource {
	GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
	GraphDatabaseService db = dbServiceProvider.getDatabase();

	/**
	 * 
	 * @param textInfo
	 *            in JSON Format
	 * @return OK on success or an ERROR as JSON
	 */
	@POST
	@Path("changemetadata/fromtradition/{tradId}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response changeOwnerOfATradition(TraditionMetadataModel textInfo, @PathParam("tradId") String witnessId) {
		
		if (!DatabaseService.checkIfUserExists(textInfo.getOwnerId(),db)) {
			return Response.status(Response.Status.NOT_FOUND).entity("Error: A user with this id does not exist")
					.build();
		}

		ExecutionEngine engine = new ExecutionEngine(db);
		try (Transaction tx = db.beginTx()) {
			ExecutionResult result = engine.execute("match (witnessId:TRADITION {id:'" + witnessId + "'}) return witnessId");
			Iterator<Node> nodes = result.columnAs("witnessId");

			if (nodes.hasNext()) {
				// Remove the old ownership
				String removeRelationQuery = "MATCH (tradition:TRADITION {id: '" + witnessId + "'}) "
						+ "MATCH tradition<-[r:NORMAL]-(:USER) DELETE r";
				result = engine.execute(removeRelationQuery);
				System.out.println(result.dumpToString());

				// Add the new ownership
				String createNewRelationQuery = "MATCH(user:USER {id:'" + textInfo.getOwnerId() + "'}) "
						+ "MATCH(tradition: TRADITION {id:'" + witnessId + "'}) " + "SET tradition.name = '"
						+ textInfo.getName() + "' " + "SET tradition.public = '" + textInfo.getIsPublic() + "' "
						+ "CREATE (tradition)<-[r:NORMAL]-(user) RETURN r, tradition";
				result = engine.execute(createNewRelationQuery);
				System.out.println(result.dumpToString());

			} else {
				// Tradition not found
				return Response.status(Response.Status.NOT_FOUND).entity("Tradition not found").build();
			}

			tx.success();
		} catch (Exception e) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
		} 
		return Response.status(Response.Status.OK).entity(textInfo).build();
	}
	
	/**
	 * Gets a list of all the complete traditions in the database.
	 * 
	 * @return
	 */
	@GET
	@Path("getalltraditions")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllTraditions() {
		List<TraditionModel> traditionList = new ArrayList<TraditionModel>();
		
		ExecutionEngine engine = new ExecutionEngine(db);
		
		try (Transaction tx = db.beginTx()) {
			
			ExecutionResult result = engine.execute("match (u:USER)-[:NORMAL]->(n:TRADITION) return n");
			Iterator<Node> traditions = result.columnAs("n");
			while(traditions.hasNext())
			{
				Node trad = traditions.next();
				TraditionModel tradModel = new TraditionModel();
				if(trad.hasProperty("id"))
					tradModel.setId(trad.getProperty("id").toString());
				if(trad.hasProperty("name"))	
					tradModel.setName(trad.getProperty("name").toString());
				traditionList.add(tradModel);
			}
			tx.success();
		} catch (Exception e) {
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
		return Response.ok().entity(traditionList).build();
	}

	/**
	 * Gets a list of all the witnesses of a tradition with the given id.
	 * 
	 * @param tradId
	 * @return
	 */
	@GET
	@Path("getallwitnesses/fromtradition/{tradId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllWitnesses(@PathParam("tradId") String tradId) {

		ArrayList<WitnessModel> witlist = new ArrayList<WitnessModel>();

		ExecutionEngine engine = new ExecutionEngine(db);

		try (Transaction tx = db.beginTx()) {
			Node traditionNode = null;
			Iterable<Relationship> relationships = null;
			Node startNode = DatabaseService.getStartNode(tradId, db);

			try {
				traditionNode = getTraditionNode(tradId, engine);								

				if (traditionNode == null){
					return Response.status(Status.NOT_FOUND).entity("tradition not found").build();
				}
				if (startNode == null)
					return Response.status(Status.NOT_FOUND).entity("no tradition with this id was found").build();

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
			} 
		} catch (Exception e) {
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
		return Response.ok(witlist).build();
	}

	/**
	 * Gets a list of all relationships of a tradition with the given id.
	 * 
	 * @param tradId
	 * @return
	 */
	@GET
	@Path("getallrelationships/{tradId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllRelationships(@PathParam("tradId") String tradId) {

		ArrayList<RelationshipModel> relList = new ArrayList<RelationshipModel>();

		

		try (Transaction tx = db.beginTx()) {

			Node startNode = DatabaseService.getStartNode(tradId, db);

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
					if (rel.hasProperty("a_derivable_from_b"))
						relMod.setA_derivable_from_b(rel.getProperty("a_derivable_from_b").toString());
					if (rel.hasProperty("alters_meaning"))
						relMod.setAlters_meaning(rel.getProperty("alters_meaning").toString());
					if (rel.hasProperty("annotation"))
						relMod.setAnnotation(rel.getProperty("annotation").toString());
					if (rel.hasProperty("b_derivable_from_a"))
						relMod.setB_derivable_from_a(rel.getProperty("b_derivable_from_a").toString());
					if (rel.hasProperty("displayform"))
						relMod.setDisplayform(rel.getProperty("displayform").toString());
					if (rel.hasProperty("extra"))
						relMod.setExtra(rel.getProperty("extra").toString());
					if (rel.hasProperty("is_significant"))
						relMod.setIs_significant(rel.getProperty("is_significant").toString());
					if (rel.hasProperty("non_independent"))
						relMod.setNon_independent(rel.getProperty("non_independent").toString());
					if (rel.hasProperty("reading_a"))
						relMod.setReading_a(rel.getProperty("reading_a").toString());
					if (rel.hasProperty("reading_b"))
						relMod.setReading_b(rel.getProperty("reading_b").toString());
					if (rel.hasProperty("scope"))
						relMod.setScope(rel.getProperty("scope").toString());
					if (rel.hasProperty("type"))
						relMod.setType(rel.getProperty("type").toString());
					if (rel.hasProperty("witness"))
						relMod.setWitness(rel.getProperty("witness").toString());
	
					relList.add(relMod);
				}
			}

		} catch (Exception e) {
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
		return Response.ok(relList).build();
	}


	/**
	 * Helper method for getting the tradition node with a given tradition id
	 * 
	 * @param tradId
	 * @param engine
	 * @return
	 */
	private Node getTraditionNode(String tradId, ExecutionEngine engine) {
		ExecutionResult result = engine.execute("match (n:TRADITION {id: '" + tradId + "'}) return n");
		Iterator<Node> nodes = result.columnAs("n");

		if (!nodes.hasNext())
			return null;
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
	@Path("gettradition/withid/{tradId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getTradition(@PathParam("tradId") String tradId) {
		Neo4JToGraphMLParser parser = new Neo4JToGraphMLParser();
		return parser.parseNeo4J(tradId);
	}
	
	/**
	 * Removes a complete tradition
	 * @param tradId
	 * @return
	 */
	@DELETE
	@Path("deletetradition/withid/{tradId}")
	public Response deleteTraditionById(@PathParam("tradId") String tradId) {

		ExecutionEngine engine = new ExecutionEngine(db);
		try (Transaction tx = db.beginTx()) {
			ExecutionResult result = engine.execute("match (tradId:TRADITION {id:'" + tradId + "'}) return tradId");
			Iterator<Node> nodes = result.columnAs("tradId");

			if (nodes.hasNext()) {
				Node node = nodes.next();
				
				/*
				 * Find all the nodes and relations to remove
				 */
				Set<Relationship> removableRelations = new HashSet<Relationship>();
				Set<Node> removableNodes = new HashSet<Node>();
				for (Node currentNode : db.traversalDescription()
				        .depthFirst()
				        .relationships( ERelations.NORMAL, Direction.OUTGOING)
				        .relationships( ERelations.STEMMA, Direction.OUTGOING)
				        .relationships( ERelations.RELATIONSHIP, Direction.OUTGOING)
				        .uniqueness( Uniqueness.RELATIONSHIP_GLOBAL )
				        .traverse( node )
				        .nodes()) 
				{
					for(Relationship currentRelationship : currentNode.getRelationships()){
						removableRelations.add(currentRelationship);
					}
					removableNodes.add(currentNode);
				}
				
				/*
				 * Remove the nodes and relations
				 */
				for(Relationship removableRel:removableRelations){
		            removableRel.delete();
		        }
				for(Node remNode:removableNodes){
		            remNode.delete();
		        }
			} else {
				return Response.status(Response.Status.NOT_FOUND).entity("A tradition with this id was not found!").build();
			}

			tx.success();
		} catch (Exception e) {
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
		return Response.status(Response.Status.OK).build();
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
	@Path("/newtraditionwithgraphml")
	public Response importGraphMl(@FormDataParam("name") String name, @FormDataParam("language") String language,
			@FormDataParam("public") String is_public, @FormDataParam("userId") String userId,
			@FormDataParam("file") InputStream uploadedInputStream,
			@FormDataParam("file") FormDataContentDisposition fileDetail) throws IOException, XMLStreamException {

		
		
		if (!DatabaseService.checkIfUserExists(userId,db)) {
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
	 * Returns DOT file from specified tradition owned by user
	 * 
	 * @param traditionName
	 * @return XML data
	 */
	@GET
	@Path("getdot/fromtradition/{tradId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getDot(@PathParam("tradId") String tradId) {
		
		String filename = "upload/" + "output.dot";
		
		File file = new File(filename);
		file.delete();
		
		ExecutionEngine engine = new ExecutionEngine(db);
		if(getTraditionNode(tradId,engine) == null)
			return Response.status(Status.NOT_FOUND).entity("No such tradition found").build();

		Neo4JToDotParser parser = new Neo4JToDotParser(db);
		parser.parseNeo4J(tradId);
		
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
	    }catch  (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return Response.ok(everything).build();
	}
}
