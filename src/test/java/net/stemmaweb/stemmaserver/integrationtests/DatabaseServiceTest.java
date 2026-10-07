package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
            // setUp()'s own tradition import (via the stemmaweb parser and
            // Tradition.createNewSection, both now routed through DatabaseService.createNode)
            // already minted some number of reading and section ids, so the counters can't be
            // assumed to start at 0 here -- capture the baseline first, then assert relative to it.
            long baseReading = (long) DatabaseService.createNode(tx, Nodes.READING).getProperty("id");
            long baseSection = (long) DatabaseService.createNode(tx, Nodes.SECTION).getProperty("id");

            Node r1 = DatabaseService.createNode(tx, Nodes.READING);
            Node r2 = DatabaseService.createNode(tx, Nodes.READING);
            Node s1 = DatabaseService.createNode(tx, Nodes.SECTION);
            assertEquals(baseReading + 1, r1.getProperty("id"));
            assertEquals(baseReading + 2, r2.getProperty("id"));
            // independent per-type counter
            assertEquals(baseSection + 1, s1.getProperty("id"));
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
            // setUp()'s own tradition import (via the stemmaweb parser, now routed through
            // DatabaseService.createRelatedRelationship for its RELATED links) already minted
            // some number of relation ids, so the counter can't be assumed to start at 0 here --
            // capture the baseline first, then assert relative to it (same approach as
            // testAssignIdIfCoveredGivesUniqueSequentialIds above).
            Node baseA = DatabaseService.createNode(tx, Nodes.READING);
            Node baseB = DatabaseService.createNode(tx, Nodes.READING);
            long baseRelation = (long) DatabaseService.createRelatedRelationship(tx, baseA, baseB).getProperty("id");

            Node a = DatabaseService.createNode(tx, Nodes.READING);
            Node b = DatabaseService.createNode(tx, Nodes.READING);
            Relationship rel = DatabaseService.createRelatedRelationship(tx, a, b);
            long expectedId = baseRelation + 1;
            assertEquals(expectedId, rel.getProperty("id"));
            assertEquals(rel, DatabaseService.findRelatedOrThrow(tx, String.valueOf(expectedId)));
            tx.commit();
        }
    }

    @Test
    public void testRapidSequentialCreationNeverDuplicatesId() {
        try (Transaction tx = db.beginTx()) {
            HashSet<Long> seen = new HashSet<>();
            for (int i = 0; i < 50; i++) {
                Node r = DatabaseService.createNode(tx, Nodes.READING);
                assertTrue(seen.add((Long) r.getProperty("id")));
            }
            tx.commit();
        }
    }

    @Test
    public void testConcurrentCreationNeverDuplicatesId() throws Exception {
        // testRapidSequentialCreationNeverDuplicatesId above runs everything in one
        // transaction, so it cannot catch a race between two genuinely concurrent
        // transactions. This test uses two real transactions, interleaved via explicit
        // handshaking so that the second transaction's nextId call is guaranteed to be
        // attempted while the first transaction's is still open (and, before the fix,
        // uncommitted) -- which is exactly the window the ROOT-node write lock closes.
        CountDownLatch aHasCreatedNode = new CountDownLatch(1);
        CountDownLatch bIsAboutToCreateNode = new CountDownLatch(1);
        AtomicLong idA = new AtomicLong(-1);
        AtomicLong idB = new AtomicLong(-1);
        AtomicReference<Throwable> failureA = new AtomicReference<>();
        AtomicReference<Throwable> failureB = new AtomicReference<>();

        Thread threadA = new Thread(() -> {
            try (Transaction tx = db.beginTx()) {
                Node r = DatabaseService.createNode(tx, Nodes.READING);
                idA.set((long) r.getProperty("id"));
                aHasCreatedNode.countDown();
                // Give thread B a real chance to reach its own nextId call (and block on
                // the ROOT write lock, if the fix is in place) before we commit and
                // release the lock.
                bIsAboutToCreateNode.await(5, TimeUnit.SECONDS);
                Thread.sleep(300);
                tx.commit();
            } catch (Throwable e) {
                failureA.set(e);
            }
        });

        Thread threadB = new Thread(() -> {
            try {
                aHasCreatedNode.await(5, TimeUnit.SECONDS);
                try (Transaction tx = db.beginTx()) {
                    bIsAboutToCreateNode.countDown();
                    Node r = DatabaseService.createNode(tx, Nodes.READING);
                    idB.set((long) r.getProperty("id"));
                    tx.commit();
                }
            } catch (Throwable e) {
                failureB.set(e);
            }
        });

        threadA.start();
        threadB.start();
        threadA.join(10000);
        threadB.join(10000);

        if (failureA.get() != null) throw new AssertionError("Thread A failed", failureA.get());
        if (failureB.get() != null) throw new AssertionError("Thread B failed", failureB.get());
        assertTrue("Both threads should have gotten an id", idA.get() >= 0 && idB.get() >= 0);
        assertTrue("Concurrent creation must never assign the same id twice", idA.get() != idB.get());
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
