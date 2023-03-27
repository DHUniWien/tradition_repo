package net.stemmaweb.stemmaserver.integrationtests;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.model.StemmaModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.parser.DotParser;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.VariantGraphService;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;

import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
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
         * Create a JerseyTestServer serving the Resource under test
         */

        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(Root.class)
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
        Response jerseyResult = Util.createTraditionFromFileOrString(jerseyTest, tName, "LR", "1", fName, "stemmaweb");
        String tradId = Util.getValueFromJson(jerseyResult, "tradId");
        assert(tradId.length() != 0);
        return tradId;
    }

    @Test
	public void getAllStemmataTest() {
        List<StemmaModel> stemmata = jerseyTest
                .target("/tradition/" + tradId + "/stemmata")
                .request()
                .get(new GenericType<>() {
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
        assertFalse(secondStemma.cameFromJobid());
    }

    @Test
    public void getAllStemmataNotFoundErrorTest() {
        Response getStemmaResponse = jerseyTest
				.target("/tradition/10000/stemmata")
                .request(MediaType.APPLICATION_JSON)
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), getStemmaResponse.getStatus());
    }

    @Test
	public void getAllStemmataStatusTest() {
        Response resp = jerseyTest
				.target("/tradition/" + tradId + "/stemmata")
                .request(MediaType.APPLICATION_JSON)
                .get();

        Response expectedResponse = Response.ok().build();
        assertEquals(expectedResponse.getStatus(), resp.getStatus());
    }

    @Test
	public void getStemmaTest() {
        String stemmaTitle = "stemma";
        StemmaModel stemma = jerseyTest
                .target("/tradition/" + tradId + "/stemma/" + stemmaTitle)
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);

        String expected = "digraph \"stemma\" {\n  0 [ class=hypothetical ];  "
                + "A [ class=extant ];  B [ class=extant ];  "
                + "C [ class=extant ];\n 0 -> A;  0 -> B;  A -> C; \n}";
        Util.assertStemmasEquivalent(expected, stemma.getDot());

        String stemmaTitle2 = "Semstem 1402333041_0";
        StemmaModel stemma2 = jerseyTest
                .target("/tradition/" + tradId + "/stemma/" + stemmaTitle2)
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);

        String expected2 = "graph \"Semstem 1402333041_0\" {\n  0 [ class=hypothetical ];\n  "
                + "A [ class=extant ];  B [ class=extant ];  "
                + "C [ class=extant ];\n 0 -- A;  A -- B;  B -- C; \n}";
        Util.assertStemmasEquivalent(expected2, stemma2.getDot());

        Response getStemmaResponse = jerseyTest
                .target("/tradition/" + tradId + "/stemma/gugus")
                .request(MediaType.APPLICATION_JSON)
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), getStemmaResponse.getStatus());
    }

    @Test
    public void setStemmaTest() {

        Node traditionNode = VariantGraphService.getTraditionNode(tradId, db);
        ArrayList<Node> stemmata = DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA);
        assertEquals(2, stemmata.size());

        StemmaModel input = new StemmaModel();
        input.setDot("graph \"Semstem 1402333041_1\" {  0 [ class=hypothetical ];  A [ class=extant ];  B [ class=extant ];  C [ class=extant ]; 0 -- A;  A -- B;  A -- C;}");

        Response actualStemmaResponse = jerseyTest
                .target("/tradition/" + tradId + "/stemma"  )
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(input));
        assertEquals(Response.Status.CREATED.getStatusCode(), actualStemmaResponse.getStatus());

        try (Transaction tx = db.beginTx()) {
            Result result2 = db.execute("match (t:TRADITION {id:'" + tradId +
                    "'})--(s:STEMMA) return count(s) AS res2");
            assertEquals(3L, result2.columnAs("res2").next());

            tx.success();
        }

        String stemmaTitle = "Semstem 1402333041_1";
        StemmaModel stemma = jerseyTest
                .target("/tradition/" + tradId + "/stemma/" + stemmaTitle)
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);

        // Parse the resulting stemma and make sure it matches
        Util.assertStemmasEquivalent(input.getDot(), stemma.getDot());
    }

    @Test
    public void setStemmaDifferentHypotheticalsTest() {
        String newStemmaDot = "digraph \"stick\" {\n"
                + "A [ class=extant ];  B [ class=extant ];  "
                + "C [ class=extant ];\n A -> B;  A -> C; \n}";
        StemmaModel newStemma = new StemmaModel();
        newStemma.setDot(newStemmaDot);
        Response result = jerseyTest
                .target("/tradition/" + tradId + "/stemma")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(newStemma));
        assertEquals(result.getStatus(), Response.Status.CREATED.getStatusCode());

        StemmaModel stemma = jerseyTest
                .target("/tradition/" + tradId + "/stemma/stick")
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        Util.assertStemmasEquivalent(newStemmaDot, stemma.getDot());
    }

    @Test
    public void addContaminatedStemmaTest() {
        String newStemmaDot = "digraph \"loop\" {\n 0 [ class=hypothetical ];"
                + "A [ class=extant ];  B [ class=extant ];  "
                + "C [ class=extant ];\n 0 -> A; A -> B;  A -> C; 0 -> C;\n}";
        StemmaModel newStemma = new StemmaModel();
        newStemma.setDot(newStemmaDot);
        Response result = jerseyTest
                .target("/tradition/" + tradId + "/stemma")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(newStemma));
        assertEquals(result.getStatus(), Response.Status.CREATED.getStatusCode());

        StemmaModel stemma = jerseyTest
                .target("/tradition/" + tradId + "/stemma/loop")
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        assertTrue(stemma.getIs_contaminated());
        Util.assertStemmasEquivalent(newStemmaDot, stemma.getDot());

        // Attempts to reorient this stemma should fail.
        result = jerseyTest
                .target("/tradition/" + tradId + "/stemma/loop/reorient/A")
                .request()
                .post(null);
        assertEquals(Response.Status.PRECONDITION_FAILED.getStatusCode(), result.getStatus());

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
                    .target("/tradition/" + tradId + "/stemma/" + stemmaTitle + "/reorient/" + newNodeId)
                    .request(MediaType.APPLICATION_JSON)
                    .post(null, StemmaModel.class);
            Util.assertStemmasEquivalent(newStemmaDot, newStemmaResponse.getDot());

            Iterable<Relationship> rel2 = startNodeStemma
                    .getRelationships(Direction.OUTGOING, ERelations.HAS_ARCHETYPE);
            assertTrue(rel2.iterator().hasNext());
            assertEquals(newNodeId, rel2.iterator().next().getEndNode().getProperty("sigil").toString());

            Response actualStemmaResponseSecond = jerseyTest
                    .target("/tradition/" + tradId + "/stemma/" + stemmaTitle + "/reorient/" + secondNodeId)
                    .request(MediaType.APPLICATION_JSON)
                    .post(null);
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

            Response actualStemmaResponse = jerseyTest
                    .target("/tradition/" + tradId + "/stemma/" + stemmaTitle + "/reorient/" + falseNode)
                    .request(MediaType.APPLICATION_JSON)
                    .post(null);
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualStemmaResponse.getStatus());

            Response actualStemmaResponse2 = jerseyTest
                    .target("/tradition/" + tradId + "/stemma/" + falseTitle + "/reorient/" + rightNode)
                    .request(MediaType.APPLICATION_JSON)
                    .post(null);
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

            Response actualStemmaResponse = jerseyTest
                    .target("/tradition/" + tradId + "/stemma/" + stemmaTitle + "/reorient/" + newNodeId)
                    .request(MediaType.APPLICATION_JSON)
                    .post(null);
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

        Response actualStemmaResponse = jerseyTest
                .target("/tradition/" + tradId + "/stemma/" +
                        stemmaTitle + "/reorient/" + falseNode)
                .request(MediaType.APPLICATION_JSON)
                .post(null);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualStemmaResponse.getStatus());

        Response actualStemmaResponse2 = jerseyTest
                .target("/tradition/" + tradId + "/stemma/" +
                        falseTitle + "/reorient/" + rightNode)
                .request(MediaType.APPLICATION_JSON)
                .post(null);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualStemmaResponse2.getStatus());

    }

    @Test
    public void reorientDigraphStemmaSameNodeAsBeforeTest() {
        String stemmaTitle = "stemma";
        String newNode = "C";

        Response actualStemmaResponse = jerseyTest
                .target("/tradition/" + tradId + "/stemma/" +
                        stemmaTitle + "/reorient/" + newNode)
                .request(MediaType.APPLICATION_JSON)
                .post(null);
        assertEquals(Response.ok().build().getStatus(), actualStemmaResponse.getStatus());

        Response actualStemmaResponse2 = jerseyTest
                .target("/tradition/" + tradId + "/stemma/" +
                        stemmaTitle + "/reorient/" + newNode)
                .request(MediaType.APPLICATION_JSON)
                .post(null);
        assertEquals(Response.ok().build().getStatus(), actualStemmaResponse2.getStatus());

    }

    @Test
    public void uploadInvalidStemmaTest () {
        // A stemma with a node (A) that is not labeled as extant or hypothetical.
        StemmaModel input = new StemmaModel();
        input.setDot("graph \"invalid\" {\n  0 [ class=hypothetical, label=\"*\" ];  " +
                "\"α\" [ class=extant ];  B [ class=extant ];  C [ class=extant ]; 0 -- A;  A -- B;  A -- C;\n}");
        Response actualStemmaResponse = jerseyTest
                .target("/tradition/" + tradId + "/stemma"  )
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(input));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), actualStemmaResponse.getStatus());
        assertTrue(actualStemmaResponse.readEntity(String.class).contains("not marked as either hypothetical or extant"));

    }

    @Test
    public void addTraditionAndStemmaTwiceTest() {
        StemmaModel input = new StemmaModel();
        input.setIdentifier("Semstem stemma");
        input.setDot("graph stemma {  0 [ class=hypothetical ];  A [ class=extant ];  B [ class=extant ];  C [ class=extant ]; 0 -- A;  A -- B;  A -- C;}");
        Response result = jerseyTest.target("/tradition/" + tradId + "/stemma")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(input));
        assertEquals(Response.Status.CREATED.getStatusCode(), result.getStatus());
        StemmaModel origStemma = result.readEntity(StemmaModel.class);
        assertEquals("Semstem stemma", origStemma.getIdentifier());
        assertEquals(9, origStemma.getDot().split("\n").length);

        // Now add the tradition and the stemma all over again and see what happens
        String newTradId = createTraditionFromFile("Tradition", "src/TestFiles/testTradition.xml");
        result = jerseyTest.target("/tradition/" + newTradId + "/stemma")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(input));
        StemmaModel secondStemma = result.readEntity(StemmaModel.class);
        assertEquals(9, secondStemma.getDot().split("\n").length);

        StemmaModel firstStemma = jerseyTest.target("/tradition/" + tradId + "/stemma/Semstem%20stemma")
                .request().get(StemmaModel.class);
        assertEquals(9, firstStemma.getDot().split("\n").length);

    }

    @Test
    public void recordStemmaLabelTest () {
        StemmaModel input = new StemmaModel();
        input.setDot("graph \"labeltest\" {\n  0 [ class=hypothetical, label=\"*\" ];  " +
                "\"α\" [ class=extant ];  B [ class=extant ];  C [ class=extant ]; 0 -- \"α\";  \"α\" -- B;  \"α\" -- C;\n}");
        Response actualStemmaResponse = jerseyTest
                .target("/tradition/" + tradId + "/stemma"  )
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(input));
        assertEquals(Response.Status.CREATED.getStatusCode(), actualStemmaResponse.getStatus());

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
        StemmaModel stemmaCM = new StemmaModel(); // its name will be "Stemma"
        StemmaModel stemmaTF = new StemmaModel(); // its name will be "TF Stemma"
        DotParser parser = new DotParser(db);
        try {
            byte[] encoded = Files.readAllBytes(Paths.get("src/TestFiles/florilegium.dot"));
            stemmaCM.setDot(new String(encoded, StandardCharsets.UTF_8));

            encoded = Files.readAllBytes(Paths.get("src/TestFiles/florilegium_tf.dot"));
            stemmaTF.setDot(new String(encoded, StandardCharsets.UTF_8));
        } catch (Exception e) {
            fail();
        }
        Response parseResponse = parser.importStemmaFromDot(tradId, stemmaCM);
        assertEquals(Response.Status.CREATED.getStatusCode(), parseResponse.getStatus());
        assertEquals(originalNodeCount + 9, countGraphNodes());

        parseResponse = parser.importStemmaFromDot(tradId, stemmaTF);
        assertEquals(Response.Status.CREATED.getStatusCode(), parseResponse.getStatus());
        assertEquals(originalNodeCount + 19, countGraphNodes());

        // Delete one stemma
        Response deleteResponse = jerseyTest
                .target("/tradition/" + tradId + "/stemma/Stemma")
                .request()
                .delete();
        assertEquals(Response.Status.OK.getStatusCode(), deleteResponse.getStatus());

        // Check the node count
        assertEquals(originalNodeCount + 10, countGraphNodes());

        // Check the remaining stemma
        String tfTitle = "TF Stemma";
        StemmaModel remainingStemma = jerseyTest
                .target("/tradition/" + tradId + "/stemma/" + tfTitle)
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        Util.assertStemmasEquivalent(stemmaTF.getDot(), remainingStemma.getDot());
    }

    @Test
    public void replaceStemmaTest() {
        Node traditionNode = VariantGraphService.getTraditionNode(tradId, db);
        ArrayList<Node> stemmata = DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA);
        assertEquals(2, stemmata.size());

        StemmaModel input = new StemmaModel();
        input.setDot("graph stemma {\n  0 [ class=hypothetical ];  A [ class=extant ];  B [ class=extant ];  " +
                "C [ class=extant ]; 0 -- A;  A -- B;  A -- C;\n}");

        Response actualStemmaResponse = jerseyTest
                .target("/tradition/" + tradId + "/stemma/stemma"  )
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(input));
        assertEquals(Response.Status.OK.getStatusCode(), actualStemmaResponse.getStatus());
        assertEquals(2, DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA).size());

        StemmaModel replacedStemma = jerseyTest
                .target("/tradition/" + tradId + "/stemma/stemma"  )
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        Util.assertStemmasEquivalent(input.getDot(), replacedStemma.getDot());
    }

    @Test
    public void replaceStemmaWithDudTest() {
        Node traditionNode = VariantGraphService.getTraditionNode(tradId, db);
        ArrayList<Node> stemmata = DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA);
        assertEquals(2, stemmata.size());

        String original = "digraph \"stemma\" {\n  0 [ class=hypothetical ];  "
                + "A [ class=extant ];  B [ class=extant ];  "
                + "C [ class=extant ]; 0 -> A;  0 -> B;  A -> C; \n}";
        String input = "graph stemma {\n  0 [ class=hypothetical ];  A [ class=extant ];  B [ class=extant ];  C [ class=extant ]; 0 -- A;  A -- B;  A -- D;\n}";

        Response actualStemmaResponse = jerseyTest
                .target("/tradition/" + tradId + "/stemma/stemma"  )
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(input));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), actualStemmaResponse.getStatus());

        // Do we still have the old one?
        assertEquals(2, DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA).size());
        StemmaModel storedStemma = jerseyTest
                .target("/tradition/" + tradId + "/stemma/stemma"  )
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        Util.assertStemmasEquivalent(original, storedStemma.getDot());
    }

    @Test
    public void replaceStemmaNameMismatchTest() {
        // This used to fail; now the name given in the model overrides the name in the dot.
        Node traditionNode = VariantGraphService.getTraditionNode(tradId, db);
        ArrayList<Node> stemmata = DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA);
        assertEquals(2, stemmata.size());

        String input = "graph stemma2 {  0 [ class=hypothetical ];  A [ class=extant ];  B [ class=extant ];  C [ class=extant ]; 0 -- A;  A -- B;  A -- C;}";
        StemmaModel stemmaSpec = new StemmaModel();
        stemmaSpec.setDot(input);

        Response actualStemmaResponse = jerseyTest
                .target("/tradition/" + tradId + "/stemma/stemma"  )
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(stemmaSpec));
        assertEquals(Response.Status.OK.getStatusCode(), actualStemmaResponse.getStatus());
        assertEquals("stemma", actualStemmaResponse.readEntity(StemmaModel.class).getIdentifier());

        // Do we still have the old one?
        assertEquals(2, DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA).size());
        StemmaModel storedStemma = jerseyTest
                .target("/tradition/" + tradId + "/stemma/stemma"  )
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        assertTrue(storedStemma.getIs_undirected());
        Util.assertStemmasEquivalent(input, storedStemma.getDot());
    }

    @Test
    public void importStemmaFromNewickTest() {
        String newickSpec = "((((((((((((M,C),D),S),F),L),V),U),T2),J),A),B),T1);";
        String dotEquivalent = "graph \"RHM 1382784254\" {\n" +
                "  0 [ class=hypothetical ];\n" +
                "  1 [ class=hypothetical ];\n" +
                "  10 [ class=hypothetical ];\n" +
                "  11 [ class=hypothetical ];\n" +
                "  2 [ class=hypothetical ];\n" +
                "  3 [ class=hypothetical ];\n" +
                "  4 [ class=hypothetical ];\n" +
                "  5 [ class=hypothetical ];\n" +
                "  6 [ class=hypothetical ];\n" +
                "  7 [ class=hypothetical ];\n" +
                "  8 [ class=hypothetical ];\n" +
                "  9 [ class=hypothetical ];\n" +
                "  A [ class=extant ];\n" +
                "  B [ class=extant ];\n" +
                "  C [ class=extant ];\n" +
                "  D [ class=extant ];\n" +
                "  F [ class=extant ];\n" +
                "  J [ class=extant ];\n" +
                "  L [ class=extant ];\n" +
                "  M [ class=extant ];\n" +
                "  S [ class=extant ];\n" +
                "  T1 [ class=extant ];\n" +
                "  T2 [ class=extant ];\n" +
                "  U [ class=extant ];\n" +
                "  V [ class=extant ];\n" +
                "  0 -- 1;\n" +
                "  0 -- T1;\n" +
                "  10 -- 11;\n" +
                "  10 -- 9;\n" +
                "  10 -- D;\n" +
                "  11 -- C;\n" +
                "  11 -- M;\n" +
                "  1 -- 2;\n" +
                "  1 -- B;\n" +
                "  2 -- 3;\n" +
                "  2 -- A;\n" +
                "  3 -- 4;\n" +
                "  4 -- 5;\n" +
                "  5 -- 6;\n" +
                "  6 -- 7;\n" +
                "  7 -- 8;\n" +
                "  8 -- 9;\n" +
                "  F -- 8;\n" +
                "  J -- 3;\n" +
                "  L -- 7;\n" +
                "  S -- 9;\n" +
                "  T2 -- 4;\n" +
                "  U -- 5;\n" +
                "  V -- 6;\n" +
                "}";
        String stemmaName = "From RHM";
        StemmaModel sm = new StemmaModel();
        sm.setIdentifier(stemmaName);
        sm.setJobid(37);
        sm.setNewick(newickSpec);
        String tradId = createTraditionFromFile("Besoin", "src/TestFiles/besoin.xml");
        // Set a matching job ID so we can test that it is removed on import
        TraditionModel textInfo = jerseyTest.target("/tradition/" + tradId)
                .request().get(TraditionModel.class);
        textInfo.setStemweb_jobid(37);
        Response r = jerseyTest.target("/tradition/" + tradId)
                .request(MediaType.APPLICATION_JSON).put(Entity.json(textInfo));
        assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());

        // Now add the stemma
        r = jerseyTest.target("/tradition/" + tradId + "/stemma")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(sm));
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        StemmaModel result = r.readEntity(StemmaModel.class);
        assertTrue(result.getIs_undirected());
        assertEquals(stemmaName, result.getIdentifier());
        assertTrue(result.cameFromJobid());
        assertEquals(Integer.valueOf(37), result.getJobid());
        Util.assertStemmasEquivalent(dotEquivalent, result.getDot());

        // and check that the job ID on the tradition has been unset.
        textInfo = jerseyTest.target("/tradition/" + tradId)
                .request().get(TraditionModel.class);
        assertNull(textInfo.getStemweb_jobid());
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