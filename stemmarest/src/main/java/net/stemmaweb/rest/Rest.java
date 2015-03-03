package net.stemmaweb.rest;

import java.io.IOException;
import java.io.InputStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;

/**
 * Root resource (exposed at "rest" path)
 */
@Path("/")
public class Rest {
	
	public static final String DB_PATH = "database"; // this is the local path to StemmaServer/database

	@GET 
    @Produces("text/plain")
    public String getIt() {
		System.out.println("Hello, World!");
        return "hello from rest.java!";
    }
	
    /**
     * Method handling HTTP GET requests. The returned object will be sent
     * to the client as "text/plain" media type.
     *
     * @return String that will be returned as a text/plain response.
     */
    
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/newtradition")
    public String create(@FormDataParam("name") String name,
    					@FormDataParam("file") InputStream uploadedInputStream,
    					@FormDataParam("file") FormDataContentDisposition fileDetail) throws IOException {
       
    	if(uploadedInputStream!=null)
				System.out.println(uploadedInputStream.available());

    	if(fileDetail!=null)
    	{
    		System.out.println("Test ok " + name + "" + fileDetail.getFileName());
    	}
    	else
    	{
    		System.out.println("error filedetail is empty");
    	}
    	GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
    	GraphDatabaseService db= dbFactory.newEmbeddedDatabase(DB_PATH);

    	try (Transaction tx = db.beginTx()) {
    		Node tradition = db.createNode(Nodes.TRADITION);	
    		tradition.setProperty("name", name);
    		
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
    	//return Response.status(200).entity("Return message").build();
    }
}
