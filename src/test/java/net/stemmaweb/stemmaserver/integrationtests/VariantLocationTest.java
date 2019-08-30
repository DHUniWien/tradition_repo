package net.stemmaweb.stemmaserver.integrationtests;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import junit.framework.TestCase;
import net.stemmaweb.model.*;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.Util;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class VariantLocationTest extends TestCase {

    private JerseyTest jerseyTest;
    private GraphDatabaseService db;

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
        assertEquals(2, vlocs.size());
        Optional<VariantLocationModel> found = vlocs.stream().filter(VariantLocationModel::getDisplacement).findFirst();
        assertTrue(found.isPresent());
        for (ReadingModel rm : found.get().getReadings()) {
            if (rm.getRank().equals(1L))
                assertEquals("Ich ... auch hier", rm.getDisplay());
            else
                assertEquals("Auch hier ... ich", rm.getDisplay());
        }

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
        assertEquals(1, vlocs.size());
        assertEquals(1, vlocs.stream().filter(VariantLocationModel::getDisplacement).count());

        // Then with a significance filter
        rsp = jerseyTest.resource().path(restPath + "variants")
                .queryParam("significant", "maybe")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        vlocs = rsp.getEntity(new GenericType<List<VariantLocationModel>>() {});
        assertEquals(1, vlocs.size());
        assertEquals(0, vlocs.stream().filter(VariantLocationModel::getDisplacement).count());
    }

    public void testChaucer() {
        Map<String,String> textinfo = setupText("testTradition.xml", "stemmaweb");
        String restPath = String.format("/tradition/%s/section/%s/", textinfo.get("tradId"), textinfo.get("sectId"));

        // Compress and relate some readings
        HashMap<String,String> readingLookup = Util.makeReadingLookup(jerseyTest, textinfo.get("tradId"));
        ReadingBoundaryModel rbm = new ReadingBoundaryModel();
        rbm.setSeparate(true);
        rbm.setCharacter(" ");
        ClientResponse rsp = jerseyTest.resource()
                .path("/reading/" + readingLookup.get("with/3") + "/concatenate/" + readingLookup.get("his/4"))
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, rbm);
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());

        RelationModel relm = new RelationModel();
        relm.setSource(readingLookup.get("the/17"));
        relm.setTarget(readingLookup.get("teh/16"));
        relm.setType("spelling");
        relm.setScope("tradition");
        rsp = jerseyTest.resource().path("/tradition/" + textinfo.get("tradId") + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, relm);
        assertEquals(Response.Status.CREATED.getStatusCode(), rsp.getStatus());
        // This should have made two relations
        GraphModel result = rsp.getEntity(GraphModel.class);
        assertEquals(2, result.getRelations().size());

        relm.setSource(readingLookup.get("to/16"));
        relm.setTarget(readingLookup.get("unto/16"));
        relm.setType("grammatical");
        relm.setIs_significant("yes");
        rsp = jerseyTest.resource().path("/tradition/" + textinfo.get("tradId") + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, relm);
        assertEquals(Response.Status.CREATED.getStatusCode(), rsp.getStatus());

        // Now we can test variant lists
        rsp = jerseyTest.resource().path(restPath + "variants").get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        List<VariantLocationModel> vlocs = rsp.getEntity(new GenericType<List<VariantLocationModel>>() {});
        assertEquals(7, vlocs.size());
        assertEquals(2, vlocs.stream().filter(VariantLocationModel::getDisplacement).count());

        rsp = jerseyTest.resource().path(restPath + "/variants")
                .queryParam("conflate", "spelling").get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        vlocs = rsp.getEntity(new GenericType<List<VariantLocationModel>>() {});
        assertEquals(5, vlocs.size());
        assertEquals(2, vlocs.stream().filter(VariantLocationModel::getDisplacement).count());

        rsp = jerseyTest.resource().path(restPath + "/variants")
                .queryParam("significant", "yes").get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        vlocs = rsp.getEntity(new GenericType<List<VariantLocationModel>>() {});
        assertEquals(7, vlocs.size());
        assertEquals(2, vlocs.stream().filter(VariantLocationModel::getDisplacement).count());
    }

    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
        super.tearDown();
    }

}
