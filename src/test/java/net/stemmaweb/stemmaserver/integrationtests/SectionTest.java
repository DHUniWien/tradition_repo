package net.stemmaweb.stemmaserver.integrationtests;

import junit.framework.TestCase;
import net.stemmaweb.model.*;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.VariantGraphService;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;

import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.MultipleFoundException;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.Assert.assertNotEquals;

/**
 * Tests for text section functionality.
 * Created by tla on 08/02/2017.
 */
public class SectionTest extends TestCase {

    private GraphDatabaseService db;
    private JerseyTest jerseyTest;
    private String tradId;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        Util.setupTestDB(db, "user@example.com");

        // Create a JerseyTestServer for the necessary REST API calls
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(Root.class)
                .create();
        jerseyTest.setUp();

        // Create a tradition to use
        Response jerseyResult = null;
        try {
            jerseyResult = Util.createTraditionFromFileOrString(jerseyTest, "Legend", "LR",
                    "user@example.com", "src/TestFiles/legendfrag.xml", "stemmaweb");
        } catch (Exception e) {
        	fail();
        }
        tradId = Util.getValueFromJson(jerseyResult, "tradId");
    }

    // test creation of a tradition, that it has a single section
    public void testTraditionCreated() {
        List<SectionModel> tSections = jerseyTest.target("/tradition/" + tradId + "/sections")
                .request()
                .get(new GenericType<>() {});
        assertEquals(1, tSections.size());
        assertEquals("DEFAULT", tSections.get(0).getName());

        SectionModel defaultSection = tSections.get(0);
        defaultSection.setName("My new name");
        Response jerseyResult = jerseyTest
                .target("/tradition/" + tradId + "/section/" + defaultSection.getId())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(defaultSection));
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        assertEquals("My new name", Util.getValueFromJson(jerseyResult, "name"));
    }

    public void testAddSection() {
        // Get the existing start and end nodes
        Node startNode = VariantGraphService.getStartNode(tradId, db);
        Node endNode = VariantGraphService.getEndNode(tradId, db);

        String newSectId = Util.getValueFromJson(Util.addSectionToTradition(jerseyTest, tradId, "src/TestFiles/lf2.xml",
                "stemmaweb", "section 2"), "sectionId");

        List<SectionModel> tSections = jerseyTest.target("/tradition/" + tradId + "/sections")
                .request()
                .get(new GenericType<>() {});
        assertEquals(2, tSections.size());
        assertEquals("section 2", tSections.get(1).getName());
        Long expectedRank = 22L;
        assertEquals(expectedRank, tSections.get(1).getEndRank());

        String aText = "quasi duobus magnis luminaribus populus terre illius ad veri dei noticiam & cultum magis " +
                "magisque illustrabatur iugiter ac informabatur Sanctus autem";
        Response jerseyResponse = jerseyTest
                .target("/tradition/" + tradId + "/section/" + newSectId + "/witness/A/text")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        String witFragment = Util.getValueFromJson(jerseyResponse, "text");
        assertEquals(aText, witFragment);

        try (Transaction tx = db.beginTx()) {
            assertEquals(startNode.getId(), VariantGraphService.getStartNode(tradId, db).getId());
            assertNotEquals(endNode.getId(), VariantGraphService.getEndNode(tradId, db).getId());
            assertEquals(VariantGraphService.getEndNode(newSectId, db).getId(), VariantGraphService.getEndNode(tradId, db).getId());
            tx.success();
        }
    }

    public void testSectionRelationships() {
        String newSectId = Util.getValueFromJson(Util.addSectionToTradition(jerseyTest, tradId, "src/TestFiles/lf2.xml",
                "stemmaweb", "section 2"), "sectionId");
        List<RelationModel> sectRels = jerseyTest.target("/tradition/" + tradId + "/section/" + newSectId + "/relations")
                .request()
                .get(new GenericType<>() {});
        assertEquals(9, sectRels.size());
        List<RelationModel> allRels = jerseyTest.target("/tradition/" + tradId + "/relations")
                .request()
                .get(new GenericType<>() {});
        assertEquals(21, allRels.size());
    }

    public void testSectionReadings() {
        String newSectId = Util.getValueFromJson(Util.addSectionToTradition(jerseyTest, tradId, "src/TestFiles/lf2.xml",
                "stemmaweb", "section 2"), "sectionId");
        List<ReadingModel> sectRdgs = jerseyTest.target("/tradition/" + tradId + "/section/" + newSectId + "/readings")
                .request()
                .get(new GenericType<>() {});
        assertEquals(47, sectRdgs.size());
        List<ReadingModel> allRdgs = jerseyTest.target("/tradition/" + tradId + "/readings")
                .request()
                .get(new GenericType<>() {});
        assertEquals(77, allRdgs.size());
    }

    public void testSectionRequestReading() {
        String newSectId = Util.getValueFromJson(Util.addSectionToTradition(jerseyTest, tradId, "src/TestFiles/lf2.xml",
                "stemmaweb", "section 2"), "sectionId");
        List<ReadingModel> sectRdgs = jerseyTest.target("/tradition/" + tradId + "/section/" + newSectId + "/readings")
                .request()
                .get(new GenericType<>() {});
        // Choose a reading at random within the list to request
        ReadingModel ourRdg = null;
        while (ourRdg == null || ourRdg.getIs_end() || ourRdg.getIs_start()) {
            int idx = (int) (Math.random() * sectRdgs.size());
            ourRdg = sectRdgs.get(idx);
        }
        Response jerseyResponse = jerseyTest.target("/tradition/" + tradId + "/section/" + newSectId + "/reading/" + ourRdg.getId())
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        ReadingModel theRdg = jerseyResponse.readEntity(ReadingModel.class);
        assertEquals(theRdg.getId(), ourRdg.getId());
        assertEquals(theRdg.getText(), ourRdg.getText());

        // Now choose a reading ID that doesn't exist in the list
        AtomicLong badRdgId = new AtomicLong(Long.parseLong(theRdg.getId()));
        while (sectRdgs.stream().anyMatch(x -> x.getId().equals(String.valueOf(badRdgId.get())))) {
            badRdgId.set((long) (Math.random() * 1000));
        }
        jerseyResponse = jerseyTest.target("/tradition/" + tradId + "/section/" + newSectId + "/reading/" + badRdgId.get())
                .request()
                .get();
        // LATER make this return something useful
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), jerseyResponse.getStatus());
    }

    public void testSectionWitnesses() {
        List<SectionModel> tSections = jerseyTest.target("/tradition/" + tradId + "/sections")
                .request()
                .get(new GenericType<>() {});
        String sectId = tSections.get(0).getId();

        List<WitnessModel> sectWits = jerseyTest.target("/tradition/"  + tradId + "/section/" + sectId + "/witnesses")
                .request()
                .get(new GenericType<>() {});
        assertEquals(37, sectWits.size());
    }

    public void testAddGraphmlSectionWithWitnesses() {
        String newSectId = Util.getValueFromJson(Util.addSectionToTradition(jerseyTest, tradId, "src/TestFiles/lf2_graphml.xml",
                "graphmlsingle", "section 2"), "sectionId");

        List<SectionModel> tSections = jerseyTest.target("/tradition/" + tradId + "/sections")
                .request()
                .get(new GenericType<>() {});
        assertEquals(2, tSections.size());
        assertEquals("section 2", tSections.get(1).getName());

        String aText = "quasi duobus magnis luminaribus populus terre illius ad veri dei noticiam & cultum magis " +
                "magisque illustrabatur iugiter ac informabatur Sanctus autem";
        Response jerseyResponse = jerseyTest
                .target("/tradition/" + tradId + "/section/" + newSectId + "/witness/A/text")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        String witFragment = Util.getValueFromJson(jerseyResponse, "text");
        assertEquals(aText, witFragment);

        // Check that our witnesses were not duplicated
        try (Transaction tx = db.beginTx()) {
            Node witV = db.findNode(Nodes.WITNESS, "sigil", "V");
            tx.success();
            assertNotNull(witV);
        } catch (MultipleFoundException e) {
            fail();
        }
    }

    public void testDeleteSection() {
        List<ReadingModel> tReadings = jerseyTest.target("/tradition/" + tradId + "/readings")
                .request()
                .get(new GenericType<>() {});
        assertEquals(30, tReadings.size());

        Util.addSectionToTradition(jerseyTest, tradId, "src/TestFiles/lf2.xml", "stemmaweb", "section 2");
        tReadings = jerseyTest.target("/tradition/" + tradId + "/readings")
                .request()
                .get(new GenericType<>() {});
        assertEquals(77, tReadings.size());

        List<SectionModel> tSections = jerseyTest.target("/tradition/" + tradId + "/sections")
                .request()
                .get(new GenericType<>() {});
        SectionModel firstSection = tSections.get(0);

        Response jerseyResult = jerseyTest
                .target("/tradition/" + tradId + "/section/" + firstSection.getId())
                .request(MediaType.APPLICATION_JSON)
                .delete();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());

        tReadings = jerseyTest.target("/tradition/" + tradId + "/readings")
                .request()
                .get(new GenericType<>() {});
        assertEquals(47, tReadings.size());
    }

    // test ordering of sections
    public void testSectionOrdering() {
        List<String> florIds = Util.importFlorilegium(jerseyTest);
        String florId = florIds.remove(0);
        // Test that we get the sections back in the correct order
        List<SectionModel> returnedSections = jerseyTest
                .target("/tradition/" + florId + "/sections")
                .request()
                .get(new GenericType<>() {});
        assertEquals(4, returnedSections.size());
        ArrayList<String> expectedSections = new ArrayList<>(Arrays.asList("DEFAULT", "part 1", "part 2", "part 3"));
        ArrayList<String> returnedIds = new ArrayList<>();
        returnedSections.forEach(x -> returnedIds.add(x.getName()));
        assertEquals(expectedSections, returnedIds);

        // Test that we get the text we expect for a witness across sections
        String bText = "τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ λόγος " +
                "ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν. Ὄψις " +
                "γυναικὸς πεφαρμακευμένον βέλος ἐστὶ ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον χρονίζει, " +
                "πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ διὰ τοῦ " +
                "τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, καὶ μὴ " +
                "δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. θάλπει ἑστῶσα βοτάνη παρ᾽ ὕδατι, καὶ πάθος ἀκολασίας, ἐν " +
                "συντυχίαις γυναικῶν. τοῦ Χρυσοστόμου Τοὺς ἐν τῇ πόλει βλασφημοῦντας, σωφρόνιζε. Κἂν ἀκούσῃς τινὸς " +
                "ἐν ἀμφόδῳ ἢ ἐν ὁδῶ ἢ ἐν ἀγορᾷ βλασφημοῦντος τὸν Θεόν, πρόσελθε, ἐπιτίμησον, κἂν πληγὰς ἐπιθεῖναι " +
                "δέῃ, μὴ παραιτήσῃ ῥάπισον αὐτοῦ τὴν ὄψιν, σύντριψον αὐτοῦ τὸ στόμα, ἁγίασόν σου τὴν χεῖρα διὰ τῆς " +
                "πληγῆς, κἂν ἐγκαλῶσι τινές, κὰν εἰς δικαστήριον ἕλκωσιν, ἀκολούθησον.";
        Response jerseyResponse = jerseyTest
                .target("/tradition/" + florId + "/witness/B/text")
                .request()
                .get();
        String wit = Util.getValueFromJson(jerseyResponse, "text");
        assertEquals(bText, wit);
    }

    public void testDeleteSectionMiddle() {
        List<String> florIds = Util.importFlorilegium(jerseyTest);
        String florId = florIds.remove(0);
        Response jerseyResult = jerseyTest
                .target("/tradition/" + florId + "/section/" + florIds.get(1))
                .request(MediaType.APPLICATION_JSON)
                .delete();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        String bText = "Ὄψις γυναικὸς πεφαρμακευμένον βέλος ἐστὶ ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον " +
                "χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ " +
                "διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, " +
                "καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. θάλπει ἑστῶσα βοτάνη παρ᾽ ὕδατι, καὶ πάθος " +
                "ἀκολασίας, ἐν συντυχίαις γυναικῶν. τοῦ Χρυσοστόμου Τοὺς ἐν τῇ πόλει βλασφημοῦντας, σωφρόνιζε. Κἂν " +
                "ἀκούσῃς τινὸς ἐν ἀμφόδῳ ἢ ἐν ὁδῶ ἢ ἐν ἀγορᾷ βλασφημοῦντος τὸν Θεόν, πρόσελθε, ἐπιτίμησον, κἂν " +
                "πληγὰς ἐπιθεῖναι δέῃ, μὴ παραιτήσῃ ῥάπισον αὐτοῦ τὴν ὄψιν, σύντριψον αὐτοῦ τὸ στόμα, ἁγίασόν σου " +
                "τὴν χεῖρα διὰ τῆς πληγῆς, κἂν ἐγκαλῶσι τινές, κὰν εἰς δικαστήριον ἕλκωσιν, ἀκολούθησον.";
        Response jerseyResponse = jerseyTest
                .target("/tradition/" + florId + "/witness/B/text")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        String wit = Util.getValueFromJson(jerseyResponse, "text");
        assertEquals(bText, wit);
    }

    public void testReorderSections() {
        List<String> florIds = Util.importFlorilegium(jerseyTest);
        String florId = florIds.remove(0);
        String reorderPath = "/section/" + florIds.get(1)
                + "/orderAfter/" + florIds.get(2);
        String bBefore = "Ὄψις γυναικὸς πεφαρμακευμένον βέλος ἐστὶ ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον " +
                "χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ " +
                "διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, " +
                "καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. θάλπει ἑστῶσα βοτάνη παρ᾽ ὕδατι, καὶ πάθος " +
                "ἀκολασίας, ἐν συντυχίαις γυναικῶν. τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο χαλεπὴν τοῖς " +
                "τοιούτοις ἀπειλὴν ὁ λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου " +
                "βλασφημεῖται ἐν τοῖς ἔθνεσιν. τοῦ Χρυσοστόμου Τοὺς ἐν τῇ πόλει βλασφημοῦντας, σωφρόνιζε. Κἂν " +
                "ἀκούσῃς τινὸς ἐν ἀμφόδῳ ἢ ἐν ὁδῶ ἢ ἐν ἀγορᾷ βλασφημοῦντος τὸν Θεόν, πρόσελθε, ἐπιτίμησον, κἂν " +
                "πληγὰς ἐπιθεῖναι δέῃ, μὴ παραιτήσῃ ῥάπισον αὐτοῦ τὴν ὄψιν, σύντριψον αὐτοῦ τὸ στόμα, ἁγίασόν σου " +
                "τὴν χεῖρα διὰ τῆς πληγῆς, κἂν ἐγκαλῶσι τινές, κὰν εἰς δικαστήριον ἕλκωσιν, ἀκολούθησον.";
        assertEquals(bBefore, _section_reorder_sequence(florId, reorderPath));

        // Now try reordering a section to be first
        String moveFirstPath = "/section/" + florIds.get(3) + "/orderAfter/none";
        String bAfter = "τοῦ Χρυσοστόμου Τοὺς ἐν τῇ πόλει βλασφημοῦντας, σωφρόνιζε. Κἂν ἀκούσῃς τινὸς ἐν ἀμφόδῳ ἢ ἐν " +
                "ὁδῶ ἢ ἐν ἀγορᾷ βλασφημοῦντος τὸν Θεόν, πρόσελθε, ἐπιτίμησον, κἂν πληγὰς ἐπιθεῖναι δέῃ, μὴ παραιτήσῃ " +
                "ῥάπισον αὐτοῦ τὴν ὄψιν, σύντριψον αὐτοῦ τὸ στόμα, ἁγίασόν σου τὴν χεῖρα διὰ τῆς πληγῆς, κἂν " +
                "ἐγκαλῶσι τινές, κὰν εἰς δικαστήριον ἕλκωσιν, ἀκολούθησον. Ὄψις γυναικὸς πεφαρμακευμένον βέλος ἐστὶ " +
                "ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον " +
                "γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν " +
                "ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. " +
                "θάλπει ἑστῶσα βοτάνη παρ᾽ ὕδατι, καὶ πάθος ἀκολασίας, ἐν συντυχίαις γυναικῶν. τὸ ὄνομά μου " +
                "βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ λόγος ἐπανατείνεται λέγων " +
                "ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν.";
        assertEquals(bAfter, _section_reorder_sequence(florId, moveFirstPath));

        // Make sure it is idempotent by doing it all again
        assertEquals(bAfter, _section_reorder_sequence(florId, reorderPath));
        assertEquals(bAfter, _section_reorder_sequence(florId, moveFirstPath));
    }

    private String _section_reorder_sequence(String traditionId, String reorderPath) {
        Response jerseyResult = jerseyTest
                .target("/tradition/" + traditionId + reorderPath)
                .request()
                .put(Entity.json(""));
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        jerseyResult = jerseyTest
                .target("/tradition/" + traditionId + "/witness/B/text")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        return Util.getValueFromJson(jerseyResult, "text");
    }

    public void testSectionWrongTradition () {
        List<String> florIds = Util.importFlorilegium(jerseyTest);
        String florId = florIds.remove(0);
        String newSectId = Util.getValueFromJson(Util.addSectionToTradition(jerseyTest, tradId,
                "src/TestFiles/lf2.xml", "stemmaweb", "section 2"), "parentId");
        Response jerseyResult = jerseyTest
                .target("/tradition/" + florId + "/section/" + newSectId + "/witness/A")
                .request()
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), jerseyResult.getStatus());
    }

    public void testSectionOrderAfterSelf () {
        List<String> florIds = Util.importFlorilegium(jerseyTest);
        String florId = florIds.remove(0);
        String tryReorder = florIds.get(2);
        Response jerseyResult = jerseyTest
                .target("/tradition/" + florId + "/section/" + tryReorder + "/orderAfter/" + tryReorder)
                .request()
                .put(Entity.text(""));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), jerseyResult.getStatus());
        String errMsg = jerseyResult.readEntity(String.class);
        assertEquals("Cannot reorder a section after itself", errMsg);
    }

    public void testMergeSections() {
        List<String> florIds = Util.importFlorilegium(jerseyTest);
        String florId = florIds.remove(0);

        // Try merge of 3 into 4
        String targetSection = florIds.get(2);
        String requestPath = "/tradition/" + florId + "/section/" + targetSection
                + "/merge/" + florIds.get(3);
        Response jerseyResponse = jerseyTest
                .target(requestPath)
                .request()
                .post(null);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());

        String pText = "Ὄψις γυναικὸς πεφαρμακευμένον βέλος ἐστὶ ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον " +
                "χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ " +
                "διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, " +
                "καὶ μὴ δῷς αὐτῆς παρρησίαν θαρρῆσαι σοί ποτε. θάλπει ἑστῶσα βοτάνη παρ᾽ ὕδατι, καὶ πάθος ἀκολασίας, " +
                "ἐν συντυχίαις γυναικῶν. τοῦ Χρυσοστόμου Τοὺς ἐν τῇ πόλει βλασφημοῦντας, σωφρόνιζε. Κἂν ἀκούσῃς " +
                "τινὸς ἐν ἀμφόδῳ ἢ ἐν ὁδῶ ἢ ἐν ἀγορᾷ βλασφημοῦντος τὸν Θεόν, πρόσελθε, ἐπιτίμησον, κἂν πληγὰς " +
                "ἐπιθεῖναι δέῃ, μὴ παραιτήσῃ ῥάπισον αὐτοῦ τὴν ὄψιν, σύντριψον αὐτοῦ τὸ στόμα, ἁγίασόν σου τὴν χεῖρα " +
                "διὰ τῆς πληγῆς, κἂν ἐγκαλῶσι τινές, κὰν εἰς δικαστήριον ἕλκωσιν, ἀκολούθησον.";
        jerseyResponse = jerseyTest
                .target("/tradition/" + florId + "/section/" + targetSection + "/witness/P/text")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        String witFragment = Util.getValueFromJson(jerseyResponse, "text");
        assertEquals(pText, witFragment);

        // Also test a witness that didn't exist in section 3
        String dText = "Ὄψις γυναικὸς βέλος ἐστὶ πεφαρμακευμένον ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον " +
                "χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ " +
                "διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, " +
                "καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. Θάλλει βοτάνη ἑστῶσα παρ᾽ ὕδατι, καὶ πάθος " +
                "ἀκολασίας, ἐν συντυχίαις γυναικῶν.";
        jerseyResponse = jerseyTest
                .target("/tradition/" + florId + "/section/" + targetSection + "/witness/D/text")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        witFragment = Util.getValueFromJson(jerseyResponse, "text");
        assertEquals(dText, witFragment);

        // Now try merge of 1 into 3, which should fail
        requestPath = "/tradition/" + florId + "/section/" + florIds.get(0)
                + "/merge/" + targetSection;
        jerseyResponse = jerseyTest
                .target(requestPath)
                .request()
                .post(null);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), jerseyResponse.getStatus());

        // Now try merge of 2 into 1
        targetSection = florIds.get(0);
        requestPath = "/tradition/" + florId + "/section/" + florIds.get(1) + "/merge/" + targetSection;
        jerseyResponse = jerseyTest.target(requestPath).request().post(null);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        assertEquals(2, jerseyTest
                .target("/tradition/" + florId + "/sections")
                .request()
                .get(new GenericType<List<SectionModel>>() {}).size());

        pText = "Μαξίμου περὶ τῆς τοῦ ἁγίου πνεύματος βλασφημίας ἀπορία αὐτόθι ἔχειν τὴν λύσιν· ὁ δὲ δεύτερος ἐστὶν " +
                "οὗτος· ὅτάν τις ἐν ἁμαρτίαις ἐνεχόμενος, ἀκούων δὲ τοῦ κυρίου λέγοντος μὴ κρίνετε φοβούμενος οὐδένα " +
                "κρίνῃ ἐν τῇ ἐξετάσει τῶν βεβιωμένων ὡς φύλαξ τῆς ἐντολῆς οὐ κρίνεται· εἰ μὴ τὸ γενέσθαι πιστόν, " +
                "εἰκότως ὅταν ἐν ἁμαρτίαις τίς ὢν οἰκονομῆται ἐκ τῆς προνοίας ἐν συμφοραῖς, ἐν ἀνάγκαις, ἐν νόσοις " +
                "ὡς οὐκ οἶδε γὰρ διὰ τῶν τοιούτων καθαίρει αὐτὸν ὁ θεός οὖν τῷ ἐν ἀπιστεία τὸν βίον κατακλείσαντι " +
                "οὔτε ἐνταῦθα οὔτε ἐν τῷ μέλλοντι ἀφεθήσεται τῆς ἀπιστίας καὶ ἀθεΐας ἡ ἁμαρτία. Ἰσιδώρου " +
                "πηλουσιώτ(ου) Γρηγορίου Νύσης Ἤκουσά που τῆς ἁγίας γραφῆς κατακρινούσης ἐκείνους, οἳ κατὰ τῆς τοῦ " +
                "θεοῦ βλασφημίας αἴτιοι γίνονται. Οὐαὶ γὰρ φησὶν δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. " +
                "Διὰ τοῦτο χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς " +
                "τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν.";
        jerseyResponse = jerseyTest
                .target("/tradition/" + florId + "/section/" + targetSection + "/witness/P/text")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        witFragment = Util.getValueFromJson(jerseyResponse, "text");
        assertEquals(pText, witFragment);

        // Also test a witness that didn't exist in section 2
        String kText = "Μαξίμου Ἡ περὶ τῆς τοῦ πνεύματος τοῦ ἁγίου βλασφημίας ἀπορία αὐτόθεν ἔχει τὴν λύσιν· ὁ δὲ " +
                "δεύτερος ἐστὶν οὗτος· ὅτάν τις ἐν ἁμαρτίαις ἐνεχόμενος, ἀκούων δὲ τοῦ κυρίου λέγοντος μὴ κρίνετε " +
                "φοβούμενος οὐδένα κρίνει ἐν τῇ ἐξετάσει τῶν βεβιωμένων ὡς φύλαξ τῆς ἐντολῆς οὐ κρίνεται· εἰ μὴ τὸ " +
                "γενέσθαι πιστόν, εἰκότως ὅταν ἐν ἁμαρτίαις τίς ὢν οἰκονομῆται ἐκ τῆς προνοίας ἐν συμφοραῖς, ἐν " +
                "ἀνάγκαις, ἐν νόσοις ὡς οὐκ οἶδε γὰρ διὰ τῶν τοιούτων καθαίρει αὐτὸν ὁ θεός τῶν ἐν ἀπιστίᾳ τὸν βίον " +
                "κατακλείσαντι οὔτε ἐνταῦθα οὔτε ἐν τῷ μέλλοντι ἀφεθήσεται τῆς ἀπιστίας καὶ ἀθεΐας ἡ ἁμαρτία.";
        jerseyResponse = jerseyTest
                .target("/tradition/" + florId + "/section/" + targetSection + "/witness/K/text")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        witFragment = Util.getValueFromJson(jerseyResponse, "text");
        assertEquals(kText, witFragment);
    }

    // Test merge of tradition sections with layered readings

    public void testSplitSection() {
        List<String> florIds = Util.importFlorilegium(jerseyTest);
        String florId = florIds.remove(0);

        String targetSectionId = florIds.get(1);
        SectionModel origSection = jerseyTest
                .target("/tradition/" + florId + "/section/" + targetSectionId)
                .request()
                .get(SectionModel.class);

        // Get the reading to split at
        ReadingModel targetReading = jerseyTest
                .target("/tradition/" + florId + "/witness/B/readings")
                .request()
                .get(new GenericType<List<ReadingModel>>() {}).get(0);
        assertEquals("τὸ", targetReading.getText());

        // Do the split
        String splitPath = "/tradition/" + florId + "/section/" + targetSectionId
                + "/splitAtRank/" + targetReading.getRank();
        Response jerseyResult = jerseyTest
                .target(splitPath)
                .request(MediaType.APPLICATION_JSON)
                .post(null);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        String newSectionId = Util.getValueFromJson(jerseyResult, "sectionId");

        // Get the data for both new sections
        SectionModel shortenedSection = jerseyTest
                .target("/tradition/" + florId + "/section/" + targetSectionId)
                .request()
                .get(SectionModel.class);
        // Check that the new section has the right ending rank
        assertEquals(targetReading.getRank(), shortenedSection.getEndRank());

        SectionModel newSection = jerseyTest
                .target("/tradition/" + florId + "/section/" + newSectionId)
                .request()
                .get(SectionModel.class);
        // Check that the new section has the right ending rank
        assertEquals(Long.valueOf(origSection.getEndRank() - targetReading.getRank() + 1), newSection.getEndRank());

        // Check that superfluous witness B has been removed from the first half
        Response jerseyResponse = jerseyTest
                .target("/tradition/" + florId + "/section/" + targetSectionId + "/witness/B/text")
                .request()
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), jerseyResponse.getStatus());

        // Check that the second half contains witness B
        jerseyResponse = jerseyTest
                .target("/tradition/" + florId + "/section/" + newSectionId + "/witness/B/text")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        assertEquals("τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ " +
                "λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν.",
                Util.getValueFromJson(jerseyResponse, "text"));

        // Check that the first half can be exported to dot
        jerseyResponse = jerseyTest
                .target("/tradition/" + florId + "/section/" + targetSectionId + "/dot")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        // ...and that there is only one start and end node each
        String dotText = jerseyResponse.readEntity(String.class);
        assertEquals(1, countOccurrences(dotText, "__END__"));
        assertEquals(1, countOccurrences(dotText, "__START__"));

        // Check that the second half can be exported to dot
        jerseyResponse = jerseyTest
                .target("/tradition/" + florId + "/section/" + newSectionId + "/dot")
                .request()
                .get(Response.class);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        dotText = jerseyResponse.readEntity(String.class);
        assertEquals(1, countOccurrences(dotText, "__END__"));
        assertEquals(1, countOccurrences(dotText, "__START__"));


        // Check that the second half has readings on rank 1
        List<ReadingModel> part2rdgs = jerseyTest
                .target("/tradition/" + florId + "/section/" + newSectionId + "/readings")
                .request()
                .get(new GenericType<>() {});
        assertTrue(part2rdgs.stream().anyMatch(x -> x.getRank().equals(1L)));

        // Check that the first half's end rank correct
        List<ReadingModel> part1rdgs = jerseyTest
                .target("/tradition/" + florId + "/section/" + targetSectionId + "/readings")
                .request()
                .get(new GenericType<>() {});
        Optional<ReadingModel> oFirstEnd = part1rdgs.stream().filter(ReadingModel::getIs_end).findFirst();
        assertTrue(oFirstEnd.isPresent());
        ReadingModel firstEnd = oFirstEnd.get();
        assertEquals(targetReading.getRank(), firstEnd.getRank());

        // Check that the second half's end rank is correct
        Optional<ReadingModel> oSecondEnd = part2rdgs.stream().filter(ReadingModel::getIs_end).findFirst();
        assertTrue(oSecondEnd.isPresent());
        ReadingModel secondEnd = oSecondEnd.get();
        assertEquals(Long.valueOf(origSection.getEndRank() - targetReading.getRank() + 1), secondEnd.getRank());

        // Check that the final nodes are all connected to END
        List<WitnessModel> firstSectWits = jerseyTest
                .target("/tradition/" + florId + "/section/" + targetSectionId + "/witnesses")
                .request()
                .get(new GenericType<>() {});
        for (WitnessModel wit : firstSectWits) {
            Response connectedTest = jerseyTest
                    .target("/tradition/" + florId + "/section/" + targetSectionId + "/witness/" + wit.getSigil() + "/text")
                    .request()
                    .get();
            assertEquals(Response.Status.OK.getStatusCode(), connectedTest.getStatus());
        }

        // Check that the respective START and END nodes belong to the right sections

        // Check that the whole B text is still valid
        String bText = "τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ λόγος " +
                "ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν. Ὄψις " +
                "γυναικὸς πεφαρμακευμένον βέλος ἐστὶ ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον χρονίζει, " +
                "πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ διὰ τοῦ " +
                "τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, καὶ μὴ " +
                "δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. θάλπει ἑστῶσα βοτάνη παρ᾽ ὕδατι, καὶ πάθος ἀκολασίας, ἐν " +
                "συντυχίαις γυναικῶν. τοῦ Χρυσοστόμου Τοὺς ἐν τῇ πόλει βλασφημοῦντας, σωφρόνιζε. Κἂν ἀκούσῃς τινὸς " +
                "ἐν ἀμφόδῳ ἢ ἐν ὁδῶ ἢ ἐν ἀγορᾷ βλασφημοῦντος τὸν Θεόν, πρόσελθε, ἐπιτίμησον, κἂν πληγὰς ἐπιθεῖναι " +
                "δέῃ, μὴ παραιτήσῃ ῥάπισον αὐτοῦ τὴν ὄψιν, σύντριψον αὐτοῦ τὸ στόμα, ἁγίασόν σου τὴν χεῖρα διὰ τῆς " +
                "πληγῆς, κἂν ἐγκαλῶσι τινές, κὰν εἰς δικαστήριον ἕλκωσιν, ἀκολούθησον.";
        jerseyResponse = jerseyTest
                .target("/tradition/" + florId + "/witness/B/text")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        assertEquals(bText, Util.getValueFromJson(jerseyResponse, "text"));
    }

    public void testSplitSectionWithLemma() {
        // Use the Florilegium
        List<String> florIds = Util.importFlorilegium(jerseyTest);
        String florId = florIds.remove(0);
        String flor3 = florIds.get(2);

        // Lemmatize section 3 based on majority reading
        try (Transaction tx = db.beginTx()) {
            Node sect3 = db.getNodeById(Long.parseLong(flor3));
            for (Node r : VariantGraphService.calculateMajorityText(sect3)) {
                if (r.hasProperty("is_start") || r.hasProperty("is_end"))
                    continue;
                r.setProperty("is_lemma", true);
            }
            tx.success();
        }

        Response r = jerseyTest.target("/tradition/" + florId + "/section/" + flor3 + "/setlemma")
                .request(MediaType.APPLICATION_JSON)
                .post(null);
        assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());

        // Now try splitting the section at the Θάλλει
        r = jerseyTest.target("/tradition/" + florId + "/section/" + flor3 + "/splitAtRank/54")
                .request(MediaType.APPLICATION_JSON).post(null);
        assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
        // Before the split there should be no readings with rank >= endRank
        SectionModel sm = jerseyTest.target("/tradition/" + florId + "/section/" + flor3)
                .request().get(SectionModel.class);
        assertEquals(Long.valueOf(54), sm.getEndRank());
        List<ReadingModel> rdgs = jerseyTest.target("/tradition/" + florId + "/section/" + flor3 + "/readings")
                .request().get(new GenericType<>() {});
        for (ReadingModel rm : rdgs)
            if (!rm.getIs_end())
                assertTrue(rm.getRank() < sm.getEndRank());
        // Check the dot output too
        String dotOutput = jerseyTest.target("/tradition/" + florId + "/section/" + flor3 + "/dot")
                .request().get(String.class);
        assertFalse(dotOutput.contains("βοτάνη"));
    }


    public void testSplitSectionMatthew() {
        Response jerseyResponse = Util.createTraditionFromFileOrString(jerseyTest, "Legend", "LR",
                "user@example.com", "src/TestFiles/ChronicleOfMatthew.xml", "stemmaweb");
        String mattId = Util.getValueFromJson(jerseyResponse, "tradId");
        List<SectionModel> returnedSections = jerseyTest
                .target("/tradition/" + mattId + "/sections")
                .request()
                .get(new GenericType<>() {});

        SectionModel origSection = returnedSections.get(0);
        String targetSectionId = origSection.getId();

        // Do the split
        String splitPath = "/tradition/" + mattId + "/section/" + targetSectionId
                + "/splitAtRank/168";
        jerseyResponse = jerseyTest
                .target(splitPath)
                .request(MediaType.APPLICATION_JSON)
                .post(null);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        String newSectionId = Util.getValueFromJson(jerseyResponse, "sectionId");
        // Check that the first half can be exported to dot
        jerseyResponse = jerseyTest
                .target("/tradition/" + mattId + "/section/" + targetSectionId + "/dot")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        // ...and that there is only one start and end node each
        String dotText = jerseyResponse.readEntity(String.class);
        assertEquals(1, countOccurrences(dotText, "__END__"));
        assertEquals(1, countOccurrences(dotText, "__START__"));

        // Check that the second half can be exported to dot
        jerseyResponse = jerseyTest
                .target("/tradition/" + mattId + "/section/" + newSectionId + "/dot")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        dotText = jerseyResponse.readEntity(String.class);
        assertEquals(1, countOccurrences(dotText, "__END__"));
        assertEquals(1, countOccurrences(dotText, "__START__"));


        // Check that the second half has readings on rank 1
        List<ReadingModel> part2rdgs = jerseyTest
                .target("/tradition/" + mattId + "/section/" + newSectionId + "/readings")
                .request()
                .get(new GenericType<>() {});
        assertTrue(part2rdgs.stream().anyMatch(x -> x.getRank().equals(1L)));

        // Check that the first half's end rank correct
        List<ReadingModel> part1rdgs = jerseyTest
                .target("/tradition/" + mattId + "/section/" + targetSectionId + "/readings")
                .request()
                .get(new GenericType<>() {});
        Optional<ReadingModel> ofe = part1rdgs.stream().filter(ReadingModel::getIs_end).findFirst();
        assertTrue(ofe.isPresent());
        ReadingModel firstEnd = ofe.get();
        assertEquals(Long.valueOf(168), firstEnd.getRank());

        // Check that the second half's end rank is correct
        Optional<ReadingModel> ose = part2rdgs.stream().filter(ReadingModel::getIs_end).findFirst();
        assertTrue(ose.isPresent());
        ReadingModel secondEnd = ose.get();
        assertEquals(Long.valueOf(origSection.getEndRank() - 168 + 1), secondEnd.getRank());

        // Check that the final nodes are all connected to END
        List<WitnessModel> firstSectWits = jerseyTest
                .target("/tradition/" + mattId + "/section/" + targetSectionId + "/witnesses")
                .request()
                .get(new GenericType<>() {});
        for (WitnessModel wit : firstSectWits) {
            Response connectedTest = jerseyTest
                    .target("/tradition/" + mattId + "/section/" + targetSectionId + "/witness/" + wit.getSigil() + "/text")
                    .request()
                    .get();
            assertEquals(Response.Status.OK.getStatusCode(), connectedTest.getStatus());
            // Check our lacunose texts
            if (wit.getSigil().equals("C") || wit.getSigil().equals("E") || wit.getSigil().equals("G") ) {
                assertEquals("", Util.getValueFromJson(connectedTest, "text"));
                checkLacunaOnly(wit.getSigil(), mattId, targetSectionId);
                checkLacunaOnly(wit.getSigil(), mattId, newSectionId);
            }
            if (wit.getSigil().equals("H") || wit.getSigil().equals("L")) {
                assertEquals("", Util.getValueFromJson(connectedTest, "text"));
                checkLacunaOnly(wit.getSigil(), mattId, targetSectionId);
            }
        }
    }

    private void checkLacunaOnly(String sigil, String tid, String sid) {
        List<ReadingModel> readingList = jerseyTest
                .target("/tradition/" + tid + "/section/" + sid + "/witness/" + sigil + "/readings")
                .request()
                .get(new GenericType<>() {});
        assertEquals(1, readingList.size());
        assertTrue(readingList.get(0).getIs_lacuna());
    }

    private static int countOccurrences (String tstr, String substr) {
        int lastindex = 0;
        int cnt = 0;
        while (lastindex > -1) {
            lastindex = tstr.indexOf(substr, lastindex + 1);
            if (lastindex > -1)
                cnt++;
        }
        return cnt;
    }

    public void testEmendation() {
        // Get the section ID
        List<SectionModel> tradSections = jerseyTest
                .target("/tradition/" + tradId + "/sections")
                .request()
                .get(new GenericType<>() {});
        String sectId = tradSections.get(0).getId();

        // Propose an emendation for the wrong ranks
        ProposedEmendationModel pem = new ProposedEmendationModel();
        pem.setAuthority("H. Granger");
        pem.setText("alohomora");
        pem.setFromRank(10L);
        pem.setToRank(12L);

        // Try to set it
        Response response = jerseyTest
                .target("/tradition/" + tradId + "/section/" + sectId + "/emend")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(pem));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());

        // Now for the right ranks
        pem.setFromRank(4L);
        pem.setToRank(6L);
        response = jerseyTest
                .target("/tradition/" + tradId + "/section/" + sectId + "/emend")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(pem));
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        // Check the reading itself
        GraphModel firstResult = response.readEntity(GraphModel.class);
        assertEquals(1, firstResult.getReadings().size());
        assertTrue(firstResult.getRelations().isEmpty());
        ReadingModel emendation = firstResult.getReadings().iterator().next();
        String emendId = emendation.getId();
        assertEquals("alohomora", emendation.getText());
        assertEquals("H. Granger", emendation.getAuthority());
        assertTrue(emendation.getIs_emendation());
        assertEquals(Long.valueOf(4), emendation.getRank());
        // Check its links
        assertEquals(12, firstResult.getSequences().size());
        assertEquals(7, firstResult.getSequences().stream().filter(x -> x.getTarget().equals(emendId)).count());
        assertEquals(5, firstResult.getSequences().stream().filter(x -> x.getSource().equals(emendId)).count());
        for (SequenceModel link : firstResult.getSequences()) {
            assertEquals("EMENDED", link.getType());
        }

        // Now set a new emendation that is zero-width
        pem.setText("Petrificus totalus");
        pem.setFromRank(10L);
        pem.setToRank(10L);
        response = jerseyTest
                .target("/tradition/" + tradId + "/section/" + sectId + "/emend")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(pem));
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        GraphModel secondResult = response.readEntity(GraphModel.class);
        assertEquals(1, secondResult.getReadings().size());
        emendation = secondResult.getReadings().iterator().next();
        assertEquals(Long.valueOf(10), emendation.getRank());
        assertTrue(secondResult.getRelations().isEmpty());
        assertEquals(2, secondResult.getSequences().size());
        for (SequenceModel link : secondResult.getSequences()) {
            ReadingModel otherReading;
            if (link.getSource().equals(emendation.getId())) {
                otherReading = jerseyTest.target("/reading/" + link.getTarget())
                        .request()
                        .get(ReadingModel.class);
                assertTrue(otherReading.getIs_end());
                assertEquals(Long.valueOf(11), otherReading.getRank());
            } else {
                otherReading = jerseyTest.target("/reading/" + link.getSource())
                        .request()
                        .get(ReadingModel.class);
                assertFalse(otherReading.getIs_end());
                assertEquals(Long.valueOf(9), otherReading.getRank());
                assertEquals("oriundus", otherReading.getText());
            }
        }

        // Check that we can retrieve these emendations
        response = jerseyTest.target("/tradition/" + tradId + "/section/" + sectId + "/emendations")
                .request(MediaType.APPLICATION_JSON)
                .get(Response.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        GraphModel allEmendations = response.readEntity(GraphModel.class);
        // The contents of firstResult and secondResult should both be in here
        assertTrue(allEmendations.getReadings().stream().anyMatch(
                x -> x.getId().equals(firstResult.getReadings().iterator().next().getId())));
        assertTrue(allEmendations.getReadings().stream().anyMatch(
                x -> x.getId().equals(secondResult.getReadings().iterator().next().getId())));
        for (SequenceModel sm : firstResult.getSequences()) {
            assertTrue(allEmendations.getSequences().stream().anyMatch(
                    x -> x.getId().equals(sm.getId())
            ));
        }

        // Split the section and check that we can still retrieve each emendation
        response = jerseyTest.target("/tradition/" + tradId + "/section/" + sectId + "/splitAtRank/8")
                .request(MediaType.APPLICATION_JSON)
                .post(null);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        String newSection = Util.getValueFromJson(response, "sectionId");
        GraphModel oldResult = jerseyTest.target("/tradition/" + tradId + "/section/" + sectId + "/emendations")
                .request(MediaType.APPLICATION_JSON)
                .get(GraphModel.class);
        assertEquals(1, oldResult.getReadings().size());
        assertEquals(firstResult.getReadings().iterator().next().getId(), oldResult.getReadings().iterator().next().getId());
        assertEquals(firstResult.getSequences().size(), oldResult.getSequences().size());
        GraphModel newResult = jerseyTest.target("/tradition/" + tradId + "/section/" + newSection + "/emendations")
                .request(MediaType.APPLICATION_JSON)
                .get(GraphModel.class);
        assertEquals(1, newResult.getReadings().size());
        assertEquals(secondResult.getReadings().iterator().next().getId(), newResult.getReadings().iterator().next().getId());
        assertEquals(secondResult.getSequences().size(), newResult.getSequences().size());

        // Re-join the section and check that we can still retrieve each emendation
        response = jerseyTest.target(("/tradition/" + tradId + "/section/" + sectId + "/merge/" + newSection))
                .request()
                .post(Entity.json(""));
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        newResult = jerseyTest.target("/tradition/" + tradId + "/section/" + sectId + "/emendations")
                .request(MediaType.APPLICATION_JSON)
                .get(GraphModel.class);
        assertEquals(2, newResult.getReadings().size());
    }

    public void testRelatedClusters() {
        List<SectionModel> tradSections = jerseyTest
                .target("/tradition/" + tradId + "/sections")
                .request()
                .get(new GenericType<>() {});
        assertEquals(1, tradSections.size());
        List<List<ReadingModel>> pathClusters = jerseyTest
                .target("/tradition/" + tradId + "/section/" + tradSections.get(0).getId() + "/colocated")
                .request()
                .get(new GenericType<>() {});
        assertEquals(5, pathClusters.size());
    }

    public void testLemmaText() {
        String newSectId = Util.getValueFromJson(Util.addSectionToTradition(jerseyTest, tradId, "src/TestFiles/lf2.xml",
                "stemmaweb", "section 2"), "sectionId");

        // First check before any lemma readings are set
        Response jerseyResult = jerseyTest
                .target("/tradition/" + tradId + "/section/" + newSectId + "/lemmatext")
                .queryParam("final", "true")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        String lemmaText = Util.getValueFromJson(jerseyResult, "text");
        assertEquals("", lemmaText);

        jerseyResult = jerseyTest
                .target("/tradition/" + tradId + "/section/" + newSectId + "/lemmareadings")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        List<ReadingModel> lemmaReadings = jerseyResult.readEntity(new GenericType<>() {});
        assertEquals(0, lemmaReadings.size());

        // Now set some lemma readings
        List<ReadingModel> allReadings = jerseyTest
                .target("/tradition/" + tradId + "/section/" + newSectId + "/readings")
                .request()
                .get(new GenericType<>() {});
        HashMap<String, String> readingLookup = new HashMap<>();
        for (ReadingModel rm : allReadings) {
            String key = rm.getText() + "/" + rm.getRank().toString();
            readingLookup.put(key, rm.getId());
        }
        String[] lemmatised = new String[]{"quasi/1", "duobus/2", "magnis/3", "luminaribus/4", "populus/5",
                "terre/6", "illius/7", "ad/8", "dei/9", "veri/10", "noticiam/11", "et/12", "cultum/13",
                "magis/14", "illustrabatur/16", "jugiter/17", "ac/18", "informabatur/19", "Sanctus/20", "autem/21"};
        // Make the request data for lemmatising
        MultivaluedMap<String, String> lemmaParam = new MultivaluedHashMap<>();
        lemmaParam.add("value", "true");
        for (String rdg : lemmatised) {
            // Set normal forms for a few selected readings
            List<KeyPropertyModel> models = new ArrayList<>();
            // models.add(keyModel);
            if (rdg.contains("autem")) {
                KeyPropertyModel km = new KeyPropertyModel();
                km.setKey("normal_form");
                km.setProperty("autem.");
                models.add(km);
            } else if (rdg.contains("quasi")) {
                KeyPropertyModel km = new KeyPropertyModel();
                km.setKey("normal_form");
                km.setProperty("Quasi");
                models.add(km);
            }
            if (models.size() > 0) {
                ReadingChangePropertyModel chgModel = new ReadingChangePropertyModel();
                chgModel.setProperties(models);

                jerseyResult = jerseyTest.target("/reading/" + readingLookup.get(rdg))
                        .request(MediaType.APPLICATION_JSON)
                        .put(Entity.json(chgModel));
                assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
            }

            // Now lemmatise the reading, whatever it is
            jerseyResult = jerseyTest.target("/reading/" + readingLookup.get(rdg) + "/setlemma")
                    .request()
                    .post(Entity.entity(lemmaParam, MediaType.APPLICATION_FORM_URLENCODED));
            assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());

        }
        // Check that we still have no final lemma text
        jerseyResult = jerseyTest
                .target("/tradition/" + tradId + "/section/" + newSectId + "/lemmatext")
                .queryParam("final", "true")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        lemmaText = Util.getValueFromJson(jerseyResult, "text");
        assertEquals("", lemmaText);

        // but that we can get a provisional lemma text
        jerseyResult = jerseyTest
                .target("/tradition/" + tradId + "/section/" + newSectId + "/lemmatext")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        lemmaText = Util.getValueFromJson(jerseyResult, "text");
        assertEquals("Quasi duobus magnis luminaribus populus terre illius ad dei veri noticiam et " +
                "cultum magis [...] illustrabatur jugiter ac informabatur Sanctus autem.", lemmaText);

        // ...and all the lemma readings.
        jerseyResult = jerseyTest
                .target("/tradition/" + tradId + "/section/" + newSectId + "/lemmareadings")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        lemmaReadings = jerseyResult.readEntity(new GenericType<>() {});
        assertEquals(20, lemmaReadings.size());
        HashSet<String> inLemma = new HashSet<>();
        lemmaReadings.forEach(x -> inLemma.add(x.getId()));
        for (String rdg : lemmatised) {
            assertTrue(inLemma.contains(readingLookup.get(rdg)));
        }
        // and a subset thereof
        jerseyResult = jerseyTest.target("/tradition/" + tradId + "/section/" + newSectId + "/lemmareadings")
                .queryParam("startRank", "11")
                .queryParam("endRank", "13")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        lemmaReadings = jerseyResult.readEntity(new GenericType<>() {});
        assertEquals(3, lemmaReadings.size());
        for (String rdg : new String[] {"noticiam/11", "et/12", "cultum/13"}) {
            String rdgid = readingLookup.get(rdg);
            assertTrue(lemmaReadings.stream().anyMatch(x -> x.getId().equals(rdgid)));
        }

        // Now set/finalise the lemma text
        jerseyResult = jerseyTest
                .target("/tradition/" + tradId + "/section/" + newSectId + "/setlemma")
                .request()
                .post(null);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());

        // Check that we now have a lemma text
        jerseyResult = jerseyTest
                .target("/tradition/" + tradId + "/section/" + newSectId + "/lemmatext")
                .queryParam("final", "true")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        lemmaText = Util.getValueFromJson(jerseyResult, "text");
        assertEquals("Quasi duobus magnis luminaribus populus terre illius ad dei veri noticiam et " +
                "cultum magis illustrabatur jugiter ac informabatur Sanctus autem.", lemmaText);

        // Check that we get the right lemma readings back too
        jerseyResult = jerseyTest
                .target("/tradition/" + tradId + "/section/" + newSectId + "/lemmareadings")
                .queryParam("final", "true")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        lemmaReadings = jerseyResult.readEntity(new GenericType<>() {});
        assertEquals(20, lemmaReadings.size());
        HashSet<String> inFinalLemma = new HashSet<>();
        lemmaReadings.forEach(x -> inFinalLemma.add(x.getId()));
        for (String rdg : lemmatised) {
            assertTrue(inFinalLemma.contains(readingLookup.get(rdg)));
        }

        // Add a lemma on the same rank, check that the other one gets unset
        jerseyResult = jerseyTest.target("/reading/" + readingLookup.get("iugiter/17") + "/setlemma")
                .request()
                .post(Entity.entity(lemmaParam, MediaType.APPLICATION_FORM_URLENCODED));
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        List<ReadingModel> changed = jerseyResult.readEntity(new GenericType<>() {});
        assertEquals(2, changed.size());
        assertTrue(changed.stream().anyMatch(x -> x.getId().equals(readingLookup.get("iugiter/17")) && x.getIs_lemma()));
        assertTrue(changed.stream().anyMatch(x -> x.getId().equals(readingLookup.get("jugiter/17")) && !x.getIs_lemma()));

        // Official lemma text hasn't changed yet
        jerseyResult = jerseyTest
                .target("/tradition/" + tradId + "/section/" + newSectId + "/lemmatext")
                .queryParam("final", "true")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        lemmaText = Util.getValueFromJson(jerseyResult, "text");
        assertEquals("Quasi duobus magnis luminaribus populus terre illius ad dei veri noticiam et " +
                "cultum magis illustrabatur jugiter ac informabatur Sanctus autem.", lemmaText);
        // nor the subset containing the change
        jerseyResult = jerseyTest.target("/tradition/" + tradId + "/section/" + newSectId + "/lemmatext")
                .queryParam("final", "true")
                .queryParam("startRdg", readingLookup.get("magis/14"))
                .queryParam("endRdg", readingLookup.get("informabatur/19"))
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        lemmaText = Util.getValueFromJson(jerseyResult, "text");
        assertEquals("magis illustrabatur jugiter ac informabatur", lemmaText);


        // ...but we can see our changes in the effective lemma text
        jerseyResult = jerseyTest
                .target("/tradition/" + tradId + "/section/" + newSectId + "/lemmatext")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        lemmaText = Util.getValueFromJson(jerseyResult, "text");
        assertEquals("Quasi duobus magnis luminaribus populus terre illius ad dei veri noticiam et " +
                "cultum magis [...] illustrabatur iugiter ac informabatur Sanctus autem.", lemmaText);

        // Re-set the lemma text, check that it changes
        jerseyResult = jerseyTest
                .target("/tradition/" + tradId + "/section/" + newSectId + "/setlemma")
                .request()
                .post(null);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        jerseyResult = jerseyTest
                .target("/tradition/" + tradId + "/section/" + newSectId + "/lemmatext")
                .queryParam("final", "true")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        lemmaText = Util.getValueFromJson(jerseyResult, "text");
        assertEquals("Quasi duobus magnis luminaribus populus terre illius ad dei veri noticiam et " +
                "cultum magis illustrabatur iugiter ac informabatur Sanctus autem.", lemmaText);

    }

    public void testFetchSectionAnnotations() {
        // Set up some annotations across sections
        HashMap<String,String> stuffCreated = setupComplexAnnotation();

        // Now request the annotations for each section
        // Request only the immediate annotations on section 1
        Response response = jerseyTest
                .target("/tradition/" + tradId + "/section/" + stuffCreated.get("section1") + "/annotations")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<AnnotationModel> sectAnn = response.readEntity(new GenericType<>() {});
        assertEquals(1, sectAnn.size());
        assertTrue(sectAnn.stream().anyMatch(x -> x.getLabel().equals("PLACEREF")
                && x.getId().equals(stuffCreated.get("ref1"))));

        // Request the whole tree on section 2
        response = jerseyTest
                .target("/tradition/" + tradId + "/section/" + stuffCreated.get("section2") + "/annotations")
                .queryParam("recursive", "true")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        sectAnn = response.readEntity(new GenericType<>() {});
        assertEquals(2, sectAnn.size());
        assertTrue(sectAnn.stream().anyMatch(x -> x.getLabel().equals("PLACEREF")
                && x.getId().equals(stuffCreated.get("ref2"))));
        assertTrue(sectAnn.stream().anyMatch(x -> x.getLabel().equals("PLACE")
                && x.getId().equals(stuffCreated.get("place"))));
    }

    public void testDeleteSectionWithAnnotations() {
        // Set up some annotations across sections
        HashMap<String,String> stuffCreated = setupComplexAnnotation();

        // Count the readings we have in section 2 now
        List<ReadingModel> s2Readings = jerseyTest
                .target("/tradition/" + tradId + "/section/" + stuffCreated.get("section2") + "/readings")
                .request()
                .get(new GenericType<>() {});

        // Now try to delete section 1
        Response response = jerseyTest
                .target("/tradition/" + tradId + "/section/" + stuffCreated.get("section1"))
                .request()
                .delete();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        // Section 2 should be unaffected
        response = jerseyTest
                .target("/tradition/" + tradId + "/section/" + stuffCreated.get("section2") + "/readings")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<ReadingModel> remaining = response.readEntity(new GenericType<>() {});
        assertEquals(s2Readings.size(), remaining.size());
        assertEquals(s2Readings.stream().map(ReadingModel::getId).sorted().collect(Collectors.toList()),
                remaining.stream().map(ReadingModel::getId).sorted().collect(Collectors.toList()));

        // Section 1 annotation shouldn't exist anymore
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotation/" + stuffCreated.get("ref1"))
                .request()
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

        // All readings and annotations for section 2 should still exist
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotation/" + stuffCreated.get("ref2"))
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotation/" + stuffCreated.get("place"))
                .request()
                .get(Response.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

    }

    private HashMap<String, String> setupComplexAnnotation() {
        HashMap<String, String> data = new HashMap<>();
        // Add the second section
        Util.addSectionToTradition(jerseyTest, tradId, "src/TestFiles/lf2.xml",
                "stemmaweb", "section 2");
        // Get both section IDs
        List<SectionModel> ourSections = jerseyTest
                .target("/tradition/" + tradId + "/sections")
                .request()
                .get(new GenericType<>() {});
        data.put("section1", ourSections.get(0).getId());
        data.put("section2", ourSections.get(1).getId());
        // Make some reading lookups
        HashMap<String, String> readingLookup = Util.makeReadingLookup(jerseyTest, tradId);

        // Set up the annotation structure
        // Make a PLACEREF annotation label
        AnnotationLabelModel pref = new AnnotationLabelModel();
        pref.setName("PLACEREF");
        pref.addLink("READING", "BEGIN,END");
        Response response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + pref.getName())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(pref));
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

        // Make a PLACE annotation label
        AnnotationLabelModel place = new AnnotationLabelModel();
        place.setName("PLACE");
        place.addLink("PLACEREF", "NAMED");
        place.addProperty("href", "String");
        place.addProperty("locatable", "Boolean");

        response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + place.getName())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(place));
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

        // Annotate some text
        AnnotationModel ref1 = new AnnotationModel();
        ref1.setLabel("PLACEREF");
        AnnotationLinkModel prb = new AnnotationLinkModel();
        prb.setType("BEGIN");
        prb.setTarget(Long.valueOf(readingLookup.get("suecia/2")));
        AnnotationLinkModel pre = new AnnotationLinkModel();
        pre.setType("END");
        pre.setTarget(Long.valueOf(readingLookup.get("suecia/2")));
        ref1.addLink(prb);
        ref1.addLink(pre);
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotation/")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(ref1));
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        ref1 = response.readEntity(AnnotationModel.class);
        data.put("ref1", ref1.getId());

        AnnotationModel suecia = new AnnotationModel();
        suecia.setLabel("PLACE");
        HashMap<String, Object> sprops = new HashMap<>();
        sprops.put("locatable", true);
        sprops.put("href", "https://en.wikipedia.org/wiki/Sweden");
        suecia.setProperties(sprops);
        AnnotationLinkModel slinks = new AnnotationLinkModel();
        slinks.setType("NAMED");
        slinks.setTarget(Long.valueOf(ref1.getId()));
        suecia.addLink(slinks);
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotation/")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(suecia));
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        suecia = response.readEntity(AnnotationModel.class);
        data.put("place", suecia.getId());

        // Make a reference in other section
        AnnotationModel ref2 = new AnnotationModel();
        ref2.setLabel("PLACEREF");
        prb = new AnnotationLinkModel();
        prb.setType("BEGIN");
        prb.setTarget(Long.valueOf(readingLookup.get("magisque/15")));
        pre = new AnnotationLinkModel();
        pre.setType("END");
        pre.setTarget(Long.valueOf(readingLookup.get("magisque/15")));
        ref2.addLink(prb);
        ref2.addLink(pre);
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotation/")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(ref2));
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        ref2 = response.readEntity(AnnotationModel.class);
        data.put("ref2", ref2.getId());

        // Link the new reference to the existing place
        AnnotationLinkModel newLink = new AnnotationLinkModel();
        newLink.setTarget(Long.valueOf(ref2.getId()));
        newLink.setType("NAMED");
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotation/" + suecia.getId() + "/link")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(newLink));
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        // Make sure that the place in question has links to two different PLACEREFs
        response = jerseyTest.target("/tradition/" + tradId + "/annotations" )
                .queryParam("label", "PLACE")
                .request().get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<AnnotationModel> places = response.readEntity(new GenericType<>() {});
        assertEquals(1, places.size());
        assertEquals(2, places.get(0).getLinks().size());

        return data;
    }

    @After
    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
        super.tearDown();
    }

}
