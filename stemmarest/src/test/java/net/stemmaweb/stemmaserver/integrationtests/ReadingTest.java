package net.stemmaweb.stemmaserver.integrationtests;

import java.util.*;
import java.util.stream.Collectors;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.model.*;
import net.stemmaweb.rest.*;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;

import net.stemmaweb.stemmaserver.Util;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.ClientResponse.Status;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import org.neo4j.test.TestGraphDatabaseFactory;

import static org.junit.Assert.*;

/**
 * 
 * Contains all tests for the api calls related to readings.
 * 
 * @author PSE FS 2015 Team2
 *
 */
public class ReadingTest {

    private String tradId;
    private String sectId;

    private String expectedWitnessA = "{\"text\":\"when april with his showers sweet with fruit the drought of march has pierced unto me the root\"}";
    private String expectedWitnessB = "{\"text\":\"when april his showers sweet with fruit the march of drought has pierced to the root\"}";
    private String expectedWitnessC = "{\"text\":\"when showers sweet with fruit to drought of march has pierced teh rood-of-the-world\"}";

    private GraphDatabaseService db;

    /*
     * JerseyTest is the test environment to Test api calls it provides a
     * grizzly http service
     */
    private JerseyTest jerseyTest;

    @Before
    public void setUp() throws Exception {

        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();

        /*
         * Populate the test database with the root node and a user with id 1
         */
        DatabaseService.createRootNode(db);
        Util.setupTestDB(db, "1");

        // Create a JerseyTestServer for the necessary REST API calls
        Root webResource = new Root();
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(webResource)
                .create();
        jerseyTest.setUp();

        /*
         * load a tradition to the test DB
         * and gets the generated id of the inserted tradition
         */
        ClientResponse jerseyResult = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/ReadingstestTradition.xml", "stemmaweb");
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        tradId = Util.getValueFromJson(jerseyResult, "tradId");
        List<SectionModel> testSections = jerseyTest.resource().path("/tradition/" + tradId + "/sections")
                .get(new GenericType<List<SectionModel>>() {});
        sectId = testSections.get(0).getId();
    }

    /**
     * Contains 29 readings at the beginning.
     *
     * @return a list of readings in the tradition
     */
    private List<ReadingModel> testNumberOfReadingsAndWitnesses(int number) {
        List<ReadingModel> listOfReadings = jerseyTest.resource()
                .path("/tradition/" + tradId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});
        assertEquals(number, listOfReadings.size());
        Response resp;

        resp = new Witness(tradId, "A").getWitnessAsText();
        assertEquals(expectedWitnessA, resp.getEntity());

        resp = new Witness(tradId, "B").getWitnessAsText();
        assertEquals(expectedWitnessB, resp.getEntity());

        resp = new Witness(tradId, "C").getWitnessAsText();
        assertEquals(expectedWitnessC, resp.getEntity());

