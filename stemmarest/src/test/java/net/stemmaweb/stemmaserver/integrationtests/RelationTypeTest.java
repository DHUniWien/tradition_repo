package net.stemmaweb.stemmaserver.integrationtests;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import junit.framework.TestCase;
import net.stemmaweb.model.*;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.Util;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class RelationTypeTest extends TestCase {
    private GraphDatabaseService db;
    private JerseyTest jerseyTest;

    private String tradId;
    private String sectId;
    private HashMap<String,String> readingLookup;


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
        List<ReadingModel> allReadings = jerseyTest.resource().path("/tradition/" + tradId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});
        readingLookup = new HashMap<>();
        for (ReadingModel rm : allReadings) {
            String key = rm.getText() + "/" + rm.getRank().toString();
            readingLookup.put(key, rm.getId());
        }
    }

    public void testNoRelationTypes() {
        // Initially, no relationship types should be defined.
        ClientResponse jerseyResult = jerseyTest.resource().path("/tradition/" + tradId + "/relationtypes")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        List<RelationTypeModel> allRelTypes = jerseyResult.getEntity(new GenericType<List<RelationTypeModel>>() {});
        assertEquals(0, allRelTypes.size());
    }

    public void testCreateRelationAddType() {
        // Find the 'legei' readings to relate
        String legeiAcute = readingLookup.getOrDefault("λέγει/1", "17");
        String legei = readingLookup.getOrDefault("λεγει/1", "17");

        // Make a relationship, check that there is a suitable relationship type created
        RelationshipModel newRel = new RelationshipModel();
        newRel.setSource(legeiAcute);
        newRel.setTarget(legei);
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

    public void testAddExplicitRelationType() {
        String legeiAcute = readingLookup.getOrDefault("λέγει/1", "17");
        String legei = readingLookup.getOrDefault("λεγει/1", "17");

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
        newRel.setSource(legeiAcute);
        newRel.setTarget(legei);
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

    public void testSimpleTransitivity() {
        String legeiAcute = readingLookup.getOrDefault("λέγει/1", "17");
        String legei = readingLookup.getOrDefault("λεγει/1", "17");
        String Legei = readingLookup.getOrDefault("Λεγει/1", "17");

        // Collect relationship IDs
        HashSet<String> createdRels = new HashSet<>();
        HashSet<String> expectedLinks = new HashSet<>();

        // Set the first link
        RelationshipModel newRel = new RelationshipModel();
        newRel.setSource(legei);
        newRel.setTarget(Legei);
        newRel.setScope("local");
        newRel.setType("spelling");
        expectedLinks.add(String.format("%s -> %s", legei, Legei));

        ClientResponse jerseyResult = jerseyTest.resource().path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newRel);
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        GraphModel result = jerseyResult.getEntity(new GenericType<GraphModel>() {});
        assertEquals(1, result.getRelationships().size());
        assertEquals(0, result.getReadings().size());
        result.getRelationships().forEach(x -> createdRels.add(x.getId()));

        // Set the second link
        newRel.setTarget(legeiAcute);
        jerseyResult = jerseyTest.resource().path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newRel);
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        result = jerseyResult.getEntity(new GenericType<GraphModel>() {});
        assertEquals(2, result.getRelationships().size());
        assertEquals(0, result.getReadings().size());
        result.getRelationships().forEach(x -> createdRels.add(x.getId()));
        expectedLinks.add(String.format("%s -> %s", legei, legeiAcute));
        expectedLinks.add(String.format("%s -> %s", Legei, legeiAcute));

        try (Transaction tx = db.beginTx()) {
            for (String rid : createdRels) {
                Relationship link = db.getRelationshipById(Long.valueOf(rid));
                String lookfor = String.format("%s -> %s", link.getStartNode().getId(), link.getEndNode().getId());
                assertTrue(expectedLinks.contains(lookfor));
                expectedLinks.remove(lookfor);
            }
            tx.success();
        }
        assertTrue(expectedLinks.isEmpty());
    }

    public void testBindlevelTransitivity() {
        // Use πάλιν at 12 and 55
        String palin = readingLookup.getOrDefault("παλιν/12", "17");
        String pali_ = readingLookup.getOrDefault("παλι¯/12", "17");
        String Palin = readingLookup.getOrDefault("Παλιν/12", "17");
        String palinac = readingLookup.getOrDefault("πάλιν/12", "17");
        String palin58 = readingLookup.getOrDefault("παλιν/55", "17");
        String pali_58 = readingLookup.getOrDefault("παλι¯/55", "17");
        String Palin58 = readingLookup.getOrDefault("Παλιν/55", "17");
        String palinac58 = readingLookup.getOrDefault("πάλιν/55", "17");

        // Collect relationship IDs
        HashSet<String> createdRels = new HashSet<>();
        HashSet<String> expectedLinks = new HashSet<>();

        // Set the first link
        RelationshipModel newRel = new RelationshipModel();
        newRel.setSource(palin);
        newRel.setTarget(Palin);
        newRel.setScope("document");
        newRel.setType("orthographic");
        expectedLinks.add(String.format("%s -> %s: orthographic", palin, Palin));
        expectedLinks.add(String.format("%s -> %s: orthographic", palin58, Palin58));

        ClientResponse jerseyResult = jerseyTest.resource().path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newRel);
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        GraphModel result = jerseyResult.getEntity(new GenericType<GraphModel>() {});
        assertEquals(2, result.getRelationships().size());
        assertEquals(0, result.getReadings().size());
        result.getRelationships().forEach(x -> createdRels.add(x.getId()));

        // Set the second link, should result in one extra per rank
        newRel.setTarget(pali_);
        newRel.setType("spelling");
        jerseyResult = jerseyTest.resource().path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newRel);
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        result = jerseyResult.getEntity(new GenericType<GraphModel>() {});
        assertEquals(4, result.getRelationships().size());
        assertEquals(0, result.getReadings().size());
        result.getRelationships().forEach(x -> createdRels.add(x.getId()));
        expectedLinks.add(String.format("%s -> %s: spelling", palin, pali_));
        expectedLinks.add(String.format("%s -> %s: spelling", palin58, pali_58));
        expectedLinks.add(String.format("%s -> %s: spelling", Palin, pali_));
        expectedLinks.add(String.format("%s -> %s: spelling", Palin58, pali_58));

        // Set the third link, should result in two extra per rank
        newRel.setSource(Palin);
        newRel.setTarget(palinac);
        newRel.setType("orthographic");
        jerseyResult = jerseyTest.resource().path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newRel);
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        result = jerseyResult.getEntity(new GenericType<GraphModel>() {});
        assertEquals(6, result.getRelationships().size());
        assertEquals(0, result.getReadings().size());
        result.getRelationships().forEach(x -> createdRels.add(x.getId()));
        expectedLinks.add(String.format("%s -> %s: orthographic", Palin, palinac));
        expectedLinks.add(String.format("%s -> %s: orthographic", Palin58, palinac58));
        expectedLinks.add(String.format("%s -> %s: spelling", pali_, palinac));
        expectedLinks.add(String.format("%s -> %s: spelling", pali_58, palinac58));
        expectedLinks.add(String.format("%s -> %s: orthographic", palin, palinac));
        expectedLinks.add(String.format("%s -> %s: orthographic", palin58, palinac58));

        try (Transaction tx = db.beginTx()) {
            for (String rid : createdRels) {
                Relationship link = db.getRelationshipById(Long.valueOf(rid));
                String lookfor = String.format("%s -> %s: %s",
                        link.getStartNode().getId(), link.getEndNode().getId(), link.getProperty("type"));
                assertTrue(expectedLinks.contains(lookfor));
                expectedLinks.remove(lookfor);
            }
            tx.success();
        }
        assertTrue(expectedLinks.isEmpty());

    }

    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
        super.tearDown();
    }

}
