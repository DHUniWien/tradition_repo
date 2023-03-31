package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

import net.stemmaweb.model.GraphModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationModel;
import net.stemmaweb.model.TextSequenceModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Root;
import net.stemmaweb.rest.Witness;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;

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

        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(Root.class)
                .create();
        jerseyTest.setUp();

        /*
         * load a tradition to the test DB
         * and gets the generated id of the inserted tradition
         */
        Response jerseyResponse = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/testTradition.xml", "stemmaweb");
        tradId = Util.getValueFromJson(jerseyResponse, "tradId");
        /*
         * get a mapping of reading text/rank to ID
         */
        readingLookup = Util.makeReadingLookup(jerseyTest, tradId);
    }

    /**
     * Test if a relation is created properly
     */
    @Test
    public void createRelationshipTest() {
        String relationshipId;
        String source = readingLookup.getOrDefault("april/2", "17");
        String target = readingLookup.getOrDefault("showers/5", "25");
        RelationModel relationship = new RelationModel();
        relationship.setSource(source);
        relationship.setTarget(target);
        relationship.setType("repetition");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");

        Response actualResponse = jerseyTest
                .target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(relationship));
        assertEquals(Response.Status.CREATED.getStatusCode(), actualResponse.getStatus());

        try (Transaction tx = db.beginTx()) {
            GraphModel readingsAndRelationships = actualResponse.readEntity(new GenericType<GraphModel>(){});
            relationshipId = ((RelationModel) readingsAndRelationships.getRelations().toArray()[0]).getId();
            Relationship loadedRelationship = tx.getRelationshipByElementId(relationshipId);

            assertEquals(source, loadedRelationship.getStartNode().getElementId());
            assertEquals(target, loadedRelationship.getEndNode().getElementId());
            assertEquals("repetition", loadedRelationship.getProperty("type"));
            assertEquals(0L,loadedRelationship.getProperty("alters_meaning"));
            assertEquals("yes",loadedRelationship.getProperty("is_significant"));
            assertEquals("april",loadedRelationship.getProperty("reading_a"));
            assertEquals("showers",loadedRelationship.getProperty("reading_b"));
            tx.close();
        }
    }

    /**
     * Test if an 404 error occurs when an invalid target node was tested
     */
    @Test
    public void createRelationshipWithInvalidTargetIdTest() {
        RelationModel relationship = new RelationModel();

        relationship.setSource("16");
        relationship.setTarget("1337");
        relationship.setType("grammatical");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");

        Response actualResponse = jerseyTest
                .target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(relationship));
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Test if an 404 error occurs when an invalid source node was tested
     */
    @Test
    public void createRelationshipWithInvalidSourceIdTest() {
        RelationModel relationship = new RelationModel();

        relationship.setSource("1337");
        relationship.setTarget("24");
        relationship.setType("grammatical");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");

        Response actualResponse = jerseyTest
                .target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(relationship));
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
        RelationModel relationship = new RelationModel();
        relationship.setSource(source);
        relationship.setTarget(target);
        relationship.setType("transposition");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");
        relationship.setScope("local");

        Response actualResponse = jerseyTest
                .target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(relationship));
        GraphModel readingsAndRelationships = actualResponse.readEntity(new GenericType<GraphModel>(){});
        relationshipId = ((RelationModel) readingsAndRelationships.getRelations().toArray()[0]).getId();

        Response removalResponse = jerseyTest
                .target("/tradition/" + tradId + "/relation/" + relationshipId)
                .request()
                .delete();
        assertEquals(Response.Status.OK.getStatusCode(), removalResponse.getStatus());

        try (Transaction tx = db.beginTx()) {
            tx.getRelationshipByElementId(relationshipId);
            tx.close();
        }
    }

    //also tests the delete method: with the testTradition
    @Test
    public void deleteRelationsTest() {
        try (Transaction tx = db.beginTx()) {
            Result result = tx.execute("match (w:READING {text:'march'}) return w");
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

            Response removalResponse = jerseyTest
                    .target("/tradition/" + tradId + "/relation/" + rel.getElementId())
                    .request()
                    .delete();
            assertEquals(Response.Status.OK.getStatusCode(), removalResponse.getStatus());

            result = tx.execute("match (w:READING {text:'march'}) return w");
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
            TextSequenceModel resp = (TextSequenceModel) new Witness(tradId, "A").getWitnessAsText().getEntity();
            assertEquals(expectedText, resp.getText());

            expectedText = "when showers sweet with april fruit the march " +
                    "of drought has pierced to the root";
            resp = (TextSequenceModel) new Witness(tradId, "B").getWitnessAsText().getEntity();
            assertEquals(expectedText, resp.getText());

            tx.close();
        }
    }

    /**
     * Test the removal method DELETE /relationship/{tradidtionId}/relationships/{relationshipId}
     * Try to delete a relationship that does not exist
     */
    @Test
    public void deleteRelationshipThatDoesNotExistTest() {
        Response removalResponse = jerseyTest
                .target("/tradition/" + tradId + "/relation/1337")
                .request()
                .delete();
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                removalResponse.getStatus());
    }

    @Test
    public void createRelationshipDocumentWideTest() {
        // Find the relevant readings
        final Comparator<Node> highestRank = (o1, o2) -> Long.valueOf(o2.getProperty("rank").toString())
                .compareTo(Long.valueOf(o1.getProperty("rank").toString()));

        String idThe = getReading("the", highestRank).getElementId();
        String idTeh = getReading("teh", highestRank).getElementId();
        assertTrue(!idTeh.isBlank());
        assertTrue(!idThe.isBlank());
        // Create the test relationship
        RelationModel r = new RelationModel();
        r.setSource(idTeh);
        r.setTarget(idThe);
        r.setType("spelling");
        r.setAlters_meaning(1L);
        r.setIs_significant("maybe");
        r.setScope("tradition");

        // Add the tradition a second time
        Response jerseyResponse = Util.createTraditionFromFileOrString(jerseyTest, "Other tradition", "LR", "1",
                "src/TestFiles/testTradition.xml", "stemmaweb");
        String secondId = Util.getValueFromJson(jerseyResponse, "tradId");
        assertNotNull(secondId);

        // Get the baseline number of relationships
        List<RelationModel> currentRels = jerseyTest
                .target("/tradition/" + tradId + "/relations")
                .request()
                .get(new GenericType<List<RelationModel>>() {});
        int existingRels = currentRels.size();

        // Set the test relationship
        jerseyResponse = jerseyTest
                .target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(r));
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResponse.getStatus());
        GraphModel newRels = jerseyResponse.readEntity(new GenericType<GraphModel>() {});
        // Test that two new relationships were created
        assertEquals(2, newRels.getRelations().size());
        // Test that they have the same is_significant property
        for (RelationModel rm : newRels.getRelations()) {
            assertEquals(r.getIs_significant(), rm.getIs_significant());
            assertEquals(r.getAlters_meaning(), rm.getAlters_meaning());
            assertEquals(r.getScope(), rm.getScope());
        }

        // Test that there are now n+2 relationships in our tradition
        currentRels = jerseyTest
                .target("/tradition/" + tradId + "/relations")
                .request()
                .get(new GenericType<List<RelationModel>>() {});
        assertEquals(existingRels + 2, currentRels.size());

        // ...and that there are still only n relationships in our identical tradition.
        List<RelationModel> secondRels = jerseyTest
                .target("/tradition/" + secondId + "/relations")
                .request()
                .get(new GenericType<List<RelationModel>>() {});
        assertEquals(existingRels, secondRels.size());
    }

    @Test(expected=NotFoundException.class)
    public void deleteRelationshipDocumentWideTest() {
        /*
         * Create two local relations between teh and the.
         */
        RelationModel relationship = new RelationModel();
        String relationshipId1;
        String relationshipId2;
        String source = readingLookup.getOrDefault("teh/16", "17");
        String target = readingLookup.getOrDefault("the/17", "17");
        relationship.setSource(source);
        relationship.setTarget(target);
        relationship.setType("spelling");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("no");
        relationship.setScope("local");

        Response actualResponse = jerseyTest
                .target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(relationship));
        GraphModel readingsAndRelationships1 = actualResponse.readEntity(new GenericType<GraphModel>(){});
        relationshipId1 = ((RelationModel) readingsAndRelationships1.getRelations().toArray()[0]).getId();

        source = readingLookup.getOrDefault("teh/10", "17");
        target = readingLookup.getOrDefault("the/10", "17");
        relationship.setSource(source);
        relationship.setTarget(target);

        actualResponse = jerseyTest
                .target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(relationship));
        GraphModel readingsAndRelationships2 = actualResponse.readEntity(new GenericType<GraphModel>(){});
        relationshipId2 = ((RelationModel) readingsAndRelationships2.getRelations().toArray()[0]).getId();

        // Now try deleting them.
        relationship.setScope("tradition");
        
        // next property is necessary to prevent error "Entity must be null for http method DELETE"
        jerseyTest.client().property(ClientProperties.SUPPRESS_HTTP_COMPLIANCE_VALIDATION, true);

        Response removalResponse = jerseyTest
                .target("/tradition/" + tradId + "/relation/remove")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(relationship));
        assertEquals(Response.Status.OK.getStatusCode(), removalResponse.getStatus());

        try (Transaction tx = db.beginTx()) {
            tx.getRelationshipByElementId(relationshipId1);
            tx.getRelationshipByElementId(relationshipId2);
            tx.close();
            fail("These relationships should no longer exist");
        }
    }

    /**
     * Test that cross relations may not be made
     */
    @Test
    public void createRelationshipTestWithCrossRelationConstraint() {
        RelationModel relationship = new RelationModel();
        String source = readingLookup.getOrDefault("root/18", "17");
        String target = readingLookup.getOrDefault("teh/16", "25");
        relationship.setSource(source);
        relationship.setTarget(target);
        relationship.setType("grammatical");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");

        // This relationship should be makeable, and three readings including the end node
        // will change rank
        Response actualResponse = jerseyTest
                .target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(relationship));
        assertEquals(Response.Status.CREATED.getStatusCode(), actualResponse.getStatus());
        GraphModel tmpGraphModel = actualResponse.readEntity(new GenericType<GraphModel>(){});
        assertEquals(1, tmpGraphModel.getRelations().size());
        assertEquals(3, tmpGraphModel.getReadings().size());
        assertTrue(tmpGraphModel.getReadings().stream().findFirst().isPresent());
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

        // this one should not be make-able, due to the cross-relationship-constraint!
        actualResponse = jerseyTest
                .target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(relationship));

        // RETURN CONFLICT IF THE CROSS RELATED RULE IS TAKING ACTION
        assertEquals(Status.CONFLICT.getStatusCode(), actualResponse.getStatusInfo().getStatusCode());
        assertEquals("This relation creation is not allowed, it would result in a cyclic graph.",
                Util.getValueFromJson(actualResponse, "error"));

        try (Transaction tx = db.beginTx()) {
            Node the = tx.getNodeByElementId(target);
            Iterator<Relationship> rels = the
                    .getRelationships(ERelations.RELATED)
                    .iterator();

            assertFalse(rels.hasNext()); // make sure node 28 does not have a relationship now!
            tx.close();
        }
    }

    @Test
    public void createRelationshipTestWithCrossRelationConstraintNotDirectlyCloseToEachOther() {
        RelationModel relationship = new RelationModel();
        String source = readingLookup.getOrDefault("root/18", "17");
        String target = readingLookup.getOrDefault("teh/16", "25");
        relationship.setSource(source);
        relationship.setTarget(target);
        relationship.setType("grammatical");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");

        // This relationship should be make-able
        Response actualResponse = jerseyTest
                .target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(relationship));
        assertEquals(Status.CREATED.getStatusCode(), actualResponse.getStatus());
        GraphModel tmpGraphModel = actualResponse.readEntity(new GenericType<GraphModel>(){});
        assertEquals(tmpGraphModel.getRelations().size(), 1L);
        String relationshipId = ((RelationModel) tmpGraphModel.getRelations().toArray()[0]).getId();

        try (Transaction tx = db.beginTx()) {
            Relationship rel = tx.getRelationshipByElementId(relationshipId);
            assertEquals("root", rel.getStartNode().getProperty("text"));
            assertEquals("teh", rel.getEndNode().getProperty("text"));
            tx.close();
        }

        Response response = jerseyTest
                .target("/reading/" + source)
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(response.readEntity(ReadingModel.class).getRank(), (Long) 18L);

        response = jerseyTest
                .target("/reading/" + target)
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(response.readEntity(ReadingModel.class).getRank(), (Long)18L);

        try (Transaction tx = db.beginTx()) {
	        Result result = tx.execute("match (w:READING {text:'rood'}) return w");
	        Iterator<Node> nodes = result.columnAs("w");
	        assertTrue(nodes.hasNext());
	        Node node = nodes.next();
	        assertFalse(nodes.hasNext());
	
	        relationship.setSource(node.getElementId());
	
	        result = tx.execute("match (w:READING {text:'unto'}) return w");
	        nodes = result.columnAs("w");
	        assertTrue(nodes.hasNext());
	        node = nodes.next();
	        assertFalse(nodes.hasNext());
	
	        relationship.setTarget(node.getId() + "");
	
	        relationship.setType("grammatical");
	        relationship.setAlters_meaning(0L);
	        relationship.setIs_significant("yes");

	        tx.close();
        }
        // this one should not be make-able, due to the cross-relationship-constraint!
        actualResponse = jerseyTest
                .target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(relationship));
        // RETURN CONFLICT IF THE CROSS RELATED RULE IS TAKING ACTION

        assertEquals(Status.CONFLICT.getStatusCode(), actualResponse.getStatusInfo().getStatusCode());
        assertEquals("This relation creation is not allowed, it would result in a cyclic graph.",
                Util.getValueFromJson(actualResponse, "error"));

        try (Transaction tx = db.beginTx()) {
            Node node22 = tx.getNodeByElementId("22");
            Iterator<Relationship> rels = node22.getRelationships(ERelations.RELATED).iterator();

            assertFalse(rels.hasNext()); // make sure node 21 does not have a relationship now!
            tx.close();
        }
    }

    @Test
    public void createRelationshipTestWithCyclicConstraint() {
        RelationModel relationship = new RelationModel();
        Node firstNode;
        Node secondNode;

        try (Transaction tx = db.beginTx()) {
	        Result result = tx.execute("match (w:READING {text:'showers'}) return w");
	        Iterator<Node> nodes = result.columnAs("w");
	        assertTrue(nodes.hasNext());
	        firstNode = nodes.next();
	        assertFalse(nodes.hasNext());
	
	        result = tx.execute("match (w:READING {text:'pierced'}) return w");
	        nodes = result.columnAs("w");
	        assertTrue(nodes.hasNext());
	        secondNode = nodes.next();
	        assertFalse(nodes.hasNext());
	
	        relationship.setSource(firstNode.getElementId());
	        relationship.setTarget(secondNode.getElementId());
	        relationship.setType("grammatical");
	        relationship.setAlters_meaning(0L);
	        relationship.setIs_significant("yes");
	        
	        tx.close();
        }

        Response actualResponse = jerseyTest
                .target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(relationship));

        assertEquals(Status.CONFLICT.getStatusCode(), actualResponse.getStatusInfo().getStatusCode());
        // TODO (SK): ->TLA fix ErrorMessage (this one does not exist). Maybe we can define an enum?
