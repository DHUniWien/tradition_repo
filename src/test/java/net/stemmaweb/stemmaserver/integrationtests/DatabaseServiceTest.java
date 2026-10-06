package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;

import jakarta.ws.rs.core.Response;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.VariantGraphService;
import net.stemmaweb.stemmaserver.Util;

/**
 * 
 * @author PSE FS 2015 Team2
 *
 */
public class DatabaseServiceTest {

    private GraphDatabaseService db;
    private String traditionId;
    private String userId;

    @Before
    public void setUp() throws Exception {

//      db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
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

    @Test
    public void getRelatedTest() {
        Response response;
        try (Transaction tx = db.beginTx()) {
        	Node tradition = VariantGraphService.getTraditionNode(tx, traditionId);
        	ArrayList<Node> witnesses = DatabaseService.getRelated(tradition, ERelations.HAS_WITNESS);
        	assertEquals(3, witnesses.size());
        	tx.close();
        } catch (Exception e) {
        	e.printStackTrace();
        }
    }

    @Test
    public void userExistsTest() {
    	try (Transaction tx = db.beginTx()) {
    		assertTrue(DatabaseService.userExists(tx, userId));
    		tx.close();
    	}
    }

    @Test
    public void testEnsureConstraintsIdempotent() {
        try (Transaction tx = db.beginTx()) {
            DatabaseService.ensureConstraints(tx);
            tx.commit();
        }
        try (Transaction tx = db.beginTx()) {
            DatabaseService.ensureConstraints(tx); // must not throw on second call
            tx.commit();
        }
    }

    @Test
    public void testAssignIdIfCoveredGivesUniqueSequentialIds() {
        try (Transaction tx = db.beginTx()) {
            Node r1 = DatabaseService.createNode(tx, Nodes.READING);
            Node r2 = DatabaseService.createNode(tx, Nodes.READING);
            Node s1 = DatabaseService.createNode(tx, Nodes.SECTION);
            assertEquals(1L, r1.getProperty("id"));
            assertEquals(2L, r2.getProperty("id"));
            // independent per-type counter -- starts at 2 here because setUp()'s own tradition
            // import (via Tradition.createNewSection, now routed through DatabaseService.createNode
            // as of the Section entity-id task) already minted section id 1
            assertEquals(2L, s1.getProperty("id"));
            tx.commit();
        }
    }

    @Test
    public void testCreateNodeSkipsIdForUncoveredLabel() {
        try (Transaction tx = db.beginTx()) {
            Node w = DatabaseService.createNode(tx, Nodes.WITNESS);
            assertFalse(w.hasProperty("id"));
            tx.commit();
        }
    }

    @Test
    public void testFindNodeOrThrowThrowsNumberFormatExceptionOnBadId() {
        try (Transaction tx = db.beginTx()) {
            assertThrows(NumberFormatException.class,
                () -> DatabaseService.findNodeOrThrow(tx, Nodes.READING, "not-a-number"));
        }
    }

    @Test
    public void testFindNodeOrThrowThrowsNotFoundOnMissingId() {
        try (Transaction tx = db.beginTx()) {
            assertThrows(NotFoundException.class,
                () -> DatabaseService.findNodeOrThrow(tx, Nodes.READING, "999999"));
        }
    }

    @Test
    public void testCreateRelatedRelationshipAssignsId() {
        try (Transaction tx = db.beginTx()) {
            Node a = DatabaseService.createNode(tx, Nodes.READING);
            Node b = DatabaseService.createNode(tx, Nodes.READING);
            Relationship rel = DatabaseService.createRelatedRelationship(tx, a, b);
            assertEquals(1L, rel.getProperty("id"));
            assertEquals(rel, DatabaseService.findRelatedOrThrow(tx, "1"));
            tx.commit();
        }
    }

    /*
     * Shut down the database
     */
    @After
    public void tearDown() {
//        db.shutdown();
    	GraphDatabaseServiceProvider.shutdown();
    }
}
