package net.stemmaweb.stemmaserver.unittests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.util.Iterator;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Tradition;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.OSDetector;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.ResourceIterable;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;

import com.sun.jersey.test.framework.JerseyTest;

/**
 * 
 * @author Ido
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class TraditionUnitTest {
	String tradId;
	GraphDatabaseService db;
	
	private GraphMLToNeo4JParser importResource;
	private Tradition tradition;

	/*
	 * JerseyTest is the test environment to Test api calls it provides a
	 * grizzly http service
	 */
	private JerseyTest jerseyTest;

	@Before
	public void setUp() throws Exception {
		
		GraphDatabaseServiceProvider.setImpermanentDatabase();
		
		db = new GraphDatabaseServiceProvider().getDatabase();
		
		importResource = new GraphMLToNeo4JParser();
		tradition = new Tradition();

		String filename = "";
		if (OSDetector.isWin())
			filename = "src\\TestXMLFiles\\testTradition.xml";
		else
			filename = "src/TestXMLFiles/testTradition.xml";

		/*
		 * Populate the test database with the root node and a user with id 1
		 */
		ExecutionEngine engine = new ExecutionEngine(db);
		try (Transaction tx = db.beginTx()) {
			ExecutionResult result = engine.execute("match (n:ROOT) return n");
			Iterator<Node> nodes = result.columnAs("n");
			Node rootNode = null;
			if (!nodes.hasNext()) {
				rootNode = db.createNode(Nodes.ROOT);
				rootNode.setProperty("name", "Root node");
				rootNode.setProperty("LAST_INSERTED_TRADITION_ID", "1000");
			}

			Node node = db.createNode(Nodes.USER);
			node.setProperty("id", "1");
			node.setProperty("isAdmin", "1");

			rootNode.createRelationshipTo(node, ERelations.NORMAL);
			tx.success();
		}


		/**
		 * load a tradition to the test DB
		 */
		try {
			importResource.parseGraphML(filename, "1");
		} catch (FileNotFoundException f) {
			// this error should not occur
			assertTrue(false);
		}
		/**
		 * gets the generated id of the inserted tradition
		 */
		try (Transaction tx = db.beginTx()) {
			ExecutionResult result = engine.execute("match (u:USER)--(t:TRADITION) return t");
			Iterator<Node> nodes = result.columnAs("t");
			assertTrue(nodes.hasNext());
			tradId = (String) nodes.next().getProperty("id");

			tx.success();
		}

		/*
		 * Create a JersyTestServer serving the Resource under test
		 */
		jerseyTest = JerseyTestServerFactory.newJerseyTestServer().addResource(tradition).create();
		jerseyTest.setUp();
	}

	@Test
	public void randomNodeExistsTest() {
		ExecutionEngine engine = new ExecutionEngine(db);
		try (Transaction tx = db.beginTx()) {
			ExecutionResult result = engine.execute("match (w:WORD {dn15:'april'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assert (nodes.hasNext());
			long rank = 2;
			assertEquals(rank, nodes.next().getProperty("dn14"));
			tx.success();
		}
	}

	/**
	 * test if the tradition node exists
	 */
	@Test
	public void traditionNodeExistsTest() {
		try (Transaction tx = db.beginTx()) {
			ResourceIterable<Node> tradNodes = db.findNodesByLabelAndProperty(Nodes.TRADITION, "dg1",
					"Tradition");
			Iterator<Node> tradNodesIt = tradNodes.iterator();
			assertTrue(tradNodesIt.hasNext());
			tx.success();
		}
	}

	/**
	 * test if the tradition end node exists
	 */
	@Test
	public void traditionEndNodeExistsTest() {
		ExecutionEngine engine = new ExecutionEngine(db);

		ExecutionResult result = engine.execute("match (e)-[:NORMAL]->(n:WORD) where n.dn15='#END#' return n");
		ResourceIterator<Node> tradNodes = result.columnAs("n");
		assertTrue(tradNodes.hasNext());
	}

	/**
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
