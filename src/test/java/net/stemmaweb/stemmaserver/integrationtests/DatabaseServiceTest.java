package net.stemmaweb.stemmaserver.integrationtests;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;

import jakarta.ws.rs.core.Response;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.AmbiguousReferenceException;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.NameConflictException;
import net.stemmaweb.services.VariantGraphService;
import net.stemmaweb.stemmaserver.Util;

import static org.junit.Assert.*;

/**
 * 
 * @author PSE FS 2015 Team2
 *
 */
public class DatabaseServiceTest {

    // The labels that get an application-assigned, sequential "id" property
    private static final Label[] MANAGED = {
            Nodes.READING, Nodes.SECTION, Nodes.ANNOTATION,
            Nodes.WITNESS, Nodes.STEMMA, Nodes.RELATION_TYPE, Nodes.ANNOTATIONLABEL
    };

    // An arbitrary relationship type that is not RELATED
    private static final RelationshipType TEST_CHILD = RelationshipType.withName("TEST_CHILD");

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
        try (Transaction tx = db.beginTx()) {
        	Node tradition = VariantGraphService.getTraditionNode(tx, traditionId);
        	ArrayList<Node> witnesses = DatabaseService.getRelated(tradition, ERelations.HAS_WITNESS);
        	assertEquals(3, witnesses.size());
        } catch (Exception e) {
        	fail(e.getMessage());
        }
    }

    @Test
    public void userExistsTest() {
    	try (Transaction tx = db.beginTx()) {
    		assertTrue(DatabaseService.userExists(tx, userId));
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
    public void testAssignIdIfManagedGivesUniqueSequentialIds() {
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
    public void testCreateNodeSkipsIdForUnmanagedLabel() {
        try (Transaction tx = db.beginTx()) {
            Node t = DatabaseService.createNode(tx, Nodes.TRADITION);
            assertFalse(t.hasProperty("id"));
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
            // testAssignIdIfManagedGivesUniqueSequentialIds above).
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
            for (Label label : MANAGED) {
                HashSet<Long> seen = new HashSet<>();
                for (int i = 0; i < 50; i++) {
                    Node n = DatabaseService.createNode(tx, label);
                    assertTrue("duplicate id for label " + label.name(), seen.add((Long) n.getProperty("id")));
                }
            }
            tx.commit();
        }
    }

    @Test
    public void testConcurrentCreationNeverDuplicatesId() throws Exception {
        for (Label label : MANAGED) {
            assertConcurrentCreationNeverDuplicatesIdFor(label);
        }
    }

    // testRapidSequentialCreationNeverDuplicatesId above runs everything in one
    // transaction, so it cannot catch a race between two genuinely concurrent
    // transactions. This uses two real transactions, interleaved via explicit
    // handshaking so that the second transaction's nextId call is guaranteed to be
    // attempted while the first transaction's is still open (and, before the fix,
    // uncommitted) -- which is exactly the window the ROOT-node write lock closes.
    private void assertConcurrentCreationNeverDuplicatesIdFor(Label label) throws Exception {
        CountDownLatch aHasCreatedNode = new CountDownLatch(1);
        CountDownLatch bIsAboutToCreateNode = new CountDownLatch(1);
        AtomicLong idA = new AtomicLong(-1);
        AtomicLong idB = new AtomicLong(-1);
        AtomicReference<Throwable> failureA = new AtomicReference<>();
        AtomicReference<Throwable> failureB = new AtomicReference<>();

        Thread threadA = new Thread(() -> {
            try (Transaction tx = db.beginTx()) {
                Node r = DatabaseService.createNode(tx, label);
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
                    Node r = DatabaseService.createNode(tx, label);
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

        if (failureA.get() != null) throw new AssertionError("Thread A failed for label " + label.name(), failureA.get());
        if (failureB.get() != null) throw new AssertionError("Thread B failed for label " + label.name(), failureB.get());
        assertTrue("Both threads should have gotten an id for label " + label.name(), idA.get() >= 0 && idB.get() >= 0);
        assertTrue("Concurrent creation must never assign the same id twice for label " + label.name(), idA.get() != idB.get());
    }

    // --- resolveManagedRef -------------------------------------------------------------

    /**
     * Builds a small fixture directly via the Node API (no REST layer involved): a parent
     * node with three WITNESS children linked by the test-local TEST_CHILD relationship
     * type, two with distinct sigils and two sharing the same sigil -- seeded directly to
     * exercise the ambiguous path, since the REST layer will never again produce this
     * duplicate-name state once Task 2's create/rename uniqueness check lands.
     */
    private Node[] buildResolveRefFixture(Transaction tx) {
        Node parent = tx.createNode(Nodes.TRADITION);
        Node unique1 = DatabaseService.createNode(tx, Nodes.WITNESS);
        unique1.setProperty("sigil", "A");
        parent.createRelationshipTo(unique1, TEST_CHILD);
        Node unique2 = DatabaseService.createNode(tx, Nodes.WITNESS);
        unique2.setProperty("sigil", "B");
        parent.createRelationshipTo(unique2, TEST_CHILD);
        Node dup1 = DatabaseService.createNode(tx, Nodes.WITNESS);
        dup1.setProperty("sigil", "DUP");
        parent.createRelationshipTo(dup1, TEST_CHILD);
        Node dup2 = DatabaseService.createNode(tx, Nodes.WITNESS);
        dup2.setProperty("sigil", "DUP");
        parent.createRelationshipTo(dup2, TEST_CHILD);
        return new Node[] {parent, unique1, unique2, dup1, dup2};
    }

    @Test
    public void testResolveManagedRefByIdSucceeds() {
        try (Transaction tx = db.beginTx()) {
            Node[] fixture = buildResolveRefFixture(tx);
            Node parent = fixture[0];
            Node unique1 = fixture[1];
            List<Node> candidates = DatabaseService.getRelated(parent, TEST_CHILD);

            String idRef = String.valueOf(unique1.getProperty("id"));
            assertEquals(unique1, DatabaseService.resolveManagedRef(tx, Nodes.WITNESS, candidates, idRef, "sigil"));
        }
    }

    @Test
    public void testResolveManagedRefByUniqueNameSucceeds() {
        try (Transaction tx = db.beginTx()) {
            Node[] fixture = buildResolveRefFixture(tx);
            Node parent = fixture[0];
            Node unique1 = fixture[1];
            List<Node> candidates = DatabaseService.getRelated(parent, TEST_CHILD);

            assertEquals(unique1, DatabaseService.resolveManagedRef(tx, Nodes.WITNESS, candidates, "A", "sigil"));
        }
    }

    @Test
    public void testResolveManagedRefByNameWithNoMatchThrowsNotFound() {
        try (Transaction tx = db.beginTx()) {
            Node parent = buildResolveRefFixture(tx)[0];
            List<Node> candidates = DatabaseService.getRelated(parent, TEST_CHILD);

            assertThrows(NotFoundException.class,
                () -> DatabaseService.resolveManagedRef(tx, Nodes.WITNESS, candidates, "NOSUCHSIGIL", "sigil"));
        }
    }

    @Test
    public void testResolveManagedRefByIdWithNoMatchThrowsNotFound() {
        try (Transaction tx = db.beginTx()) {
            Node parent = buildResolveRefFixture(tx)[0];
            List<Node> candidates = DatabaseService.getRelated(parent, TEST_CHILD);

            assertThrows(NotFoundException.class,
                () -> DatabaseService.resolveManagedRef(tx, Nodes.WITNESS, candidates, "999999", "sigil"));
        }
    }

    @Test
    public void testResolveManagedRefByIdOutsideCandidatesThrowsNotFound() {
        // A numeric id that belongs to a node of the right label, but under a *different*
        // parent (i.e. a different tradition), must not resolve: from the caller's point of
        // view, that id simply doesn't exist within the scope it asked about. Covers all four
        // dual-addressed entity types.
        Label[] labels = {Nodes.WITNESS, Nodes.STEMMA, Nodes.RELATION_TYPE, Nodes.ANNOTATIONLABEL};
        String[] nameProps = {"sigil", "name", "name", "name"};
        for (int i = 0; i < labels.length; i++) {
            Label label = labels[i];
            String nameProp = nameProps[i];
            try (Transaction tx = db.beginTx()) {
                Node parentA = tx.createNode(Nodes.TRADITION);
                Node parentB = tx.createNode(Nodes.TRADITION);
                Node childA = DatabaseService.createNode(tx, label);
                childA.setProperty(nameProp, "X");
                parentA.createRelationshipTo(childA, TEST_CHILD);
                Node childB = DatabaseService.createNode(tx, label);
                childB.setProperty(nameProp, "X");
                parentB.createRelationshipTo(childB, TEST_CHILD);

                List<Node> candidatesA = DatabaseService.getRelated(parentA, TEST_CHILD);
                List<Node> candidatesB = DatabaseService.getRelated(parentB, TEST_CHILD);
                String idA = String.valueOf(childA.getProperty("id"));
                String idB = String.valueOf(childB.getProperty("id"));

                // In scope: resolves.
                assertEquals(childA, DatabaseService.resolveManagedRef(tx, label, candidatesA, idA, nameProp));
                assertEquals(childB, DatabaseService.resolveManagedRef(tx, label, candidatesB, idB, nameProp));
                // Out of scope: NotFound, in both directions.;
                assertThrows("label " + label.name(), NotFoundException.class,
                        () -> DatabaseService.resolveManagedRef(tx, label, candidatesB, idA, nameProp));
                assertThrows("label " + label.name(), NotFoundException.class,
                        () -> DatabaseService.resolveManagedRef(tx, label, candidatesA, idB, nameProp));
            }
        }
    }

    @Test
    public void testResolveManagedRefByDuplicatedNameThrowsAmbiguousReference() {
        try (Transaction tx = db.beginTx()) {
            Node parent = buildResolveRefFixture(tx)[0];
            List<Node> candidates = DatabaseService.getRelated(parent, TEST_CHILD);

            assertThrows(AmbiguousReferenceException.class,
                () -> DatabaseService.resolveManagedRef(tx, Nodes.WITNESS, candidates, "DUP", "sigil"));
        }
    }

    // --- ensureNameUnique ---------------------------------------------------------------

    @Test
    public void testEnsureNameUniqueAllowsFreshName() {
        try (Transaction tx = db.beginTx()) {
            Node parent = tx.createNode(Nodes.TRADITION);
            Node sibling = DatabaseService.createNode(tx, Nodes.WITNESS);
            sibling.setProperty("sigil", "A");
            parent.createRelationshipTo(sibling, TEST_CHILD);

            // Must not throw.
            DatabaseService.ensureNameUnique(tx, parent, TEST_CHILD, Nodes.WITNESS, "sigil", "B", null);
        }
    }

    @Test
    public void testEnsureNameUniqueThrowsOnSiblingCollision() {
        try (Transaction tx = db.beginTx()) {
            Node parent = tx.createNode(Nodes.TRADITION);
            Node sibling = DatabaseService.createNode(tx, Nodes.WITNESS);
            sibling.setProperty("sigil", "A");
            parent.createRelationshipTo(sibling, TEST_CHILD);

            assertThrows(NameConflictException.class,
                () -> DatabaseService.ensureNameUnique(tx, parent, TEST_CHILD, Nodes.WITNESS, "sigil", "A", null));
        }
    }

    @Test
    public void testEnsureNameUniqueAllowsRenameToOwnCurrentName() {
        try (Transaction tx = db.beginTx()) {
            Node parent = tx.createNode(Nodes.TRADITION);
            Node sibling = DatabaseService.createNode(tx, Nodes.WITNESS);
            sibling.setProperty("sigil", "A");
            parent.createRelationshipTo(sibling, TEST_CHILD);

            // Must not throw: excludeSelf is the node itself, so its own current name
            // doesn't count as a collision.
            DatabaseService.ensureNameUnique(tx, parent, TEST_CHILD, Nodes.WITNESS, "sigil", "A", sibling);
        }
    }

    @Test
    public void testEnsureNameUniqueThrowsOnRenameToDifferentSiblingsName() {
        try (Transaction tx = db.beginTx()) {
            Node parent = tx.createNode(Nodes.TRADITION);
            Node siblingA = DatabaseService.createNode(tx, Nodes.WITNESS);
            siblingA.setProperty("sigil", "A");
            parent.createRelationshipTo(siblingA, TEST_CHILD);
            Node siblingB = DatabaseService.createNode(tx, Nodes.WITNESS);
            siblingB.setProperty("sigil", "B");
            parent.createRelationshipTo(siblingB, TEST_CHILD);

            assertThrows(NameConflictException.class,
                () -> DatabaseService.ensureNameUnique(tx, parent, TEST_CHILD, Nodes.WITNESS, "sigil", "A", siblingB));
        }
    }

    @Test
    public void testConcurrentEnsureNameUniqueNeverAllowsBothToSucceed() throws Exception {
        final String parentElementId;
        final String siblingAElementId;
        final String siblingBElementId;
        try (Transaction setupTx = db.beginTx()) {
            Node parent = setupTx.createNode(Nodes.TRADITION);
            Node siblingA = DatabaseService.createNode(setupTx, Nodes.WITNESS);
            siblingA.setProperty("sigil", "A");
            parent.createRelationshipTo(siblingA, TEST_CHILD);
            Node siblingB = DatabaseService.createNode(setupTx, Nodes.WITNESS);
            siblingB.setProperty("sigil", "B");
            parent.createRelationshipTo(siblingB, TEST_CHILD);
            parentElementId = parent.getElementId();
            siblingAElementId = siblingA.getElementId();
            siblingBElementId = siblingB.getElementId();
            setupTx.commit();
        }

        // Two threads each try to claim the same new name ("NEW") for two different
        // existing sibling nodes. Handshaking modeled directly on
        // assertConcurrentCreationNeverDuplicatesIdFor above: thread A is guaranteed to
        // reach (and pass) its check, and hold the parent write lock, while thread B is
        // guaranteed to be attempting its own check concurrently -- so thread B's
        // ensureNameUnique call must block on the lock until A commits, then see A's
        // already-renamed sibling and lose.
        CountDownLatch aHasAcquiredLock = new CountDownLatch(1);
        CountDownLatch bIsAboutToCheck = new CountDownLatch(1);
        AtomicReference<String> resultA = new AtomicReference<>();
        AtomicReference<String> resultB = new AtomicReference<>();
        AtomicReference<Throwable> failureA = new AtomicReference<>();
        AtomicReference<Throwable> failureB = new AtomicReference<>();

        Thread threadA = new Thread(() -> {
            try (Transaction tx = db.beginTx()) {
                Node parent = tx.getNodeByElementId(parentElementId);
                Node self = tx.getNodeByElementId(siblingAElementId);
                DatabaseService.ensureNameUnique(tx, parent, TEST_CHILD, Nodes.WITNESS, "sigil", "NEW", self);
                aHasAcquiredLock.countDown();
                bIsAboutToCheck.await(5, TimeUnit.SECONDS);
                self.setProperty("sigil", "NEW");
                Thread.sleep(300);
                tx.commit();
                resultA.set("OK");
            } catch (NameConflictException e) {
                resultA.set("CONFLICT");
            } catch (Throwable e) {
                failureA.set(e);
            }
        });

        Thread threadB = new Thread(() -> {
            try {
                aHasAcquiredLock.await(5, TimeUnit.SECONDS);
                try (Transaction tx = db.beginTx()) {
                    Node parent = tx.getNodeByElementId(parentElementId);
                    Node self = tx.getNodeByElementId(siblingBElementId);
                    bIsAboutToCheck.countDown();
                    DatabaseService.ensureNameUnique(tx, parent, TEST_CHILD, Nodes.WITNESS, "sigil", "NEW", self);
                    self.setProperty("sigil", "NEW");
                    tx.commit();
                    resultB.set("OK");
                }
            } catch (NameConflictException e) {
                resultB.set("CONFLICT");
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
        assertNotNull("Thread A must have reached a result", resultA.get());
        assertNotNull("Thread B must have reached a result", resultB.get());
        boolean exactlyOneSucceeded = ("OK".equals(resultA.get()) && "CONFLICT".equals(resultB.get()))
                || ("CONFLICT".equals(resultA.get()) && "OK".equals(resultB.get()));
        assertTrue("Exactly one of the two threads must succeed in claiming the name "
                + "(A=" + resultA.get() + ", B=" + resultB.get() + ")", exactlyOneSucceeded);
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
