package net.stemmaweb.stemmaserver.integrationtests;

import junit.framework.TestCase;
import net.stemmaweb.model.*;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.Util;
import org.glassfish.jersey.test.JerseyTest;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        Response rsp = Util.createTraditionFromFileOrString(jerseyTest, filename, "LR", "1", String.format("src/TestFiles/%s", filename), filetype);
        assertEquals(Response.Status.CREATED.getStatusCode(), rsp.getStatus());
        String tradId = Util.getValueFromJson(rsp, "tradId");
        // Get the section ID
        rsp = jerseyTest.target("/tradition/" + tradId + "/sections").request().get();
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        List<SectionModel> sm = rsp.readEntity(new GenericType<List<SectionModel>>() {});
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
        Response rsp = jerseyTest.target(restPath + "variants").request().get();
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        List<VariantLocationModel> vlocs = rsp.readEntity(new GenericType<List<VariantLocationModel>>() {});
        assertEquals(3, vlocs.size());
        /* Optional<VariantLocationModel> found = vlocs.stream().filter(VariantLocationModel::hasDisplacement).findFirst();
        assertTrue(found.isPresent());
        VariantLocationModel toTest = found.get();
        for (ReadingModel rm : toTest.getBase()) {
            if (rm.getRank().equals(1L))
                assertEquals("Ich ... auch hier", rm.getDisplay());
            else
                assertEquals("Auch hier ... ich", rm.getDisplay());
        } */

        // Then with conflated spelling
        HashMap<String,String> readingLookup = Util.makeReadingLookup(jerseyTest, textinfo.get("tradId"));
        RelationModel rm = new RelationModel();
        rm.setSource(readingLookup.get("Plätzchen/5"));
        rm.setTarget(readingLookup.get("Pläzchen/5"));
        rm.setType("spelling");
        rm.setIs_significant("yes");
        rm.setScope("local");
        rsp = jerseyTest
                .target("/tradition/" + textinfo.get("tradId") + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(rm));
        assertEquals(Response.Status.CREATED.getStatusCode(), rsp.getStatus());

        rsp = jerseyTest.target(restPath + "variants")
                .queryParam("normalize", "spelling")
                .request().get();
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        vlocs = rsp.readEntity(new GenericType<List<VariantLocationModel>>() {});
        assertEquals(2, vlocs.size());
        assertEquals(2, vlocs.stream().filter(VariantLocationModel::hasDisplacement).count());

        // Then with a significance filter
        rsp = jerseyTest.target(restPath + "variants")
                .queryParam("significant", "maybe")
                .request().get();
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        vlocs = rsp.readEntity(new GenericType<List<VariantLocationModel>>() {});
        assertEquals(1, vlocs.size());
        assertEquals(0, vlocs.stream().filter(VariantLocationModel::hasDisplacement).count());
    }

    public void testChaucer() {
        Map<String,String> textinfo = setupText("testTradition.xml", "stemmaweb");
        String restPath = String.format("/tradition/%s/section/%s/", textinfo.get("tradId"), textinfo.get("sectId"));

        // Compress and relate some readings
        HashMap<String,String> readingLookup = Util.makeReadingLookup(jerseyTest, textinfo.get("tradId"));
        ReadingBoundaryModel rbm = new ReadingBoundaryModel();
        rbm.setSeparate(true);
        rbm.setCharacter(" ");
        Response rsp = jerseyTest.target("/reading/" + readingLookup.get("with/3") + "/concatenate/" + readingLookup.get("his/4"))
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(rbm));
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());

        RelationModel relm = new RelationModel();
        relm.setSource(readingLookup.get("the/17"));
        relm.setTarget(readingLookup.get("teh/16"));
        relm.setType("spelling");
        relm.setScope("tradition");
        rsp = jerseyTest.target("/tradition/" + textinfo.get("tradId") + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(relm));
        assertEquals(Response.Status.CREATED.getStatusCode(), rsp.getStatus());
        // This should have made two relations
        GraphModel result = rsp.readEntity(GraphModel.class);
        assertEquals(2, result.getRelations().size());

        relm.setSource(readingLookup.get("to/16"));
        relm.setTarget(readingLookup.get("unto/16"));
        relm.setType("grammatical");
        relm.setIs_significant("yes");
        rsp = jerseyTest.target("/tradition/" + textinfo.get("tradId") + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(relm));
        assertEquals(Response.Status.CREATED.getStatusCode(), rsp.getStatus());

        // Now we can test variant lists
        rsp = jerseyTest.target(restPath + "variants").request().get();
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        List<VariantLocationModel> vlocs = rsp.readEntity(new GenericType<List<VariantLocationModel>>() {});
        assertEquals(7, vlocs.size());
        assertEquals(2, vlocs.stream().filter(VariantLocationModel::hasDisplacement).count());

        rsp = jerseyTest.target(restPath + "variants")
                .queryParam("normalize", "spelling").request().get();
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        vlocs = rsp.readEntity(new GenericType<List<VariantLocationModel>>() {});
        assertEquals(6, vlocs.size());
        assertEquals(2, vlocs.stream().filter(VariantLocationModel::hasDisplacement).count());

        rsp = jerseyTest.target(restPath + "variants")
                .queryParam("significant", "yes").request().get();
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        vlocs = rsp.readEntity(new GenericType<List<VariantLocationModel>>() {});
        assertEquals(1, vlocs.size());
        assertEquals(0, vlocs.stream().filter(VariantLocationModel::hasDisplacement).count());
    }

    public void testMatthew() {
        Map<String,String> textinfo = setupText("milestone-401-related.xml", "graphml");
        String restPath = String.format("/tradition/%s/section/%s/", textinfo.get("tradId"), textinfo.get("sectId"));

        // First with no options
        Response rsp = jerseyTest.target(restPath + "variants")
                .queryParam("normalize", "spelling")
                .request().get();
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        List<VariantLocationModel> vlocs = rsp.readEntity(new GenericType<List<VariantLocationModel>>() {});
        assertEquals(238, vlocs.size());
    }

    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
        super.tearDown();
    }

}
