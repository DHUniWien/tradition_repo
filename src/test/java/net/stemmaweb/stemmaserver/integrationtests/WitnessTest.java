package net.stemmaweb.stemmaserver.integrationtests;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.SectionModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.model.WitnessTextModel;
import net.stemmaweb.rest.*;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;

import net.stemmaweb.stemmaserver.Util;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.graphdb.*;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import org.neo4j.test.TestGraphDatabaseFactory;

import static org.junit.Assert.*;

/**
 * 
 * Contains all tests for the api calls related to witnesses.
 * 
 * @author PSE FS 2015 Team2
 *
 */
public class WitnessTest {
    private String tradId;
    private GraphDatabaseService db;

    /*
     * JerseyTest is the test environment to Test api calls it provides a
     * grizzly http service
     */
    private JerseyTest jerseyTest;


    @Before
    public void setUp() throws Exception {
        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        Util.setupTestDB(db, "1");

        // Create a JerseyTestServer for the necessary REST API calls
        Root webResource = new Root();
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(webResource)
                .create();
        jerseyTest.setUp();

         // load a tradition to the test DB
        tradId = createTraditionFromFile("Tradition", "src/TestFiles/testTradition.xml");
    }

    private String createTraditionFromFile(String tName, String fName) {

        ClientResponse jerseyResult = null;
        try {
            jerseyResult = Util.createTraditionFromFileOrString(jerseyTest, tName, "LR", "1", fName, "stemmaweb");
        } catch (Exception e) {
            fail();
        }
        String tradId = Util.getValueFromJson(jerseyResult, "tradId");
        assert(tradId.length() != 0);
        return tradId;
    }

    @Test
    public void witnessAsTextTestA() {
        String expectedText = "when april with his showers sweet with "
                + "fruit the drought of march has pierced unto the root";
        Response resp = new Witness(tradId, "A").getWitnessAsText();
        assertEquals(expectedText, ((WitnessTextModel) resp.getEntity()).getText());

        String returnedText = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/witness/A/text")
                .get(String.class);
        assertEquals(constructResult(expectedText), returnedText);
    }

