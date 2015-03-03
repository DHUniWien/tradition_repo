package net.stemmaweb.rest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


/**
 * 
 * @author jakobschaerer
 *
 */
@Path("/witness")
public class Witness {
    
	@GET 
    @Produces("text/plain")
    public String getIt() {
        return "The Witnesses";
    }
	
	/**
	 * 
	 * @param textId
	 * @return a witness as //TODO what 
	 */
	
	@GET
	@Path("{textId}")
	@Produces(MediaType.APPLICATION_JSON)
	public String getReadings(@PathParam("textId") String textId){
		return textId;
	}

	
}
