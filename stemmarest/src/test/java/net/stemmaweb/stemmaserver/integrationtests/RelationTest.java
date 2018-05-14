package net.stemmaweb.stemmaserver.integrationtests;

import java.util.*;
import java.util.stream.Collectors;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.model.GraphModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.model.WitnessTextModel;
import net.stemmaweb.rest.*;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;

import net.stemmaweb.stemmaserver.Util;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.*;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.ClientResponse.Status;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import org.neo4j.test.TestGraphDatabaseFactory;

import static org.junit.Assert.*;

/**
 * 
 * Contains all tests for the api calls related to relations between readings.
 * 
 * @author PSE FS 2015 Team2
 *
 */
public class RelationTest {
    private String tradId;
    private GraphDatabaseService db;

    /*
     * JerseyTest is the test environment to Test api calls it provides a
     * grizzly http service
     */
    private JerseyTest jerseyTest;
    private HashMap<String, String> readingLookup;

    @Before
    public void setUp() throws Exception {
        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
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
        ClientResponse jerseyResponse = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/testTradition.xml", "stemmaweb");
        tradId = Util.getValueFromJson(jerseyResponse, "tradId");
        /*
         * get a mapping of reading text/rank to ID
         */
        List<ReadingModel> allReadings = jerseyTest.resource().path("/tradition/" + tradId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});
        readingLookup = new HashMap<>();
        for (ReadingModel rm : allReadings) {
            String key = rm.getText() + "/" + rm.getRank().toString();
            readingLookup.put(key, rm.getId());
        }
    }

    /**
     * Test if a relation is created properly
     */
    @Test
    public void createRelationshipTest() {
        String relationshipId;
        String source = readingLookup.getOrDefault("april/2", "17");
        String target = readingLookup.getOrDefault("showers/5", "25");
        RelationshipModel relationship = new RelationshipModel();
        relationship.setSource(source);
        relationship.setTarget(target);
        relationship.setType("repetition");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");
        relationship.setReading_a("april");
        relationship.setReading_b("showers");

        ClientResponse actualResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, relationship);
        assertEquals(Response.Status.CREATED.getStatusCode(), actualResponse.getStatus());

        try (Transaction tx = db.beginTx()) {
            GraphModel readingsAndRelationships = actualResponse.getEntity(new GenericType<GraphModel>(){});
            relationshipId = ((RelationshipModel) readingsAndRelationships.getRelationships().toArray()[0]).getId();
            Relationship loadedRelationship = db.getRelationshipById(Long.parseLong(relationshipId));

            assertEquals(Long.valueOf(source), (Long) loadedRelationship.getStartNode().getId());
            assertEquals(Long.valueOf(target), (Long) loadedRelationship.getEndNode().getId());
            assertEquals("repetition", loadedRelationship.getProperty("type"));
            assertEquals(0L,loadedRelationship.getProperty("alters_meaning"));
            assertEquals("yes",loadedRelationship.getProperty("is_significant"));
            assertEquals("april",loadedRelationship.getProperty("reading_a"));
            assertEquals("showers",loadedRelationship.getProperty("reading_b"));
            tx.success();
        }
    }

    /**
     * Test if an 404 error occurs when an invalid target node was tested
     */
    @Test
    public void createRelationshipWithInvalidTargetIdTest() {
        RelationshipModel relationship = new RelationshipModel();

        relationship.setSource("16");
        relationship.setTarget("1337");
        relationship.setType("grammatical");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");
        relationship.setReading_a("april");
        relationship.setReading_b("showers");

        ClientResponse actualResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, relationship);
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Test if an 404 error occurs when an invalid source node was tested
     */
    @Test
    public void createRelationshipWithInvalidSourceIdTest() {
        RelationshipModel relationship = new RelationshipModel();

        relationship.setSource("1337");
        relationship.setTarget("24");
        relationship.setType("grammatical");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");
        relationship.setReading_a("april");
        relationship.setReading_b("showers");

        ClientResponse actualResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, relationship);
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                actualResponse.getStatus());
    }

    /**
     * Test the removal method DELETE /relationship/{traditionId}/relationships/{relationshipId}
     */
    @Test(expected=NotFoundException.class)
    public void deleteRelationshipTest() {
        /*
         * Create a relationship
         */
        String source = readingLookup.getOrDefault("april/2", "17");
        String target = readingLookup.getOrDefault("showers/5", "25");
        String relationshipId;
        RelationshipModel relationship = new RelationshipModel();
        relationship.setSource(source);
        relationship.setTarget(target);
        relationship.setType("transposition");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");
        relationship.setReading_a("april");
        relationship.setReading_b("showers");
        relationship.setScope("local");

        ClientResponse actualResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, relationship);
        GraphModel readingsAndRelationships = actualResponse.getEntity(new GenericType<GraphModel>(){});
        relationshipId = ((RelationshipModel) readingsAndRelationships.getRelationships().toArray()[0]).getId();

        ClientResponse removalResponse = jerseyTest.resource()
                .path("/tradition/" + tradId + "/relation/" + relationshipId)
                .delete(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), removalResponse.getStatus());

        try (Transaction tx = db.beginTx()) {
            db.getRelationshipById(Long.parseLong(relationshipId));
            tx.success();
        }
    }

    //also tests the delete method: with the testTradition
    @Test
    public void deleteRelationsTest() {
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'march'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node march1 = nodes.next();
            assertTrue(nodes.hasNext());
            Node march2 = nodes.next();
            assertFalse(nodes.hasNext());

            Relationship rel = march1.getSingleRelationship(ERelations.RELATED, Direction.BOTH);
            //checks that the correct relationship has been found
            assertNotNull(rel);
            assertEquals(march2, rel.getOtherNode(march1));

            ClientResponse removalResponse = jerseyTest
                    .resource()
                    .path("/tradition/" + tradId + "/relation/" + rel.getId())
                    .delete(ClientResponse.class);
            assertEquals(Response.Status.OK.getStatusCode(), removalResponse.getStatus());

            result = db.execute("match (w:READING {text:'march'}) return w");
            nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            march1 = nodes.next();
            assertTrue(nodes.hasNext());
            nodes.next();   // march2
            assertFalse(nodes.hasNext());

            Iterable<Relationship> rels = march1.getRelationships(ERelations.RELATED);

            assertFalse(rels.iterator().hasNext());
            String expectedText = "when april with his showers sweet with " +
                    "fruit the drought of march has pierced unto the root";
            WitnessTextModel resp = (WitnessTextModel) new Witness(tradId, "A").getWitnessAsText().getEntity();
            assertEquals(expectedText, resp.getText());

            expectedText = "when showers sweet with april fruit the march " +
                    "of drought has pierced to the root";
            resp = (WitnessTextModel) new Witness(tradId, "B").getWitnessAsText().getEntity();
            assertEquals(expectedText, resp.getText());

            tx.success();
        }
    }

    /**
     * Test the removal method DELETE /relationship/{tradidtionId}/relationships/{relationshipId}
     * Try to delete a relationship that does not exist
     */
    @Test
    public void deleteRelationshipThatDoesNotExistTest() {
        ClientResponse removalResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation/1337")
                .delete(ClientResponse.class);
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                removalResponse.getStatus());
    }

    @Test
    public void createRelationshipDocumentWideTest() {
        // Find the relevant readings
        final Comparator<Node> highestRank = (o1, o2) -> Long.valueOf(o2.getProperty("rank").toString())
                .compareTo(Long.valueOf(o1.getProperty("rank").toString()));

        Long idThe = getReading("the", highestRank).getId();
        Long idTeh = getReading("teh", highestRank).getId();
        assertTrue(idTeh > 0L);
        assertTrue(idThe > 0L);
        // Create the test relationship
        RelationshipModel r = new RelationshipModel();
        r.setSource(idTeh.toString());
        r.setTarget(idThe.toString());
        r.setType("spelling");
        r.setReading_a("teh");
        r.setReading_b("the");
        r.setAlters_meaning(1L);
        r.setIs_significant("maybe");
        r.setScope("document");

        // Add the tradition a second time
        ClientResponse jerseyResponse = Util.createTraditionFromFileOrString(jerseyTest, "Other tradition", "LR", "1",
                "src/TestFiles/testTradition.xml", "stemmaweb");
        String secondId = Util.getValueFromJson(jerseyResponse, "tradId");
        assertNotNull(secondId);

        // Get the baseline number of relationships
        List<RelationshipModel> currentRels = jerseyTest.resource()
                .path("/tradition/" + tradId + "/relationships")
                .get(new GenericType<List<RelationshipModel>>() {});
        int existingRels = currentRels.size();

        // Set the test relationship
        jerseyResponse = jerseyTest.resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, r);
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResponse.getStatus());
        GraphModel newRels = jerseyResponse.getEntity(new GenericType<GraphModel>() {});
        // Test that two new relationships were created
        assertEquals(2, newRels.getRelationships().size());
        // Test that they have the same is_significant property
        for (RelationshipModel rm : newRels.getRelationships()) {
            assertEquals(r.getIs_significant(), rm.getIs_significant());
            assertEquals(r.getAlters_meaning(), rm.getAlters_meaning());
            assertEquals(r.getScope(), rm.getScope());
        }

        // Test that there are now n+2 relationships in our tradition
        currentRels = jerseyTest.resource()
                .path("/tradition/" + tradId + "/relationships")
                .get(new GenericType<List<RelationshipModel>>() {});
        assertEquals(existingRels + 2, currentRels.size());

        // ...and that there are still only n relationships in our identical tradition.
        List<RelationshipModel> secondRels = jerseyTest.resource()
                .path("/tradition/" + secondId + "/relationships")
                .get(new GenericType<List<RelationshipModel>>() {});
        assertEquals(existingRels, secondRels.size());
    }

    @Test(expected=NotFoundException.class)
    public void deleteRelationshipDocumentWideTest() {
        /*
         * Create a relationship
         */
        RelationshipModel relationship = new RelationshipModel();
        String relationshipId1;
        String relationshipId2;
        String source = readingLookup.getOrDefault("april/2", "17");
        String target = readingLookup.getOrDefault("pierced/15", "17");
        relationship.setSource(source);
        relationship.setTarget(target);
        relationship.setType("transposition");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");
        relationship.setReading_a("april");
        relationship.setReading_b("pierced");
        relationship.setScope("local");

        ClientResponse actualResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, relationship);
        GraphModel readingsAndRelationships1 = actualResponse.getEntity(new GenericType<GraphModel>(){});
        relationshipId1 = ((RelationshipModel) readingsAndRelationships1.getRelationships().toArray()[0]).getId();

        source = readingLookup.getOrDefault("april/8", "17");
        target = readingLookup.getOrDefault("pierced/15", "17");
        relationship.setSource(source);
        relationship.setTarget(target);
        relationship.setType("transposition");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");
        relationship.setReading_a("april");
        relationship.setReading_b("pierced");
        relationship.setScope("local");

        actualResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, relationship);
        GraphModel readingsAndRelationships2 = actualResponse.getEntity(new GenericType<GraphModel>(){});
        relationshipId2 = ((RelationshipModel) readingsAndRelationships2.getRelationships().toArray()[0]).getId();

        relationship.setScope("document");

        ClientResponse removalResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class, relationship);
        assertEquals(Response.Status.OK.getStatusCode(), removalResponse.getStatus());

        try (Transaction tx = db.beginTx()) {
            Relationship rel1 = db.getRelationshipById(Long.parseLong(relationshipId1));
            Relationship rel2 = db.getRelationshipById(Long.parseLong(relationshipId2));

            RelationshipModel relMod1 = new RelationshipModel(rel1);
            assertEquals(rel1, relMod1);

            RelationshipModel relMod2 = new RelationshipModel(rel2);
            assertEquals(rel2, relMod2);
            tx.success();
        }
    }

    /**
     * Test that cross relations may not be made
     */
    @Test
    public void createRelationshipTestWithCrossRelationConstraint() {
        RelationshipModel relationship = new RelationshipModel();
        String source = readingLookup.getOrDefault("root/18", "17");
        String target = readingLookup.getOrDefault("teh/16", "25");
        relationship.setSource(source);
        relationship.setTarget(target);
        relationship.setType("grammatical");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");
        relationship.setReading_a("root");
        relationship.setReading_b("teh");

        // This relationship should be makeable, and three readings including the end node
        // will change rank
        ClientResponse actualResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, relationship);
        assertEquals(Response.Status.CREATED.getStatusCode(), actualResponse.getStatus());
        GraphModel tmpGraphModel = actualResponse.getEntity(new GenericType<GraphModel>(){});
        assertEquals(1, tmpGraphModel.getRelationships().size());
        assertEquals(3, tmpGraphModel.getReadings().size());
        assertEquals(true, tmpGraphModel.getReadings().stream().findFirst().isPresent());
        HashMap<String, Long> rankChange = new HashMap<>();
        rankChange.put("teh", 18L);
        rankChange.put("rood", 19L);
        rankChange.put("#END#", 20L);
        for (ReadingModel r : tmpGraphModel.getReadings()) {
            assertTrue(rankChange.containsKey(r.getText()));
            assertEquals(rankChange.get(r.getText()), r.getRank());
        }

        source = readingLookup.getOrDefault("root/18", "17");
        target = readingLookup.getOrDefault("the/17", "25");
        relationship.setSource(source);
        relationship.setTarget(target);
        relationship.setType("grammatical");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");
        relationship.setReading_a("root");
        relationship.setReading_b("the");

        // this one should not be make-able, due to the cross-relationship-constraint!
        actualResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, relationship);

        // RETURN CONFLICT IF THE CROSS RELATED RULE IS TAKING ACTION
        assertEquals(Status.CONFLICT.getStatusCode(), actualResponse.getStatusInfo().getStatusCode());
        assertEquals("This relationship creation is not allowed, it would result in a cyclic graph.",
                Util.getValueFromJson(actualResponse, "error"));

        try (Transaction tx = db.beginTx()) {
            Node the = db.getNodeById(Long.valueOf(target));
            Iterator<Relationship> rels = the
                    .getRelationships(ERelations.RELATED)
                    .iterator();

            assertTrue(!rels.hasNext()); // make sure node 28 does not have a relationship now!
            tx.success();
        }
    }

    @Test
    public void createRelationshipTestWithCrossRelationConstraintNotDirectlyCloseToEachOther() {
        RelationshipModel relationship = new RelationshipModel();
        String source = readingLookup.getOrDefault("root/18", "17");
        String target = readingLookup.getOrDefault("teh/16", "25");
        relationship.setSource(source);
        relationship.setTarget(target);
        relationship.setType("grammatical");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");
        relationship.setReading_a("root");
        relationship.setReading_b("teh");

        // This relationship should be make-able
        ClientResponse actualResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, relationship);
        assertEquals(Status.CREATED.getStatusCode(), actualResponse.getStatus());
        GraphModel tmpGraphModel = actualResponse.getEntity(new GenericType<GraphModel>(){});
        assertEquals(tmpGraphModel.getRelationships().size(), 1L);
        String relationshipId = ((RelationshipModel) tmpGraphModel.getRelationships().toArray()[0]).getId();

        try (Transaction tx = db.beginTx()) {
            Relationship rel = db.getRelationshipById(Integer.parseInt(relationshipId));
            assertEquals("root", rel.getStartNode().getProperty("text"));
            assertEquals("teh", rel.getEndNode().getProperty("text"));
            tx.success();
        }

        ClientResponse response = jerseyTest
                .resource()
                .path("/reading/" + source)
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(response.getEntity(ReadingModel.class).getRank(), (Long) 18L);

        response = jerseyTest
                .resource()
                .path("/reading/" + target)
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(response.getEntity(ReadingModel.class).getRank(), (Long)18L);

        Result result = db.execute("match (w:READING {text:'rood'}) return w");
        Iterator<Node> nodes = result.columnAs("w");
        assertTrue(nodes.hasNext());
        Node node = nodes.next();
        assertFalse(nodes.hasNext());

        relationship.setSource(node.getId() + "");

        result = db.execute("match (w:READING {text:'unto'}) return w");
        nodes = result.columnAs("w");
        assertTrue(nodes.hasNext());
        node = nodes.next();
        assertFalse(nodes.hasNext());

        relationship.setTarget(node.getId() + "");

        relationship.setType("grammatical");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");
        relationship.setReading_a("rood");
        relationship.setReading_b("unto");

        // this one should not be make-able, due to the cross-relationship-constraint!
        actualResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, relationship);
        // RETURN CONFLICT IF THE CROSS RELATED RULE IS TAKING ACTION

        assertEquals(Status.CONFLICT.getStatusCode(), actualResponse.getStatusInfo().getStatusCode());
        assertEquals("This relationship creation is not allowed, it would result in a cyclic graph.",
                Util.getValueFromJson(actualResponse, "error"));

        try (Transaction tx = db.beginTx()) {
            Node node22 = db.getNodeById(22L);
            Iterator<Relationship> rels = node22.getRelationships(ERelations.RELATED).iterator();

            assertTrue(!rels.hasNext()); // make sure node 21 does not have a relationship now!
            tx.success();
        }
    }

    @Test
    public void createRelationshipTestWithCyclicConstraint() {
        RelationshipModel relationship = new RelationshipModel();

        Result result = db.execute("match (w:READING {text:'showers'}) return w");
        Iterator<Node> nodes = result.columnAs("w");
        assertTrue(nodes.hasNext());
        Node firstNode = nodes.next();
        assertFalse(nodes.hasNext());

        result = db.execute("match (w:READING {text:'pierced'}) return w");
        nodes = result.columnAs("w");
        assertTrue(nodes.hasNext());
        Node secondNode = nodes.next();
        assertFalse(nodes.hasNext());

        relationship.setSource(firstNode.getId() + "");
        relationship.setTarget(secondNode.getId() + "");
        relationship.setType("grammatical");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");
        relationship.setReading_a("showers");
        relationship.setReading_b("pierced");

        ClientResponse actualResponse = jerseyTest.resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, relationship);

        assertEquals(Status.CONFLICT.getStatusCode(), actualResponse.getStatusInfo().getStatusCode());
        // TODO (SK): ->TLA fix ErrorMessage (this one does not exist). Maybe we can define an enum?
