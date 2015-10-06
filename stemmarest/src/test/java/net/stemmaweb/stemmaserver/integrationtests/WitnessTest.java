package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.stemmaweb.model.KeyPropertyModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Witness;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.*;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import org.neo4j.test.TestGraphDatabaseFactory;

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

    private Witness witness;


    @Before
    public void setUp() throws Exception {
		File testfile = new File("src/TestXMLFiles/testTradition.xml");

        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();

        /*
         * The Resource under test. The mockDbFactory will be injected into this
         * resource.
         */
        GraphMLToNeo4JParser importResource = new GraphMLToNeo4JParser();
        witness = new Witness();


        /*
         * Populate the test database with the root node and a user with id 1
         */
        DatabaseService.createRootNode(db);
        try (Transaction tx = db.beginTx()) {
            Node rootNode = db.findNode(Nodes.ROOT, "name", "Root node");
            Node node = db.createNode(Nodes.USER);
            node.setProperty("id", "1");
            node.setProperty("isAdmin", "1");

            rootNode.createRelationshipTo(node, ERelations.SEQUENCE);
            tx.success();
        }


        /**
         * load a tradition to the test DB
         */
        try {
            importResource.parseGraphML(testfile.getPath(), "1", "Tradition");
        } catch (FileNotFoundException f) {
            // this error should not occur
            assertTrue(false);
        }
        /**
         * gets the generated id of the inserted tradition
         */
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (u:USER)--(t:TRADITION) return t");
            Iterator<Node> nodes = result.columnAs("t");
            assertTrue(nodes.hasNext());
            tradId = (String) nodes.next().getProperty("id");

            tx.success();
        }

        /*
         * Create a JersyTestServer serving the Resource under test
         */
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(witness)
                .create();
        jerseyTest.setUp();
    }

    @Test
    public void witnessAsTextTestA() {
        String expectedText = constructResult("when april with his showers sweet with "
                + "fruit the drought of march has pierced unto the root");
        Response resp = witness.getWitnessAsText(tradId, "A");
        assertEquals(expectedText, resp.getEntity());

        String returnedText = jerseyTest
                .resource()
                .path("/witness/gettext/fromtradition/" + tradId + "/ofwitness/A")
                .get(String.class);
        assertEquals(expectedText, returnedText);
    }

    @Test
    public void witnessAsTextNotExistingTest() {
        ClientResponse response = jerseyTest
                .resource()
                .path("/witness/gettext/fromtradition/" + tradId + "/ofwitness/D")
                .get(ClientResponse.class);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
                response.getStatus());
        assertEquals("no witness with this id was found",
                response.getEntity(String.class));
    }

    @Test
    public void witnessAsTextTestB() {
        String expectedText = constructResult("when showers sweet with april fruit the march "
                + "of drought has pierced to the root");
        Response resp = witness.getWitnessAsText(tradId, "B");
        assertEquals(expectedText, resp.getEntity());

        String returnedText = jerseyTest
                .resource()
                .path("/witness/gettext/fromtradition/" + tradId + "/ofwitness/B")
                .get(String.class);
        assertEquals(expectedText, returnedText);
    }

    @Test
    public void witnessAsListTest() {
        String[] texts = { "when", "april", "with", "his", "showers", "sweet",
                "with", "fruit", "the", "drought", "of", "march", "has",
                "pierced", "unto", "the", "root" };
        List<ReadingModel> listOfReadings = jerseyTest
                .resource()
                .path("/witness/getreadinglist/fromtradition/" + tradId + "/ofwitness/A")
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
                .path("/witness/getreadinglist/fromtradition/" + tradId + "/ofwitness/D")
                .get(ClientResponse.class);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
		assertEquals("no witness with this id was found", response.getEntity(String.class));
    }

    @Test
    public void witnessBetweenRanksTest() {

        String expectedText = constructResult("april with his showers");
        String response = jerseyTest.resource()
                .path("/witness/gettext/fromtradition/" + tradId + "/ofwitness/A/"
                        + "fromstartrank/2/toendrank/5")
				.get(String.class);
		assertEquals(expectedText, response);
	}

	/**
	 * as ranks are adjusted should give same result as previous test
	 */
	@Test
    public void witnessBetweenRanksWrongWayTest() {
        String expectedText = constructResult("april with his showers");
        String response = jerseyTest
                .resource()
                .path("/witness/gettext/fromtradition/" + tradId + "/ofwitness/A/"
                        + "fromstartrank/2/toendrank/5")
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
                .path("/witness/gettext/fromtradition/" + tradId + "/ofwitness/A/"
                        + "fromstartrank/5/toendrank/5")
				.get(ClientResponse.class);
		assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertEquals("end-rank is equal to start-rank", response.getEntity(String.class));
    }

    //if the end rank is too high, will return all the readings between start rank to end of witness
    @Test
    public void witnessBetweenRanksTooHighEndRankTest() {
        String expectedText = "{\"text\":\"showers sweet with fruit the drought of march has pierced unto the root\"}";
        String response = jerseyTest
                .resource()
                .path("/witness/gettext/fromtradition/" + tradId + "/ofwitness/A/"
                        + "fromstartrank/5/toendrank/30")
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
     *
     * !!NOTE!! this test will fail due to start node identification bug, until the
     * fix gets merged from the acceptancetests branch! When that is fixed, it
     * will fail for the intended reason.
     */
    @Test
    public void correctedWitnessTextTest() {
        // Our expected values
        String qText = "Ἡ περὶ τοῦ ἁγίου πνεύματος βλασφημία αὐτόθεν ἔχει τὴν λύσιν· ὁ δὲ δεύτερος ἐστὶν οὗτος· ὅτάν τις ἐν ἁμαρτίαις ἐνεχόμενος, ἀκούων δὲ τοῦ κυρίου λέγοντος μὴ κρίνετε φοβούμενος οὐδένα κρίνῃ ἐν τῇ ἐξετάσει τῶν βεβιωμένων ὡς φύλαξ τῆς ἐντολῆς οὐ κρίνεται· εἰ μὴ τὸ γενέσθαι πιστόν, εἰκότως ὅταν ἐν ἁμαρτίαις τίς ὢν οἰκονομῆται ἐκ τῆς προνοίας ἐν συμφοραῖς, ἐν ἀνάγκαις, ἐν νόσοις ὡς οὐκ οἶδε γὰρ διὰ τῶν τοιούτων καθαίρει αὐτὸν ὁ θεός οὖν τῷ ἐν ἀπιστίᾳ τὸν βίον καταλύσαντι οὔτε ἐνταῦθα οὔτε ἐν τῷ μέλλοντι ἀφεθήσεται τῆς ἀπιστίας καὶ ἀθεΐας ἡ ἁμαρτία. νείλου τοῦ νύσσης Ἤκουσά που τῆς ἁγίας γραφῆς κατακρινούσης ἐκείνους, οἳ κατὰ τοῦ θεοῦ βλασφημίας αἴτιοι γίνονται. Οὐαὶ γὰρ φησὶν δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο γὰρ χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν. Νείλου μοναχοῦ Ὄψις γυναικὸς βέλος ἐστὶ πεφαρμακευμένον ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. Θάλλει βοτάνη ἑστῶσα παρ᾽ ὕδατι, καὶ πάθος ἀκολασίας, ἐν συντυχίαις γυναικῶν.";
        String eText = "Ἡ περὶ τῆς τοῦ πνεύματος τοῦ ἁγίου βλασφημίας ἀπορία αὐτόθεν ἔχει τὴν λύσιν· ὁ δὲ δεύτερος ἐστὶν οὗτος· ὅτάν τις ἐν ἁμαρτίαις ἐνεχόμενος, ἀκούων δὲ τοῦ κυρίου λέγοντος μὴ κρίνετε φοβούμενος οὐδένα κρίνει ἐν τῇ ἐξετάσει τῶν βεβιωμένων ὡς φύλαξ τῆς ἐντολῆς οὐ κρίνεται· εἰ μὴ τὸ γενέσθαι πιστόν, εἰκότως ὅταν ἐν ἁμαρτίαις τίς ὢν οἰκονομεῖται ἐκ τῆς προνοίας ἐν συμφοραῖς, ἐν ἀνάγκαις, ἐν νόσοις ὡς οὐκ οἶδε γὰρ διὰ τῶν τοιούτων καθαίρει αὐτὸν ὁ θεός τῷ ἐν ἀπιστίᾳ τὸν βίον κατακλύσαντι οὔτε ἐνταῦθα οὔτε ἐν τῷ μέλλοντι ἀφεθήσεται τῆς ἀπιστίας καὶ ἀθεΐας ἡ ἁμαρτία. Ἰσιδώρου Πηλουσίου Γρηγορίου Νύσης Ἤκουσά που τῆς ἁγίας γραφῆς κατακρινούσης ἐκείνους, οἳ κατὰ τῆς τοῦ θεοῦ βλασφημίας αἴτιοι γίνονται. Οὐαὶ γὰρ φησὶν δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν. Νείλου μοναχοῦ Ὄψις γυναικὸς βέλος ἐστὶ πεφαρμακευμένον ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. Θάλλει βοτάνη ἑστῶσα παρ᾽ ὕδατι, καὶ πάθος ἀκολασίας, ἐν συντυχίαις γυναικῶν.";
        String tText = "Ἡ περὶ τῆς τοῦ πνεύματος τοῦ ἁγίου βλασφημίας ἀπορία αὐτόθι ἔχει τὴν λύσιν· ὁ δὲ δεύτερος ἐστὶν οὗτος· ὅτάν τις ἐν ἁμαρτίαις ἐνεχόμενος, ἀκούων δὲ τοῦ κυρίου λέγοντος μὴ κρίνεται φοβούμενος οὐδένα κρίνει ἐν τῇ ἐξετάσει τῶν βεβιωμένων ὡς φύλαξ τῆς ἐντολῆς οὐ κρίνεται· εἰ μὴ τὸ γενέσθαι πιστόν, εἰκότως ὅταν ἐν ἁμαρτίαις τίς ὢν οἰκονομεῖται ἐκ τῆς προνοίας ἐν συμφοραῖς, ἐν ἀνάγκαις, ἐν νόσοις ὡς οὐκ οἶδε γὰρ διὰ τῶν τοιούτων καθαίρει αὐτὸν ὁ θεός οὖν τῷ ἐν ἀπιστίᾳ τὸν βίον κατακλείσαντι οὔτε ἐνταῦθα οὔτε ἐν τῷ μέλλοντι ἀφεθήσεται τῆς ἀπιστίας καὶ ἀθεΐας ἡ ἁμαρτία. Γρηγορίου Νύσης Ἤκουσά που τῆς ἁγίας γραφῆς κατακρινούσης ἐκείνους, οἳ κατὰ τῆς τοῦ θεοῦ βλασφημίας αἴτιοι γίνονται. Οὐαὶ γὰρ φησὶν δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν. Νείλου μοναχοῦ Ὄψις γυναικὸς μέλος ἐστὶ πεφαρμακευμένον ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. Θάλλει βοτάνη ἐστῶσα παρ᾽ ὕδατι, καὶ πάθος ἀκολασίας, ἐν συντυχίαις γυναικῶν.";

        // Read in the tradition in question
        File testfile = new File("src/TestXMLFiles/florilegium_graphml.xml");
        GraphMLToNeo4JParser importResource = new GraphMLToNeo4JParser();
        Response parseResponse = null;
        try {
            parseResponse = importResource.parseGraphML(testfile.getPath(), "1", "Florilegium");
        } catch (FileNotFoundException f) {
            // this error should not occur
            assertTrue(false);
        }
        String newId = parseResponse.getEntity().toString().replaceAll("\\D+", "");

        // Now get the witness text for each of our complex sigla.
        String response = jerseyTest
                .resource()
                .path("/witness/gettext/fromtradition/" + newId + "/ofwitness/Q")
                .get(String.class);
        assertEquals(constructResult(qText), response);
        response = jerseyTest
                .resource()
                .path("/witness/gettext/fromtradition/" + newId + "/ofwitness/E")
                .get(String.class);
        assertEquals(constructResult(eText), response);
        response = jerseyTest
                .resource()
                .path("/witness/gettext/fromtradition/" + newId + "/ofwitness/T")
                .get(String.class);
        assertEquals(constructResult(tText), response);
    }

    private String constructResult (String text) {
        return String.format("{\"text\":\"%s\"}", text);
    }

    /**
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
