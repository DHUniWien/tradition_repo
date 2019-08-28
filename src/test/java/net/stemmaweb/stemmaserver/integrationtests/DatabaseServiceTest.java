package net.stemmaweb.stemmaserver.integrationtests;

import java.util.ArrayList;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;

import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;

import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.Response;

import static org.junit.Assert.*;

/**
 * 
 * @author PSE FS 2015 Team2
 *
 */
public class DatabaseServiceTest {

    private GraphDatabaseService db;
    private JerseyTest jerseyTest;
    private String traditionId;
    private String userId;

    @Before
    public void setUp() throws Exception {

        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        userId = "simon";
        Util.setupTestDB(db, userId);


        /*
         * load a tradition to the test DB
         */
        jerseyTest = JerseyTestServerFactory
                .newJerseyTestServer()
                .addResource(Root.class)
                .create();
        jerseyTest.setUp();

        Response jerseyResult = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", userId,
                "src/TestFiles/testTradition.xml", "stemmaweb");
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        /*
         * gets the generated id of the inserted tradition
         */
        traditionId = Util.getValueFromJson(jerseyResult, "tradId");
    }

    @Test
    public void getStartNodeTest() {
        try (Transaction tx = db.beginTx()) {
            assertEquals("#START#", DatabaseService
                    .getStartNode(traditionId, db)
                    .getProperty("text")
                    .toString());
            tx.success();
        } catch (NullPointerException e) {
           fail();
        }
    }

    @Test
    public void getTraditionNodeTest() {
        Node foundTradition = DatabaseService.getTraditionNode(traditionId, db);
        assertNotNull(foundTradition);
    }

    @Test
    public void getRelatedTest() {
        Node tradition = DatabaseService.getTraditionNode(traditionId, db);
        ArrayList<Node> witnesses = DatabaseService.getRelated(tradition, ERelations.HAS_WITNESS);
        assertEquals(3, witnesses.size());
    }

    @Test
    public void userExistsTest() {
        assertTrue(DatabaseService.userExists(userId, db));
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
