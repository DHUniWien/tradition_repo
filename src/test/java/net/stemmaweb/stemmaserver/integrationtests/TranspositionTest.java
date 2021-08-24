package net.stemmaweb.stemmaserver.integrationtests;



import net.stemmaweb.model.GraphModel;
import net.stemmaweb.model.RelationModel;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;

import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;

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
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(Root.class)
                .create();
        jerseyTest.setUp();

        /*
         * create a tradition inside the test DB
         */
        Response jerseyResult = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/testTradition.xml", "stemmaweb");
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        tradId = Util.getValueFromJson(jerseyResult, "tradId");
        /*
         * gets the generated ids that we need for our tests
         */
        HashMap<String, String> readingLookup = Util.makeReadingLookup(jerseyTest, tradId);
        rootId = Long.valueOf(readingLookup.get("root/18"));
        roodId = Long.valueOf(readingLookup.get("rood/17"));
        tehId = Long.valueOf(readingLookup.get("teh/16"));
        theId = Long.valueOf(readingLookup.get("the/17"));
    }

    /**
     * Test that a transposition cannot be made if the readings are alignable
     */
    @Test
    public void disallowedTranspositionTest() {

        // First make sure that the pivot relationship does not exist
        /*
         * RelationModel shouldnotexist = new RelationModel();
         * shouldnotexist.setSource(tehId.toString());
         * shouldnotexist.setTarget(rootId.toString());
         * shouldnotexist.setScope("local");
         * 
         * jerseyTest.client().property(ClientProperties.
         * SUPPRESS_HTTP_COMPLIANCE_VALIDATION, true);
         * 
         * Response checkme = jerseyTest.target("/tradition/" + tradId + "/relation")
         * .request() .method("DELETE", Entity.json(shouldnotexist));
         * 
         * 
         * assertEquals(Response.Status.NOT_FOUND.getStatusCode(), checkme.getStatus());
         */

        // Now set up the relationship to be created
        RelationModel relationship = new RelationModel();
        relationship.setSource(tehId.toString());
        relationship.setTarget(rootId.toString());
        relationship.setType("transposition");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");

        Response actualResponse = jerseyTest
                .target("/tradition/" + tradId + "/relation")
                .request()
                .post(Entity.json(relationship));
        assertEquals(Response.Status.CONFLICT.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Test if an 404 error occurs when an invalid target node was tested
     */
    @Test
    public void allowedTranspositionTest()

    {
        // First create the pivot alignment
        RelationModel relationship = new RelationModel();
        String relationshipId;

        relationship.setSource(theId.toString());
        relationship.setTarget(roodId.toString());
        relationship.setType("uncertain");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");

        Response actualResponse = jerseyTest
                .target("/tradition/" + tradId + "/relation")
                .request()
                .post(Entity.json(relationship));
        assertEquals(Response.Status.CREATED.getStatusCode(), actualResponse.getStatus());

        // Make sure it is there
        try (Transaction tx = db.beginTx()) {
            GraphModel readingsAndRelationships = actualResponse.readEntity(new GenericType<GraphModel>(){});
            relationshipId = ((RelationModel) readingsAndRelationships.getRelations().toArray()[0]).getId();
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
        relationship = new RelationModel();
        relationship.setSource(tehId.toString());
        relationship.setTarget(rootId.toString());
        relationship.setType("transposition");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");

        actualResponse = jerseyTest
                .target("/tradition/" + tradId + "/relation")
                .request()
                .post(Entity.json(relationship));
        assertEquals(Response.Status.CREATED.getStatusCode(), actualResponse.getStatus());

        // and make sure it is there.
        try (Transaction tx = db.beginTx()) {
            GraphModel readingsAndRelationships = actualResponse.readEntity(new GenericType<GraphModel>(){});
            relationshipId = ((RelationModel) readingsAndRelationships.getRelations().toArray()[0]).getId();
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
