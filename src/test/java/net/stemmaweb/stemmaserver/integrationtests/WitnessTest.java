package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.SectionModel;
import net.stemmaweb.model.TextSequenceModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Root;
import net.stemmaweb.rest.Witness;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;

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

        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(Root.class)
                .create();
        jerseyTest.setUp();

         // load a tradition to the test DB
        tradId = createTraditionFromFile("Tradition", "src/TestFiles/testTradition.xml");
    }

    private String createTraditionFromFile(String tName, String fName) {

        Response jerseyResult = null;
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
        assertEquals(expectedText, ((TextSequenceModel) resp.getEntity()).getText());

        String returnedText = jerseyTest
                .target("/tradition/" + tradId + "/witness/A/text")
                .request()
                .get(String.class);
        assertEquals(constructResult(expectedText), returnedText);
    }

    @Test
    public void witnessAsTextNotExistingTest() {
        Response response = jerseyTest
                .target("/tradition/" + tradId + "/witness/D/text")
                .request()
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
                response.getStatus());
        assertEquals("No witness path found for this sigil", Util.getValueFromJson(response, "error"));
    }

    @Test
    public void witnessAsTextTestB() {
        String expectedText = "when showers sweet with april fruit the march "
                + "of drought has pierced to the root";
        Response resp = new Witness(tradId, "B").getWitnessAsText();
        assertEquals(expectedText, ((TextSequenceModel) resp.getEntity()).getText());

        String returnedText = jerseyTest
                .target("/tradition/" + tradId + "/witness/B/text")
                .request()
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
        TextSequenceModel returnedText = (TextSequenceModel) new Witness(foxId, "w1").getWitnessAsText().getEntity();
        assertNotEquals(expectedText, returnedText.getText());

        // Find the reading that is the period
        Node period;
        try (Transaction tx = db.beginTx()) {
            period = tx.findNode(Nodes.READING, "text", ". ");
            assertNotNull(period);
            period.setProperty("join_prior", true);
            tx.close();
        }
        returnedText = (TextSequenceModel) new Witness(foxId, "w1").getWitnessAsText().getEntity();
        assertEquals(expectedText, returnedText.getText());

        // Now find its predecessors and mark them as join_next
        try (Transaction tx = db.beginTx()) {
            for (Relationship r : period.getRelationships(Direction.INCOMING, ERelations.SEQUENCE)) {
                Node n = r.getStartNode();
                n.setProperty("join_next", true);
            }
            tx.close();
        }
        returnedText = (TextSequenceModel) new Witness(foxId, "w1").getWitnessAsText().getEntity();
        assertEquals(expectedText, returnedText.getText());

        try (Transaction tx = db.beginTx()) {
            period.removeProperty("join_prior");
            tx.commit();
        }
        returnedText = (TextSequenceModel) new Witness(foxId, "w1").getWitnessAsText().getEntity();
        assertEquals(expectedText, returnedText.getText());
    }

    @Test
    public void witnessAsListTest() {
        String[] texts = { "when", "april", "with", "his", "showers", "sweet",
                "with", "fruit", "the", "drought", "of", "march", "has",
                "pierced", "unto", "the", "root" };
        List<ReadingModel> listOfReadings = jerseyTest
                .target("/tradition/" + tradId + "/witness/A/readings")
                .request()
                .get(new GenericType<List<ReadingModel>>() {
                });
        assertEquals(texts.length, listOfReadings.size());
        for (int i = 0; i < listOfReadings.size(); i++) {
            assertEquals(texts[i], listOfReadings.get(i).getText());
        }
    }

    @Test
    public void witnessAsListNotExistingTest() {
        Response response = jerseyTest
                .target("/tradition/" + tradId + "/witness/D/readings")
                .request()
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        assertEquals("No witness path found for this sigil", Util.getValueFromJson(response, "error"));
    }

    @Test
    public void witnessBetweenRanksTest() {

        String expectedText = constructResult("april with his showers");
        String response = jerseyTest.target("/tradition/" + tradId + "/witness/A/text")
                .queryParam("start", "2")
                .queryParam("end", "5")
                .request()
                .get(String.class);
        assertEquals(expectedText, response);
    }

    @Test
    public void getWitnessTest() {
        // Get a witness
        WitnessModel witnessA = jerseyTest.target("/tradition/" + tradId + "/witness/A")
                .request()
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
        Response jerseyResponse = jerseyTest.target("/tradition/" + tradId + "/witness/A")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        WitnessModel alsoA = jerseyResponse.readEntity(WitnessModel.class);
        assertEquals(witnessA.getId(), alsoA.getId());
    }

    @Test
    public void lookupWitnessById() {
        // Try it with a good ID
        WitnessModel witnessA = jerseyTest.target("/tradition/" + tradId + "/witness/A")
                .request()
                .get(WitnessModel.class);
        String aId = witnessA.getId();
        WitnessModel aById = jerseyTest.target("/tradition/" + tradId + "/witness/" + aId)
                .request()
                .get(WitnessModel.class);
        assertEquals(witnessA.getSigil(), aById.getSigil());
        assertEquals(witnessA.getId(), aById.getId());

        // Now try it with a bad ID
        Response response = jerseyTest.target("/tradition/" + tradId + "/witness/ABCD")
                .request()
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

        // Now try it with a bad numeric ID
        response = jerseyTest.target("/tradition/" + tradId + "/witness/12345")
                .request()
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

        // Now try it with a numeric ID of a node that is not a witness node
        List<SectionModel> ourSections = jerseyTest.target("/tradition/" + tradId + "/sections")
                .request()
                .get(new GenericType<List<SectionModel>>() {});
        String sectId = ourSections.get(0).getId();
        response = jerseyTest.target("/tradition/" + tradId + "/witness/" + sectId)
                .request()
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void deleteAWitness() {
        // Get all the readings we have
        HashSet<String> remaining = new HashSet<>();
        remaining.addAll(jerseyTest.target("/tradition/" + tradId + "/witness/B/readings")
                .request()
                .get(new GenericType<List<ReadingModel>>() {})
                .stream().map(ReadingModel::getId).collect(Collectors.toList()));
        remaining.addAll(jerseyTest.target("/tradition/" + tradId + "/witness/C/readings")
                .request()
                .get(new GenericType<List<ReadingModel>>() {})
                .stream().map(ReadingModel::getId).collect(Collectors.toList()));
        // Try deleting witness A
        Response result = jerseyTest.target("/tradition/" + tradId + "/witness/A")
                .request()
                .delete();
        assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
        // Check that it is no longer in the witness list
        assertTrue(jerseyTest.target("/tradition/" + tradId + "/witnesses")
                .request()
                .get(new GenericType<List<WitnessModel>>(){}).stream().noneMatch(x -> x.getSigil().equals("A")));
        // Check that all the remaining readings are in our pre-collected set
        for (ReadingModel rm : jerseyTest.target("/tradition/" + tradId + "/readings")
                .request()
                .get(new GenericType<List<ReadingModel>>() {}))
            if (!rm.getIs_end() && !rm.getIs_start())
                assertTrue(remaining.contains(rm.getId()));

        // Now add a witness out-of-band, that doesn't have any particular data, to make sure we can
        // delete errant witnesses
        String bogusId;
        try (Transaction tx = db.beginTx()) {
            Node traditionNode = tx.findNode(Nodes.TRADITION, "id", tradId);
            Node bogus = tx.createNode(Nodes.WITNESS);
            bogusId = bogus.getElementId();
            bogus.setProperty("hypothetical", false);
            bogus.setProperty("sigil", "n\":\"RJKYRSKZ");
            traditionNode.createRelationshipTo(bogus, ERelations.HAS_WITNESS);
            tx.commit();
        }
        assertNotNull(bogusId);
        assertEquals(3, jerseyTest.target("/tradition/" + tradId + "/witnesses")
                .request()
                .get(new GenericType<List<WitnessModel>>(){}).size());
        result = jerseyTest.target(String.format("/tradition/%s/witness/%d", tradId, bogusId))
                .request()
                .delete();
        assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
        assertEquals(2, jerseyTest.target("/tradition/" + tradId + "/witnesses")
                .request()
                .get(new GenericType<List<WitnessModel>>(){}).size());


        // Now add another tradition with overlapping sigla and try to delete its witness B
        String secondTradId = createTraditionFromFile("Chaucer", "src/TestFiles/Collatex-16.xml");
        assertNotNull(secondTradId);
        result = jerseyTest.target(String.format("/tradition/%s/witness/B", secondTradId))
                .request()
                .delete();
        assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
    }

    @Ignore
    @Test
    public void deleteWitnessFromStemma() {

    }

    @Test
    public void createWitnessInvalidSigil() {
        Response r = Util.createTraditionFromFileOrString(jerseyTest, "592th", "LR", "1",
                "src/TestFiles/592th.xml", "graphmlsingle");
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        String error = Util.getValueFromJson(r, "error");
        assertEquals("The character \" may not appear in a sigil name.", error);
    }

    /**
     * as ranks are adjusted should give same result as previous test
     */
    @Test
    public void witnessBetweenRanksWrongWayTest() {
        String expectedText = constructResult("april with his showers");
        String response = jerseyTest
                .target("/tradition/" + tradId + "/witness/A/text")
                .queryParam("start", "5")
                .queryParam("end", "2")
                .request()
                .get(String.class);
        assertEquals(expectedText, response);
    }

    /**
     * gives same ranks for start and end should return error
     */
    @Test
    public void witnessBetweenRanksSameRanksTest() {
        Response response = jerseyTest
                .target("/tradition/" + tradId + "/witness/A/text")
                .queryParam("start", "5")
                .queryParam("end", "5")
                .request()
                .get();
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("end-rank is equal to start-rank", Util.getValueFromJson(response, "error"));
    }

    //if the end rank is too high, will return all the readings between start rank to end of witness
    @Test
    public void witnessBetweenRanksTooHighEndRankTest() {
        String expectedText = constructResult("showers sweet with fruit the drought of march has pierced unto the root");
        String response = jerseyTest
                .target("/tradition/" + tradId + "/witness/A/text")
                .queryParam("start", "5")
                .queryParam("end", "30")
                .request()
                .get(String.class);
        assertEquals(expectedText, response);
    }
    /**
     * test if the tradition node exists
     */
    @Test
    public void traditionNodeExistsTest() {
        try (Transaction tx = db.beginTx()) {
            ResourceIterator<Node> tradNodesIt = tx.findNodes(Nodes.TRADITION, "name", "Tradition");
            assertTrue(tradNodesIt.hasNext());
            tx.close();
        }
    }

    /**
     * test if the tradition end node exists
     */
    @Test
    public void traditionEndNodeExistsTest() {
        try (Transaction tx = db.beginTx()) {
            ResourceIterator<Node> tradNodesIt = tx.findNodes(Nodes.READING, "text", "#END#");
            assertTrue(tradNodesIt.hasNext());
            tx.close();
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
                .target("/tradition/" + newId + "/witness/Q/text")
                .request()
                .get(String.class);
        assertEquals(constructResult(qText), response);
        response = jerseyTest
                .target("/tradition/" + newId + "/witness/E/text")
                .request()
                .get(String.class);
        assertEquals(constructResult(eText), response);
        response = jerseyTest
                .target("/tradition/" + newId + "/witness/T/text")
                .request()
                .get(String.class);
        assertEquals(constructResult(tText), response);

        // Now try to get the uncorrected text.
        response = jerseyTest
                .target("/tradition/" + newId + "/witness/Q/text")
                .queryParam("layer", "a.c.")
                .request()
                .get(String.class);
        assertEquals(constructResult(qacText), response);
        response = jerseyTest
                .target("/tradition/" + newId + "/witness/E/text")
                .queryParam("layer", "a.c.")
                .request()
                .get(String.class);
        assertEquals(constructResult(eacText), response);
        response = jerseyTest
                .target("/tradition/" + newId + "/witness/T/text")
                .queryParam("layer", "a.c.")
                .request()
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
