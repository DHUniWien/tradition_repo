package net.stemmaweb.stemmaserver.integrationtests;

import net.stemmaweb.model.RelationModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Relation;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.VariantGraphService;
import net.stemmaweb.stemmaserver.Util;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.*;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.Assert.*;

public class VariantGraphServiceTest {
    private GraphDatabaseService db;
    private String traditionId;
    private String userId;

    @Before
    public void setUp() throws Exception {

        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
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
            Node startNode = VariantGraphService.getStartNode(traditionId, db);
            assertNotNull(startNode);
            assertEquals("#START#", startNode.getProperty("text"));
            assertEquals(true, startNode.getProperty("is_start"));
            tx.success();
        }
    }

    @Test
    public void getEndNodeTest() {
        try (Transaction tx = db.beginTx()) {
            Node endNode = VariantGraphService.getEndNode(traditionId, db);
            assertNotNull(endNode);
            assertEquals("#END#", endNode.getProperty("text"));
            assertEquals(true, endNode.getProperty("is_end"));
            tx.success();
        }
    }

    @Test
    public void getSectionNodesTest() {
        ArrayList<Node> sectionNodes = VariantGraphService.getSectionNodes(traditionId, db);
        assertNotNull(sectionNodes);
        assertEquals(1, sectionNodes.size());
        try (Transaction tx = db.beginTx()) {
            assertTrue(sectionNodes.get(0).hasLabel(Label.label("SECTION")));
            tx.success();
        }
    }

    @Test
    public void getTraditionNodeTest() {
        Node foundTradition = VariantGraphService.getTraditionNode(traditionId, db);
        assertNotNull(foundTradition);
        // Now by section node
        ArrayList<Node> sectionNodes = VariantGraphService.getSectionNodes(traditionId, db);
        assertNotNull(sectionNodes);
        assertEquals(1, sectionNodes.size());
        assertEquals(foundTradition, VariantGraphService.getTraditionNode(sectionNodes.get(0)));
    }

    @Test
    public void normalizeGraphTest() {
        String newTradId = Util.getValueFromJson(
                Util.createTraditionDirectly("Tradition", "LR", userId,
                        "src/TestFiles/globalrel_test.xml", "stemmaweb"),
                "tradId"
        );
        ArrayList<Node> sections = VariantGraphService.getSectionNodes(newTradId, db);
        assertNotNull(sections);
        try (Transaction tx = db.beginTx()) {
            HashMap<Node,Node> representatives = VariantGraphService.normalizeGraph(sections.get(0), "collated");
            for (Node n : representatives.keySet()) {
                // If it is represented by itself, it should have an NSEQUENCE both in and out; if not, not.
                if (!n.hasProperty("is_end"))
                    assertEquals(n.equals(representatives.get(n)), n.hasRelationship(ERelations.NSEQUENCE, Direction.OUTGOING));
                if (!n.hasProperty("is_start"))
                    assertEquals(n.equals(representatives.get(n)), n.hasRelationship(ERelations.NSEQUENCE, Direction.INCOMING));
                // If it's at rank 6, it should have a REPRESENTS link
                if (n.getProperty("rank").equals(6L)) {
                    Direction d = n.getProperty("text").equals("weljellensä") ? Direction.OUTGOING : Direction.INCOMING;
                    assertTrue(n.hasRelationship(ERelations.REPRESENTS, d));
                } else if (n.getProperty("rank").equals(9L)) {
                    Direction d = n.getProperty("text").equals("Hämehen") ? Direction.OUTGOING : Direction.INCOMING;
                    assertTrue(n.hasRelationship(ERelations.REPRESENTS, d));
                }
            }
            tx.success();
        } catch (Exception e) {
            fail();
        }

        // Now clear the normalization and make sure we didn't fail.
        try (Transaction tx = db.beginTx()) {
            VariantGraphService.clearNormalization(sections.get(0));
            assertTrue(db.getAllRelationships().stream().noneMatch(x -> x.isType(ERelations.NSEQUENCE)));
            assertTrue(db.getAllRelationships().stream().noneMatch(x -> x.isType(ERelations.REPRESENTS)));
            tx.success();
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
        ArrayList<Node> sections = VariantGraphService.getSectionNodes(newTradId, db);
        assertNotNull(sections);
        String sectId = String.valueOf(sections.get(0).getId());
        Node startNode = VariantGraphService.getStartNode(sectId, db);
        String expectedMajority = "sanoi herra Heinärickus Erjkillen weljellensä Läckämme Hämehen maallen";
        try (Transaction tx = db.beginTx()) {
            VariantGraphService.calculateMajorityText(sections.get(0));
            // We should be able to crawl along from the start node and get the text
            Node current = startNode;
            ArrayList<String> words = new ArrayList<>();
            Node endNode = VariantGraphService.getEndNode(sectId, db);
            while (current.hasRelationship(ERelations.MAJORITY, Direction.OUTGOING)) {
                Node next = current.getSingleRelationship(ERelations.MAJORITY, Direction.OUTGOING).getEndNode();
                if (!next.equals(endNode))
                    words.add(next.getProperty("text").toString());
                current = next;
            }
            assertEquals(expectedMajority, String.join(" ", words));
            tx.success();
        } catch (Exception e) {
            fail();
        }

        // Clear the majority text and make sure it really went
        try (Transaction tx = db.beginTx()) {
            VariantGraphService.clearMajorityText(sections.get(0));
            assertTrue(db.getAllRelationships().stream().noneMatch(x -> x.isType(ERelations.MAJORITY)));
            tx.success();
        } catch (Exception e) {
            fail();
        }

        // Now lemmatize some smaller readings, normalize, and make sure the majority text adjusts
        expectedMajority = "sanoi herra Heinäricki Erjkillen weliellensä Läckämme Hämehen maallen";
        try (Transaction tx = db.beginTx()) {
            // Lemmatise a minority reading
            Node n = db.findNode(Nodes.READING, "text", "weliellensä");
            assertNotNull(n);
            n.setProperty("is_lemma", true);
            // Collate two readings so that together they outweigh the otherwise-majority
            Node n1 = db.findNode(Nodes.READING, "text", "Heinäricki");
            Node n2 = db.findNode(Nodes.READING, "text", "Henärickus");
            RelationModel rm = new RelationModel();
            rm.setSource(String.valueOf(n1.getId()));
            rm.setTarget(String.valueOf(n2.getId()));
            rm.setType("collated");
            rm.setScope("local");
            Relation relRest = new Relation(newTradId);
            Response r = relRest.create(rm);
            assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
            VariantGraphService.normalizeGraph(sections.get(0), "collated");
            VariantGraphService.calculateMajorityText(sections.get(0));
            Node current = startNode;
            ArrayList<String> words = new ArrayList<>();
            while (current.hasRelationship(ERelations.MAJORITY, Direction.OUTGOING)) {
                Node next = current.getSingleRelationship(ERelations.MAJORITY, Direction.OUTGOING).getEndNode();
                if (next.getProperty("is_end", false).equals(false))
                    words.add(next.getProperty("text").toString());
                current = next;
            }
            assertEquals(expectedMajority, String.join(" ", words));
            tx.success();
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
        db.shutdown();
    }

}
