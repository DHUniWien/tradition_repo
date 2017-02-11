package net.stemmaweb.stemmaserver.integrationtests;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.multipart.FormDataBodyPart;
import com.sun.jersey.multipart.FormDataMultiPart;
import com.sun.jersey.test.framework.JerseyTest;
import junit.framework.TestCase;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.SectionModel;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;
import org.junit.After;
import org.junit.Before;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.MediaType;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
                    "user@example.com", "src/TestFiles/legendfrag.xml", "graphml");
        } catch (Exception e) {
            fail();
        }
        tradId = Util.getValueFromJson(jerseyResult, "tradId");
    }

    private String addNewSection(String traditionId, String fileName, String fileType, String sectionName) {
        FormDataMultiPart form = new FormDataMultiPart();
        form.field("filetype", fileType);
        form.field("name", sectionName);
        InputStream input = null;
        try {
            input = new FileInputStream(fileName);
        } catch (FileNotFoundException f) {
            fail();
        }
        FormDataBodyPart fdp = new FormDataBodyPart("file", input,
                MediaType.APPLICATION_OCTET_STREAM_TYPE);
        form.bodyPart(fdp);
        ClientResponse jerseyResult = jerseyTest.resource()
                .path("/tradition/" + traditionId + "/section")
                .type(MediaType.MULTIPART_FORM_DATA_TYPE)
                .post(ClientResponse.class, form);
        assertEquals(ClientResponse.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        return Util.getValueFromJson(jerseyResult, "parentId");
    }

    // test creation of a tradition, that it has a single section
    public void testTraditionCreated() throws Exception {
        List<SectionModel> tSections = jerseyTest.resource().path("/tradition/" + tradId + "/sections")
                .get(new GenericType<List<SectionModel>>() {});
        assertEquals(1, tSections.size());
        assertEquals("DEFAULT", tSections.get(0).getName());
    }

    public void testAddSection() throws Exception {
        String newSectId = addNewSection(tradId,"src/TestFiles/lf2.xml", "graphml",
                "section 2");

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

    public void testDeleteSection() throws Exception {
        List<ReadingModel> tReadings = jerseyTest.resource().path("/tradition/" + tradId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});
        assertEquals(30, tReadings.size());

        addNewSection(tradId, "src/TestFiles/lf2.xml", "graphml", "section 2");
        tReadings = jerseyTest.resource().path("/tradition/" + tradId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});
        assertEquals(78, tReadings.size());

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
        assertEquals(48, tReadings.size());
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
            addNewSection(florId, fileName, "csv", String.format("part %d", i));
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
        String newSectId = addNewSection(tradId,"src/TestFiles/lf2.xml", "graphml",
                "section 2");
        ClientResponse jerseyResult = jerseyTest.resource()
                .path("/tradition/" + florId + "/section/" + newSectId + "/witness/A")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.NOT_FOUND.getStatusCode(), jerseyResult.getStatus());
    }

    // test merging two sections

    // test splitting a section
    public void testSplitSection() {
        String florId = importFlorilegium();
        List<SectionModel> returnedSections = jerseyTest.resource()
                .path("/tradition/" + florId + "/sections")
                .get(new GenericType<List<SectionModel>>() {});

        String targetSection = returnedSections.get(2).getId();

        // Get the reading to split at
        Optional<ReadingModel> targetReadings = jerseyTest.resource()
                .path("/tradition/" + florId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {})
                .stream().filter(x -> x.getText().equals("βέλτιον")).findFirst();
        assertTrue(targetReadings.isPresent());
        if (!targetReadings.isPresent()) // Just to avoid stupid warning on .get() below
            return;

        // Do the split
        String splitPath = "/tradition/" + florId + "/section/" + targetSection
                + "/splitAtRank/" + targetReadings.get().getRank();
        ClientResponse jerseyResult = jerseyTest.resource()
                .path(splitPath)
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());

        // Check that section 2 text is now shorter
        ClientResponse jerseyResponse = jerseyTest.resource()
                .path("/tradition/" + florId + "/section/" + targetSection + "/witness/B/text")
                .get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        String witFragment = Util.getValueFromJson(jerseyResponse, "text");
        assertEquals("Ὄψις γυναικὸς πεφαρμακευμένον βέλος ἐστὶ ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον " +
                "χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται.", witFragment);

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
        String wit = Util.getValueFromJson(jerseyResponse, "text");
        assertEquals(bText, wit);
    }


    @After
    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
        super.tearDown();
    }

}
