package net.stemmaweb.stemmaserver.integrationtests;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import junit.framework.TestCase;
import net.stemmaweb.model.*;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.Util;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

public class RelationTypeTest extends TestCase {
    private GraphDatabaseService db;
    private JerseyTest jerseyTest;

    private String tradId;
    private String sectId;
    private List<ReadingModel> testReadings;


    public void setUp() throws Exception {
        super.setUp();
        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        Util.setupTestDB(db, "1");

        // Create a JerseyTestServer for the necessary REST API calls
        jerseyTest = Util.setupJersey();

        ClientResponse jerseyResult = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/john.csv", "csv");
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        tradId = Util.getValueFromJson(jerseyResult, "tradId");
        List<SectionModel> testSections = jerseyTest.resource().path("/tradition/" + tradId + "/sections")
                .get(new GenericType<List<SectionModel>>() {});
        sectId = testSections.get(0).getId();
        testReadings = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section/" + sectId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});
    }

    public void testNoRelationTypes() throws Exception {
        // Initially, no relationship types should be defined.
        ClientResponse jerseyResult = jerseyTest.resource().path("/tradition/" + tradId + "/relationtypes")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        List<RelationTypeModel> allRelTypes = jerseyResult.getEntity(new GenericType<List<RelationTypeModel>>() {});
        assertEquals(0, allRelTypes.size());
    }

    public void testCreateRelationAddType() throws Exception {
        // Find the 'legei' readings to relate
        ReadingModel legeiAcute = testReadings.stream().filter(x -> x.getRank() == 1 && x.getText().equals("λέγει")).findFirst().get();
        ReadingModel legei = testReadings.stream().filter(x -> x.getRank() == 1 && x.getText().equals("λεγει")).findFirst().get();

        // Make a relationship, check that there is a suitable relationship type created
        RelationshipModel newRel = new RelationshipModel();
        newRel.setSource(legeiAcute.getId());
        newRel.setTarget(legei.getId());
        newRel.setScope("document");
        newRel.setDisplayform("λέγει");
        newRel.setType("spelling");
        newRel.setIs_significant("no");

        ClientResponse jerseyResult = jerseyTest.resource().path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newRel);
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        GraphModel result = jerseyResult.getEntity(new GenericType<GraphModel>() {});
        assertEquals(2, result.getRelationships().size());

        // Now check that the spelling relation type has been created
        List<RelationTypeModel> allRelTypes = jerseyTest.resource().path("/tradition/" + tradId + "/relationtypes")
                .get(new GenericType<List<RelationTypeModel>>() {});
        assertEquals(1, allRelTypes.size());
        assertEquals("spelling", allRelTypes.get(0).getName());
        assertEquals(1, allRelTypes.get(0).getBindlevel());
    }

    public void testAddExplicitRelationType() throws Exception {
        ReadingModel legeiAcute = testReadings.stream().filter(x -> x.getRank() == 1 && x.getText().equals("λέγει")).findFirst().get();
        ReadingModel legei = testReadings.stream().filter(x -> x.getRank() == 1 && x.getText().equals("λεγει")).findFirst().get();

        // Make a relationship type of our own
        RelationTypeModel rtm = new RelationTypeModel();
        rtm.setName("spelling");
        rtm.setDescription("A weaker version of the spelling relationship");
        rtm.setIs_colocation(true);
        ClientResponse jerseyResult = jerseyTest.resource().path("/tradition/" + tradId + "/relationtype/spelling")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, rtm);
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());

        // Now use it
        RelationshipModel newRel = new RelationshipModel();
        newRel.setSource(legeiAcute.getId());
        newRel.setTarget(legei.getId());
        newRel.setScope("document");
        newRel.setDisplayform("λέγει");
        newRel.setType("spelling");
        newRel.setIs_significant("no");

        jerseyResult = jerseyTest.resource().path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newRel);
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        GraphModel result = jerseyResult.getEntity(new GenericType<GraphModel>() {});
        assertEquals(2, result.getRelationships().size());

        // Check that our relation type hasn't changed
        List<RelationTypeModel> allRelTypes = jerseyTest.resource().path("/tradition/" + tradId + "/relationtypes")
                .get(new GenericType<List<RelationTypeModel>>() {});
        assertEquals(1, allRelTypes.size());
        assertEquals("spelling", allRelTypes.get(0).getName());
        assertEquals("A weaker version of the spelling relationship", allRelTypes.get(0).getDescription());
        assertEquals(10, allRelTypes.get(0).getBindlevel());
    }

    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
        super.tearDown();
    }

}
