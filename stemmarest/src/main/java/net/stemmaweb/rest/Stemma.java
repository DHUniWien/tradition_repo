package net.stemmaweb.rest;

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

import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.DotToNeo4JParser;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.Neo4JToDotParser;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Uniqueness;

/**
 * Comprises all the api calls related to a stemma.
 * Can be called using http://BASE_URL/stemma
 * @author PSE FS 2015 Team2
 */
@Path("/stemma")
public class Stemma implements IResource {
	
	GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
	GraphDatabaseService db = dbServiceProvider.getDatabase();
	
	/**
	 * Gets a list of all Stemmata available, as dot format
	 * 
	 * @param tradId
	 * @return Http Response ok and a list of DOT JSON strings on success or an
	 *         ERROR in JSON
	 */
	@GET
	@Path("getallstemmata/fromtradition/{tradId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllStemmata(@PathParam("tradId") String tradId)
	{
		Response resp = null;
		
		
		// check if tradition exists
		if(DatabaseService.getStartNode(tradId, db)!=null)
		{
			try(Transaction tx = db.beginTx())
			{
			ExecutionEngine engine = new ExecutionEngine(db);
			// find all stemmata associated with this tradition
			ExecutionResult result = engine.execute("match (t:TRADITION {id:'"+ tradId +"'})-[:STEMMA]->(s:STEMMA) return s");
			
			Iterator<Node> stemmata = result.columnAs("s");
			Neo4JToDotParser parser = new Neo4JToDotParser(db);
			ArrayList<String> stemmataList = new ArrayList<String>();
			while(stemmata.hasNext())
			{
				Node stemma = stemmata.next();
				System.out.println(stemma.getProperty("name"));
				Response localResp = parser.parseNeo4JStemma(tradId, stemma.getProperty("name").toString());
				String dot = (String) localResp.getEntity();
				stemmataList.add(dot);
			}
		
			resp = Response.ok(stemmataList).build();
			tx.success();
			}
			catch (Exception e) {
				resp = Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
		else
		{
			resp = Response.status(Status.NOT_FOUND).entity("No such tradition found").build();
		}
		return resp;
	}
	
	/**
	 * Returns JSON string with a Stemma of a tradition in DOT format
	 * 
	 * @param tratitionId
	 * @param stemmaTitle
	 * @return Http Response ok and DOT JSON string on success or an ERROR in
	 *         JSON
	 */
	@GET
	@Path("getstemma/fromtradition/{tradId}/withtitle/{stemmaTitle}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getStemma(@PathParam("tradId") String tradId,@PathParam("stemmaTitle") String stemmaTitle) {
		
		Neo4JToDotParser parser = new Neo4JToDotParser(db);
		Response resp = parser.parseNeo4JStemma(tradId, stemmaTitle);
		
		return resp;
	}

	/**
	 * Puts the Stemma of a DOT file in the database
	 * 
	 * @param tratitionId
	 * @return Http Response ok and DOT JSON string on success or an ERROR in
	 *         JSON
	 */
	@POST
	@Path("newstemma/intradition/{tradId}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response setStemma(@PathParam("tradId") String tradId, String dot) {
		DotToNeo4JParser parser = new DotToNeo4JParser(db);
		Response resp = parser.parseDot(dot,tradId);
		
		return resp;
	}
	
	/**
	 * Reorients a stemma tree with a given new root node
	 * @param tradId
	 * @param stemmaTitle
	 * @param nodeId
	 * @return Http Response ok and DOT JSON string on success or an ERROR in
	 *         JSON
	 */
	@POST
	@Path("reorientstemma/fromtradition/{tradId}/withtitle/{stemmaTitle}/withnewrootnode/{nodeId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response reorientStemma(@PathParam("tradId") String tradId,@PathParam("stemmaTitle") String stemmaTitle,
			@PathParam("nodeId") String nodeId) {
		
		ExecutionEngine engine = new ExecutionEngine(db);
		
		Response resp;

		try (Transaction tx = db.beginTx()) 
    	{
    		ExecutionResult result1 = engine.execute("match (t:TRADITION {id:'"+ 
    						tradId + "'})-[:STEMMA]->(n:STEMMA { name:'" + 
    						stemmaTitle +"'}) return n");
    		Iterator<Node> stNodes = result1.columnAs("n");
    		
    		if(!stNodes.hasNext()) {
    			return Response.status(Status.NOT_FOUND).build();
    		}
    		
			Node startNodeStemma = stNodes.next();
    		String stemmaType = startNodeStemma.getProperty("type").toString();
    		
    		ExecutionResult result2 = engine.execute("match (s:STEMMA { name:'"+
    						stemmaTitle+"'})-[:STEMMA*..]-(w:WITNESS { id:'" +
    						nodeId + "'}) return w");
    		Iterator<Node> nodes = result2.columnAs("w");
    		
    		if(!nodes.hasNext()) {
    			return Response.status(Status.NOT_FOUND).build();
    		}
    		
			Node newRootNode = nodes.next();
    		
    		if(stemmaType.equals("digraph"))
				resp = reorientDigraph(newRootNode, startNodeStemma);
    		else
    			resp = reorientGraph(newRootNode,startNodeStemma);
		
		
		tx.success();
		}
		resp = getStemma(tradId, stemmaTitle);
		return resp;
    	
	}

	/**
	 * Reorients a directed Graph: Searches the path to the new RootNode; reverse the
	 * relationships; changes the first relationship
	 * 
	 * @param newRootNode
	 * @param startNodeStemma
	 * @return
	 */
	private Response reorientDigraph(Node newRootNode, Node startNodeStemma) {
		
		Iterator<Relationship> stRels = startNodeStemma.getRelationships().iterator();
		
		if(!stRels.hasNext()) {
			return Response.status(Status.NOT_FOUND).build();
		}
		
		String  actualRootNodeId = stRels.next().getEndNode().getProperty("id").toString();
		
		if(actualRootNodeId.equals(newRootNode.getProperty("id").toString())) {
				return Response.ok().build();
		}
		
		ArrayList<Relationship> pathRels = new ArrayList<Relationship>();

		for (Relationship rel : db.traversalDescription().breadthFirst()
				.relationships(ERelations.STEMMA,Direction.INCOMING)
				.uniqueness(Uniqueness.NODE_GLOBAL)
				.traverse(newRootNode).relationships()) {
			
			pathRels.add(rel);
			
			if(rel.getStartNode().getProperty("id").toString().equals(actualRootNodeId))
				break;
			
		}
		
		for(Relationship rela : pathRels) {
			
			Node startNode = rela.getStartNode();
			Node endNode = rela.getEndNode();
			
			rela.delete();
			
			endNode.createRelationshipTo(startNode,ERelations.STEMMA);
		}
		
		reorientGraph(newRootNode, startNodeStemma);
			
		return Response.ok().build();
		
	}
	
	/**
	 * Reorients an undirected graph: deletes first relationship to node and exchange with a relationship to 
	 * the new root node
	 * 
	 * @param newRootNode
	 * @param startNodeStemma
	 * @return
	 */
	private Response reorientGraph(Node newRootNode, Node startNodeStemma) {
		
		Iterator<Relationship> rels = startNodeStemma.getRelationships().iterator();
		
		if(!rels.hasNext()) {
			return Response.status(Status.NOT_FOUND).build();
		}
		
		Relationship rootRel = rels.next();
		
		rootRel.delete();
		
		startNodeStemma.createRelationshipTo(newRootNode,ERelations.STEMMA);
		return Response.ok().build();
	}

	
}