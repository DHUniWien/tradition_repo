package net.stemmaweb.rest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;

import net.stemmaweb.services.GraphMLToNeo4JParser;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.stream.XMLStreamException;

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
     * @throws XMLStreamException 
     */
    
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/newtradition")
    public String create(
    					@FormDataParam("name") String name,
    					@FormDataParam("language") String language,
    					@FormDataParam("public") String is_public,
    					@FormDataParam("file") InputStream uploadedInputStream,
    					@FormDataParam("file") FormDataContentDisposition fileDetail) throws IOException, XMLStreamException {
      
    	
    	Boolean is_public_bool = is_public.equals("on")? true : false;
    	String uploadedFileLocation = "upload/" + fileDetail.getFileName();
    	 
		// save it
		writeToFile(uploadedInputStream, uploadedFileLocation);
    	
		GraphMLToNeo4JParser.parseGraphML(uploadedFileLocation, DB_PATH);
		
		deleteFile(uploadedFileLocation);
    	
		/*
		 * UNCOMMENT THIS BLOCK IF YOU WANT TO INSERT ADDITIONAL INFORMATION INTO THE DB
    	GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
    	GraphDatabaseService db= dbFactory.newEmbeddedDatabase(DB_PATH);
    
    	try{
    		for(Vertex node : graph.getVertices())
    		{
    			try (Transaction tx = db.beginTx()) {
    				Node nd = db.createNode(Nodes.WORD);	
    				System.out.println(node.getId() + " " + node.getProperty("dn15"));
    				if(node.getProperty("dn15")!=null)
    					nd.setProperty("text", node.getProperty("dn15")); // text
    				if(node.getProperty("dn14")!=null)
    					nd.setProperty("rank", node.getProperty("dn14")); // rank
    				if(node.getProperty("dn1")!=null)
    					nd.setProperty("id", node.getProperty("dn1")); // id
    				//
    				//nd.setProperty("rank", node.getProperty("dn14")); // rank
    			//nd.setProperty("rank", node.getProperty("dn14")); // rank
    				//nd.setProperty("id", node.getProperty("dn1")); // id
    				i++;
    				tx.success();
    			}

    	    	
    		}
    	}
    	catch(Exception e)
    	{
    		System.out.println("Error while doing transaction! " + i);
    		return "{\"Status\": \"ERROR\"}";
    		//return Response.status(500).entity("Internal Server Error").build();
    	}
    	finally{
    		db.shutdown();
    	}*/
	    	
    	return "{\"Status\": \"OK\"}";
    	//return Response.status(200).entity(output).build();
    }
    
    // save uploaded file to temp location
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
 	
 	// delete file from location
 	private void deleteFile(String filename)
 	{
 		File file = new File(filename);
 		file.delete();
 	}
}