/*        assertEquals(
                "This relationship creation is not allowed. Merging the two related readings would result in a cyclic graph.",
                actualResponse.getEntity(String.class));
*/
        try (Transaction tx = db.beginTx()) {
            Node node1 = tx.getNodeByElementId(firstNode.getElementId());
            Iterator<Relationship> rels = node1.getRelationships(ERelations.RELATED).iterator();

            assertFalse(rels.hasNext()); // make sure node does not have a relationship now!

            Node node2 = tx.getNodeByElementId(secondNode.getElementId());
            rels = node2.getRelationships(ERelations.RELATED).iterator();

            assertFalse(rels.hasNext()); // make sure node does not have a relationship now!
            tx.close();
        }
    }

    @Test
    public void createRelationshipOverCollatedTest() {
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "Legend", "LR", "1",
                "src/TestFiles/legendfrag.xml", "stemmaweb");
        String newTradId = Util.getValueFromJson(response, "tradId");

        List<RelationModel> allrels = jerseyTest.target("/tradition/" + newTradId + "/relations")
                .request()
                .get(new GenericType<List<RelationModel>>() {});

        // Try to overwrite a strong relationship
        List<RelationModel> orthorels = allrels.stream().filter(
                x -> x.getType().equals("orthographic"))
                .collect(Collectors.toList());
        assertFalse(orthorels.isEmpty());
        RelationModel rel = orthorels.get(0);
        rel.setType("spelling");
        rel.setScope("tradition");
        response = jerseyTest.target("/tradition/" + newTradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(rel));
        assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());

        List<RelationModel> collaterels = allrels.stream()
                .filter(x -> x.getType().equals("collated"))
                .collect(Collectors.toList());
        assertFalse(collaterels.isEmpty());
        rel = collaterels.get(0);
        // Change this to a stronger relationship type
        rel.setType("other");
        rel.setScope("local");
        rel.setIs_significant("yes");

        // Now try making the "new" relationship
        response = jerseyTest.target("/tradition/" + newTradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(rel));
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    }

    @Test
    public void createRelationshipBreakCollatedTest() {
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "Legend", "LR", "1",
                "src/TestFiles/legendfrag.xml", "stemmaweb");
        String newTradId = Util.getValueFromJson(response, "tradId");

        String myId;
        String otherId;
        try (Transaction tx = db.beginTx()) {
            List<Node> henries = tx.findNodes(Nodes.READING, "text", "henricus").stream()
                    .filter(x -> x.getProperty("rank").equals(4L)).collect(Collectors.toList());
            Node other = tx.findNode(Nodes.READING, "text", "heinricus");
            assertEquals(1, henries.size());
            assertNotNull(other);
            myId = henries.get(0).getElementId();
            otherId = other.getElementId();
            tx.close();
        }
        RelationModel r = new RelationModel();
        r.setSource(otherId);
        r.setTarget(myId);
        r.setType("spelling");
        r.setScope("local");
        r.setIs_significant("no");
        response = jerseyTest.target("/tradition/" + newTradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(r));
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    }

    @Test
    public void createBadRelationshipRestoreCollatedTest () {
        // Create a collation between rood and root
        String roodId;
        String the1Id = "";
        String the2Id = "";
        try (Transaction tx = db.beginTx()) {
            roodId = tx.findNode(Nodes.READING, "text", "rood").getElementId();
            List<Node> thes = tx.findNodes(Nodes.READING, "text", "the").stream()
                    .collect(Collectors.toList());
            for (Node the : thes) {
                if (the.getProperty("rank").equals(17L))
                    the1Id = the.getElementId();
                else
                    the2Id = the.getElementId();
            }
            tx.close();
        }
        assertTrue(!the1Id.isBlank());
        assertTrue(!the2Id.isBlank());        // Make a collated relationship between rood and the
        RelationModel model = new RelationModel();
        model.setSource(roodId);
        model.setTarget(the1Id);
        model.setType("collated");

        Response response = jerseyTest.target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(model));
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

        // Try to make a transposition relationship between rood and root

        model.setTarget(String.valueOf(the2Id));
        model.setType("orthographic");
        response = jerseyTest
                .target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(model));
        assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());

        // Check that the collated link is still there
        List<RelationModel> allRels = jerseyTest.target("/tradition/" + tradId + "/relations")
                .request()
                .get(new GenericType<List<RelationModel>>() {});
        assertEquals(4, allRels.size());
        boolean foundCollated = false;
        for (RelationModel r : allRels) {
            if (r.getType().equals("collated") && r.getSource().equals(String.valueOf(roodId))
                    && r.getTarget().equals(String.valueOf(the1Id)))
                foundCollated = true;
        }
        assertTrue(foundCollated);
    }

    @Test
    public void createRelationshipWrongTraditionTest() {
        Response jerseyResponse = Util.createTraditionFromFileOrString(jerseyTest, "Legend", "LR",
                "1", "src/TestFiles/legendfrag.xml", "stemmaweb");
        String newTradId = Util.getValueFromJson(jerseyResponse, "tradId");

        // We pretend to set a relationship between "ex" and "de"
        List<ReadingModel> lfReadings = jerseyTest.target("/tradition/" + newTradId + "/readings")
                .request()
                .get(new GenericType<List<ReadingModel>>() {}).stream().filter(x -> x.getRank() == 7)
                .collect(Collectors.toList());
        RelationModel model = new RelationModel();
        model.setType("lexical");
        for (ReadingModel rm : lfReadings) {
            if (rm.getText().equals("de")) model.setSource(rm.getId());
            if (rm.getText().equals("ex")) model.setTarget(rm.getId());
        }

        // Posting to the wrong tradition should fail
        jerseyResponse = jerseyTest
                .target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(model));
        assertEquals(Response.Status.CONFLICT.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());

        // Posting to the right tradition should succeed
        jerseyResponse = jerseyTest.target("/tradition/" + newTradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(model));
        assertEquals(Status.CREATED.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());
    }

    /*
     * Test if the get relationship method returns the correct value
     */
    @Test
    public void getRelationshipTest() {
        Response response = jerseyTest
                .target("/tradition/" + tradId + "/relations")
                .request()
                .get();
        assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());
        List<RelationModel> relationships = response.readEntity(new GenericType<List<RelationModel>>() {});
        assertEquals(3, relationships.size());
        for (RelationModel rel : relationships) {
            assertEquals("local", rel.getScope());
            assertEquals("transposition", rel.getType());
            assertNull(rel.getSource_reading());
            assertNull(rel.getTarget_reading());
        }

        // Now get the relation including reading information
        response = jerseyTest.target("/tradition/" + tradId + "/relations")
                .queryParam("include_readings", "true")
                .request().get();
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        relationships = response.readEntity(new GenericType<List<RelationModel>>() {});
        assertEquals(3, relationships.size());
        for (RelationModel rel : relationships) {
            assertEquals("local", rel.getScope());
            assertEquals("transposition", rel.getType());
            assertNotNull(rel.getSource_reading());
            assertNotNull(rel.getTarget_reading());
            assertEquals(rel.getSource_reading().getText(), rel.getTarget_reading().getText());
        }
    }

    /**
     * Test if the get relationship method returns correct state
     */
    @Test
    public void getRelationshipCorrectStatusTest(){
        Response response = jerseyTest
                .target("/tradition/" + tradId + "/relations")
                .request()
                .get();
        assertEquals(Response.ok().build().getStatus(), response.getStatus());
    }

    /**
     * Test if the get relationship method returns the correct not found error on a non existing tradid
     */
    @Test
    public void getRelationshipExceptionTest() {
        Response response = jerseyTest
                .target("/tradition/6999/relations")
                .request(MediaType.APPLICATION_JSON)
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void getNoRelationshipTest(){
         /*
         * load a tradition with no Realtionships to the test DB
         */
        Response jerseyResponse = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/testTraditionNoRealtions.xml", "stemmaweb");
        String newTradId = Util.getValueFromJson(jerseyResponse, "tradId");

        Response response = jerseyTest
                .target("/tradition/" + newTradId + "/relations")
                .request()
                .get();
        assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());
        assertEquals("[]", response.readEntity(String.class));
    }

    private Node getReading(String text, Comparator<Node> c) {
        Node result;
        try (Transaction tx = db.beginTx()) {
            List<Node> available = tx.findNodes(Nodes.READING, "text", text).stream().collect(Collectors.toList());
            if (c != null)
                available.sort(c);
            tx.close();
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
