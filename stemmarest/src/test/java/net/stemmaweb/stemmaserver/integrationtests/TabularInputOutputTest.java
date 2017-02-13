package net.stemmaweb.stemmaserver.integrationtests;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import junit.framework.TestCase;
import net.stemmaweb.model.GraphModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.rest.*;
import net.stemmaweb.services.*;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashSet;


/**
 * Test tabular parsing of various forms.
 */
@SuppressWarnings("unchecked")
public class TabularInputOutputTest extends TestCase {

    private GraphDatabaseService db;
    private JerseyTest jerseyTest;

    public void setUp() throws Exception {
        super.setUp();
        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        Util.setupTestDB(db, "1");

        // Create a JerseyTestServer for the necessary REST API calls
        Root webResource = new Root();
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(webResource)
                .create();
        jerseyTest.setUp();
    }

    public void testParseCSV() throws Exception {
        ClientResponse response = Util.createTraditionFromFileOrString(jerseyTest, "Florilegium", "LR", "1",
                "src/TestFiles/florilegium_simple.csv", "csv");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        String tradId = Util.getValueFromJson(response, "tradId");
        Tradition tradition = new Tradition(tradId);

        Response result = tradition.getAllWitnesses();
        ArrayList<WitnessModel> allWitnesses = (ArrayList<WitnessModel>) result.getEntity();
        assertEquals(13, allWitnesses.size());

        // Get a witness text
        Witness witness = new Witness(tradId, "K");
        String witnessText = Util.getValueFromJson(witness.getWitnessAsText(), "text");
        System.out.println(witnessText);

        result = tradition.getAllReadings();
        ArrayList<ReadingModel> allReadings = (ArrayList<ReadingModel>) result.getEntity();
        assertEquals(310, allReadings.size());
        Boolean foundReading = false;
        for (ReadingModel r : allReadings)
            if (r.getText().equals("Μαξίμου"))
                foundReading = true;
        assertTrue(foundReading);
    }

    public void testParseCsvLayers() throws Exception {
        ClientResponse response = Util.createTraditionFromFileOrString(jerseyTest, "Florilegium", "LR", "1",
                "src/TestFiles/florilegium.csv", "csv");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        String tradId = Util.getValueFromJson(response, "tradId");
        Tradition tradition = new Tradition(tradId);

        Response result = tradition.getAllWitnesses();
        ArrayList<WitnessModel> allWitnesses = (ArrayList<WitnessModel>) result.getEntity();
        assertEquals(13, allWitnesses.size());

        // Get a witness text
        Witness witness = new Witness(tradId, "E");
        String witnessText = Util.getValueFromJson(witness.getWitnessAsText(), "text");
        String witnessLayerText = Util.getValueFromJson(witness.getWitnessAsTextWithLayer("a.c.", "0", "E"), "text");
        System.out.println(witnessText);
        System.out.println(witnessLayerText);
        assertFalse(witnessLayerText.equals(witnessText));

        result = tradition.getAllReadings();
        ArrayList<ReadingModel> allReadings = (ArrayList<ReadingModel>) result.getEntity();
        assertEquals(311, allReadings.size());
        Boolean foundReading = false;
        for (ReadingModel r : allReadings)
            if (r.getText().equals("Μαξίμου"))
                foundReading = true;
        assertTrue(foundReading);
    }

    public void testSetRelationship() throws Exception {
        ClientResponse response = Util.createTraditionFromFileOrString(jerseyTest, "Florilegium", "LR", "1",
                "src/TestFiles/florilegium.csv", "csv");
        String tradId = Util.getValueFromJson(response, "tradId");
        Tradition tradition = new Tradition(tradId);
        // Get the readings and look for our ἔχει(ν)
        Response result = tradition.getAllReadings();
        ArrayList<ReadingModel> readings = (ArrayList<ReadingModel>) result.getEntity();
        String source = null;
        String target = null;
        for (ReadingModel r : readings)
            if (r.getRank().equals(14L))
                if (r.getText().equals("ἔχει"))
                    source = r.getId();
                else if (r.getText().equals("ἔχειν"))
                    target = r.getId();
        assertNotNull(source);
        assertNotNull(target);

        // Now set the relationship
        RelationshipModel relationship = new RelationshipModel();
        relationship.setSource(source);
        relationship.setTarget(target);
        relationship.setType("grammatical");
        relationship.setAlters_meaning(0L);
        relationship.setScope("document");
        ClientResponse actualResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, relationship);
        assertEquals(Response.Status.CREATED.getStatusCode(), actualResponse.getStatus());

