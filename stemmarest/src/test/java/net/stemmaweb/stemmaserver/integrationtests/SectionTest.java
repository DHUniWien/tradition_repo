package net.stemmaweb.stemmaserver.integrationtests;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import junit.framework.TestCase;
import net.stemmaweb.model.*;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;
import org.junit.After;
import org.junit.Before;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.MultipleFoundException;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.MediaType;
import java.util.*;

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
                .addResource(new Root())
                .create();
        jerseyTest.setUp();

        // Create a tradition to use
        ClientResponse jerseyResult = null;
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
        List<SectionModel> tSections = jerseyTest.resource().path("/tradition/" + tradId + "/sections")
                .get(new GenericType<List<SectionModel>>() {});
        assertEquals(1, tSections.size());
        assertEquals("DEFAULT", tSections.get(0).getName());

        SectionModel defaultSection = tSections.get(0);
        defaultSection.setName("My new name");
        ClientResponse jerseyResult = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section/" + defaultSection.getId())
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, defaultSection);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());
        assertEquals("My new name", Util.getValueFromJson(jerseyResult, "name"));
    }

    public void testAddSection() {
        // Get the existing start and end nodes
        Node startNode = DatabaseService.getStartNode(tradId, db);
        Node endNode = DatabaseService.getEndNode(tradId, db);

        String newSectId = Util.getValueFromJson(Util.addSectionToTradition(jerseyTest, tradId, "src/TestFiles/lf2.xml",
                "stemmaweb", "section 2"), "parentId");

        List<SectionModel> tSections = jerseyTest.resource().path("/tradition/" + tradId + "/sections")
                .get(new GenericType<List<SectionModel>>() {});
        assertEquals(2, tSections.size());
        assertEquals("section 2", tSections.get(1).getName());
        Long expectedRank = 22L;
        assertEquals(expectedRank, tSections.get(1).getEndRank());

        String aText = "quasi duobus magnis luminaribus populus terre illius ad veri dei noticiam & cultum magis " +
                "magisque illustrabatur iugiter ac informabatur Sanctus autem";
        ClientResponse jerseyResponse = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section/" + newSectId + "/witness/A/text")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        String witFragment = Util.getValueFromJson(jerseyResponse, "text");
        assertEquals(aText, witFragment);

        try (Transaction tx = db.beginTx()) {
            assertEquals(startNode.getId(), DatabaseService.getStartNode(tradId, db).getId());
            assertNotEquals(endNode.getId(), DatabaseService.getEndNode(tradId, db).getId());
            assertEquals(DatabaseService.getEndNode(newSectId, db).getId(), DatabaseService.getEndNode(tradId, db).getId());
            tx.success();
        }
    }

    public void testSectionRelationships() {
        String newSectId = Util.getValueFromJson(Util.addSectionToTradition(jerseyTest, tradId, "src/TestFiles/lf2.xml",
                "stemmaweb", "section 2"), "parentId");
        List<RelationModel> sectRels = jerseyTest.resource().path("/tradition/" + tradId + "/section/" + newSectId + "/relations")
                .get(new GenericType<List<RelationModel>>() {});
        assertEquals(9, sectRels.size());
        List<RelationModel> allRels = jerseyTest.resource().path("/tradition/" + tradId + "/relations")
                .get(new GenericType<List<RelationModel>>() {});
        assertEquals(21, allRels.size());
    }

    public void testSectionReadings() {
        String newSectId = Util.getValueFromJson(Util.addSectionToTradition(jerseyTest, tradId, "src/TestFiles/lf2.xml",
                "stemmaweb", "section 2"), "parentId");
        List<ReadingModel> sectRdgs = jerseyTest.resource().path("/tradition/" + tradId + "/section/" + newSectId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});
        assertEquals(46, sectRdgs.size());
        List<ReadingModel> allRdgs = jerseyTest.resource().path("/tradition/" + tradId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});
        assertEquals(75, allRdgs.size());
    }

    public void testSectionWitnesses() {
        List<SectionModel> tSections = jerseyTest.resource().path("/tradition/" + tradId + "/sections")
                .get(new GenericType<List<SectionModel>>() {});
        String sectId = tSections.get(0).getId();

        List<WitnessModel> sectWits = jerseyTest.resource().path("/tradition/"  + tradId + "/section/" + sectId + "/witnesses")
                .get(new GenericType<List<WitnessModel>>() {});
        assertEquals(37, sectWits.size());
    }

    public void testAddGraphmlSectionWithWitnesses() {
        String newSectId = Util.getValueFromJson(Util.addSectionToTradition(jerseyTest, tradId, "src/TestFiles/lf2_graphml.xml",
                "graphml", "section 2"), "parentId");

        List<SectionModel> tSections = jerseyTest.resource().path("/tradition/" + tradId + "/sections")
                .get(new GenericType<List<SectionModel>>() {});
        assertEquals(2, tSections.size());
        assertEquals("section 2", tSections.get(1).getName());

        String aText = "quasi duobus magnis luminaribus populus terre illius ad veri dei noticiam & cultum magis " +
                "magisque illustrabatur iugiter ac informabatur Sanctus autem";
        ClientResponse jerseyResponse = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section/" + newSectId + "/witness/A/text")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatus());
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
        List<ReadingModel> tReadings = jerseyTest.resource().path("/tradition/" + tradId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});
        assertEquals(29, tReadings.size());

        Util.addSectionToTradition(jerseyTest, tradId, "src/TestFiles/lf2.xml", "stemmaweb", "section 2");
        tReadings = jerseyTest.resource().path("/tradition/" + tradId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});
        assertEquals(75, tReadings.size());

        List<SectionModel> tSections = jerseyTest.resource().path("/tradition/" + tradId + "/sections")
                .get(new GenericType<List<SectionModel>>() {});
        SectionModel firstSection = tSections.get(0);

        ClientResponse jerseyResult = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section/" + firstSection.getId())
                .type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());

        tReadings = jerseyTest.resource().path("/tradition/" + tradId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});
        assertEquals(46, tReadings.size());
    }

    private String importFlorilegium () {
        ClientResponse jerseyResult = Util.createTraditionFromFileOrString(jerseyTest, "Florilegium", "LR",
                    "user@example.com", "src/TestFiles/florilegium_w.csv", "csv");
        assertEquals(ClientResponse.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        String florId = Util.getValueFromJson(jerseyResult, "tradId");
        // Get the existing single section ID
        SectionModel firstSection = jerseyTest.resource().path("/tradition/" + tradId + "/sections")
                .get(new GenericType<List<SectionModel>>() {}).get(0);
        assertNotNull(firstSection);

        // Add the other three sections
        int i = 0;
        while (i < 3) {
            String fileName = String.format("src/TestFiles/florilegium_%c.csv", 120 + i++);
            Util.addSectionToTradition(jerseyTest, florId, fileName, "csv", String.format("part %d", i));
        }
        return florId;
    }

    // test ordering of sections
    public void testSectionOrdering() {
        String florId = importFlorilegium();
        // Test that we get the sections back in the correct order
        List<SectionModel> returnedSections = jerseyTest.resource()
                .path("/tradition/" + florId + "/sections")
                .get(new GenericType<List<SectionModel>>() {});
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
        ClientResponse jerseyResponse = jerseyTest.resource()
                .path("/tradition/" + florId + "/witness/B/text").get(ClientResponse.class);
        String wit = Util.getValueFromJson(jerseyResponse, "text");
        assertEquals(bText, wit);
    }

    public void testDeleteSectionMiddle() {
        String florId = importFlorilegium();
        List<SectionModel> returnedSections = jerseyTest.resource()
                .path("/tradition/" + florId + "/sections")
                .get(new GenericType<List<SectionModel>>() {});
        ClientResponse jerseyResult = jerseyTest.resource()
                .path("/tradition/" + florId + "/section/" + returnedSections.get(1).getId())
                .type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());
        String bText = "Ὄψις γυναικὸς πεφαρμακευμένον βέλος ἐστὶ ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον " +
                "χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ " +
                "διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, " +
                "καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. θάλπει ἑστῶσα βοτάνη παρ᾽ ὕδατι, καὶ πάθος " +
                "ἀκολασίας, ἐν συντυχίαις γυναικῶν. τοῦ Χρυσοστόμου Τοὺς ἐν τῇ πόλει βλασφημοῦντας, σωφρόνιζε. Κἂν " +
                "ἀκούσῃς τινὸς ἐν ἀμφόδῳ ἢ ἐν ὁδῶ ἢ ἐν ἀγορᾷ βλασφημοῦντος τὸν Θεόν, πρόσελθε, ἐπιτίμησον, κἂν " +
                "πληγὰς ἐπιθεῖναι δέῃ, μὴ παραιτήσῃ ῥάπισον αὐτοῦ τὴν ὄψιν, σύντριψον αὐτοῦ τὸ στόμα, ἁγίασόν σου " +
                "τὴν χεῖρα διὰ τῆς πληγῆς, κἂν ἐγκαλῶσι τινές, κὰν εἰς δικαστήριον ἕλκωσιν, ἀκολούθησον.";
        ClientResponse jerseyResponse = jerseyTest.resource()
                .path("/tradition/" + florId + "/witness/B/text").get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        String wit = Util.getValueFromJson(jerseyResponse, "text");
        assertEquals(bText, wit);
    }

    public void testReorderSections() {
        String florId = importFlorilegium();
        List<SectionModel> returnedSections = jerseyTest.resource()
                .path("/tradition/" + florId + "/sections")
                .get(new GenericType<List<SectionModel>>() {});
        String reorderPath = "/section/" + returnedSections.get(1).getId()
                + "/orderAfter/" + returnedSections.get(2).getId();
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
        String moveFirstPath = "/section/" + returnedSections.get(3).getId() + "/orderAfter/none";
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
        ClientResponse jerseyResult = jerseyTest.resource()
                .path("/tradition/" + traditionId + reorderPath)
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());
        jerseyResult = jerseyTest.resource()
                .path("/tradition/" + traditionId + "/witness/B/text").get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());
        return Util.getValueFromJson(jerseyResult, "text");
    }

    public void testSectionWrongTradition () {
        String florId = importFlorilegium();
        String newSectId = Util.getValueFromJson(Util.addSectionToTradition(jerseyTest, tradId,
                "src/TestFiles/lf2.xml", "stemmaweb", "section 2"), "parentId");
        ClientResponse jerseyResult = jerseyTest.resource()
                .path("/tradition/" + florId + "/section/" + newSectId + "/witness/A")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.NOT_FOUND.getStatusCode(), jerseyResult.getStatus());
    }

    public void testSectionOrderAfterSelf () {
        String florId = importFlorilegium();
        List<SectionModel> returnedSections = jerseyTest.resource()
                .path("/tradition/" + florId + "/sections")
                .get(new GenericType<List<SectionModel>>() {});
        String tryReorder = returnedSections.get(2).getId();
        ClientResponse jerseyResult = jerseyTest.resource()
                .path("/tradition/" + florId + "/section/" + tryReorder + "/orderAfter/" + tryReorder)
                .put(ClientResponse.class);
        assertEquals(ClientResponse.Status.BAD_REQUEST.getStatusCode(), jerseyResult.getStatus());
        String errMsg = jerseyResult.getEntity(String.class);
        assertEquals("Cannot reorder a section after itself", errMsg);
    }

    public void testMergeSections() {
        String florId = importFlorilegium();
        List<SectionModel> returnedSections = jerseyTest.resource()
                .path("/tradition/" + florId + "/sections")
                .get(new GenericType<List<SectionModel>>() {});

        // Try merge of 3 into 4
        String targetSection = returnedSections.get(2).getId();
        String requestPath = "/tradition/" + florId + "/section/" + targetSection
                + "/merge/" + returnedSections.get(3).getId();
        ClientResponse jerseyResponse = jerseyTest.resource()
                .path(requestPath).post(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatus());

        String pText = "Ὄψις γυναικὸς πεφαρμακευμένον βέλος ἐστὶ ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον " +
                "χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ " +
                "διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, " +
                "καὶ μὴ δῷς αὐτῆς παρρησίαν θαρρῆσαι σοί ποτε. θάλπει ἑστῶσα βοτάνη παρ᾽ ὕδατι, καὶ πάθος ἀκολασίας, " +
                "ἐν συντυχίαις γυναικῶν. τοῦ Χρυσοστόμου Τοὺς ἐν τῇ πόλει βλασφημοῦντας, σωφρόνιζε. Κἂν ἀκούσῃς " +
                "τινὸς ἐν ἀμφόδῳ ἢ ἐν ὁδῶ ἢ ἐν ἀγορᾷ βλασφημοῦντος τὸν Θεόν, πρόσελθε, ἐπιτίμησον, κἂν πληγὰς " +
                "ἐπιθεῖναι δέῃ, μὴ παραιτήσῃ ῥάπισον αὐτοῦ τὴν ὄψιν, σύντριψον αὐτοῦ τὸ στόμα, ἁγίασόν σου τὴν χεῖρα " +
                "διὰ τῆς πληγῆς, κἂν ἐγκαλῶσι τινές, κὰν εἰς δικαστήριον ἕλκωσιν, ἀκολούθησον.";
        jerseyResponse = jerseyTest.resource()
                .path("/tradition/" + florId + "/section/" + targetSection + "/witness/P/text")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        String witFragment = Util.getValueFromJson(jerseyResponse, "text");
        assertEquals(pText, witFragment);

        // Also test a witness that didn't exist in section 3
        String dText = "Ὄψις γυναικὸς βέλος ἐστὶ πεφαρμακευμένον ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον " +
                "χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ " +
                "διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, " +
                "καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. Θάλλει βοτάνη ἑστῶσα παρ᾽ ὕδατι, καὶ πάθος " +
                "ἀκολασίας, ἐν συντυχίαις γυναικῶν.";
        jerseyResponse = jerseyTest.resource()
                .path("/tradition/" + florId + "/section/" + targetSection + "/witness/D/text")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        witFragment = Util.getValueFromJson(jerseyResponse, "text");
        assertEquals(dText, witFragment);

        // Now try merge of 1 into 3, which should fail
        requestPath = "/tradition/" + florId + "/section/" + returnedSections.get(0).getId()
                + "/merge/" + targetSection;
        jerseyResponse = jerseyTest.resource()
                .path(requestPath).post(ClientResponse.class);
        assertEquals(ClientResponse.Status.BAD_REQUEST.getStatusCode(), jerseyResponse.getStatus());

        // Now try merge of 2 into 1
        targetSection = returnedSections.get(0).getId();
        requestPath = "/tradition/" + florId + "/section/" + returnedSections.get(1).getId() + "/merge/" + targetSection;
        jerseyResponse = jerseyTest.resource().path(requestPath).post(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        assertEquals(2, jerseyTest.resource()
                .path("/tradition/" + florId + "/sections")
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
        jerseyResponse = jerseyTest.resource()
                .path("/tradition/" + florId + "/section/" + targetSection + "/witness/P/text")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        witFragment = Util.getValueFromJson(jerseyResponse, "text");
        assertEquals(pText, witFragment);

        // Also test a witness that didn't exist in section 2
        String kText = "Μαξίμου Ἡ περὶ τῆς τοῦ πνεύματος τοῦ ἁγίου βλασφημίας ἀπορία αὐτόθεν ἔχει τὴν λύσιν· ὁ δὲ " +
                "δεύτερος ἐστὶν οὗτος· ὅτάν τις ἐν ἁμαρτίαις ἐνεχόμενος, ἀκούων δὲ τοῦ κυρίου λέγοντος μὴ κρίνετε " +
                "φοβούμενος οὐδένα κρίνει ἐν τῇ ἐξετάσει τῶν βεβιωμένων ὡς φύλαξ τῆς ἐντολῆς οὐ κρίνεται· εἰ μὴ τὸ " +
                "γενέσθαι πιστόν, εἰκότως ὅταν ἐν ἁμαρτίαις τίς ὢν οἰκονομῆται ἐκ τῆς προνοίας ἐν συμφοραῖς, ἐν " +
                "ἀνάγκαις, ἐν νόσοις ὡς οὐκ οἶδε γὰρ διὰ τῶν τοιούτων καθαίρει αὐτὸν ὁ θεός τῶν ἐν ἀπιστίᾳ τὸν βίον " +
                "κατακλείσαντι οὔτε ἐνταῦθα οὔτε ἐν τῷ μέλλοντι ἀφεθήσεται τῆς ἀπιστίας καὶ ἀθεΐας ἡ ἁμαρτία.";
        jerseyResponse = jerseyTest.resource()
                .path("/tradition/" + florId + "/section/" + targetSection + "/witness/K/text")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        witFragment = Util.getValueFromJson(jerseyResponse, "text");
        assertEquals(kText, witFragment);
    }

    // Test merge of tradition sections with layered readings

    public void testSplitSection() {
        String florId = importFlorilegium();
        List<SectionModel> returnedSections = jerseyTest.resource()
                .path("/tradition/" + florId + "/sections")
                .get(new GenericType<List<SectionModel>>() {});

        SectionModel origSection = returnedSections.get(1);
        String targetSectionId = origSection.getId();

        // Get the reading to split at
        ReadingModel targetReading = jerseyTest.resource()
                .path("/tradition/" + florId + "/witness/B/readings")
                .get(new GenericType<List<ReadingModel>>() {}).get(0);
        assertEquals("τὸ", targetReading.getText());

        // Do the split
        String splitPath = "/tradition/" + florId + "/section/" + targetSectionId
                + "/splitAtRank/" + targetReading.getRank();
        ClientResponse jerseyResult = jerseyTest.resource()
                .path(splitPath)
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());
        String newSectionId = Util.getValueFromJson(jerseyResult, "sectionId");

        // Get the data for both new sections
        SectionModel shortenedSection = jerseyTest.resource()
                .path("/tradition/" + florId + "/section/" + targetSectionId)
                .get(SectionModel.class);
        // Check that the new section has the right ending rank
        assertEquals(targetReading.getRank(), shortenedSection.getEndRank());

        SectionModel newSection = jerseyTest.resource()
                .path("/tradition/" + florId + "/section/" + newSectionId)
                .get(SectionModel.class);
        // Check that the new section has the right ending rank
        assertEquals(Long.valueOf(origSection.getEndRank() - targetReading.getRank() + 1), newSection.getEndRank());

        // Check that superfluous witness B has been removed from the first half
        ClientResponse jerseyResponse = jerseyTest.resource()
                .path("/tradition/" + florId + "/section/" + targetSectionId + "/witness/B/text")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.NOT_FOUND.getStatusCode(), jerseyResponse.getStatus());

        // Check that the second half contains witness B
        jerseyResponse = jerseyTest.resource()
                .path("/tradition/" + florId + "/section/" + newSectionId + "/witness/B/text")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        assertEquals("τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ " +
                "λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν.",
                Util.getValueFromJson(jerseyResponse, "text"));

        // Check that the first half can be exported to dot
        jerseyResponse = jerseyTest.resource()
                .path("/tradition/" + florId + "/section/" + targetSectionId + "/dot")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        // ...and that there is only one start and end node each
        String dotText = jerseyResponse.getEntity(String.class);
        assertEquals(1, countOccurrences(dotText, "__END__"));
        assertEquals(1, countOccurrences(dotText, "__START__"));

        // Check that the second half can be exported to dot
        jerseyResponse = jerseyTest.resource()
                .path("/tradition/" + florId + "/section/" + newSectionId + "/dot")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        dotText = jerseyResponse.getEntity(String.class);
        assertEquals(1, countOccurrences(dotText, "__END__"));
        assertEquals(1, countOccurrences(dotText, "__START__"));


        // Check that the second half has readings on rank 1
        List<ReadingModel> part2rdgs = jerseyTest.resource()
                .path("/tradition/" + florId + "/section/" + newSectionId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});
        boolean foundRank1 = false;
        for (ReadingModel rdg : part2rdgs)
            if (rdg.getRank().equals(1L))
                foundRank1 = true;
        assertTrue(foundRank1);

        // Check that the first half's end rank correct
        List<ReadingModel> part1rdgs = jerseyTest.resource()
                .path("/tradition/" + florId + "/section/" + targetSectionId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});
        ReadingModel firstEnd = part1rdgs.stream().filter(ReadingModel::getIs_end).findFirst().get();
        assertEquals(targetReading.getRank(), firstEnd.getRank());

        // Check that the second half's end rank is correct
        ReadingModel secondEnd = part2rdgs.stream().filter(ReadingModel::getIs_end).findFirst().get();
        assertEquals(Long.valueOf(origSection.getEndRank() - targetReading.getRank() + 1), secondEnd.getRank());

        // Check that the final nodes are all connected to END
        List<WitnessModel> firstSectWits = jerseyTest.resource()
                .path("/tradition/" + florId + "/section/" + targetSectionId + "/witnesses")
                .get(new GenericType<List<WitnessModel>>() {});
        for (WitnessModel wit : firstSectWits) {
            ClientResponse connectedTest = jerseyTest.resource()
                    .path("/tradition/" + florId + "/section/" + targetSectionId + "/witness/" + wit.getSigil() + "/text")
                    .get(ClientResponse.class);
            assertEquals(ClientResponse.Status.OK.getStatusCode(), connectedTest.getStatus());
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
        jerseyResponse = jerseyTest.resource()
                .path("/tradition/" + florId + "/witness/B/text").get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        assertEquals(bText, Util.getValueFromJson(jerseyResponse, "text"));
    }

    public void testSplitSectionMatthew() {
        ClientResponse jerseyResponse = Util.createTraditionFromFileOrString(jerseyTest, "Legend", "LR",
                "user@example.com", "src/TestFiles/ChronicleOfMatthew.xml", "stemmaweb");
        String mattId = Util.getValueFromJson(jerseyResponse, "tradId");
        List<SectionModel> returnedSections = jerseyTest.resource()
                .path("/tradition/" + mattId + "/sections")
                .get(new GenericType<List<SectionModel>>() {});

        SectionModel origSection = returnedSections.get(0);
        String targetSectionId = origSection.getId();

        // Do the split
        String splitPath = "/tradition/" + mattId + "/section/" + targetSectionId
                + "/splitAtRank/168";
        jerseyResponse = jerseyTest.resource()
                .path(splitPath)
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        String newSectionId = Util.getValueFromJson(jerseyResponse, "sectionId");
        // Check that the first half can be exported to dot
        jerseyResponse = jerseyTest.resource()
                .path("/tradition/" + mattId + "/section/" + targetSectionId + "/dot")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        // ...and that there is only one start and end node each
        String dotText = jerseyResponse.getEntity(String.class);
        assertEquals(1, countOccurrences(dotText, "__END__"));
        assertEquals(1, countOccurrences(dotText, "__START__"));

        // Check that the second half can be exported to dot
        jerseyResponse = jerseyTest.resource()
                .path("/tradition/" + mattId + "/section/" + newSectionId + "/dot")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        dotText = jerseyResponse.getEntity(String.class);
        assertEquals(1, countOccurrences(dotText, "__END__"));
        assertEquals(1, countOccurrences(dotText, "__START__"));


        // Check that the second half has readings on rank 1
        List<ReadingModel> part2rdgs = jerseyTest.resource()
                .path("/tradition/" + mattId + "/section/" + newSectionId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});
        boolean foundRank1 = false;
        for (ReadingModel rdg : part2rdgs)
            if (rdg.getRank().equals(1L))
                foundRank1 = true;
        assertTrue(foundRank1);

        // Check that the first half's end rank correct
        List<ReadingModel> part1rdgs = jerseyTest.resource()
                .path("/tradition/" + mattId + "/section/" + targetSectionId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});
        ReadingModel firstEnd = part1rdgs.stream().filter(ReadingModel::getIs_end).findFirst().get();
        assertEquals(Long.valueOf(168), firstEnd.getRank());

        // Check that the second half's end rank is correct
        ReadingModel secondEnd = part2rdgs.stream().filter(ReadingModel::getIs_end).findFirst().get();
        assertEquals(Long.valueOf(origSection.getEndRank() - 168 + 1), secondEnd.getRank());

        // Check that the final nodes are all connected to END
        List<WitnessModel> firstSectWits = jerseyTest.resource()
                .path("/tradition/" + mattId + "/section/" + targetSectionId + "/witnesses")
                .get(new GenericType<List<WitnessModel>>() {});
        for (WitnessModel wit : firstSectWits) {
            ClientResponse connectedTest = jerseyTest.resource()
                    .path("/tradition/" + mattId + "/section/" + targetSectionId + "/witness/" + wit.getSigil() + "/text")
                    .get(ClientResponse.class);
            assertEquals(ClientResponse.Status.OK.getStatusCode(), connectedTest.getStatus());
        }
        // Check that now-empty witnesses H and L are gone from the first section
        for (WitnessModel wit : firstSectWits) {
            assertNotEquals("H", wit.getSigil());
            assertNotEquals("L", wit.getSigil());
        }
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

    public void testSectionDotOutput() {
        String florId = importFlorilegium();
        List<SectionModel> returnedSections = jerseyTest.resource()
                .path("/tradition/" + florId + "/sections")
                .get(new GenericType<List<SectionModel>>() {});

        String targetSection = returnedSections.get(3).getId();

        String getDot = "/tradition/" + florId + "/dot";
        ClientResponse jerseyResult = jerseyTest.resource()
                .path(getDot)
                .type(MediaType.TEXT_PLAIN)
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());
        // Do a basic sanity check of the dot file - does it have the right number of lines?
        String[] dotLines = jerseyResult.getEntity(String.class).split("\n");
        assertEquals(650, dotLines.length);

        String wWord = "Μαξίμου";
        String xWord = "βλασφημεῖται";
        String yWord = "συντυχίας";
        String zWord = "βλασφημοῦντος";

        // Set a normal form on one reading, see if it turns up in dot output.
        // Also stick a double quote inside one of the readings
        try (Transaction tx = db.beginTx()) {
            Node n = db.findNode(Nodes.READING, "text", "ὄψιν,");
            assertNotNull(n);
            n.setProperty("text", "ὄψ\"ιν,");
            n.setProperty("normal_form", "ὄψιν");
            n = db.findNode(Nodes.READING, "text", "γυναικῶν.");
            assertNotNull(n);
            n.setProperty("text", "γυν\"αι\"κῶν.");
            tx.success();
        }

        String getSectionDot = "/tradition/" + florId + "/section/" + returnedSections.get(2).getId() + "/dot";
        jerseyResult = jerseyTest.resource()
                .path(getSectionDot)
                .queryParam("expand_sigla", "true")
                .type(MediaType.TEXT_PLAIN)
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());
        String dotStr = jerseyResult.getEntity(String.class);
        assertTrue(dotStr.startsWith("digraph \"part 2\""));
        assertFalse(dotStr.contains("majority"));
        assertTrue(dotStr.contains(yWord));
        assertTrue(dotStr.contains("γυν\\\"αι\\\"κῶν."));


        getSectionDot = "/tradition/" + florId + "/section/" + targetSection + "/dot";
        jerseyResult = jerseyTest.resource()
                .path(getSectionDot)
                .queryParam("show_normal", "true")
                .type(MediaType.TEXT_PLAIN)
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());
        dotStr = jerseyResult.getEntity(String.class);
        dotLines = dotStr.split("\n");
        assertEquals(120, dotLines.length);
        assertFalse(dotStr.contains(wWord));
        assertFalse(dotStr.contains(xWord));
        assertFalse(dotStr.contains(yWord));
        assertTrue(dotStr.contains(zWord));
        assertTrue(dotStr.contains("#START#"));
        assertTrue(dotStr.contains("#END#"));
        assertTrue(dotStr.contains("<ὄ&psi;&quot;&iota;&nu;,<BR/><FONT COLOR=\"grey\">ὄ&psi;&iota;&nu;</FONT>>"));
    }

    public void testLemmaText() {
        String newSectId = Util.getValueFromJson(Util.addSectionToTradition(jerseyTest, tradId, "src/TestFiles/lf2.xml",
                "stemmaweb", "section 2"), "parentId");

        // First check before any lemma readings are set
        ClientResponse jerseyResult = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section/" + newSectId + "/lemmatext")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());
        String lemmaText = jerseyResult.getEntity(String.class);
        assertEquals("", lemmaText);

        jerseyResult = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section/" + newSectId + "/lemmareadings")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());
        List<ReadingModel> lemmaReadings = jerseyResult.getEntity(new GenericType<List<ReadingModel>>() {});
        assertEquals(0, lemmaReadings.size());

        // Now set some lemma readings
        List<ReadingModel> allReadings = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section/" + newSectId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});
        HashMap<String, String> readingLookup = new HashMap<>();
        for (ReadingModel rm : allReadings) {
            String key = rm.getText() + "/" + rm.getRank().toString();
            readingLookup.put(key, rm.getId());
        }
        KeyPropertyModel keyModel = new KeyPropertyModel();
        keyModel.setKey("is_lemma");
        keyModel.setProperty(true);
        String[] lemmatised = new String[]{"quasi/1", "duobus/2", "magnis/3", "luminaribus/4", "populus/5",
                "terre/6", "illius/7", "ad/8", "dei/9", "veri/10", "noticiam/11", "et/12", "cultum/13",
                "magis/14", "illustrabatur/16", "jugiter/17", "ac/18", "informabatur/19", "Sanctus/20", "autem/21"};
        for (String rdg : lemmatised) {
            List<KeyPropertyModel> models = new ArrayList<>();
            models.add(keyModel);
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
            ReadingChangePropertyModel chgModel = new ReadingChangePropertyModel();
            chgModel.setProperties(models);

            jerseyResult = jerseyTest.resource().path("/reading/" + readingLookup.get(rdg))
                    .type(MediaType.APPLICATION_JSON)
                    .put(ClientResponse.class, chgModel);
            assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());
        }
        // Check that we still have no lemma text
        jerseyResult = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section/" + newSectId + "/lemmatext")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());
        lemmaText = jerseyResult.getEntity(String.class);
        assertEquals("", lemmaText);

        // Now make the lemma text
        jerseyResult = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section/" + newSectId + "/setlemma")
                .post(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());

        // Check that we now have a lemma text
        jerseyResult = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section/" + newSectId + "/lemmatext")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());
        lemmaText = jerseyResult.getEntity(String.class);
        assertEquals("Quasi duobus magnis luminaribus populus terre illius ad dei veri noticiam et " +
                "cultum magis illustrabatur jugiter ac informabatur Sanctus autem.", lemmaText);

        jerseyResult = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section/" + newSectId + "/lemmareadings")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());
        lemmaReadings = jerseyResult.getEntity(new GenericType<List<ReadingModel>>() {});
        assertEquals(20, lemmaReadings.size());
        HashSet<String> inLemma = new HashSet<>();
        lemmaReadings.forEach(x -> inLemma.add(x.getId()));
        for (String rdg : lemmatised) {
            assertTrue(inLemma.contains(readingLookup.get(rdg)));
        }

        // Add a lemma on the same rank, check that we get an error
        List<KeyPropertyModel> models = new ArrayList<>();
        models.add(keyModel);
        ReadingChangePropertyModel chgModel = new ReadingChangePropertyModel();
        chgModel.setProperties(models);
        jerseyResult = jerseyTest.resource().path("/reading/" + readingLookup.get("iugiter/17"))
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, chgModel);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());

        // Lemma text hasn't changed yet
        jerseyResult = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section/" + newSectId + "/lemmatext")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());
        lemmaText = jerseyResult.getEntity(String.class);
        assertEquals("Quasi duobus magnis luminaribus populus terre illius ad dei veri noticiam et " +
                "cultum magis illustrabatur jugiter ac informabatur Sanctus autem.", lemmaText);

        // Now try to change it
        jerseyResult = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section/" + newSectId + "/setlemma")
                .post(ClientResponse.class);
        assertEquals(ClientResponse.Status.CONFLICT.getStatusCode(), jerseyResult.getStatus());

        // But the lemma text still hasn't changed
        jerseyResult = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section/" + newSectId + "/lemmatext")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());
        lemmaText = jerseyResult.getEntity(String.class);
        assertEquals("Quasi duobus magnis luminaribus populus terre illius ad dei veri noticiam et " +
                "cultum magis illustrabatur jugiter ac informabatur Sanctus autem.", lemmaText);

        // Remove the bad lemma, change another lemma, check that the text changes
        keyModel.setProperty(false);
        jerseyResult = jerseyTest.resource().path("/reading/" + readingLookup.get("iugiter/17"))
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, chgModel);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());
        jerseyResult = jerseyTest.resource().path("/reading/" + readingLookup.get("ac/18"))
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, chgModel);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());
        jerseyResult = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section/" + newSectId + "/setlemma")
                .post(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());
        jerseyResult = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section/" + newSectId + "/lemmatext")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());
        lemmaText = jerseyResult.getEntity(String.class);
        assertEquals("Quasi duobus magnis luminaribus populus terre illius ad dei veri noticiam et " +
                "cultum magis illustrabatur jugiter informabatur Sanctus autem.", lemmaText);

    }

    public void testSetLemmaText() {

    }

    @After
    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
        super.tearDown();
    }

}
