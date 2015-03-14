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

import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;

import net.stemmaweb.model.*;


/**
 * 
 * @author ramona, sevi, joel
 *
 **/

@Path("/tradition")
public class Tradition {
	public static final String DB_PATH = "database";

	GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
    /**
     * 
     * @param textInfo in JSON Format 
     * @return OK on success or an ERROR as JSON
     */
    @POST
    @Path("{textId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(TextInfoModel textInfo,
    		@PathParam("textId") String textId){
    	
    	User user = new User();
    	if(!user.checkUserExists(textInfo.getOwnerId()))
    	{
    		return Response.status(Response.Status.CONFLICT).entity("Error: A user with this id does not exist").build();
    	}

    	GraphDatabaseService db= dbFactory.newEmbeddedDatabase(DB_PATH);

    	ExecutionEngine engine = new ExecutionEngine(db);
    	try(Transaction tx = db.beginTx())
    	{
    		ExecutionResult result = engine.execute("match (textId:TRADITION {id:'"+textId+"'}) return textId");
    		Iterator<Node> nodes = result.columnAs("textId");
    		
    		
    		if(nodes.hasNext()){
    			// Remove the old ownership
    			String removeRelationQuery = "MATCH (tradition:TRADITION {id: '"+textId+"'}) "
    					+ "MATCH tradition<-[r:NORMAL]-(:USER) DELETE r";
    			result = engine.execute(removeRelationQuery);
    			System.out.println(result.dumpToString());
    			
    			// Add the new ownership
    			String createNewRelationQuery = "MATCH(user:USER {id:'"+textInfo.getOwnerId()+"'}) "
    					+ "MATCH(tradition: TRADITION {id:'"+textId+"'}) "
    							+ "SET tradition.name = '"+textInfo.getName()+"' "
    									+ "SET tradition.public = '"+textInfo.getIsPublic()+"' "
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
	    TraversalDescription td = db.traversalDescription()
	            .breadthFirst()
	            .relationships( Relations.NORMAL, Direction.OUTGOING )
	            .evaluator( Evaluators.excludeStartPosition() );
	    return td.traverse( reading );
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

		GraphDatabaseService db = dbFactory.newEmbeddedDatabase("database");
		
		ExecutionEngine engine = new ExecutionEngine(db);
		ReadingModel reading = null;
		
		try (Transaction tx = db.beginTx()) {
			Node traditionNode = null;
			Node startNode = null;
    		ExecutionResult result = engine.execute("match (n:TRADITION {id: '"+ tradId +"'}) return n");
    		Iterator<Node> nodes = result.columnAs("n");
    		
    		if(!nodes.hasNext())
    			return Response.status(Status.NOT_FOUND).entity("trad node not found").build();
    		
    		traditionNode = nodes.next();
    		
    		Iterable<Relationship> rels = traditionNode.getRelationships(Direction.OUTGOING);
    		
    		if(rels==null) 
    			return Response.status(Status.NOT_FOUND).entity("rels not found").build();

    		Iterator<Relationship> relIt = rels.iterator();
    		
    		while( relIt.hasNext()) {
    			Relationship rel = relIt.next();
    			startNode = rel.getEndNode();
    			if(startNode!=null && startNode.hasProperty("text")) {
    				if(startNode.getProperty("text").equals("#START#")) {
    					rels = startNode.getRelationships(Direction.OUTGOING);
    					break;
    				}
    			}	
    		}

    		if(rels == null) 
    			return Response.status(Status.NOT_FOUND).entity("start node not found").build();
    		
    		Traverser traverser = getReading( startNode, db );
    		for ( org.neo4j.graphdb.Path path : traverser){
    			String id = (String) path.endNode().getProperty("id"); 
    		    if(id.matches(".*" + readId)) {
    		    	reading = Reading.readingModelFromNode(path.endNode());
    		    	break;
    		    }
    		}
    		if(reading == null)
    			return Response.status(Status.NOT_FOUND).entity("no reading with this id found").build();
    		
    		tx.success();
		}
		catch(Exception e) {
	    	e.printStackTrace();
	    }	
		finally {
			db.shutdown();
		}
		
		return Response.ok(reading).build();
	}

	@GET
	@Path("witness/{tradId}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getWitness(@PathParam("tradId") String tradId) {
		
		ArrayList<WitnessModel> witlist= new ArrayList<WitnessModel>();

		GraphDatabaseService db= dbFactory.newEmbeddedDatabase(DB_PATH);
		
		ExecutionEngine engine = new ExecutionEngine(db);
	
		
		try (Transaction tx = db.beginTx()) 
		{
			Node traditionNode = null;
			Node startNode = null;
    		ExecutionResult result = engine.execute("match (n:TRADITION {id: '"+ tradId +"'}) return n");
    		Iterator<Node> nodes = result.columnAs("n");
    		
    		if(!nodes.hasNext())
    			return Response.status(Status.NOT_FOUND).entity("trad node not found").build();
    		
    		traditionNode = nodes.next();
    		    		
    		Iterable<Relationship> rels = traditionNode.getRelationships(Direction.OUTGOING);
    		
    		if(rels==null) 
    			return Response.status(Status.NOT_FOUND).entity("rels not found").build();

    		Iterator<Relationship> relIt = rels.iterator();
    		
    		while( relIt.hasNext()) 
    		{
    			Relationship rel = relIt.next();
    			startNode = rel.getEndNode();
    			if(startNode!=null && startNode.hasProperty("text"))
    			{
    				if(startNode.getProperty("text").equals("#START#"))
    				{
    					rels = startNode.getRelationships(Direction.OUTGOING);
    					break;
    				}
    			}	
    		}

    		if(rels==null) 
    			return Response.status(Status.NOT_FOUND).entity("start node not found").build();

    		for(Relationship rel : rels)
    		{
    			for(String id : ((String[])rel.getProperty("lexemes")) ) 
        		{
        			WitnessModel witM = new WitnessModel();
        			witM.setId(id);
        			
        			witlist.add(witM);
        		}
    		}
    		
    		
    		
			tx.success();
		}
		catch(Exception e)
	    {
	    	e.printStackTrace();
	    }	
		finally
		{
			db.shutdown();
		}
		//return Response.status(Status.NOT_FOUND).build();
		
		return Response.ok(witlist).build();
	}
	
	/**
	 * Returns GraphML file from specified tradition owned by user
	 * @param userId
	 * @param traditionName
	 * @return XML data
	 */
	@GET 
	@Path("get/{tradId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTradition(@PathParam("tradId") String tradId)
	{
        return Neo4JToGraphMLParser.parseNeo4J(tradId, DB_PATH);
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
    public Response create(
    					@FormDataParam("name") String name,
    					@FormDataParam("language") String language,
    					@FormDataParam("public") String is_public,
    					@FormDataParam("userId") String userId,
    					@FormDataParam("file") InputStream uploadedInputStream,
    					@FormDataParam("file") FormDataContentDisposition fileDetail) throws IOException, XMLStreamException {
    	
    	User user = new User();
    	if(!user.checkUserExists(userId))
    	{
    		return Response.status(Response.Status.CONFLICT).entity("Error: No user with this id exists").build();
    	}
    	
    	//Boolean is_public_bool = is_public.equals("on")? true : false;
    	String uploadedFileLocation = "upload/" + fileDetail.getFileName();
    	 
		// save it
		writeToFile(uploadedInputStream, uploadedFileLocation);
    	
		Response resp = GraphMLToNeo4JParser.parseGraphML(uploadedFileLocation, DB_PATH, userId);
		// The prefix will always be some sort of '12_', to make sure that all nodes are unique
		
		deleteFile(uploadedFileLocation);

    	return resp;
    }
    
    
    /**
     * Helper class for writing stream into a given location
     * @param uploadedInputStream
     * @param uploadedFileLocation
     */
 	private void writeToFile(InputStream uploadedInputStream,
 		String uploadedFileLocation) {
  
 		try {
 			
 			OutputStream out = new FileOutputStream(new File(
 					uploadedFileLocation));
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
 	 * Helper class for deleting a file by given name
 	 * @param filename
 	 */
 	private void deleteFile(String filename)
 	{
 		File file = new File(filename);
 		file.delete();
 	}
}
