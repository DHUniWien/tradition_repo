package net.stemmaweb.stemmaserver.integrationtests;



import static org.junit.Assert.assertEquals;

import java.util.HashMap;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;

import net.stemmaweb.model.GraphModel;
import net.stemmaweb.model.RelationModel;
import net.stemmaweb.rest.Root;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;

/**
 * 
 * Contains all tests for the api calls related to relations between readings.
 * 
 * @author PSE FS 2015 Team2
 *
 */
public class TranspositionTest {
    private String tradId;
    private String rootId;
    private String roodId;
    private String theId;
    private String tehId;

    private GraphDatabaseService db;
    private DatabaseManagementService dbbuilder;

    /*
     * JerseyTest is the test environment to Test api calls it provides a
     * grizzly http service
     */
    private JerseyTest jerseyTest;


    @Before
    public void setUp() throws Exception {
//        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
    	dbbuilder = new TestDatabaseManagementServiceBuilder().build();
    	dbbuilder.createDatabase("stemmatest");
    	db = dbbuilder.database("stemmatest");
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
        rootId = readingLookup.get("root/18");
        roodId = readingLookup.get("rood/17");
        tehId = readingLookup.get("teh/16");
        theId = readingLookup.get("the/17");
    }

    /**
     * Test that a transposition cannot be made if the readings are alignable
     */
    @Test
    public void disallowedTranspositionTest() {

        // First make sure that the pivot relationship does not exist
        /*
         * RelationModel shouldnotexist = new RelationModel();
         * shouldnotexist.setSource(tehId);
         * shouldnotexist.setTarget(rootId);
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
        relationship.setSource(tehId);
        relationship.setTarget(rootId);
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

        relationship.setSource(theId);
        relationship.setTarget(roodId);
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
            Relationship loadedRelationship = tx.getRelationshipByElementId(relationshipId);

            assertEquals(theId, loadedRelationship.getStartNode().getElementId());
            assertEquals(roodId, loadedRelationship.getEndNode().getElementId());
            assertEquals("uncertain", loadedRelationship.getProperty("type"));
            assertEquals(0L, loadedRelationship.getProperty("alters_meaning"));
            assertEquals("yes", loadedRelationship.getProperty("is_significant"));
            assertEquals("the", loadedRelationship.getProperty("reading_a"));
            assertEquals("rood", loadedRelationship.getProperty("reading_b"));
            tx.close();
        }

        // Now create the transposition, which should work this time
        relationship = new RelationModel();
        relationship.setSource(tehId);
        relationship.setTarget(rootId);
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
            Relationship loadedRelationship = tx.getRelationshipByElementId(relationshipId);

            assertEquals(tehId, loadedRelationship.getStartNode().getElementId());
            assertEquals(rootId, loadedRelationship.getEndNode().getElementId());
            assertEquals("transposition", loadedRelationship.getProperty("type"));
            assertEquals(0L, loadedRelationship.getProperty("alters_meaning"));
            assertEquals("yes", loadedRelationship.getProperty("is_significant"));
            assertEquals("teh", loadedRelationship.getProperty("reading_a"));
            assertEquals("root", loadedRelationship.getProperty("reading_b"));
            tx.close();
        }
    }

    /*
     * Shut down the jersey server
     * @throws Exception
     */
    @After
    public void tearDown() throws Exception {
//        db.shutdown();
    	if (dbbuilder != null) {
    		dbbuilder.shutdownDatabase(db.databaseName());
    	}
        jerseyTest.tearDown();
    }
}
