package net.stemmaweb.stemmaserver.integrationtests;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.model.GraphModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationshipModel;
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
                "src/TestFiles/testTradition.xml", "graphml");
        tradId = Util.getValueFromJson(jerseyResponse, "tradId");
    }

    /**
     * Test if a relation is created properly
     */
    @Test
    public void createRelationshipTest() {
        RelationshipModel relationship = new RelationshipModel();
        String relationshipId;
        relationship.setSource("16");
        relationship.setTarget("24");
        relationship.setType("repetition");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");
        relationship.setReading_a("april");
        relationship.setReading_b("showers");

        ClientResponse actualResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, relationship);
        assertEquals(Response.Status.CREATED.getStatusCode(), actualResponse.getStatus());

        try (Transaction tx = db.beginTx()) {
            GraphModel readingsAndRelationships = actualResponse.getEntity(new GenericType<ArrayList<GraphModel>>(){}).get(0);
            relationshipId = readingsAndRelationships.getRelationships().get(0).getId();
            Relationship loadedRelationship = db.getRelationshipById(Long.parseLong(relationshipId));

            assertEquals(16L, loadedRelationship.getStartNode().getId());
            assertEquals(24L, loadedRelationship.getEndNode().getId());
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
                .put(ClientResponse.class, relationship);
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
                .put(ClientResponse.class, relationship);
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                actualResponse.getStatus());
    }

    /**
     * Test the removal method DELETE /relationship/{tradidtionId}/relationships/{relationshipId}
     */
    @Test(expected=NotFoundException.class)
    public void deleteRelationshipTest() {
        /*
         * Create a relationship
         */
        RelationshipModel relationship = new RelationshipModel();
        String relationshipId;
        relationship.setSource("16");
        relationship.setTarget("24");
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
                .put(ClientResponse.class, relationship);
        GraphModel readingsAndRelationships = actualResponse.getEntity(new GenericType<ArrayList<GraphModel>>(){}).get(0);
        relationshipId = readingsAndRelationships.getRelationships().get(0).getId();

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
            String expectedText = "{\"text\":\"when april with his showers sweet with " +
                    "fruit the drought of march has pierced unto the root\"}";
            Response resp = new Witness(tradId, "A").getWitnessAsText();
            assertEquals(expectedText, resp.getEntity());

            expectedText = "{\"text\":\"when showers sweet with april fruit the march " +
                    "of drought has pierced to the root\"}";
            resp = new Witness(tradId, "B").getWitnessAsText();
            assertEquals(expectedText, resp.getEntity());

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

    /**
     * Test the removal method by posting two nodes to /relation/{witnessId}/relationships/delete
     */
    @Test(expected=NotFoundException.class)
    public void deleteRelationshipTestdeleteAll() {
        /*
         * Create two relationships between two nodes
         */
        RelationshipModel relationship = new RelationshipModel();
        String relationshipId1;
        String relationshipId2;
        relationship.setSource("16");
        relationship.setTarget("24");
        relationship.setType("transposition");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");
        relationship.setReading_a("april");
        relationship.setReading_b("showers");
        relationship.setScope("local");

        ClientResponse actualResponse1 = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, relationship);
        GraphModel readingsAndRelationships1 = actualResponse1.getEntity(new GenericType<ArrayList<GraphModel>>(){}).get(0);
        relationshipId1 = readingsAndRelationships1.getRelationships().get(0).getId();

        relationship = new RelationshipModel();
        relationship.setSource("16");
        relationship.setTarget("24");
        relationship.setType("repetition");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");
        relationship.setReading_a("april");
        relationship.setReading_b("showers");
        relationship.setScope("local");

        ClientResponse actualResponse2 = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, relationship);
        GraphModel readingsAndRelationships2 = actualResponse2.getEntity(new GenericType<ArrayList<GraphModel>>(){}).get(0);
        relationshipId2 = readingsAndRelationships2.getRelationships().get(0).getId();

        /*
         * Create the model to delete
         */
        RelationshipModel removeModel = new RelationshipModel();
        removeModel.setSource("16");
        removeModel.setTarget("24");
        removeModel.setScope("local");

        ClientResponse removalResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class, removeModel);
        assertEquals(Response.Status.OK.getStatusCode(), removalResponse.getStatus());

        try (Transaction tx = db.beginTx()) {
            db.getRelationshipById(Long.parseLong(relationshipId1));
            db.getRelationshipById(Long.parseLong(relationshipId2));
            tx.success();
        }
    }

    @Test(expected=NotFoundException.class)
    public void deleteRelationshipDocumentWideTest() {
        /*
         * Create a relationship
         */
        RelationshipModel relationship = new RelationshipModel();
        String relationshipId1;
        String relationshipId2;
        relationship.setSource("16");
        relationship.setTarget("17");
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
                .put(ClientResponse.class, relationship);
        GraphModel readingsAndRelationships1 = actualResponse.getEntity(new GenericType<ArrayList<GraphModel>>(){}).get(0);
        relationshipId1 = readingsAndRelationships1.getRelationships().get(0).getId();

        relationship.setSource("27");
        relationship.setTarget("17");
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
                .put(ClientResponse.class, relationship);
        GraphModel readingsAndRelationships2 = actualResponse.getEntity(new GenericType<ArrayList<GraphModel>>(){}).get(0);
        relationshipId2 = readingsAndRelationships2.getRelationships().get(0).getId();

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
        relationship.setSource("6");
        relationship.setTarget("20");

        relationship.setType("grammatical");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");
        relationship.setReading_a("root");
        relationship.setReading_b("teh");

        // This relationship should be makeable
        ClientResponse actualResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, relationship);
        assertEquals(Response.Status.CREATED.getStatusCode(), actualResponse.getStatus());
        ArrayList<GraphModel> tmpGraphModel = actualResponse.getEntity(new GenericType<ArrayList<GraphModel>>(){});
        assertEquals(tmpGraphModel.size(), 1L);

        Tradition tradition = new Tradition(tradId);
        assertEquals(tradition.recalculateRank(20L), true);

        relationship.setSource("21");
        relationship.setTarget("28");
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
                .put(ClientResponse.class, relationship);

        // RETURN CONFLICT IF THE CROSS RELATED RULE IS TAKING ACTION
        assertEquals(Status.CONFLICT.getStatusCode(), actualResponse.getStatusInfo().getStatusCode());
        assertEquals("This relationship creation is not allowed, it would result in a cyclic graph.",
                actualResponse.getEntity(String.class));

        try (Transaction tx = db.beginTx()) {
            Node node28 = db.getNodeById(28L);
            Iterator<Relationship> rels = node28
                    .getRelationships(ERelations.RELATED)
                    .iterator();

            assertTrue(!rels.hasNext()); // make sure node 28 does not have a relationship now!
            tx.success();
        }
    }

    @Test
    public void createRelationshipTestWithCrossRelationConstraintNotDirectlyCloseToEachOther() {
        RelationshipModel relationship = new RelationshipModel();
        relationship.setSource("6");
        relationship.setTarget("20");
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
                .put(ClientResponse.class, relationship);
        assertEquals(Status.CREATED.getStatusCode(), actualResponse.getStatus());
        ArrayList<GraphModel> tmpGraphModel = actualResponse.getEntity(new GenericType<ArrayList<GraphModel>>(){});
        assertEquals(tmpGraphModel.size(), 1L);
        String relationshipId = tmpGraphModel.get(0).getRelationships().get(0).getId();

        ClientResponse response = jerseyTest
                .resource()
                .path("/reading/6")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(response.getEntity(ReadingModel.class).getRank(), (Long) 18L);

        response = jerseyTest
                .resource()
                .path("/reading/20")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(response.getEntity(ReadingModel.class).getRank(), (Long)18L);

        try (Transaction tx = db.beginTx()) {
            Relationship rel = db.getRelationshipById(Integer.parseInt(relationshipId));
            assertEquals("root", rel.getStartNode().getProperty("text"));
            assertEquals("teh", rel.getEndNode().getProperty("text"));
            tx.success();
        }

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
                .put(ClientResponse.class, relationship);
        // RETURN CONFLICT IF THE CROSS RELATED RULE IS TAKING ACTION

        assertEquals(Status.CONFLICT.getStatusCode(), actualResponse.getStatusInfo().getStatusCode());
        // TODO (SK): ->TLA fix ErrorMessage (this one does not exist). Maybe we can define an enum?
        //assertEquals("This relationship creation is not allowed. Would produce cross-relationship.",
        //        actualResponse.getEntity(String.class));

        try (Transaction tx = db.beginTx()) {
            Node node21 = db.getNodeById(21L);
            Iterator<Relationship> rels = node21.getRelationships(ERelations.RELATED).iterator();

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
                .put(ClientResponse.class, relationship);

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
            assertTrue(rel.getType().equals("transposition"));
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
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    }

    @Test
    public void getNoRelationshipTest(){
         /*
         * load a tradition with no Realtionships to the test DB
         */
        ClientResponse jerseyResponse = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/testTraditionNoRealtions.xml", "graphml");
        String newTradId = Util.getValueFromJson(jerseyResponse, "tradId");

        ClientResponse response = jerseyTest
                .resource()
                .path("/tradition/" + newTradId + "/relationships")
                .get(ClientResponse.class);
        assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());
        assertEquals("[]", response.getEntity(String.class));
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
