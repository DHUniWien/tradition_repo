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
@Path("/user")
public class User {
	
	/**
	 * 
	 * @return User!
	 */
    @GET 
    @Produces("text/plain")
    public String getIt() {
        return "User!";
    }
    
    /**
     * 
     * @param userModel in JSON Format 
     * @return OK on success or an ERROR as JSON
     */
    @POST
    @Path("create")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(UserModel userModel){
    	
		GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
    	GraphDatabaseService db= dbFactory.newEmbeddedDatabase("database");

    	ExecutionEngine engine = new ExecutionEngine(db);
    	try(Transaction tx = db.beginTx())
    	{
    		ExecutionResult result = engine.execute("match (userId:USER {id:'"+userModel.getId()+"'}) return userId");
    		Iterator<Node> nodes = result.columnAs("userId");
    		ExecutionResult rootNodeSearch = engine.execute("match (n:ROOT) return n");
    		Node rootNode = (Node) rootNodeSearch.columnAs("n").next();
    		

    		if(!nodes.hasNext())
    		{
    			Node node = db.createNode(Nodes.USER);
    			node.setProperty("id", userModel.getId());
    			node.setProperty("isAdmin", userModel.getIsAdmin());
    			
    			node.createRelationshipTo(rootNode, Relations.NORMAL);
    		} else {
    			return Response.status(Response.Status.CONFLICT).entity("Error: A user with this id already exists").build();
    		}

    		tx.success();
    	} finally {
        	db.shutdown();
    	}
    	
    	return Response.status(Response.Status.CREATED).build();
    }
    
    /**
     * 
     * @param userId
     * @return UserModel as JSON
     */
    @GET
	@Path("{userId}")
    @Produces(MediaType.APPLICATION_JSON)
	public Response getUserById(@PathParam("userId") String userId) {
    	UserModel userModel = new UserModel();
    	GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
    	GraphDatabaseService db= dbFactory.newEmbeddedDatabase("database");

    	ExecutionEngine engine = new ExecutionEngine(db);
    	try(Transaction tx = db.beginTx())
    	{
    		ExecutionResult result = engine.execute("match (userId:USER {id:'"+userId+"'}) return userId");
    		Iterator<Node> nodes = result.columnAs("userId");
    		
    		if(nodes.hasNext()){
    			Node node = nodes.next();
        		userModel.setId((String) node.getProperty("id"));
        		userModel.setIsAdmin((String) node.getProperty("isAdmin"));
    		} else {
    			Response.status(Response.Status.NOT_FOUND);
    		}

    		tx.success();
    	} finally {
        	db.shutdown();
    	}  	
    	return Response.status(Response.Status.OK).entity(userModel).build();
	}
    
}
