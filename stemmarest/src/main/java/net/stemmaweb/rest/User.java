package net.stemmaweb.rest;

import java.util.Iterator;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import net.stemmaweb.model.UserModel;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import scala.util.control.Exception.Finally;

import com.sun.jersey.multipart.FormDataParam;

@Path("/user")
public class User {
    @GET 
    @Produces("text/plain")
    public String getIt() {
        return "User!";
    }
    
    @POST
    @Path("create")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String create(UserModel userModel){
    	
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
    			
    			Relationship relation = node.createRelationshipTo(rootNode, Relations.NORMAL);
    		} else {
    			return "{\"Status\": \"ERROR A User with this id already exists\"}";
    		}

    		tx.success();
    	} finally {
        	db.shutdown();
    	}
    	
    	
    	return "{\"Status\": \"OK\"}";
    }
    
    @GET
	@Path("{userId}")
	public String getUserById(@PathParam("userId") String userId) {

		return userId;
	}
    
    @POST
    @Path("{userId}/addtradition")

    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String addTradition( UserModel user){
    	return user.getId();
    }

}
