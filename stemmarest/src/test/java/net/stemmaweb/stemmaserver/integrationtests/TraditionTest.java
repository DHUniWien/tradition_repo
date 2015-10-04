package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Tradition;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import org.neo4j.test.TestGraphDatabaseFactory;

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
    private GraphMLToNeo4JParser importResource;

    @Before
    public void setUp() throws Exception {

        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();


        importResource = new GraphMLToNeo4JParser();
        Tradition tradition = new Tradition();

		File testfile = new File("src/TestXMLFiles/testTradition.xml");

        /*
         * Populate the test database with the root node and a user with id 1
         */
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (n:ROOT) return n");
            Iterator<Node> nodes = result.columnAs("n");
            Node rootNode = null;
            if (!nodes.hasNext()) {
                rootNode = db.createNode(Nodes.ROOT);
                rootNode.setProperty("name", "Root node");
                rootNode.setProperty("LAST_INSERTED_TRADITION_ID", "1000");
            }

            Node node = db.createNode(Nodes.USER);
            node.setProperty("id", "1");
            node.setProperty("isAdmin", "1");

            rootNode.createRelationshipTo(node, ERelations.SYSTEMUSER);
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
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer().addResource(tradition).create();
        jerseyTest.setUp();
    }

    @Test
    public void getAllTraditionsTest() {
        // import a second tradition into the db
		File testfile = new File("src/TestXMLFiles/testTradition.xml");

        try {
            importResource.parseGraphML(testfile.getPath(), "1", "Tradition");
        } catch (FileNotFoundException f) {
            // this error should not occur
            assertTrue(false);
        }

        TraditionModel trad1 = new TraditionModel();
        trad1.setId("1001");
        trad1.setName("Tradition");
        TraditionModel trad2 = new TraditionModel();
        trad2.setId("1002");
        trad2.setName("Tradition");

        List<TraditionModel> traditions = jerseyTest.resource().path("/tradition/getalltraditions")
                .get(new GenericType<List<TraditionModel>>() {});
        TraditionModel firstTradition = traditions.get(0);
        assertEquals(trad1.getId(), firstTradition.getId());
        assertEquals(trad1.getName(), firstTradition.getName());

        TraditionModel lastTradition = traditions.get(1);
        assertEquals(trad2.getId(), lastTradition.getId());
        assertEquals(trad2.getName(), lastTradition.getName());
    }

    @Test
    public void getAllTraditionsWithParameterNotFoundTest() {
        ClientResponse resp = jerseyTest
                .resource()
                .path("/tradition/getalltraditions/" + 2342)
                .get(ClientResponse.class);
        assertEquals(Response.status(Status.NOT_FOUND).build().getStatus(), resp.getStatus());
    }

    /* TODO: This test needs to be fixed - it expects the relationships to be returned in
       an order that is not guaranteed. */
    @Test(expected = org.junit.ComparisonFailure.class)
    public void getAllRelationshipsTest() {
        String jsonPayload = "{\"isAdmin\":0,\"id\":1}";
        jerseyTest.resource()
                .path("/user/createuser")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, jsonPayload);

        RelationshipModel rel = new RelationshipModel();
        rel.setSource("27");
        rel.setTarget("16");
        rel.setId("36");
        rel.setReading_a("april");
        rel.setIs_significant("no");
        rel.setReading_b("april");
        rel.setAlters_meaning("0");
        rel.setType("transposition");
        rel.setScope("local");

        List<RelationshipModel> relationships = jerseyTest.resource()
                .path("/tradition/getallrelationships/fromtradition/" + tradId)
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
                .path("/tradition/getallrelationships/fromtradition/" + tradId)
                .get(new GenericType<List<RelationshipModel>>() {});

        assertEquals(3, relationships.size());
    }

    @Test
    public void getAllWitnessesTest() {
        Set<String> expectedWitnesses = new HashSet<>(Arrays.asList("A", "B", "C"));
        List<WitnessModel> witnesses = jerseyTest.resource()
                .path("/tradition/getallwitnesses/fromtradition/" + tradId)
                .get(new GenericType<List<WitnessModel>>() {});
        assertEquals(expectedWitnesses.size(), witnesses.size());
        for (WitnessModel w: witnesses) {
            assertTrue(expectedWitnesses.contains(w.getSigil()));
        }
    }

    @Test
    public void getAllWitnessesTraditionNotFoundTest() {
        ClientResponse resp = jerseyTest.resource()
                .path("/tradition/getallwitnesses/fromtradition/" + 10000)
                .get(ClientResponse.class);

        assertEquals(Response.status(Status.NOT_FOUND).build().getStatus(), resp.getStatus());
    }

    @Test
    public void getDotOfNonExistentTraditionTest() {
        ClientResponse resp = jerseyTest
                .resource()
                .path("/tradition/getdot/fromtradition/" + 10000)
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);

        assertEquals(Response.status(Status.NOT_FOUND).build().getStatus(), resp.getStatus());
    }

    @Test
    public void getDotTest() {
        String str = jerseyTest
                .resource()
                .path("/tradition/getdot/fromtradition/" + tradId)
                .type(MediaType.APPLICATION_JSON)
                .get(String.class);

        String[] exp = new String[60];
        exp[1] = "digraph { n4 [label=\"#START#\"];n4->n5 [label=\"A,B,C\", id=\"e0\"];";
        exp[2] = "n5 [label=\"when\"];";
        exp[3] = "n5->n16 [label=\"A\", id=";
        exp[4] = "n5->n24 [label=\"B,C\", id=";
        exp[5] = "n16 [label=\"april\"];";
        exp[6] = "n16->n22 [label=\"A\", id=";
        exp[7] = "n24 [label=\"showers\"];";
        exp[8] = "n24->n25 [label=\"A,B,C\", id=";
        exp[9] = "n22 [label=\"with\"];";
        exp[10] = "n22->n23 [label=\"A\", id=";
        exp[11] = "n25 [label=\"sweet\"];";
        exp[12] = "n25->n26 [label=\"A,B,C\", id=";
        exp[13] = "n23 [label=\"his\"];";
        exp[14] = "n23->n24 [label=\"A\", id=";
        exp[15] = "n26 [label=\"with\"];";
        exp[16] = "n26->n27 [label=\"B,C\", id=";
        exp[17] = "n26->n7 [label=\"A\", id=";
        exp[18] = "n27 [label=\"april\"];";
        exp[19] = "n27->n7 [label=\"B,C\", id=";
        exp[20] = "n7 [label=\"fruit\"];";
        exp[21] = "n7->n9 [label=\"C\", id=";
        exp[22] = "n7->n8 [label=\"A,B\", id=";
        exp[23] = "n9 [label=\"teh\"];";
        exp[24] = "n9->n11 [label=\"C\", id=";
        exp[25] = "n8 [label=\"the\"];";
        exp[26] = "n8->n10 [label=\"B\", id=";
        exp[27] = "n8->n11 [label=\"A\", id=";
        exp[28] = "n11 [label=\"drought\"];";
        exp[29] = "n11->n12 [label=\"A,C\", id=";
        exp[30] = "n10 [label=\"march\"];";
        exp[31] = "n10->n12 [label=\"B\", id=";
        exp[32] = "n12 [label=\"of\"];";
        exp[33] = "n12->n14 [label=\"B\", id=";
        exp[34] = "n12->n13 [label=\"A,C\", id=";
        exp[35] = "n14 [label=\"drought\"];";
        exp[36] = "n14->n15 [label=\"B\", id=";
        exp[37] = "n13 [label=\"march\"];";
        exp[38] = "n13->n15 [label=\"A,C\", id=";
        exp[39] = "n15 [label=\"has\"];";
        exp[40] = "n15->n17 [label=\"A,B,C\", id=";
        exp[41] = "n17 [label=\"pierced\"];";
        exp[42] = "n17->n18 [label=\"A\", id=";
        exp[43] = "n17->n19 [label=\"B\", id=";
        exp[44] = "n17->n20 [label=\"C\", id=";
        exp[45] = "n18 [label=\"unto\"];";
        exp[46] = "n18->n21 [label=\"A\", id=";
        exp[47] = "n19 [label=\"to\"];";
        exp[48] = "n19->n21 [label=\"B\", id=";
        exp[49] = "n20 [label=\"teh\"];";
        exp[50] = "n20->n28 [label=\"C\", id=";
        exp[51] = "n21 [label=\"the\"];";
        exp[52] = "n21->n6 [label=\"A,B\", id=";
        exp[53] = "n28 [label=\"rood\"];";
        exp[54] = "n28->n3 [label=\"C\", id=";
        exp[55] = "n6 [label=\"root\"];";
        exp[56] = "n6->n3 [label=\"A,B\", id=";
        exp[57] = "n3 [label=\"#END#\"];";
        exp[58] = "subgraph { edge [dir=none]n16->n27 [style=dotted, label=\"transposition\", id=";
        exp[59] = "n11->n14 [style=dotted, label=\"transposition\", id=";
        exp[0] = "n10->n13 [style=dotted, label=\"transposition\", id=";

        for (String anExp : exp) {
            assertTrue(str.contains(anExp));
        }
    }

    /**
     * Test if it is possible to change the user of a Tradition
     */
    @Test
    public void changeMetadataOfATraditionTest() {

        /*
         * Create a second user with id 42
         */
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (n:ROOT) return n");
            Iterator<Node> nodes = result.columnAs("n");
            Node rootNode = nodes.next();

            Node node = db.createNode(Nodes.USER);
            node.setProperty("id", "42");
            node.setProperty("isAdmin", "1");

            rootNode.createRelationshipTo(node, ERelations.OWNS_TRADITION);
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
        textInfo.setIsPublic("0");
        textInfo.setOwnerId("42");

        ClientResponse ownerChangeResponse = jerseyTest.resource().path("/tradition/changemetadata/fromtradition/" + tradId).type(MediaType.APPLICATION_JSON).post(ClientResponse.class, textInfo);
        assertEquals(Response.Status.OK.getStatusCode(), ownerChangeResponse.getStatus());

        /*
         * Test if user with id 42 has now the tradition
         */
        try (Transaction tx = db.beginTx()) {
            result = db.execute("match (n)<-[:OWNS_TRADITION]-(userId:USER {id:'42'}) return n");
            Iterator<Node> tradIterator = result.columnAs("n");
            Node tradNode = tradIterator.next();
            TraditionModel tradition = new TraditionModel();
            tradition.setId(tradNode.getProperty("id").toString());
            tradition.setName(tradNode.getProperty("name").toString());

            assertTrue(tradition.getId().equals(tradId));
            assertTrue(tradition.getName().equals("RenamedTraditionName"));

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
        textInfo.setIsPublic("0");
        textInfo.setOwnerId("1337");

        ClientResponse removalResponse = jerseyTest.resource().path("/tradition/changemetadata/fromtradition/" + tradId).type(MediaType.APPLICATION_JSON).post(ClientResponse.class, textInfo);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), removalResponse.getStatus());
        assertEquals(removalResponse.getEntity(String.class), "Error: A user with this id does not exist");

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
            node.setProperty("isAdmin", "1");

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
        textInfo.setIsPublic("0");
        textInfo.setOwnerId("42");

        ClientResponse removalResponse = jerseyTest.resource().path("/tradition/changemetadata/fromtradition/1337").type(MediaType.APPLICATION_JSON).post(ClientResponse.class, textInfo);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), removalResponse.getStatus());
        assertEquals(removalResponse.getEntity(String.class), "Tradition not found");

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
        ClientResponse removalResponse = jerseyTest.resource().path("/tradition/deletetradition/withid/" + tradId).type(MediaType.APPLICATION_JSON).delete(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), removalResponse.getStatus());


        Node startNode;
        try (Transaction tx = db.beginTx()) {
            startNode = DatabaseService.getStartNode(tradId, db);
        }

        assertTrue(startNode == null);
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
            ClientResponse removalResponse = jerseyTest.resource().path("/tradition/deletetradition/withid/1337").delete(ClientResponse.class);
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
