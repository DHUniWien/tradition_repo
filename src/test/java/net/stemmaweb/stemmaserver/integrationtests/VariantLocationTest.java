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
import java.util.Optional;
import java.util.stream.Collectors;

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
        VariantListModel vlist = rsp.readEntity(VariantListModel.class);
        List<VariantLocationModel> vlocs = vlist.getVariantlist();
        assertEquals(3, vlocs.size());
        // Two of our three locations should have a displacement
        List<VariantLocationModel> found = vlocs.stream().filter(VariantLocationModel::hasDisplacement)
                .collect(Collectors.toList());
        assertEquals(2, found.size());
        for (VariantLocationModel f : found) {
            assertEquals(1, f.getBase().size());
            ReadingModel rm = f.getBase().get(0);
            if (rm.getRank().equals(1L))
                assertEquals("Ich", rm.getText());
            else
                assertEquals("auch hier", rm.getText());
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
                .target("/tradition/" + textinfo.get("tradId") + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(rm));
        assertEquals(Response.Status.CREATED.getStatusCode(), rsp.getStatus());

        rsp = jerseyTest.target(restPath + "variants")
                .queryParam("normalize", "spelling")
                .request().get();
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        vlist = rsp.readEntity(VariantListModel.class);
        vlocs = vlist.getVariantlist();
        assertEquals(2, vlocs.size());
        assertEquals(2, vlocs.stream().filter(VariantLocationModel::hasDisplacement).count());

        // Now try combining the transposition
        rsp = jerseyTest.target(restPath + "variants")
                .queryParam("normalize", "spelling")
                .queryParam("combine_dislocations", "yes")
                .request().get();
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        vlist = rsp.readEntity(VariantListModel.class);
        vlocs = vlist.getVariantlist();
        assertEquals(2, vlocs.size());

        // Then with a significance filter
        rsp = jerseyTest.target(restPath + "variants")
                .queryParam("significant", "maybe")
                .request().get();
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        vlist = rsp.readEntity(VariantListModel.class);
        vlocs = vlist.getVariantlist();
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
        VariantListModel vlist = rsp.readEntity(VariantListModel.class);
        List<VariantLocationModel> vlocs = vlist.getVariantlist();
        assertEquals(7, vlocs.size());
        assertEquals(2, vlocs.stream().filter(VariantLocationModel::hasDisplacement).count());

        rsp = jerseyTest.target(restPath + "variants")
                .queryParam("normalize", "spelling").request().get();
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        vlist = rsp.readEntity(VariantListModel.class);
        vlocs = vlist.getVariantlist();
        assertEquals(6, vlocs.size());
        assertEquals(2, vlocs.stream().filter(VariantLocationModel::hasDisplacement).count());

        rsp = jerseyTest.target(restPath + "variants")
                .queryParam("significant", "yes").request().get();
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        vlist = rsp.readEntity(VariantListModel.class);
        vlocs = vlist.getVariantlist();
        assertEquals(1, vlocs.size());
        assertEquals(0, vlocs.stream().filter(VariantLocationModel::hasDisplacement).count());
    }

    public void testMatthew() {
        Map<String,String> textinfo = setupText("milestone-401-related.xml", "graphml");
        String restPath = String.format("/tradition/%s/section/%s/", textinfo.get("tradId"), textinfo.get("sectId"));

        // First normalized, no suppression, no combination
        Response rsp = jerseyTest.target(restPath + "variants")
                .queryParam("normalize", "spelling")
                .queryParam("suppress_matching", "none")
                .request().get();
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        VariantListModel vlist = rsp.readEntity(VariantListModel.class);
        List<VariantLocationModel> vlocs = vlist.getVariantlist();
        assertEquals(122, vlocs.size());

        // Now normalized, suppress punctuation, no combination
        rsp = jerseyTest.target(restPath + "variants")
                .queryParam("normalize", "spelling")
                .request().get();
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        vlist = rsp.readEntity(VariantListModel.class);
        vlocs = vlist.getVariantlist();
        assertEquals(88, vlocs.size());
        // TODO Check on some that should have been altered
        // 26: յաշխարհն] 	աշխարհն: C D I J K M1775 M2899 M8232 O V W Y Z;

        // there is no 68
        // 69: զոր] 	զորս: D E F I J M1775 M3380 M8232 O V W W243 W246 X Y Z;
        // check 107 #2 for W (a.c.)
        // 122: գաւառին] 	գաւառի: A; 	գաւառէն: C D E F G H I J M2855 M3380 M6605 W W243 W246 Y Z;
        // 128: և] 	(om.): Bz644 W246 X;
        // 139: զառաւել] 	զառաւելն: E F G M2855 M3380 M8232 V W243 W246 Y; (only one)
        // 151: անողորմ արձակեալ] 	յարձակեալ: A; 	յանողորմ յարձակեալ: V Y; ի վերայ իրերաց (interp.): Bz644 K;
        // there is no 152
        // 174: անասնոց] 	անասնցն: E F G M2855 M3380 M8232 W243 W246 Y; 	աւանաց և գիւղից transp. post (NULL): Bz644 K;
        // 194: ժամանակի] 	ժամանակին: D I J M1775 M2855 M8232 O W;

        // Now normalized, suppress punctuation, collapse dislocations
        rsp = jerseyTest.target(restPath + "variants")
                .queryParam("normalize", "spelling")
                .queryParam("combine_dislocations", "true")
                .request().get();
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        vlist = rsp.readEntity(VariantListModel.class);
        vlocs = vlist.getVariantlist();
        assertEquals(85, vlocs.size());

        // 16 should compress
        // 99 should compress
        // 134 should maybe compress?
        // 149 should be gone into 151
    }

    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
        super.tearDown();
    }

}
