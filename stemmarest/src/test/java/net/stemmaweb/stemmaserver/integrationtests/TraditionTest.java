package net.stemmaweb.stemmaserver.integrationtests;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.model.*;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.DatabaseService;
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
 * Contains all tests for the api calls related to the tradition.
 *
 * @author PSE FS 2015 Team2
 */
public class TraditionTest {
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

        // Create a JerseyTestServer for the necessary REST API calls
        Root webResource = new Root();
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(webResource)
                .create();
        jerseyTest.setUp();

        /*
         * create a tradition inside the test DB
         */
        tradId = createTraditionFromFile("Tradition", "src/TestFiles/testTradition.xml", "1");
    }

    private String createTraditionFromFile(String tName, String fName, String userId) {

        ClientResponse jerseyResult = null;
        try {
            jerseyResult = Util.createTraditionFromFileOrString(jerseyTest, tName, "LR", userId, fName, "stemmaweb");
        } catch (Exception e) {
            fail();
        }
        return Util.getValueFromJson(jerseyResult, "tradId");
    }

    @Test
    public void getAllTraditionsTest() {
        HashSet<String> expectedIds = new HashSet<>();
        expectedIds.add(tradId);

        // import a second tradition into the db
        String testfile = "src/TestFiles/testTradition.xml";
        expectedIds.add(createTraditionFromFile("Tradition", testfile, "1"));

        List<TraditionModel> traditions = jerseyTest.resource().path("/traditions")
                .get(new GenericType<List<TraditionModel>>() {});
        for (TraditionModel returned : traditions) {
            assertTrue(expectedIds.contains(returned.getId()));
            assertEquals("Tradition", returned.getName());
        }
    }

    @Test
    public void getAllTraditionsWithParameterNotFoundTest() {
        ClientResponse resp = jerseyTest
                .resource()
                .path("/traditions/" + 2342)
                .get(ClientResponse.class);
        assertEquals(Response.status(Status.NOT_FOUND).build().getStatus(), resp.getStatus());
    }

    /* TODO: This test needs to be fixed - it expects the relationships to be returned in
       an order that is not guaranteed. */
    @Test(expected = org.junit.ComparisonFailure.class)
    public void getAllRelationshipsTest() {
        String jsonPayload = "{\"role\":\"user\",\"id\":1}";
        jerseyTest.resource()
                .path("/user/1")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, jsonPayload);

        RelationshipModel rel = new RelationshipModel();
        rel.setSource("27");
        rel.setTarget("16");
        rel.setId("36");
        rel.setReading_a("april");
        rel.setIs_significant("no");
        rel.setReading_b("april");
        rel.setAlters_meaning(0L);
        rel.setType("transposition");
        rel.setScope("local");

        List<RelationshipModel> relationships = jerseyTest.resource()
                .path("/tradition/" + tradId + "/relationships")
                .get(new GenericType<List<RelationshipModel>>() {});
        RelationshipModel relLoaded = relationships.get(2);

        assertEquals(rel.getSource(), relLoaded.getSource());
        assertEquals(rel.getTarget(), relLoaded.getTarget());
        assertEquals(rel.getId(), relLoaded.getId());
        assertEquals(rel.getReading_a(), relLoaded.getReading_a());
        assertEquals(rel.getIs_significant(), relLoaded.getIs_significant());
        assertEquals(rel.getReading_b(), relLoaded.getReading_b());
        assertEquals(rel.getAlters_meaning(), relLoaded.getAlters_meaning());
        assertEquals(rel.getType(), relLoaded.getType());
        assertEquals(rel.getScope(), relLoaded.getScope());
    }

    @Test
    public void getAllRelationshipsCorrectAmountTest() {

        List<RelationshipModel> relationships = jerseyTest.resource()
                .path("/tradition/" + tradId + "/relationships")
                .get(new GenericType<List<RelationshipModel>>() {});

        assertEquals(3, relationships.size());
    }

    @Test
    public void getAllWitnessesTest() {
        Set<String> expectedWitnesses = new HashSet<>(Arrays.asList("A", "B", "C"));
        List<WitnessModel> witnesses = jerseyTest.resource()
                .path("/tradition/" + tradId + "/witnesses")
                .get(new GenericType<List<WitnessModel>>() {});
        assertEquals(expectedWitnesses.size(), witnesses.size());
        for (WitnessModel w: witnesses) {
            assertTrue(expectedWitnesses.contains(w.getSigil()));
        }
    }

    @Test
    public void getAllWitnessesTraditionNotFoundTest() {
        ClientResponse resp = jerseyTest.resource()
                .path("/tradition/10000/witnesses")
                .get(ClientResponse.class);

        assertEquals(Response.status(Status.NOT_FOUND).build().getStatus(), resp.getStatus());
    }

    @Test
    public void getDotOfNonExistentTraditionTest() {
        ClientResponse resp = jerseyTest
                .resource()
                .path("/tradition/10000/dot")
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);

        assertEquals(Response.status(Status.NOT_FOUND).build().getStatus(), resp.getStatus());
    }

    @Test
    public void getDotTest() {
        String str = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/dot")
                .type(MediaType.APPLICATION_JSON)
                .get(String.class);

        String[] exp = new String[64];
        exp[0] = "digraph \"Tradition\" {";
        exp[1] = "graph [bgcolor=\"none\", rankdir=\"LR\"];";
        exp[2] = "node [fillcolor=\"white\", fontsize=\"14\", shape=\"ellipse\", style=\"filled\"];";
        exp[3] = "edge [arrowhead=\"open\", color=\"#000000\", fontcolor=\"#000000\"];";
        exp[4] = "subgraph { rank=same \"n5\" \"#SILENT#\" }";
        exp[5] = "\"#SILENT#\" [shape=diamond,color=white,penwidth=0,label=\"\"];";
        exp[6] = "n5 [id=\"n5\", label=\"#START#\"];";
        exp[7] = "n6 [id=\"n6\", label=\"when\"];";
        exp[8] = "n5->n6 [label=\"A,B,C\", id=\"e0\", penwidth=\"1.4\"];";
        exp[9] = "n17 [id=\"n17\", label=\"april\"]";
        exp[10] = "n6->n17 [label=\"A\", id=\"e1\", penwidth=\"1.0\"];";
        exp[11] = "n25 [id=\"n25\", label=\"showers\"];";
        exp[12] = "n24->n25 [label=\"A\", id=\"e2\", penwidth=\"1.0\"];";
        exp[13] = "n6->n25 [label=\"B,C\", id=\"e3\", penwidth=\"1.2\"];";
        exp[14] = "n23 [id=\"n23\", label=\"with\"];";
        exp[15] = "n17->n23 [label=\"A\", id=\"e4\", penwidth=\"1.0\"];";
        exp[16] = "n26 [id=\"n26\", label=\"sweet\"];";
        exp[17] = "n25->n26 [label=\"A,B,C\", id=\"e5\", penwidth=\"1.4\"];";
        exp[18] = "n24 [id=\"n24\", label=\"his\"];";
        exp[19] = "n23->n24 [label=\"A\", id=\"e6\", penwidth=\"1.0\"];";
        exp[20] = "n27 [id=\"n27\", label=\"with\"];";
        exp[21] = "n26->n27 [label=\"A,B,C\", id=\"e7\", penwidth=\"1.4\"];";
        exp[22] = "n28 [id=\"n28\", label=\"april\"];";
        exp[23] = "n27->n28 [label=\"B,C\", id=\"e10\", penwidth=\"1.2\"];";
        exp[24] = "n8 [id=\"n8\", label=\"fruit\"];";
        exp[25] = "n27->n8 [label=\"A\", id=\"e8\", penwidth=\"1.0\"];";
        exp[26] = "n28->n8 [label=\"B,C\", id=\"e9\", penwidth=\"1.2\"];";
        exp[27] = "n10 [id=\"n10\", label=\"teh\"];";
        exp[28] = "n8->n10 [label=\"C\", id=\"e11\", penwidth=\"1.0\"];";
        exp[29] = "n9 [id=\"n9\", label=\"the\"];";
        exp[30] = "n8->n9 [label=\"A,B\", id=\"e12\", penwidth=\"1.2\"];";
        exp[31] = "n12 [id=\"n12\", label=\"drought\"];";
        exp[32] = "n10->n12 [label=\"C\", id=\"e13\", penwidth=\"1.0\"];";
        exp[33] = "n9->n12 [label=\"A\", id=\"e14\", penwidth=\"1.0\"];";
        exp[34] = "n11 [id=\"n11\", label=\"march\"];";
        exp[35] = "n9->n11 [label=\"B\", id=\"e15\", penwidth=\"1.0\"];";
        exp[36] = "n13 [id=\"n13\", label=\"of\"];";
        exp[37] = "n12->n13 [label=\"A,C\", id=\"e16\", penwidth=\"1.2\"];";
        exp[38] = "n11->n13 [label=\"B\", id=\"e17\", penwidth=\"1.0\"];";
        exp[39] = "n15 [id=\"n15\", label=\"drought\"];";
        exp[40] = "n13->n15 [label=\"B\", id=\"e19\", penwidth=\"1.0\"];";
        exp[41] = "n14 [id=\"n14\", label=\"march\"];";
        exp[42] = "n13->n14 [label=\"A,C\", id=\"e18\", penwidth=\"1.2\"];";
        exp[43] = "n16 [id=\"n16\", label=\"has\"];";
        exp[44] = "n15->n16 [label=\"B\", id=\"e21\", penwidth=\"1.0\"];";
        exp[45] = "n14->n16 [label=\"A,C\", id=\"e20\", penwidth=\"1.2\"];";
        exp[46] = "n18 [id=\"n18\", label=\"pierced\"];";
        exp[47] = "n16->n18 [label=\"A,B,C\", id=\"e22\", penwidth=\"1.4\"];";
        exp[48] = "n20 [id=\"n20\", label=\"to\"];";
        exp[49] = "n18->n20 [label=\"B\", id=\"e25\", penwidth=\"1.0\"];";
        exp[50] = "n19 [id=\"n19\", label=\"unto\"];";
        exp[51] = "n18->n19 [label=\"A\", id=\"e23\", penwidth=\"1.0\"];";
        exp[52] = "n21 [id=\"n21\", label=\"teh\"];";
        exp[53] = "n18->n21 [label=\"C\", id=\"e24\", penwidth=\"1.0\"];";
        exp[54] = "n22 [id=\"n22\", label=\"the\"];";
        exp[55] = "n19->n22 [label=\"A\", id=\"e27\", penwidth=\"1.0\"];";
        exp[56] = "n20->n22 [label=\"B\", id=\"e26\", penwidth=\"1.0\"];";
        exp[57] = "n29 [id=\"n29\", label=\"rood\"];";
        exp[58] = "n21->n29 [label=\"C\", id=\"e28\", penwidth=\"1.0\"];";
        exp[59] = "n7 [id=\"n7\", label=\"root\"];";
        exp[60] = "n22->n7 [label=\"A,B\", id=\"e29\", penwidth=\"1.2\"];";
        exp[61] = "n4 [id=\"n4\", label=\"#END#\"];";
        exp[62] = "n29->n4 [label=\"C\", id=\"e30\", penwidth=\"1.0\"];";
        exp[63] = "n7->n4 [label=\"A,B\", id=\"e31\", penwidth=\"1.2\"];";

        for (String anExp : exp) {
            assertTrue(str.contains(anExp));
        }
    }

    /**
     * Test if it is possible to change the user of a Tradition
     */
    @Test
    public void changeMetadataOfATraditionTest() {

        Result result;
        Node newUser;
        /*
         * Create a second user with id 42
         */
        try (Transaction tx = db.beginTx()) {
            Node rootNode = db.findNode(Nodes.ROOT, "name", "Root node");
            newUser = db.createNode(Nodes.USER);
            newUser.setProperty("id", "42");
            newUser.setProperty("role", "admin");

            rootNode.createRelationshipTo(newUser, ERelations.SYSTEMUSER);
            tx.success();
        }

        /*
         * The user with id 42 has no tradition
         */
        try (Transaction tx = db.beginTx()) {
            Iterable<Relationship> ownedTraditions = newUser.getRelationships(ERelations.OWNS_TRADITION);
            tx.success();
            assertFalse(ownedTraditions.iterator().hasNext());
        }

        /*
         * Verify that user 1 has tradition
         */
        try (Transaction tx = db.beginTx()) {
            Node origUser = db.findNode(Nodes.USER, "id", "1");
            Iterable<Relationship> ownership = origUser.getRelationships(ERelations.OWNS_TRADITION);
            assertTrue(ownership.iterator().hasNext());
            Node tradNode = ownership.iterator().next().getEndNode();
            TraditionModel tradition = new TraditionModel(tradNode);

            assertTrue(tradition.getId().equals(tradId));
            assertTrue(tradition.getName().equals("Tradition"));

            tx.success();
        } catch (Exception e) {
            fail();
        }

        /*
         * Change the owner of the tradition, and another of its properties
         */
        TraditionModel textInfo = new TraditionModel();
        textInfo.setName("RenamedTraditionName");
        textInfo.setLanguage("nital");
        textInfo.setDirection("RL");
        textInfo.setIs_public(false);
        textInfo.setOwner("42");

        ClientResponse ownerChangeResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId)
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, textInfo);
        assertEquals(Status.OK.getStatusCode(), ownerChangeResponse.getStatus());

        /*
         * Test if user with id 42 has now the tradition
         */
        try (Transaction tx = db.beginTx()) {
            Node tradNode = db.findNode(Nodes.TRADITION, "id", tradId);
            TraditionModel tradition = new TraditionModel(tradNode);

            assertEquals("42", tradition.getOwner());
            assertEquals(tradId, tradition.getId());
            assertEquals("RenamedTraditionName", tradition.getName());
            assertEquals("RL", tradition.getDirection());
            tx.success();

        }

        /*
         * The user with id 1 has no tradition
         */
        try (Transaction tx = db.beginTx()) {
            result = db.execute("match (n)<-[:OWNS_TRADITION]-(userId:USER {id:'1'}) return n");
            Iterator<Node> tradIterator = result.columnAs("n");
            assertTrue(!tradIterator.hasNext());

            tx.success();
        }
    }


    /**
     * Test if there is the correct error when trying to change a tradition with an invalid userid
     */
	@Test
    public void changeMetadataOfATraditionTestWithWrongUser() {
        /* Preconditon
         * The user with id 1 has tradition
         */
        Result result;
        try (Transaction tx = db.beginTx()) {
            result = db.execute("match (n)<-[:OWNS_TRADITION]-(userId:USER {id:'1'}) return n");
            Iterator<Node> tradIterator = result.columnAs("n");
            Node tradNode = tradIterator.next();
            TraditionModel tradition = new TraditionModel();
            tradition.setId(tradNode.getProperty("id").toString());
            tradition.setName(tradNode.getProperty("name").toString());

            assertTrue(tradition.getId().equals(tradId));
            assertTrue(tradition.getName().equals("Tradition"));

            tx.success();
        }

        /*
         * Change the owner of the tradition
         */
        TraditionModel textInfo = new TraditionModel();
        textInfo.setName("RenamedTraditionName");
        textInfo.setLanguage("nital");
        textInfo.setIs_public(false);
        textInfo.setOwner("1337");

        ClientResponse removalResponse = jerseyTest.resource()
                .path("/tradition/" + tradId)
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, textInfo);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), removalResponse.getStatus());
        assertEquals("A user with this id does not exist", Util.getValueFromJson(removalResponse, "error"));

        /* PostCondition
         * The user with id 1 has still tradition
         */
        TraditionModel tradition = new TraditionModel();
        try (Transaction tx = db.beginTx()) {
            result = db.execute("match (n)<-[:OWNS_TRADITION]-(userId:USER {id:'1'}) return n");
            Iterator<Node> tradIterator = result.columnAs("n");
            Node tradNode = tradIterator.next();

            tradition.setId(tradNode.getProperty("id").toString());
            tradition.setName(tradNode.getProperty("name").toString());

            tx.success();
        }

        assertTrue(tradition.getId().equals(tradId));
        assertTrue(tradition.getName().equals("Tradition"));

    }

    /**
     * Test if it is posibible to change the user of a Tradition with invalid traditionid
     */
    @Test
    public void changeMetadataOfATraditionTestWithInvalidTradid() {

        /*
         * Create a second user with id 42
         */
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (n:ROOT) return n");
            Iterator<Node> nodes = result.columnAs("n");
            Node rootNode = nodes.next();

            Node node = db.createNode(Nodes.USER);
            node.setProperty("id", "42");
            node.setProperty("role", "admin");

            rootNode.createRelationshipTo(node, ERelations.SYSTEMUSER);
            tx.success();
        }

        /*
         * The user with id 42 has no tradition
         */
        Result result;
        try (Transaction tx = db.beginTx()) {
            result = db.execute("match (n)<-[:OWNS_TRADITION]-(userId:USER {id:'42'}) return n");
            Iterator<Node> tradIterator = result.columnAs("n");
            assertTrue(!tradIterator.hasNext());

            tx.success();

        }

        /*
         * The user with id 1 has tradition
         */
        try (Transaction tx = db.beginTx()) {
            result = db.execute("match (n)<-[:OWNS_TRADITION]-(userId:USER {id:'1'}) return n");
            Iterator<Node> tradIterator = result.columnAs("n");
            Node tradNode = tradIterator.next();
            TraditionModel tradition = new TraditionModel();
            tradition.setId(tradNode.getProperty("id").toString());
            tradition.setName(tradNode.getProperty("name").toString());

            assertTrue(tradition.getId().equals(tradId));
            assertTrue(tradition.getName().equals("Tradition"));

            tx.success();
        }

        /*
         * Change the owner of the tradition
         */
        TraditionModel textInfo = new TraditionModel();
        textInfo.setName("RenamedTraditionName");
        textInfo.setLanguage("nital");
        textInfo.setIs_public(false);
        textInfo.setOwner("42");

        ClientResponse removalResponse = jerseyTest
                .resource()
                .path("/tradition/1337")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, textInfo);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), removalResponse.getStatus());
        assertEquals("There is no Tradition with this id", Util.getValueFromJson(removalResponse, "error"));

        /*
         * Post condition nothing has changed
         *
         */

        /*
         * Test if user with id 1 has still the old tradition
         */
        try (Transaction tx = db.beginTx()) {
            result = db.execute("match (n)<-[:OWNS_TRADITION]-(userId:USER {id:'1'}) return n");
            Iterator<Node> tradIterator = result.columnAs("n");
            Node tradNode = tradIterator.next();
            TraditionModel tradition = new TraditionModel();
            tradition.setId(tradNode.getProperty("id").toString());
            tradition.setName(tradNode.getProperty("name").toString());

            assertTrue(tradition.getId().equals(tradId));
            assertTrue(tradition.getName().equals("Tradition"));

            tx.success();
        }

        /*
         * The user with id 42 has still no tradition
         */
        try (Transaction tx = db.beginTx()) {
            result = db.execute("match (n)<-[:OWNS_TRADITION]-(userId:USER {id:'42'}) return n");
            Iterator<Node> tradIterator = result.columnAs("n");
            assertTrue(!tradIterator.hasNext());

            tx.success();
        }
    }

    /**
     * Remove a complete Tradition
     */
    @Test
    public void deleteTraditionByIdTest() {
        ClientResponse removalResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId)
                .type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), removalResponse.getStatus());


        Node startNode = DatabaseService.getStartNode(tradId, db);

        assertTrue(startNode == null);
    }

    /**
     * Test that all the nodes of a tradition have been removed
     */
    @Test
    public void deleteTraditionCompletelyTest() {
        // create a new user
        UserModel userModel = new UserModel();
        userModel.setId("user@example.org");
        userModel.setRole("user");
        ClientResponse jerseyResponse = jerseyTest.resource()
                .path("/user/user@example.org")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, userModel);
        assertEquals(Status.CREATED.getStatusCode(), jerseyResponse.getStatus());

        // count the total number of nodes
        AtomicInteger numNodes = new AtomicInteger(0);
        try (Transaction tx = db.beginTx()) {
            db.execute("match (n) return n").forEachRemaining(x -> numNodes.getAndIncrement());
            tx.success();
        }
        int originalNodeCount = numNodes.get();

        // upload the florilegium
        String testfile = "src/TestFiles/florilegium_graphml.xml";
        String florId = createTraditionFromFile("Florilegium", testfile, userModel.getId());

        // give it a stemma
        String newStemma = null;
        try {
            byte[] encStemma = Files.readAllBytes(Paths.get("src/TestFiles/florilegium.dot"));
            newStemma = new String(encStemma, Charset.forName("utf-8"));
        } catch (IOException e) {
            fail();
        }
        jerseyResponse = jerseyTest
                .resource()
                .path("/tradition/" + florId + "/stemma")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newStemma);
        assertEquals(ClientResponse.Status.CREATED.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());

        // re-root the stemma
        jerseyResponse = jerseyTest
                .resource()
                .path("/tradition/" + florId + "/stemma/Stemma/reorient/2")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newStemma);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());

        // give it some relationships - rank 37, rank 13, ranks 217/219
        try (Transaction tx = db.beginTx()) {
            int[] alignRanks = {77, 110};
            for (int r : alignRanks) {
                ResourceIterator<Node> atRank = db.findNodes(Nodes.READING, "rank", r);
                assertTrue(atRank.hasNext());
                ReadingModel rdg1 = new ReadingModel(atRank.next());
                ReadingModel rdg2 = new ReadingModel(atRank.next());
                RelationshipModel rel = new RelationshipModel();
                rel.setReading_a(rdg1.getText());
                rel.setReading_b(rdg2.getText());
                rel.setType("grammatical");
                rel.setScope("local");
                rel.setSource(rdg1.getId());
                rel.setTarget(rdg2.getId());
                jerseyResponse = jerseyTest.resource()
                        .path("/tradition/" + tradId + "/relation")
                        .type(MediaType.APPLICATION_JSON)
                        .post(ClientResponse.class, rel);
                assertEquals(ClientResponse.Status.CREATED.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());
            }

            // and a transposition, for kicks
            Node tx1 = db.findNode(Nodes.READING, "rank", 217);
            Node tx2 = db.findNode(Nodes.READING, "rank", 219);
            RelationshipModel txrel = new RelationshipModel();
            txrel.setType("transposition");
            txrel.setScope("local");
            txrel.setSource(String.valueOf(tx1.getId()));
            txrel.setTarget(String.valueOf(tx2.getId()));
            jerseyResponse = jerseyTest.resource()
                    .path("/tradition/" + tradId + "/relation")
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, txrel);
            assertEquals(ClientResponse.Status.CREATED.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());
            tx.success();
        }

        // now count the nodes
        numNodes.set(0);
        try (Transaction tx = db.beginTx()) {
            db.execute("match (n) return n").forEachRemaining(x -> numNodes.getAndIncrement());
            tx.success();
        }
        assertTrue(numNodes.get() > originalNodeCount + 200);

        // delete the florilegium
        jerseyResponse = jerseyTest
                .resource()
                .path("/tradition/" + florId)
                .type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());

        // nodes should be back to original number
        numNodes.set(0);
        try (Transaction tx = db.beginTx()) {
            db.execute("match (n) return n").forEachRemaining(x -> numNodes.getAndIncrement());
            tx.success();
        }
        assertEquals(originalNodeCount, numNodes.get());
    }

    /**
     * Test do delete a Tradition with an invalid id deletTraditionById
     */
    @Test
    public void deleteATraditionWithInvalidIdTest() {
        try (Transaction tx = db.beginTx()) {
            /*
             * Try to remove a tradition with invalid id
             */
            ClientResponse removalResponse = jerseyTest
                    .resource()
                    .path("/tradition/1337")
                    .delete(ClientResponse.class);
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), removalResponse.getStatus());

            /*
             * Test if user 1 still exists
             */
            Result result = db.execute("match (userId:USER {id:'1'}) return userId");
            Iterator<Node> nodes = result.columnAs("userId");
            assertTrue(nodes.hasNext());

            /*
             * Check if tradition {tradId} still exists
             */
            result = db.execute("match (t:TRADITION {id:'" + tradId + "'}) return t");
            nodes = result.columnAs("t");
            assertTrue(nodes.hasNext());
            tx.success();
        }
    }

    /*
     * Shut down the jersey server
     *
     * @throws Exception
     */
    @After
    public void tearDown() throws Exception {
        jerseyTest.tearDown();
        db.shutdown();
    }
}
