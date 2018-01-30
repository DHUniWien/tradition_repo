package net.stemmaweb.stemmaserver.integrationtests;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import net.stemmaweb.model.*;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.exporter.StemmawebExporter;

import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.Uniqueness;
import org.neo4j.test.TestGraphDatabaseFactory;

import static org.junit.Assert.*;

/**
 *
 * @author PSE FS 2015 Team2
 *
 */
public class StemmawebInputOutputTest {

    private GraphDatabaseService db;
    private StemmawebExporter exportStemmawebResource;

    private JerseyTest jerseyTest;

    @Before
    public void setUp() throws Exception {

        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        Util.setupTestDB(db, "1");

        exportStemmawebResource = new StemmawebExporter();

        // Create a JerseyTestServer for the necessary REST API calls
        Root webResource = new Root();
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(webResource)
                .create();
        jerseyTest.setUp();
    }

    /**
     * Try to import a non existent file
     */
    @Test
    public void graphMLImportNonexistentFileTest() {
        ClientResponse response = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/SapientiaFileNotExisting.xml", "stemmaweb");
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertFalse(traditionNodeExists());
    }

    /**
     * Try to import a file with errors
     */
    @Test
    public void graphMLImportXMLStreamErrorTest() {
        ClientResponse response = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/SapientiaWithError.xml", "stemmaweb");
        assertFalse(response == null);
        assertEquals(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build().getStatus(),
                    response.getStatus());
        assertFalse(traditionNodeExists());
    }

    /**
     * Import a correct file
     */
    @Test
    public void graphMLImportSuccessTest() {
        ClientResponse response = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                    "src/TestFiles/besoin.xml", "stemmaweb");
        assertEquals(Response.status(Response.Status.CREATED).build().getStatus(),
                response.getStatus());

