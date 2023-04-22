package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;

import net.stemmaweb.rest.ERelations;

/**
 * 
 * Contains all tests for the api calls related to unicode compatibility.
 * 
 * @author PSE FS 2015 Team2
 *
 */
public class UnicodeTest {

    private GraphDatabaseService graphDb;
	private DatabaseManagementService dbbuilder;

    @Before
    public void prepareTestDatabase() {
//        graphDb = new TestGraphDatabaseFactory().newImpermanentDatabase();
    	dbbuilder = new TestDatabaseManagementServiceBuilder().build();
    	dbbuilder.createDatabase("stemmatest");
    	graphDb = dbbuilder.database("stemmatest");
        // create a new Graph Database
    }

    @Test
    public void testUnicodeCapability() {
        Node n;
        try (Transaction tx = graphDb.beginTx()) {
            n = tx.createNode();
            n.setProperty("name", "Ã¤Ã¶Ã¼×“×’×›Î±Î²Î³");
            tx.commit();
        }

        // The node should have a valid id
        assertTrue(!n.getElementId().isBlank());

        // Retrieve a node by using the id of the created node. The id's and
        // property should match.
        try (Transaction tx = graphDb.beginTx()) {
            Node foundNode = tx.getNodeByElementId(n.getElementId());
            assertEquals(foundNode.getElementId(), n.getElementId());
            assertEquals("Ã¤Ã¶Ã¼×“×’×›Î±Î²Î³", foundNode.getProperty("name"));
            tx.close();
        }
    }

    @Test
    public void testHebrewCapabilityMatch() {
        Node node1;
        Node node2;
        Relationship relationship;
        try (Transaction tx = graphDb.beginTx()) {
            node1 = tx.createNode();
            node1.setProperty("name", "בדיקה");
            node2 = tx.createNode();
            node2.setProperty("name", "עוד בדיקה");

            relationship = node1.createRelationshipTo(node2, ERelations.SEQUENCE);
            relationship.setProperty("type", "יחס");
            tx.commit();
        }

        // The node should have a valid id
        assertTrue(!node1.getElementId().isBlank());
        assertTrue(!node2.getElementId().isBlank());
        assertTrue(!relationship.getElementId().isBlank());

        // Retrieve nodes and relationship by using the id of the created node. The id's and
        // property should match.
        try (Transaction tx = graphDb.beginTx()) {
            Node foundNode1 = tx.getNodeByElementId(node1.getElementId());
            Node foundNode2 = tx.getNodeByElementId(node2.getElementId());
            Relationship foundRelationship = tx.getRelationshipByElementId(relationship.getElementId());

            assertEquals(foundNode1.getElementId(), node1.getElementId());
            assertEquals("בדיקה", foundNode1.getProperty("name"));
            assertEquals("עוד בדיקה", foundNode2.getProperty("name"));
            assertEquals("יחס", foundRelationship.getProperty("type"));
            tx.close();
        }
    }

    @Test
    public void testHebrewCapabilityNoMatch() {
        Node n;
        try (Transaction tx = graphDb.beginTx()) {
            n = tx.createNode();
            n.setProperty("name", "בדיקה");
            tx.commit();
        }

        // The node should have a valid id
        assertTrue(!n.getElementId().isBlank());

        // Retrieve a node by using the id of the created node. The id's and
        // property should match.
        try (Transaction tx = graphDb.beginTx()) {
            Node foundNode = tx.getNodeByElementId(n.getElementId());
            assertEquals(foundNode.getElementId(), n.getElementId());
            assertNotEquals("בליקה", foundNode.getProperty("name"));
            tx.close();
        }
    }

    @Test
    public void testGreekCapabilityMatch() {
        Node n;
        try (Transaction tx = graphDb.beginTx()) {
            n = tx.createNode();
            n.setProperty("name", "ειπον");
            tx.commit();
        }

        // The node should have a valid id
        assertTrue(!n.getElementId().isBlank());

        // Retrieve a node by using the id of the created node. The id's and
        // property should match.
        try (Transaction tx = graphDb.beginTx()) {
            Node foundNode = tx.getNodeByElementId(n.getElementId());
            assertEquals(foundNode.getElementId(), n.getElementId());
            assertEquals("ειπον", foundNode.getProperty("name"));
            tx.close();
        }
    }

    @Test
    public void testGreekCapabilityNoMatch() {
        Node n;
        try (Transaction tx = graphDb.beginTx()) {
            n = tx.createNode();
            n.setProperty("name", "ειπον");
            tx.commit();
        }

        // The node should have a valid id
        assertTrue(!n.getElementId().isBlank());

        // Retrieve a node by using the id of the created node. The id's and
        // property should match.
        try (Transaction tx = graphDb.beginTx()) {
            Node foundNode = tx.getNodeByElementId(n.getElementId());
            assertEquals(foundNode.getElementId(), n.getElementId());
            assertNotEquals("ειπων", foundNode.getProperty("name"));
            tx.close();
        }
    }

    @Test
    public void testArabicCapabilityMatch() {
        Node n;
        try (Transaction tx = graphDb.beginTx()) {
            n = tx.createNode();
            n.setProperty("name", "المطلق");
            tx.commit();
        }

        // The node should have a valid id
        assertTrue(!n.getElementId().isBlank());

        // Retrieve a node by using the id of the created node. The id's and
        // property should match.
        try (Transaction tx = graphDb.beginTx()) {
            Node foundNode = tx.getNodeByElementId(n.getElementId());
            assertEquals(foundNode.getElementId(), n.getElementId());
            assertEquals("المطلق", foundNode.getProperty("name"));
            tx.close();
        }
    }

    @Test
    public void testArabicCapabilityNoMatch() {
        Node n;
        try (Transaction tx = graphDb.beginTx()) {
            n = tx.createNode();
            n.setProperty("name", "المطلق");
            tx.commit();
        }

        // The node should have a valid id
        assertTrue(!n.getElementId().isBlank());

        // Retrieve a node by using the id of the created node. The id's and
        // property should match.
        try (Transaction tx = graphDb.beginTx()) {
            Node foundNode = tx.getNodeByElementId(n.getElementId());
            assertEquals(foundNode.getElementId(), n.getElementId());
            assertNotEquals("المطلو", foundNode.getProperty("name"));
            tx.close();
        }
    }

    @After
    public void destroyTestDatabase() {
//        graphDb.shutdown();    // destroy the test database
    	if (dbbuilder != null) {
    		dbbuilder.shutdownDatabase(graphDb.databaseName());
    	}
    }
}