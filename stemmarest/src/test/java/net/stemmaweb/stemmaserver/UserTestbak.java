package net.stemmaweb.stemmaserver;
import static org.junit.Assert.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import net.stemmaweb.model.UserModel;

import org.junit.Test;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.test.framework.JerseyTest;

/**
 * 
 * @author jakob
 *
 */
public class UserTestbak extends JerseyTest {
	
    public UserTestbak()throws Exception {
        super("net.stemmaweb.rest");
    }
    
    @Test
    public void Test() {
        WebResource webResource = resource();
        String responseMsg = webResource.path("user").get(String.class);
        assertEquals("User!", responseMsg);
    }
 
    @Test
    public void createUserTest() {
        WebResource webResource = resource();

        String jsonPayload = "{\"isAdmin\":0,\"id\":1337}";
        ClientResponse returnJSON = webResource.path("user/create")
                .type(MediaType.APPLICATION_JSON).post(ClientResponse.class, jsonPayload);
        System.out.println("test");
        assertEquals(Response.Status.CREATED.getStatusCode(),returnJSON.getStatus());
    }
    
    @Test
    public void getUserTest() {
        WebResource webResource = resource();

        ClientResponse returnJSON = webResource.path("user/1337")
                .type(MediaType.APPLICATION_JSON).get(ClientResponse.class);
        UserModel user = returnJSON.getEntity(UserModel.class);
        assertEquals(Response.Status.OK.getStatusCode(),returnJSON.getStatus());
        assertTrue(user.getId().equals("1337"));
        assertTrue(user.getIsAdmin().equals("0"));
    }
    
}
