package net.stemmaweb.stemmaserver;
import static org.junit.Assert.*;

import org.junit.Test;

import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.test.framework.JerseyTest;
/**
 * 
 * @author jakob
 *
 */
public class MyResourceTest extends JerseyTest {

    public MyResourceTest()throws Exception {
        super("net.stemmaweb.rest");
    }

    @Test
    public void Test() {
        WebResource webResource = resource();
        String responseMsg = webResource.path("myresource").get(String.class);
        assertEquals("Hi there!", responseMsg);
    }
}
