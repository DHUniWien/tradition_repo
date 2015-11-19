package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.multipart.FormDataBodyPart;
import com.sun.jersey.multipart.FormDataMultiPart;
import com.sun.jersey.test.framework.JerseyTest;
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
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

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
        Root webResource = new Root();

        /**
         * Populate the test database with the root node and a user with id 1
         */
        userId = "simon";
        DatabaseService.createRootNode(db);
        try (Transaction tx = db.beginTx()) {
            Node rootNode = db.findNode(Nodes.ROOT, "name", "Root node");
            Node node = db.createNode(Nodes.USER);
            node.setProperty("id", userId);
            node.setProperty("isAdmin", "1");

            rootNode.createRelationshipTo(node, ERelations.SYSTEMUSER); //ERelations.SEQUENCE);
            tx.success();
        }

        /**
         * load a tradition to the test DB
         */
        jerseyTest = JerseyTestServerFactory
                .newJerseyTestServer()
                .addResource(webResource)
                .create();
        jerseyTest.setUp();

        ClientResponse jerseyResult = null;
        try {
            FormDataMultiPart form = new FormDataMultiPart();
            form.field("filetype", "graphml")
                    .field("name", "Tradition")
                    .field("direction", "LR")
                    .field("userId", userId);
            FormDataBodyPart fdp = new FormDataBodyPart("file",
                    new FileInputStream("src/TestFiles/testTradition.xml"),
                    MediaType.APPLICATION_OCTET_STREAM_TYPE);
            form.bodyPart(fdp);
            jerseyResult = jerseyTest.resource()
                    .path("/tradition")
                    .type(MediaType.MULTIPART_FORM_DATA_TYPE)
                    .put(ClientResponse.class, form);
        } catch (FileNotFoundException e) {
            assertTrue(false);
        }
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        /**
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
            assertTrue(false);
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
        jerseyTest.tearDown();
        db.shutdown();
    }
}