        GraphModel readingsAndRelationships = actualResponse.getEntity(new GenericType<GraphModel>(){});
        assertEquals(2, readingsAndRelationships.getReadings().size());
        assertEquals(1, readingsAndRelationships.getRelationships().size());
    }

    public void testParseExcel() throws Exception {
        // Test a bad file
        ClientResponse response = Util.createTraditionFromFileOrString(jerseyTest, "Armenian XLS", "LR", "1",
                "src/TestFiles/armexample_bad.xlsx", "xlsx");
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity(String.class).contains("has too many columns!"));


        // Test a good XLS file
        response = Util.createTraditionFromFileOrString(jerseyTest, "Armenian XLS", "LR", "1",
                "src/TestFiles/armexample.xls", "xls");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        String tradId = Util.getValueFromJson(response, "tradId");

        // Now retrieve the tradition and test what it has.
        Tradition tradition = new Tradition(tradId);

        Response result = tradition.getAllWitnesses();
        ArrayList<WitnessModel> allWitnesses = (ArrayList<WitnessModel>) result.getEntity();
        assertEquals(3, allWitnesses.size());

        result = tradition.getAllReadings();
        ArrayList<ReadingModel> allReadings = (ArrayList<ReadingModel>) result.getEntity();
        assertEquals(11, allReadings.size());
        Boolean foundReading = false;
        for (ReadingModel r : allReadings)
            if (r.getText().equals("այնոսիկ"))
                foundReading = true;
        assertTrue(foundReading);

        // Test a good XLSX file
        response = Util.createTraditionFromFileOrString(jerseyTest, "Armenian XLS", "LR", "1",
                "src/TestFiles/armexample.xlsx", "xlsx");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

        // Now retrieve the tradition and test what it has.
        tradId = Util.getValueFromJson(response, "tradId");
        tradition = new Tradition(tradId);

        result = tradition.getAllWitnesses();
        allWitnesses = (ArrayList<WitnessModel>) result.getEntity();
        assertEquals(3, allWitnesses.size());
        Boolean foundWitness = false;
        for (WitnessModel w : allWitnesses)
            if (w.getSigil().equals("Աբ2"))
                foundWitness = true;
        assertTrue(foundWitness);

        result = tradition.getAllReadings();
        allReadings = (ArrayList<ReadingModel>) result.getEntity();
        assertEquals(12, allReadings.size());
        foundReading = false;
        for (ReadingModel r : allReadings)
            if (r.getText().equals("այսոսիկ"))
                foundReading = true;
        assertTrue(foundReading);
    }

    // testOutputJSON
    public void testJSONExport() throws Exception {
        // Set up some data
        ClientResponse response = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/testTradition.xml", "graphml");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        String traditionId = Util.getValueFromJson(response, "tradId");

        // Get it back out
        ClientResponse result = jerseyTest
                .resource()
                .path("/tradition/" + traditionId + "/json")
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
        // Get the JSON out
        JSONObject table = result.getEntity(JSONObject.class);
        assertTrue(table.has("alignment"));
        assertTrue(table.has("length"));
        assertEquals(18, table.getInt("length"));
        JSONArray resultAlignment = table.getJSONArray("alignment");
        int i;
        for (i = 0; i < resultAlignment.length(); i++) {
            JSONObject resultWitness = resultAlignment.getJSONObject(0);
            assertTrue(resultWitness.has("witness"));
            assertTrue(resultWitness.has("tokens"));
            assertEquals(18, resultWitness.getJSONArray("tokens").length());
        }
        assertEquals(3, i);
    }

    public void testConflatedJSONExport () throws Exception {
        ClientResponse response = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/globalrel_test.xml", "graphml");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        String traditionId = Util.getValueFromJson(response, "tradId");
        assertNotNull(traditionId);
        // Get it back out
        ClientResponse result = jerseyTest
                .resource()
                .path("/tradition/" + traditionId + "/json")
                .queryParam("conflate", "collated")
                .queryParam("conflate", "spelling")
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
        // Get the JSON out
        JSONObject table = result.getEntity(JSONObject.class);
        assertTrue(table.has("alignment"));
        assertTrue(table.has("length"));
        assertEquals(10, table.getInt("length"));
        // Every reading at rank 6 should be identical
        JSONArray witnesses = table.getJSONArray("alignment");
        HashSet<String> readingsAt6 = new HashSet<>();
        for (int i = 0; i < witnesses.length(); i++) {
            Object r6 = witnesses.getJSONObject(i).getJSONArray("tokens").get(5);
            if (!r6.toString().equals("null"))
                readingsAt6.add(((JSONObject) r6).getString("t"));
        }
        assertEquals(1, readingsAt6.size());

        // Every reading at rank 9 should be identical
        HashSet<String> readingsAt9 = new HashSet<>();
        for (int i = 0; i < witnesses.length(); i++) {
            Object r9 = witnesses.getJSONObject(i).getJSONArray("tokens").get(8);
            if (!r9.toString().equals("null"))
                readingsAt9.add(((JSONObject) r9).getString("t"));
        }
        assertEquals(1, readingsAt9.size());
    }

    // TODO testOutputCSV

    // TODO testOutputExcel

    // TODO testOutputWithLayers

    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
        super.tearDown();
    }
}