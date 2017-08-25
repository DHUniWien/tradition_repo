package net.stemmaweb.stemmaserver.integrationtests;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import junit.framework.TestCase;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.SectionModel;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Root;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    public void testTraditionCreated() throws Exception {
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

    public void testAddSection() throws Exception {
        String newSectId = Util.getValueFromJson(Util.addSectionToTradition(jerseyTest, tradId, "src/TestFiles/lf2.xml",
                "stemmaweb", "section 2"), "parentId");

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
    }

    public void testAddGraphmlSectionWithWitnesses() throws Exception {
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

    public void testDeleteSection() throws Exception {
        List<ReadingModel> tReadings = jerseyTest.resource().path("/tradition/" + tradId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});
        assertEquals(30, tReadings.size());

        Util.addSectionToTradition(jerseyTest, tradId, "src/TestFiles/lf2.xml", "stemmaweb", "section 2");
        tReadings = jerseyTest.resource().path("/tradition/" + tradId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});
        assertEquals(77, tReadings.size());

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
        assertEquals(47, tReadings.size());
    }

    private String importFlorilegium () {
        ClientResponse jerseyResult = null;
        try {
            jerseyResult = Util.createTraditionFromFileOrString(jerseyTest, "Florilegium", "LR",
                    "user@example.com", "src/TestFiles/florilegium_w.csv", "csv");
        } catch (Exception e) {
            fail();
        }
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
    public void testSectionOrdering() throws Exception {
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
        String reorderPath = "/tradition/" + florId + "/section/" + returnedSections.get(1).getId()
                + "/orderAfter/" + returnedSections.get(2).getId();
        ClientResponse jerseyResult = jerseyTest.resource()
                .path(reorderPath)
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());
        String bText = "Ὄψις γυναικὸς πεφαρμακευμένον βέλος ἐστὶ ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον " +
                "χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ " +
                "διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, " +
                "καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. θάλπει ἑστῶσα βοτάνη παρ᾽ ὕδατι, καὶ πάθος " +
                "ἀκολασίας, ἐν συντυχίαις γυναικῶν. τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο χαλεπὴν τοῖς " +
                "τοιούτοις ἀπειλὴν ὁ λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου " +
                "βλασφημεῖται ἐν τοῖς ἔθνεσιν. τοῦ Χρυσοστόμου Τοὺς ἐν τῇ πόλει βλασφημοῦντας, σωφρόνιζε. Κἂν " +
                "ἀκούσῃς τινὸς ἐν ἀμφόδῳ ἢ ἐν ὁδῶ ἢ ἐν ἀγορᾷ βλασφημοῦντος τὸν Θεόν, πρόσελθε, ἐπιτίμησον, κἂν " +
                "πληγὰς ἐπιθεῖναι δέῃ, μὴ παραιτήσῃ ῥάπισον αὐτοῦ τὴν ὄψιν, σύντριψον αὐτοῦ τὸ στόμα, ἁγίασόν σου " +
                "τὴν χεῖρα διὰ τῆς πληγῆς, κἂν ἐγκαλῶσι τινές, κὰν εἰς δικαστήριον ἕλκωσιν, ἀκολούθησον.";
        ClientResponse jerseyResponse = jerseyTest.resource()
                .path("/tradition/" + florId + "/witness/B/text").get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        String wit = Util.getValueFromJson(jerseyResponse, "text");
        assertEquals(bText, wit);
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

        String targetSection = returnedSections.get(1).getId();

        // Get the reading to split at
        ReadingModel targetReading = jerseyTest.resource()
                .path("/tradition/" + florId + "/witness/B/readings")
                .get(new GenericType<List<ReadingModel>>() {}).get(0);
        assertEquals("τὸ", targetReading.getText());

        // Do the split
        String splitPath = "/tradition/" + florId + "/section/" + targetSection
                + "/splitAtRank/" + targetReading.getRank();
        ClientResponse jerseyResult = jerseyTest.resource()
                .path(splitPath)
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());
        String newSection = Util.getValueFromJson(jerseyResult, "sectionId");

        // Check that superfluous witness B has been removed from the first half
        ClientResponse jerseyResponse = jerseyTest.resource()
                .path("/tradition/" + florId + "/section/" + targetSection + "/witness/B/text")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.NOT_FOUND.getStatusCode(), jerseyResponse.getStatus());

        // Check that the second half contains witness B
        jerseyResponse = jerseyTest.resource()
                .path("/tradition/" + florId + "/section/" + newSection + "/witness/B/text")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        assertEquals("τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ " +
                "λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν.",
                Util.getValueFromJson(jerseyResponse, "text"));

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
        assertEquals(646, dotLines.length);

        String wWord = "Μαξίμου";
        String xWord = "βλασφημεῖται";
        String yWord = "συντυχίας";
        String zWord = "βλασφημοῦντος";

        String getSectionDot = "/tradition/" + florId + "/section/" + targetSection + "/dot";
        jerseyResult = jerseyTest.resource()
                .path(getSectionDot)
                .type(MediaType.TEXT_PLAIN)
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());
        dotLines = jerseyResult.getEntity(String.class).split("\n");
        assertEquals(120, dotLines.length);
        Boolean spottedZ = false;
        Boolean sectionStartLabeled = false;
        Boolean sectionEndLabeled = false;
        for (String dotLine : dotLines) {
            assertFalse(dotLine.contains(wWord));
            assertFalse(dotLine.contains(xWord));
            assertFalse(dotLine.contains(yWord));
            if (dotLine.contains(zWord))
                spottedZ = true;
            if (dotLine.contains("#START#"))
                sectionStartLabeled = true;
            if (dotLine.contains("#START#"))
                sectionEndLabeled = true;
        }
        assertTrue(spottedZ);
        assertTrue(sectionStartLabeled);
        assertTrue(sectionEndLabeled);
    }

    @After
    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
        super.tearDown();
    }

}
