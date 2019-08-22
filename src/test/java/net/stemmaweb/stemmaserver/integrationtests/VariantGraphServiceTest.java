package net.stemmaweb.stemmaserver.integrationtests;

import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.VariantGraphService;
import net.stemmaweb.stemmaserver.Util;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.Response;

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

    @Test
    public void getStartNodeTest() {
        try (Transaction tx = db.beginTx()) {
            assertEquals("#START#", VariantGraphService
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
        Node foundTradition = VariantGraphService.getTraditionNode(traditionId, db);
        assertNotNull(foundTradition);
    }

    // TODO lots more things to test meanwhile!

    /*
     * Shut down the database
     */
    @After
    public void tearDown() {
        db.shutdown();
    }

}