        assertTrue(traditionNodeExists());
    }

    /**
     * test if the tradition node exists
     */
    private Boolean traditionNodeExists(){
        Boolean answer;
        try(Transaction tx = db.beginTx()) {
            ResourceIterator<Node> tradNodesIt = db.findNodes(Nodes.TRADITION, "name", "Tradition");
            answer = tradNodesIt.hasNext();
            tx.success();
        }
        return answer;
    }

    /**
     * try to export a non existent tradition
     */
    @Test
    public void graphMLExportTraditionNotFoundTest(){
        Response actualResponse = exportStemmawebResource.writeNeo4J("1002");
        assertEquals(Response.status(Response.Status.NOT_FOUND).build().getStatus(),
                actualResponse.getStatus());
    }

    /**
     * try to export a correct tradition
     */
    @Test
    public void graphMLExportSuccessTest(){
        ClientResponse response = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
            "src/TestFiles/testTradition.xml", "stemmaweb");
        String traditionId = Util.getValueFromJson(response, "tradId");

        assertNotNull(traditionId);
        Response actualResponse = exportStemmawebResource.writeNeo4J(traditionId);
        assertEquals(Response.ok().build().getStatus(), actualResponse.getStatus());

        String xmlOutput = actualResponse.getEntity().toString();
        response = Util.createTraditionFromFileOrString(jerseyTest, "Tradition 2", "BI", "1", xmlOutput, "stemmaweb");
        assertEquals(Response.status(Response.Status.CREATED).build().getStatus(),
                response.getStatus());
    }

    /**
     * import a tradition with Unicode sigla
     */
    @Test
    public void unicodeSigilTest() {
        ClientResponse response = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/john.xml", "stemmaweb");
        assertEquals(Response.status(Response.Status.CREATED).build().getStatus(),
                response.getStatus());

        // Check that we have witness α
        Node alpha;
        try (Transaction tx = db.beginTx()) {
            alpha = db.findNode(Nodes.WITNESS, "sigil", "α");
            assertNotNull(alpha);
            // Check that witness α is marked as needing quotes
            assertTrue((Boolean) alpha.getProperty("quotesigil"));
            tx.success();
        }
    }

    /**
     * Ports of test suite from Perl Text::Tradition::Parser::Self.
     *
     * #1: parse a file, check for the correct number of readings, paths, and witnesses
     */

    @Test
    public void importFlorilegiumTest () {
        ClientResponse response = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/florilegium_graphml.xml", "stemmaweb");

        // Check for success and get the tradition id
        assertEquals(Response.status(Response.Status.CREATED).build().getStatus(),
                response.getStatus());
        String traditionId = Util.getValueFromJson(response, "tradId");

        // Check for the correct number of reading nodes
        List<ReadingModel> readings = jerseyTest
                .resource()
                .path("/tradition/" + traditionId + "/readings")
                .type(MediaType.APPLICATION_JSON)
                .get(new GenericType<List<ReadingModel>>() {});
        assertEquals(319, readings.size()); // really 319

        // Check for the correct number of sequence paths. Do this with a traversal.
        AtomicInteger sequenceCount = new AtomicInteger(0);
        Node startNode = DatabaseService.getStartNode(traditionId, db);
        assertNotNull(startNode);
        try (Transaction tx = db.beginTx()) {
            db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .evaluator(Evaluators.all())
                    .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL).traverse(startNode)
                    .relationships().forEach(x -> sequenceCount.getAndIncrement());
            tx.success();
        }
        assertEquals(376, sequenceCount.get()); // should be 376


        // Check for the correct number of witnesses
        List<WitnessModel> witnesses = jerseyTest
                .resource()
                .path("/tradition/" + traditionId + "/witnesses")
                .type(MediaType.APPLICATION_JSON)
                .get(new GenericType<List<WitnessModel>>() {});
        assertEquals(13, witnesses.size());  // should be 13

        // Check for the correct number of relationships
        List<RelationshipModel> relations = jerseyTest
                .resource()
                .path("/tradition/" + traditionId + "/relationships")
                .type(MediaType.APPLICATION_JSON)
                .get(new GenericType<List<RelationshipModel>>() {});
        assertEquals(7, relations.size());

        // Spot-check a correct relationship setup
        try(Transaction tx = db.beginTx()) {
            // With this query we are working around some obnoxious problems with divergent
            // Unicode renderings of some Greek letters.
            Result result = db.execute("match (q:READING {text:'πνεύματος'})-->(bs:READING {text:'βλασφημίας'})-->(a:READING {text:'ἀπορία'}), " +
                    "(q)-->(b:READING {text:'βλασφημία'}) return bs, a, b");
            assertTrue(result.hasNext());
            tx.success();
        }

    }


    /**
     * #2: parse a file, mess around with the tradition, export it, check the result
     */

    @Test
    public void exportFlorilegiumTest () {
        ClientResponse response = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/florilegium_graphml.xml", "stemmaweb");

        // Check for success and get the tradition id
        assertEquals(Response.status(Response.Status.CREATED).build().getStatus(),
                response.getStatus());
        String traditionId = Util.getValueFromJson(response, "tradId");

        // Check for the correct number of reading nodes
        List<ReadingModel> origReadings = jerseyTest
                .resource()
                .path("/tradition/" + traditionId + "/readings")
                .type(MediaType.APPLICATION_JSON)
                .get(new GenericType<List<ReadingModel>>() {});
        assertEquals(319, origReadings.size());

        // Set the language
        String jsonPayload = "{\"language\":\"Greek\"}";
        ClientResponse jerseyResponse = jerseyTest
                .resource()
                .path("/tradition/" + traditionId)
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, jsonPayload);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());
        assertEquals("Greek", Util.getValueFromJson(jerseyResponse, "language"));

        // Add a stemma
        String newStemma = "digraph Stemma {\n" +
                "    \"α\" [ class=hypothetical ];\n" +
                "    \"γ\" [ class=hypothetical ];\n" +
                "    \"δ\" [ class=hypothetical ];\n" +
                "    2 [ class=hypothetical,label=\"*\" ];\n" +
                "    3 [ class=hypothetical,label=\"*\" ];\n" +
                "    4 [ class=hypothetical,label=\"*\" ];\n" +
                "    5 [ class=hypothetical,label=\"*\" ];\n" +
                "    7 [ class=hypothetical,label=\"*\" ];\n" +
                "    A [ class=extant ];\n" +
                "    B [ class=extant ];\n" +
                "    C [ class=extant ];\n" +
                "    D [ class=extant ];\n" +
                "    E [ class=extant ];\n" +
                "    F [ class=extant ];\n" +
                "    G [ class=extant ];\n" +
                "    H [ class=extant ];\n" +
                "    K [ class=extant ];\n" +
                "    P [ class=extant ];\n" +
                "    Q [ class=extant ];\n" +
                "    S [ class=extant ];\n" +
                "    T [ class=extant ];\n" +
                "    \"α\" -> A;\n" +
                "    \"α\" -> T;\n" +
                "    \"α\" -> \"δ\";\n" +
                "    \"δ\" -> 2;\n" +
                "    2 -> C;\n" +
                "    2 -> B;\n" +
                "    B -> P;\n" +
                "    B -> S;\n" +
                "    \"δ\" -> \"γ\";\n" +
                "    \"γ\" -> 3;\n" +
                "    3 -> F;\n" +
                "    3 -> H;\n" +
                "    \"γ\" -> 4;\n" +
                "    4 -> D;\n" +
                "    4 -> 5;\n" +
                "    5 -> Q;\n" +
                "    5 -> K;\n" +
                "    5 -> 7;\n" +
                "    7 -> E;\n" +
                "    7 -> G;\n" +
                "}\n";
        jerseyResponse = jerseyTest
                .resource()
                .path("/tradition/" + traditionId + "/stemma")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newStemma);
        assertEquals(ClientResponse.Status.CREATED.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());

        // Merge a couple of nodes
        Node blasphemias;
        Node aporia;
        Node blasphemia;
        try(Transaction tx = db.beginTx()) {
            // With this query we are working around some obnoxious problems with divergent
            // Unicode renderings of some Greek letters.
            Result result = db.execute("match (q:READING {text:'πνεύματος'})-->(bs:READING {text:'βλασφημίας'})-->(a:READING {text:'ἀπορία'}), " +
                    "(q)-->(b:READING {text:'βλασφημία'}) return bs, a, b");
            assertTrue (result.hasNext());
            Map<String, Object> row = result.next();
            blasphemias = (Node) row.get("bs");
            aporia = (Node) row.get("a");
            blasphemia = (Node) row.get("b");
            tx.success();
        }

        ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel(); // take all the defaults
        jerseyResponse = jerseyTest
                .resource()
                .path("/reading/" + blasphemias.getId() + "/concatenate/" + aporia.getId())
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, readingBoundaryModel);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        try(Transaction tx = db.beginTx()) {
            assertEquals("βλασφημίας ἀπορία", blasphemias.getProperty("text"));
            tx.success();
        }

        // Add a new
        RelationshipModel relationship = new RelationshipModel();
        relationship.setSource(String.valueOf(blasphemias.getId()));
        relationship.setTarget(String.valueOf(blasphemia.getId()));
        relationship.setType("lexical");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");
        relationship.setReading_a("βλασφημίας ἀπορία");
        relationship.setReading_b("βλασφημία");

        jerseyResponse = jerseyTest
                .resource()
                .path("/tradition/" + traditionId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, relationship);
        assertEquals(ClientResponse.Status.CREATED.getStatusCode(), jerseyResponse.getStatus());

        // Export the GraphML in Stemmaweb form
        Response parseResponse = exportStemmawebResource.writeNeo4J(traditionId);
        assertEquals(Response.ok().build().getStatus(), parseResponse.getStatus());

        // Re-import and test the result
        response = Util.createTraditionFromFileOrString(jerseyTest, "Tradition 2", "LR", "1",
                parseResponse.getEntity().toString(), "stemmaweb");
        // Check for success and get the tradition id
        assertEquals(Response.status(Response.Status.CREATED).build().getStatus(),
                response.getStatus());
        traditionId = Util.getValueFromJson(response, "tradId");

        // Check for the correct number of reading nodes
        List<ReadingModel> readings = jerseyTest
                .resource()
                .path("/tradition/" + traditionId + "/readings")
                .type(MediaType.APPLICATION_JSON)
                .get(new GenericType<List<ReadingModel>>() {});
        assertEquals(318, readings.size());

        // Check for the correct number of sequence paths. Do this with a traversal.
        AtomicInteger sequenceCount = new AtomicInteger(0);
        Node startNode = DatabaseService.getStartNode(traditionId, db);
        assertNotNull(startNode);
        try (Transaction tx = db.beginTx()) {
            db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .evaluator(Evaluators.all())
                    .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL).traverse(startNode)
                    .relationships().forEach(x -> sequenceCount.getAndIncrement());
            tx.success();
        }
        assertEquals(375, sequenceCount.get());


        // Check for the correct number of witnesses
        List<WitnessModel> witnesses = jerseyTest
                .resource()
                .path("/tradition/" + traditionId + "/witnesses")
                .type(MediaType.APPLICATION_JSON)
                .get(new GenericType<List<WitnessModel>>() {});
        assertEquals(13, witnesses.size());

        // Check for the correct number of relationships
        List<RelationshipModel> relations = jerseyTest
                .resource()
                .path("/tradition/" + traditionId + "/relationships")
                .type(MediaType.APPLICATION_JSON)
                .get(new GenericType<List<RelationshipModel>>() {
                });
        assertEquals(8, relations.size());

        // Check for the existence of the stemma
        List<StemmaModel> stemmata = jerseyTest
                .resource()
                .path("/tradition/" + traditionId + "/stemmata")
                .get(new GenericType<List<StemmaModel>>() {
                });
        assertEquals(1, stemmata.size());

        Util.assertStemmasEquivalent(newStemma, stemmata.get(0).getDot());

        // Check for the correct language setting
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
