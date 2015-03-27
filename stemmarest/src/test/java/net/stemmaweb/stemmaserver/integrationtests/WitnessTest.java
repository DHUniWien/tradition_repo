package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.Response;

import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Relations;
import net.stemmaweb.rest.Witness;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.DbPathProblemService;
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

import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;

/**
 * 
 * @author Jakob
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class WitnessTest {
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
	protected GraphDatabaseService mockDbService = new TestGraphDatabaseFactory()
			.newImpermanentDatabase();

	/*
	 * The Resource under test. The mockDbFactory will be injected into this
	 * resource.
	 */
	@InjectMocks
	private GraphMLToNeo4JParser importResource;

	@InjectMocks
	private Witness witness;

	/*
	 * JerseyTest is the test environment to Test api calls it provides a
	 * grizzly http service
	 */
	private JerseyTest jerseyTest;

	@Before
	public void setUp() throws Exception {
		String filename = "";
		if(OSDetector.isWin())
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

			rootNode.createRelationshipTo(node, Relations.NORMAL);
			tx.success();
		}

		/*
		 * Manipulate the newEmbeddedDatabase method of the mockDbFactory to
		 * return new TestGraphDatabaseFactory().newImpermanentDatabase()
		 * instead of dbFactory.newEmbeddedDatabase("database");
		 */
		Mockito.when(mockDbFactory.newEmbeddedDatabase(Matchers.anyString()))
				.thenReturn(mockDbService);

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
			ExecutionResult result = engine
					.execute("match (u:USER)--(t:TRADITION) return t");
			Iterator<Node> nodes = result.columnAs("t");
			assertTrue(nodes.hasNext());
			tradId = (String) nodes.next().getProperty("id");

			tx.success();
		}

		/*
		 * Create a JersyTestServer serving the Resource under test
		 */
		jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
				.addResource(witness).create();
		jerseyTest.setUp();
	}

	@Test
	public void witnessAsTextTestA() {
		String expectedText = "when april with his showers sweet with fruit the drought of march has pierced unto the root";
		Response resp = witness.getWitnessAsPlainText(tradId, "A");
		assertEquals(expectedText, resp.getEntity());

		String returnedText = jerseyTest.resource()
				.path("/witness/string/" + tradId + "/A").get(String.class);
		assertEquals(expectedText, returnedText);

	}

	@Test
	public void witnessAsTextTestB() {
		String expectedText = "when showers sweet with april fruit the march of drought has pierced to the root";
		Response resp = witness.getWitnessAsPlainText(tradId, "B");
		assertEquals(expectedText, resp.getEntity());

		String returnedText = jerseyTest.resource()
				.path("/witness/string/" + tradId + "/B").get(String.class);
		assertEquals(expectedText, returnedText);

	}

	// not working yet!! TODO get the result as json string
	@Test
	public void witnessAsListTest() {
		String[] texts = { "when", "april", "with", "his", "showers", "sweet",
				"with", "fruit", "the", "drought", "of", "march", "has",
				"pierced", "unto", "the", "root" };
		List<ReadingModel> listOfReadings = jerseyTest.resource()
				.path("/witness/list/" + tradId + "/A")
				.get(new GenericType<List<ReadingModel>>() {
				});
		assertEquals(texts.length, listOfReadings.size());
		for (int i = 0; i < listOfReadings.size(); i++) {
			assertEquals(texts[i], listOfReadings.get(i).getDn15());
		}
	}

	@Test
	public void witnessBetweenRanksTest() {

		String expectedText = "april with his showers";
		String actualResponse = jerseyTest.resource()
				.path("/witness/string/rank/" + tradId + "/A/2/5")
				.get(String.class);
		assertEquals(expectedText, actualResponse);
	}

	/**
	 * test if the tradition node exists
	 */
	@Test
	public void traditionNodeExistsTest() {
		try (Transaction tx = mockDbService.beginTx()) {
			ResourceIterable<Node> tradNodes = mockDbService
					.findNodesByLabelAndProperty(Nodes.TRADITION, "dg1",
							"Tradition");
			Iterator<Node> tradNodesIt = tradNodes.iterator();
			assertTrue(tradNodesIt.hasNext());
			tx.success();
		}
	}

	@Test
	public void nextReadingTest() {
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		String readId;
		try (Transaction tx = mockDbService.beginTx()) {
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'with'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assert (nodes.hasNext());
			readId = (String) nodes.next().getProperty("dn1");

			tx.success();
		}

		ReadingModel actualResponse = jerseyTest.resource()
				.path("/witness/reading/next/" + tradId + "/A/" + readId)
				.get(ReadingModel.class);
		assertEquals("his", actualResponse.getDn15());
	}

	@Test
	public void previousReadingTest() {

		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		String readId;
		try (Transaction tx = mockDbService.beginTx()) {
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'with'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assert (nodes.hasNext());
			readId = (String) nodes.next().getProperty("dn1");

			tx.success();
		}
		ReadingModel actualResponse = jerseyTest.resource()
				.path("/witness/reading/previous/" + tradId + "/A/" + readId)
				.get(ReadingModel.class);
		assertEquals("april", actualResponse.getDn15());
	}

	/**
	 * test if the tradition end node exists
	 */
	@Test
	public void traditionEndNodeExistsTest() {
		ExecutionEngine engine = new ExecutionEngine(mockDbService);

		ExecutionResult result = engine
				.execute("match (e)-[:NORMAL]->(n:WORD) where n.dn15='#END#' return n");
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
		mockDbService.shutdown();
		jerseyTest.tearDown();
	}

}
