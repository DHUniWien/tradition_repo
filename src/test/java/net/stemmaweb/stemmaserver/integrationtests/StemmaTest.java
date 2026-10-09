package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
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
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import net.stemmaweb.model.StemmaModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.parser.DotParser;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.VariantGraphService;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;

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
        DatabaseManagementService dbbuilder = new TestDatabaseManagementServiceBuilder().impermanent().build();
    	db = dbbuilder.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME);
    	new GraphDatabaseServiceProvider(dbbuilder, db);
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
        assert(!tradId.isEmpty());
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
        if (!firstStemma.getName().equals("stemma")) {
            firstStemma = stemmata.get(1);
            secondStemma = stemmata.get(0);
        }

        String expected = """
digraph "stemma" {
  0 [ class=hypothetical ];
  A [ class=extant ];  B [ class=extant ];
  C [ class=extant ]; 0 -> A;  0 -> B;  A -> C;
}""";

        Util.assertStemmasEquivalent(expected, firstStemma.getDot());
        assertEquals("stemma", firstStemma.getName());
        assertFalse(firstStemma.getIs_undirected());

        String expected2 = """
graph "Semstem 1402333041_0" {
  0 [ class=hypothetical ];
  A [ class=extant ];  B [ class=extant ];
  C [ class=extant ]; 0 -- A;  A -- B;  B -- C;
}""";
        Util.assertStemmasEquivalent(expected2, secondStemma.getDot());
        assertEquals("Semstem 1402333041_0", secondStemma.getName());
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

        try (Response expectedResponse = Response.ok().build()) {
            assertEquals(expectedResponse.getStatus(), resp.getStatus());
        }
    }

    @Test
	public void getStemmaTest() {
        String stemmaTitle = "stemma";
        StemmaModel stemma = jerseyTest
                .target("/tradition/" + tradId + "/stemma/" + stemmaTitle)
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);

        String expected = """
digraph "stemma" {
  0 [ class=hypothetical ];
  A [ class=extant ];  B [ class=extant ];
  C [ class=extant ];
  0 -> A;
  0 -> B;
  A -> C;
}""";
        Util.assertStemmasEquivalent(expected, stemma.getDot());

        String stemmaTitle2 = "Semstem 1402333041_0";
        StemmaModel stemma2 = jerseyTest
                .target("/tradition/" + tradId + "/stemma/" + stemmaTitle2)
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);

        String expected2 = """
graph "Semstem 1402333041_0" {
  0 [ class=hypothetical ];
  A [ class=extant ];
  B [ class=extant ];
  C [ class=extant ];
  0 -- A;
  A -- B;
  B -- C;
}""";
        Util.assertStemmasEquivalent(expected2, stemma2.getDot());

        Response getStemmaResponse = jerseyTest
                .target("/tradition/" + tradId + "/stemma/gugus")
                .request(MediaType.APPLICATION_JSON)
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), getStemmaResponse.getStatus());
    }

    @Test
    public void setStemmaTest() {

        try (Transaction tx = db.beginTx()) {
            Node traditionNode = VariantGraphService.getTraditionNode(tx, tradId);
            ArrayList<Node> stemmata = DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA);
            assertEquals(2, stemmata.size());
        }
        StemmaModel input = new StemmaModel();
        input.setDot("graph \"Semstem 1402333041_1\" {  0 [ class=hypothetical ];  A [ class=extant ];  B [ class=extant ];  C [ class=extant ]; 0 -- A;  A -- B;  A -- C;}");
        	
        try (Response actualStemmaResponse = jerseyTest
                .target("/tradition/" + tradId + "/stemma"  )
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(input))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), actualStemmaResponse.getStatus());
        }
        try (Transaction tx = db.beginTx()) {
            Result result2 = tx.execute("match (t:TRADITION {id:'" + tradId +
                    "'})--(s:STEMMA) return count(s) AS res2");
            assertEquals(3L, result2.columnAs("res2").next());
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
        String newStemmaDot = """
digraph "stick" {
  A [ class=extant ];
  B [ class=extant ];
  C [ class=extant ];
  A -> B;
  A -> C;\s
}""";
        StemmaModel newStemma = new StemmaModel();
        newStemma.setDot(newStemmaDot);
        try (Response result = jerseyTest
                .target("/tradition/" + tradId + "/stemma")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(newStemma))) {
            assertEquals(result.getStatus(), Response.Status.CREATED.getStatusCode());
        }

        StemmaModel stemma = jerseyTest
                .target("/tradition/" + tradId + "/stemma/stick")
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        Util.assertStemmasEquivalent(newStemmaDot, stemma.getDot());
    }

    @Test
    public void addContaminatedStemmaTest() {
        String newStemmaDot = """
digraph "loop" {
  0 [ class=hypothetical ];
  A [ class=extant ];
  B [ class=extant ];
  C [ class=extant ];
  0 -> A;
  A -> B;
  A -> C;
  0 -> C;
}""";
        StemmaModel newStemma = new StemmaModel();
        newStemma.setDot(newStemmaDot);

        try (Response result = jerseyTest
                .target("/tradition/" + tradId + "/stemma")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(newStemma))) {
            assertEquals(result.getStatus(), Response.Status.CREATED.getStatusCode());
        }

        StemmaModel stemma = jerseyTest
                .target("/tradition/" + tradId + "/stemma/loop")
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        assertTrue(stemma.getIs_contaminated());
        Util.assertStemmasEquivalent(newStemmaDot, stemma.getDot());

        // Attempts to reorient this stemma should fail.
        try (Response result = jerseyTest
                .target("/tradition/" + tradId + "/stemma/loop/reorient/A")
                .request()
                .post(null)) {
            assertEquals(Response.Status.PRECONDITION_FAILED.getStatusCode(), result.getStatus());
        }
    }

    @Test
    public void reorientGraphStemmaTest() {
        String stemmaTitle = "Semstem 1402333041_0";
        String newNodeId = "C";
        String secondNodeId = "0";

        try (Transaction tx = db.beginTx()) {
            Result result1 = tx.execute("match (t:TRADITION {id:'" +
                    tradId + "'})-[:HAS_STEMMA]->(n:STEMMA { name:'" +
                    stemmaTitle + "'}) return n");
            Iterator<Node> stNodes = result1.columnAs("n");
            assertTrue(stNodes.hasNext());
            Node startNodeStemma = stNodes.next();

            List<Relationship> rel1 = DatabaseService.getRelationships(startNodeStemma,
                    Direction.OUTGOING, ERelations.HAS_ARCHETYPE);
            assertTrue(rel1.isEmpty());

            String newStemmaDot = "digraph \"Semstem 1402333041_0\" {  0 [ class=hypothetical ];  A [ class=extant ];  B [ class=extant ];  C [ class=extant ]; C -> B;  B -> A;  A -> 0;}";
            StemmaModel newStemmaResponse = jerseyTest
                    .target("/tradition/" + tradId + "/stemma/" + stemmaTitle + "/reorient/" + newNodeId)
                    .request(MediaType.APPLICATION_JSON)
                    .post(null, StemmaModel.class);
            Util.assertStemmasEquivalent(newStemmaDot, newStemmaResponse.getDot());

            List<Relationship> rel2 = DatabaseService.getRelationships(startNodeStemma,
                    Direction.OUTGOING, ERelations.HAS_ARCHETYPE);
            assertFalse(rel2.isEmpty());
            assertEquals(newNodeId, rel2.getFirst().getEndNode().getProperty("sigil").toString());

            try (Response actualStemmaResponseSecond = jerseyTest
                    .target("/tradition/" + tradId + "/stemma/" + stemmaTitle + "/reorient/" + secondNodeId)
                    .request(MediaType.APPLICATION_JSON)
                    .post(null)) {
                assertEquals(Response.Status.OK.getStatusCode(), actualStemmaResponseSecond.getStatus());
            }
        }
    }

    @Test
    public void reorientGraphStemmaNoNodesTest() {
        String stemmaTitle = "Semstem 1402333041_0";
        String falseNode = "X";
        String rightNode = "C";
        String falseTitle = "X";

        try (Response actualStemmaResponse = jerseyTest
                .target("/tradition/" + tradId + "/stemma/" + stemmaTitle + "/reorient/" + falseNode)
                .request(MediaType.APPLICATION_JSON)
                .post(null)) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualStemmaResponse.getStatus());
        }

        try (Response actualStemmaResponse2 = jerseyTest
                .target("/tradition/" + tradId + "/stemma/" + falseTitle + "/reorient/" + rightNode)
                .request(MediaType.APPLICATION_JSON)
                .post(null)) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualStemmaResponse2.getStatus());
        }

    }

    @Test
    public void reorientDigraphStemmaTest() {
        String stemmaTitle = "stemma";
        String newNodeId = "C";

        try (Transaction tx = db.beginTx()) {
            Result result1 = tx.execute("match (t:TRADITION {id:'" +
                    tradId + "'})-[:HAS_STEMMA]->(n:STEMMA { name:'" +
                    stemmaTitle + "'}) return n");
            Iterator<Node> stNodes = result1.columnAs("n");
            assertTrue(stNodes.hasNext());
            Node startNodeStemma = stNodes.next();

            List<Relationship> relBevor = DatabaseService.getRelationships(startNodeStemma,
                    Direction.OUTGOING, ERelations.HAS_ARCHETYPE);
            assertFalse(relBevor.isEmpty());
            assertEquals("0", relBevor.getFirst().getEndNode().getProperty("sigil").toString());

            try (Response actualStemmaResponse = jerseyTest
                    .target("/tradition/" + tradId + "/stemma/" + stemmaTitle + "/reorient/" + newNodeId)
                    .request(MediaType.APPLICATION_JSON)
                    .post(null)) {
                assertEquals(Response.Status.OK.getStatusCode(), actualStemmaResponse.getStatus());
            }

            List<Relationship> relAfter = DatabaseService.getRelationships(startNodeStemma,
                    Direction.OUTGOING, ERelations.HAS_ARCHETYPE);
            assertFalse(relAfter.isEmpty());
            assertEquals(newNodeId,
                    relAfter.getFirst().getEndNode().getProperty("sigil").toString());
        }
    }

    @Test
    public void reorientDigraphStemmaNoNodesTest() {
        String stemmaTitle = "stemma";
        String falseNode = "X";
        String rightNode = "C";
        String falseTitle = "X";

        try (Response actualStemmaResponse = jerseyTest
                .target("/tradition/" + tradId + "/stemma/" +
                        stemmaTitle + "/reorient/" + falseNode)
                .request(MediaType.APPLICATION_JSON)
                .post(null)) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualStemmaResponse.getStatus());
        }

        try (Response actualStemmaResponse2 = jerseyTest
                .target("/tradition/" + tradId + "/stemma/" +
                        falseTitle + "/reorient/" + rightNode)
                .request(MediaType.APPLICATION_JSON)
                .post(null)) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualStemmaResponse2.getStatus());
        }

    }

    @Test
    public void reorientDigraphStemmaSameNodeAsBeforeTest() {
        String stemmaTitle = "stemma";
        String newNode = "C";

        try (Response actualStemmaResponse = jerseyTest
                .target("/tradition/" + tradId + "/stemma/" +
                        stemmaTitle + "/reorient/" + newNode)
                .request(MediaType.APPLICATION_JSON)
                .post(null)) {
            assertEquals(Response.Status.OK.getStatusCode(), actualStemmaResponse.getStatus());
        }

        try (Response actualStemmaResponse2 = jerseyTest
                .target("/tradition/" + tradId + "/stemma/" +
                        stemmaTitle + "/reorient/" + newNode)
                .request(MediaType.APPLICATION_JSON)
                .post(null)) {
            assertEquals(Response.Status.OK.getStatusCode(), actualStemmaResponse2.getStatus());
        }

    }

    @Test
    public void uploadInvalidStemmaTest () {
        // A stemma with a node (A) that is not labeled as extant or hypothetical.
        StemmaModel input = new StemmaModel();
        input.setDot("""
graph "invalid" {
  0 [ class=hypothetical, label="*" ];
  "α" [ class=extant ];
  B [ class=extant ];
  C [ class=extant ];
  0 -- A;
  A -- B;
  A -- C;
}""");
        try (Response actualStemmaResponse = jerseyTest
                .target("/tradition/" + tradId + "/stemma")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(input))) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), actualStemmaResponse.getStatus());
            assertTrue(actualStemmaResponse.readEntity(String.class).contains("not marked as either hypothetical or extant"));
        }

    }

    @Test
    public void addTraditionAndStemmaTwiceTest() {
        StemmaModel input = new StemmaModel();
        input.setName("Semstem stemma");
        input.setDot("graph stemma {  0 [ class=hypothetical ];  A [ class=extant ];  B [ class=extant ];  C [ class=extant ]; 0 -- A;  A -- B;  A -- C;}");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/stemma")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(input))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), result.getStatus());
            StemmaModel origStemma = result.readEntity(StemmaModel.class);
            assertEquals("Semstem stemma", origStemma.getName());
            assertEquals(9, origStemma.getDot().split("\n").length);
        }

        // Now add the tradition and the stemma all over again and see what happens
        String newTradId = createTraditionFromFile("Tradition", "src/TestFiles/testTradition.xml");
        try (Response result = jerseyTest.target("/tradition/" + newTradId + "/stemma")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(input))) {
            StemmaModel secondStemma = result.readEntity(StemmaModel.class);
            assertEquals(9, secondStemma.getDot().split("\n").length);
        }

        StemmaModel firstStemma = jerseyTest.target("/tradition/" + tradId + "/stemma/Semstem%20stemma")
                .request().get(StemmaModel.class);
        assertEquals(9, firstStemma.getDot().split("\n").length);

    }

    @Test
    public void recordStemmaLabelTest () {
        StemmaModel input = new StemmaModel();
        input.setDot("""
graph "labeltest" {
  0 [ class=hypothetical, label="*" ];
  "α" [ class=extant ];
  B [ class=extant ];
  C [ class=extant ];
  0 -- "α";
  "α" -- B;
  "α" -- C;
}""");
        try (Response actualStemmaResponse = jerseyTest
                .target("/tradition/" + tradId + "/stemma")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(input))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), actualStemmaResponse.getStatus());
        }

        try (Transaction tx = db.beginTx()) {
            Result r = tx.execute("match (s:STEMMA {name:'labeltest'})-[:HAS_WITNESS]->(z:WITNESS {sigil:'0'}), " +
                    "(s)-[:HAS_WITNESS]->(a:WITNESS {sigil:'α'}) return z, a");
            assertTrue(r.hasNext());
            Map<String, Object> row = r.next();
            Node alphaNode = (Node) row.get("a");
            Node zeroNode = (Node) row.get("z");

            assertTrue(zeroNode.hasProperty("label"));
            assertFalse(alphaNode.hasProperty("label"));
        }

    }

    @Test
    public void deleteStemmaTest () {
        String fileName = "src/TestFiles/florilegium_graphml.xml";
        String tradId = createTraditionFromFile("Florilegium", fileName);
        int originalNodeCount = 0;

        StemmaModel stemmaCM = new StemmaModel(); // its name will be "Stemma"
        StemmaModel stemmaTF = new StemmaModel(); // its name will be "TF Stemma"
        try {
            byte[] encoded = Files.readAllBytes(Paths.get("src/TestFiles/florilegium.dot"));
            stemmaCM.setDot(new String(encoded, StandardCharsets.UTF_8));

            encoded = Files.readAllBytes(Paths.get("src/TestFiles/florilegium_tf.dot"));
            stemmaTF.setDot(new String(encoded, StandardCharsets.UTF_8));
        } catch (Exception e) {
            fail();
        }
        try (Transaction tx = db.beginTx()) {
            // Count the nodes to start with
            originalNodeCount = countGraphNodes(tx);

            // Add two stemmata and check the node count
            DotParser parser = new DotParser(tx);

            String stemmaId = parser.importStemmaFromDot(tradId, stemmaCM);
            assertEquals(stemmaCM.getName(), stemmaId);
            assertEquals(originalNodeCount + 9, countGraphNodes(tx));

            stemmaId = parser.importStemmaFromDot(tradId, stemmaTF);
            assertEquals(stemmaTF.getName(), stemmaId);
            tx.commit();
        } catch (Exception e) {
            fail();
        }
        try (Transaction tx = db.beginTx()) {
            assertEquals(originalNodeCount + 19, countGraphNodes(tx));
        }
        	
        // Delete one stemma
        try (Response deleteResponse = jerseyTest
                .target("/tradition/" + tradId + "/stemma/Stemma")
                .request()
                .delete()) {
            assertEquals(Response.Status.OK.getStatusCode(), deleteResponse.getStatus());
        }
        try (Transaction tx = db.beginTx()) {
            // Check the node count
            assertEquals(originalNodeCount + 10, countGraphNodes(tx));
        }
        	
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
        try (Transaction tx = db.beginTx()) {
    		Node traditionNode = VariantGraphService.getTraditionNode(tx, tradId);
            ArrayList<Node> stemmata = DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA);
            assertEquals(2, stemmata.size());
        }

        StemmaModel input = new StemmaModel();
        input.setDot("""
graph stemma {
  0 [ class=hypothetical ];
  A [ class=extant ];
  B [ class=extant ];
  C [ class=extant ];
  0 -- A;
  A -- B;
  A -- C;
}""");

        try (Response actualStemmaResponse = jerseyTest
                .target("/tradition/" + tradId + "/stemma/stemma")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(input))) {
            assertEquals(Response.Status.OK.getStatusCode(), actualStemmaResponse.getStatus());
        }
        try (Transaction tx = db.beginTx()) {
        	Node traditionNode = VariantGraphService.getTraditionNode(tx, tradId);
        	assertEquals(2, DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA).size());
        }

        StemmaModel replacedStemma = jerseyTest
                .target("/tradition/" + tradId + "/stemma/stemma"  )
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        Util.assertStemmasEquivalent(input.getDot(), replacedStemma.getDot());
    }

    @Test
    public void replaceStemmaWithDudTest() {
        try (Transaction tx = db.beginTx()) {
    		Node traditionNode = VariantGraphService.getTraditionNode(tx, tradId);
    		ArrayList<Node> stemmata = DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA);
            assertEquals(2, stemmata.size());
        }

        String original = """
digraph "stemma" {
  0 [ class=hypothetical ];
  A [ class=extant ];
  B [ class=extant ];
  C [ class=extant ];
  0 -> A;
  0 -> B;
  A -> C;
}""";
        String input = "graph stemma {\n  0 [ class=hypothetical ];  A [ class=extant ];  B [ class=extant ];  C [ class=extant ]; 0 -- A;  A -- B;  A -- D;\n}";

        try (Response actualStemmaResponse = jerseyTest
                .target("/tradition/" + tradId + "/stemma/stemma")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(input))) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), actualStemmaResponse.getStatus());
        }

        // Do we still have the old one?
    	try (Transaction tx = db.beginTx()) {
    		Node traditionNode = VariantGraphService.getTraditionNode(tx, tradId);
    		ArrayList<Node> stemmata = DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA);
            assertEquals(2, stemmata.size());
        }

        StemmaModel storedStemma = jerseyTest
                .target("/tradition/" + tradId + "/stemma/stemma"  )
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        Util.assertStemmasEquivalent(original, storedStemma.getDot());
    }

    @Test
    public void replaceStemmaNameMismatchTest() {
        // This used to fail; now the name given in the model overrides the name in the dot.
    	try (Transaction tx = db.beginTx()) {
            Node traditionNode = VariantGraphService.getTraditionNode(tx, tradId);
            ArrayList<Node> stemmata = DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA);
            assertEquals(2, stemmata.size());
        }

        String input = "graph stemma2 {  0 [ class=hypothetical ];  A [ class=extant ];  B [ class=extant ];  C [ class=extant ]; 0 -- A;  A -- B;  A -- C;}";
        StemmaModel stemmaSpec = new StemmaModel();
        stemmaSpec.setDot(input);

        try (Response actualStemmaResponse = jerseyTest
                .target("/tradition/" + tradId + "/stemma/stemma")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(stemmaSpec))) {
            assertEquals(Response.Status.OK.getStatusCode(), actualStemmaResponse.getStatus());
            assertEquals("stemma", actualStemmaResponse.readEntity(StemmaModel.class).getName());
        }

        // Do we still have the old one?
    	try (Transaction tx = db.beginTx()) {
            Node traditionNode = VariantGraphService.getTraditionNode(tx, tradId);
            ArrayList<Node> stemmata = DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA);
            assertEquals(2, stemmata.size());
        }

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
        String dotEquivalent = """
graph "RHM 1382784254" {
  0 [ class=hypothetical ];
  1 [ class=hypothetical ];
  10 [ class=hypothetical ];
  11 [ class=hypothetical ];
  2 [ class=hypothetical ];
  3 [ class=hypothetical ];
  4 [ class=hypothetical ];
  5 [ class=hypothetical ];
  6 [ class=hypothetical ];
  7 [ class=hypothetical ];
  8 [ class=hypothetical ];
  9 [ class=hypothetical ];
  A [ class=extant ];
  B [ class=extant ];
  C [ class=extant ];
  D [ class=extant ];
  F [ class=extant ];
  J [ class=extant ];
  L [ class=extant ];
  M [ class=extant ];
  S [ class=extant ];
  T1 [ class=extant ];
  T2 [ class=extant ];
  U [ class=extant ];
  V [ class=extant ];
  0 -- 1;
  0 -- T1;
  10 -- 11;
  10 -- 9;
  10 -- D;
  11 -- C;
  11 -- M;
  1 -- 2;
  1 -- B;
  2 -- 3;
  2 -- A;
  3 -- 4;
  4 -- 5;
  5 -- 6;
  6 -- 7;
  7 -- 8;
  8 -- 9;
  F -- 8;
  J -- 3;
  L -- 7;
  S -- 9;
  T2 -- 4;
  U -- 5;
  V -- 6;
}""";
        String stemmaName = "From RHM";
        StemmaModel sm = new StemmaModel();
        sm.setName(stemmaName);
        sm.setJobid(37);
        sm.setNewick(newickSpec);
        String tradId = createTraditionFromFile("Besoin", "src/TestFiles/besoin.xml");
        // Set a matching job ID so we can test that it is removed on import
        TraditionModel textInfo = jerseyTest.target("/tradition/" + tradId)
                .request().get(TraditionModel.class);
        textInfo.setStemweb_jobid(37);
        try (Response r = jerseyTest.target("/tradition/" + tradId)
                .request(MediaType.APPLICATION_JSON).put(Entity.json(textInfo))) {
            assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
        }
        // Now add the stemma
        StemmaModel result;
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/stemma")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(sm))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
            result = r.readEntity(StemmaModel.class);
        }

        assertTrue(result.getIs_undirected());
        assertEquals(stemmaName, result.getName());
        assertTrue(result.cameFromJobid());
        assertEquals(Integer.valueOf(37), result.getJobid());
        Util.assertStemmasEquivalent(dotEquivalent, result.getDot());

        // and check that the job ID on the tradition has been unset.
        textInfo = jerseyTest.target("/tradition/" + tradId)
                .request().get(TraditionModel.class);
        assertNull(textInfo.getStemweb_jobid());
    }

    @Test
    public void importNeighbourNetStemmaTest() {
        // See if a Neighbour Net generated stemma will actually import correctly
        String tradId = createTraditionFromFile("Sapientia", "src/TestFiles/Sapientia.xml");
        // Set a job ID on the tradition
        TraditionModel textInfo = jerseyTest.target("/tradition/" + tradId)
                .request().get(TraditionModel.class);
        textInfo.setStemweb_jobid(52);
        try (Response r = jerseyTest.target("/tradition/" + tradId)
                .request(MediaType.APPLICATION_JSON).put(Entity.json(textInfo))) {
            assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
            assertEquals(textInfo.getStemweb_jobid(), r.readEntity(TraditionModel.class).getStemweb_jobid());
        }
        // Read the dot specification in from the test file
        String dotSpec = "";
        try {
            dotSpec = new String(Files.readAllBytes(Paths.get("src/TestFiles/sapientia_nn.dot")));
        } catch (IOException e) {
            fail(e.getMessage());
        }
        StemmaModel sm = new StemmaModel();
        sm.setName("Neighbour Net 1749128699");
        sm.setJobid(52);
        sm.setDot(dotSpec);
        // Post the stemma and make sure it has the right information
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/stemma")
                .request(MediaType.APPLICATION_JSON).post(Entity.json(sm))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
            StemmaModel result = r.readEntity(StemmaModel.class);
            assertTrue(result.getIs_undirected());
            assertTrue(result.cameFromJobid());
            assertEquals(sm.getJobid(), result.getJobid());
        }
        // and check that the job ID on the tradition has been unset.
        textInfo = jerseyTest.target("/tradition/" + tradId)
                .request().get(TraditionModel.class);
        assertNull(textInfo.getStemweb_jobid());
    }

    @Test
    public void stemmaIdAssignmentTest() {
        StemmaModel firstStemma = jerseyTest.target("/tradition/" + tradId + "/stemma/stemma")
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        assertNotNull(firstStemma.getId());
        Long.parseLong(firstStemma.getId());  // throws if not a valid Long

        StemmaModel secondStemma = jerseyTest.target("/tradition/" + tradId + "/stemma/Semstem%201402333041_0")
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        assertNotNull(secondStemma.getId());
        Long.parseLong(secondStemma.getId());

        assertNotEquals(firstStemma.getId(), secondStemma.getId());
    }

    @Test
    public void stemmaDuplicateNameRejectedTest() {
        // A second stemma with the same name as an existing one, in the same tradition: 409.
        StemmaModel dup = new StemmaModel();
        dup.setDot("graph \"stemma\" {  H0 [ class=hypothetical ];  A [ class=extant ];  H0 -- A;}");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/stemma")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(dup))) {
            assertEquals(Response.Status.CONFLICT.getStatusCode(), result.getStatus());
        }
    }

    @Test
    public void stemmaNumericOnlyNameRejectedDotTest() {
        StemmaModel input = new StemmaModel();
        input.setDot("graph \"123\" {  H0 [ class=hypothetical ];  A [ class=extant ];  H0 -- A;}");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/stemma")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(input))) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), result.getStatus());
        }
    }

    @Test
    public void stemmaNumericOnlyNameRejectedNewickTest() {
        StemmaModel sm = new StemmaModel();
        sm.setName("123");
        sm.setNewick("(A,B);");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/stemma")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(sm))) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), result.getStatus());
        }
    }

    @Test
    public void stemmaDualAddressingTest() {
        // GET by numeric id behaves identically to GET by name.
        StemmaModel byName = jerseyTest.target("/tradition/" + tradId + "/stemma/stemma")
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        String id = byName.getId();
        StemmaModel byId = jerseyTest.target("/tradition/" + tradId + "/stemma/" + id)
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        assertEquals(byName.getName(), byId.getName());
        assertEquals(byName.getId(), byId.getId());
        Util.assertStemmasEquivalent(byName.getDot(), byId.getDot());

        // A numeric ref matching no stemma: 404.
        Response notFound = jerseyTest.target("/tradition/" + tradId + "/stemma/999999")
                .request(MediaType.APPLICATION_JSON)
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), notFound.getStatus());

        // PUT (metadata-only rename) by numeric id behaves identically to PUT by name.
        StemmaModel renameReq = new StemmaModel();
        renameReq.setName("renamed-by-id");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/stemma/" + id)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(renameReq))) {
            assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
            assertEquals("renamed-by-id", result.readEntity(StemmaModel.class).getName());
        }

        // DELETE by numeric id behaves identically to DELETE by name.
        StemmaModel second = jerseyTest.target("/tradition/" + tradId + "/stemma/Semstem%201402333041_0")
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/stemma/" + second.getId())
                .request()
                .delete()) {
            assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
        }
        Response afterDelete = jerseyTest.target("/tradition/" + tradId + "/stemma/" + second.getId())
                .request(MediaType.APPLICATION_JSON)
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), afterDelete.getStatus());
    }

    @Test
    public void reorientStemmaByNumericIdTest() {
        StemmaModel stemma = jerseyTest.target("/tradition/" + tradId + "/stemma/stemma")
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/stemma/" + stemma.getId() + "/reorient/C")
                .request(MediaType.APPLICATION_JSON)
                .post(null)) {
            assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
        }
    }

    @Test
    public void ambiguousStemmaNameTest() {
        // Simulate legacy data from before name uniqueness was enforced: two STEMMA nodes
        // sharing the same name, created directly via the Neo4j API (bypassing the import
        // parsers / DatabaseService.ensureNameUnique entirely).
        String dupName = "DUPSTEMMA";
        try (Transaction tx = db.beginTx()) {
            Node tradNode = VariantGraphService.getTraditionNode(tx, tradId);
            for (int i = 0; i < 2; i++) {
                Node bogus = DatabaseService.createNode(tx, Nodes.STEMMA);
                bogus.setProperty("name", dupName);
                bogus.setProperty("directed", false);
                tradNode.createRelationshipTo(bogus, ERelations.HAS_STEMMA);
            }
            tx.commit();
        }
        Response response = jerseyTest.target("/tradition/" + tradId + "/stemma/" + dupName)
                .request(MediaType.APPLICATION_JSON)
                .get();
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    public void metadataOnlyRenameTest() {
        StemmaModel original = jerseyTest.target("/tradition/" + tradId + "/stemma/stemma")
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);

        // Rename only (no dot/newick in the body): 200, name changed, topology unchanged.
        StemmaModel renameReq = new StemmaModel();
        renameReq.setName("stemma-renamed");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/stemma/stemma")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(renameReq))) {
            assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
            StemmaModel renamed = result.readEntity(StemmaModel.class);
            assertEquals("stemma-renamed", renamed.getName());
            assertEquals(original.getId(), renamed.getId());
            Util.assertStemmasEquivalent(original.getDot(), renamed.getDot());
        }

        // Renaming to a name already used by another stemma in the tradition: 409.
        StemmaModel conflictReq = new StemmaModel();
        conflictReq.setName("Semstem 1402333041_0");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/stemma/stemma-renamed")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(conflictReq))) {
            assertEquals(Response.Status.CONFLICT.getStatusCode(), result.getStatus());
        }

        // Renaming to a numeric name: 400.
        StemmaModel numericReq = new StemmaModel();
        numericReq.setName("123");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/stemma/stemma-renamed")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(numericReq))) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), result.getStatus());
        }

        // Renaming to the stemma's own current name: 200, not 409.
        StemmaModel noopReq = new StemmaModel();
        noopReq.setName("stemma-renamed");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/stemma/stemma-renamed")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(noopReq))) {
            assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
        }

        // A metadata-only body (no dot/newick) against a nonexistent ref: 404 -- creation
        // still requires a dot or newick payload.
        StemmaModel noSuchRef = new StemmaModel();
        noSuchRef.setName("doesnotmatter");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/stemma/doesnotexist")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(noSuchRef))) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), result.getStatus());
        }
    }

    @Test
    public void stemmaNonexistentTraditionTest() {
        // GET/PUT/DELETE against a tradition id that doesn't exist returns 404
        String badTradId = "10000";

        Response getResponse = jerseyTest.target("/tradition/" + badTradId + "/stemma/stemma")
                .request(MediaType.APPLICATION_JSON)
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), getResponse.getStatus());

        StemmaModel putBody = new StemmaModel();
        putBody.setName("whatever");
        try (Response putResponse = jerseyTest.target("/tradition/" + badTradId + "/stemma/stemma")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(putBody))) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), putResponse.getStatus());
        }

        try (Response deleteResponse = jerseyTest.target("/tradition/" + badTradId + "/stemma/stemma")
                .request()
                .delete()) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), deleteResponse.getStatus());
        }
    }

    @Test
    public void replaceStemmaByIdKeepsIdTest() {
        StemmaModel original = jerseyTest.target("/tradition/" + tradId + "/stemma/stemma")
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        String id = original.getId();
        assertNotNull(id);

        // Full (dot) replacement, addressed by numeric id.
        StemmaModel input = new StemmaModel();
        input.setDot("graph stemma {  H0 [ class=hypothetical ];  A [ class=extant ];  B [ class=extant ];"
                + "  C [ class=extant ]; H0 -- A;  A -- B;  A -- C;}");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/stemma/" + id)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(input))) {
            assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
            StemmaModel replaced = result.readEntity(StemmaModel.class);
            assertEquals(id, replaced.getId());
            assertEquals("stemma", replaced.getName());
            Util.assertStemmasEquivalent(input.getDot(), replaced.getDot());
        }
        // Still addressable by the same id afterwards.
        StemmaModel byId = jerseyTest.target("/tradition/" + tradId + "/stemma/" + id)
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        assertEquals("stemma", byId.getName());
        Util.assertStemmasEquivalent(input.getDot(), byId.getDot());

        // Full (Newick) replacement, addressed by numeric id.
        StemmaModel newick = new StemmaModel();
        newick.setNewick("((A,B),C);");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/stemma/" + id)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(newick))) {
            assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
            StemmaModel replaced = result.readEntity(StemmaModel.class);
            assertEquals(id, replaced.getId());
            assertEquals("stemma", replaced.getName());
        }

        // Full replacement addressed by name also keeps the id.
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/stemma/stemma")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(input))) {
            assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
            assertEquals(id, result.readEntity(StemmaModel.class).getId());
        }

        // Full replacement by id that also renames
        StemmaModel renaming = new StemmaModel();
        renaming.setName("stemma-v2");
        renaming.setDot(input.getDot());
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/stemma/" + id)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(renaming))) {
            assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
            StemmaModel replaced = result.readEntity(StemmaModel.class);
            assertEquals(id, replaced.getId());
            assertEquals("stemma-v2", replaced.getName());
        }
        try (Transaction tx = db.beginTx()) {
            Node traditionNode = VariantGraphService.getTraditionNode(tx, tradId);
            assertEquals(2, DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA).size());
        }
    }

    @Test
    public void stemmaCrossTraditionIdTest() {
        // A numeric stemma id belonging to another tradition must not resolve within this one.
        String otherTradId = createTraditionFromFile("Other", "src/TestFiles/testTradition.xml");
        StemmaModel ours = jerseyTest.target("/tradition/" + tradId + "/stemma/stemma")
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        String ourId = ours.getId();

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), jerseyTest
                .target("/tradition/" + otherTradId + "/stemma/" + ourId)
                .request(MediaType.APPLICATION_JSON).get().getStatus());
        StemmaModel renameReq = new StemmaModel();
        renameReq.setName("hijacked");
        try (Response r = jerseyTest.target("/tradition/" + otherTradId + "/stemma/" + ourId)
                .request(MediaType.APPLICATION_JSON).put(Entity.json(renameReq))) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), r.getStatus());
        }
        try (Response r = jerseyTest.target("/tradition/" + otherTradId + "/stemma/" + ourId + "/reorient/C")
                .request(MediaType.APPLICATION_JSON).post(null)) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), r.getStatus());
        }
        try (Response r = jerseyTest.target("/tradition/" + otherTradId + "/stemma/" + ourId)
                .request().delete()) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), r.getStatus());
        }

        // Ours is untouched.
        StemmaModel stillOurs = jerseyTest.target("/tradition/" + tradId + "/stemma/" + ourId)
                .request(MediaType.APPLICATION_JSON)
                .get(StemmaModel.class);
        assertEquals("stemma", stillOurs.getName());
        Util.assertStemmasEquivalent(ours.getDot(), stillOurs.getDot());
    }

    @Test
    public void bareDigitHypotheticalSigilTest() {
        // Hypothetical witnesses conventionally carry bare-digit sigils; these are never
        // exported as TEI xml:ids, so they need not be NCNames.
        StemmaModel input = new StemmaModel();
        input.setDot("digraph \"digits\" {  0 [ class=hypothetical ];  1 [ class=hypothetical ];  A [ class=extant ];"
                + "  B [ class=extant ];  C [ class=extant ]; 0 -> A;  0 -> 1;  1 -> B;  1 -> C;}");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/stemma")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(input))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), result.getStatus());
            Util.assertStemmasEquivalent(input.getDot(), result.readEntity(StemmaModel.class).getDot());
        }

        // A hypothetical sigil with characters that would break a REST path is still rejected.
        StemmaModel badHypothetical = new StemmaModel();
        badHypothetical.setDot("digraph \"badhyp\" {  \"x#y\" [ class=hypothetical ];  A [ class=extant ]; \"x#y\" -> A;}");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/stemma")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(badHypothetical))) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), result.getStatus());
        }

        // An extant witness with a bare-digit sigil is still rejected.
        StemmaModel badExtant = new StemmaModel();
        badExtant.setDot("digraph \"badext\" {  H9 [ class=hypothetical ];  7 [ class=extant ]; H9 -> 7;}");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/stemma")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(badExtant))) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), result.getStatus());
        }
        // Neither failed attempt left any partial stemma or witness behind.
        try (Transaction tx = db.beginTx()) {
            Node traditionNode = VariantGraphService.getTraditionNode(tx, tradId);
            assertEquals(3, DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA).size());
            assertEquals(3, DatabaseService.getRelated(traditionNode, ERelations.HAS_WITNESS).size());
        }
    }

    @Test
    public void legacyEmbeddedStemmaSigilTest() throws IOException {
        String legacy = Files.readString(Paths.get("src/TestFiles/testTradition.xml"), StandardCharsets.UTF_8);

        // Legacy Stemmaweb XML with bare-digit hypothetical sigils imports, stemmata and all.
        String digits = legacy.replace("H0", "0");
        Response r = Util.createTraditionFromFileOrString(jerseyTest, "Legacy", "LR", "1", digits, "stemmaweb");
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        String digitsId = Util.getValueFromJson(r, "tradId");
        List<StemmaModel> stemmata = jerseyTest.target("/tradition/" + digitsId + "/stemmata")
                .request().get(new GenericType<>() {});
        assertEquals(2, stemmata.size());
        assertTrue(stemmata.stream().allMatch(x -> x.getDot().contains("0")));

        // One bad embedded stemma is skipped without failing the whole tradition import, and
        // leaves no partial stemma behind.
        String oneBad = legacy.replaceFirst("graph \"Semstem 1402333041_0\" \\{  0", "graph \"Semstem 1402333041_0\" {  \"x#y\"")
                .replace("0 -- A", "\"x#y\" -- A");
        assertTrue(oneBad.contains("x#y"));
        r = Util.createTraditionFromFileOrString(jerseyTest, "LegacyBad", "LR", "1", oneBad, "stemmaweb");
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        String badId = Util.getValueFromJson(r, "tradId");
        stemmata = jerseyTest.target("/tradition/" + badId + "/stemmata")
                .request().get(new GenericType<>() {});
        assertEquals(1, stemmata.size());
        assertEquals("stemma", stemmata.getFirst().getName());
        try (Transaction tx = db.beginTx()) {
            // Every STEMMA node in the database belongs to some tradition.
            tx.findNodes(Nodes.STEMMA).forEachRemaining(
                    s -> assertNotNull(s.getSingleRelationship(ERelations.HAS_STEMMA, Direction.INCOMING)));
        }
    }

    private int countGraphNodes(Transaction tx) {
        AtomicInteger numNodes = new AtomicInteger(0);
        tx.execute("match (n) return n").forEachRemaining(x -> numNodes.getAndIncrement());

        return numNodes.get();
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