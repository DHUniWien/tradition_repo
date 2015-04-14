package net.stemmaweb.stemmaserver.unittests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.core.Response;

import net.stemmaweb.model.DuplicateModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Tradition;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.OSDetector;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.ResourceIterable;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.test.TestGraphDatabaseFactory;

import com.sun.jersey.test.framework.JerseyTest;

/**
 * 
 * @author Ido
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class TraditionUnitTest {
	private String tradId;
	/*
	 * Create a Mock object for the dbFactory.
	 */
	@Mock
	protected GraphDatabaseFactory mockDbFactory = new GraphDatabaseFactory();

	/*
	 * Create a Spy object for dbService.
	 */
	@Spy
	protected GraphDatabaseService mockDbService = new TestGraphDatabaseFactory().newImpermanentDatabase();

	/*
	 * The Resource under test. The mockDbFactory will be injected into this
	 * resource.
	 */
	@InjectMocks
	private GraphMLToNeo4JParser importResource;

	@InjectMocks
	private Tradition tradition;

	/*
	 * JerseyTest is the test environment to Test api calls it provides a
	 * grizzly http service
	 */
	private JerseyTest jerseyTest;

	@Before
	public void setUp() throws Exception {

		String filename = "";
		if (OSDetector.isWin())
			filename = "src\\TestXMLFiles\\testTradition.xml";
		else
			filename = "src/TestXMLFiles/testTradition.xml";

		/*
		 * Populate the test database with the root node and a user with id 1
		 */
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		try (Transaction tx = mockDbService.beginTx()) {
			ExecutionResult result = engine.execute("match (n:ROOT) return n");
			Iterator<Node> nodes = result.columnAs("n");
			Node rootNode = null;
			if (!nodes.hasNext()) {
				rootNode = mockDbService.createNode(Nodes.ROOT);
				rootNode.setProperty("name", "Root node");
				rootNode.setProperty("LAST_INSERTED_TRADITION_ID", "1000");
			}

			Node node = mockDbService.createNode(Nodes.USER);
			node.setProperty("id", "1");
			node.setProperty("isAdmin", "1");

			rootNode.createRelationshipTo(node, ERelations.NORMAL);
			tx.success();
		}

		/*
		 * Manipulate the newEmbeddedDatabase method of the mockDbFactory to
		 * return new TestGraphDatabaseFactory().newImpermanentDatabase()
		 * instead of dbFactory.newEmbeddedDatabase("database");
		 */
		Mockito.when(mockDbFactory.newEmbeddedDatabase(Matchers.anyString())).thenReturn(mockDbService);

		/*
		 * Avoid the Databaseservice to shutdown. (Override the shutdown method
		 * with nothing)
		 */
		Mockito.doNothing().when(mockDbService).shutdown();

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
		try (Transaction tx = mockDbService.beginTx()) {
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
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		try (Transaction tx = mockDbService.beginTx()) {
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
		try (Transaction tx = mockDbService.beginTx()) {
			ResourceIterable<Node> tradNodes = mockDbService.findNodesByLabelAndProperty(Nodes.TRADITION, "dg1",
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
		ExecutionEngine engine = new ExecutionEngine(mockDbService);

		ExecutionResult result = engine.execute("match (e)-[:NORMAL]->(n:WORD) where n.dn15='#END#' return n");
		ResourceIterator<Node> tradNodes = result.columnAs("n");
		assertTrue(tradNodes.hasNext());
	}

	@Test
	public void duplicateReadingTest() {
		DuplicateModel duplicateModel = new DuplicateModel();
		List<Long> readings = new LinkedList<Long>();
		readings.add(16L);
		readings.add(18L);
		duplicateModel.setReadings(readings);
		List<String> witnesses = new LinkedList<String>();
		witnesses.add("B");
		witnesses.add("C");
		duplicateModel.setWitnesses(witnesses);
		Response response = tradition.duplicateReading(tradId, duplicateModel);

		String expected = "Successfully duplicated readings";

		assertEquals(expected, response.getEntity().toString());
	}

	// @Test
	// public void duplicateReadingWithNotAllowedWitnessTest() {
	// DuplicateModel duplicateModel = new DuplicateModel();
	// List<Long> readings = new LinkedList<Long>();
	// readings.add(16L);
	// readings.add(18L);
	// duplicateModel.setReadings(readings);
	// List<String> witnesses = new LinkedList<String>();
	// witnesses.add("A");
	// witnesses.add("B");
	// duplicateModel.setWitnesses(witnesses);
	// Response response = tradition.duplicateReading(tradId, duplicateModel);
	//
	// String expected =
	// "The node to be duplicated has to be part of the new witnesses";
	//
	// assertEquals(expected, response.getEntity().toString());
	// }

	@Test
	public void mergeReadingsTest() {
		DuplicateModel duplicateModel = new DuplicateModel();
		List<Long> readings = new LinkedList<Long>();
		readings.add(16L);
		readings.add(18L);
		duplicateModel.setReadings(readings);
		List<String> witnesses = new LinkedList<String>();
		witnesses.add("B");
		witnesses.add("C");
		duplicateModel.setWitnesses(witnesses);
		tradition.duplicateReading(tradId, duplicateModel);

		Response response = tradition.mergeReadings(tradId, 16, 39);

		String expected = "Successfully merged readings";

		assertEquals(expected, response.getEntity().toString());
	}

	@Test
	public void mergeReadingsWithDifferentTextTest() {
		Response response = tradition.mergeReadings(tradId, 16, 23);

		String expected = "Readings to be merged do not contain the same text";

		assertEquals(expected, response.getEntity().toString());
	}

	@Test
	public void splitReadingContainingOnlyOneWordTest() {
		Response response = tradition.splitReading(tradId, 16);

		String expected = "A reading to be splitted has to contain at least 2 words";

		assertEquals(expected, response.getEntity().toString());
	}

	/**
	 * Shut down the jersey server
	 * 
	 * @throws Exception
	 */
	@After
	public void tearDown() throws Exception {
		mockDbService.shutdown();
		jerseyTest.tearDown();
	}

}
