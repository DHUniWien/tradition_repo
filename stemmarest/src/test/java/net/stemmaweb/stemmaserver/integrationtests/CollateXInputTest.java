package net.stemmaweb.stemmaserver.integrationtests;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import junit.framework.TestCase;
import net.stemmaweb.model.*;
import net.stemmaweb.rest.*;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.FileNotFoundException;
import java.util.ArrayList;

/**
 * Test the CollateX parser
 *
 */
public class CollateXInputTest extends TestCase {

    private GraphDatabaseService db;
    private JerseyTest jerseyTest;

    public void setUp() throws Exception {
        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        Util.setupTestDB(db, "1");

        // Create a JerseyTestServer for the necessary REST API calls
        Root webResource = new Root();
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(webResource)
                .create();
        jerseyTest.setUp();
    }

    public void testParseCollateX() throws Exception {
        ClientResponse cResult = null;
        try {
            String fileName = "src/TestFiles/plaetzchen_cx.xml";
            cResult = Util.createTraditionFromFile(jerseyTest, "Auch hier", "LR", "1", fileName, "collatex");
        } catch (FileNotFoundException e) {
            assertTrue(false);
        }
        assertEquals(Response.Status.CREATED.getStatusCode(), cResult.getStatus());

        String tradId = Util.getValueFromJson(cResult, "tradId");
        Tradition tradition = new Tradition(tradId);

        Response result = tradition.getAllWitnesses();
        ArrayList<WitnessModel> allWitnesses = (ArrayList<WitnessModel>) result.getEntity();
        assertEquals(3, allWitnesses.size());

        // Get a witness text
        Witness witness = new Witness(tradId, "W2");
        String witnessText = Util.getValueFromJson(witness.getWitnessAsText(), "text");
        assertEquals("Ich hab auch hier wieder ein Pläzchen", witnessText);

        result = tradition.getAllReadings();
        ArrayList<ReadingModel> allReadings = (ArrayList<ReadingModel>) result.getEntity();
        assertEquals(10, allReadings.size());
        Boolean foundReading = false;
        for (ReadingModel r : allReadings)
            if (r.getText().equals("Plätzchen"))
                foundReading = true;
        assertTrue(foundReading);
    }

    public void testAddRelationship() throws Exception {
        // Parse the file
        ClientResponse cResult = null;
        try {
            String fileName = "src/TestFiles/plaetzchen_cx.xml";
            cResult = Util.createTraditionFromFile(jerseyTest, "Auch hier", "LR", "1", fileName, "collatex");
        } catch (FileNotFoundException e) {
            assertTrue(false);
        }
        assertEquals(Response.Status.CREATED.getStatusCode(), cResult.getStatus());

        String tradId = Util.getValueFromJson(cResult, "tradId");
        Tradition tradition = new Tradition(tradId);

        // Get the relevant reading IDs
        Response result = tradition.getAllReadings();
        ArrayList<ReadingModel> readings = (ArrayList<ReadingModel>) result.getEntity();
        String source = null;
        String target = null;
        for (ReadingModel r : readings)
            if (r.getText().equals("Plätzchen"))
                source = r.getId();
            else if (r.getText().equals("Pläzchen"))
                target = r.getId();
        assertNotNull(source);
        assertNotNull(target);

        // Make the relationship
        RelationshipModel relationship = new RelationshipModel();
        relationship.setSource(source);
        relationship.setTarget(target);
        relationship.setType("spelling");
        relationship.setScope("local");
        ClientResponse actualResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, relationship);
        assertEquals(Response.Status.CREATED.getStatusCode(), actualResponse.getStatus());

        GraphModel readingsAndRelationships = actualResponse.getEntity(new GenericType<ArrayList<GraphModel>>(){}).get(0);
        assertEquals(2, readingsAndRelationships.getReadings().size());
        assertEquals(1, readingsAndRelationships.getRelationships().size());


    }

    public void testParseCollateXJersey() throws Exception {
        ClientResponse cResult = null;
        try {
            String fileName = "src/TestFiles/plaetzchen_cx.xml";
            cResult = Util.createTraditionFromFile(jerseyTest, "Auch hier", "LR", "1", fileName, "collatex");
        } catch (FileNotFoundException e) {
            assertTrue(false);
        }
        assertEquals(Response.Status.CREATED.getStatusCode(), cResult.getStatus());
        String tradId = Util.getValueFromJson(cResult, "tradId");
        Tradition tradition = new Tradition(tradId);

        Response result = tradition.getTraditionInfo();
        TraditionModel tradInfo = (TraditionModel) result.getEntity();
        assertEquals("LR", tradInfo.getDirection());
        assertEquals("Auch hier", tradInfo.getName());

        result = tradition.getAllWitnesses();
        ArrayList<WitnessModel> allWitnesses = (ArrayList<WitnessModel>) result.getEntity();
        assertEquals(3, allWitnesses.size());

        // Get a witness text
        Witness witness = new Witness(tradId, "W2");
        String witnessText = Util.getValueFromJson(witness.getWitnessAsText(), "text");
        assertEquals("Ich hab auch hier wieder ein Pläzchen", witnessText);

        result = tradition.getAllReadings();
        ArrayList<ReadingModel> allReadings = (ArrayList<ReadingModel>) result.getEntity();
        assertEquals(10, allReadings.size());
        Boolean foundReading = false;
        for (ReadingModel r : allReadings)
            if (r.getText().equals("Plätzchen")) {
                foundReading = true;
                break;
            }
        assertTrue(foundReading);
    }

    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
    }
}
