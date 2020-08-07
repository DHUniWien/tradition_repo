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
import java.util.*;
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

        // None of our locations should yet have a displacement, but two should have variants that are displaced
        assertTrue(vlocs.stream().noneMatch(VariantLocationModel::hasDisplacement));
        List<VariantLocationModel> found = vlocs.stream().filter(x -> x.getVariants().stream()
                .anyMatch(VariantModel::getDisplaced)).collect(Collectors.toList());
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
        assertEquals(0, vlocs.stream().filter(VariantLocationModel::hasDisplacement).count());
        // The "Plä(t)zchen" should no longer be there
        assertTrue(vlocs.stream().noneMatch(x -> x.toString().contains("Plä")));
        HashSet<String> expected = new HashSet<>();
        vlocs.forEach(x -> expected.add(x.toString()));


        // Now try combining the transpositions. Here it won't work, since they are symmetrical and
        // that makes things complicated.
        rsp = jerseyTest.target(restPath + "variants")
                .queryParam("normalize", "spelling")
                .queryParam("combine_dislocations", "yes")
                .request().get();
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        vlist = rsp.readEntity(VariantListModel.class);
        vlocs = vlist.getVariantlist();
        assertEquals(2, vlocs.size());
        assertTrue(expected.containsAll(vlocs.stream().map(VariantLocationModel::toString).collect(Collectors.toList())));

        // Then with a significance filter, and no normalisation, which will return only the spelling variant
        rsp = jerseyTest.target(restPath + "variants")
                .queryParam("significant", "maybe")
                .request().get();
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        vlist = rsp.readEntity(VariantListModel.class);
        vlocs = vlist.getVariantlist();
        assertEquals(1, vlocs.size());
        assertEquals(0, vlocs.stream().filter(VariantLocationModel::hasDisplacement).count());
        assertTrue(vlocs.stream().allMatch(x -> x.toString().contains("Plä")));

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
        assertEquals(0, vlocs.stream().filter(VariantLocationModel::hasDisplacement).count());

        // Check that parameters are set correctly
        assertFalse(vlist.isDislocationCombined());
        assertFalse(vlist.isFilterTypeOne());
        assertFalse(vlist.isNonsenseSuppressed());
        assertEquals("^(\\p{IsPunctuation}+)$", vlist.getSuppressedReadingsRegex());
        assertEquals("majority", vlist.getBasisText());
        assertNull(vlist.getConflateOnRelation());
        assertEquals("no", vlist.getSignificant());
        assertEquals(1, vlist.getDislocationTypes().size());
        assertTrue(vlist.getDislocationTypes().contains("transposition"));

        rsp = jerseyTest.target(restPath + "variants")
                .queryParam("normalize", "spelling").request().get();
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        vlist = rsp.readEntity(VariantListModel.class);
        vlocs = vlist.getVariantlist();
        assertEquals(6, vlocs.size());
        assertEquals(0, vlocs.stream().filter(VariantLocationModel::hasDisplacement).count());

        // Check that parameters are set correctly
        assertFalse(vlist.isDislocationCombined());
        assertFalse(vlist.isFilterTypeOne());
        assertFalse(vlist.isNonsenseSuppressed());
        assertEquals("^(\\p{IsPunctuation}+)$", vlist.getSuppressedReadingsRegex());
        assertEquals("majority", vlist.getBasisText());
        assertEquals("spelling", vlist.getConflateOnRelation());
        assertEquals("no", vlist.getSignificant());
        assertEquals(1, vlist.getDislocationTypes().size());
        assertTrue(vlist.getDislocationTypes().contains("transposition"));

        rsp = jerseyTest.target(restPath + "variants")
                .queryParam("significant", "yes").request().get();
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        vlist = rsp.readEntity(VariantListModel.class);
        vlocs = vlist.getVariantlist();
        assertEquals(1, vlocs.size());
        assertEquals(0, vlocs.stream().filter(VariantLocationModel::hasDisplacement).count());

        // Check that parameters are set correctly
        assertFalse(vlist.isDislocationCombined());
        assertFalse(vlist.isFilterTypeOne());
        assertFalse(vlist.isNonsenseSuppressed());
        assertEquals("^(\\p{IsPunctuation}+)$", vlist.getSuppressedReadingsRegex());
        assertEquals("majority", vlist.getBasisText());
        assertNull(vlist.getConflateOnRelation());
        assertEquals("yes", vlist.getSignificant());
        assertEquals(1, vlist.getDislocationTypes().size());
        assertTrue(vlist.getDislocationTypes().contains("transposition"));
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
        assertEquals(121, vlocs.size());

        // Spot check for omissions, interpolations and a.c. readings
        HashSet<String> stringifiedVList = new HashSet<>();
        vlocs.forEach(x -> stringifiedVList.add(x.toString()));
        // There should be no repeats
        assertEquals(121, stringifiedVList.size());
        List<String> expected = Arrays.asList(
                "2: ընդ] \tընդիր: J (a.c.); ",
                "4: ընդ] \t(om.): Bz644 K; ",
                "6: այնոսիկ և] \t. (interp.): Bz644 D F H K M2899 M8232 W Y; ",
                "103: . և քրիստոնեայք] \tքրիստոնէիցն: F (a.c.); ",
                "106: քրիստոնեայք անթիւք] \tքրիստոնեայքն անթիւ: M8232; ",
                "107: անթիւք] \tանթիւ: Bz644 K W (a.c.); "
        );
        assertTrue(stringifiedVList.containsAll(expected));


        // Now normalized, suppress punctuation and nonsense readings, no combination
        rsp = jerseyTest.target(restPath + "variants")
                .queryParam("normalize", "spelling")
                .queryParam("exclude_nonsense", "true")
                .request().get();
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        vlist = rsp.readEntity(VariantListModel.class);
        vlocs = vlist.getVariantlist();
        assertEquals(81, vlocs.size());

        // Check on some that should no longer exist
        assertTrue(vlocs.stream().noneMatch(x -> x.getRankIndex().equals(6L)));
        assertTrue(vlocs.stream().noneMatch(x -> x.getRankIndex().equals(68L)));
        assertEquals(1, vlocs.stream().filter(x -> x.getRankIndex().equals(139L)).count());

        // Check on some that should have changed
        stringifiedVList.clear();
        vlocs.forEach(x -> stringifiedVList.add(x.toString()));
        assertEquals(81, stringifiedVList.size());
        expected = Arrays.asList(
                "26: յաշխարհն] \tաշխարհն: C D I J K M1775 M2899 M8232 O V W Y Z; ",
                "69: զոր] \tզորս: D E F I J M1775 M3380 M8232 O V W W243 W246 X Y Z; ",
                "104: և քրիստոնեայք] \tքրիստոնէիցն: F (a.c.); ",
                "106: քրիստոնեայք անթիւք] \tքրիստոնեայքն անթիւ: M8232; ",
                "107: անթիւք] \tանթիւ: Bz644 K W (a.c.); ",
                "122: գաւառին] \tգաւառի: A; \tգաւառէն: C D E F G H I J M2855 M3380 M6605 W W243 W246 Y Z; ",
                "128: և] \t(om.): Bz644 W246 X; ",
                "139: զառաւել] \tզառաւելն: E F G M2855 M3380 M8232 V W243 W246 Y; ",
                "174: անասնոց] \tանասնցն: E F G M2855 M3380 M8232 W243 W246 Y; \tաւանաց և գիւղից: Bz644 K; ",
                "194: ժամանակի] \tժամանակին: D I J M1775 M2855 M8232 O W; "
        );
        assertTrue(stringifiedVList.containsAll(expected));

        // Now normalized, suppress punctuation & nonsense readings, collapse dislocations
        rsp = jerseyTest.target(restPath + "variants")
                .queryParam("normalize", "spelling")
                .queryParam("exclude_nonsense", "true")
                .queryParam("combine_dislocations", "true")
                .request().get();
        assertEquals(Response.Status.OK.getStatusCode(), rsp.getStatus());
        vlist = rsp.readEntity(VariantListModel.class);
        vlocs = vlist.getVariantlist();
        assertEquals(80, vlocs.size());

        // Check on the ones that should have been changed
        assertTrue(vlocs.stream().noneMatch(x -> x.getRankIndex().equals(101L)));
        stringifiedVList.clear();
        vlocs.forEach(x -> stringifiedVList.add(x.toString()));
        assertEquals(80, stringifiedVList.size());
        expected = Arrays.asList(
                "16: սով] \ttransp. post սաստիկ: X; ",
                "18: սաստիկ ի] \tև (interp.): D; ",
                "99: լինէր] \ttransp. post անցումն: Bz644 K M8232 V Y; ",
                "134: սաստկանայր] \ttransp. post սովն: C D I J M1775 M6605 O W Z; ",
                "136: սովն առաւել] \tսաստկացաւ (interp.): H M2899; "
        );
        assertTrue(stringifiedVList.containsAll(expected));

        // 149 persists because 151 lemma doesn't exactly match
        //
    }

    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
        super.tearDown();
    }

}
