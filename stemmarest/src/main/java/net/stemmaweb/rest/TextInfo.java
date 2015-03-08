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

import net.stemmaweb.model.TextInfoModel;
import net.stemmaweb.model.UserModel;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

/**
 * 
 * @author jakob
 *
 */
@Path("/textinfo")
public class TextInfo {
	
    @GET 
    @Produces("text/plain")
    public String getIt() {
        return "textinfo!";
    }
    
    /**
     * 
     * @param userModel in JSON Format 
     * @return OK on success or an ERROR as JSON
     */
    @POST
    @Path("{textId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(TextInfoModel textInfo,
    		@PathParam("textId") String textId){
    	
    	if(!User.checkUserExists(textInfo.getOwnerId()))
    	{
    		return Response.status(Response.Status.CONFLICT).entity("Error: A user with this id does not exist").build();
    	}
    	
		GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
    	GraphDatabaseService db= dbFactory.newEmbeddedDatabase("database");

    	ExecutionEngine engine = new ExecutionEngine(db);
    	try(Transaction tx = db.beginTx())
    	{
    		ExecutionResult result = engine.execute("match (textId:TRADITION {id:'"+textId+"'}) return textId");
    		Iterator<Node> nodes = result.columnAs("textId");
    		
    		if(nodes.hasNext()){
    			Node node = nodes.next();
    			// TODO DH-80 Copy a tradition add the new name and link it to the user
    		} else {
    			// Tradition no found
    			return Response.status(Response.Status.NOT_FOUND).build();
    		}

    		tx.success();
    	} finally {
        	db.shutdown();
    	}
    	return Response.status(Response.Status.OK).entity(textInfo).build();
    }
    
}
