package net.stemmaweb.stemmaserver.integrationtests;

import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.VariantGraphService;
import net.stemmaweb.stemmaserver.Util;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.Response;

import java.util.ArrayList;

import static org.junit.Assert.*;

public class VariantGraphServiceTest {
    private GraphDatabaseService db;
    private String traditionId;

    @Before
    public void setUp() throws Exception {

        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        String userId = "simon";
        Util.setupTestDB(db, userId);

        /*
         * load a tradition to the test DB, without Jersey
         */
        Response result = Util.createTraditionDirectly("Tradition", "LR", userId,
                "src/TestFiles/testTradition.xml", "stemmaweb");
        assertEquals(Response.Status.CREATED.getStatusCode(), result.getStatus());
        /*
         * gets the generated id of the inserted tradition
         */
        traditionId = Util.getValueFromJson(result, "tradId");
    }

    // public void sectionInTraditionTest()

    @Test
    public void getStartNodeTest() {
        try (Transaction tx = db.beginTx()) {
            Node startNode = VariantGraphService.getStartNode(traditionId, db);
            assertNotNull(startNode);
            assertEquals("#START#", startNode.getProperty("text"));
            assertEquals(true, startNode.getProperty("is_start"));
            tx.success();
        }
    }

    @Test
    public void getEndNodeTest() {
        try (Transaction tx = db.beginTx()) {
            Node endNode = VariantGraphService.getEndNode(traditionId, db);
            assertNotNull(endNode);
            assertEquals("#END#", endNode.getProperty("text"));
            assertEquals(true, endNode.getProperty("is_end"));
            tx.success();
        }
    }

    @Test
    public void getSectionNodesTest() {
        ArrayList<Node> sectionNodes = VariantGraphService.getSectionNodes(traditionId, db);
        assertNotNull(sectionNodes);
        assertEquals(1, sectionNodes.size());
        try (Transaction tx = db.beginTx()) {
            assertTrue(sectionNodes.get(0).hasLabel(Label.label("SECTION")));
            tx.success();
        }
    }

    @Test
    public void getTraditionNodeTest() {
        Node foundTradition = VariantGraphService.getTraditionNode(traditionId, db);
        assertNotNull(foundTradition);
        // Now by section node
        ArrayList<Node> sectionNodes = VariantGraphService.getSectionNodes(traditionId, db);
        assertNotNull(sectionNodes);
        assertEquals(1, sectionNodes.size());
        assertEquals(foundTradition, VariantGraphService.getTraditionNode(sectionNodes.get(0)));
    }

    // normalizeGraphTest()

    // removeNormalizationTest()

    // returnEntireTraditionTest()

    // returnTraditionSectionTest()

    // returnTraditionRelationsTest()

    /*
     * Shut down the database
     */
    @After
    public void tearDown() {
        db.shutdown();
    }

}
