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

import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.UserModel;

import org.junit.Before;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;



/**
 * 
 * @author jakob, severin
 *
 */
@Path("/user")
public class User implements IResource {
	GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();

	@GET
	public String getIt(){
		return "User!";
	}
    /**
     * This method can be used to determine whether a user with given Id exists in the DB
     * @param userId
     * @return
     */
    public boolean checkUserExists(String userId)
    {
    	boolean userExists = false;
    	GraphDatabaseService db= dbFactory.newEmbeddedDatabase(DB_PATH);
    	ExecutionEngine engine = new ExecutionEngine(db);
    	try(Transaction tx = db.beginTx())
    	{
    		ExecutionResult result = engine.execute("match (userId:USER {id:'"+userId+"'}) return userId");
    		Iterator<Node> nodes = result.columnAs("userId");
    		if(nodes.hasNext())
    			userExists = true;
    		else
    			userExists = false;
    		tx.success();
    	}
    	finally {
    		db.shutdown();
    	}
		return userExists;
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
    	
    	if(checkUserExists(userModel.getId()))
    	{
    		return Response.status(Response.Status.CONFLICT).entity("Error: A user with this id already exists").build();
    	}
    	
    	GraphDatabaseService db= dbFactory.newEmbeddedDatabase(DB_PATH);
    	ExecutionEngine engine = new ExecutionEngine(db);
    	try(Transaction tx = db.beginTx())
    	{
    		ExecutionResult rootNodeSearch = engine.execute("match (n:ROOT) return n");
    		Node rootNode = (Node) rootNodeSearch.columnAs("n").next();
    		
    		Node node = db.createNode(Nodes.USER);
    		node.setProperty("id", userModel.getId());
    		node.setProperty("isAdmin", userModel.getIsAdmin());
    			
    		rootNode.createRelationshipTo(node, Relations.NORMAL);

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
    	GraphDatabaseService db= dbFactory.newEmbeddedDatabase(DB_PATH);

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
    			return Response.status(Response.Status.NOT_FOUND).build();
    		}

    		tx.success();
    	} finally {
        	db.shutdown();
    	}  	
    	return Response.status(Response.Status.OK).entity(userModel).build();
	}
    
    @GET
    @Path("traditions/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTraditionsByUserId(@PathParam("userId") String userId)
    {
    	ArrayList<TraditionModel> traditions = new ArrayList<TraditionModel>();
    	if(!checkUserExists(userId))
    	{
    		return Response.status(Response.Status.NOT_FOUND).entity("Error: A user with this id does not exist!").build();
    	}
    	
    	GraphDatabaseService db= dbFactory.newEmbeddedDatabase(DB_PATH);

    	ExecutionEngine engine = new ExecutionEngine(db);
    	ExecutionResult result = null;
    	try(Transaction tx = db.beginTx())
    	{
    		result = engine.execute("match (n)<-[:NORMAL]-(userId:USER {id:'"+userId+"'}) return n");
    		Iterator<Node> tradIterator = result.columnAs("n");
   			while(tradIterator.hasNext())
   			{
  				if(tradIterator.hasNext())
  				{
  					Node tradNode = tradIterator.next();
  					TraditionModel tradition = new TraditionModel();
  					tradition.setId(tradNode.getProperty("id").toString());
  					tradition.setName(tradNode.getProperty("name").toString());
   					traditions.add(tradition);
  				}
   			}
    		
    		tx.success();
   		
    	} finally {
    		db.shutdown();
    	}
    	
    	return Response.status(Response.Status.OK).entity(traditions).build();
    }
    
    
}
