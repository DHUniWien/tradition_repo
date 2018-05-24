package net.stemmaweb.stemmaserver.integrationtests;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.model.StemmaModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.parser.DotParser;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.*;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import org.neo4j.test.TestGraphDatabaseFactory;

import static org.junit.Assert.*;

/**
 * Contains all tests for the api calls related to stemmas.
 *
 * @author PSE FS 2015 Team2
 */
public class StemmaTest {
    private String tradId;

    private GraphDatabaseService db;

    /*
     * JerseyTest is the test environment to Test api calls it provides a
     * grizzly http service
     */
    private JerseyTest jerseyTest;

    @Before
    public void setUp() throws Exception {

        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory()
                .newImpermanentDatabase())
                .getDatabase();
        Util.setupTestDB(db, "1");

        /*
         * Create a JersyTestServer serving the Resource under test
         */
        Root webResource = new Root();
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(webResource)
                .create();
        jerseyTest.setUp();

        /*
         * load a tradition to the test DB
         * and gets the generated id of the inserted tradition
         */
        String fileName = "src/TestFiles/testTradition.xml";
        tradId = createTraditionFromFile("Tradition", fileName);
    }

    private String createTraditionFromFile(String tName, String fName) {
        ClientResponse jerseyResult = Util.createTraditionFromFileOrString(jerseyTest, tName, "LR", "1", fName, "stemmaweb");
        String tradId = Util.getValueFromJson(jerseyResult, "tradId");
        assert(tradId.length() != 0);
        return tradId;
    }

    @Test
	public void getAllStemmataTest() {
        List<StemmaModel> stemmata = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemmata")
                .get(new GenericType<List<StemmaModel>>() {
                });
        assertEquals(2, stemmata.size());

        StemmaModel firstStemma = stemmata.get(0);
        StemmaModel secondStemma = stemmata.get(1);
        if (!firstStemma.getIdentifier().equals("stemma")) {
            firstStemma = stemmata.get(1);
            secondStemma = stemmata.get(0);
        }

        String expected = "digraph \"stemma\" {\n  0 [ class=hypothetical ];  "
                + "A [ class=extant ];  B [ class=extant ];  "
                + "C [ class=extant ]; 0 -> A;  0 -> B;  A -> C; \n}";

        Util.assertStemmasEquivalent(expected, firstStemma.getDot());
        assertEquals("stemma", firstStemma.getIdentifier());
        assertFalse(firstStemma.getIs_undirected());

        String expected2 = "graph \"Semstem 1402333041_0\" {\n  0 [ class=hypothetical ];  "
                + "A [ class=extant ];  B [ class=extant ];  "
                + "C [ class=extant ]; 0 -- A;  A -- B;  B -- C; \n}";
        Util.assertStemmasEquivalent(expected2, secondStemma.getDot());
        assertEquals("Semstem 1402333041_0", secondStemma.getIdentifier());
        assertTrue(secondStemma.getIs_undirected());
        assertNull(secondStemma.getFrom_jobid());
    }

    @Test
    public void getAllStemmataNotFoundErrorTest() {
        ClientResponse getStemmaResponse = jerseyTest
				.resource()
                .path("/tradition/10000/stemmata")
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), getStemmaResponse.getStatus());
    }

    @Test
	public void getAllStemmataStatusTest() {
        ClientResponse resp = jerseyTest
				.resource()
                .path("/tradition/" + tradId + "/stemmata")
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);

        Response expectedResponse = Response.ok().build();
        assertEquals(expectedResponse.getStatus(), resp.getStatus());
    }

    @Test
	public void getStemmaTest() {
        String stemmaTitle = "stemma";
        StemmaModel stemma = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma/" + stemmaTitle)
                .type(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);

        String expected = "digraph \"stemma\" {\n  0 [ class=hypothetical ];  "
                + "A [ class=extant ];  B [ class=extant ];  "
                + "C [ class=extant ];\n 0 -> A;  0 -> B;  A -> C; \n}";
        Util.assertStemmasEquivalent(expected, stemma.getDot());

        String stemmaTitle2 = "Semstem 1402333041_0";
        StemmaModel stemma2 = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma/" + stemmaTitle2)
                .type(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);

        String expected2 = "graph \"Semstem 1402333041_0\" {\n  0 [ class=hypothetical ];\n  "
                + "A [ class=extant ];  B [ class=extant ];  "
                + "C [ class=extant ];\n 0 -- A;  A -- B;  B -- C; \n}";
        Util.assertStemmasEquivalent(expected2, stemma2.getDot());

        ClientResponse getStemmaResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma/gugus")
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), getStemmaResponse.getStatus());
    }

    @Test
    public void setStemmaTest() {

        Node traditionNode = DatabaseService.getTraditionNode(tradId, db);
        ArrayList<Node> stemmata = DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA);
        assertEquals(2, stemmata.size());

        String input = "graph \"Semstem 1402333041_1\" {  0 [ class=hypothetical ];  A [ class=extant ];  B [ class=extant ];  C [ class=extant ]; 0 -- A;  A -- B;  A -- C;}";

        ClientResponse actualStemmaResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma"  )
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, input);
        assertEquals(ClientResponse.Status.CREATED.getStatusCode(), actualStemmaResponse.getStatus());

        try (Transaction tx = db.beginTx()) {
            Result result2 = db.execute("match (t:TRADITION {id:'" + tradId +
                    "'})--(s:STEMMA) return count(s) AS res2");
            assertEquals(3L, result2.columnAs("res2").next());

            tx.success();
        }

        String stemmaTitle = "Semstem 1402333041_1";
        StemmaModel stemma = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma/" + stemmaTitle)
                .type(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);

        // Parse the resulting stemma and make sure it matches
        Util.assertStemmasEquivalent(input, stemma.getDot());
    }

    @Test
    public void setStemmaDifferentHypotheticalsTest() {
        String newStemmaDot = "digraph \"stick\" {\n"
                + "A [ class=extant ];  B [ class=extant ];  "
                + "C [ class=extant ];\n A -> B;  A -> C; \n}";
        ClientResponse result = jerseyTest.resource()
                .path("/tradition/" + tradId + "/stemma")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newStemmaDot);
        assertEquals(result.getStatus(), ClientResponse.Status.CREATED.getStatusCode());

        StemmaModel stemma = jerseyTest.resource()
                .path("/tradition/" + tradId + "/stemma/stick")
                .type(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        Util.assertStemmasEquivalent(newStemmaDot, stemma.getDot());
    }

    @Test
    public void addContaminatedStemmaTest() {
        String newStemmaDot = "digraph \"loop\" {\n 0 [ class=hypothetical ];"
                + "A [ class=extant ];  B [ class=extant ];  "
                + "C [ class=extant ];\n 0 -> A; A -> B;  A -> C; 0 -> C;\n}";
        ClientResponse result = jerseyTest.resource()
                .path("/tradition/" + tradId + "/stemma")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newStemmaDot);
        assertEquals(result.getStatus(), ClientResponse.Status.CREATED.getStatusCode());

        StemmaModel stemma = jerseyTest.resource()
                .path("/tradition/" + tradId + "/stemma/loop")
                .type(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        assertTrue(stemma.getIs_contaminated());
        Util.assertStemmasEquivalent(newStemmaDot, stemma.getDot());

        // Attempts to reorient this stemma should fail.
        result = jerseyTest.resource()
                .path("/tradition/" + tradId + "/stemma/loop/reorient/A")
                .post(ClientResponse.class);
        assertEquals(ClientResponse.Status.PRECONDITION_FAILED.getStatusCode(), result.getStatus());

    }

    @Test
    public void setStemmaNotFoundTest() {
        String emptyInput = "";

        ClientResponse actualStemmaResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma" )
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, emptyInput);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), actualStemmaResponse.getStatus());
    }

    @Test
    public void reorientGraphStemmaTest() {
        String stemmaTitle = "Semstem 1402333041_0";
        String newNodeId = "C";
        String secondNodeId = "0";

        try (Transaction tx = db.beginTx()) {
            Result result1 = db.execute("match (t:TRADITION {id:'" +
                    tradId + "'})-[:HAS_STEMMA]->(n:STEMMA { name:'" +
                    stemmaTitle + "'}) return n");
            Iterator<Node> stNodes = result1.columnAs("n");
            assertTrue(stNodes.hasNext());
            Node startNodeStemma = stNodes.next();

            Iterable<Relationship> rel1 = startNodeStemma
                    .getRelationships(Direction.OUTGOING, ERelations.HAS_ARCHETYPE);
            assertFalse(rel1.iterator().hasNext());

            String newStemmaDot = "digraph \"Semstem 1402333041_0\" {  0 [ class=hypothetical ];  A [ class=extant ];  B [ class=extant ];  C [ class=extant ]; C -> B;  B -> A;  A -> 0;}";
            StemmaModel newStemmaResponse = jerseyTest
                    .resource()
                    .path("/tradition/" + tradId + "/stemma/" + stemmaTitle + "/reorient/" + newNodeId)
                    .type(MediaType.APPLICATION_JSON)
                    .post(StemmaModel.class);
            Util.assertStemmasEquivalent(newStemmaDot, newStemmaResponse.getDot());

            Iterable<Relationship> rel2 = startNodeStemma
                    .getRelationships(Direction.OUTGOING, ERelations.HAS_ARCHETYPE);
            assertTrue(rel2.iterator().hasNext());
            assertEquals(newNodeId, rel2.iterator().next().getEndNode().getProperty("sigil").toString());

            ClientResponse actualStemmaResponseSecond = jerseyTest
                    .resource()
                    .path("/tradition/" + tradId + "/stemma/" + stemmaTitle + "/reorient/" + secondNodeId)
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class);
            assertEquals(Response.ok().build().getStatus(), actualStemmaResponseSecond.getStatus());

            tx.success();
        }
    }

    @Test
    public void reorientGraphStemmaNoNodesTest() {
        String stemmaTitle = "Semstem 1402333041_0";
        String falseNode = "X";
        String rightNode = "C";
        String falseTitle = "X";


        try (Transaction tx = db.beginTx()) {

            ClientResponse actualStemmaResponse = jerseyTest
                    .resource()
                    .path("/tradition/" + tradId + "/stemma/" + stemmaTitle + "/reorient/" + falseNode)
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class);
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualStemmaResponse.getStatus());

            ClientResponse actualStemmaResponse2 = jerseyTest
                    .resource()
                    .path("/tradition/" + tradId + "/stemma/" + falseTitle + "/reorient/" + rightNode)
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class);
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualStemmaResponse2.getStatus());

            tx.success();
        }
    }

    @Test
    public void reorientDigraphStemmaTest() {
        String stemmaTitle = "stemma";
        String newNodeId = "C";

        try (Transaction tx = db.beginTx()) {
            Result result1 = db.execute("match (t:TRADITION {id:'" +
                    tradId + "'})-[:HAS_STEMMA]->(n:STEMMA { name:'" +
                    stemmaTitle + "'}) return n");
            Iterator<Node> stNodes = result1.columnAs("n");
            assertTrue(stNodes.hasNext());
            Node startNodeStemma = stNodes.next();

            Iterable<Relationship> relBevor = startNodeStemma
                    .getRelationships(Direction.OUTGOING, ERelations.HAS_ARCHETYPE);
            assertTrue(relBevor.iterator().hasNext());
            assertEquals("0", relBevor.iterator().next().getEndNode().getProperty("sigil").toString());

            ClientResponse actualStemmaResponse = jerseyTest
                    .resource()
                    .path("/tradition/" + tradId + "/stemma/" + stemmaTitle + "/reorient/" + newNodeId)
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class);
            assertEquals(Response.ok().build().getStatus(), actualStemmaResponse.getStatus());

            Iterable<Relationship> relAfter = startNodeStemma
                    .getRelationships(Direction.OUTGOING, ERelations.HAS_ARCHETYPE);
            assertTrue(relAfter.iterator().hasNext());
            assertEquals(relAfter.iterator().next().getEndNode().getProperty("sigil").toString(),
                    newNodeId);

            tx.success();
        }
    }

    @Test
    public void reorientDigraphStemmaNoNodesTest() {
        String stemmaTitle = "stemma";
        String falseNode = "X";
        String rightNode = "C";
        String falseTitle = "X";

        ClientResponse actualStemmaResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma/" +
                        stemmaTitle + "/reorient/" + falseNode)
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualStemmaResponse.getStatus());

        ClientResponse actualStemmaResponse2 = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma/" +
                        falseTitle + "/reorient/" + rightNode)
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualStemmaResponse2.getStatus());

    }

    @Test
    public void reorientDigraphStemmaSameNodeAsBeforeTest() {
        String stemmaTitle = "stemma";
        String newNode = "C";

        ClientResponse actualStemmaResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma/" +
                        stemmaTitle + "/reorient/" + newNode)
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class);
        assertEquals(Response.ok().build().getStatus(), actualStemmaResponse.getStatus());

        ClientResponse actualStemmaResponse2 = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma/" +
                        stemmaTitle + "/reorient/" + newNode)
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class);
        assertEquals(Response.ok().build().getStatus(), actualStemmaResponse2.getStatus());

    }

    @Test
    public void uploadInvalidStemmaTest () {
        // A stemma with a node (A) that is not labeled as extant or hypothetical.
        String input = "graph \"invalid\" {\n  0 [ class=hypothetical, label=\"*\" ];  \"α\" [ class=extant ];  B [ class=extant ];  C [ class=extant ]; 0 -- A;  A -- B;  A -- C;\n}";
        ClientResponse actualStemmaResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma"  )
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, input);
        assertEquals(ClientResponse.Status.BAD_REQUEST.getStatusCode(), actualStemmaResponse.getStatus());
        assertTrue(actualStemmaResponse.getEntity(String.class).contains("not marked as either hypothetical or extant"));

    }


    @Test
    public void recordStemmaLabelTest () {
        String input = "graph \"labeltest\" {\n  0 [ class=hypothetical, label=\"*\" ];  \"α\" [ class=extant ];  B [ class=extant ];  C [ class=extant ]; 0 -- \"α\";  \"α\" -- B;  \"α\" -- C;\n}";
        ClientResponse actualStemmaResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma"  )
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, input);
        assertEquals(ClientResponse.Status.CREATED.getStatusCode(), actualStemmaResponse.getStatus());

        try (Transaction tx = db.beginTx()) {
            Result r = db.execute("match (s:STEMMA {name:'labeltest'})-[:HAS_WITNESS]->(z:WITNESS {sigil:'0'}), " +
                    "(s)-[:HAS_WITNESS]->(a:WITNESS {sigil:'α'}) return z, a");
            assertTrue(r.hasNext());
            Map<String, Object> row = r.next();
            Node alphaNode = (Node) row.get("a");
            Node zeroNode = (Node) row.get("z");

            assertTrue(zeroNode.hasProperty("label"));
            assertFalse(alphaNode.hasProperty("label"));
            tx.success();
        }

    }

    @Test
    public void deleteStemmaTest () {
        String fileName = "src/TestFiles/florilegium_graphml.xml";
        String tradId = createTraditionFromFile("Florilegium", fileName);

        // Count the nodes to start with
        int originalNodeCount = countGraphNodes();

        // Add two stemmata and check the node count
        String stemmaCM = null;
        String stemmaTF = null;
        DotParser parser = new DotParser(db);
        try {
            byte[] encoded = Files.readAllBytes(Paths.get("src/TestFiles/florilegium.dot"));
            stemmaCM = new String(encoded, Charset.forName("utf-8"));

            encoded = Files.readAllBytes(Paths.get("src/TestFiles/florilegium_tf.dot"));
            stemmaTF = new String(encoded, Charset.forName("utf-8"));
        } catch (Exception e) {
            fail();
        }
        Response parseResponse = parser.importStemmaFromDot(stemmaCM, tradId);
        assertEquals(ClientResponse.Status.CREATED.getStatusCode(), parseResponse.getStatus());
        assertEquals(originalNodeCount + 9, countGraphNodes());

        parseResponse = parser.importStemmaFromDot(stemmaTF, tradId);
        assertEquals(ClientResponse.Status.CREATED.getStatusCode(), parseResponse.getStatus());
        assertEquals(originalNodeCount + 19, countGraphNodes());

        // Delete one stemma
        ClientResponse deleteResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma/Stemma")
                .type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), deleteResponse.getStatus());

        // Check the node count
        assertEquals(originalNodeCount + 10, countGraphNodes());

        // Check the remaining stemma
        String tfTitle = "TF Stemma";
        StemmaModel remainingStemma = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma/" + tfTitle)
                .type(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        Util.assertStemmasEquivalent(stemmaTF, remainingStemma.getDot());
    }

    @Test
    public void replaceStemmaTest() {
        Node traditionNode = DatabaseService.getTraditionNode(tradId, db);
        ArrayList<Node> stemmata = DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA);
        assertEquals(2, stemmata.size());

        String input = "graph stemma {\n  0 [ class=hypothetical ];  A [ class=extant ];  B [ class=extant ];  C [ class=extant ]; 0 -- A;  A -- B;  A -- C;\n}";

        ClientResponse actualStemmaResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma/stemma"  )
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, input);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), actualStemmaResponse.getStatus());
        assertEquals(2, DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA).size());

        StemmaModel replacedStemma = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma/stemma"  )
                .type(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        Util.assertStemmasEquivalent(input, replacedStemma.getDot());
    }

    @Test
    public void replaceStemmaWithDudTest() {
        Node traditionNode = DatabaseService.getTraditionNode(tradId, db);
        ArrayList<Node> stemmata = DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA);
        assertEquals(2, stemmata.size());

        String original = "digraph \"stemma\" {\n  0 [ class=hypothetical ];  "
                + "A [ class=extant ];  B [ class=extant ];  "
                + "C [ class=extant ]; 0 -> A;  0 -> B;  A -> C; \n}";
        String input = "graph stemma {\n  0 [ class=hypothetical ];  A [ class=extant ];  B [ class=extant ];  C [ class=extant ]; 0 -- A;  A -- B;  A -- D;\n}";

        ClientResponse actualStemmaResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma/stemma"  )
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, input);
        assertEquals(ClientResponse.Status.BAD_REQUEST.getStatusCode(), actualStemmaResponse.getStatus());

        // Do we still have the old one?
        assertEquals(2, DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA).size());
        StemmaModel storedStemma = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma/stemma"  )
                .type(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        Util.assertStemmasEquivalent(original, storedStemma.getDot());
    }

    @Test
    public void replaceStemmaNameMismatchTest() {
        Node traditionNode = DatabaseService.getTraditionNode(tradId, db);
        ArrayList<Node> stemmata = DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA);
        assertEquals(2, stemmata.size());

        String original = "digraph \"stemma\" {\n  0 [ class=hypothetical ];  "
                + "A [ class=extant ];  B [ class=extant ];  "
                + "C [ class=extant ]; 0 -> A;  0 -> B;  A -> C; \n}";
        String input = "graph stemma2 {  0 [ class=hypothetical ];  A [ class=extant ];  B [ class=extant ];  C [ class=extant ]; 0 -- A;  A -- B;  A -- C;}";

        ClientResponse actualStemmaResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma/stemma"  )
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, input);
        assertEquals(ClientResponse.Status.BAD_REQUEST.getStatusCode(), actualStemmaResponse.getStatus());
        assertTrue(actualStemmaResponse.getEntity(String.class).contains("Name mismatch"));

        // Do we still have the old one?
        assertEquals(2, DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA).size());
        StemmaModel storedStemma = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma/stemma"  )
                .type(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        Util.assertStemmasEquivalent(original, storedStemma.getDot());
    }


    private int countGraphNodes() {
        AtomicInteger numNodes = new AtomicInteger(0);
        try (Transaction tx = db.beginTx()) {
            db.execute("match (n) return n").forEachRemaining(x -> numNodes.getAndIncrement());
            tx.success();
        }
        return numNodes.get();
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