    @Test
    public void witnessAsTextNotExistingTest() {
        ClientResponse response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/witness/D/text")
                .get(ClientResponse.class);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
                response.getStatus());
        assertEquals("No witness path found for this sigil", Util.getValueFromJson(response, "error"));
    }

    @Test
    public void witnessAsTextTestB() {
        String expectedText = "when showers sweet with april fruit the march "
                + "of drought has pierced to the root";
        Response resp = new Witness(tradId, "B").getWitnessAsText();
        assertEquals(expectedText, ((WitnessTextModel) resp.getEntity()).getText());

        String returnedText = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/witness/B/text")
                .get(String.class);
        assertEquals(constructResult(expectedText), returnedText);
    }

    @Test
    public void witnessAsTextWithJoins() {
        String expectedText = "the quick brown fox jumped over the lazy dogs.";
        String foxId = null;
        try {
            foxId = Util.getValueFromJson(
                    Util.createTraditionFromFileOrString(jerseyTest, "quick brown fox", "LR",
                    "1", "src/TestFiles/quick_brown_fox.xml", "collatex"), "tradId");
        } catch (Exception e) {
            fail();
        }
        WitnessTextModel returnedText = (WitnessTextModel) new Witness(foxId, "w1").getWitnessAsText().getEntity();
        assertNotEquals(expectedText, returnedText.getText());

        // Find the reading that is the period
        Node period;
        try (Transaction tx = db.beginTx()) {
            period = db.findNode(Nodes.READING, "text", ". ");
            assertNotNull(period);
            period.setProperty("join_prior", true);
            tx.success();
        }
        returnedText = (WitnessTextModel) new Witness(foxId, "w1").getWitnessAsText().getEntity();
        assertEquals(expectedText, returnedText.getText());

        // Now find its predecessors and mark them as join_next
        try (Transaction tx = db.beginTx()) {
            for (Relationship r : period.getRelationships(Direction.INCOMING, ERelations.SEQUENCE)) {
                Node n = r.getStartNode();
                n.setProperty("join_next", true);
            }
            tx.success();
        }
        returnedText = (WitnessTextModel) new Witness(foxId, "w1").getWitnessAsText().getEntity();
        assertEquals(expectedText, returnedText.getText());

        try (Transaction tx = db.beginTx()) {
            period.removeProperty("join_prior");
            tx.success();
        }
        returnedText = (WitnessTextModel) new Witness(foxId, "w1").getWitnessAsText().getEntity();
        assertEquals(expectedText, returnedText.getText());
    }

    @Test
    public void witnessAsListTest() {
        String[] texts = { "when", "april", "with", "his", "showers", "sweet",
                "with", "fruit", "the", "drought", "of", "march", "has",
                "pierced", "unto", "the", "root" };
        List<ReadingModel> listOfReadings = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/witness/A/readings")
                .get(new GenericType<List<ReadingModel>>() {
                });
        assertEquals(texts.length, listOfReadings.size());
        for (int i = 0; i < listOfReadings.size(); i++) {
            assertEquals(texts[i], listOfReadings.get(i).getText());
        }
    }

    @Test
    public void witnessAsListNotExistingTest() {
        ClientResponse response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/witness/D/readings")
                .get(ClientResponse.class);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        assertEquals("No witness path found for this sigil", Util.getValueFromJson(response, "error"));
    }

    @Test
    public void witnessBetweenRanksTest() {

        String expectedText = constructResult("april with his showers");
        String response = jerseyTest.resource()
                .path("/tradition/" + tradId + "/witness/A/text")
                .queryParam("start", "2")
                .queryParam("end", "5")
                .get(String.class);
        assertEquals(expectedText, response);
    }

    @Test
    public void getWitnessTest() {
        // Get a witness
        WitnessModel witnessA = jerseyTest.resource().path("/tradition/" + tradId + "/witness/A")
                .get(WitnessModel.class);
        assertEquals("A", witnessA.getSigil());
        assertNotNull(witnessA.getId());
        try {
            Long ourId = Long.valueOf(witnessA.getId());
            assertTrue(ourId > 0);
        } catch (NumberFormatException n) {
            fail();
        }

        // Add another tradition with witness A
        String secondTradId = createTraditionFromFile("Chaucer", "src/TestFiles/Collatex-16.xml");
        assertNotNull(secondTradId);

        // Now try getting our same witness again
        ClientResponse jerseyResponse = jerseyTest.resource().path("/tradition/" + tradId + "/witness/A")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        WitnessModel alsoA = jerseyResponse.getEntity(WitnessModel.class);
        assertEquals(witnessA.getId(), alsoA.getId());
    }

    @Test
    public void lookupWitnessById() {
        // Try it with a good ID
        WitnessModel witnessA = jerseyTest.resource()
                .path("/tradition/" + tradId + "/witness/A")
                .get(WitnessModel.class);
        String aId = witnessA.getId();
        WitnessModel aById = jerseyTest.resource()
                .path("/tradition/" + tradId + "/witness/" + aId)
                .get(WitnessModel.class);
        assertEquals(witnessA.getSigil(), aById.getSigil());
        assertEquals(witnessA.getId(), aById.getId());

        // Now try it with a bad ID
        ClientResponse response = jerseyTest.resource()
                .path("/tradition/" + tradId + "/witness/ABCD")
                .get(ClientResponse.class);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

        // Now try it with a bad numeric ID
        response = jerseyTest.resource()
                .path("/tradition/" + tradId + "/witness/12345")
                .get(ClientResponse.class);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

        // Now try it with a numeric ID of a node that is not a witness node
        List<SectionModel> ourSections = jerseyTest.resource()
                .path("/tradition/" + tradId + "/sections")
                .get(new GenericType<List<SectionModel>>() {});
        String sectId = ourSections.get(0).getId();
        response = jerseyTest.resource()
                .path("/tradition/" + tradId + "/witness/" + sectId)
                .get(ClientResponse.class);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void deleteAWitness() {
        // Get all the readings we have
        HashSet<String> remaining = new HashSet<>();
        remaining.addAll(jerseyTest.resource().path("/tradition/" + tradId + "/witness/B/readings")
                .get(new GenericType<List<ReadingModel>>() {})
                .stream().map(ReadingModel::getId).collect(Collectors.toList()));
        remaining.addAll(jerseyTest.resource().path("/tradition/" + tradId + "/witness/C/readings")
                .get(new GenericType<List<ReadingModel>>() {})
                .stream().map(ReadingModel::getId).collect(Collectors.toList()));
        // Try deleting witness A
        ClientResponse result = jerseyTest.resource()
                .path("/tradition/" + tradId + "/witness/A").delete(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
        // Check that it is no longer in the witness list
        assertTrue(jerseyTest.resource().path("/tradition/" + tradId + "/witnesses")
                .get(new GenericType<List<WitnessModel>>(){}).stream().noneMatch(x -> x.getSigil().equals("A")));
        // Check that all the remaining readings are in our pre-collected set
        for (ReadingModel rm : jerseyTest.resource().path("/tradition/" + tradId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {}))
            if (!rm.getIs_end() && !rm.getIs_start())
                assertTrue(remaining.contains(rm.getId()));

        // Now add a witness out-of-band, that doesn't have any particular data, to make sure we can
        // delete errant witnesses
        Long bogusId;
        try (Transaction tx = db.beginTx()) {
            Node traditionNode = db.findNode(Nodes.TRADITION, "id", tradId);
            Node bogus = db.createNode(Nodes.WITNESS);
            bogusId = bogus.getId();
            bogus.setProperty("hypothetical", false);
            bogus.setProperty("sigil", "n\":\"RJKYRSKZ");
            traditionNode.createRelationshipTo(bogus, ERelations.HAS_WITNESS);
            tx.success();
        }
        assertNotNull(bogusId);
        assertEquals(3, jerseyTest.resource().path("/tradition/" + tradId + "/witnesses")
                .get(new GenericType<List<WitnessModel>>(){}).size());
        result = jerseyTest.resource()
                .path(String.format("/tradition/%s/witness/%d", tradId, bogusId))
                .delete(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
        assertEquals(2, jerseyTest.resource().path("/tradition/" + tradId + "/witnesses")
                .get(new GenericType<List<WitnessModel>>(){}).size());


        // Now add another tradition with overlapping sigla and try to delete its witness B
        String secondTradId = createTraditionFromFile("Chaucer", "src/TestFiles/Collatex-16.xml");
        assertNotNull(secondTradId);
        result = jerseyTest.resource().path(String.format("/tradition/%s/witness/B", secondTradId))
                .delete(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
    }

    @Ignore
    @Test
    public void deleteWitnessFromStemma() {

    }

    @Test
    public void createWitnessInvalidSigil() {
        ClientResponse r = Util.createTraditionFromFileOrString(jerseyTest, "592th", "LR", "1",
                "src/TestFiles/592th.xml", "graphml");
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        String error = r.getEntity(String.class);
        assertEquals("The character \" may not appear in a sigil name.", error);
    }

    /**
     * as ranks are adjusted should give same result as previous test
     */
    @Test
    public void witnessBetweenRanksWrongWayTest() {
        String expectedText = constructResult("april with his showers");
        String response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/witness/A/text")
                .queryParam("start", "5")
                .queryParam("end", "2")
                .get(String.class);
        assertEquals(expectedText, response);
    }

    /**
     * gives same ranks for start and end should return error
     */
    @Test
    public void witnessBetweenRanksSameRanksTest() {
        ClientResponse response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/witness/A/text")
                .queryParam("start", "5")
                .queryParam("end", "5")
                .get(ClientResponse.class);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("end-rank is equal to start-rank", Util.getValueFromJson(response, "error"));
    }

    //if the end rank is too high, will return all the readings between start rank to end of witness
    @Test
    public void witnessBetweenRanksTooHighEndRankTest() {
        String expectedText = constructResult("showers sweet with fruit the drought of march has pierced unto the root");
        String response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/witness/A/text")
                .queryParam("start", "5")
                .queryParam("end", "30")
                .get(String.class);
        assertEquals(expectedText, response);
    }
    /**
     * test if the tradition node exists
     */
    @Test
    public void traditionNodeExistsTest() {
        try (Transaction tx = db.beginTx()) {
            ResourceIterator<Node> tradNodesIt = db.findNodes(Nodes.TRADITION, "name", "Tradition");
            assertTrue(tradNodesIt.hasNext());
            tx.success();
        }
    }

    /**
     * test if the tradition end node exists
     */
    @Test
    public void traditionEndNodeExistsTest() {
        try (Transaction tx = db.beginTx()) {
            ResourceIterator<Node> tradNodesIt = db.findNodes(Nodes.READING, "text", "#END#");
            assertTrue(tradNodesIt.hasNext());
            tx.success();
        }
    }

    /**
     * test what text gets returned when a witness correction layer is involved
     */
    @Test
    public void correctedWitnessTextTest() {
        // Our expected values
        String qText = "Ἡ περὶ τοῦ ἁγίου πνεύματος βλασφημία αὐτόθεν ἔχει τὴν λύσιν· ὁ δὲ δεύτερος ἐστὶν οὗτος· ὅτάν τις ἐν ἁμαρτίαις ἐνεχόμενος, ἀκούων δὲ τοῦ κυρίου λέγοντος μὴ κρίνετε φοβούμενος οὐδένα κρίνῃ ἐν τῇ ἐξετάσει τῶν βεβιωμένων ὡς φύλαξ τῆς ἐντολῆς οὐ κρίνεται· εἰ μὴ τὸ γενέσθαι πιστόν, εἰκότως ὅταν ἐν ἁμαρτίαις τίς ὢν οἰκονομῆται ἐκ τῆς προνοίας ἐν συμφοραῖς, ἐν ἀνάγκαις, ἐν νόσοις ὡς οὐκ οἶδε γὰρ διὰ τῶν τοιούτων καθαίρει αὐτὸν ὁ θεός οὖν τῷ ἐν ἀπιστίᾳ τὸν βίον καταλύσαντι οὔτε ἐνταῦθα οὔτε ἐν τῷ μέλλοντι ἀφεθήσεται τῆς ἀπιστίας καὶ ἀθεΐας ἡ ἁμαρτία. νείλου τοῦ νύσσης Ἤκουσά που τῆς ἁγίας γραφῆς κατακρινούσης ἐκείνους, οἳ κατὰ τοῦ θεοῦ βλασφημίας αἴτιοι γίνονται. Οὐαὶ γὰρ φησὶν δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο γὰρ χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν. Νείλου μοναχοῦ Ὄψις γυναικὸς βέλος ἐστὶ πεφαρμακευμένον ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. Θάλλει βοτάνη ἑστῶσα παρ᾽ ὕδατι, καὶ πάθος ἀκολασίας, ἐν συντυχίαις γυναικῶν.";
        String eText = "Ἡ περὶ τῆς τοῦ πνεύματος τοῦ ἁγίου βλασφημίας ἀπορία αὐτόθεν ἔχει τὴν λύσιν· ὁ δὲ δεύτερος ἐστὶν οὗτος· ὅτάν τις ἐν ἁμαρτίαις ἐνεχόμενος, ἀκούων δὲ τοῦ κυρίου λέγοντος μὴ κρίνετε φοβούμενος οὐδένα κρίνει ἐν τῇ ἐξετάσει τῶν βεβιωμένων ὡς φύλαξ τῆς ἐντολῆς οὐ κρίνεται· εἰ μὴ τὸ γενέσθαι πιστόν, εἰκότως ὅταν ἐν ἁμαρτίαις τίς ὢν οἰκονομεῖται ἐκ τῆς προνοίας ἐν συμφοραῖς, ἐν ἀνάγκαις, ἐν νόσοις ὡς οὐκ οἶδε γὰρ διὰ τῶν τοιούτων καθαίρει αὐτὸν ὁ θεός τῷ ἐν ἀπιστίᾳ τὸν βίον κατακλύσαντι οὔτε ἐνταῦθα οὔτε ἐν τῷ μέλλοντι ἀφεθήσεται τῆς ἀπιστίας καὶ ἀθεΐας ἡ ἁμαρτία. Ἰσιδώρου Πηλουσίου Γρηγορίου Νύσης Ἤκουσά που τῆς ἁγίας γραφῆς κατακρινούσης ἐκείνους, οἳ κατὰ τῆς τοῦ θεοῦ βλασφημίας αἴτιοι γίνονται. Οὐαὶ γὰρ φησὶν δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν. Νείλου μοναχοῦ Ὄψις γυναικὸς βέλος ἐστὶ πεφαρμακευμένον ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. Θάλλει βοτάνη ἑστῶσα παρ᾽ ὕδατι, καὶ πάθος ἀκολασίας, ἐν συντυχίαις γυναικῶν.";
        String tText = "Ἡ περὶ τῆς τοῦ πνεύματος τοῦ ἁγίου βλασφημίας ἀπορία αὐτόθι ἔχει τὴν λύσιν· ὁ δὲ δεύτερος ἐστὶν οὗτος· ὅτάν τις ἐν ἁμαρτίαις ἐνεχόμενος, ἀκούων δὲ τοῦ κυρίου λέγοντος μὴ κρίνεται φοβούμενος οὐδένα κρίνει ἐν τῇ ἐξετάσει τῶν βεβιωμένων ὡς φύλαξ τῆς ἐντολῆς οὐ κρίνεται· εἰ μὴ τὸ γενέσθαι πιστόν, εἰκότως ὅταν ἐν ἁμαρτίαις τίς ὢν οἰκονομεῖται ἐκ τῆς προνοίας ἐν συμφοραῖς, ἐν ἀνάγκαις, ἐν νόσοις ὡς οὐκ οἶδε γὰρ διὰ τῶν τοιούτων καθαίρει αὐτὸν ὁ θεός οὖν τῷ ἐν ἀπιστίᾳ τὸν βίον κατακλείσαντι οὔτε ἐνταῦθα οὔτε ἐν τῷ μέλλοντι ἀφεθήσεται τῆς ἀπιστίας καὶ ἀθεΐας ἡ ἁμαρτία. Γρηγορίου Νύσης Ἤκουσά που τῆς ἁγίας γραφῆς κατακρινούσης ἐκείνους, οἳ κατὰ τῆς τοῦ θεοῦ βλασφημίας αἴτιοι γίνονται. Οὐαὶ γὰρ φησὶν δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν. Νείλου μοναχοῦ Ὄψις γυναικὸς μέλος ἐστὶ πεφαρμακευμένον ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. Θάλλει βοτάνη ἐστῶσα παρ᾽ ὕδατι, καὶ πάθος ἀκολασίας, ἐν συντυχίαις γυναικῶν.";
        String qacText = "Ἡ περὶ τοῦ ἁγίου πνεύματος βλασφημία αὐτόθεν ἔχει τὴν λύσιν· ὁ δὲ δεύτερος ἐστὶν οὗτος· ὅτάν τις ἐν ἁμαρτίαις ἐνεχόμενος, ἀκούων δὲ τοῦ κυρίου λέγοντος μὴ κρίνετε φοβούμενος οὐδένα κρίνει ἐν τῇ ἐξετάσει τῶν βεβιωμένων ὡς φύλαξ τῆς ἐντολῆς οὐ κρίνεται· εἰ μὴ τὸ γενέσθαι πιστόν, εἰκότως ὅταν ἐν ἁμαρτίαις τίς ὢν οἰκονομῆται ἐκ τῆς προνοίας ἐν συμφοραῖς, ἐν ἀνάγκαις, ἐν νόσοις ὡς οὐκ οἶδε γὰρ διὰ τῶν τοιούτων καθαίρει αὐτὸν ὁ θεός οὖν τῷ ἐν ἀπιστίᾳ τὸν βίον κατακλείσαντι οὔτε ἐνταῦθα οὔτε ἐν τῷ μέλλοντι ἀφεθήσεται τῆς ἀπιστίας καὶ ἀθεΐας ἡ ἁμαρτία. νείλου τοῦ νύσσης Ἤκουσά που τῆς ἁγίας γραφῆς κατακινούσης ἐκείνους, οἳ κατὰ τοῦ θεοῦ βλασφημίας αἴτιοι γίνονται. Οὐαὶ γὰρ φησὶν δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο γὰρ χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν. Νείλου μοναχοῦ Ὄψις γυναικὸς βέλος ἐστὶ πεφαρμακευμένον ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. Θάλλει βοτάνη ἑστῶσα παρ᾽ ὕδατι, καὶ πάθος ἀκολασίας, ἐν συντυχίαις γυναικῶν.";
        String eacText = "Ἡ περὶ τῆς τοῦ πνεύματος τοῦ ἁγίου βλασφημίας ἀπορία αὐτόθεν ἔχει τὴν λύσιν· ὁ δὲ δεύτερος ἐστὶν οὗτος· ὅτάν τις ἐν ἁμαρτίαις ἐνεχόμενος, ἀκούων δὲ τοῦ κυρίου λέγοντος μὴ κρίνετε φοβούμενος οὐδένα κρίνει ἐν τῇ ἐξετάσει τῶν βεβιωμένων ὡς φύλαξ τῆς ἐντολῆς οὐ κρίνεται· εἰ μὴ τὸ γενέσθαι πιστόν, εἰκότως ὅταν ἐν ἁμαρτίαις τίς ὢν οἰκονομεῖται ἐκ τῆς προνοίας ἐν συμφοραῖς, ἐν ἀνάγκαις, ἐν νόσοις ὡς οὐκ οἶδε γὰρ διὰ τῶν τοιούτων καθαίρει αὐτὸν ὁ θεός τῷ ἐν ἀπιστίᾳ τὸν βίον καταλύσαντι οὔτε ἐνταῦθα οὔτε ἐν τῷ μέλλοντι ἀφεθήσεται τῆς ἀπιστίας καὶ ἀθεΐας ἡ ἁμαρτία. Ἰσιδώρου Πηλουσίου Γρηγορίου Νύσης Ἤκουσά που τῆς ἁγίας γραφῆς κατακρινούσης ἐκείνους, οἳ κατὰ τῆς τοῦ θεοῦ βλασφημίας αἴτιοι γίνονται. Οὐαὶ γὰρ φησὶν δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν. Νείλου μοναχοῦ Ὄψις γυναικὸς βέλος ἐστὶ πεφαρμακευμένον ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. Θάλλει βοτάνη ἑστῶσα παρ᾽ ὕδατι, καὶ πάθος ἀκολασίας, ἐν συντυχίαις γυναικῶν.";
        String tacText = "Ἡ περὶ τῆς τοῦ πνεύματος τοῦ ἁγίου βλασφημίας ἀπορία αὐτόθι ἔχει τὴν λύσιν· ὁ δὲ δεύτερος ἐστὶν οὗτος· ὅτάν τις ἐν ἁμαρτίαις ἐνεχόμενος, ἀκούων δὲ τοῦ κυρίου λέγοντος μὴ κρίνεται φοβούμενος οὐδένα κρίνει ἐν τῇ ἐξετάσει τῶν βεβιωμένων ὡς φύλαξ τῆς ἐντολῆς οὐ κρίνεται· εἰ μὴ τὸ γενέσθαι πιστόν, εἰκότως ὅταν ἐν ἁμαρτίαις τίς ὢν οἰκονομεῖται ἐκ τῆς προνοίας ἐν συμφοραῖς, ἐν ἀνάγκαις, ἐν νόσοις ὡς οὐκ οἶδε γὰρ διὰ τῶν τοιούτων καθαίρει αὐτὸν ὁ θεός οὖν τῷ ἐν ἀπιστίᾳ τὸν βίον κατακλείσαντι οὔτε ἐνταῦθα οὔτε ἐν τῷ μέλλοντι ἀφεθήσεται τῆς ἀπιστίας καὶ ἀθεΐας ἡ ἁμαρτία. Γρηγορίου Νύσης Ἤκουσά που τῆς ἁγίας γραφῆς κατακρινούσης ἐκείνους, οἳ κατὰ τῆς τοῦ θεοῦ βλασφημίας αἴτιοι γίνονται. Οὐαὶ γὰρ φησὶν δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν. Νείλου μοναχοῦ Ὄψις γυναικὸς μέλος ἐστὶ πεφαρμακευμένον ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τὸν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. Θάλλει βοτάνη ἐστῶσα παρ᾽ ὕδατι, καὶ πάθος ἀκολασίας, ἐν συντυχίαις γυναικῶν.";

        // Create the tradition in question
        String newId = createTraditionFromFile("Florilegium", "src/TestFiles/florilegium_graphml.xml");
        // Now get the witness text for each of our complex sigla.
        String response = jerseyTest
                .resource()
                .path("/tradition/" + newId + "/witness/Q/text")
                .get(String.class);
        assertEquals(constructResult(qText), response);
        response = jerseyTest
                .resource()
                .path("/tradition/" + newId + "/witness/E/text")
                .get(String.class);
        assertEquals(constructResult(eText), response);
        response = jerseyTest
                .resource()
                .path("/tradition/" + newId + "/witness/T/text")
                .get(String.class);
        assertEquals(constructResult(tText), response);

        // Now try to get the uncorrected text.
        response = jerseyTest
                .resource()
                .path("/tradition/" + newId + "/witness/Q/text")
                .queryParam("layer", "a.c.")
                .get(String.class);
        assertEquals(constructResult(qacText), response);
        response = jerseyTest
                .resource()
                .path("/tradition/" + newId + "/witness/E/text")
                .queryParam("layer", "a.c.")
                .get(String.class);
        assertEquals(constructResult(eacText), response);
        response = jerseyTest
                .resource()
                .path("/tradition/" + newId + "/witness/T/text")
                .queryParam("layer", "a.c.")
                .get(String.class);
        assertEquals(constructResult(tacText), response);

    }

    private String constructResult (String text) {
        return String.format("{\"text\":\"%s\"}", text);
    }

    /*
     * Shut down the jersey server
     *
     * @throws Exception
     */
    @After
    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
    }
}
