package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Iterator;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.GraphMLToNeo4JParser;

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

    @Before
    public void setUp() throws Exception {

        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();

        GraphMLToNeo4JParser importResource = new GraphMLToNeo4JParser();

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
            String tradId = (String) nodes.next().getProperty("id");

            tx.success();
        }
    }

    @Test
    public void getStartNodeTest(){
        try(Transaction tx = db.beginTx())
        {
            assertEquals("#START#", DatabaseService.getStartNode("1001",db)
                    .getProperty("text")
                    .toString());
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
    }

}
