package net.stemmaweb.stemmaserver.integrationtests;


import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import net.stemmaweb.model.GraphModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.*;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
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


    @Before
    public void setUp() throws Exception {
        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
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
        ClientResponse jerseyResult = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/testTradition.xml", "graphml");
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());

        /*
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

        ClientResponse checkme = jerseyTest.resource().path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class, shouldnotexist);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), checkme.getStatus());

        // Now set up the relationship to be created
        RelationshipModel relationship = new RelationshipModel();
        relationship.setSource(tehId.toString());
        relationship.setTarget(rootId.toString());
        relationship.setType("transposition");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");
        relationship.setReading_a("teh");
        relationship.setReading_b("root");

        ClientResponse actualResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, relationship);
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
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");
        relationship.setReading_a("the");
        relationship.setReading_b("rood");

        ClientResponse actualResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, relationship);
        assertEquals(Response.Status.CREATED.getStatusCode(), actualResponse.getStatus());

        // Make sure it is there
        try (Transaction tx = db.beginTx()) {
            ArrayList<GraphModel> readingsAndRelationships = actualResponse.getEntity(new GenericType<ArrayList<GraphModel>>(){});
            relationshipId = readingsAndRelationships.get(0).getRelationships().get(0).getId();
            Relationship loadedRelationship = db.getRelationshipById(Long.parseLong(relationshipId));

            assertEquals(theId, (Long) loadedRelationship.getStartNode().getId());
            assertEquals(roodId, (Long) loadedRelationship.getEndNode().getId());
            assertEquals("uncertain", loadedRelationship.getProperty("type"));
            assertEquals(0L, loadedRelationship.getProperty("alters_meaning"));
            assertEquals("yes", loadedRelationship.getProperty("is_significant"));
            assertEquals("the", loadedRelationship.getProperty("reading_a"));
            assertEquals("rood", loadedRelationship.getProperty("reading_b"));
            tx.success();
        }

        // Now create the transposition, which should work this time
        relationship = new RelationshipModel();
        relationship.setSource(tehId.toString());
        relationship.setTarget(rootId.toString());
        relationship.setType("transposition");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");
        relationship.setReading_a("teh");
        relationship.setReading_b("root");

        actualResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, relationship);
        assertEquals(Response.Status.CREATED.getStatusCode(), actualResponse.getStatus());

        // and make sure it is there.
        try (Transaction tx = db.beginTx()) {
            ArrayList<GraphModel> readingsAndRelationships = actualResponse.getEntity(new GenericType<ArrayList<GraphModel>>(){});
            relationshipId = readingsAndRelationships.get(0).getRelationships().get(0).getId();
            Relationship loadedRelationship = db.getRelationshipById(Long.parseLong(relationshipId));

            assertEquals(tehId, (Long) loadedRelationship.getStartNode().getId());
            assertEquals(rootId, (Long) loadedRelationship.getEndNode().getId());
            assertEquals("transposition", loadedRelationship.getProperty("type"));
            assertEquals(0L, loadedRelationship.getProperty("alters_meaning"));
            assertEquals("yes", loadedRelationship.getProperty("is_significant"));
            assertEquals("teh", loadedRelationship.getProperty("reading_a"));
            assertEquals("root", loadedRelationship.getProperty("reading_b"));
            tx.success();
        }
    }

    /*
     * Shut down the jersey server
     * @throws Exception
     */
    @After
    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
    }
}
