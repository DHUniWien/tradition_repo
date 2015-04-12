package net.stemmaweb.stemmaserver.unittests;

import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.util.Iterator;

import javax.ws.rs.core.Response;

import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Reading;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Witness;
import net.stemmaweb.services.GraphMLToNeo4JParser;
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
 * @author Jakob
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class ReadingUnitTest {
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
	private Reading reading;

	@InjectMocks
	private Witness witness;

	@Before
	public void setUp() throws Exception {

		String filename = "";
		if (OSDetector.isWin())
			filename = "src\\TestXMLFiles\\ReadingstestTradition.xml";
		else
			filename = "src/TestXMLFiles/ReadingstestTradition.xml";

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
	}

	@Test
	public void witnessAsTextTestB() {
		String expectedText = "{\"text\":\"when april his showers sweet with fruit the march of drought has pierced to the root\"}";
		Response resp = witness.getWitnessAsPlainText(tradId, "B");
		assertEquals(expectedText, resp.getEntity());
	}

	@Test
	public void compressReadingTest() {
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		Node showers, sweet;
		try (Transaction tx = mockDbService.beginTx()) {
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'showers'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assert (nodes.hasNext());
			showers = nodes.next();

			result = engine.execute("match (w:WORD {dn15:'sweet'}) return w");
			nodes = result.columnAs("w");
			assert (nodes.hasNext());
			sweet = nodes.next();
			reading.compressReadings(tradId, showers.getId(), sweet.getId());

			assertEquals("showers sweet", showers.getProperty("dn15"));

			result = engine.execute("match (w:WORD {dn15:'sweet'}) return w");
			nodes = result.columnAs("w");
			assertFalse(nodes.hasNext());
			tx.success();
		}
	}

	@Test
	public void randomNodeExistsTest() {
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		try (Transaction tx = mockDbService.beginTx()) {
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'april'}) return w");
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
			ResourceIterable<Node> tradNodes = mockDbService
					.findNodesByLabelAndProperty(Nodes.TRADITION, "dg1",
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
	}

}