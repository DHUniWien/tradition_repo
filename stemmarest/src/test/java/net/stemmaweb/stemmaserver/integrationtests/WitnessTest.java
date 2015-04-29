package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.Response;

import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Witness;
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

import com.sun.jersey.api.client.ClientResponse;
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
		String expectedText = "{\"text\":\"when april with his showers sweet with "
				+ "fruit the drought of march has pierced unto the root\"}";
		Response resp = witness.getWitnessAsText(tradId, "A");
		assertEquals(expectedText, resp.getEntity());

		String returnedText = jerseyTest.resource()
				.path("/witness/gettext/fromtradition/" + tradId + "/ofwitness/A").get(String.class);
		assertEquals(expectedText, returnedText);
	}

	@Test
	public void witnessAsTextNotExistingTest() {
		ClientResponse response = jerseyTest.resource()
				.path("/witness/gettext/fromtradition/" + tradId + "/ofwitness/D")
				.get(ClientResponse.class);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
				response.getStatus());
		assertEquals("no witness with this id was found",
				response.getEntity(String.class));
	}

	@Test
	public void witnessAsTextTestB() {
		String expectedText = "{\"text\":\"when showers sweet with april fruit the march "
				+ "of drought has pierced to the root\"}";
		Response resp = witness.getWitnessAsText(tradId, "B");
		assertEquals(expectedText, resp.getEntity());

		String returnedText = jerseyTest.resource()
				.path("/witness/gettext/fromtradition/" + tradId + "/ofwitness/B").get(String.class);
		assertEquals(expectedText, returnedText);
	}

	@Test
	public void witnessAsListTest() {
		String[] texts = { "when", "april", "with", "his", "showers", "sweet",
				"with", "fruit", "the", "drought", "of", "march", "has",
				"pierced", "unto", "the", "root" };
		List<ReadingModel> listOfReadings = jerseyTest.resource()
				.path("/witness/getreadinglist/fromtradition/" + tradId + "/ofwitness/A")
				.get(new GenericType<List<ReadingModel>>() {
				});
		assertEquals(texts.length, listOfReadings.size());
		for (int i = 0; i < listOfReadings.size(); i++) {
			assertEquals(texts[i], listOfReadings.get(i).getDn15());
		}
	}
	
	@Test
	public void witnessAsListNotExistingTest() {
		ClientResponse response = jerseyTest.resource()
				.path("/witness/getreadinglist/fromtradition/" + tradId + "/ofwitness/D")
				.get(ClientResponse.class);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
				response.getStatus());
		assertEquals("no witness with this id was found",
				response.getEntity(String.class));
	}

	@Test
	public void witnessBetweenRanksTest() {

		String expectedText = "{\"text\":\"april with his showers\"}";
		String response = jerseyTest.resource()
				.path("/witness/gettext/fromtradition/" + tradId + "/ofwitness/A/"
						+ "fromstartrank/2/toendrank/5")
				.get(String.class);
		assertEquals(expectedText, response);
	}

	/**
	 * as ranks are adjusted should give same result as previous test
	 */
	@Test
	public void witnessBetweenRanksWrongWayTest() {
		String expectedText = "{\"text\":\"april with his showers\"}";
		String response = jerseyTest.resource()
				.path("/witness/gettext/fromtradition/" + tradId + "/ofwitness/A/"
						+ "fromstartrank/2/toendrank/5")
				.get(String.class);
		assertEquals(expectedText, response);
	}

	/**
	 * gives same ranks for start and end should return error
	 */
	@Test
	public void witnessBetweenRanksSameRanksTest() {
		ClientResponse response = jerseyTest.resource()
				.path("/witness/gettext/fromtradition/" + tradId + "/ofwitness/A/"
						+ "fromstartrank/5/toendrank/5")
				.get(ClientResponse.class);
		assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
				response.getStatus());
		assertEquals("end-rank is equal to start-rank",
				response.getEntity(String.class));
	}
	
	//if the end rank is too high, will return all the readings between start rank to end of witness
	@Test
	public void witnessBetweenRanksTooHighEndRankTest() {
		String expectedText = "{\"text\":\"showers sweet with fruit the drought of march has pierced unto the root\"}";
		String response = jerseyTest.resource()
				.path("/witness/gettext/fromtradition/" + tradId + "/ofwitness/A/"
						+ "fromstartrank/5/toendrank/30")
				.get(String.class);
		assertEquals(expectedText, response);

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
		jerseyTest.tearDown();
	}
}
