package net.stemmaweb.stemmaserver;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

/**
 * Root resource (exposed at "rest" path)
 */
@Path("rest")
public class Rest {
	
	public static final String DB_PATH = "database"; // this is the local path to StemmaServer/database

    /**
     * Method handling HTTP GET requests. The returned object will be sent
     * to the client as "text/plain" media type.
     *
     * @return String that will be returned as a text/plain response.
     */
    
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes("application/x-www-form-urlencoded")
    @Path("/newtradition")
    public String create(MultivaluedMap<String, String> formParams) {
       
    	
    	GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
    	GraphDatabaseService db= dbFactory.newEmbeddedDatabase(DB_PATH);

    	try (Transaction tx = db.beginTx()) {
    		Node tradition = db.createNode(Nodes.TRADITION);	
    		tradition.setProperty("name", formParams.get("traditionname").get(0));
    		
    		tx.success();
    	}
    	catch(Exception e)
    	{
    		System.out.println("Error while doing transaction!");
    		return "{\"Status\": \"ERROR\"}";
    	}
    	finally
    	{
    		db.shutdown();
    	}
    	
    	return "{\"Status\": \"OK\"}";
    }
}
