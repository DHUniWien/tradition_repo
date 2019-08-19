package net.stemmaweb.stemmaserver.integrationtests;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import junit.framework.TestCase;
import net.stemmaweb.model.RelationModel;
import net.stemmaweb.model.SectionModel;
import net.stemmaweb.model.VariantLocationModel;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.Util;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VariantLocationTest extends TestCase {

    private GraphDatabaseService db;
    private JerseyTest jerseyTest;

    public void setUp() throws Exception {
        super.setUp();
        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        Util.setupTestDB(db, "1");

        // Create a JerseyTestServer for the necessary REST API calls
        jerseyTest = Util.setupJersey();
    }

    private Map<String,String> setupText(String filename, String filetype) {
        // Make the tradition and get its ID
        ClientResponse rsp = Util.createTraditionFromFileOrString(jerseyTest, filename, "LR", "1", String.format("src/TestFiles/%s", filename), filetype);
        assertEquals(Response.Status.CREATED.getStatusCode(), rsp.getStatus());
        String tradId = Util.getValueFromJson(rsp, "tradId");
        // Get the section ID
        rsp = jerseyTest.resource().path("/tradition/" + tradId + "/sections").get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        List<SectionModel> sm = rsp.getEntity(new GenericType<List<SectionModel>>() {});
        assertEquals(1, sm.size());
        String sectId = sm.get(0).getId();

        Map<String,String> textinfo = new HashMap<>();
        textinfo.put("tradId", tradId);
        textinfo.put("sectId", sectId);
        return textinfo;
    }

    // To test: Plätzchen, Chaucer, Legend, Florilegium

    public void testPlaetzchen() {
        Map<String,String> textinfo = setupText("plaetzchen_cx.xml", "collatex");
        String restPath = String.format("/tradition/%s/section/%s/", textinfo.get("tradId"), textinfo.get("sectId"));

        // First with no modifications
        ClientResponse rsp = jerseyTest.resource().path(restPath + "variants").get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        List<VariantLocationModel> vlocs = rsp.getEntity(new GenericType<List<VariantLocationModel>>() {});
        assertEquals(3, vlocs.size());
        assertEquals(2, vlocs.stream().filter(VariantLocationModel::getDisplacement).count());

        // Then with conflated spelling
        HashMap<String,String> readingLookup = Util.makeReadingLookup(jerseyTest, textinfo.get("tradId"));
        RelationModel rm = new RelationModel();
        rm.setSource(readingLookup.get("Plätzchen/5"));
        rm.setTarget(readingLookup.get("Pläzchen/5"));
        rm.setType("spelling");
        rm.setIs_significant("yes");
        rm.setScope("local");
        rsp = jerseyTest
                .resource()
                .path("/tradition/" + textinfo.get("tradId") + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, rm);
        assertEquals(Response.Status.CREATED.getStatusCode(), rsp.getStatus());

        rsp = jerseyTest.resource().path(restPath + "variants")
                .queryParam("conflate", "spelling")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        vlocs = rsp.getEntity(new GenericType<List<VariantLocationModel>>() {});
        assertEquals(2, vlocs.size());
        assertEquals(2, vlocs.stream().filter(VariantLocationModel::getDisplacement).count());

        // Then with a significance filter
        rsp = jerseyTest.resource().path(restPath + "variants")
                .queryParam("significant", "maybe")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        vlocs = rsp.getEntity(new GenericType<List<VariantLocationModel>>() {});
        assertEquals(1, vlocs.size());
        assertEquals(0, vlocs.stream().filter(VariantLocationModel::getDisplacement).count());
    }

}
