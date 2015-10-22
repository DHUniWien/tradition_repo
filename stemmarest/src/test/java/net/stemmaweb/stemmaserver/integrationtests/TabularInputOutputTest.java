package net.stemmaweb.stemmaserver.integrationtests;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.test.framework.JerseyTest;
import junit.framework.TestCase;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.rest.*;
import net.stemmaweb.services.*;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test tabular parsing of various forms.
 */
public class TabularInputOutputTest extends TestCase {

    private GraphDatabaseService db;
    private JerseyTest jerseyTest;
    private TabularToNeo4JParser importResource;

    public void setUp() throws Exception {
        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        DatabaseService.createRootNode(db);
        try(Transaction tx = db.beginTx())
        {
            Node rootNode = db.findNode(Nodes.ROOT, "name", "Root node");
            Node node = db.createNode(Nodes.USER);
            node.setProperty("id", "1");
            node.setProperty("role", "admin");

            rootNode.createRelationshipTo(node, ERelations.SEQUENCE);
            tx.success();
        }

        importResource = new TabularToNeo4JParser();
        // Create a JerseyTestServer for the necessary REST API calls
        Root webResource = new Root();
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(webResource)
                .create();
        jerseyTest.setUp();
    }

    public void testParseCSV() throws Exception {
        InputStream fileStream = new FileInputStream("src/TestFiles/florilegium_simple.csv");
        Response result = importResource.parseCSV(fileStream, "1", "Florilegium", ',');
        assertEquals(Response.Status.CREATED.getStatusCode(), result.getStatus());

        String tradId = Util.getValueFromJson(result, "tradId");
        Tradition tradition = new Tradition(tradId);

        result = tradition.getAllWitnesses();
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

    public void testParseExcel() throws Exception {
        // Test a bad file
        InputStream fileStream = new FileInputStream("src/TestFiles/armexample_bad.xlsx");
        Response result = importResource.parseExcel(fileStream, "1", "Armenian XLS", "xlsx");
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), result.getStatus());
        assertTrue(result.getEntity().toString().contains("has too many columns!"));

        // Test a good XLS file
        fileStream = new FileInputStream("src/TestFiles/armexample.xls");
        result = importResource.parseExcel(fileStream, "1", "Armenian XLS", "xls");
        assertEquals(Response.Status.CREATED.getStatusCode(), result.getStatus());

        // Now retrieve the tradition and test what it has.
        String tradId = Util.getValueFromJson(result, "tradId");
        Tradition tradition = new Tradition(tradId);

        result = tradition.getAllWitnesses();
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
        fileStream = new FileInputStream("src/TestFiles/armexample.xlsx");
        result = importResource.parseExcel(fileStream, "1", "Armenian XLS", "xlsx");
        assertEquals(Response.Status.CREATED.getStatusCode(), result.getStatus());

        // Now retrieve the tradition and test what it has.
        tradId = Util.getValueFromJson(result, "tradId");
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
        GraphMLToNeo4JParser graphImporter = new GraphMLToNeo4JParser();
        String traditionId = null;
        try
        {
            Response response = graphImporter.parseGraphML("src/TestFiles/testTradition.xml", "1", "Tradition");
            traditionId = Util.getValueFromJson(response, "tradId");
        }
        catch(FileNotFoundException f)
        {
            // this error should not occur
            assertTrue(false);
        }
        assertNotNull(traditionId);

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

    // testOutputCSV

    // testOutputExcel

    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
    }
}