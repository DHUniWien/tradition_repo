package net.stemmaweb.stemmaserver.integrationtests;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import net.stemmaweb.model.GraphModel;
import net.stemmaweb.model.KeyPropertyModel;
import net.stemmaweb.model.ReadingChangePropertyModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationModel;
import net.stemmaweb.model.RelationTypeModel;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.Util;

import static org.junit.Assert.*;

public class RelationTypeTest {
    private GraphDatabaseService db;
    private JerseyTest jerseyTest;

    private String tradId;
    private HashMap<String,String> readingLookup;

    @Before
    public void setUp() throws Exception {
//        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        DatabaseManagementService dbbuilder = new TestDatabaseManagementServiceBuilder().impermanent().build();
    	db = dbbuilder.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME);
    	new GraphDatabaseServiceProvider(dbbuilder, db);
        Util.setupTestDB(db, "1");

        // Create a JerseyTestServer for the necessary REST API calls
        jerseyTest = Util.setupJersey();

        Response jerseyResult = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/john.csv", "csv");
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        tradId = Util.getValueFromJson(jerseyResult, "tradId");
        readingLookup = Util.makeReadingLookup(jerseyTest, tradId);
    }

    @Test
    public void testInitialRelationTypes() {
        // Initially, the only defined relation type should be the "collated" one for the CSV input.
        Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relationtypes")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        List<RelationTypeModel> allRelTypes = jerseyResult.readEntity(new GenericType<>() {});
        assertEquals(1, allRelTypes.size());
        assertEquals("collated", allRelTypes.getFirst().getName());
    }

    @Test
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

        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(newRel))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
            GraphModel result = jerseyResult.readEntity(new GenericType<>() {});
            assertEquals(2, result.getRelations().size());
        }

        // Now check that the spelling relation type has been created
        List<RelationTypeModel> allRelTypes = jerseyTest.target("/tradition/" + tradId + "/relationtypes")
                .request()
                .get(new GenericType<>() {});
        assertEquals(2, allRelTypes.size());
        RelationTypeModel rtm = jerseyTest.target("/tradition/" + tradId + "/relationtype/spelling")
                .request().get(RelationTypeModel.class);
        assertEquals("spelling", rtm.getName());
        assertEquals(1, rtm.getBindlevel());
        assertTrue(rtm.getIs_colocation());
    }

    private void checkExpectedRelations(HashSet<String> createdRels, HashSet<String> expectedLinks) {
        try (Transaction tx = db.beginTx()) {
            for (String rid : createdRels) {
                Relationship link = DatabaseService.findRelatedOrThrow(tx, rid);
                String lookfor = String.format("%s -> %s: %s",
                        link.getStartNode().getProperty("id").toString(), link.getEndNode().getProperty("id").toString(), link.getProperty("type"));
                String lookrev = String.format("%s -> %s: %s",
                        link.getEndNode().getProperty("id").toString(), link.getStartNode().getProperty("id").toString(), link.getProperty("type"));
                String message = String.format("looking for %s in %s", lookfor,
                        java.util.Arrays.toString(expectedLinks.toArray()));
                assertTrue(message,expectedLinks.remove(lookfor) ^ expectedLinks.remove(lookrev));
            }
            tx.commit();
        }
        assertTrue(expectedLinks.isEmpty());
    }

    @Test
    public void testAddExplicitRelationType() {
        String legeiAcute = readingLookup.getOrDefault("λέγει/1", "17");
        String legei = readingLookup.getOrDefault("λεγει/1", "17");

        // Make a relationship type of our own
        RelationTypeModel rtm = new RelationTypeModel();
        rtm.setName("spelling");
        rtm.setDescription("A weaker version of the spelling relationship");
        rtm.setIs_colocation(true);
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relationtype/spelling")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(rtm))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        }

        // Now use it
        RelationModel newRel = new RelationModel();
        newRel.setSource(legeiAcute);
        newRel.setTarget(legei);
        newRel.setScope("tradition");
        newRel.setDisplayform("λέγει");
        newRel.setType("spelling");
        newRel.setIs_significant("no");

        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(newRel))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
            GraphModel result = jerseyResult.readEntity(new GenericType<>() {});
            assertEquals(2, result.getRelations().size());
        }

        // Check that our relation type hasn't changed
        List<RelationTypeModel> allRelTypes = jerseyTest.target("/tradition/" + tradId + "/relationtypes")
                .request()
                .get(new GenericType<>() {});
        assertEquals(2, allRelTypes.size());
        RelationTypeModel spel = jerseyTest.target("/tradition/" + tradId + "/relationtype/spelling")
                .request().get(RelationTypeModel.class);
        assertEquals("spelling", spel.getName());
        assertEquals("A weaker version of the spelling relationship", spel.getDescription());
        assertEquals(10, spel.getBindlevel());
    }

    @Test
    public void testAddDefaultType() {
        RelationTypeModel rtm = new RelationTypeModel();
        rtm.setName("spelling");
        rtm.setDefaultsettings(true);

        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relationtype/spelling")
                .request(MediaType.APPLICATION_JSON).put(Entity.json(rtm))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
            RelationTypeModel created = jerseyResult.readEntity(RelationTypeModel.class);
            assertEquals("spelling", created.getName());
            assertNull(created.getDefaultsettings());
            assertEquals("These are the same reading, spelled differently.", created.getDescription());
            assertEquals(1, created.getBindlevel());
            assertTrue(created.getIs_colocation());
            assertTrue(created.getIs_transitive());
        }

        // Now try setting the same default relation again, which should fail
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relationtype/spelling")
                .request(MediaType.APPLICATION_JSON).put(Entity.json(rtm))) {
            assertEquals(Response.Status.CONFLICT.getStatusCode(), jerseyResult.getStatus());
        }
    }

    @Test
    public void testAddDisplayProperty () {
        // Make an arbitrary (default) relation type
        RelationTypeModel rtm = new RelationTypeModel();
        rtm.setName("grammatical");
        rtm.setDefaultsettings(true);
        RelationTypeModel created;
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relationtype/grammatical")
                .request(MediaType.APPLICATION_JSON).put(Entity.json(rtm))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
            created = jerseyResult.readEntity(RelationTypeModel.class);
        }
        assertEquals("grammatical", created.getName());
        // Set a display property
        String firstDisplay = "{\"net.stemmaweb.variantmapper\": {\"color\": \"yellow\"}}";
        created.setDisplay(firstDisplay);
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relationtype/grammatical")
                .request(MediaType.APPLICATION_JSON).put(Entity.json(created))) {
            assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
            assertEquals(firstDisplay, jerseyResult.readEntity(RelationTypeModel.class).getDisplay());
        }
        // Try to set a bad display property
        String badDisplay = "\"net.stemmweb.variantmapper\": {\"color\": \"red\"}";
        created.setDisplay(badDisplay);
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relationtype/grammatical")
                .request(MediaType.APPLICATION_JSON).put(Entity.json(created))) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), jerseyResult.getStatus());
            assertTrue(Util.getValueFromJson(jerseyResult, "error").startsWith("Invalid display string"));
        }
        // Make sure nothing changed
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relationtype/grammatical")
                .request(MediaType.APPLICATION_JSON).get()) {
            assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
            assertEquals(firstDisplay, jerseyResult.readEntity(RelationTypeModel.class).getDisplay());
        }

    }

    @Test
    public void testNonGeneralizable() {
        String legeiAcute = readingLookup.getOrDefault("λέγει/1", "17");
        String legei = readingLookup.getOrDefault("λεγει/1", "17");

        // Make a relationship type of our own
        RelationTypeModel rtm = new RelationTypeModel();
        rtm.setName("important");
        rtm.setDescription("Something we care about for our own reasons");
        rtm.setIs_colocation(true);
        rtm.setIs_generalizable(false);
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relationtype/important")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(rtm))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        }

        // Now use it
        RelationModel newRel = new RelationModel();
        newRel.setSource(legeiAcute);
        newRel.setTarget(legei);
        newRel.setScope("tradition");
        newRel.setType("important");

        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(newRel))) {
            assertEquals(Response.Status.CONFLICT.getStatusCode(), jerseyResult.getStatus());
        }
    }

    @Test
    public void testUseRegular() {
        // Set a normal form
        String auTw = readingLookup.getOrDefault("αυΤω/3", "17");
        String autwi = readingLookup.getOrDefault("αὐτῷ/3", "17");

        KeyPropertyModel kp = new KeyPropertyModel();
        kp.setKey("normal_form");
        kp.setProperty("αυτῶ");
        ReadingChangePropertyModel newNormal = new ReadingChangePropertyModel();
        newNormal.addProperty(kp);
        try (Response jerseyResult = jerseyTest.target("/reading/" + auTw)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(newNormal))) {
            assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        }

        // Now make the relation
        RelationModel newRel = new RelationModel();
        newRel.setSource(autwi);
        newRel.setTarget(auTw);
        newRel.setType("grammatical");
        newRel.setScope("tradition");
        GraphModel result;
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(newRel))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
            result = jerseyResult.readEntity(new GenericType<>() {});
        }
        assertEquals(2, result.getRelations().size());
        assertEquals(0, result.getReadings().size());
        // and check that the normal form αυτῶ was found at rank 28.
        for (RelationModel rm : result.getRelations()) {
            if (rm.getSource().equals(autwi)) continue;
            ReadingModel otherRankReading = jerseyTest.target("/reading/" + rm.getTarget())
                    .request()
                    .get(new GenericType<>() {});
            assertEquals("αυτῶ", otherRankReading.getText());
        }
    }

    @Test
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

        GraphModel result;
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(newRel))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
            result = jerseyResult.readEntity(new GenericType<>() {});
        }
        assertEquals(1, result.getRelations().size());
        assertEquals(0, result.getReadings().size());
        result.getRelations().forEach(x -> createdRels.add(x.getId()));

        // Set the second link
        newRel.setTarget(legeiAcute);
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(newRel))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
            result = jerseyResult.readEntity(new GenericType<>() {});
        }
        assertEquals(2, result.getRelations().size());
        assertEquals(0, result.getReadings().size());
        result.getRelations().forEach(x -> createdRels.add(x.getId()));
        expectedLinks.add(String.format("%s -> %s: spelling", legei, legeiAcute));
        expectedLinks.add(String.format("%s -> %s: spelling", Legei, legeiAcute));

        checkExpectedRelations(createdRels, expectedLinks);
    }

    @Test
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

        GraphModel result;
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(newRel))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
            result = jerseyResult.readEntity(new GenericType<>() {});
            assertEquals(2, result.getRelations().size());
            assertEquals(0, result.getReadings().size());
            result.getRelations().forEach(x -> createdRels.add(x.getId()));
        }

        // Set the second link, should result in one extra per rank
        newRel.setTarget(pali_);
        newRel.setType("spelling");
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(newRel))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
            result = jerseyResult.readEntity(new GenericType<>() {});
        }
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
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(newRel))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
            result = jerseyResult.readEntity(new GenericType<>() {});
        }
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

    @Test
    public void testTransitivityReRanking() {
        // Use the εὑρίσκω variants at ranks 22 and 24/25
        String eurisko22 = readingLookup.getOrDefault("εὑρίσκω/22", "17");
        String euricko22 = readingLookup.getOrDefault("ε̣υριϲκω/22", "17");
        String euricko24 = readingLookup.getOrDefault("ευριϲκω/24", "17");
        String eurecko24 = readingLookup.getOrDefault("ευρηϲκω/24", "17");
        String ricko25 = readingLookup.getOrDefault("ριϲκω/25", "17");

        HashSet<String> testReadings = new HashSet<>();

        // Remove all the 'collated' relations and then re-rank from the beginning.
        for (RelationModel rm : jerseyTest.target("/tradition/" + tradId + "/relations")
                .request().get(new GenericType<List<RelationModel>>() {})) {
            if (rm.getType().equals("collated")) {
                try (Response rd = jerseyTest.target("/tradition/" + tradId + "/relation/remove")
                        .request(MediaType.APPLICATION_JSON).post(Entity.json(rm))) {
                    assertEquals(Response.Status.OK.getStatusCode(), rd.getStatus());
                }
            }
        }
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/initRanks")
                .request()
                .post(null)) {
            assertEquals("success", Util.getValueFromJson(jerseyResult, "result"));
        }

        // First make the same-rank relations
        RelationModel newRel = new RelationModel();
        newRel.setSource(eurisko22);
        newRel.setTarget(euricko22);
        newRel.setScope("local");
        newRel.setType("orthographic");
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(newRel))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
            GraphModel result = jerseyResult.readEntity(new GenericType<>() {});
            assertEquals(1, result.getRelations().size());
            assertEquals(0, result.getReadings().size());
        }

        testReadings.add(eurisko22);
        testReadings.add(euricko22);

        try (Transaction tx = db.beginTx()) {
            for (String nid : testReadings) {
                Node n = DatabaseService.findNodeOrThrow(tx, Nodes.READING, nid);
                assertEquals(22L, n.getProperty("rank"));
            }
            tx.commit();
        }

        newRel.setSource(euricko24);
        newRel.setTarget(eurecko24);
        newRel.setType("spelling");
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(newRel))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
            GraphModel result = jerseyResult.readEntity(new GenericType<>() {});
            assertEquals(1, result.getRelations().size());
            assertEquals(0, result.getReadings().size());
        }

        testReadings.add(euricko24);
        testReadings.add(eurecko24);

        // Now join them together, and test that the appropriate ranks changed
        newRel.setTarget(eurisko22);
        newRel.setType("orthographic");
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(newRel))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
            GraphModel result = jerseyResult.readEntity(new GenericType<>() {});
            assertEquals(4, result.getRelations().size());
            assertEquals(8, result.getReadings().size());
        }

        try (Transaction tx = db.beginTx()) {
            for (String nid : testReadings) {
                Node n = DatabaseService.findNodeOrThrow(tx, Nodes.READING, nid);
                assertEquals(24L, n.getProperty("rank"));
            }
            tx.commit();
        }

        // Now add in an "other" relation, which is *not* transitive, to make sure the ranks still update.
        newRel.setTarget(ricko25);
        newRel.setType("other");
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(newRel))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
            GraphModel result = jerseyResult.readEntity(new GenericType<>() {});
            assertEquals(1, result.getRelations().size());
            // This will affect readings all the way to the end node.
            assertTrue(result.getReadings().size() > 100);
            Optional<ReadingModel> optEnd = result.getReadings().stream().filter(ReadingModel::getIs_end).findAny();
            assertTrue(optEnd.isPresent());
            assertEquals(Long.valueOf(69), optEnd.get().getRank());
        }

        testReadings.add(ricko25);
        try (Transaction tx = db.beginTx()) {
            for (String nid : testReadings) {
                Node n = DatabaseService.findNodeOrThrow(tx, Nodes.READING, nid);
                assertEquals(25L, n.getProperty("rank"));
            }
            tx.commit();
        }
    }

    @Test
    public void testRelTypeDelete() {
        String legeiAcute = readingLookup.getOrDefault("λέγει/1", "17");
        String legei = readingLookup.getOrDefault("λεγει/1", "17");

        // Make a relationship type of our own
        RelationTypeModel rtm = new RelationTypeModel();
        rtm.setName("accents");
        rtm.setDescription("Readings are the same but for diacriticals");
        rtm.setIs_colocation(true);
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relationtype/accents")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(rtm))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        }

        // Now use it
        RelationModel newRel = new RelationModel();
        newRel.setSource(legeiAcute);
        newRel.setTarget(legei);
        newRel.setScope("tradition");
        newRel.setDisplayform("λέγει");
        newRel.setType("accents");
        newRel.setIs_significant("no");

        GraphModel result;
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(newRel))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
            result = jerseyResult.readEntity(new GenericType<>() {});
            assertEquals(2, result.getRelations().size());
        }

        // Now try to delete the relation type, even though relations exist
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relationtype/accents")
                .request().delete()) {
            assertEquals(Response.Status.CONFLICT.getStatusCode(), jerseyResult.getStatus());
        }

        // Delete the relations in question
        for (RelationModel rm : result.getRelations()) {
            try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relation/remove")
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(rm))) {
                assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
            }
        }

        // Try again to delete the relation type, which should work
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relationtype/accents")
                .request().delete()) {
            assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
            RelationTypeModel deletedRt = jerseyResult.readEntity(RelationTypeModel.class);
            assertEquals(rtm.getName(), deletedRt.getName());

        }

        // Now, for fun, try to delete a nonexistent relation type
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/diacriticals")
                .request().delete()) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), r.getStatus());
        }
    }

    @Test
    public void testPutRenameUsesUrlNotBody() {
        // Create a relation type under "accents"
        RelationTypeModel rtm = new RelationTypeModel();
        rtm.setName("accents");
        rtm.setDescription("Readings are the same but for diacriticals");
        rtm.setIs_colocation(true);
        String reltypeId;
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/accents")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(rtm))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
            reltypeId = r.readEntity(RelationTypeModel.class).getId();
        }

        // PUT again to the same URL, with a new name in the body
        rtm.setName("diacriticals");
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/accents")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(rtm))) {
            assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
            // The ID should not have changed
            RelationTypeModel result = r.readEntity(RelationTypeModel.class);
            assertEquals(reltypeId, result.getId());
            assertEquals("diacriticals", result.getName());
        }

        // Fetching by the new name succeeds
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/diacriticals")
                .request(MediaType.APPLICATION_JSON).get()) {
            RelationTypeModel result = r.readEntity(RelationTypeModel.class);
            assertEquals(reltypeId, result.getId());
            assertEquals("diacriticals", result.getName());
        }

        // Fetching by the old name now 404s
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/accents")
                .request(MediaType.APPLICATION_JSON).get()) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), r.getStatus());
        }

        // There is only one relation type of the new name
        List<RelationTypeModel> allRelTypes = jerseyTest.target("/tradition/" + tradId + "/relationtypes")
                .request().get(new GenericType<>() {});
        long matching = allRelTypes.stream().filter(t -> t.getName().equals("diacriticals")).count();
        assertEquals(1, matching);
        assertTrue(allRelTypes.stream().noneMatch(t -> t.getName().equals("accents")));
    }

    @Test
    public void testRelationTypeIdAndDualAddressing() {
        RelationTypeModel rtm = new RelationTypeModel();
        rtm.setName("testtype");
        rtm.setDescription("A test type");
        RelationTypeModel created;
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/testtype")
                .request(MediaType.APPLICATION_JSON).put(Entity.json(rtm))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
            created = r.readEntity(RelationTypeModel.class);
        }
        assertNotNull(created.getId());
        assertTrue(DatabaseService.nameIsNumeric(created.getId()));
        String id = created.getId();

        // GET by numeric id works identically to GET by name
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/" + id)
                .request(MediaType.APPLICATION_JSON).get()) {
            assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
            assertEquals("testtype", r.readEntity(RelationTypeModel.class).getName());
        }

        // PUT (rename) by numeric id
        rtm.setName("renamedtype");
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/" + id)
                .request(MediaType.APPLICATION_JSON).put(Entity.json(rtm))) {
            assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
            assertEquals("renamedtype", r.readEntity(RelationTypeModel.class).getName());
        }

        // DELETE by numeric id
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/" + id)
                .request().delete()) {
            assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
        }
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/" + id)
                .request(MediaType.APPLICATION_JSON).get()) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), r.getStatus());
        }
    }

    @Test
    public void testRelationTypeNumericNameRejected() {
        // A numeric-only name is rejected on create...
        RelationTypeModel rtm = new RelationTypeModel();
        rtm.setName("12345");
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/12345")
                .request(MediaType.APPLICATION_JSON).put(Entity.json(rtm))) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        }

        // ...and on rename.
        RelationTypeModel legit = new RelationTypeModel();
        legit.setName("legit");
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/legit")
                .request(MediaType.APPLICATION_JSON).put(Entity.json(legit))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        }
        legit.setName("98765");
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/legit")
                .request(MediaType.APPLICATION_JSON).put(Entity.json(legit))) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        }
    }

    @Test
    public void testRelationTypeRenameCollisionAndSelfRename() {
        RelationTypeModel a = new RelationTypeModel();
        a.setName("typea");
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/typea")
                .request(MediaType.APPLICATION_JSON).put(Entity.json(a))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        }
        RelationTypeModel b = new RelationTypeModel();
        b.setName("typeb");
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/typeb")
                .request(MediaType.APPLICATION_JSON).put(Entity.json(b))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        }

        // Renaming typeb to a name already used by typea is rejected with 409
        b.setName("typea");
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/typeb")
                .request(MediaType.APPLICATION_JSON).put(Entity.json(b))) {
            assertEquals(Response.Status.CONFLICT.getStatusCode(), r.getStatus());
        }

        // Renaming typea to its own current name succeeds (200, not 409)
        a.setName("typea");
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/typea")
                .request(MediaType.APPLICATION_JSON).put(Entity.json(a))) {
            assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
        }
    }

    @Test
    public void relationTypeNonexistentTraditionTest() {
        // GET/PUT/DELETE against a tradition id that doesn't exist returns 404
        String badTradId = "10000";

        Response getResponse = jerseyTest.target("/tradition/" + badTradId + "/relationtype/foo")
                .request(MediaType.APPLICATION_JSON)
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), getResponse.getStatus());

        RelationTypeModel putBody = new RelationTypeModel();
        putBody.setName("foo");
        try (Response putResponse = jerseyTest.target("/tradition/" + badTradId + "/relationtype/foo")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(putBody))) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), putResponse.getStatus());
        }

        try (Response deleteResponse = jerseyTest.target("/tradition/" + badTradId + "/relationtype/foo")
                .request()
                .delete()) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), deleteResponse.getStatus());
        }
    }

    @Test
    public void testRelTypeDeleteByIdBlockedByExistingRelations() {
        // Make sure deletion guard checks relations by type name, not type ID
        String legeiAcute = readingLookup.getOrDefault("λέγει/1", "17");
        String legei = readingLookup.getOrDefault("λεγει/1", "17");

        RelationTypeModel rtm = new RelationTypeModel();
        rtm.setName("accents");
        rtm.setDescription("Readings are the same but for diacriticals");
        rtm.setIs_colocation(true);
        RelationTypeModel created;
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relationtype/accents")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(rtm))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
            created = jerseyResult.readEntity(RelationTypeModel.class);
        }
        String id = created.getId();
        assertNotNull(id);

        // Make a RELATED relationship of this type, so its "type" property carries the name
        // "accents".
        RelationModel newRel = new RelationModel();
        newRel.setSource(legeiAcute);
        newRel.setTarget(legei);
        newRel.setScope("tradition");
        newRel.setDisplayform("λέγει");
        newRel.setType("accents");
        newRel.setIs_significant("no");
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(newRel))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
            GraphModel result = jerseyResult.readEntity(new GenericType<>() {});
            assertEquals(2, result.getRelations().size());
        }

        // Deleting the type by its numeric id must still be blocked by the existing relations.
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relationtype/" + id)
                .request().delete()) {
            assertEquals(Response.Status.CONFLICT.getStatusCode(), jerseyResult.getStatus());
        }

        // Sanity check: it's still there and still addressable by id.
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/relationtype/" + id)
                .request(MediaType.APPLICATION_JSON).get()) {
            assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
            assertEquals("accents", jerseyResult.readEntity(RelationTypeModel.class).getName());
        }
    }

    @Test
    public void testRelTypeRenameBlockedWhileInUse() {
        String legeiAcute = readingLookup.getOrDefault("λέγει/1", "17");
        String legei = readingLookup.getOrDefault("λεγει/1", "17");

        RelationTypeModel rtm = new RelationTypeModel();
        rtm.setName("accents");
        rtm.setDescription("Readings are the same but for diacriticals");
        rtm.setIs_colocation(true);
        RelationTypeModel created;
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/accents")
                .request(MediaType.APPLICATION_JSON).put(Entity.json(rtm))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
            created = r.readEntity(RelationTypeModel.class);
        }
        RelationModel newRel = new RelationModel();
        newRel.setSource(legeiAcute);
        newRel.setTarget(legei);
        newRel.setScope("local");
        newRel.setType("accents");
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON).post(Entity.json(newRel))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        }

        // Renaming the in-use type (by name or by id) is refused, and nothing changes.
        rtm.setName("diacriticals");
        for (String ref : new String[] {"accents", created.getId()}) {
            try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/" + ref)
                    .request(MediaType.APPLICATION_JSON).put(Entity.json(rtm))) {
                assertEquals(Response.Status.CONFLICT.getStatusCode(), r.getStatus());
            }
        }
        RelationTypeModel unchanged = jerseyTest.target("/tradition/" + tradId + "/relationtype/" + created.getId())
                .request().get(RelationTypeModel.class);
        assertEquals("accents", unchanged.getName());
        assertEquals("Readings are the same but for diacriticals", unchanged.getDescription());

        // Updating other properties under the same name, while in use, still works.
        rtm.setName("accents");
        rtm.setDescription("Differences of accent only");
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/accents")
                .request(MediaType.APPLICATION_JSON).put(Entity.json(rtm))) {
            assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
            RelationTypeModel updated = r.readEntity(RelationTypeModel.class);
            assertEquals("accents", updated.getName());
            assertEquals("Differences of accent only", updated.getDescription());
        }
    }

    @Test
    public void testRelTypePutWithoutName() {
        RelationTypeModel rtm = new RelationTypeModel();
        rtm.setName("accents");
        rtm.setDescription("Readings are the same but for diacriticals");
        RelationTypeModel created;
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/accents")
                .request(MediaType.APPLICATION_JSON).put(Entity.json(rtm))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
            created = r.readEntity(RelationTypeModel.class);
        }

        // A body with no "name" key at all updates the other fields and leaves the name alone,
        // whether addressed by name or by id.
        String noName = "{\"description\": \"Accent differences only\", \"bindlevel\": 3}";
        for (String ref : new String[] {"accents", created.getId()}) {
            try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/" + ref)
                    .request(MediaType.APPLICATION_JSON).put(Entity.json(noName))) {
                assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
                RelationTypeModel updated = r.readEntity(RelationTypeModel.class);
                assertEquals("accents", updated.getName());
                assertEquals(created.getId(), updated.getId());
                assertEquals("Accent differences only", updated.getDescription());
                assertEquals(3, updated.getBindlevel());
            }
        }
        // Same with an explicit null name.
        String nullName = "{\"name\": null, \"description\": \"Accents\"}";
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/accents")
                .request(MediaType.APPLICATION_JSON).put(Entity.json(nullName))) {
            assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
            assertEquals("accents", r.readEntity(RelationTypeModel.class).getName());
        }
        List<RelationTypeModel> allRelTypes = jerseyTest.target("/tradition/" + tradId + "/relationtypes")
                .request().get(new GenericType<>() {});
        assertEquals(2, allRelTypes.size());

        // Creating a new type with no name in the body takes its name from the URL.
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/grammatical")
                .request(MediaType.APPLICATION_JSON).put(Entity.json("{\"description\": \"Grammar\"}"))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
            assertEquals("grammatical", r.readEntity(RelationTypeModel.class).getName());
        }
    }

    @Test
    public void testRelTypeCrossTraditionId() {
        RelationTypeModel rtm = new RelationTypeModel();
        rtm.setName("accents");
        RelationTypeModel created;
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/relationtype/accents")
                .request(MediaType.APPLICATION_JSON).put(Entity.json(rtm))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
            created = r.readEntity(RelationTypeModel.class);
        }
        Response jerseyResult = Util.createTraditionFromFileOrString(jerseyTest, "Other", "LR", "1",
                "src/TestFiles/john.csv", "csv");
        String otherId = Util.getValueFromJson(jerseyResult, "tradId");
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), jerseyTest
                .target("/tradition/" + otherId + "/relationtype/" + created.getId()).request().get().getStatus());
        try (Response r = jerseyTest.target("/tradition/" + otherId + "/relationtype/" + created.getId())
                .request().delete()) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), r.getStatus());
        }
        assertEquals("accents", jerseyTest.target("/tradition/" + tradId + "/relationtype/" + created.getId())
                .request().get(RelationTypeModel.class).getName());
    }

    @After
    public void tearDown() throws Exception {
//        db.shutdown();
    	GraphDatabaseServiceProvider.shutdown();
        jerseyTest.tearDown();
    }

}
