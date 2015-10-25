package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Iterator;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.parser.GraphMLParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

/**
 * 
 * @author PSE FS 2015 Team2
 *
 */
public class DatabaseServiceTest {

    private GraphDatabaseService db;
    private String traditionId;
    private String userId;

    @Before
    public void setUp() throws Exception {

        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();

        GraphMLParser importResource = new GraphMLParser();

		File testfile = new File("src/TestFiles/testTradition.xml");

        /*
         * Populate the test database with the root node and a user with id 1
         */
        userId = "simon";
        DatabaseService.createRootNode(db);
        try (Transaction tx = db.beginTx()) {
            Node rootNode = db.findNode(Nodes.ROOT, "name", "Root node");
            Node node = db.createNode(Nodes.USER);
            node.setProperty("id", userId);
            node.setProperty("role", "admin");

            rootNode.createRelationshipTo(node, ERelations.SEQUENCE);
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
            traditionId = (String) nodes.next().getProperty("id");

            tx.success();
        }
    }

    @Test
    public void getStartNodeTest() {
        try (Transaction tx = db.beginTx()) {
            assertEquals("#START#", DatabaseService.getStartNode(traditionId, db)
                    .getProperty("text")
                    .toString());
            tx.success();
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

    /**
     * Shut down the jersey server
     *
     * @throws Exception
     */
    @After
    public void tearDown() throws Exception {
        db.shutdown();
    }

}
