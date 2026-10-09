package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.Uniqueness;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import net.stemmaweb.exporter.StemmawebExporter;
import net.stemmaweb.model.ReadingBoundaryModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationModel;
import net.stemmaweb.model.StemmaModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.VariantGraphService;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;

/**
 *
 * @author PSE FS 2015 Team2
 *
 */
public class StemmawebInputOutputTest {

    private GraphDatabaseService db;

    private JerseyTest jerseyTest;

    @Before
    public void setUp() throws Exception {

//        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        DatabaseManagementService dbbuilder = new TestDatabaseManagementServiceBuilder().impermanent().build();
    	db = dbbuilder.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME);
    	new GraphDatabaseServiceProvider(dbbuilder, db);
        Util.setupTestDB(db, "1");

        // Create a JerseyTestServer for the necessary REST API calls
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(Root.class)
                .create();
        jerseyTest.setUp();
    }

    /**
     * Try to import a non existent file
     */
    @Test
    public void graphMLImportNonexistentFileTest() {
        try (Response response = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/SapientiaFileNotExisting.xml", "stemmaweb")) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        }
        assertFalse(traditionNodeExists());
    }

    /**
     * Try to import a file with errors
     */
    @Test
    public void graphMLImportXMLStreamErrorTest() {
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/SapientiaWithError.xml", "stemmaweb");
        assertNotNull(response);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertFalse(traditionNodeExists());
    }

    /**
     * Import a correct file
     */
    @Test
    public void graphMLImportSuccessTest() {
        try (Response response = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/besoin.xml", "stemmaweb")) {
            assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        }
        assertTrue(traditionNodeExists());
    }

    /**
     * test if the tradition node exists
     */
    private boolean traditionNodeExists(){
        boolean answer;
        try(Transaction tx = db.beginTx()) {
            ResourceIterator<Node> tradNodesIt = tx.findNodes(Nodes.TRADITION, "name", "Tradition");
            answer = tradNodesIt.hasNext();
        }
        return answer;
    }

    /**
     * try to export a non existent tradition
     */
    @Test
    public void graphMLExportTraditionNotFoundTest(){
        try (Transaction tx = db.beginTx()) {
            try (Response actualResponse = new StemmawebExporter(tx).writeNeo4J("1002")) {
                assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualResponse.getStatus());
            }
        }
    }

    /**
     * try to export a correct tradition
     */
    @Test
    public void graphMLExportSuccessTest(){
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
            "src/TestFiles/testTradition.xml", "stemmaweb");
        String traditionId = Util.getValueFromJson(response, "tradId");
        assertNotNull(traditionId);
        String xmlOutput;
        try (Transaction tx = db.beginTx()) {
            try (Response actualResponse = new StemmawebExporter(tx).writeNeo4J(traditionId)) {
                assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
                xmlOutput = actualResponse.getEntity().toString();
            }
        }
        response = Util.createTraditionFromFileOrString(jerseyTest, "Tradition 2", "BI", "1", xmlOutput, "stemmaweb");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    }

    /**
     * import a tradition with Unicode sigla
     */
    @Test
    public void unicodeSigilTest() {
        try (Response response = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/john.xml", "stemmaweb")) {
            assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        }

        // Check that we have witness α
        Node alpha;
        try (Transaction tx = db.beginTx()) {
            alpha = tx.findNode(Nodes.WITNESS, "sigil", "α");
            assertNotNull(alpha);
            // Check that witness α is marked as needing quotes
            assertTrue((Boolean) alpha.getProperty("quotesigil"));
        }
    }

    /**
     * Ports of test suite from Perl Text::Tradition::Parser::Self.
     * #1: parse a file, check for the correct number of readings, paths, and witnesses
     */

    @Test
    public void importFlorilegiumTest () {
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/florilegium_graphml.xml", "stemmaweb");

        // Check for success and get the tradition id
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        String traditionId = Util.getValueFromJson(response, "tradId");

        // Check for the correct number of reading nodes
        List<ReadingModel> readings = jerseyTest
                .target("/tradition/" + traditionId + "/readings")
                .request(MediaType.APPLICATION_JSON)
                .get(new GenericType<>() {});
        assertEquals(319, readings.size()); // really 319

        // Check for the correct number of sequence paths. Do this with a traversal.
        AtomicInteger sequenceCount = new AtomicInteger(0);
        try (Transaction tx = db.beginTx()) {
        	Node startNode = VariantGraphService.getStartNode(tx, traditionId);
        	assertNotNull(startNode);
            tx.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .evaluator(Evaluators.all())
                    .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL).traverse(startNode)
                    .relationships().forEach(x -> sequenceCount.getAndIncrement());
        }
        assertEquals(376, sequenceCount.get()); // should be 376


        // Check for the correct number of witnesses
        List<WitnessModel> witnesses = jerseyTest
                .target("/tradition/" + traditionId + "/witnesses")
                .request(MediaType.APPLICATION_JSON)
                .get(new GenericType<>() {});
        assertEquals(13, witnesses.size());  // should be 13

        // Check for the correct number of relationships
        List<RelationModel> relations = jerseyTest
                .target("/tradition/" + traditionId + "/relations")
                .request(MediaType.APPLICATION_JSON)
                .get(new GenericType<>() {});
        assertEquals(7, relations.size());

        // Spot-check a correct relationship setup
        try(Transaction tx = db.beginTx()) {
            // With this query we are working around some obnoxious problems with divergent
            // Unicode renderings of some Greek letters.
            Result result = tx.execute("match (q:READING {text:'πνεύματος'})-->(bs:READING {text:'βλασφημίας'})-->(a:READING {text:'ἀπορία'}), " +
                    "(q)-->(b:READING {text:'βλασφημία'}) return bs, a, b");
            assertTrue(result.hasNext());
        }

    }


    /**
     * #2: parse a file, mess around with the tradition, export it, check the result
     */

    @Test
    public void exportFlorilegiumTest () {
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/florilegium_graphml.xml", "stemmaweb");

        // Check for success and get the tradition id
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        String traditionId = Util.getValueFromJson(response, "tradId");

        // Check for the correct number of reading nodes
        List<ReadingModel> origReadings = jerseyTest
                .target("/tradition/" + traditionId + "/readings")
                .request(MediaType.APPLICATION_JSON)
                .get(new GenericType<>() {});
        assertEquals(319, origReadings.size());

        // Set the language
        String jsonPayload = "{\"language\":\"Greek\"}";
        Response jerseyResponse = jerseyTest
                .target("/tradition/" + traditionId)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(jsonPayload));
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());
        assertEquals("Greek", Util.getValueFromJson(jerseyResponse, "language"));

        // Add a stemma
        StemmaModel newStemma = new StemmaModel();
        newStemma.setDot("""
digraph Stemma {
    "α" [ class=hypothetical ];
    "γ" [ class=hypothetical ];
    "δ" [ class=hypothetical ];
    H2 [ class=hypothetical,label="*" ];
    H3 [ class=hypothetical,label="*" ];
    H4 [ class=hypothetical,label="*" ];
    H5 [ class=hypothetical,label="*" ];
    H7 [ class=hypothetical,label="*" ];
    A [ class=extant ];
    B [ class=extant ];
    C [ class=extant ];
    D [ class=extant ];
    E [ class=extant ];
    F [ class=extant ];
    G [ class=extant ];
    H [ class=extant ];
    K [ class=extant ];
    P [ class=extant ];
    Q [ class=extant ];
    S [ class=extant ];
    T [ class=extant ];
    "α" -> A;
    "α" -> T;
    "α" -> "δ";
    "δ" -> H2;
    H2 -> C;
    H2 -> B;
    B -> P;
    B -> S;
    "δ" -> "γ";
    "γ" -> H3;
    H3 -> F;
    H3 -> H;
    "γ" -> H4;
    H4 -> D;
    H4 -> H5;
    H5 -> Q;
    H5 -> K;
    H5 -> H7;
    H7 -> E;
    H7 -> G;
}
""");
        jerseyResponse = jerseyTest
                .target("/tradition/" + traditionId + "/stemma")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(newStemma));
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());

        // Merge a couple of nodes
        String blasphemias;
        String aporia;
        String blasphemia;
        try(Transaction tx = db.beginTx()) {
            // With this query we are working around some obnoxious problems with divergent
            // Unicode renderings of some Greek letters.
            Result result = tx.execute("match (q:READING {text:'πνεύματος'})-->(bs:READING {text:'βλασφημίας'})-->(a:READING {text:'ἀπορία'}), " +
                    "(q)-->(b:READING {text:'βλασφημία'}) return bs, a, b");
            assertTrue (result.hasNext());
            Map<String, Object> row = result.next();
            blasphemias = ((Node) row.get("bs")).getProperty("id").toString();
            aporia = ((Node) row.get("a")).getProperty("id").toString();
            blasphemia = ((Node) row.get("b")).getProperty("id").toString();
        }

        ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel(); // take all the defaults
        jerseyResponse = jerseyTest
                .target("/reading/" + blasphemias + "/concatenate/" + aporia)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(readingBoundaryModel));
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        try(Transaction tx = db.beginTx()) {
            Node bsNode = DatabaseService.findNodeOrThrow(tx, Nodes.READING, blasphemias);
            assertEquals("βλασφημίας ἀπορία", bsNode.getProperty("text"));
        }

        // Add a new
        RelationModel relationship = new RelationModel();
        relationship.setSource(blasphemias);
        relationship.setTarget(blasphemia);
        relationship.setType("lexical");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");

        jerseyResponse = jerseyTest
                .target("/tradition/" + traditionId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(relationship));
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResponse.getStatus());

        // Export the GraphML in Stemmaweb form
        String graphmlOutput;
        try (Transaction tx = db.beginTx()) {
            try (Response parseResponse = new StemmawebExporter(tx).writeNeo4J(traditionId)) {
                assertEquals(Response.Status.OK.getStatusCode(), parseResponse.getStatus());
                graphmlOutput = parseResponse.getEntity().toString();
            }
        }

        // Re-import and test the result
        response = Util.createTraditionFromFileOrString(jerseyTest, "Tradition 2", "LR", "1",
                graphmlOutput, "stemmaweb");
        // Check for success and get the tradition id
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        traditionId = Util.getValueFromJson(response, "tradId");

        // Check for the correct number of reading nodes
        List<ReadingModel> readings = jerseyTest
                .target("/tradition/" + traditionId + "/readings")
                .request(MediaType.APPLICATION_JSON)
                .get(new GenericType<>() {});
        assertEquals(318, readings.size());

        // Check for the correct number of sequence paths. Do this with a traversal.
        AtomicInteger sequenceCount = new AtomicInteger(0);
        try (Transaction tx = db.beginTx()) {
        	Node startNode = VariantGraphService.getStartNode(tx, traditionId);
        	assertNotNull(startNode);
            tx.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .evaluator(Evaluators.all())
                    .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL).traverse(startNode)
                    .relationships().forEach(x -> sequenceCount.getAndIncrement());
        }
        assertEquals(375, sequenceCount.get());


        // Check for the correct number of witnesses
        List<WitnessModel> witnesses = jerseyTest
                .target("/tradition/" + traditionId + "/witnesses")
                .request(MediaType.APPLICATION_JSON)
                .get(new GenericType<>() {});
        assertEquals(13, witnesses.size());

        // Check for the correct number of relationships
        List<RelationModel> relations = jerseyTest
                .target("/tradition/" + traditionId + "/relations")
                .request(MediaType.APPLICATION_JSON)
                .get(new GenericType<>() {});
        assertEquals(8, relations.size());

        // Check for the existence of the stemma
        List<StemmaModel> stemmata = jerseyTest
                .target("/tradition/" + traditionId + "/stemmata")
                .request()
                .get(new GenericType<>() {});
        assertEquals(1, stemmata.size());

        Util.assertStemmasEquivalent(newStemma.getDot(), stemmata.getFirst().getDot());

        // Check for the correct language setting
    }

    /*
     * Shut down the jersey server
     *
     * @throws Exception
     */
    @After
    public void tearDown() throws Exception {
//        db.shutdown();
    	GraphDatabaseServiceProvider.shutdown();
        jerseyTest.tearDown();
    }
}
