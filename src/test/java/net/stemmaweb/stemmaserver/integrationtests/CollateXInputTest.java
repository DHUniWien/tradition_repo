package net.stemmaweb.stemmaserver.integrationtests;

import junit.framework.TestCase;
import net.stemmaweb.model.*;
import net.stemmaweb.rest.*;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.Util;

import org.glassfish.jersey.test.JerseyTest;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Test the CollateX parser
 *
 */
public class CollateXInputTest extends TestCase {

    private GraphDatabaseService db;
    private JerseyTest jerseyTest;

    public void setUp() throws Exception {
        super.setUp();
        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        Util.setupTestDB(db, "1");

        // Create a JerseyTestServer for the necessary REST API calls
        jerseyTest = Util.setupJersey();
    }

    public void testParseCollateX() {
        Response cResult = Util.createTraditionFromFileOrString(jerseyTest, "Auch hier", "LR", "1",
                "src/TestFiles/plaetzchen_cx.xml", "collatex");
        assertEquals(Response.Status.CREATED.getStatusCode(), cResult.getStatus());

        String tradId = Util.getValueFromJson(cResult, "tradId");
        Tradition tradition = new Tradition(tradId);

        Response result = tradition.getAllWitnesses();
        @SuppressWarnings("unchecked")
        ArrayList<WitnessModel> allWitnesses = (ArrayList<WitnessModel>) result.getEntity();
        assertEquals(3, allWitnesses.size());

        // Get a witness text
        Witness witness = new Witness(tradId, "W2");
        TextSequenceModel response = (TextSequenceModel) witness.getWitnessAsText().getEntity();
        assertEquals("Ich hab auch hier wieder ein Pläzchen", response.getText());

        result = tradition.getAllReadings();
        @SuppressWarnings("unchecked")
        ArrayList<ReadingModel> allReadings = (ArrayList<ReadingModel>) result.getEntity();
        assertEquals(10, allReadings.size());
        assertTrue(allReadings.stream().anyMatch(x -> x.getText().equals("Plätzchen")));

        // Check that the common readings are set correctly
        List<String> common = allReadings.stream().filter(ReadingModel::getIs_common)
                .map(ReadingModel::getText).collect(Collectors.toList());
        List<String> expected = Arrays.asList("hab", "wieder ein");
        assertEquals(expected, common);
    }

    public void testParseCollateXFromPlaintext() {
        // To check that we deal as sensibly as possible with extraneous spaces in the
        // CollateX default string tokenisation
        Response cResult = Util.createTraditionFromFileOrString(jerseyTest, "Quick foxes", "LR", "1",
                "src/TestFiles/quick_brown_fox.xml", "collatex");
        assertEquals(Response.Status.CREATED.getStatusCode(), cResult.getStatus());

        String tradId = Util.getValueFromJson(cResult, "tradId");
        Witness witness = new Witness(tradId, "w1");
        TextSequenceModel response = (TextSequenceModel) witness.getWitnessAsText().getEntity();
        assertEquals("the quick brown fox jumped over the lazy dogs .", response.getText());
    }

    public void testAddRelationship() {
        // Parse the file
        Response cResult = Util.createTraditionFromFileOrString(jerseyTest, "Auch hier", "LR", "1",
                "src/TestFiles/plaetzchen_cx.xml", "collatex");
        assertEquals(Response.Status.CREATED.getStatusCode(), cResult.getStatus());

        String tradId = Util.getValueFromJson(cResult, "tradId");
        Tradition tradition = new Tradition(tradId);

        // Get the relevant reading IDs
        Response result = tradition.getAllReadings();
        @SuppressWarnings("unchecked")
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

        // Make the relation
        RelationModel relation = new RelationModel();
        relation.setSource(source);
        relation.setTarget(target);
        relation.setType("spelling");
        relation.setScope("local");
        Response actualResponse = jerseyTest
                .target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(relation));
        assertEquals(Response.Status.CREATED.getStatusCode(), actualResponse.getStatus());

        GraphModel readingsAndRelationships = actualResponse.readEntity(new GenericType<GraphModel>(){});
        assertEquals(0, readingsAndRelationships.getReadings().size());
        assertEquals(1, readingsAndRelationships.getRelations().size());


    }

    public void testParseCollateXJersey() {
        Response cResult = Util.createTraditionFromFileOrString(jerseyTest, "Auch hier", "LR", "1",
                "src/TestFiles/plaetzchen_cx.xml", "collatex");
        assertEquals(Response.Status.CREATED.getStatusCode(), cResult.getStatus());
        String tradId = Util.getValueFromJson(cResult, "tradId");

        Response result = jerseyTest.target("/tradition/" + tradId).request().get();
        TraditionModel tradInfo = result.readEntity(TraditionModel.class);
        assertEquals("LR", tradInfo.getDirection());
        assertEquals("Auch hier", tradInfo.getName());

        result = jerseyTest.target(("/tradition/" + tradId + "/witnesses")).request().get();
        ArrayList<WitnessModel> allWitnesses = result.readEntity(new GenericType<ArrayList<WitnessModel>>() {});
        assertEquals(3, allWitnesses.size());

        // Get a witness text
        result = jerseyTest.target("/tradition/" + tradId + "/witness/W2/text").request(MediaType.APPLICATION_JSON).get();
        TextSequenceModel response = result.readEntity(TextSequenceModel.class);
        assertEquals("Ich hab auch hier wieder ein Pläzchen", response.getText());

        result = jerseyTest.target("/tradition/" + tradId + "/readings").request().get();
        ArrayList<ReadingModel> allReadings = result.readEntity(new GenericType<ArrayList<ReadingModel>>() {});
        assertEquals(10, allReadings.size());
        boolean foundReading = false;
        List<String> common = Arrays.asList("hab", "wieder ein");
        for (ReadingModel r : allReadings) {
            if (r.getText().equals("Plätzchen")) {
                foundReading = true;
                break;
            }
            if (common.contains(r.getText()))
                assertTrue(r.getIs_common());
        }
        assertTrue(foundReading);
    }

    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
        super.tearDown();
    }
}
