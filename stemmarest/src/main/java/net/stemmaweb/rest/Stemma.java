package net.stemmaweb.rest;

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
import net.stemmaweb.services.DotToNeo4JParser;
import net.stemmaweb.services.Neo4JToDotParser;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.traversal.Uniqueness;

import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;


/**
 * 
 * @author ramona
 *
 **/

@Path("/stemma")
public class Stemma implements IResource {
	
	GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
	private GraphDatabaseService db;
	
	/**
	 * Returns JSON string with a stemma of a tradition in DOT format
	 * 
	 * @param tratitionId
	 * @param stemmaTitle
	 * @return DOT JSON string
	 */
	@GET
	@Path("/{tradId}/{stemmaTitle}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getStemma(@PathParam("tradId") String tradId,@PathParam("stemmaTitle") String stemmaTitle) {
		
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		Neo4JToDotParser parser = new Neo4JToDotParser(db);
		Response resp = parser.parseNeo4JStemma(tradId, stemmaTitle);
		
		return resp;
	}

	/**
	 * Puts the stemma of a DOT file in the database
	 * 
	 * @param tratitionId
	 * @return 
	 */
	@POST
	@Path("/{tradId}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response setStemma(@PathParam("tradId") String tradId, String dot) {
		
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		DotToNeo4JParser parser = new DotToNeo4JParser(db);
		Response resp = parser.parseDot(dot,tradId);
		
		return resp;
	}
	
	/**
	 * Reorients the stemma tree with a given new root node
	 * 
	 * @param tratitionId
	 * @return 
	 */
	@POST
	@Path("/reorient/{tradId}/{stemmaTitle}/{nodeId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response reorientStemma(@PathParam("tradId") String tradId,@PathParam("stemmaTitle") String stemmaTitle,
			@PathParam("nodeId") String nodeId) {
		
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		ExecutionEngine engine = new ExecutionEngine(db);
		
		Response resp;

		try (Transaction tx = db.beginTx()) 
    	{
    		ExecutionResult result1 = engine.execute("match (t:TRADITION {id:'"+ 
    						tradId + "'})-[:STEMMA]->(n:STEMMA { name:'" + 
    						stemmaTitle +"'}) return n");
    		Iterator<Node> stNodes = result1.columnAs("n");
    		
    		if(!stNodes.hasNext()) {
    	    	db.shutdown();
    			return Response.status(Status.NOT_FOUND).build();
    		}
    		
			Node startNodeStemma = stNodes.next();
    		String stemmaType = startNodeStemma.getProperty("type").toString();
    		
    		Node newRootNode = null;
			for (Node node : db.traversalDescription().depthFirst()
					.relationships(ERelations.STEMMA,Direction.OUTGOING)
					.uniqueness(Uniqueness.NODE_GLOBAL)
					.traverse(startNodeStemma).nodes()) {
				if(node.hasProperty("id")) {
	    			if(node.getProperty("id").equals(nodeId))
	    				 newRootNode = node;
				}
    		}
    		
    		if(stemmaType.equals("digraph"))
	    		resp = reorientDigraph(newRootNode,startNodeStemma);
    		else
    			resp = reorientGrap(newRootNode,startNodeStemma);
		
		
		tx.success();
		}
		finally
    	{
    		db.shutdown();
    	}
		return resp;
    	
	}

	private Response reorientDigraph(Node newRootNode, Node startNodeStemma) {
		return null;
		
	}
	
	private Response reorientGrap(Node newRootNode, Node startNodeStemma) {
		
		Iterator<Relationship> rels = startNodeStemma.getRelationships().iterator();
		
		System.out.println("Here we are!");
		
		if(!rels.hasNext()) {
			db.shutdown();
		return Response.status(Status.NOT_FOUND).build();
		}
		
		Relationship rootRel = rels.next();
		rootRel.delete();
		
		startNodeStemma.createRelationshipTo(newRootNode,ERelations.STEMMA);
		return Response.ok().build();
	}

	
}