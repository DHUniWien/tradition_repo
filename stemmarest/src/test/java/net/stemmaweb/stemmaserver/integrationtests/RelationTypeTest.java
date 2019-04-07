package net.stemmaweb.stemmaserver.integrationtests;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import junit.framework.TestCase;
import net.stemmaweb.model.*;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.Util;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

public class RelationTypeTest extends TestCase {
    private GraphDatabaseService db;
    private JerseyTest jerseyTest;

    private String tradId;
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
        readingLookup = Util.makeReadingLookup(jerseyTest, tradId);
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
        RelationModel newRel = new RelationModel();
        newRel.setSource(legeiAcute);
        newRel.setTarget(legei);
        newRel.setScope("tradition");
        newRel.setDisplayform("λέγει");
        newRel.setType("spelling");
        newRel.setIs_significant("no");

        ClientResponse jerseyResult = jerseyTest.resource().path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newRel);
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        GraphModel result = jerseyResult.getEntity(new GenericType<GraphModel>() {});
        assertEquals(2, result.getRelations().size());

        // Now check that the spelling relation type has been created
        List<RelationTypeModel> allRelTypes = jerseyTest.resource().path("/tradition/" + tradId + "/relationtypes")
                .get(new GenericType<List<RelationTypeModel>>() {});
        assertEquals(1, allRelTypes.size());
        assertEquals("spelling", allRelTypes.get(0).getName());
        assertEquals(1, allRelTypes.get(0).getBindlevel());
    }

    private void checkExpectedRelations(HashSet<String> createdRels, HashSet<String> expectedLinks) {
        try (Transaction tx = db.beginTx()) {
            for (String rid : createdRels) {
                Relationship link = db.getRelationshipById(Long.valueOf(rid));
                String lookfor = String.format("%s -> %s: %s",
                        link.getStartNode().getId(), link.getEndNode().getId(), link.getProperty("type"));
                String lookrev = String.format("%s -> %s: %s",
                        link.getEndNode().getId(), link.getStartNode().getId(), link.getProperty("type"));
                String message = String.format("looking for %s in %s", lookfor,
                        java.util.Arrays.toString(expectedLinks.toArray()));
                assertTrue(message,expectedLinks.remove(lookfor) ^ expectedLinks.remove(lookrev));
            }
            tx.success();
        }
        assertTrue(expectedLinks.isEmpty());
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
        RelationModel newRel = new RelationModel();
        newRel.setSource(legeiAcute);
        newRel.setTarget(legei);
        newRel.setScope("tradition");
        newRel.setDisplayform("λέγει");
        newRel.setType("spelling");
        newRel.setIs_significant("no");

        jerseyResult = jerseyTest.resource().path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newRel);
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        GraphModel result = jerseyResult.getEntity(new GenericType<GraphModel>() {});
        assertEquals(2, result.getRelations().size());

        // Check that our relation type hasn't changed
        List<RelationTypeModel> allRelTypes = jerseyTest.resource().path("/tradition/" + tradId + "/relationtypes")
                .get(new GenericType<List<RelationTypeModel>>() {});
        assertEquals(1, allRelTypes.size());
        assertEquals("spelling", allRelTypes.get(0).getName());
        assertEquals("A weaker version of the spelling relationship", allRelTypes.get(0).getDescription());
        assertEquals(10, allRelTypes.get(0).getBindlevel());
    }

    public void testNonGeneralizable() {
        String legeiAcute = readingLookup.getOrDefault("λέγει/1", "17");
        String legei = readingLookup.getOrDefault("λεγει/1", "17");

        // Make a relationship type of our own
        RelationTypeModel rtm = new RelationTypeModel();
        rtm.setName("important");
        rtm.setDescription("Something we care about for our own reasons");
        rtm.setIs_colocation(true);
        rtm.setIs_generalizable(false);
        ClientResponse jerseyResult = jerseyTest.resource().path("/tradition/" + tradId + "/relationtype/important")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, rtm);
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());

        // Now use it
        RelationModel newRel = new RelationModel();
        newRel.setSource(legeiAcute);
        newRel.setTarget(legei);
        newRel.setScope("tradition");
        newRel.setType("important");

        jerseyResult = jerseyTest.resource().path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newRel);
        assertEquals(Response.Status.CONFLICT.getStatusCode(), jerseyResult.getStatus());
    }

    public void testUseRegular() {
        // Set a normal form
        String auTw = readingLookup.getOrDefault("αυΤω/3", "17");
        String autwi = readingLookup.getOrDefault("αὐτῷ/3", "17");

        KeyPropertyModel kp = new KeyPropertyModel();
        kp.setKey("normal_form");
        kp.setProperty("αυτῶ");
        ReadingChangePropertyModel newNormal = new ReadingChangePropertyModel();
        newNormal.addProperty(kp);
        ClientResponse jerseyResult = jerseyTest.resource().path("/reading/" + auTw)
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, newNormal);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());

        // Now make the relation
        RelationModel newRel = new RelationModel();
        newRel.setSource(autwi);
        newRel.setTarget(auTw);
        newRel.setType("grammatical");
        newRel.setScope("tradition");
        jerseyResult = jerseyTest.resource().path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newRel);
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());

        // and check that the normal form αυτῶ was found at rank 28.
        GraphModel result = jerseyResult.getEntity(new GenericType<GraphModel>() {});
        assertEquals(2, result.getRelations().size());
        assertEquals(0, result.getReadings().size());
        for (RelationModel rm : result.getRelations()) {
            if (rm.getSource().equals(autwi)) continue;
            ReadingModel otherRankReading = jerseyTest.resource().path("/reading/" + rm.getTarget())
                    .get(new GenericType<ReadingModel>() {});
            assertEquals("αυτῶ", otherRankReading.getText());
        }
    }

    public void testSimpleTransitivity() {
        String legeiAcute = readingLookup.getOrDefault("λέγει/1", "17");
        String legei = readingLookup.getOrDefault("λεγει/1", "17");
        String Legei = readingLookup.getOrDefault("Λεγει/1", "17");

        // Collect relationship IDs
        HashSet<String> createdRels = new HashSet<>();
        HashSet<String> expectedLinks = new HashSet<>();

        // Set the first link
        RelationModel newRel = new RelationModel();
        newRel.setSource(legei);
        newRel.setTarget(Legei);
        newRel.setScope("local");
        newRel.setType("spelling");
        expectedLinks.add(String.format("%s -> %s: spelling", legei, Legei));

        ClientResponse jerseyResult = jerseyTest.resource().path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newRel);
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        GraphModel result = jerseyResult.getEntity(new GenericType<GraphModel>() {});
        assertEquals(1, result.getRelations().size());
        assertEquals(0, result.getReadings().size());
        result.getRelations().forEach(x -> createdRels.add(x.getId()));

        // Set the second link
        newRel.setTarget(legeiAcute);
        jerseyResult = jerseyTest.resource().path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newRel);
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        result = jerseyResult.getEntity(new GenericType<GraphModel>() {});
        assertEquals(2, result.getRelations().size());
        assertEquals(0, result.getReadings().size());
        result.getRelations().forEach(x -> createdRels.add(x.getId()));
        expectedLinks.add(String.format("%s -> %s: spelling", legei, legeiAcute));
        expectedLinks.add(String.format("%s -> %s: spelling", Legei, legeiAcute));

        checkExpectedRelations(createdRels, expectedLinks);
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
        RelationModel newRel = new RelationModel();
        newRel.setSource(palin);
        newRel.setTarget(Palin);
        newRel.setScope("tradition");
        newRel.setType("orthographic");
        expectedLinks.add(String.format("%s -> %s: orthographic", palin, Palin));
        expectedLinks.add(String.format("%s -> %s: orthographic", palin58, Palin58));

        ClientResponse jerseyResult = jerseyTest.resource().path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newRel);
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        GraphModel result = jerseyResult.getEntity(new GenericType<GraphModel>() {});
        assertEquals(2, result.getRelations().size());
        assertEquals(0, result.getReadings().size());
        result.getRelations().forEach(x -> createdRels.add(x.getId()));

        // Set the second link, should result in one extra per rank
        newRel.setTarget(pali_);
        newRel.setType("spelling");
        jerseyResult = jerseyTest.resource().path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newRel);
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        result = jerseyResult.getEntity(new GenericType<GraphModel>() {});
        assertEquals(4, result.getRelations().size());
        assertEquals(0, result.getReadings().size());
        result.getRelations().forEach(x -> createdRels.add(x.getId()));
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
        assertEquals(6, result.getRelations().size());
        assertEquals(0, result.getReadings().size());
        result.getRelations().forEach(x -> createdRels.add(x.getId()));
        expectedLinks.add(String.format("%s -> %s: orthographic", Palin, palinac));
        expectedLinks.add(String.format("%s -> %s: orthographic", Palin58, palinac58));
        expectedLinks.add(String.format("%s -> %s: spelling", pali_, palinac));
        expectedLinks.add(String.format("%s -> %s: spelling", pali_58, palinac58));
        expectedLinks.add(String.format("%s -> %s: orthographic", palin, palinac));
        expectedLinks.add(String.format("%s -> %s: orthographic", palin58, palinac58));

        checkExpectedRelations(createdRels, expectedLinks);
    }

    public void testTransitivityReRanking() {
        // Use the εὑρίσκω variants at ranks 22 and 24/25
        String eurisko22 = readingLookup.getOrDefault("εὑρίσκω/22", "17");
        String euricko22 = readingLookup.getOrDefault("ε̣υριϲκω/22", "17");
        String euricko24 = readingLookup.getOrDefault("ευριϲκω/24", "17");
        String eurecko24 = readingLookup.getOrDefault("ευρηϲκω/24", "17");
        String ricko25 = readingLookup.getOrDefault("ριϲκω/25", "17");

        HashSet<String> testReadings = new HashSet<>();

        // Re-rank the whole thing according to relations or lack thereof
        // LATER maybe implement the 'collated' relation
        ClientResponse jerseyResult = jerseyTest.resource().path("/tradition/" + tradId + "/initRanks")
                .get(ClientResponse.class);
        assertEquals("success", Util.getValueFromJson(jerseyResult, "result"));

        // First make the same-rank relations
        RelationModel newRel = new RelationModel();
        newRel.setSource(eurisko22);
        newRel.setTarget(euricko22);
        newRel.setScope("local");
        newRel.setType("orthographic");
        jerseyResult = jerseyTest.resource().path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newRel);
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        GraphModel result = jerseyResult.getEntity(new GenericType<GraphModel>() {});
        assertEquals(1, result.getRelations().size());
        assertEquals(0, result.getReadings().size());
        testReadings.add(eurisko22);
        testReadings.add(euricko22);

        try (Transaction tx = db.beginTx()) {
            for (String nid : testReadings) {
                Node n = db.getNodeById(Long.valueOf(nid));
                assertEquals(22L, n.getProperty("rank"));
            }
            tx.success();
        }

        newRel.setSource(euricko24);
        newRel.setTarget(eurecko24);
        newRel.setType("spelling");
        jerseyResult = jerseyTest.resource().path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newRel);
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        result = jerseyResult.getEntity(new GenericType<GraphModel>() {});
        assertEquals(1, result.getRelations().size());
        assertEquals(0, result.getReadings().size());
        testReadings.add(euricko24);
        testReadings.add(eurecko24);

        // Now join them together, and test that the appropriate ranks changed
        newRel.setTarget(eurisko22);
        newRel.setType("orthographic");
        jerseyResult = jerseyTest.resource().path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newRel);
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        result = jerseyResult.getEntity(new GenericType<GraphModel>() {});
        assertEquals(4, result.getRelations().size());
        assertEquals(8, result.getReadings().size());

        try (Transaction tx = db.beginTx()) {
            for (String nid : testReadings) {
                Node n = db.getNodeById(Long.valueOf(nid));
                assertEquals(24L, n.getProperty("rank"));
            }
            tx.success();
        }

        // Now add in an "other" relation, which is *not* transitive, to make sure the ranks still update.
        newRel.setTarget(ricko25);
        newRel.setType("other");
        jerseyResult = jerseyTest.resource().path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newRel);
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        result = jerseyResult.getEntity(new GenericType<GraphModel>() {});
        assertEquals(1, result.getRelations().size());
        // This will affect readings all the way to the end node.
        assertTrue(result.getReadings().size() > 100);
        Optional<ReadingModel> optEnd = result.getReadings().stream().filter(ReadingModel::getIs_end).findAny();
        assertTrue(optEnd.isPresent());
        assertEquals(Long.valueOf(69), optEnd.get().getRank());

        testReadings.add(ricko25);

        try (Transaction tx = db.beginTx()) {
            for (String nid : testReadings) {
                Node n = db.getNodeById(Long.valueOf(nid));
                assertEquals(25L, n.getProperty("rank"));
            }
            tx.success();
        }
    }

    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
        super.tearDown();
    }

}
