package net.stemmaweb.stemmaserver.integrationtests;

import com.opencsv.CSVReader;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.test.framework.JerseyTest;
import junit.framework.TestCase;
import net.stemmaweb.model.*;
import net.stemmaweb.rest.*;
import net.stemmaweb.services.*;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertNotEquals;


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

    public void testParseCSV() {
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
        WitnessTextModel resp = (WitnessTextModel) witness.getWitnessAsText().getEntity();
        System.out.println(resp.getText());

        result = tradition.getAllReadings();
        ArrayList<ReadingModel> allReadings = (ArrayList<ReadingModel>) result.getEntity();
        assertEquals(310, allReadings.size());
        boolean foundReading = false;
        for (ReadingModel r : allReadings)
            if (r.getText().equals("Μαξίμου"))
                foundReading = true;
        assertTrue(foundReading);
    }

    public void testParseCsvLayers() {
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
        WitnessTextModel tm = (WitnessTextModel) witness.getWitnessAsText().getEntity();
        List<String> layers = new ArrayList<>();
        layers.add("a.c.");
        WitnessTextModel ltm = (WitnessTextModel) witness.getWitnessAsTextWithLayer(
                layers, "0", "E").getEntity();
        System.out.println(tm.getText());
        System.out.println(ltm.getText());
        assertNotEquals(tm.getText(), ltm.getText());

        result = tradition.getAllReadings();
        ArrayList<ReadingModel> allReadings = (ArrayList<ReadingModel>) result.getEntity();
        assertEquals(311, allReadings.size());
        boolean foundReading = false;
        for (ReadingModel r : allReadings)
            if (r.getText().equals("Μαξίμου"))
                foundReading = true;
        assertTrue(foundReading);
    }

    public void testSetRelationship() {
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
        RelationModel relationship = new RelationModel();
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
        assertEquals(0, readingsAndRelationships.getReadings().size());
        assertEquals(1, readingsAndRelationships.getRelations().size());
    }

    public void testParseExcel() {
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
        boolean foundReading = false;
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
        boolean foundWitness = false;
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
                "src/TestFiles/testTradition.xml", "stemmaweb");
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

    public void testExportSelectedSections() throws Exception {
        ClientResponse response = Util.createTraditionFromFileOrString(jerseyTest, "Florilegium", "LR",
                "1", "src/TestFiles/florilegium_w.csv", "csv");
        assertEquals(ClientResponse.Status.CREATED.getStatusCode(), response.getStatus());
        String tradId = Util.getValueFromJson(response, "tradId");

        ArrayList<String> tradSections = new ArrayList<>();
        // Get the existing single section ID
        SectionModel firstSection = jerseyTest.resource().path("/tradition/" + tradId + "/sections")
                .get(new GenericType<List<SectionModel>>() {}).get(0);
        assertNotNull(firstSection);
        tradSections.add(firstSection.getId());

        // Add the other three sections
        int i = 0;
        while (i < 3) {
            String fileName = String.format("src/TestFiles/florilegium_%c.csv", 120 + i++);
            String sectId = Util.getValueFromJson(
                    Util.addSectionToTradition(jerseyTest, tradId, fileName, "csv", String.format("part %d", i)),
                    "parentId");
            tradSections.add(sectId);
        }

        // Request all sections
        response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/json")
                 .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        JSONObject table = response.getEntity(JSONObject.class);
        assertEquals(272, table.getInt("length"));
        assertEquals(13, table.getJSONArray("alignment").length());

        // Request section 4
        response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/json")
                .queryParam("start_section", tradSections.get(3))
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        table = response.getEntity(JSONObject.class);
        assertEquals(53, table.getInt("length"));
        assertEquals(5, table.getJSONArray("alignment").length());

        // Request sections up to 2
        response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/json")
                .queryParam("end_section", tradSections.get(1))
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        table = response.getEntity(JSONObject.class);
        assertEquals(155, table.getInt("length"));
        assertEquals(13, table.getJSONArray("alignment").length());

        // Request section 3
        response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/json")
                .queryParam("start_section", tradSections.get(2))
                .queryParam("end_section", tradSections.get(2))
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        table = response.getEntity(JSONObject.class);
        assertEquals(64, table.getInt("length"));
        assertEquals(13, table.getJSONArray("alignment").length());

        // Request sections 3-1
        response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/csv")
                .queryParam("start_section", tradSections.get(2))
                .queryParam("end_section", tradSections.get(0))
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("End section found before start section reached", response.getEntity(String.class));

        // Request sections 3-bad
        response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/tsv")
                .queryParam("start_section", tradSections.get(2))
                .queryParam("end_section", "123456")
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("Specified end section not found", response.getEntity(String.class));

        // Request sections bad-2
        response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/json")
                .queryParam("start_section", "123456")
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("Specified start section not found", response.getEntity(String.class));

        // Request sections on nonexisten tradition
        response = jerseyTest
                .resource()
                .path("/tradition/tradId/json")
                .queryParam("end_section", tradSections.get(1))
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

    }

    public void testConflatedJSONExport () throws Exception {
        ClientResponse response = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/globalrel_test.xml", "stemmaweb");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        String traditionId = Util.getValueFromJson(response, "tradId");
        assertNotNull(traditionId);
        // Get it back out
        ClientResponse result = jerseyTest
                .resource()
                .path("/tradition/" + traditionId + "/json")
                .queryParam("conflate", "collated")
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
                readingsAt6.add(((JSONObject) r6).getString("text"));
        }
        assertEquals(1, readingsAt6.size());

        // Every reading at rank 9 should be identical
        HashSet<String> readingsAt9 = new HashSet<>();
        for (int i = 0; i < witnesses.length(); i++) {
            Object r9 = witnesses.getJSONObject(i).getJSONArray("tokens").get(8);
            if (!r9.toString().equals("null"))
                readingsAt9.add(((JSONObject) r9).getString("text"));
        }
        assertEquals(1, readingsAt9.size());

        // TODO set some spelling & orthographic relations and make sure conflation still works
    }

    public void testCSVExport() throws Exception {
        ClientResponse response = Util.createTraditionFromFileOrString(jerseyTest, "Plaetzchen", "LR", "1",
                "src/TestFiles/plaetzchen_cx.xml", "collatex");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        String traditionId = Util.getValueFromJson(response, "tradId");
        assertNotNull(traditionId);

        // Set a spelling relationship between "plätzchen" nodes
        String ptz;
        String pz;
        try (Transaction tx = db.beginTx()) {
            Result res = db.execute("MATCH (n:READING {text:\"Plätzchen\", rank:5}) RETURN n");
            Iterator<Node> nodes = res.columnAs("n");
            assertTrue(nodes.hasNext());
            ptz = String.valueOf(nodes.next().getId());
            res = db.execute("MATCH (n:READING {text:\"Pläzchen\", rank:5}) RETURN n");
            nodes = res.columnAs("n");
            assertTrue(nodes.hasNext());
            pz = String.valueOf(nodes.next().getId());
            tx.success();
        }
        RelationModel spellingrel = new RelationModel();
        spellingrel.setSource(ptz);
        spellingrel.setTarget(pz);
        spellingrel.setType("spelling");
        spellingrel.setScope("local");
        ClientResponse result = jerseyTest.resource().path("/tradition/" + traditionId + "/relation/")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, spellingrel);
        assertEquals(Response.Status.CREATED.getStatusCode(), result.getStatus());


        // Get CSV without conflation
        result = jerseyTest.resource().path("/tradition/" + traditionId + "/csv")
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
        CSVReader rdr = new CSVReader(new StringReader(result.getEntity(String.class)));
        // See that we have our witnesses
        String[] wits = rdr.readNext();
        assertEquals(3, wits.length);
        assertEquals("W2", wits[1]);
        // See that we have our rows
        List<String[]> rows = rdr.readAll();
        assertEquals(5, rows.size());
        // See that the last row has two separate readings
        HashSet<String> rank5 = new HashSet<>(Arrays.asList(rows.get(4)));
        assertEquals(2, rank5.size());
        assertTrue(rank5.contains("Plätzchen"));
        assertTrue(rank5.contains("Pläzchen"));


        // Now with conflation, and exercise the tab-sep functionality at the same time
        result = jerseyTest
                .resource()
                .path("/tradition/" + traditionId + "/tsv")
                .queryParam("conflate", "spelling")
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
        rdr = new CSVReader(new StringReader(result.getEntity(String.class)), '\t');
        // See that we have our witnesses
        wits = rdr.readNext();
        assertEquals(3, wits.length);
        assertEquals("W2", wits[1]);
        // See that we have our rows
        rows = rdr.readAll();
        assertEquals(5, rows.size());
        // See that the last row has two separate readings
        rank5 = new HashSet<>(Arrays.asList(rows.get(4)));
        assertEquals(1, rank5.size());
        assertTrue(rank5.contains("Plätzchen") || rank5.contains("Pläzchen"));
    }

    public void testExportMultiSection() throws Exception {
        ClientResponse response = Util.createTraditionFromFileOrString(jerseyTest, "Legend", "LR", "1",
                "src/TestFiles/lf2.xml", "stemmaweb");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        String traditionId = Util.getValueFromJson(response, "tradId");
        assertNotNull(traditionId);

        // Get the CSV and check the witness length
        response = jerseyTest.resource().path("/tradition/" + traditionId + "/csv")
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        CSVReader rdr = new CSVReader(new StringReader(response.getEntity(String.class)));
        // See that we have our witnesses
        String[] wits = rdr.readNext();
        assertEquals(34, wits.length);
        assertEquals("B", wits[1]);

        // Add the second section
        Util.addSectionToTradition(jerseyTest, traditionId, "src/TestFiles/legendfrag.xml",
                "stemmaweb", "section 2");

        // Export the whole thing to JSON and check the readings
        response = jerseyTest.resource().path("/tradition/" + traditionId + "/json")
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        JSONObject table = response.getEntity(JSONObject.class);
        assertTrue(table.has("alignment"));
        assertTrue(table.has("length"));
        assertEquals(30, table.getInt("length"));
        JSONObject witN = table.getJSONArray("alignment").getJSONObject(24);
        assertEquals("N", witN.getString("witness"));
        for (int i=0; i < 21; i++) {
            assertEquals("null", witN.getJSONArray("tokens").getString(i));
        }
        JSONObject firstN = witN.getJSONArray("tokens").getJSONObject(21);
        assertEquals("in", firstN.getString("text"));


        // Now export the whole thing to CSV
        response = jerseyTest.resource().path("/tradition/" + traditionId + "/csv")
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        rdr = new CSVReader(new StringReader(response.getEntity(String.class)));
        // See that we have our witnesses
        wits = rdr.readNext();
        assertEquals(37, wits.length);
        assertEquals("Ab", wits[1]);
    }

    public void testExportWithLayers() throws Exception {
        // Take the uncorrected MoE section
        ClientResponse response = Util.createTraditionFromFileOrString(jerseyTest, "Chronicle", "LR", "1",
                "src/TestFiles/Matthew-401.json", "cxjson");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        String traditionId = Util.getValueFromJson(response, "tradId");
        assertNotNull(traditionId);

        // Now add the section with corrections
        Util.addSectionToTradition(jerseyTest, traditionId, "src/TestFiles/Matthew-407.json",
                "cxjson", "AM 407");

        // Export it to JSON
        response = jerseyTest.resource().path("/tradition/" + traditionId + "/json")
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        JSONObject table = response.getEntity(JSONObject.class);
        assertEquals(239, table.getInt("length"));
        JSONArray alignment = table.getJSONArray("alignment");
        // There should be a.c. columns for these witnesses
        String[] expectedCorr = {"Bz644", "C", "F", "G", "I", "J", "K", "M6605", "O", "W243", "Z"};
        HashSet<String> correctedWits = new HashSet<>(Arrays.asList(expectedCorr));
        HashSet<String> actualCorr = new HashSet<>();
        // The text of C and C (a.c.) should be identical for the first 159 ranks
        JSONArray cTokens = null;
        JSONArray cAcTokens = null;
        for (int i=0; i < alignment.length(); i++) {
            JSONObject witTokens = alignment.getJSONObject(i);
            String thisWit = witTokens.getString("witness");
            String thisLayer = witTokens.isNull("layer") ? null : witTokens.getString("layer");
            if (thisLayer != null)
                actualCorr.add(thisWit);
            if (thisWit.equals("C") && thisLayer == null) cTokens = witTokens.getJSONArray("tokens");
            else if (thisWit.equals("C")) cAcTokens = witTokens.getJSONArray("tokens");

        }
        assertTrue(correctedWits.containsAll(actualCorr));
        assertEquals(34, alignment.length());
        assertNotNull(cTokens);
        assertNotNull(cAcTokens);
        // The first 159 ranks of witness C (a.c.) should be the same as for witness C
        for (int i=0; i < 159; i++) {
            try {
                assertEquals(cTokens.getJSONObject(i).getString("text"),
                        cAcTokens.getJSONObject(i).getString("text"));
            } catch (JSONException e) {
                assertEquals(cTokens.getString(i), cAcTokens.getString(i));
            }
        }

    }

    public void testComplexLayerExport() {
        ClientResponse response = Util.createTraditionFromFileOrString(jerseyTest, "Florilegium", "LR", "1",
                "src/TestFiles/florilegium_tei_ps.xml", "teips");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        String tradId = Util.getValueFromJson(response, "tradId");

        // Split this into multiple sections - ranks 38, 156, 228
        List<SectionModel> allSections = jerseyTest.resource().path("/tradition/" + tradId + "/sections/")
                .get(new GenericType<List<SectionModel>>() {});
        String section1 = allSections.get(0).getId();
        ClientResponse jerseyResponse = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section/" + section1 + "/splitAtRank/228")
                .post(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        String section4 = Util.getValueFromJson(jerseyResponse, "sectionId");
        jerseyResponse = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section/" + section1 + "/splitAtRank/156")
                .post(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        // String section3 = Util.getValueFromJson(jerseyResponse, "sectionId");
        jerseyResponse = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section/" + section1 + "/splitAtRank/38")
                .post(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        // String section2 = Util.getValueFromJson(jerseyResponse, "sectionId");

        // Section 1 should be missing witnesses B and G
        // Section 4 should be missing all witnesses except A/B/C/P/S
        List<WitnessModel> section1Wits = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section/" + section1 + "/witnesses")
                .get(new GenericType<List<WitnessModel>>() {});
        assertEquals(11, section1Wits.size());
        for (WitnessModel wm : section1Wits) {
            assertNotEquals("G", wm.getSigil());
            assertNotEquals("B", wm.getSigil());
        }
        List<WitnessModel> section4Wits = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section/" + section4 + "/witnesses")
                .get(new GenericType<List<WitnessModel>>() {});
        assertEquals(5, section4Wits.size());
        List<String> expectedWits = Arrays.asList("A", "B", "C", "P", "S");
        for (WitnessModel wm : section4Wits)
            assertTrue(expectedWits.contains(wm.getSigil()));

        // Now we are ready to export tabular and see what we get.
        jerseyResponse = jerseyTest.resource().path("/tradition/" + tradId + "/json")
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        // Ensure we have all our witnesses
        AlignmentModel alignment = jerseyResponse.getEntity(AlignmentModel.class);
        assertEquals(280, alignment.getLength());
        List<WitnessTokensModel> table = alignment.getAlignment();
        assertEquals(21, table.size());

        HashSet<String> allSigla = new HashSet<>();
        // Ensure we have all our layers
        for (WitnessTokensModel wtm : table)
            allSigla.add(wtm.constructSigil());
        ArrayList<String> allExpectedWits = new ArrayList<>(Arrays.asList("D", "E", "E (a.c.)", "E (s.l.)", "F", "G",
                "H", "H (s.l.)", "K", "P (a.c.)", "Q", "Q (a.c.)", "Q (s.l.)", "Q (margin)", "T", "T (a.c.)"));
        allExpectedWits.addAll(expectedWits);
        for (String sigil : allExpectedWits)
            assertTrue(allSigla.contains(sigil));

        // Set up variables for our specific witness tests
        List<ReadingModel> qColumn = null, qAcColumn = null, qSlColumn = null, qMarginColumn = null;
        List<ReadingModel> eColumn = null, eAcColumn = null, eSlColumn = null;
        List<ReadingModel> hColumn = null, hSlColumn = null;
        List<ReadingModel> pColumn = null, pAcColumn = null;
        List<ReadingModel> tColumn = null, tAcColumn = null;
        // Ensure that each text is correct
        for (WitnessTokensModel wtm : table) {
            assertEquals(alignment.getLength(), wtm.getTokens().size());
            String sigil = wtm.constructSigil();
            WebResource request = jerseyTest.resource()
                    .path("/tradition/" + tradId + "/witness/" + wtm.getWitness() + "/readings");
            if (wtm.hasLayer()) {
                request = request.queryParam("layer", wtm.getLayer());
            }
            List<ReadingModel> witReadings = request.get(new GenericType<List<ReadingModel>>() {});
            // Save the specific columns we will need later
            switch (wtm.constructSigil()) {
                case "Q (a.c.)":
                    qAcColumn = wtm.getTokens();
                    break;
                case "Q (s.l.)":
                    qSlColumn = wtm.getTokens();
                    break;
                case "Q (margin)":
                    qMarginColumn = wtm.getTokens();
                    break;
                case "Q":
                    qColumn = wtm.getTokens();
                    break;
                case "E (a.c.)":
                    eAcColumn = wtm.getTokens();
                    break;
                case "E (s.l.)":
                    eSlColumn = wtm.getTokens();
                    break;
                case "E":
                    eColumn = wtm.getTokens();
                    break;
                case "H (s.l.)":
                    hSlColumn = wtm.getTokens();
                    break;
                case "H":
                    hColumn = wtm.getTokens();
                    break;
                case "P (a.c.)":
                    pAcColumn = wtm.getTokens();
                    break;
                case "P":
                    pColumn = wtm.getTokens();
                    break;
                case "T (a.c.)":
                    tAcColumn = wtm.getTokens();
                    break;
                case "T":
                    tColumn = wtm.getTokens();
                    break;
            }

            // Make a copy of the tableReadings since we will mutate it
            List<ReadingModel> tableReadings = new ArrayList<>(wtm.getTokens());
            // Check that the first reading is at the correct rank
            Long firstRank = witReadings.get(0).getRank();
            // Account for section splits, above
            if (wtm.getWitness().equals("B") || wtm.getWitness().equals("G"))
                firstRank += 37;
            int i = 1;
            while (i < firstRank) {
                assertNull(tableReadings.get(i-1));
                i++;
            }
            assertNotNull(tableReadings.get(i-1));
            // Check that the last reading is at the correct rank
            if (!expectedWits.contains(wtm.getWitness()))
                for (int j = 227; j < alignment.getLength(); j++)
                    assertNull(tableReadings.get(j));

            // Check that the (non-null) reading sequences are identical
            tableReadings.removeIf(Objects::isNull);
            List<String> witRdgIds = witReadings.stream().map(ReadingModel::getId).collect(Collectors.toList());
            List<String> tableRdgIds = tableReadings.stream().map(ReadingModel::getId).collect(Collectors.toList());

            assertEquals(witRdgIds, tableRdgIds);
        }

        // Specific data sanity tests...
        // Ensure that H (s.l.) differs from H only at rank 32
        // Ensure that Q (a.c.) differs from Q only at ranks 35, 87, 110
        // Ensure that P (a.c.) differs from P only at ranks 69–71
        // Ensure that Q and E (s.l.) differ from bases only at rank 87
        // Ensure that E (a.c.) differs from E only at rank 87
        // Ensure that Q (margin) differs from Q only at rank 101
        // Ensure that T (a.c.) differs from T only at rank 200
        assertNotNull(qColumn);
        assertNotNull(qAcColumn);
        assertNotNull(qSlColumn);
        assertNotNull(qMarginColumn);
        assertNotNull(eColumn);
        assertNotNull(eAcColumn);
        assertNotNull(eSlColumn);
        assertNotNull(hColumn);
        assertNotNull(hSlColumn);
        assertNotNull(pColumn);
        assertNotNull(pAcColumn);
        assertNotNull(tColumn);
        assertNotNull(tAcColumn);
        for (int i = 0; i < alignment.getLength(); i++) {
            if (i == 31) {
                assertTrue(checkRdgSame(qColumn, qAcColumn, i));
                assertTrue(checkRdgSame(qColumn, qSlColumn, i));
                assertTrue(checkRdgSame(qColumn, qMarginColumn, i));
                assertTrue(checkRdgSame(eColumn, eAcColumn, i));
                assertTrue(checkRdgSame(eColumn, eSlColumn, i));
                assertFalse(checkRdgSame(hColumn, hSlColumn, i));
                assertTrue(checkRdgSame(pColumn, pAcColumn, i));
                assertTrue(checkRdgSame(tColumn, tAcColumn, i));
            } else if (i == 34) {
                assertFalse(checkRdgSame(qColumn, qAcColumn, i));
                assertTrue(checkRdgSame(qColumn, qSlColumn, i));
                assertTrue(checkRdgSame(qColumn, qMarginColumn, i));
                assertTrue(checkRdgSame(eColumn, eAcColumn, i));
                assertTrue(checkRdgSame(eColumn, eSlColumn, i));
                assertTrue(checkRdgSame(hColumn, hSlColumn, i));
                assertTrue(checkRdgSame(pColumn, pAcColumn, i));
                assertTrue(checkRdgSame(tColumn, tAcColumn, i));
            } else if (i > 67 && i < 71) {
                assertTrue(checkRdgSame(qColumn, qAcColumn, i));
                assertTrue(checkRdgSame(qColumn, qSlColumn, i));
                assertTrue(checkRdgSame(qColumn, qMarginColumn, i));
                assertTrue(checkRdgSame(eColumn, eAcColumn, i));
                assertTrue(checkRdgSame(eColumn, eSlColumn, i));
                assertTrue(checkRdgSame(hColumn, hSlColumn, i));
                assertFalse(checkRdgSame(pColumn, pAcColumn, i));
                assertTrue(checkRdgSame(tColumn, tAcColumn, i));
            } else if (i == 86) {
                assertFalse(checkRdgSame(qColumn, qAcColumn, i));
                assertFalse(checkRdgSame(qColumn, qSlColumn, i));
                assertTrue(checkRdgSame(qColumn, qMarginColumn, i));
                assertFalse(checkRdgSame(eColumn, eAcColumn, i));
                assertFalse(checkRdgSame(eColumn, eSlColumn, i));
                assertTrue(checkRdgSame(hColumn, hSlColumn, i));
                assertTrue(checkRdgSame(pColumn, pAcColumn, i));
                assertTrue(checkRdgSame(tColumn, tAcColumn, i));
            } else if (i == 100) {
                assertTrue(checkRdgSame(qColumn, qAcColumn, i));
                assertTrue(checkRdgSame(qColumn, qSlColumn, i));
                assertFalse(checkRdgSame(qColumn, qMarginColumn, i));
                assertTrue(checkRdgSame(eColumn, eAcColumn, i));
                assertTrue(checkRdgSame(eColumn, eSlColumn, i));
                assertTrue(checkRdgSame(hColumn, hSlColumn, i));
                assertTrue(checkRdgSame(pColumn, pAcColumn, i));
                assertTrue(checkRdgSame(tColumn, tAcColumn, i));
            } else if (i == 109) {
                assertFalse(checkRdgSame(qColumn, qAcColumn, i));
                assertTrue(checkRdgSame(qColumn, qSlColumn, i));
                assertTrue(checkRdgSame(qColumn, qMarginColumn, i));
                assertTrue(checkRdgSame(eColumn, eAcColumn, i));
                assertTrue(checkRdgSame(eColumn, eSlColumn, i));
                assertTrue(checkRdgSame(hColumn, hSlColumn, i));
                assertTrue(checkRdgSame(pColumn, pAcColumn, i));
                assertTrue(checkRdgSame(tColumn, tAcColumn, i));
            } else if (i == 199) {
                assertTrue(checkRdgSame(qColumn, qAcColumn, i));
                assertTrue(checkRdgSame(qColumn, qSlColumn, i));
                assertTrue(checkRdgSame(qColumn, qMarginColumn, i));
                assertTrue(checkRdgSame(eColumn, eAcColumn, i));
                assertTrue(checkRdgSame(eColumn, eSlColumn, i));
                assertTrue(checkRdgSame(hColumn, hSlColumn, i));
                assertTrue(checkRdgSame(pColumn, pAcColumn, i));
                assertFalse(checkRdgSame(tColumn, tAcColumn, i));
            } else {
                assertTrue(checkRdgSame(qColumn, qAcColumn, i));
                assertTrue(checkRdgSame(qColumn, qSlColumn, i));
                assertTrue(checkRdgSame(qColumn, qMarginColumn, i));
                assertTrue(checkRdgSame(eColumn, eAcColumn, i));
                assertTrue(checkRdgSame(eColumn, eSlColumn, i));
                assertTrue(checkRdgSame(hColumn, hSlColumn, i));
                assertTrue(checkRdgSame(pColumn, pAcColumn, i));
                assertTrue(checkRdgSame(tColumn, tAcColumn, i));
            }
        }
    }

    private boolean checkRdgSame(List<ReadingModel> c1, List<ReadingModel> c2, int index) {
        ReadingModel r1 = c1.get(index);
        ReadingModel r2 = c2.get(index);
        if (r1 == null) return r2 == null;
        if (r2 == null) return false; // we would have returned already if c1 were null
        return r1.getId().equals(r2.getId());
    }


    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
        super.tearDown();
    }
}