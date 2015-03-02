package net.stemmaweb.stemmaserver;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

/**
 * Root resource (exposed at "myresource" path)
 */
@Path("myresource")
public class MyResource {
	
	public static final String DB_PATH = "database"; // this is the local path to StemmaServer/database

    /**
     * Method handling HTTP GET requests. The returned object will be sent
     * to the client as "text/plain" media type.
     *
     * @return String that will be returned as a text/plain response.
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getIt() {
        return "Got it!";
    }
    
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/test")
    public String test() {
        return "{\"Status\": \"OK\"}";
    }
    
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/{nodename}")
    public String create(@PathParam("nodename") String nodename) {
       
    	GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
    	GraphDatabaseService db= dbFactory.newEmbeddedDatabase(DB_PATH);
    	
    	try (Transaction tx = db.beginTx()) {
    		Node word1 = db.createNode(Nodes.READING);	
    		Node word2 = db.createNode(Nodes.READING);
    		word1.setProperty("val", "Hallo");
    		word2.setProperty("val", "Welt");
    		
    		Relationship rel = word1.createRelationshipTo(word2,
    				Relations.NORMAL);
    		rel.setProperty("val", ",");
    		
    		tx.success();
    	}
    	
    	
    	return "Node created";
    }
}
