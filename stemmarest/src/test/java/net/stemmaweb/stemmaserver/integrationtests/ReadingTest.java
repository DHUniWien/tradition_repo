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

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.ClientResponse.Status;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;

/**
 * 
 * @author Jakob
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class ReadingTest {
	private String expectedWitnessA = "{\"text\":\"when april with his showers sweet with fruit the drought of march has pierced unto the root\"}";
	private String expectedWitnessB = "{\"text\":\"when april his showers sweet with fruit the march of drought has pierced to the root\"}";
	private String expectedWitnessC = "{\"text\":\"when showers sweet with april fruit teh drought of march has pierced teh rood\"}";

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

	private void testNumberOfReadings(int number) {
		List<ReadingModel> listOfReadings = jerseyTest.resource().path("/reading/" + tradId)
				.get(new GenericType<List<ReadingModel>>() {
				});
		assertEquals(number, listOfReadings.size());
	}

	private void testWitnesses() {
		Response resp;

		resp = witness.getWitnessAsPlainText(tradId, "A");
		assertEquals(expectedWitnessA, resp.getEntity());

		resp = witness.getWitnessAsPlainText(tradId, "B");
		assertEquals(expectedWitnessB, resp.getEntity());

		resp = witness.getWitnessAsPlainText(tradId, "C");
		assertEquals(expectedWitnessC, resp.getEntity());
	}

	@Test
	public void getReadingTest() throws JsonProcessingException {
		String expected = "{\"dn1\":\"16\",\"dn2\":\"0\",\"dn11\":\"Default\",\"dn14\":2,\"dn15\":\"april\"}";

		Response resp = reading.getReading(tradId, 16);

		ObjectMapper mapper = new ObjectMapper();
		mapper.setSerializationInclusion(Include.NON_NULL);
		String json = mapper.writeValueAsString(resp.getEntity());

		assertEquals(expected, json);
	}

	@Test
	public void duplicateReadingTest() {
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		ExecutionResult result = engine.execute("match (w:WORD {dn15:'showers'}) return w");
		Iterator<Node> nodes = result.columnAs("w");
		assertTrue(nodes.hasNext());
		Node firstNode = nodes.next();
		assertFalse(nodes.hasNext());

		result = engine.execute("match (w:WORD {dn15:'sweet'}) return w");
		nodes = result.columnAs("w");
		assertTrue(nodes.hasNext());
		Node secondNode = nodes.next();
		assertFalse(nodes.hasNext());

		testNumberOfReadings(29);

		testWitnesses();

		// duplicate reading
		String jsonPayload = "{\"readings\":[" + firstNode.getId() + ", " + secondNode.getId()
				+ "], \"witnesses\":[\"A\",\"B\" ]}";
		ClientResponse response = jerseyTest.resource().path("/reading/duplicate/" + tradId)
				.type(MediaType.APPLICATION_JSON).post(ClientResponse.class, jsonPayload);

		assertEquals(Status.OK, response.getClientResponseStatus());

		testNumberOfReadings(31);

		testWitnesses();

		result = engine.execute("match (w:WORD {dn15:'showers'}) return w");
		nodes = result.columnAs("w");
		assertTrue(nodes.hasNext());
		Node originalShowers = nodes.next();
		assertTrue(nodes.hasNext());
		Node duplicatedShowers = nodes.next();
		assertFalse(nodes.hasNext());

		result = engine.execute("match (w:WORD {dn15:'sweet'}) return w");
		nodes = result.columnAs("w");
		assertTrue(nodes.hasNext());
		Node originalSweet = nodes.next();
		assertTrue(nodes.hasNext());
		Node duplicatedSweet = nodes.next();
		assertFalse(nodes.hasNext());

		// compare original and duplicated
		try (Transaction tx = mockDbService.beginTx()) {
			for (int i = 14; i < 16; i++) {
				String key = "dn" + i;
				assertEquals(originalShowers.getProperty(key), duplicatedShowers.getProperty(key));
				assertEquals(originalSweet.getProperty(key), duplicatedSweet.getProperty(key));
			}

			tx.success();
		}
	}

	@Test
	public void duplicateReadingEdgeCaseTest() {
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		ExecutionResult result = engine.execute("match (w:WORD {dn15:'of'}) return w");
		Iterator<Node> nodes = result.columnAs("w");
		assertTrue(nodes.hasNext());
		Node node = nodes.next();
		assertFalse(nodes.hasNext());

		testNumberOfReadings(29);

		testWitnesses();

		// duplicate reading
		String jsonPayload = "{\"readings\":[" + node.getId() + "], \"witnesses\":[\"A\",\"B\" ]}";
		ClientResponse response = jerseyTest.resource().path("/reading/duplicate/" + tradId)
				.type(MediaType.APPLICATION_JSON).post(ClientResponse.class, jsonPayload);

		assertEquals(Status.OK, response.getClientResponseStatus());

		testNumberOfReadings(30);

		testWitnesses();

		result = engine.execute("match (w:WORD {dn15:'of'}) return w");
		nodes = result.columnAs("w");
		assertTrue(nodes.hasNext());
		Node originalOf = nodes.next();
		assertTrue(nodes.hasNext());
		Node duplicatedOf = nodes.next();
		assertFalse(nodes.hasNext());

		// compare original and duplicated
		try (Transaction tx = mockDbService.beginTx()) {
			for (int i = 14; i < 16; i++) {
				String key = "dn" + i;
				assertEquals(originalOf.getProperty(key), duplicatedOf.getProperty(key));
			}

			tx.success();
		}
	}

	@Test
	public void duplicateReadingWithOnlyOneWitnessTest() {
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		ExecutionResult result = engine.execute("match (w:WORD {dn15:'rood'}) return w");
		Iterator<Node> nodes = result.columnAs("w");
		assertTrue(nodes.hasNext());
		Node firstNode = nodes.next();
		assertFalse(nodes.hasNext());

		// duplicate reading
		String jsonPayload = "{\"readings\":[" + firstNode.getId() + "], \"witnesses\":[\"C\"]}";
		ClientResponse response = jerseyTest.resource().path("/reading/duplicate/" + tradId)
				.type(MediaType.APPLICATION_JSON).post(ClientResponse.class, jsonPayload);

		assertEquals(Status.INTERNAL_SERVER_ERROR, response.getClientResponseStatus());
	}

	@Test
	public void duplicateReadingWithNoWitnessesInJSONTest() {
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		ExecutionResult result = engine.execute("match (w:WORD {dn15:'rood'}) return w");
		Iterator<Node> nodes = result.columnAs("w");
		assertTrue(nodes.hasNext());
		Node firstNode = nodes.next();
		assertFalse(nodes.hasNext());

		// duplicate reading
		String jsonPayload = "{\"readings\":[" + firstNode.getId() + "], \"witnesses\":[]}";
		ClientResponse response = jerseyTest.resource().path("/reading/duplicate/" + tradId)
				.type(MediaType.APPLICATION_JSON).post(ClientResponse.class, jsonPayload);

		assertEquals(Status.INTERNAL_SERVER_ERROR, response.getClientResponseStatus());
	}

	@Test
	public void duplicateReadingWithNotAllowedWitnessesTest() {
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		ExecutionResult result = engine.execute("match (w:WORD {dn15:'root'}) return w");
		Iterator<Node> nodes = result.columnAs("w");
		assertTrue(nodes.hasNext());
		Node firstNode = nodes.next();
		assertFalse(nodes.hasNext());

		// duplicate reading
		String jsonPayload = "{\"readings\":[" + firstNode.getId() + "], \"witnesses\":[\"C\"]}";
		ClientResponse response = jerseyTest.resource().path("/reading/duplicate/" + tradId)
				.type(MediaType.APPLICATION_JSON).post(ClientResponse.class, jsonPayload);

		assertEquals(Status.INTERNAL_SERVER_ERROR, response.getClientResponseStatus());
	}

	@Test
	public void mergeReadingsTest() {
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		ExecutionResult result = engine.execute("match (w:WORD {dn15:'fruit'}) return w");
		Iterator<Node> nodes = result.columnAs("w");
		assertTrue(nodes.hasNext());
		Node firstNode = nodes.next();
		assertTrue(nodes.hasNext());
		Node secondNode = nodes.next();
		assertFalse(nodes.hasNext());

		testNumberOfReadings(29);

		testWitnesses();

		// merge readings
		ClientResponse response = jerseyTest.resource()
				.path("/reading/merge/" + tradId + "/" + firstNode.getId() + "/" + secondNode.getId())
				.type(MediaType.APPLICATION_JSON).post(ClientResponse.class);

		assertEquals(Status.OK, response.getClientResponseStatus());

		// should contain one reading less now
		testNumberOfReadings(28);

		testWitnesses();

		result = engine.execute("match (w:WORD {dn15:'fruit'}) return w");
		nodes = result.columnAs("w");
		assertTrue(nodes.hasNext());
		Node staying = nodes.next();
		assertFalse(nodes.hasNext());
		
		// try (Transaction tx = mockDbService.beginTx()) {
		//
		// assertFalse(firstNode.hasRelationship(ERelations.RELATIONSHIP));
		// assertTrue(secondNode.hasRelationship(ERelations.RELATIONSHIP));
		//
		// for (Relationship oldRel :
		// firstNode.getRelationships(ERelations.RELATIONSHIP))
		// for (Relationship stayingRel :
		// staying.getRelationships(ERelations.RELATIONSHIP))
		//
		// tx.success();
		// }
	}

	@Test
	public void mergeReadingsWithTranspositionRelationshipTest() {
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		ExecutionResult result = engine.execute("match (w:WORD {dn15:'april'}) return w");
		Iterator<Node> nodes = result.columnAs("w");
		assertTrue(nodes.hasNext());
		Node firstNode = nodes.next();
		assertTrue(nodes.hasNext());
		Node secondNode = nodes.next();
		assertFalse(nodes.hasNext());

		testNumberOfReadings(29);

		testWitnesses();

		// merge readings
		ClientResponse response = jerseyTest.resource()
				.path("/reading/merge/" + tradId + "/" + firstNode.getId() + "/" + secondNode.getId())
				.type(MediaType.APPLICATION_JSON).post(ClientResponse.class);

		assertEquals(Status.INTERNAL_SERVER_ERROR, response.getClientResponseStatus());

		testNumberOfReadings(29);

		testWitnesses();

		result = engine.execute("match (w:WORD {dn15:'april'}) return w");
		nodes = result.columnAs("w");
		assertTrue(nodes.hasNext());
		firstNode = nodes.next();
		assertTrue(nodes.hasNext());
		secondNode = nodes.next();
		assertFalse(nodes.hasNext());
	}

	// TODO include relationships of class one into ReadingTestTradition
	// @Test
	// public void mergeReadingsWithClassOneRelationshipTest() {
	// ExecutionEngine engine = new ExecutionEngine(mockDbService);
	// ExecutionResult result =
	// engine.execute("match (w:WORD {dn15:'april'}) return w");
	// Iterator<Node> nodes = result.columnAs("w");
	// assertTrue(nodes.hasNext());
	// Node firstNode = nodes.next();
	// assertTrue(nodes.hasNext());
	// Node secondNode = nodes.next();
	// assertFalse(nodes.hasNext());
	//
	// testNumberOfReadings(29);
	//
	// testWitnesses();
	//
	// // merge readings
	// ClientResponse response = jerseyTest.resource()
	// .path("/reading/merge/" + tradId + "/" + firstNode.getId() + "/" +
	// secondNode.getId())
	// .type(MediaType.APPLICATION_JSON).post(ClientResponse.class);
	//
	// assertEquals(Status.INTERNAL_SERVER_ERROR,
	// response.getClientResponseStatus());
	//
	// testNumberOfReadings(29);
	//
	// testWitnesses();
	//
	// result = engine.execute("match (w:WORD {dn15:'april'}) return w");
	// nodes = result.columnAs("w");
	// assertTrue(nodes.hasNext());
	// firstNode = nodes.next();
	// assertTrue(nodes.hasNext());
	// secondNode = nodes.next();
	// assertFalse(nodes.hasNext());
	// }

	@Test
	public void mergeReadingsEdgeCaseTest() {
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		ExecutionResult result = engine.execute("match (w:WORD {dn15:'march'}) return w");
		Iterator<Node> nodes = result.columnAs("w");
		assertTrue(nodes.hasNext());
		Node firstNode = nodes.next();
		assertTrue(nodes.hasNext());
		Node secondNode = nodes.next();
		assertFalse(nodes.hasNext());

		testNumberOfReadings(29);

		testWitnesses();

		// merge readings
		ClientResponse response = jerseyTest.resource()
				.path("/reading/merge/" + tradId + "/" + firstNode.getId() + "/" + secondNode.getId())
				.type(MediaType.APPLICATION_JSON).post(ClientResponse.class);

		assertEquals(Status.INTERNAL_SERVER_ERROR, response.getClientResponseStatus());

		testNumberOfReadings(29);

		testWitnesses();

		result = engine.execute("match (w:WORD {dn15:'march'}) return w");
		nodes = result.columnAs("w");
		assertTrue(nodes.hasNext());
		firstNode = nodes.next();
		assertTrue(nodes.hasNext());
		secondNode = nodes.next();
		assertFalse(nodes.hasNext());
	}

	@Test
	public void mergeReadingsWithDifferentTextTest() {
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		ExecutionResult result = engine.execute("match (w:WORD {dn15:'showers'}) return w");
		Iterator<Node> nodes = result.columnAs("w");
		assertTrue(nodes.hasNext());
		Node firstNode = nodes.next();

		result = engine.execute("match (w:WORD {dn15:'sweet'}) return w");
		nodes = result.columnAs("w");
		assertTrue(nodes.hasNext());
		Node secondNode = nodes.next();

		// merge readings
		ClientResponse response = jerseyTest.resource()
				.path("/reading/merge/" + tradId + "/" + firstNode.getId() + "/" + secondNode.getId())
				.type(MediaType.APPLICATION_JSON).post(ClientResponse.class);

		assertEquals(Status.INTERNAL_SERVER_ERROR, response.getClientResponseStatus());
	}

	@Test
	public void splitReadingTest() {
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		ExecutionResult result = engine.execute("match (w:WORD {dn15:'the root'}) return w");
		Iterator<Node> nodes = result.columnAs("w");
		assertTrue(nodes.hasNext());
		Node node = nodes.next();
		assertFalse(nodes.hasNext());

		testNumberOfReadings(29);

		testWitnesses();

		// split reading
		ClientResponse response = jerseyTest.resource().path("/reading/split/" + tradId + "/" + node.getId())
				.type(MediaType.APPLICATION_JSON).post(ClientResponse.class);

		assertEquals(Status.OK, response.getClientResponseStatus());

		// should contain one reading more now
		testNumberOfReadings(30);

		testWitnesses();
	}

	@Test
	public void splitReadingWithOnlyOneWordTest() {
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		ExecutionResult result = engine.execute("match (w:WORD {dn15:'showers'}) return w");
		Iterator<Node> nodes = result.columnAs("w");
		assertTrue(nodes.hasNext());
		Node node = nodes.next();
		assertFalse(nodes.hasNext());

		testNumberOfReadings(29);

		testWitnesses();

		// split reading
		ClientResponse response = jerseyTest.resource().path("/reading/split/" + tradId + "/" + node.getId())
				.type(MediaType.APPLICATION_JSON).post(ClientResponse.class);

		assertEquals(Status.INTERNAL_SERVER_ERROR, response.getClientResponseStatus());

		testNumberOfReadings(29);

		testWitnesses();
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
	public void couldBeIdenticalReadingsTest() {
		List<List<ReadingModel>> couldBeIdenticalReadings = jerseyTest.resource()
				.path("/reading/couldBeIdentical/" + tradId + "/1/15")
				.get(new GenericType<List<List<ReadingModel>>>() {
				});
		assertEquals(2, couldBeIdenticalReadings.size());
		
		assertEquals(couldBeIdenticalReadings.get(0).get(0).getDn15(),
				couldBeIdenticalReadings.get(0).get(1).getDn15());
		assertEquals("fruit", couldBeIdenticalReadings.get(0).get(0).getDn15());
		
		assertFalse(couldBeIdenticalReadings.get(0).get(0).getDn14()==
				couldBeIdenticalReadings.get(0).get(1).getDn14());
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
			assertEquals("reading are not neighbors. could not compress", response.getEntity(String.class));

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
		long withReadId, piercedReadId;
		try (Transaction tx = mockDbService.beginTx()) {
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'with'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assert (nodes.hasNext());
			withReadId = nodes.next().getId();
			
			result = engine
					.execute("match (w:WORD {dn15:'pierced'}) return w");
			nodes = result.columnAs("w");
			assert (nodes.hasNext());
			piercedReadId = nodes.next().getId();

			tx.success();
		}

		ReadingModel actualResponse = jerseyTest.resource()
				.path("/reading/next/A/" + withReadId)
				.get(ReadingModel.class);
		assertEquals("his", actualResponse.getDn15());
		
		actualResponse = jerseyTest.resource()
				.path("/reading/next/A/" + piercedReadId)
				.get(ReadingModel.class);
		assertEquals("unto", actualResponse.getDn15());
		
		actualResponse = jerseyTest.resource()
				.path("/reading/next/B/" + piercedReadId)
				.get(ReadingModel.class);
		assertEquals("to", actualResponse.getDn15());
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
				.path("/reading/next/B/" + readId)
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
				.path("/reading/previous/A/" + readId)
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
				.path("/reading/previous/A/" + readId)
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