/*        assertEquals(
                "This relationship creation is not allowed. Merging the two related readings would result in a cyclic graph.",
                actualResponse.getEntity(String.class));
*/
        try (Transaction tx = db.beginTx()) {
            Node node1 = db.getNodeById(firstNode.getId());
            Iterator<Relationship> rels = node1.getRelationships(ERelations.RELATED).iterator();

            assertTrue(!rels.hasNext()); // make sure node does not have a relationship now!

            Node node2 = db.getNodeById(secondNode.getId());
            rels = node2.getRelationships(ERelations.RELATED).iterator();

            assertTrue(!rels.hasNext()); // make sure node does not have a relationship now!
            tx.success();
        }
    }

    @Test
    public void createRelationshipOverCollatedTest() {
        ClientResponse response = Util.createTraditionFromFileOrString(jerseyTest, "Legend", "LR", "1",
                "src/TestFiles/legendfrag.xml", "stemmaweb");
        String newTradId = Util.getValueFromJson(response, "tradId");

        List<RelationshipModel> allrels = jerseyTest.resource().path("/tradition/" + newTradId + "/relationships")
                .get(new GenericType<List<RelationshipModel>>() {});

        // Try to overwrite a strong relationship
        List<RelationshipModel> orthorels = allrels.stream().filter(
                x -> x.getType().equals("orthographic") && x.getReading_a().equals("suecia") && x.getReading_b().equals("Swecia"))
                .collect(Collectors.toList());
        assertEquals(1, orthorels.size());
        RelationshipModel rel = orthorels.get(0);
        rel.setType("spelling");
        rel.setScope("document");
        response = jerseyTest.resource().path("/tradition/" + newTradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, rel);
        assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());

        List<RelationshipModel> collaterels = allrels.stream()
                .filter(x -> x.getType().equals("collated") && x.getReading_b().equals("henricus"))
                .collect(Collectors.toList());
        assertEquals(1, collaterels.size());
        rel = collaterels.get(0);
        // Change this to a stronger relationship type
        rel.setType("other");
        rel.setScope("local");
        rel.setIs_significant("yes");

        // Now try making the "new" relationship
        response = jerseyTest.resource().path("/tradition/" + newTradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, rel);
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    }

    @Test
    public void createRelationshipBreakCollatedTest() {
        ClientResponse response = Util.createTraditionFromFileOrString(jerseyTest, "Legend", "LR", "1",
                "src/TestFiles/legendfrag.xml", "stemmaweb");
        String newTradId = Util.getValueFromJson(response, "tradId");

        String myId;
        String otherId;
        try (Transaction tx = db.beginTx()) {
            List<Node> henries = db.findNodes(Nodes.READING, "text", "henricus").stream()
                    .filter(x -> x.getProperty("rank").equals(4L)).collect(Collectors.toList());
            Node other = db.findNode(Nodes.READING, "text", "heinricus");
            assertEquals(1, henries.size());
            assertNotNull(other);
            myId = String.valueOf(henries.get(0).getId());
            otherId = String.valueOf(other.getId());
            tx.success();
        }
        RelationshipModel r = new RelationshipModel();
        r.setSource(otherId);
        r.setTarget(myId);
        r.setType("spelling");
        r.setScope("local");
        r.setIs_significant("no");
        response = jerseyTest.resource().path("/tradition/" + newTradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, r);
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    }

    @Test
    public void createBadRelationshipRestoreCollatedTest () {
        // Create a collation between rood and root
        Long roodId;
        Long the1Id = 0L;
        Long the2Id = 0L;
        try (Transaction tx = db.beginTx()) {
            roodId = db.findNode(Nodes.READING, "text", "rood").getId();
            List<Node> thes = db.findNodes(Nodes.READING, "text", "the").stream()
                    .collect(Collectors.toList());
            for (Node the : thes) {
                if (the.getProperty("rank").equals(17L))
                    the1Id = the.getId();
                else
                    the2Id = the.getId();
            }
            tx.success();
        }
        assertTrue(the1Id > 0);
        assertTrue(the2Id > 0);

        // Make a collated relationship between rood and the
        RelationshipModel model = new RelationshipModel();
        model.setSource(roodId.toString());
        model.setTarget(the1Id.toString());
        model.setType("collated");
        model.setReading_a("rood");
        model.setReading_b("the");

        ClientResponse response = jerseyTest.resource().path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, model);
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

        // Try to make a transposition relationship between rood and root

        model.setTarget(the2Id.toString());
        model.setType("orthographic");
        model.setReading_b("root");
        response = jerseyTest.resource().path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, model);
        assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());

        // Check that the collated link is still there
        List<RelationshipModel> allRels = jerseyTest.resource().path("/tradition/" + tradId + "/relationships")
                .get(new GenericType<List<RelationshipModel>>() {});
        assertEquals(4, allRels.size());
        Boolean foundCollated = false;
        for (RelationshipModel r : allRels) {
            if (r.getType().equals("collated") && r.getSource().equals(roodId.toString())
                    && r.getTarget().equals(the1Id.toString()))
                foundCollated = true;
        }
        assertTrue(foundCollated);
    }

    @Test
    public void createRelationshipWrongTraditionTest() {
        ClientResponse jerseyResponse = Util.createTraditionFromFileOrString(jerseyTest, "Legend", "LR",
                "1", "src/TestFiles/legendfrag.xml", "stemmaweb");
        String newTradId = Util.getValueFromJson(jerseyResponse, "tradId");

        // We pretend to set a relationship between "ex" and "de"
        List<ReadingModel> lfReadings = jerseyTest.resource().path("/tradition/" + newTradId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {}).stream().filter(x -> x.getRank() == 7)
                .collect(Collectors.toList());
        RelationshipModel model = new RelationshipModel();
        model.setType("lexical");
        model.setReading_a("de");
        model.setReading_b("ex");
        for (ReadingModel rm : lfReadings) {
            if (rm.getText().equals("de")) model.setSource(rm.getId());
            if (rm.getText().equals("ex")) model.setTarget(rm.getId());
        }

        // Posting to the wrong tradition should fail
        jerseyResponse = jerseyTest.resource().path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, model);
        assertEquals(ClientResponse.Status.CONFLICT.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());

        // Posting to the right tradition should succeed
        jerseyResponse = jerseyTest.resource().path("/tradition/" + newTradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, model);
        assertEquals(Status.CREATED.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());
    }

    /*
     * Test if the get relationship method returns the correct value
     */
    @Test
    public void getRelationshipTest() {
        ClientResponse response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relationships")
                .get(ClientResponse.class);
        List<RelationshipModel> relationships = jerseyTest.resource()
                .path("/tradition/" + tradId + "/relationships")
                .get(new GenericType<List<RelationshipModel>>() {});
        assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());
        for (RelationshipModel rel : relationships) {
            assertTrue(rel.getReading_b().equals("april")
                    || rel.getReading_b().equals("drought")
                    || rel.getReading_b().equals("march"));
            assertEquals("transposition", rel.getType());
        }
    }

    /**
     * Test if the get relationship method returns correct state
     */
    @Test
    public void getRelationshipCorrectStatusTest(){
        ClientResponse response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relationships")
                .get(ClientResponse.class);
        assertEquals(Response.ok().build().getStatus(), response.getStatus());
    }

    /**
     * Test if the get relationship method returns the correct not found error on a non existing tradid
     */
    @Test
    public void getRelationshipExceptionTest() {
        ClientResponse response = jerseyTest
                .resource()
                .path("/tradition/6999/relationships")
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void getNoRelationshipTest(){
         /*
         * load a tradition with no Realtionships to the test DB
         */
        ClientResponse jerseyResponse = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/testTraditionNoRealtions.xml", "stemmaweb");
        String newTradId = Util.getValueFromJson(jerseyResponse, "tradId");

        ClientResponse response = jerseyTest
                .resource()
                .path("/tradition/" + newTradId + "/relationships")
                .get(ClientResponse.class);
        assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());
        assertEquals("[]", response.getEntity(String.class));
    }

    private Node getReading(String text, Comparator<Node> c) {
        Node result;
        try (Transaction tx = db.beginTx()) {
            List<Node> available = db.findNodes(Nodes.READING, "text", text).stream().collect(Collectors.toList());
            if (c != null)
                available.sort(c);
            tx.success();
            result = available.get(0);
        }
        return result;
    }

    /*
     * Shut down the jersey server
     * @throws Exception
     */
    @After
    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
    }
}
