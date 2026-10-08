package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;

import jakarta.ws.rs.core.Response;
import net.stemmaweb.model.RelationModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Relation;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.VariantGraphService;
import net.stemmaweb.stemmaserver.Util;

public class VariantGraphServiceTest {
    private GraphDatabaseService db;
    private String traditionId;
    private String userId;

    @Before
    public void setUp() throws Exception {

//        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        DatabaseManagementService dbbuilder = new TestDatabaseManagementServiceBuilder().impermanent().build();
    	db = dbbuilder.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME);
    	new GraphDatabaseServiceProvider(dbbuilder, db);
        userId = "simon";
        Util.setupTestDB(db, userId);

        /*
         * load a tradition to the test DB, without Jersey
         */
        Response result = Util.createTraditionDirectly("Tradition", "LR", userId,
                "src/TestFiles/testTradition.xml", "stemmaweb");
        assertEquals(Response.Status.CREATED.getStatusCode(), result.getStatus());
        /*
         * gets the generated id of the inserted tradition
         */
        traditionId = Util.getValueFromJson(result, "tradId");
    }

    // public void sectionInTraditionTest()

    @Test
    public void getStartNodeTest() {
        try (Transaction tx = db.beginTx()) {
            Node startNode = VariantGraphService.getStartNode(tx, traditionId);
            assertNotNull(startNode);
            assertEquals("#START#", startNode.getProperty("text"));
            assertEquals(true, startNode.getProperty("is_start"));
        }
    }

    @Test
    public void getEndNodeTest() {
        try (Transaction tx = db.beginTx()) {
            Node endNode = VariantGraphService.getEndNode(tx, traditionId);
            assertNotNull(endNode);
            assertEquals("#END#", endNode.getProperty("text"));
            assertEquals(true, endNode.getProperty("is_end"));
        }
    }

    @Test
    public void getSectionNodesTest() {
        try (Transaction tx = db.beginTx()) {
        	ArrayList<Node> sectionNodes = VariantGraphService.getSectionNodes(tx, traditionId);
        	assertEquals(1, sectionNodes.size());
            assertTrue(sectionNodes.getFirst().hasLabel(Label.label("SECTION")));
        }
    }

    @Test
    public void getTraditionNodeTest() {
    	try (Transaction tx = db.beginTx()) {
    		Node foundTradition = VariantGraphService.getTraditionNode(tx, traditionId);
    		assertNotNull(foundTradition);
    		// Now by section node
    		ArrayList<Node> sectionNodes = VariantGraphService.getSectionNodes(tx, traditionId);
    		assertEquals(1, sectionNodes.size());
    		assertEquals(foundTradition, VariantGraphService.getTraditionNode(tx, sectionNodes.getFirst()));
    	}
    }

    @Test
    public void normalizeGraphTest() {
        String newTradId = Util.getValueFromJson(
                Util.createTraditionDirectly("Tradition", "LR", userId,
                        "src/TestFiles/globalrel_test.xml", "stemmaweb"),
                "tradId"
        );
        try (Transaction tx = db.beginTx()) {
        	ArrayList<Node> sections = VariantGraphService.getSectionNodes(tx, newTradId);
            HashMap<Node,Node> representatives = VariantGraphService.normalizeGraph(tx, sections.getFirst(), "collated");
            for (Node n : representatives.keySet()) {
                // If it is represented by itself, it should have an NSEQUENCE both in and out; if not, not.
                if (!n.hasProperty("is_end"))
                    assertEquals(n.equals(representatives.get(n)), n.hasRelationship(Direction.OUTGOING, ERelations.NSEQUENCE));
                if (!n.hasProperty("is_start"))
                    assertEquals(n.equals(representatives.get(n)), n.hasRelationship(Direction.INCOMING, ERelations.NSEQUENCE));
                // If it's at rank 6, it should have a REPRESENTS link
                if (n.getProperty("rank").equals(6L)) {
                    Direction d = n.getProperty("text").equals("weljellensä") ? Direction.OUTGOING : Direction.INCOMING;
                    assertTrue(n.hasRelationship(d, ERelations.REPRESENTS));
                } else if (n.getProperty("rank").equals(9L)) {
                    Direction d = n.getProperty("text").equals("Hämehen") ? Direction.OUTGOING : Direction.INCOMING;
                    assertTrue(n.hasRelationship(d, ERelations.REPRESENTS));
                }
            }
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void calculateMajorityTest() {
        String newTradId = Util.getValueFromJson(
                Util.createTraditionDirectly("Tradition", "LR", userId,
                        "src/TestFiles/globalrel_test.xml", "stemmaweb"),
                "tradId"
        );
        String expectedMajority = "sanoi herra Heinärickus Erjkillen weljellensä Läckämme Hämehen maallen";
        try (Transaction tx = db.beginTx()) {
        	ArrayList<Node> sections = VariantGraphService.getSectionNodes(tx, newTradId);
            List<Node> majorityReadings = VariantGraphService.calculateMajorityText(tx, sections.getFirst());
            List<String> words = majorityReadings.stream()
                    .filter(x -> !x.hasProperty("is_start") && !x.hasProperty("is_end"))
                    .map(x -> x.getProperty("text").toString()).toList();
            assertEquals(expectedMajority, String.join(" ", words));
            
            // Now lemmatize some smaller readings, normalize, and make sure the majority text adjusts
            expectedMajority = "sanoi herra Heinäricki Erjkillen weliellensä Läckämme Hämehen maallen";
            // Lemmatise a minority reading
            Node n = tx.findNode(Nodes.READING, "text", "weliellensä");
            assertNotNull(n);
            n.setProperty("is_lemma", true);
            // Collate two readings so that together they outweigh the otherwise-majority
            Node n1 = tx.findNode(Nodes.READING, "text", "Heinäricki");
            Node n2 = tx.findNode(Nodes.READING, "text", "Henärickus");
            RelationModel rm = new RelationModel();
            rm.setSource(n1.getProperty("id").toString());
            rm.setTarget(n2.getProperty("id").toString());
            rm.setType("collated");
            rm.setScope("local");
            Relation relRest = new Relation(newTradId);
            try (Response r = relRest.create(rm)) {
                assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
            }
            VariantGraphService.normalizeGraph(tx, sections.getFirst(), "collated"); // TODO not sure this has an effect??
            majorityReadings = VariantGraphService.calculateMajorityText(tx, sections.getFirst());
            words = majorityReadings.stream()
            		.filter(x -> !x.hasProperty("is_start") && !x.hasProperty("is_end"))
            		.map(x -> x.getProperty("text").toString()).toList();
            assertEquals(expectedMajority, String.join(" ", words));
        } catch (Exception e) {
            fail();
        }
    }

    // clearMajorityTest()

    // returnEntireTraditionTest()

    // returnTraditionSectionTest()

    // returnTraditionRelationsTest()

    /*
     * Shut down the database
     */
    @After
    public void tearDown() {
//        db.shutdown();
    	GraphDatabaseServiceProvider.shutdown();
    }

}
