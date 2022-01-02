package net.stemmaweb.stemmaserver.integrationtests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.model.*;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.VariantGraphService;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;

import net.stemmaweb.stemmaserver.Util;

import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
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

        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(Root.class)
                .create();
        jerseyTest.setUp();

        /*
         * create a tradition inside the test DB
         */
        tradId = createTraditionFromFile("Tradition", "src/TestFiles/testTradition.xml", "1");
    }

    private String createTraditionFromFile(String tName, String fName, String userId) {

        Response jerseyResult = null;
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

        List<TraditionModel> traditions = jerseyTest.target("/traditions")
                .request()
                .get(new GenericType<>() {});
        for (TraditionModel returned : traditions) {
            assertTrue(expectedIds.contains(returned.getId()));
            assertEquals("Tradition", returned.getName());
        }
    }

    @Test
    public void getAllTraditionsWithParameterNotFoundTest() {
        Response resp = jerseyTest
                .target("/traditions/" + 2342)
                .request()
                .get();
        assertEquals(Response.status(Status.NOT_FOUND).build().getStatus(), resp.getStatus());
    }

    /* TODO: This test needs to be fixed - it expects the relationships to be returned in
       an order that is not guaranteed. */
    @Test(expected = org.junit.ComparisonFailure.class)
    public void getAllRelationshipsTest() {
        String jsonPayload = "{\"role\":\"user\",\"id\":1}";
        jerseyTest.target("/user/1")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(jsonPayload));

        RelationModel rel = new RelationModel();
        rel.setSource("27");
        rel.setTarget("16");
        rel.setId("36");
        rel.setIs_significant("no");
        rel.setAlters_meaning(0L);
        rel.setType("transposition");
        rel.setScope("local");

        List<RelationModel> relationships = jerseyTest.target("/tradition/" + tradId + "/relations")
                .request()
                .get(new GenericType<>() {});
        RelationModel relLoaded = relationships.get(2);

        assertEquals(rel.getSource(), relLoaded.getSource());
        assertEquals(rel.getTarget(), relLoaded.getTarget());
        assertEquals(rel.getId(), relLoaded.getId());
        assertEquals(rel.getIs_significant(), relLoaded.getIs_significant());
        assertEquals(rel.getAlters_meaning(), relLoaded.getAlters_meaning());
        assertEquals(rel.getType(), relLoaded.getType());
        assertEquals(rel.getScope(), relLoaded.getScope());
    }

    @Test
    public void getAllRelationshipsCorrectAmountTest() {

        List<RelationModel> relationships = jerseyTest.target("/tradition/" + tradId + "/relations")
                .request()
                .get(new GenericType<>() {});

        assertEquals(3, relationships.size());
    }

    @Test
    public void getAllWitnessesTest() {
        Set<String> expectedWitnesses = new HashSet<>(Arrays.asList("A", "B", "C"));
        List<WitnessModel> witnesses = jerseyTest.target("/tradition/" + tradId + "/witnesses")
                .request()
                .get(new GenericType<>() {});
        assertEquals(expectedWitnesses.size(), witnesses.size());
        for (WitnessModel w: witnesses) {
            assertTrue(expectedWitnesses.contains(w.getSigil()));
        }
    }

    @Test
    public void getWitnessesMultiSectionTest () {
        Set<String> expectedWitnesses = new HashSet<>(Arrays.asList("A", "B", "C"));
        // Add the same data as a second section
        Util.addSectionToTradition(jerseyTest, tradId, "src/TestFiles/testTradition.xml", "stemmaweb", "section 2");
        List<WitnessModel> witnesses = jerseyTest.target("/tradition/" + tradId + "/witnesses")
                .request()
                .get(new GenericType<>() {});
        assertEquals(expectedWitnesses.size(), witnesses.size());
        for (WitnessModel w: witnesses) {
            assertTrue(expectedWitnesses.contains(w.getSigil()));
        }
    }

    @Test
    public void getAllWitnessesTraditionNotFoundTest() {
        Response resp = jerseyTest.target("/tradition/10000/witnesses")
                .request()
                .get();

        assertEquals(Response.status(Status.NOT_FOUND).build().getStatus(), resp.getStatus());
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

            assertEquals(tradId, tradition.getId());
            assertEquals("Tradition", tradition.getName());

            tx.success();
        } catch (Exception e) {
            fail();
        }

        /*
         * Change the owner of the tradition, and another of its properties, and add a stemweb_jobid
         */
        TraditionModel textInfo = new TraditionModel();
        textInfo.setName("RenamedTraditionName");
        textInfo.setLanguage("nital");
        textInfo.setDirection("RL");
        textInfo.setIs_public(false);
        textInfo.setOwner("42");
        textInfo.setStemweb_jobid(3);

        Response ownerChangeResponse = jerseyTest
                .target("/tradition/" + tradId)
                .request()
                .put(Entity.json(textInfo));
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
            assertEquals(Integer.valueOf(3), tradition.getStemweb_jobid());
            tx.success();

        }

        /*
         * The user with id 1 has no tradition
         */
        try (Transaction tx = db.beginTx()) {
            result = db.execute("match (n)<-[:OWNS_TRADITION]-(userId:USER {id:'1'}) return n");
            Iterator<Node> tradIterator = result.columnAs("n");
            assertFalse(tradIterator.hasNext());

            tx.success();
        }

        /*
         * Now check that we can delete the Stemweb job ID
         */
        TraditionModel sjDel = new TraditionModel();
        sjDel.setStemweb_jobid(0);
        Response jobIdDelResponse = jerseyTest
                .target("/tradition/" + tradId)
                .request()
                .put(Entity.json(sjDel));
        assertEquals(Status.OK.getStatusCode(), jobIdDelResponse.getStatus());
        TraditionModel sjResult = jobIdDelResponse.readEntity(TraditionModel.class);
        assertEquals("RenamedTraditionName", sjResult.getName());
        assertEquals("RL", sjResult.getDirection());
        assertEquals(tradId, sjResult.getId());
        assertEquals("42", sjResult.getOwner());
        assertNull(sjResult.getStemweb_jobid());

    }


    /**
     * Test if there is the correct error when trying to change a tradition with an invalid userid
     */
	@Test
    public void changeMetadataOfATraditionTestWithWrongUser() {
        /* Preconditon
         * The user with id 1 has tradition
         */
        Response jerseyResult = jerseyTest.target("/user/1/traditions").request().get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        List<TraditionModel> tradList = jerseyResult.readEntity(new GenericType<>() {});
        assertEquals(1, tradList.size());
        assertEquals(tradId, tradList.get(0).getId());

        /*
         * Change the owner of the tradition
         */
        TraditionModel textInfo = new TraditionModel();
        textInfo.setName("RenamedTraditionName");
        textInfo.setLanguage("nital");
        textInfo.setIs_public(false);
        textInfo.setOwner("1337");

        Response removalResponse = jerseyTest.target("/tradition/" + tradId)
                .request()
                .put(Entity.json(textInfo));
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), removalResponse.getStatus());
        assertEquals("A user with this id does not exist", Util.getValueFromJson(removalResponse, "error"));

        /* PostCondition
         * The user with id 1 has still tradition
         */
        jerseyResult = jerseyTest.target("/user/1/traditions").request().get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        tradList = jerseyResult.readEntity(new GenericType<>() {});
        assertEquals(1, tradList.size());
        assertEquals(tradId, tradList.get(0).getId());
        assertEquals("Tradition", tradList.get(0).getName());

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
        Response jerseyResult = jerseyTest.target("/user/42/traditions").request().get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        assertEquals(0, jerseyResult.readEntity(new GenericType<List<TraditionModel>>() {}).size());

        /*
         * The user with id 1 has tradition
         */
        jerseyResult = jerseyTest.target("/user/1/traditions").request().get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        List<TraditionModel> tradList = jerseyResult.readEntity(new GenericType<>() {});
        assertEquals(1, tradList.size());
        assertEquals(tradId, tradList.get(0).getId());

        /*
         * Change the owner of the tradition
         */
        TraditionModel textInfo = new TraditionModel();
        textInfo.setName("RenamedTraditionName");
        textInfo.setLanguage("nital");
        textInfo.setIs_public(false);
        textInfo.setOwner("42");

        Response removalResponse = jerseyTest
                .target("/tradition/1337")
                .request()
                .put(Entity.json(textInfo));
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), removalResponse.getStatus());
        assertEquals("There is no Tradition with this id", Util.getValueFromJson(removalResponse, "error"));

        /*
         * Post condition nothing has changed
         *
         */

        /*
         * Test if user with id 1 has still the old tradition
         */
        jerseyResult = jerseyTest.target("/user/1/traditions").request().get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        tradList = jerseyResult.readEntity(new GenericType<>() {});
        assertEquals(1, tradList.size());
        assertEquals(tradId, tradList.get(0).getId());
        assertEquals("Tradition", tradList.get(0).getName());

        /*
         * The user with id 42 has still no tradition
         */
        jerseyResult = jerseyTest.target("/user/42/traditions").request().get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        assertEquals(0, jerseyResult.readEntity(new GenericType<List<TraditionModel>>() {}).size());
    }

    /**
     * Remove a complete Tradition
     */
    @Test
    public void deleteTraditionByIdTest() {
        Response removalResponse = jerseyTest
                .target("/tradition/" + tradId)
                .request()
                .delete();
        assertEquals(Response.Status.OK.getStatusCode(), removalResponse.getStatus());


        Node startNode = VariantGraphService.getStartNode(tradId, db);

        assertNull(startNode);
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
        Response jerseyResponse = jerseyTest.target("/user/user@example.org")
                .request()
                .put(Entity.json(userModel));
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
        StemmaModel newStemma = new StemmaModel();
        try {
            byte[] encStemma = Files.readAllBytes(Paths.get("src/TestFiles/florilegium.dot"));
            newStemma.setDot(new String(encStemma, StandardCharsets.UTF_8));
        } catch (IOException e) {
            fail();
        }
        jerseyResponse = jerseyTest
                .target("/tradition/" + florId + "/stemma")
                .request()
                .post(Entity.json(newStemma));
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());

        // re-root the stemma
        jerseyResponse = jerseyTest
                .target("/tradition/" + florId + "/stemma/Stemma/reorient/2")
                .request()
                .post(null);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());

        // give it some relationships - rank 37, rank 13, ranks 217/219
        try (Transaction tx = db.beginTx()) {
            int[] alignRanks = {77, 110};
            for (int r : alignRanks) {
                ResourceIterator<Node> atRank = db.findNodes(Nodes.READING, "rank", r);
                assertTrue(atRank.hasNext());
                ReadingModel rdg1 = new ReadingModel(atRank.next());
                ReadingModel rdg2 = new ReadingModel(atRank.next());
                RelationModel rel = new RelationModel();
                rel.setType("grammatical");
                rel.setScope("local");
                rel.setSource(rdg1.getId());
                rel.setTarget(rdg2.getId());
                jerseyResponse = jerseyTest.target("/tradition/" + florId + "/relation")
                        .request()
                        .post(Entity.json(rel));
                assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());
            }

            // and a transposition, for kicks
            Node tx1 = db.findNode(Nodes.READING, "rank", 217);
            Node tx2 = db.findNode(Nodes.READING, "rank", 219);
            RelationModel txrel = new RelationModel();
            txrel.setType("transposition");
            txrel.setScope("local");
            txrel.setSource(String.valueOf(tx1.getId()));
            txrel.setTarget(String.valueOf(tx2.getId()));
            jerseyResponse = jerseyTest.target("/tradition/" + florId + "/relation")
                    .request()
                    .post(Entity.json(txrel));
            assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());
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
                .target("/tradition/" + florId)
                .request()
                .delete();
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
            Response removalResponse = jerseyTest
                    .target("/tradition/1337")
                    .request()
                    .delete();
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