        return listOfReadings;
    }

    @Test
    public void changeReadingPropertiesOnePropertyTest() {
        Node node;
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'showers'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            node = nodes.next();
            assertFalse(nodes.hasNext());

            KeyPropertyModel keyModel = new KeyPropertyModel();
            keyModel.setKey("text");
            keyModel.setProperty("snow");
            ReadingChangePropertyModel chgModel = new ReadingChangePropertyModel();
            List<KeyPropertyModel> models = new ArrayList<>();
            models.add(keyModel);
            chgModel.setProperties(models);

            ClientResponse response = jerseyTest
                    .resource()
                    .path("/reading/" + node.getId())
                    .type(MediaType.APPLICATION_JSON)
                    .put(ClientResponse.class, chgModel);

            assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());
            assertEquals("snow", node.getProperty("text"));

            String expectedWitnessA = "{\"text\":\"when april with his snow sweet with fruit the drought of march has pierced unto me the root\"}";
            Response resp = new Witness(tradId, "A").getWitnessAsText();
            assertEquals(expectedWitnessA, resp.getEntity());
            tx.success();
        }
    }

    @Test
    public void changeReadingPropertiesWrongDatatypeTest() {
        Long nodeid;
        try (Transaction tx = db.beginTx()) {
            nodeid = db.findNode(Nodes.READING, "text", "showers").getId();
            tx.success();
        }

        KeyPropertyModel keyModel = new KeyPropertyModel();
        keyModel.setKey("text");
        keyModel.setProperty("rainshowers");
        KeyPropertyModel keyModel2 = new KeyPropertyModel();
        keyModel2.setKey("join_next");
        keyModel2.setProperty("true");
        List<KeyPropertyModel> models = new ArrayList<>();
        models.add(keyModel);
        models.add(keyModel2);
        ReadingChangePropertyModel chgModel = new ReadingChangePropertyModel();
        chgModel.setProperties(models);
        ClientResponse response = jerseyTest
                .resource().path("/reading/" + nodeid)
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, chgModel);
        assertEquals(Status.BAD_REQUEST.getStatusCode(), response.getStatus());

        // Check that the node text didn't change
        try (Transaction tx = db.beginTx()) {
            Node node = db.getNodeById(nodeid);
            assertEquals("showers", node.getProperty("text").toString());
            tx.success();
        }
    }

    @Test
    public void changeReadingPropertiesMultiplePropertiesTest() {

        try (Transaction tx = db.beginTx()) {
            Node node = db.findNode(Nodes.READING, "text", "showers");

            KeyPropertyModel keyModel = new KeyPropertyModel();
            keyModel.setKey("text");
            keyModel.setProperty("snow");
            ReadingChangePropertyModel chgModel = new ReadingChangePropertyModel();
            List<KeyPropertyModel> models = new ArrayList<>();
            models.add(keyModel);
            KeyPropertyModel keyModel2 = new KeyPropertyModel();
            keyModel2.setKey("language");
            keyModel2.setProperty("hebrew");
            models.add(keyModel2);
            KeyPropertyModel keyModel3 = new KeyPropertyModel();
            keyModel3.setKey("is_lemma");
            keyModel3.setProperty(true);
            models.add(keyModel3);
            chgModel.setProperties(models);

            ClientResponse response = jerseyTest
                    .resource()
                    .path("/reading/" + node.getId())
                    .type(MediaType.APPLICATION_JSON)
                    .put(ClientResponse.class, chgModel);

            assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());
            assertEquals("snow", node.getProperty("text"));
            assertEquals("hebrew", node.getProperty("language"));
            assertTrue(Boolean.valueOf(node.getProperty("is_lemma").toString()));
            String expectedWitnessA = "{\"text\":\"when april with his snow sweet with fruit the drought of march has pierced unto me the root\"}";
            Response resp = new Witness(tradId, "A").getWitnessAsText();
            assertEquals(expectedWitnessA, resp.getEntity());
            tx.success();
        }
    }

    @Test
    public void changeReadingPropertiesPropertyKeyNotFoundTest() {
        Node node;
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'showers'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            node = nodes.next();
            assertFalse(nodes.hasNext());

            KeyPropertyModel keyModel = new KeyPropertyModel();
            keyModel.setKey("test");
            keyModel.setProperty("snow");
            ReadingChangePropertyModel chgModel = new ReadingChangePropertyModel();
            List<KeyPropertyModel> models = new ArrayList<>();
            models.add(keyModel);
            chgModel.setProperties(models);
            ClientResponse response = jerseyTest
                    .resource()
                    .path("/reading/" + node.getId())
                    .type(MediaType.APPLICATION_JSON)
                    .put(ClientResponse.class, chgModel);

            assertEquals(Status.BAD_REQUEST.getStatusCode(),
                    response.getStatusInfo().getStatusCode());
            assertEquals("Reading has no such property 'test'",
                    Util.getValueFromJson(response, "error"));
            tx.success();
        }
    }

    @Test
    public void getReadingJsonTest() throws JsonProcessingException {
        String expected = "{\"id\":\"16\",\"is_common\":true,\"is_end\":false,\"is_lacuna\":false,\"is_lemma\":false,\"is_nonsense\":false,\"is_ph\":false,\"is_start\":false,\"join_next\":false,\"join_prior\":false,\"language\":\"Default\",\"rank\":14,\"text\":\"has\"}";

        ClientResponse resp = jerseyTest.resource()
                .path("/reading/" + 16)
                .type(MediaType.APPLICATION_JSON).get(ClientResponse.class);

        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(Include.NON_NULL);
        String json = mapper.writeValueAsString(resp
                .getEntity(ReadingModel.class));

        assertEquals(expected, json);
    }

    @Test
    public void getReadingReadingModelTest() {
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'showers'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node node = nodes.next();
            assertFalse(nodes.hasNext());

            ReadingModel expectedReadingModel;
            expectedReadingModel = new ReadingModel(node);

            ReadingModel readingModel = jerseyTest.resource()
                    .path("/reading/" + node.getId())
                    .type(MediaType.APPLICATION_JSON).get(ReadingModel.class);

            assertTrue(readingModel != null);
            assertEquals(expectedReadingModel.getRank(), readingModel.getRank());
            assertEquals(expectedReadingModel.getText(), readingModel.getText());
            tx.success();
        }
    }

    @Test
    public void getReadingWithFalseIdTest() {
        ClientResponse response = jerseyTest.resource()
                .path("/reading/" + 200)
                .type(MediaType.APPLICATION_JSON).get(ClientResponse.class);

        assertEquals(Status.NO_CONTENT.getStatusCode(),
                response.getStatusInfo().getStatusCode());
    }

    @Test
    public void duplicateTest() {
        try (Transaction tx = db.beginTx()) {
            Node firstNode = db.findNode(Nodes.READING, "text", "showers");
            Node secondNode = db.findNode(Nodes.READING, "text", "sweet");

            String witnessA = jerseyTest.resource().path("/tradition/" + tradId + "/witness/A/text")
                    .get(String.class);
            String witnessB = jerseyTest.resource().path("/tradition/" + tradId + "/witness/B/text")
                    .get(String.class);
            String witnessC = jerseyTest.resource().path("/tradition/" + tradId + "/witness/C/text")
                    .get(String.class);

            // get existing relationships
            List<RelationshipModel> allRels = jerseyTest.resource().path("/tradition/" + tradId + "/relationships")
                    .get(new GenericType<List<RelationshipModel>>() {});

            // duplicate reading
            List<String> rdgs = new ArrayList<>();
            rdgs.add(String.valueOf(firstNode.getId()));
            rdgs.add(String.valueOf(secondNode.getId()));
            DuplicateModel jsonPayload = new DuplicateModel();
            jsonPayload.setReadings(rdgs);
            jsonPayload.setWitnesses(new ArrayList<>(Arrays.asList("A", "B")));

            ClientResponse response = jerseyTest.resource()
                    .path("/reading/" + firstNode.getId() + "/duplicate")
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, jsonPayload);

            // Check that no relationships were harmed by this duplication
            assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());
            List<RelationshipModel> ourRels = jerseyTest.resource().path("/tradition/" + tradId + "/relationships")
                    .get(new GenericType<List<RelationshipModel>>() {});
            assertEquals(allRels.size(), ourRels.size());
            for (RelationshipModel rm : allRels) {
                long found = ourRels.stream()
                        .filter(x -> x.getSource().equals(rm.getSource()) && x.getTarget().equals(rm.getTarget())
                                && x.getType().equals(rm.getType())).count();
                assertEquals(1L, found);
            }

            GraphModel readingsAndRelationshipsModel = response.getEntity(GraphModel.class);
            assertEquals(0, readingsAndRelationshipsModel.getRelationships().size());
            assertEquals(witnessA, jerseyTest.resource().path("/tradition/" + tradId + "/witness/A/text")
                    .get(String.class));
            assertEquals(witnessB, jerseyTest.resource().path("/tradition/" + tradId + "/witness/B/text")
                    .get(String.class));
            assertEquals(witnessC, jerseyTest.resource().path("/tradition/" + tradId + "/witness/C/text")
                    .get(String.class));

            testNumberOfReadingsAndWitnesses(31);

            // check that orig_reading was set in the model
            List<ReadingModel> readingModels = new ArrayList<>(readingsAndRelationshipsModel.getReadings());
            ReadingModel showersModel;
            ReadingModel sweetModel;
            if (readingModels.get(0).getText().equals("showers")) {
                showersModel = readingModels.get(0);
                sweetModel = readingModels.get(1);
            } else {
                showersModel = readingModels.get(1);
                sweetModel = readingModels.get(0);
            }
            assertEquals(String.valueOf(firstNode.getId()), showersModel.getOrig_reading());
            assertEquals(String.valueOf(secondNode.getId()), sweetModel.getOrig_reading());

            // check that the nodes exist, and that orig_reading was not set on the node
            ResourceIterator<Node> showers = db.findNodes(Nodes.READING, "text", "showers");
            Node duplicatedShowers = null;
            while (showers.hasNext()) {
                Node n = showers.next();
                if (!n.equals(firstNode))
                    duplicatedShowers = n;
            }
            assertNotNull(duplicatedShowers);
            assertFalse(duplicatedShowers.hasProperty("orig_reading"));

            ResourceIterator<Node> sweet = db.findNodes(Nodes.READING, "text", "sweet");
            Node duplicatedSweet = null;
            while (sweet.hasNext()) {
                Node n = sweet.next();
                if (!n.equals(secondNode))
                    duplicatedSweet = n;
            }
            assertNotNull(duplicatedSweet);
            assertFalse(duplicatedSweet.hasProperty("orig_reading"));

            // compare original and duplicated
            Iterable<String> keys = firstNode.getPropertyKeys();
            for (String key : keys) {
                String val1 = firstNode.getProperty(key).toString();
                String val2 = duplicatedShowers.getProperty(key).toString();
                assertEquals(val1, val2);
            }

            keys = secondNode.getPropertyKeys();
            for (String key : keys) {
                String val1 = secondNode.getProperty(key).toString();
                String val2 = duplicatedSweet.getProperty(key).toString();
                assertEquals(val1, val2);
            }
            tx.success();
        }
    }

    @Test
    public void duplicateWithDuplicateForTwoWitnessesTest() {
        try (Transaction tx = db.beginTx()) {
            // get all relationships
            List<RelationshipModel> allRels = jerseyTest.resource().path("/tradition/" + tradId + "/relationships")
                    .get(new GenericType<List<RelationshipModel>>() {});
            // add a relationship between droughts
            List<Node> droughts = db.findNodes(Nodes.READING, "text", "drought")
                    .stream().collect(Collectors.toList());
            assertEquals(2, droughts.size());
            RelationshipModel drel = new RelationshipModel();
            drel.setSource(String.valueOf(droughts.get(1).getId()));
            drel.setTarget(String.valueOf(droughts.get(0).getId()));
            drel.setType("transposition");
            drel.setScope("local");
            ClientResponse response = jerseyTest.resource().path("/tradition/" + tradId + "/relation")
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, drel);
            assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

            // duplicate reading
            Node node = db.findNode(Nodes.READING, "text", "of");
            String jsonPayload = "{\"readings\":[" + node.getId() + "], \"witnesses\":[\"A\",\"C\" ]}";
            response = jerseyTest.resource().path("/reading/" + node.getId() + "/duplicate")
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, jsonPayload);

            assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());

            // check that now-invalid relationships are gone
            GraphModel readingsAndRelationshipsModel = response.getEntity(GraphModel.class);
            ReadingModel firstWord = (ReadingModel) readingsAndRelationshipsModel.getReadings().toArray()[0];
            assertEquals("of", firstWord.getText());
            assertEquals(2, readingsAndRelationshipsModel.getRelationships().size());
            List<RelationshipModel> ourRels = jerseyTest.resource().path("/tradition/" + tradId + "/relationships")
                    .get(new GenericType<List<RelationshipModel>>() {});
            assertEquals(allRels.size() - 1, ourRels.size());
            for (RelationshipModel del : readingsAndRelationshipsModel.getRelationships()) {
                for (RelationshipModel kept : ourRels) {
                    assertNotEquals(kept.getSource(), del.getSource());
                    assertNotEquals(kept.getTarget(), del.getTarget());
                }
            }

            testNumberOfReadingsAndWitnesses(30);
            List<Node> ofNodes = db.findNodes(Nodes.READING, "text", "of")
                    .stream().collect(Collectors.toList());
            assertEquals(2, ofNodes.size());
            Node duplicatedOf = null;
            for (Node n : ofNodes)
                if (!node.equals(n))
                    duplicatedOf = n;
            assertNotNull(duplicatedOf);

            // test witnesses and number of paths
            int numberOfPaths = 0;
            for (Relationship incoming : node.getRelationships(ERelations.SEQUENCE,
                    Direction.INCOMING)) {
                assertEquals("B", ((String[]) incoming.getProperty("witnesses"))[0]);
                numberOfPaths++;
            }
            assertEquals(1, numberOfPaths);

            numberOfPaths = 0;
            for (Relationship incoming : duplicatedOf.getRelationships(ERelations.SEQUENCE,
                    Direction.INCOMING)) {
                assertEquals("A", ((String[]) incoming.getProperty("witnesses"))[0]);
                assertEquals("C", ((String[]) incoming.getProperty("witnesses"))[1]);
                numberOfPaths++;
            }
            assertEquals(1, numberOfPaths);

            numberOfPaths = 0;
            for (Relationship outgoing : node.getRelationships(ERelations.SEQUENCE,
                    Direction.OUTGOING)) {
                assertEquals("B", ((String[]) outgoing.getProperty("witnesses"))[0]);
                numberOfPaths++;
            }
            assertEquals(1, numberOfPaths);

            numberOfPaths = 0;
            for (Relationship outgoing : duplicatedOf.getRelationships(ERelations.SEQUENCE,
                    Direction.OUTGOING)) {
                assertEquals("A", ((String[]) outgoing.getProperty("witnesses"))[0]);
                assertEquals("C", ((String[]) outgoing.getProperty("witnesses"))[1]);
                numberOfPaths++;
            }
            assertEquals(1, numberOfPaths);

            // compare original and duplicated
            Iterable<String> keys = node.getPropertyKeys();
            for (String key : keys) {
                String val1 = node.getProperty(key).toString();
                String val2 = duplicatedOf.getProperty(key).toString();
                assertEquals(val1, val2);
            }
            tx.success();
        }
    }

    @Test
    public void duplicateWithDuplicateForOneWitnessTest() {
        try (Transaction tx = db.beginTx()) {
            Node originalOf = db.findNode(Nodes.READING, "text", "of");
            // get all relationships as baseline
            List<RelationshipModel> origRelations = jerseyTest.resource()
                    .path("/tradition/" + tradId + "/relationships")
                    .get(new GenericType<List<RelationshipModel>>() {});

            // duplicate reading
            String jsonPayload = "{\"readings\":[" + originalOf.getId() + "], \"witnesses\":[\"B\"]}";
            ClientResponse response = jerseyTest.resource().path("/reading/" + originalOf.getId() + "/duplicate")
                    .type(MediaType.APPLICATION_JSON).post(ClientResponse.class, jsonPayload);

            assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());

            GraphModel readingsAndRelationshipsModel = response.getEntity(GraphModel.class);
            ReadingModel firstWord = (ReadingModel) readingsAndRelationshipsModel.getReadings().toArray()[0];
            assertEquals("of", firstWord.getText());
            assertEquals(1, readingsAndRelationshipsModel.getRelationships().size());
            List<RelationshipModel> nowRelations = jerseyTest.resource()
                    .path("/tradition/" + tradId + "/relationships")
                    .get(new GenericType<List<RelationshipModel>>() {});
            assertEquals(origRelations.size() - 1, nowRelations.size());
            for (RelationshipModel nr : nowRelations) assertNotEquals("march", nr.getReading_a());

            testNumberOfReadingsAndWitnesses(30);

            Node duplicatedOf = db.getNodeById(Long.valueOf(firstWord.getId()));
            // test witnesses and number of paths
            int numberOfPaths = 0;
            for (Relationship incoming : originalOf.getRelationships(ERelations.SEQUENCE,
                    Direction.INCOMING)) {
                assertEquals("A", ((String[]) incoming.getProperty("witnesses"))[0]);
                assertEquals("C", ((String[]) incoming.getProperty("witnesses"))[1]);
                numberOfPaths++;
            }
            assertEquals(1, numberOfPaths);

            numberOfPaths = 0;
            for (Relationship incoming : duplicatedOf.getRelationships(ERelations.SEQUENCE,
                    Direction.INCOMING)) {
                assertEquals("B", ((String[]) incoming.getProperty("witnesses"))[0]);
                numberOfPaths++;
            }
            assertEquals(1, numberOfPaths);

            numberOfPaths = 0;
            for (Relationship outgoing : originalOf.getRelationships(ERelations.SEQUENCE,
                    Direction.OUTGOING)) {
                assertEquals("A", ((String[]) outgoing.getProperty("witnesses"))[0]);
                assertEquals("C", ((String[]) outgoing.getProperty("witnesses"))[1]);
                numberOfPaths++;
            }
            assertEquals(1, numberOfPaths);

            numberOfPaths = 0;
            for (Relationship outgoing : duplicatedOf.getRelationships(ERelations.SEQUENCE,
                    Direction.OUTGOING)) {
                assertEquals("B", ((String[]) outgoing.getProperty("witnesses"))[0]);
                numberOfPaths++;
            }
            assertEquals(1, numberOfPaths);

            // compare original and duplicated
            Iterable<String> keys = originalOf.getPropertyKeys();
            for (String key : keys) {
                String val1 = originalOf.getProperty(key).toString();
                String val2 = duplicatedOf.getProperty(key).toString();
                assertEquals(val1, val2);
            }
            tx.success();
        }
    }

    @Test
    public void duplicateWitnessCrossingTest() {
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'of'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node node = nodes.next();
            assertFalse(nodes.hasNext());

            // duplicate reading
            String jsonPayload = "{\"readings\":[" + node.getId()
                    + "], \"witnesses\":[\"A\",\"B\" ]}";
            ClientResponse response = jerseyTest.resource()
                    .path("/reading/" + node.getId() + "/duplicate")
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, jsonPayload);

            assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());

            testNumberOfReadingsAndWitnesses(30);

            result = db.execute("match (w:READING {text:'of'}) return w");
            nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node originalOf = nodes.next();
            assertTrue(nodes.hasNext());
            Node duplicatedOf = nodes.next();
            assertFalse(nodes.hasNext());

            // compare original and duplicated
            Iterable<String> keys = originalOf.getPropertyKeys();
            for (String key : keys) {
                String val1 = originalOf.getProperty(key).toString();
                String val2 = duplicatedOf.getProperty(key).toString();
                assertEquals(val1, val2);
            }
            tx.success();
        }
    }

    @Test
    public void duplicateWithNoWitnessesInJSONTest() {
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'rood-of-the-world'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node firstNode = nodes.next();
            assertFalse(nodes.hasNext());

            // duplicate reading
            String jsonPayload = "{\"readings\":[" + firstNode.getId()
                    + "], \"witnesses\":[]}";
            ClientResponse response = jerseyTest.resource()
                    .path("/reading/" + firstNode.getId() + "/duplicate")
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, jsonPayload);

            assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                    response.getStatusInfo().getStatusCode());
            assertEquals(
                    "The witness list has to contain at least one witness",
                    Util.getValueFromJson(response, "error"));
            tx.success();
        }
    }

    @Test
    public void duplicateWithOnlyOneWitnessTest() {
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'rood-of-the-world'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node firstNode = nodes.next();
            assertFalse(nodes.hasNext());

            // duplicate reading
            String jsonPayload = "{\"readings\":[" + firstNode.getId()
                    + "], \"witnesses\":[\"C\"]}";
            ClientResponse response = jerseyTest.resource()
                    .path("/reading/" + firstNode.getId() + "/duplicate")
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, jsonPayload);

            assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                    response.getStatusInfo().getStatusCode());
            assertEquals("The reading has to be in at least two witnesses",
                    Util.getValueFromJson(response, "error"));
            tx.success();
        }
    }

    @Test
    public void duplicateWithNotAllowedWitnessesTest() {
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'root'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node firstNode = nodes.next();
            assertFalse(nodes.hasNext());

            // duplicate reading
            String jsonPayload = "{\"readings\":[" + firstNode.getId()
                    + "], \"witnesses\":[\"C\"]}";
            ClientResponse response = jerseyTest.resource()
                    .path("/reading/" + firstNode.getId() + "/duplicate")
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, jsonPayload);

            assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                    response.getStatusInfo().getStatusCode());
            assertEquals(
                    "The reading has to be in the witnesses to be duplicated",
                    Util.getValueFromJson(response, "error"));
            tx.success();
        }
    }

    @Test
    public void mergeReadingsTest() {
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'fruit'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node firstNode = nodes.next();
            assertTrue(nodes.hasNext());
            Node secondNode = nodes.next();
            assertFalse(nodes.hasNext());

            // merge readings
            ClientResponse response = jerseyTest
                    .resource()
                    .path("/reading/" + firstNode.getId()
                            + "/merge/" + secondNode.getId())
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class);

            assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());

            // should contain one reading less now
            testNumberOfReadingsAndWitnesses(28);

            result = db.execute("match (w:READING {text:'fruit'}) return w");
            nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node stayingNode = nodes.next();
            assertFalse(nodes.hasNext());

            // test witnesses
            Relationship incoming = stayingNode.getSingleRelationship(
                    ERelations.SEQUENCE, Direction.INCOMING);
            assertEquals("A", ((String[]) incoming.getProperty("witnesses"))[0]);
            assertEquals("B", ((String[]) incoming.getProperty("witnesses"))[1]);
            assertEquals("C", ((String[]) incoming.getProperty("witnesses"))[2]);

            int counter = 0;
            for (Relationship outgoing : stayingNode.getRelationships(
                    ERelations.SEQUENCE, Direction.OUTGOING)) {
                counter++;
                if (outgoing.getOtherNode(stayingNode).getProperty("text").equals("the")) {
                    assertEquals("A", ((String[]) outgoing.getProperty("witnesses"))[0]);
                    assertEquals("B", ((String[]) outgoing.getProperty("witnesses"))[1]);
                }
                if (outgoing.getOtherNode(stayingNode).getProperty("text").equals("to")) {
                    assertEquals("C", ((String[]) outgoing.getProperty("witnesses"))[0]);
                }
            }
            assertEquals(2, counter);

            // test relationships
            int numberOfRelationships = 0;
            for (Relationship rel : stayingNode.getRelationships(ERelations.RELATED)) {
                numberOfRelationships++;
                // test that relationships have been copied
                if (rel.getOtherNode(stayingNode).getProperty("text").equals("when")) {
                    assertEquals("grammatical", rel.getProperty("type"));
                    assertEquals("when", rel.getOtherNode(stayingNode).getProperty("text"));
                }
                if (rel.getOtherNode(stayingNode).getProperty("text").equals("the root")) {
                    assertEquals("transposition", rel.getProperty("type"));
                    assertEquals("the root", rel.getOtherNode(stayingNode).getProperty("text"));
                }

                // test that relationship between the two readings has been
                // deleted
                assertTrue(rel.getOtherNode(stayingNode) != stayingNode);
            }
            assertEquals(2, numberOfRelationships);
            tx.success();
        }
    }

    @Test
    public void mergeReadingsGetsCyclicTest() {
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'drought'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node firstNode = nodes.next();
            assertTrue(nodes.hasNext());
            Node secondNode = nodes.next();
            assertFalse(nodes.hasNext());

            // merge readings
            ClientResponse response = jerseyTest
                    .resource()
                    .path("/reading/" + firstNode.getId()
                            + "/merge/" + secondNode.getId())
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class);

            assertEquals(Status.CONFLICT.getStatusCode(),
                    response.getStatusInfo().getStatusCode());
            assertEquals("Readings to be merged would make the graph cyclic",
                    Util.getValueFromJson(response, "error"));

            testNumberOfReadingsAndWitnesses(29);

            result = db.execute("match (w:READING {text:'drought'}) return w");
            nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            nodes.next();    // first Node
            assertTrue(nodes.hasNext());
            nodes.next();    // second Node
            assertFalse(nodes.hasNext());
            tx.success();
        }
    }

    @Test
    public void mergeReadingsGetsCyclicWithNodesFarApartTest() {
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'to'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node firstNode = nodes.next();
            assertTrue(nodes.hasNext());
            Node secondNode = nodes.next();
            assertFalse(nodes.hasNext());

            // merge readings
            ClientResponse response = jerseyTest
                    .resource()
                    .path("/reading/" + firstNode.getId()
                            + "/merge/" + secondNode.getId())
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class);

            assertEquals(Status.CONFLICT.getStatusCode(),
                    response.getStatusInfo().getStatusCode());
            assertEquals("Readings to be merged would make the graph cyclic",
                    Util.getValueFromJson(response, "error"));

            testNumberOfReadingsAndWitnesses(29);

            result = db.execute("match (w:READING {text:'to'}) return w");
            nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            nodes.next();    // first node
            assertTrue(nodes.hasNext());
            nodes.next();    // second node
            assertFalse(nodes.hasNext());
            tx.success();
        }
    }

    @Test
    public void mergeReadingsWithClassTwoRelationshipsTest() {
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'march'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node firstNode = nodes.next();
            assertTrue(nodes.hasNext());
            Node secondNode = nodes.next();
            assertFalse(nodes.hasNext());

            // merge readings
            ClientResponse response = jerseyTest
                    .resource()
                    .path("/reading/" + firstNode.getId()
                            + "/merge/" + secondNode.getId())
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class);

            assertEquals(Status.CONFLICT.getStatusCode(),
                    response.getStatusInfo().getStatusCode());
            assertEquals(
                    "Readings to be merged cannot contain cross-location relationships",
                    Util.getValueFromJson(response, "error"));

            testNumberOfReadingsAndWitnesses(29);

            result = db.execute("match (w:READING {text:'march'}) return w");
            nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            nodes.next();    // first node
            assertTrue(nodes.hasNext());
            nodes.next();    // second node
            assertFalse(nodes.hasNext());
            tx.success();
        }
    }

    @Test
    public void mergeReadingsWithDifferentTextTest() {
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'showers'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node firstNode = nodes.next();

            result = db.execute("match (w:READING {text:'sweet'}) return w");
            nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node secondNode = nodes.next();

            // merge readings
            ClientResponse response = jerseyTest
                    .resource()
                    .path("/reading/" + firstNode.getId()
                            + "/merge/" + secondNode.getId())
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class);

            assertEquals(Status.CONFLICT.getStatusCode(),
                    response.getStatusInfo().getStatusCode());
