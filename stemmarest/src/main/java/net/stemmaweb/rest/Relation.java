package net.stemmaweb.rest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

@Path("/relation")
public class Relation implements IResource {
	
    @GET 
    @Produces("text/plain")
    public String getIt() {
        return "The relation api is up and running";
    }
}
