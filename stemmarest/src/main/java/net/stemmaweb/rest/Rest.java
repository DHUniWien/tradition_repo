package net.stemmaweb.rest;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.services.Neo4JToGraphMLParser;

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
	
	@GET 
	@Path("/gettradition")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTradition()
	{
		
        return Neo4JToGraphMLParser.parseNeo4J("1","Sapientia", DB_PATH);
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
    public Response create(
    					@FormDataParam("name") String name,
    					@FormDataParam("language") String language,
    					@FormDataParam("public") String is_public,
    					@FormDataParam("userId") String userId,
    					@FormDataParam("file") InputStream uploadedInputStream,
    					@FormDataParam("file") FormDataContentDisposition fileDetail) throws IOException, XMLStreamException {
      
    	if(!User.checkUserExists(userId))
    	{
    		return Response.status(Response.Status.CONFLICT).entity("Error: No user with this id exists").build();
    	}
    	
    	//Boolean is_public_bool = is_public.equals("on")? true : false;
    	String uploadedFileLocation = "upload/" + fileDetail.getFileName();
    	 
		// save it
		writeToFile(uploadedInputStream, uploadedFileLocation);
    	
		Response resp = GraphMLToNeo4JParser.parseGraphML(uploadedFileLocation, DB_PATH, userId, name.substring(0, 3));
		// The prefix will always be some sort of '12_', to make sure that all nodes are unique
		
		deleteFile(uploadedFileLocation);

    	return resp;
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