//            assertEquals("Readings to be merged do not contain the same text",
//                    response.getEntity(String.class));
            //TODO (SK 20151001: decide if this test is still necessary;
            //                   if so, modify it, otherwise we can remove it.
            assertEquals("Readings to be merged would make the graph cyclic",
                    Util.getValueFromJson(response, "error"));
            tx.success();
        }
    }

    @Test
    public void splitReadingTest() {
        Node node;
        Result result;
        Iterator<Node> nodes;
        try (Transaction tx = db.beginTx()) {
            node = db.findNode(Nodes.READING, "text", "the root");
            assertTrue(node.hasRelationship(ERelations.RELATED));

            // delete relationship, so that splitting is possible
            node.getSingleRelationship(ERelations.RELATED,
                    Direction.INCOMING).delete();

            assertFalse(node.hasRelationship(ERelations.RELATED));
            tx.success();
        }

        // split reading
        ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
        readingBoundaryModel.setCharacter(" ");
        ClientResponse response = jerseyTest
                .resource()
                .path("/reading/" + node.getId()
                        + "/split/0")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, readingBoundaryModel);

        assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());

        GraphModel readingsAndRelationshipsModel = response
                .getEntity(GraphModel.class);
        HashSet<String> rdgWords = new HashSet<>();
        readingsAndRelationshipsModel.getReadings().forEach(x -> rdgWords.add(x.getText()));
        assertTrue(rdgWords.contains("the"));
        assertTrue(rdgWords.contains("root"));
        assertEquals(1, readingsAndRelationshipsModel.getRelationships()
                .size());

        try (Transaction tx = db.beginTx()) {
            result = db.execute("match (w:READING {text:'the'}) return w");
            nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            nodes.next();
            assertTrue(nodes.hasNext());
            Node the2 = nodes.next();
            assertTrue(nodes.hasNext());
            Node the3 = nodes.next();
            assertFalse(nodes.hasNext());

            assertEquals((long) 17, the2.getProperty("rank"));
            assertEquals((long) 17, the3.getProperty("rank"));

            result = db.execute("match (w:READING {text:'root'}) return w");
            nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node root1 = nodes.next();
            assertTrue(nodes.hasNext());
            Node root2 = nodes.next();
            assertFalse(nodes.hasNext());

            assertEquals((long) 18, root1.getProperty("rank"));
            assertEquals((long) 18, root2.getProperty("rank"));

            // should contain one reading more now
            testNumberOfReadingsAndWitnesses(30);
            tx.success();
        }
    }

    @Test
    public void splitReadingWithOtherSeparatorAndMultipleWordsTest() {
        try (Transaction tx = db.beginTx()) {

            Result result = db.execute("match (w:READING {text:'rood-of-the-world'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node node = nodes.next();
            assertFalse(nodes.hasNext());

            // split reading
            ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
            readingBoundaryModel.setCharacter("-");
            ClientResponse response = jerseyTest
                    .resource()
                    .path("/reading/" + node.getId()
                            + "/split/0")
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, readingBoundaryModel);

            assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());

            expectedWitnessC = "{\"text\":\"when showers sweet with fruit to drought of march has pierced teh rood of the world\"}";

            testNumberOfReadingsAndWitnesses(32);
            tx.success();
        }
    }

    @Test
    public void splitReadingWithSlashAsSeparatorTest() {
        // prepare the database for the test
        try (Transaction tx = db.beginTx()) {
            Node node = db.findNode(Nodes.READING, "text", "rood-of-the-world");
            assertNotNull(node);
            node.setProperty("text", "rood/of/the/world");
            tx.success();
        }

        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'rood/of/the/world'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node node = nodes.next();
            assertFalse(nodes.hasNext());

            // split reading
            ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
            readingBoundaryModel.setCharacter("/");
            ClientResponse response = jerseyTest
                    .resource()
                    .path("/reading/" + node.getId()
                            + "/split/0")
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, readingBoundaryModel);

            assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());

            expectedWitnessC = "{\"text\":\"when showers sweet with fruit to drought of march has pierced teh rood of the world\"}";

            testNumberOfReadingsAndWitnesses(32);
            tx.success();
        }
    }

    @Test
    public void splitReadingWithOtherSeparatorAndMultipleWordsAndIndexTest() {
        try (Transaction tx = db.beginTx()) {

            Result result = db.execute("match (w:READING {text:'rood-of-the-world'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node node = nodes.next();
            assertFalse(nodes.hasNext());

            // split reading
            ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
            readingBoundaryModel.setCharacter("-");
            ClientResponse response = jerseyTest
                    .resource()
                    .path("/reading/" + node.getId()
                            + "/split/4")
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, readingBoundaryModel);

            assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());

            expectedWitnessC = "{\"text\":\"when showers sweet with fruit to drought of march has pierced teh rood of-the-world\"}";

            testNumberOfReadingsAndWitnesses(30);
            tx.success();
        }
    }

    @Test
    public void splitReadingWithLongSeparatorAndIndexTest() {
        try (Transaction tx = db.beginTx()) {

            Result result = db.execute("match (w:READING {text:'rood-of-the-world'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node node = nodes.next();
            assertFalse(nodes.hasNext());

            // split reading
            ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
            readingBoundaryModel.setCharacter("-of-");
            ClientResponse response = jerseyTest
                    .resource()
                    .path("/reading/" + node.getId()
                            + "/split/4")
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, readingBoundaryModel);

            assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());

            expectedWitnessC = "{\"text\":\"when showers sweet with fruit to drought of march has pierced teh rood the-world\"}";

            testNumberOfReadingsAndWitnesses(30);
            tx.success();
        }
    }


    @Test
    public void splitReadingWithNotExistingSeparatorInWordTest() {
        // prepare the database for the test
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'rood-of-the-world'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node node = nodes.next();
            assertFalse(nodes.hasNext());

            // split reading
            ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
            readingBoundaryModel.setCharacter("/");
            ClientResponse response = jerseyTest
                    .resource()
                    .path("/reading/" + node.getId()
                            + "/split/2")
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, readingBoundaryModel);

            assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                    response.getStatusInfo().getStatusCode());
            assertEquals("no such separator exists",
                    Util.getValueFromJson(response, "error"));
            tx.success();
        }
    }

    @Test
    public void splitReadingWithNotExistingSeparatorInIndexTest() {
        // prepare the database for the test
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'root'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node node = nodes.next();
            assertFalse(nodes.hasNext());

            // split reading
            ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
            readingBoundaryModel.setCharacter("t");
            ClientResponse response = jerseyTest
                    .resource()
                    .path("/reading/" + node.getId()
                            + "/split/2")
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, readingBoundaryModel);

            assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                    response.getStatusInfo().getStatusCode());
            assertEquals("The separator does not appear in the index location in the text",
                    Util.getValueFromJson(response, "error"));
            tx.success();
        }
    }

    @Test
    public void splitReadingWithExistingSeparatorInIndexTest() {
        // prepare the database for the test
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'root'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node node = nodes.next();
            assertFalse(nodes.hasNext());

            // split reading
            ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
            readingBoundaryModel.setCharacter("oo");
            ClientResponse response = jerseyTest
                    .resource()
                    .path("/reading/" + node.getId()
                            + "/split/1")
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, readingBoundaryModel);


            assertEquals(Status.OK.getStatusCode(),
                    response.getStatusInfo().getStatusCode());
            expectedWitnessA = "{\"text\":\"when april with his showers sweet with fruit the drought of march has pierced unto me the r t\"}";

            testNumberOfReadingsAndWitnesses(30);
            tx.success();
        }
    }

    @Test
    public void splitReadingWithExistingSeparatorInIndexOneCharTest() {
        // prepare the database for the test
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'root'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node node = nodes.next();
            assertFalse(nodes.hasNext());

            // split reading
            ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
            readingBoundaryModel.setCharacter("o");
            ClientResponse response = jerseyTest
                    .resource()
                    .path("/reading/" + node.getId()
                            + "/split/1")
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, readingBoundaryModel);


            assertEquals(Status.OK.getStatusCode(),
                    response.getStatusInfo().getStatusCode());
            expectedWitnessA = "{\"text\":\"when april with his showers sweet with fruit the drought of march has pierced unto me the r ot\"}";

            testNumberOfReadingsAndWitnesses(30);
            tx.success();
        }
    }

    @Test
    public void splitReadingWithQuotesAsSeparatorTest() {
        // prepare the database for the test
        try (Transaction tx = db.beginTx()) {
            Node node = db.findNode(Nodes.READING, "text", "rood-of-the-world");
            assertNotNull(node);
            node.setProperty("text", "rood\"of\"the\"world");
            tx.success();
        }

        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'rood\"of\"the\"world'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node node = nodes.next();
            assertFalse(nodes.hasNext());

            // split reading
            ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
            readingBoundaryModel.setCharacter("\"");
            ClientResponse response = jerseyTest
                    .resource()
                    .path("/reading/" + node.getId()
                            + "/split/0")
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, readingBoundaryModel);

            assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());

            expectedWitnessC = "{\"text\":\"when showers sweet with fruit to drought of march has pierced teh rood of the world\"}";

            testNumberOfReadingsAndWitnesses(32);
            tx.success();
        }
    }

    @Test
    public void splitReadingWithWrongIndexTest() {
        try (Transaction tx = db.beginTx()) {

            Result result = db.execute("match (w:READING {text:'root'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node node = nodes.next();
            assertFalse(nodes.hasNext());

            // split reading
            ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
            readingBoundaryModel.setCharacter("");
            ClientResponse response = jerseyTest
                    .resource()
                    .path("/reading/" + node.getId()
                            + "/split/7")
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, readingBoundaryModel);

            assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                    response.getStatusInfo().getStatusCode());
            assertEquals("The index must be smaller than the text length",
                    Util.getValueFromJson(response, "error"));

            testNumberOfReadingsAndWitnesses(29);
            tx.success();
        }
    }

    @Test
    public void splitReadingWithRelationshipTest() {
        try (Transaction tx = db.beginTx()) {

            Result result = db.execute("match (w:READING {text:'the root'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node node = nodes.next();
            assertFalse(nodes.hasNext());

            // split reading
            ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
            readingBoundaryModel.setCharacter(" ");
            ClientResponse response = jerseyTest
                    .resource()
                    .path("/reading/" + node.getId() + "/split/0")
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, readingBoundaryModel);

            assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                    response.getStatusInfo().getStatusCode());
            assertEquals(
                    "A reading to be split cannot be part of any relationship",
                    Util.getValueFromJson(response, "error"));

            testNumberOfReadingsAndWitnesses(29);
            tx.success();
        }
    }

    /**
     * tests the splitting of a reading when there is no rank-gap after it
     * should return error
     * (this test isn't necessary anymore, since we now perform a recalculation
     *  of the rank(s) after each reading-operation.
     *  but, we could use this test to verify that this recalculation works correctly.)
     */
    @Ignore
    @Test
    public void splitReadingNoAvailableRankTest() {
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'unto me'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node untoMe = nodes.next();
            assertFalse(nodes.hasNext());

            // split reading
            ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
            readingBoundaryModel.setCharacter("");
            ClientResponse response = jerseyTest
                    .resource()
                    .path("/reading/" + untoMe.getId() + "/split/0")
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, readingBoundaryModel);

            assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                    response.getStatusInfo().getStatusCode());
            assertEquals(
                    "There has to be a rank-gap after a reading to be split",
                    response.getEntity(String.class));
            tx.success();
        }
    }

    /**
     * test that all readings of a tradition are returned sorted ascending
     * according to rank
     */
    @Test(expected = org.junit.ComparisonFailure.class)
    public void allReadingsOfTraditionTest() {
        List<ReadingModel> listOfReadings = jerseyTest.resource()
                .path("/tradition/" + tradId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {
                });
        Collections.sort(listOfReadings);

        assertEquals(29, listOfReadings.size());

        String expectedTest = "#START# when april april with his his showers sweet with fruit fruit the to drought march of march drought has pierced teh to unto me rood-of-the-world the root the root #END#";
        List<String> words = listOfReadings.stream().map(ReadingModel::getText).collect(Collectors.toList());
        String text = String.join(" ", words);
        assertEquals(expectedTest, text);

        int[] expectedRanks = { 0, 1, 2, 2, 3, 4, 4, 5, 6, 7, 8, 9, 10, 10, 11,
                11, 12, 13, 13, 14, 15, 16, 16, 16, 17, 17, 17, 18, 21 };
        for (int i = 0; i < listOfReadings.size(); i++) {
            assertEquals(expectedRanks[i], (int) (long) listOfReadings.get(i).getRank());
        }
    }

    @Test
    public void allReadingsOfTraditionNotFoundTest() {
        String falseTradId = "I don't exist";
        ClientResponse response = jerseyTest.resource()
                .path("/tradition/" + falseTradId + "/readings")
                .get(ClientResponse.class);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
                response.getStatus());
        assertEquals("There is no tradition with this id",
                response.getEntity(String.class));
    }

    @Test
    public void identicalReadingsOneResultTest() {
        List<ReadingModel> identicalReadings;

        List<List<ReadingModel>> listOfIdenticalReadings = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/section/" + sectId + "/identicalreadings/3/8")
                .get(new GenericType<List<List<ReadingModel>>>() {});
        assertEquals(1, listOfIdenticalReadings.size());
        identicalReadings = listOfIdenticalReadings.get(0);
        assertEquals(2, identicalReadings.size());
        assertEquals("his", identicalReadings.get(1).getText());

        assertEquals(identicalReadings.get(0).getText(),
                identicalReadings.get(1).getText());
    }

    @Test
    public void identicalReadingsTwoResultsTest() {
        List<ReadingModel> identicalReadings;

        List<List<ReadingModel>> listOfIdenticalReadings = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/section/" + sectId + "/identicalreadings/1/8")
                .get(new GenericType<List<List<ReadingModel>>>() {});
        assertEquals(2, listOfIdenticalReadings.size());

        identicalReadings = listOfIdenticalReadings.get(0);
        assertEquals(2, identicalReadings.size());
        assertEquals("april", identicalReadings.get(1).getText());
        assertEquals(identicalReadings.get(0).getText(), identicalReadings.get(1).getText());

        identicalReadings = listOfIdenticalReadings.get(1);
        assertEquals(2, identicalReadings.size());
        assertEquals("his", identicalReadings.get(1).getText());
        assertEquals(identicalReadings.get(0).getText(),
                identicalReadings.get(1).getText());
    }

    @Test
    public void identicalReadingsNoResultTest() {
        ClientResponse response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/section/" + sectId + "/identicalreadings/10/15")
                .get(ClientResponse.class);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
                response.getStatus());
        assertEquals("no identical readings were found",
                response.getEntity(String.class));
    }

    @Test
    public void couldBeIdenticalReadingsTest() {
        ClientResponse response = Util.createTraditionFromFileOrString(jerseyTest, "Legend", "LR", "1",
                "src/TestFiles/legendfrag.xml", "stemmaweb");
        String newTradId = Util.getValueFromJson(response, "tradId");
        List<SectionModel> sects = jerseyTest.resource().path("/tradition/" + newTradId + "/sections")
                .get(new GenericType<List<SectionModel>>() {});
        assertEquals(1, sects.size());
        String newSectId = sects.get(0).getId();

        // Remove the 'collated' relationship that prevents merging
        try (Transaction tx = db.beginTx()) {
            db.getAllRelationships().stream()
                    .filter(x -> x.isType(ERelations.RELATED) && x.getProperty("type").equals("collated"))
                    .forEach(Relationship::delete);
            tx.success();
        }

        // Now we should have mergeable readings
        List<List<ReadingModel>> couldBeIdenticalReadings = jerseyTest
                .resource()
                .path("/tradition/" + newTradId + "/section/" + newSectId + "/mergeablereadings/2/9")
                .get(new GenericType<List<List<ReadingModel>>>() {});
        assertEquals(4, couldBeIdenticalReadings.size());
        HashSet<String> expectedIdentical = new HashSet<>(Arrays.asList("beatus", "pontifex", "venerabilis", "henricus"));
        for (List<ReadingModel> cbi : couldBeIdenticalReadings) {
            assertTrue(expectedIdentical.contains(cbi.get(0).getText()));
        }
    }

    // same as above, but on a different text
    @Test
    public void mergeableReadingsTest() {
        ClientResponse response = Util.createTraditionFromFileOrString(jerseyTest, "Legend", "LR", "1",
                "src/TestFiles/legendfrag.xml", "stemmaweb");
        String newTradId = Util.getValueFromJson(response, "tradId");
        List<SectionModel> sects = jerseyTest.resource().path("/tradition/" + newTradId + "/sections")
                .get(new GenericType<List<SectionModel>>() {});
        assertEquals(1, sects.size());
        String newSectId = sects.get(0).getId();
        String firstId = "";
        String secondId = "";
        try (Transaction tx = db.beginTx()) {
            // Find the venerabili
            ResourceIterator<Node> ri = db.findNodes(Nodes.READING, "text", "venerabilis");
            while (ri.hasNext()) {
                Node n = ri.next();
                if (n.getProperty("rank").equals(3L))
                    firstId = String.valueOf(n.getId());
                if (n.getProperty("rank").equals(5L))
                    secondId = String.valueOf(n.getId());
            }
            // Get rid of all the "collated" relationships
            db.getAllRelationships().stream()
                    .filter(x -> x.isType(ERelations.RELATED) && x.getProperty("type").equals("collated"))
                    .forEach(Relationship::delete);
            tx.success();
        }

        // Merge the venerabili
        response = jerseyTest.resource().path("/reading/" + firstId + "/merge/" + secondId).post(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        // Check that the ranks are correct
        try (Transaction tx = db.beginTx()) {
            Node remain = db.getNodeById(Long.valueOf(firstId));
            assertEquals(5L, remain.getProperty("rank"));
            Node capv = db.findNode(Nodes.READING, "text", "Venerabilis");
            assertEquals(5L, capv.getProperty("rank"));
            Node uene = db.findNode(Nodes.READING, "text", "uenerabilis");
            assertEquals(5L, uene.getProperty("rank"));
            tx.success();
        }

        // Check that the pontifices are mergeable
        response = jerseyTest.resource()
                .path("/tradition/" + newTradId + "/section/" + newSectId + "/mergeablereadings/3/10")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<List<ReadingModel>> r = response.getEntity(new GenericType<List<List<ReadingModel>>>() {});
        assertEquals(1, r.size());
        assertEquals("pontifex", r.get(0).get(0).getText());
    }

    /**
     * should not find any could-be identical readings
     */
    @Test
    public void couldBeIdenticalReadingsNoResultTest() {
        ClientResponse response = Util.createTraditionFromFileOrString(jerseyTest, "Legend", "LR", "1",
                "src/TestFiles/legendfrag.xml", "stemmaweb");
        String newTradId = Util.getValueFromJson(response, "tradId");
        List<SectionModel> sects = jerseyTest.resource().path("/tradition/" + newTradId + "/sections")
                .get(new GenericType<List<SectionModel>>() {});
        assertEquals(1, sects.size());
        String newSectId = sects.get(0).getId();

        response = jerseyTest
                .resource()
                .path("/tradition/" + newTradId + "/section/" + newSectId + "/mergeablereadings/2/9")
                .get(ClientResponse.class);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<List<ReadingModel>> result =
                response.getEntity(new GenericType<List<List<ReadingModel>>>() {});
        assertEquals(0, result.size());
    }

    // compress with separate set to 1, but the empty string between words
    @Test(expected = org.junit.ComparisonFailure.class)
    public void compressReadingsNoConcatenatingNoTextTest() {
        Node showers, sweet;
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'showers'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assert (nodes.hasNext());
            showers = nodes.next();

            result = db.execute("match (w:READING {text:'sweet'}) return w");
            nodes = result.columnAs("w");
            assert (nodes.hasNext());
            sweet = nodes.next();

            ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
            readingBoundaryModel.setCharacter("");
            ClientResponse res = jerseyTest
                    .resource()
                    .path("/reading/" + showers.getId() + "/concatenate/" + sweet.getId())
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, readingBoundaryModel);

            assertEquals(Response.Status.OK.getStatusCode(), res.getStatus());
            assertEquals("showerssweet", showers.getProperty("text"));

            result = db.execute("match (w:READING {text:'showerssweet'}) return w");
            nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            nodes.next();
            assertFalse(nodes.hasNext());

            result = db.execute("match (w:READING {text:'sweet'}) return w");
            nodes = result.columnAs("w");
            assertFalse(nodes.hasNext());

            result = db.execute("match (w:READING {text:'showers'}) return w");
            nodes = result.columnAs("w");
            assertFalse(nodes.hasNext());

            // there is one reading less in the tradition and witnesses have not
            // been changed
            List<ReadingModel> listOfReadings = testNumberOfReadingsAndWitnesses(28);
            Collections.sort(listOfReadings);

            // tradition still has all the texts
            String expectedTest = "#START# when april april with his his showers sweet with fruit fruit the to drought march of march drought has pierced teh to unto me rood-of-the-world the root the root #END#";
            List<String> words = listOfReadings.stream().map(ReadingModel::getText).collect(Collectors.toList());
            String text = String.join(" ", words);
            assertEquals(expectedTest, text);

            // no more reading with rank 6
            int[] expectedRanks = { 0, 1, 2, 2, 3, 4, 4, 5, 7, 8, 9, 10, 10,
                    11, 11, 12, 13, 13, 14, 15, 16, 16, 16, 17, 17, 17, 18, 21 };
            for (int i = 0; i < listOfReadings.size(); i++) {
                assertEquals(expectedRanks[i],
                        (int) (long) listOfReadings.get(i).getRank());
            }
            tx.success();
        }
    }

    // compress with separate set to 0: no space between words
    @Test(expected = org.junit.ComparisonFailure.class)
    public void compressReadingsNoConcatenatingWithTextTest() {
        Node showers, sweet;
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'showers'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assert (nodes.hasNext());
            showers = nodes.next();

            result = db.execute("match (w:READING {text:'sweet'}) return w");
            nodes = result.columnAs("w");
            assert (nodes.hasNext());
            sweet = nodes.next();

            ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
            readingBoundaryModel.setSeparate(false);
            readingBoundaryModel.setCharacter("shouldNotBeDesplayd");
            ClientResponse res = jerseyTest
                    .resource()
                    .path("/reading/" + showers.getId() + "/concatenate/" + sweet.getId())
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, readingBoundaryModel);

            assertEquals(Response.Status.OK.getStatusCode(), res.getStatus());
            assertEquals("showerssweet", showers.getProperty("text"));

            result = db.execute("match (w:READING {text:'showerssweet'}) return w");
            nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            nodes.next();
            assertFalse(nodes.hasNext());

            result = db.execute("match (w:READING {text:'sweet'}) return w");
            nodes = result.columnAs("w");
            assertFalse(nodes.hasNext());

            result = db.execute("match (w:READING {text:'showers'}) return w");
            nodes = result.columnAs("w");
            assertFalse(nodes.hasNext());

            // there is one reading less in the tradition and witnesses have not
            // been changed
            List<ReadingModel> listOfReadings = testNumberOfReadingsAndWitnesses(28);
            Collections.sort(listOfReadings);

            // tradition still has all the texts
            String expectedTest = "#START# when april april with his his showers sweet with fruit fruit the to drought march of march drought has pierced teh to unto me rood-of-the-world the root the root #END#";
            List<String> words = listOfReadings.stream().map(ReadingModel::getText).collect(Collectors.toList());
            String text = String.join(" ", words);
            assertEquals(expectedTest, text);

            // no more reading with rank 6
            int[] expectedRanks = { 0, 1, 2, 2, 3, 4, 4, 5, 7, 8, 9, 10, 10,
                    11, 11, 12, 13, 13, 14, 15, 16, 16, 16, 17, 17, 17, 18, 21 };
            for (int i = 0; i < listOfReadings.size(); i++) {
                assertEquals(expectedRanks[i], (int) (long) listOfReadings.get(i).getRank());
            }
            tx.success();
        }
    }

    // compress with text between the readings' texts
    @Test
    public void compressReadingsWithConcatenatingWithConTextTest() {
        Node showers, sweet;
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'showers'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assert (nodes.hasNext());
            showers = nodes.next();

            result = db.execute("match (w:READING {text:'sweet'}) return w");
            nodes = result.columnAs("w");
            assert (nodes.hasNext());
            sweet = nodes.next();

            ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
            readingBoundaryModel.setCharacter("test");
            ClientResponse res = jerseyTest
                    .resource()
                    .path("/reading/" + showers.getId() + "/concatenate/" + sweet.getId() + "/1")
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, readingBoundaryModel);

            assertEquals(Response.Status.OK.getStatusCode(), res.getStatus());
            assertEquals("showerstestsweet", showers.getProperty("text"));

            result = db.execute("match (w:READING {text:'showerstestsweet'}) return w");
            nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            nodes.next();
            assertFalse(nodes.hasNext());

            result = db.execute("match (w:READING {text:'sweet'}) return w");
            nodes = result.columnAs("w");
            assertFalse(nodes.hasNext());

            result = db.execute("match (w:READING {text:'showers'}) return w");
            nodes = result.columnAs("w");
            assertFalse(nodes.hasNext());

            String expectedWitnessA = "{\"text\":\"when april with his showerstestsweet with fruit the drought of march has pierced unto me the root\"}";
            Response resp = new Witness(tradId, "A").getWitnessAsText();
            assertEquals(expectedWitnessA, resp.getEntity());
            tx.success();
        }
    }

    // compress with " between the readings' texts
    @Test
    public void compressReadingsWithConcatenatingWithQuotationAsTextTest() {
        Node showers, sweet;
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'showers'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assert (nodes.hasNext());
            showers = nodes.next();

            result = db.execute("match (w:READING {text:'sweet'}) return w");
            nodes = result.columnAs("w");
            assert (nodes.hasNext());
            sweet = nodes.next();

            ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
            readingBoundaryModel.setCharacter("\"");
            ClientResponse res = jerseyTest
                    .resource()
                    .path("/reading/" + showers.getId() + "/concatenate/" + sweet.getId())
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, readingBoundaryModel);

            assertEquals(Response.Status.OK.getStatusCode(), res.getStatus());
            assertEquals("showers\"sweet", showers.getProperty("text"));

            result = db.execute("match (w:READING {text:'showers\"sweet'}) return w");
            nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            nodes.next();
            assertFalse(nodes.hasNext());

            result = db.execute("match (w:READING {text:'sweet'}) return w");
            nodes = result.columnAs("w");
            assertFalse(nodes.hasNext());

            result = db.execute("match (w:READING {text:'showers'}) return w");
            nodes = result.columnAs("w");
            assertFalse(nodes.hasNext());

            String expectedWitnessA = "{\"text\":\"when april with his showers\"sweet with fruit the drought of march has pierced unto me the root\"}";
            Response resp = new Witness(tradId, "A").getWitnessAsText();
            assertEquals(expectedWitnessA, resp.getEntity());
            tx.success();
        }
    }

    // compress with / between the readings' texts
    @Test
    public void compressReadingsWithConcatenatingWithSlashAsTextTest() {
        Node showers, sweet;
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'showers'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assert (nodes.hasNext());
            showers = nodes.next();

            result = db.execute("match (w:READING {text:'sweet'}) return w");
            nodes = result.columnAs("w");
            assert (nodes.hasNext());
            sweet = nodes.next();

            ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
            readingBoundaryModel.setCharacter("/");
            ClientResponse res = jerseyTest
                    .resource()
                    .path("/reading/" + showers.getId() + "/concatenate/" + sweet.getId())
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, readingBoundaryModel);

            assertEquals(Response.Status.OK.getStatusCode(), res.getStatus());
            assertEquals("showers/sweet", showers.getProperty("text"));

            result = db.execute("match (w:READING {text:'showers/sweet'}) return w");
            nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            nodes.next();
            assertFalse(nodes.hasNext());

            result = db.execute("match (w:READING {text:'sweet'}) return w");
            nodes = result.columnAs("w");
            assertFalse(nodes.hasNext());

            result = db.execute("match (w:READING {text:'showers'}) return w");
            nodes = result.columnAs("w");
            assertFalse(nodes.hasNext());

            expectedWitnessB = "{\"text\":\"when april his showers/sweet with fruit the march of drought has pierced to the root\"}";
            Response resp = new Witness(tradId, "B").getWitnessAsText();
            assertEquals(expectedWitnessB, resp.getEntity());
            tx.success();
        }
    }

    // compress with concatenating the two texts without a gap or text
    @Test
    public void compressReadingsWithConcatenatingWithoutConTextTest() {
        Node showers, sweet;
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'showers'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assert (nodes.hasNext());
            showers = nodes.next();

            result = db.execute("match (w:READING {text:'sweet'}) return w");
            nodes = result.columnAs("w");
            assert (nodes.hasNext());
            sweet = nodes.next();

            ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
            readingBoundaryModel.setCharacter("");
            ClientResponse res = jerseyTest
                    .resource()
                    .path("/reading/" + showers.getId() + "/concatenate/" + sweet.getId())
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, readingBoundaryModel);

            assertEquals(Response.Status.OK.getStatusCode(), res.getStatus());
            assertEquals("showerssweet", showers.getProperty("text"));

            result = db.execute("match (w:READING {text:'showerssweet'}) return w");
            nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            nodes.next();
            assertFalse(nodes.hasNext());

            result = db.execute("match (w:READING {text:'sweet'}) return w");
            nodes = result.columnAs("w");
            assertFalse(nodes.hasNext());

            result = db.execute("match (w:READING {text:'showers'}) return w");
            nodes = result.columnAs("w");
            assertFalse(nodes.hasNext());

            String expectedWitnessA = "{\"text\":\"when april with his showerssweet with fruit the drought of march has pierced unto me the root\"}";
            Response resp = new Witness(tradId, "A").getWitnessAsText();
            assertEquals(expectedWitnessA, resp.getEntity());
            tx.success();
        }
    }

    /**
     * the given reading are not neighbors Should return error tests that
     * readings were not compressed
     */
    @Test
    public void notNeighborsCompressReadingTest() {
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'showers'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assert (nodes.hasNext());
            Node showers = nodes.next();

            result = db.execute("match (w:READING {text:'fruit'}) return w");
            nodes = result.columnAs("w");
            assert (nodes.hasNext());
            Node fruit = nodes.next();

            ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
            readingBoundaryModel.setCharacter("shouldNotBeDesplayd");
            ClientResponse response = jerseyTest
                    .resource()
                    .path("/reading/" + showers.getId() + "/concatenate/" + fruit.getId())
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, readingBoundaryModel);

            assertEquals(Response.Status.CONFLICT.getStatusCode(),
                    response.getStatus());
            assertEquals("reading are not contiguous. could not compress",
                    Util.getValueFromJson(response, "error"));

            result = db.execute("match (w:READING {text:'showers sweet'}) return w");
            nodes = result.columnAs("w");
            assertFalse(nodes.hasNext());

            assertEquals("showers", showers.getProperty("text"));
            assertEquals("fruit", fruit.getProperty("text"));
            tx.success();
        }
    }

    @Test
    public void nextReadingTest() {
        long withReadId;
        try (Transaction tx = db.beginTx()) {
            withReadId = db.findNodes(Nodes.READING, "text", "with").next().getId();
            tx.success();
        }

        ReadingModel actualResponse = jerseyTest
                .resource()
                .path("/reading/" + withReadId + "/next/A"
                       ).get(ReadingModel.class);
        assertEquals("his", actualResponse.getText());
    }

    // tests that the next reading is correctly returned according to witness
    @Test
    public void nextReadingWithTwoWitnessesTest() {
        long piercedReadId;
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'pierced'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assert (nodes.hasNext());
            piercedReadId = nodes.next().getId();
            tx.success();
        }

        ReadingModel actualResponse = jerseyTest
                .resource()
                .path("/reading/" + piercedReadId +"/next/A")
                .get(ReadingModel.class);
        assertEquals("unto me", actualResponse.getText());

        actualResponse = jerseyTest
                .resource()
                .path("/reading/" + piercedReadId + "/next/B")
                .get(ReadingModel.class);
        assertEquals("to", actualResponse.getText());
    }

    // the given reading is the last reading in a witness
    // should return error
    @Test
    public void nextReadingLastNodeTest() {
        long readId;
        try (Transaction tx = db.beginTx()) {
            readId = db.findNode(Nodes.READING, "text", "the root").getId();
            tx.success();
        }

        ClientResponse response = jerseyTest
                .resource()
                .path("/reading/" + readId + "/next/B" )
                .get(ClientResponse.class);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
                response.getStatus());
        assertEquals("this was the last reading of this witness",
                Util.getValueFromJson(response, "error"));
    }

    @Test
    public void previousReadingTest() {
        long readId;
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'with'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assert (nodes.hasNext());
            readId = nodes.next().getId();
            tx.success();
        }
        ReadingModel actualResponse = jerseyTest
                .resource()
                .path("/reading/" + readId + "/prior/A")
                .get(ReadingModel.class);
        assertEquals("april", actualResponse.getText());
    }

    // tests that the previous reading is correctly returned according to
    // witness
    @Test
    public void previousReadingTwoWitnessesTest() {
        long ofId;
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'of'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assert (nodes.hasNext());
            ofId = nodes.next().getId();
            tx.success();
        }
        ReadingModel actualResponse = jerseyTest
                .resource()
                .path("/reading/" + ofId + "/prior/A")
                .get(ReadingModel.class);
        assertEquals("drought", actualResponse.getText());

        actualResponse = jerseyTest
                .resource()
                .path("/reading/" + ofId + "/prior/B")
                .get(ReadingModel.class);
        assertEquals("march", actualResponse.getText());
    }

    // the given reading is the first reading in a witness
    // should return error
    @Test
    public void previousReadingFirstNodeTest() {
        long readId;
        try (Transaction tx = db.beginTx()) {
            Node rdg = db.findNode(Nodes.READING, "text", "when");
            readId = rdg.getId();
            tx.success();
        }
        ClientResponse actualResponse = jerseyTest
                .resource()
                .path("/reading/" + readId + "/prior/A")
                .get(ClientResponse.class);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
                actualResponse.getStatus());
        assertEquals("this was the first reading of this witness",
                Util.getValueFromJson(actualResponse, "error"));
    }

    @Test
    public void relatedReadingsTest() {
        long readId;
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'fruit', rank:8}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            readId = nodes.next().getId();
            tx.success();
        }
        ClientResponse jerseyResponse = jerseyTest.resource().path("/reading/" + readId + "/related")
                .queryParam("types", "transposition")
                .queryParam("types", "repetition")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        List<ReadingModel> relatedReadings = jerseyResponse.getEntity(new GenericType<List<ReadingModel>>() {});
        assertEquals(1, relatedReadings.size());
        assertEquals("the root", relatedReadings.get(0).getText());
        List<ReadingModel> allRels = jerseyTest.resource().path("/reading/" + readId + "/related")
                .get(new GenericType<List<ReadingModel>>() {});
        assertEquals(3, allRels.size());
    }

/*  Write this test when we are sure what we need to test!
    @Test
    public void readingWitnessTest() {
        long readId;

    }
*/

    @Test
    public void randomNodeExistsTest() {
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'april'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assert (nodes.hasNext());
            long rank = 2;
            assertEquals(rank, nodes.next().getProperty("rank"));
            tx.success();
        }
    }

    /**
     * test if the tradition node exists
     */
    @Test
    public void traditionNodeExistsTest() {
        try (Transaction tx = db.beginTx()) {
            ResourceIterator<Node> tradNodesIt = db.findNodes(Nodes.TRADITION, "name", "Tradition");
            assertTrue(tradNodesIt.hasNext());
            tx.success();
        }
    }

    /**
     * test if the tradition end node exists
     */
    @Test
    public void traditionEndNodeExistsTest() {
        try (Transaction tx = db.beginTx()) {
            ResourceIterator<Node> tradNodesIt = db.findNodes(Nodes.READING, "text", "#END#");
            assertTrue(tradNodesIt.hasNext());
            tx.success();
        }
    }

    /*
     * Shut down the jersey server
     *
     * @throws Exception
     */
    @After
    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
    }

}
