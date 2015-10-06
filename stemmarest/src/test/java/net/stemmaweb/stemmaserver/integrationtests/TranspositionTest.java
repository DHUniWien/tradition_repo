package net.stemmaweb.stemmaserver.integrationtests;


import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.test.framework.JerseyTest;
import net.stemmaweb.model.GraphModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Relation;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.*;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * 
 * Contains all tests for the api calls related to relations between readings.
 * 
 * @author PSE FS 2015 Team2
 *
 */
public class TranspositionTest {
    private String tradId;
    private Long rootId;
    private Long roodId;
    private Long theId;
    private Long tehId;

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
        Relation relation = new Relation();

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
         * gets the generated ids that we need for our tests
         */
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (u:USER)--(t:TRADITION), " +
                    "(root:READING {text:'root'}), (rood:READING {text:'rood'}), " +
                    "(:READING {text:'pierced'})-->(teh:READING {text:'teh'}), " +
                    "(:READING {text:'to'})-->(the:READING {text:'the'}) " +
                    "return t, root, rood, the, teh");
            assertTrue(result.hasNext());
            Map<String, Object> row = result.next();
            tradId = (String) ((Node) row.get("t")).getProperty("id");
            ReadingModel rootModel = new ReadingModel((Node) row.get("root"));
            rootId = Long.valueOf(rootModel.getId());
            ReadingModel roodModel = new ReadingModel((Node) row.get("rood"));
            roodId = Long.valueOf(roodModel.getId());
            ReadingModel tehModel = new ReadingModel((Node) row.get("teh"));
            tehId = Long.valueOf(tehModel.getId());
            ReadingModel theModel = new ReadingModel((Node) row.get("the"));
            theId = Long.valueOf(theModel.getId());

            tx.success();
        }

        /*
         * Create a JersyTestServer serving the Resource under test
         */
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer().addResource(relation).create();
        jerseyTest.setUp();
    }

    /**
     * Test that a transposition cannot be made if the readings are alignable
     */
    @Test
    public void disallowedTranspositionTest() {

        // First make sure that the pivot relationship does not exist
        RelationshipModel shouldnotexist = new RelationshipModel();
        shouldnotexist.setSource(tehId.toString());
        shouldnotexist.setTarget(rootId.toString());
        shouldnotexist.setScope("local");

        ClientResponse checkme = jerseyTest.resource().path("/relation/deleterelationship/fromtradition/" + tradId)
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, shouldnotexist);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), checkme.getStatus());

        // Now set up the relationship to be created
        RelationshipModel relationship = new RelationshipModel();
        relationship.setSource(tehId.toString());
        relationship.setTarget(rootId.toString());
        relationship.setType("transposition");
        relationship.setAlters_meaning("0");
        relationship.setIs_significant("true");
        relationship.setReading_a("teh");
        relationship.setReading_b("root");

        ClientResponse actualResponse = jerseyTest
                .resource()
                .path("/relation/createrelationship")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, relationship);
        assertEquals(Response.Status.CONFLICT.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Test if an 404 error occurs when an invalid target node was tested
     */
    @Test
    public void allowedTranspositionTest()

    {
        // First create the pivot alignment
        RelationshipModel relationship = new RelationshipModel();
        String relationshipId;

        relationship.setSource(theId.toString());
        relationship.setTarget(roodId.toString());
        relationship.setType("uncertain");
        relationship.setAlters_meaning("0");
        relationship.setIs_significant("true");
        relationship.setReading_a("the");
        relationship.setReading_b("rood");

        ClientResponse actualResponse = jerseyTest
                .resource()
                .path("/relation/createrelationship")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, relationship);
        assertEquals(Response.Status.CREATED.getStatusCode(), actualResponse.getStatus());

        // Make sure it is there
        try (Transaction tx = db.beginTx()) {
            GraphModel readingsAndRelationships = actualResponse.getEntity(GraphModel.class);
            relationshipId = readingsAndRelationships.getRelationships().get(0).getId();
            Relationship loadedRelationship = db.getRelationshipById(Long.parseLong(relationshipId));

            assertEquals(theId, (Long) loadedRelationship.getStartNode().getId());
            assertEquals(roodId, (Long) loadedRelationship.getEndNode().getId());
            assertEquals("uncertain", loadedRelationship.getProperty("type"));
            assertEquals("0", loadedRelationship.getProperty("alters_meaning"));
            assertEquals("true", loadedRelationship.getProperty("is_significant"));
            assertEquals("the", loadedRelationship.getProperty("reading_a"));
            assertEquals("rood", loadedRelationship.getProperty("reading_b"));
        }

        // Now create the transposition, which should work this time
        relationship = new RelationshipModel();
        relationship.setSource(tehId.toString());
        relationship.setTarget(rootId.toString());
        relationship.setType("transposition");
        relationship.setAlters_meaning("0");
        relationship.setIs_significant("true");
        relationship.setReading_a("teh");
        relationship.setReading_b("root");

        actualResponse = jerseyTest
                .resource()
                .path("/relation/createrelationship")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, relationship);
        assertEquals(Response.Status.CREATED.getStatusCode(), actualResponse.getStatus());

        // and make sure it is there.
        try (Transaction tx = db.beginTx()) {
            GraphModel readingsAndRelationships = actualResponse.getEntity(GraphModel.class);
            relationshipId = readingsAndRelationships.getRelationships().get(0).getId();
            Relationship loadedRelationship = db.getRelationshipById(Long.parseLong(relationshipId));

            assertEquals(tehId, (Long) loadedRelationship.getStartNode().getId());
            assertEquals(rootId, (Long) loadedRelationship.getEndNode().getId());
            assertEquals("transposition", loadedRelationship.getProperty("type"));
            assertEquals("0", loadedRelationship.getProperty("alters_meaning"));
            assertEquals("true", loadedRelationship.getProperty("is_significant"));
            assertEquals("teh", loadedRelationship.getProperty("reading_a"));
            assertEquals("root", loadedRelationship.getProperty("reading_b"));
        }
    }

    /**
     * Shut down the jersey server
     * @throws Exception
     */
    @After
    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
    }
}
