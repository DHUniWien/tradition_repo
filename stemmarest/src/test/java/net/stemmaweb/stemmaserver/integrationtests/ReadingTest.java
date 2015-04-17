package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Reading;
import net.stemmaweb.rest.Tradition;
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
import com.sun.jersey.api.client.ClientResponse.Status;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.test.framework.JerseyTest;

/**
 * 
 * @author Jakob
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class ReadingTest {
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

	/*
	 * JerseyTest is the test environment to Test api calls it provides a
	 * grizzly http service
	 */
	private JerseyTest jerseyTest;

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

		/*
		 * Create a JersyTestServer serving the Resource under test
		 */
		jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
				.addResource(reading).create();
		jerseyTest.setUp();
	}

	/**
	 * test that all readings of a tradition are returned sorted ascending
	 * according to rank
	 */
	@Test
	public void allReadingsOfTraditionTest() {
		List<ReadingModel> listOfReadings = jerseyTest.resource()
				.path("/reading/" + tradId)
				.get(new GenericType<List<ReadingModel>>() {
				});
		Collections.sort(listOfReadings);

		assertEquals(29, listOfReadings.size());

		String expectedTest = "#START# when april april with his his showers sweet with fruit fruit the teh drought march of march drought has pierced teh to unto rood the root the root #END#";
		String text = "";
		for (int i = 0; i < listOfReadings.size(); i++) {
			text += listOfReadings.get(i).getDn15() + " ";
		}
		assertEquals(expectedTest, text.trim());

		int[] expectedRanks = { 0, 1, 2, 2, 3, 4, 4, 5, 6, 7, 8, 9, 10, 10, 11,
				11, 12, 13, 13, 14, 15, 16, 16, 16, 17, 17, 17, 18, 19 };
		for (int i = 0; i < listOfReadings.size(); i++) {
			assertEquals(expectedRanks[i], (int) (long) listOfReadings.get(i)
					.getDn14());
		}
	}
	
	@Test
	public void allReadingsOfTraditionNotFoundTest() {
		String falseTradId = tradId+1;
		ClientResponse response = jerseyTest.resource()
				.path("/reading/" + falseTradId)
				.get(ClientResponse.class);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
				response.getStatus());
		assertEquals("Could not find tradition with this id", response.getEntity(String.class));
	}

	@Test
	public void identicalReadingsOneResultTest() {
		List<ReadingModel> identicalReadings = new ArrayList<ReadingModel>();

		List<List<ReadingModel>> listOfIdenticalReadings = jerseyTest.resource()
				.path("/reading/identical/" + tradId + "/3/8")
				.get(new GenericType<List<List<ReadingModel>>>() {
				});
		assertEquals(1, listOfIdenticalReadings.size());
		identicalReadings = listOfIdenticalReadings.get(0);
		assertEquals(2, identicalReadings.size());
		assertEquals("his", identicalReadings.get(1).getDn15());

		assertEquals(identicalReadings.get(0).getDn15(),
				identicalReadings.get(1).getDn15());
	}
	
	@Test
	public void identicalReadingsTwoResultsTest() {
		List<ReadingModel> identicalReadings = new ArrayList<ReadingModel>();

		List<List<ReadingModel>> listOfIdenticalReadings = jerseyTest.resource()
				.path("/reading/identical/" + tradId + "/1/8")
				.get(new GenericType<List<List<ReadingModel>>>() {
				});
		assertEquals(2, listOfIdenticalReadings.size());
		
		identicalReadings = listOfIdenticalReadings.get(0);
		assertEquals(2, identicalReadings.size());
		assertEquals("april", identicalReadings.get(1).getDn15());
		assertEquals(identicalReadings.get(0).getDn15(),
				identicalReadings.get(1).getDn15());
		
		identicalReadings = listOfIdenticalReadings.get(1);
		assertEquals(2, identicalReadings.size());
		assertEquals("his", identicalReadings.get(1).getDn15());
		assertEquals(identicalReadings.get(0).getDn15(),
				identicalReadings.get(1).getDn15());
	}
	
	@Test
	public void identicalReadingsNoResultTest() {
		ClientResponse response = jerseyTest.resource()
				.path("/reading/identical/" + tradId + "/10/15")
				.get(ClientResponse.class);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
				response.getStatus());
		assertEquals("no identical readings were found", response.getEntity(String.class));
	}


	@Test
	public void compressReadingTest() {
		Node showers, sweet;
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
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

			ClientResponse res = jerseyTest
					.resource()
					.path("/reading/compress/" + tradId + "/" + showers.getId()
							+ "/" + sweet.getId()).get(ClientResponse.class);

			assertEquals(Response.Status.OK.getStatusCode(), res.getStatus());
			assertEquals("Successfully compressed readings", res.getEntity(String.class));


			assertEquals("showers sweet", showers.getProperty("dn15"));

			result = engine
					.execute("match (w:WORD {dn15:'showers sweet'}) return w");
			nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			nodes.next();
			assertFalse(nodes.hasNext());

			result = engine.execute("match (w:WORD {dn15:'sweet'}) return w");
			nodes = result.columnAs("w");
			assertFalse(nodes.hasNext());

			result = engine.execute("match (w:WORD {dn15:'showers'}) return w");
			nodes = result.columnAs("w");
			assertFalse(nodes.hasNext());

			// both witnesses are still the same
			String expectedText = "{\"text\":\"when april with his showers sweet with fruit the drought of march has pierced unto the root\"}";
			Response resp = witness.getWitnessAsPlainText(tradId, "A");
			assertEquals(expectedText, resp.getEntity());

			expectedText = "{\"text\":\"when april his showers sweet with fruit the march of drought has pierced to the root\"}";
			resp = witness.getWitnessAsPlainText(tradId, "B");
			assertEquals(expectedText, resp.getEntity());

			List<ReadingModel> listOfReadings = jerseyTest.resource()
					.path("/reading/" + tradId)
					.get(new GenericType<List<ReadingModel>>() {
					});
			Collections.sort(listOfReadings);

			// tradition still has all the texts
			String expectedTest = "#START# when april april with his his showers sweet with fruit fruit the teh drought march of march drought has pierced teh to unto rood the root the root #END#";
			String text = "";
			for (int i = 0; i < listOfReadings.size(); i++) {
				text += listOfReadings.get(i).getDn15() + " ";
			}
			assertEquals(expectedTest, text.trim());

			// there is one reading less in the tradition
			assertEquals(28, listOfReadings.size());

			// no more reading with rank 6
			int[] expectedRanks = { 0, 1, 2, 2, 3, 4, 4, 5, 7, 8, 9, 10, 10,
					11, 11, 12, 13, 13, 14, 15, 16, 16, 16, 17, 17, 17, 18, 19 };
			for (int i = 0; i < listOfReadings.size(); i++) {
				assertEquals(expectedRanks[i],
						(int) (long) listOfReadings.get(i).getDn14());
			}
			tx.success();
		}
	}

	/**
	 * the given reading are not neighbors tests that readings were not
	 * compressed
	 */
	@Test
	public void notNeighborsCompressReadingTest() {
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		try (Transaction tx = mockDbService.beginTx()) {
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'showers'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assert (nodes.hasNext());
			Node showers = nodes.next();

			result = engine.execute("match (w:WORD {dn15:'fruit'}) return w");
			nodes = result.columnAs("w");
			assert (nodes.hasNext());
			Node fruit = nodes.next();

			ClientResponse response = jerseyTest
					.resource()
					.path("/reading/compress/" + tradId + "/" + showers.getId()
							+ "/" + fruit.getId()).get(ClientResponse.class);

			assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
					response.getStatus());
			assertEquals("problem with a reading. Could not compress", response.getEntity(String.class));

			result = engine
					.execute("match (w:WORD {dn15:'showers sweet'}) return w");
			nodes = result.columnAs("w");
			assertFalse(nodes.hasNext());

			assertEquals("showers", showers.getProperty("dn15"));
			assertEquals("fruit", fruit.getProperty("dn15"));

			tx.success();
		}
	}

	@Test
	public void nextReadingTest() {
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		long readId;
		try (Transaction tx = mockDbService.beginTx()) {
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'with'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assert (nodes.hasNext());
			readId = nodes.next().getId();

			tx.success();
		}

		ReadingModel actualResponse = jerseyTest.resource()
				.path("/reading/next/" + tradId + "/A/" + readId)
				.get(ReadingModel.class);
		assertEquals("his", actualResponse.getDn15());
	}

	@Test
	public void nextReadingLastNodeTest() {
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		long readId;
		try (Transaction tx = mockDbService.beginTx()) {
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'the root'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			readId = nodes.next().getId();

			tx.success();
		}

		ClientResponse response = jerseyTest.resource()
				.path("/reading/next/" + tradId + "/B/" + readId)
				.get(ClientResponse.class);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
				response.getStatus());
		assertEquals("this was the last reading of this witness", response.getEntity(String.class));
	}

	@Test
	public void previousReadingTest() {

		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		long readId;
		try (Transaction tx = mockDbService.beginTx()) {
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'with'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assert (nodes.hasNext());
			readId = nodes.next().getId();

			tx.success();
		}
		ReadingModel actualResponse = jerseyTest.resource()
				.path("/reading/previous/" + tradId + "/A/" + readId)
				.get(ReadingModel.class);
		assertEquals("april", actualResponse.getDn15());
	}
	
	@Test
	public void previousReadingFirstNodeTest() {

		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		long readId;
		try (Transaction tx = mockDbService.beginTx()) {
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'when'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			readId = nodes.next().getId();

			tx.success();
		}
		ClientResponse actualResponse = jerseyTest.resource()
				.path("/reading/previous/" + tradId + "/A/" + readId)
				.get(ClientResponse.class);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
				actualResponse.getStatus());
		assertEquals("there is no previous reading to this reading", actualResponse.getEntity(String.class));
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
		jerseyTest.tearDown();
	}

}
