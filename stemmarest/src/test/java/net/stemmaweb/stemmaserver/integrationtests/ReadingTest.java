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
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ResourceIterable;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.ClientResponse.Status;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;

/**
 * 
 * Contains all tests for the api calls related to readings.
 * 
 * @author PSE FS 2015 Team2
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class ReadingTest {
	private String expectedWitnessA = "{\"text\":\"when april with his showers sweet with fruit the drought of march has pierced unto me the root\"}";
	private String expectedWitnessB = "{\"text\":\"when april his showers sweet with fruit the march of drought has pierced to the root\"}";
	private String expectedWitnessC = "{\"text\":\"when showers sweet with fruit to drought of march has pierced teh rood\"}";

	private String tradId;

	GraphDatabaseService db;

	/*
	 * JerseyTest is the test environment to Test api calls it provides a
	 * grizzly http service
	 */
	private JerseyTest jerseyTest;

	private Reading reading;
	private Witness witness;
	private GraphMLToNeo4JParser importResource;

	@Before
	public void setUp() throws Exception {

		GraphDatabaseServiceProvider.setImpermanentDatabase();

		db = new GraphDatabaseServiceProvider().getDatabase();

		reading = new Reading();
		witness = new Witness();
		importResource = new GraphMLToNeo4JParser();

		String filename = "";
		if (OSDetector.isWin())
			filename = "src\\TestXMLFiles\\ReadingstestTradition.xml";
		else
			filename = "src/TestXMLFiles/ReadingstestTradition.xml";

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
	 * Contains 29 readings at the beginning.
	 */
	private void testNumberOfReadings(int number) {
		List<ReadingModel> listOfReadings = jerseyTest.resource()
				.path("/reading/getallreadings/fromtradition/" + tradId)
				.get(new GenericType<List<ReadingModel>>() {
				});
		assertEquals(number, listOfReadings.size());
	}

	private void testWitnesses() {
		Response resp;

		resp = witness.getWitnessAsText(tradId, "A");
		assertEquals(expectedWitnessA, resp.getEntity());

		resp = witness.getWitnessAsText(tradId, "B");
		assertEquals(expectedWitnessB, resp.getEntity());

		resp = witness.getWitnessAsText(tradId, "C");
		assertEquals(expectedWitnessC, resp.getEntity());
	}

	// not working yes
	// TODO fix json payload
	@Test
	public void changeReadingPropertiesTest() {

		Node node;
		ClientResponse response;
		try (Transaction tx = db.beginTx()) {
			ExecutionEngine engine = new ExecutionEngine(db);
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'showers'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			node = nodes.next();
			assertFalse(nodes.hasNext());
			
			String jsonPayload = "{\"key\":\"dn15\",\"newProperty\":\"snow\"}";
			response = jerseyTest.resource()
					.path("/reading/changeproperties/ofreading/" + node.getId())
					.type(MediaType.APPLICATION_JSON)
					.post(ClientResponse.class, jsonPayload);

			assertEquals("snow", (String) node.getProperty("dn15"));
			tx.success();
		}		
	}

	@Test
	public void getReadingJsonTest() throws JsonProcessingException {
		String expected = "{\"dn1\":\"16\",\"dn2\":\"0\",\"dn11\":\"Default\",\"dn14\":2,\"dn15\":\"april\"}";

		ClientResponse resp = jerseyTest
				.resource()
				.path("/reading/getreading/withreadingid/" + 16)
				.type(MediaType.APPLICATION_JSON).get(ClientResponse.class);

		ObjectMapper mapper = new ObjectMapper();
		mapper.setSerializationInclusion(Include.NON_NULL);
		String json = mapper.writeValueAsString(resp
				.getEntity(ReadingModel.class));

		assertEquals(expected, json);
	}

	@Test
	public void getReadingReadingModelTest() {
		try (Transaction tx = db.beginTx()) {
			ExecutionEngine engine = new ExecutionEngine(db);
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'showers'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			Node node = nodes.next();
			assertFalse(nodes.hasNext());

			ReadingModel expectedReadingModel = null;
			expectedReadingModel = new ReadingModel(node);

			ReadingModel readingModel = jerseyTest
					.resource()
					.path("/reading/getreading/withreadingid/" + node.getId())
					.type(MediaType.APPLICATION_JSON).get(ReadingModel.class);

			assertTrue(readingModel != null);
			assertEquals(expectedReadingModel.getDn14(), readingModel.getDn14());
			assertEquals(expectedReadingModel.getDn15(), readingModel.getDn15());
			tx.success();
		}
	}

	@Test
	public void getReadingWithFalseIdTest() {
		ClientResponse response = jerseyTest
				.resource()
				.path("/reading/getreading/withreadingid/" + 200)
				.type(MediaType.APPLICATION_JSON).get(ClientResponse.class);

		assertEquals(Status.INTERNAL_SERVER_ERROR,
				response.getClientResponseStatus());
	}

	@Test
	public void duplicateReadingTest() {
		try (Transaction tx = db.beginTx()) {
			ExecutionEngine engine = new ExecutionEngine(db);
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'showers'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			Node firstNode = nodes.next();
			assertFalse(nodes.hasNext());

			result = engine.execute("match (w:WORD {dn15:'sweet'}) return w");
			nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			Node secondNode = nodes.next();
			assertFalse(nodes.hasNext());

			// duplicate reading
			String jsonPayload = "{\"readings\":[" + firstNode.getId() + ", "
					+ secondNode.getId() + "], \"witnesses\":[\"A\",\"B\" ]}";
			ClientResponse response = jerseyTest.resource()
					.path("/reading/duplicatereading")
					.type(MediaType.APPLICATION_JSON)
					.post(ClientResponse.class, jsonPayload);

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
			for (int i = 14; i < 16; i++) {
				String key = "dn" + i;
				assertEquals(originalShowers.getProperty(key),
						duplicatedShowers.getProperty(key));
				assertEquals(originalSweet.getProperty(key),
						duplicatedSweet.getProperty(key));
			}

			tx.success();
		}
	}

	@Test
	public void duplicateReadingWitnessCrossingTest() {
		try (Transaction tx = db.beginTx()) {
			ExecutionEngine engine = new ExecutionEngine(db);
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'of'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			Node node = nodes.next();
			assertFalse(nodes.hasNext());

			// duplicate reading
			String jsonPayload = "{\"readings\":[" + node.getId()
					+ "], \"witnesses\":[\"A\",\"B\" ]}";
			ClientResponse response = jerseyTest.resource()
					.path("/reading/duplicatereading")
					.type(MediaType.APPLICATION_JSON)
					.post(ClientResponse.class, jsonPayload);

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
			for (int i = 14; i < 16; i++) {
				String key = "dn" + i;
				assertEquals(originalOf.getProperty(key),
						duplicatedOf.getProperty(key));
			}

			tx.success();
		}
	}

	@Test
	public void duplicateReadingWithNoWitnessesInJSONTest() {
		try (Transaction tx = db.beginTx()) {
			ExecutionEngine engine = new ExecutionEngine(db);
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'rood'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			Node firstNode = nodes.next();
			assertFalse(nodes.hasNext());

			// duplicate reading
			String jsonPayload = "{\"readings\":[" + firstNode.getId()
					+ "], \"witnesses\":[]}";
			ClientResponse response = jerseyTest.resource()
					.path("/reading/duplicatereading")
					.type(MediaType.APPLICATION_JSON)
					.post(ClientResponse.class, jsonPayload);

			assertEquals(Status.INTERNAL_SERVER_ERROR,
					response.getClientResponseStatus());
			assertEquals(
					"The witness list has to contain at least one witness",
					response.getEntity(String.class));

			tx.success();
		}
	}

	@Test
	public void duplicateReadingWithOnlyOneWitnessTest() {
		try (Transaction tx = db.beginTx()) {
			ExecutionEngine engine = new ExecutionEngine(db);
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'rood'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			Node firstNode = nodes.next();
			assertFalse(nodes.hasNext());

			// duplicate reading
			String jsonPayload = "{\"readings\":[" + firstNode.getId()
					+ "], \"witnesses\":[\"C\"]}";
			ClientResponse response = jerseyTest.resource()
					.path("/reading/duplicatereading")
					.type(MediaType.APPLICATION_JSON)
					.post(ClientResponse.class, jsonPayload);

			assertEquals(Status.INTERNAL_SERVER_ERROR,
					response.getClientResponseStatus());
			assertEquals("The reading has to be in at least two witnesses",
					response.getEntity(String.class));

			tx.success();
		}
	}

	@Test
	public void duplicateReadingWithNotAllowedWitnessesTest() {
		try (Transaction tx = db.beginTx()) {
			ExecutionEngine engine = new ExecutionEngine(db);
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'root'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			Node firstNode = nodes.next();
			assertFalse(nodes.hasNext());

			// duplicate reading
			String jsonPayload = "{\"readings\":[" + firstNode.getId()
					+ "], \"witnesses\":[\"C\"]}";
			ClientResponse response = jerseyTest.resource()
					.path("/reading/duplicatereading")
					.type(MediaType.APPLICATION_JSON)
					.post(ClientResponse.class, jsonPayload);

			assertEquals(Status.INTERNAL_SERVER_ERROR,
					response.getClientResponseStatus());
			assertEquals(
					"The reading has to be in the witnesses to be duplicated",
					response.getEntity(String.class));

			tx.success();
		}
	}

	@Test
	public void mergeReadingsTest() {
		try (Transaction tx = db.beginTx()) {
			ExecutionEngine engine = new ExecutionEngine(db);
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'fruit'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			Node firstNode = nodes.next();
			assertTrue(nodes.hasNext());
			Node secondNode = nodes.next();
			assertFalse(nodes.hasNext());

			// merge readings
			ClientResponse response = jerseyTest
					.resource()
					.path("/reading/mergereadings/first/" + firstNode.getId()
							+ "/second/" + secondNode.getId())
					.type(MediaType.APPLICATION_JSON)
					.post(ClientResponse.class);

			assertEquals(Status.OK, response.getClientResponseStatus());

			// should contain one reading less now
			testNumberOfReadings(28);

			testWitnesses();

			result = engine.execute("match (w:WORD {dn15:'fruit'}) return w");
			nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			Node stayingNode = nodes.next();
			assertFalse(nodes.hasNext());

			// test witnesses
			Relationship incoming = stayingNode.getSingleRelationship(
					ERelations.NORMAL, Direction.INCOMING);
			assertEquals("A", ((String[]) incoming.getProperty("lexemes"))[0]);
			assertEquals("C", ((String[]) incoming.getProperty("lexemes"))[1]);
			assertEquals("B", ((String[]) incoming.getProperty("lexemes"))[2]);

			for (Relationship outgoing : stayingNode.getRelationships(
					ERelations.NORMAL, Direction.OUTGOING)) {
				if (outgoing.getOtherNode(stayingNode).getProperty("dn15")
						.equals("the")) {
					assertEquals("A",
							((String[]) outgoing.getProperty("lexemes"))[0]);
					assertEquals("B",
							((String[]) outgoing.getProperty("lexemes"))[1]);
				}
				if (outgoing.getOtherNode(stayingNode).getProperty("dn15")
						.equals("to")) {
					assertEquals("C",
							((String[]) outgoing.getProperty("lexemes"))[0]);
				}
			}

			// test relationships
			int numberOfRelationships = 0;
			for (Relationship rel : stayingNode
					.getRelationships(ERelations.RELATIONSHIP)) {
				numberOfRelationships++;
				// test that relationships have been copied
				if (rel.getOtherNode(stayingNode).getProperty("dn15")
						.equals("when")) {
					assertEquals("grammatical", rel.getProperty("de11"));
					assertEquals("when", rel.getOtherNode(stayingNode)
							.getProperty("dn15"));
				}
				if (rel.getOtherNode(stayingNode).getProperty("dn15")
						.equals("the root")) {
					assertEquals("transposition", rel.getProperty("de11"));
					assertEquals("the root", rel.getOtherNode(stayingNode)
							.getProperty("dn15"));
				}

				// test that relationship between the two readings has been
				// deleted
				assertTrue(rel.getOtherNode(stayingNode) != stayingNode);
			}
			assertEquals(2, numberOfRelationships);

			tx.success();
		}
	}

	@Test
	public void mergeReadingsGetsCyclicTest() {
		try (Transaction tx = db.beginTx()) {
			ExecutionEngine engine = new ExecutionEngine(db);
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'drought'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			Node firstNode = nodes.next();
			assertTrue(nodes.hasNext());
			Node secondNode = nodes.next();
			assertFalse(nodes.hasNext());

			// merge readings
			ClientResponse response = jerseyTest
					.resource()
					.path("/reading/mergereadings/first/" + firstNode.getId()
							+ "/second/" + secondNode.getId())
					.type(MediaType.APPLICATION_JSON)
					.post(ClientResponse.class);

			assertEquals(Status.INTERNAL_SERVER_ERROR,
					response.getClientResponseStatus());
			assertEquals("Readings to be merged would make the graph cyclic",
					response.getEntity(String.class));

			testNumberOfReadings(29);

			testWitnesses();

			result = engine.execute("match (w:WORD {dn15:'drought'}) return w");
			nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			firstNode = nodes.next();
			assertTrue(nodes.hasNext());
			secondNode = nodes.next();
			assertFalse(nodes.hasNext());

			tx.success();
		}
	}

	@Test
	public void mergeReadingsGetsCyclicWithNodesFarApartTest() {
		try (Transaction tx = db.beginTx()) {
			ExecutionEngine engine = new ExecutionEngine(db);
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'to'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			Node firstNode = nodes.next();
			assertTrue(nodes.hasNext());
			Node secondNode = nodes.next();
			assertFalse(nodes.hasNext());

			// merge readings
			ClientResponse response = jerseyTest
					.resource()
					.path("/reading/mergereadings/first/" + firstNode.getId()
							+ "/second/" + secondNode.getId())
					.type(MediaType.APPLICATION_JSON)
					.post(ClientResponse.class);

			assertEquals(Status.INTERNAL_SERVER_ERROR,
					response.getClientResponseStatus());
			assertEquals("Readings to be merged would make the graph cyclic",
					response.getEntity(String.class));

			testNumberOfReadings(29);

			testWitnesses();

			result = engine.execute("match (w:WORD {dn15:'to'}) return w");
			nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			firstNode = nodes.next();
			assertTrue(nodes.hasNext());
			secondNode = nodes.next();
			assertFalse(nodes.hasNext());

			tx.success();
		}
	}

	@Test
	public void mergeReadingsWithoutRelationshipBetweenEachOtherTest() {
		try (Transaction tx = db.beginTx()) {
			ExecutionEngine engine = new ExecutionEngine(db);
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'his'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			Node firstNode = nodes.next();
			assertTrue(nodes.hasNext());
			Node secondNode = nodes.next();
			assertFalse(nodes.hasNext());

			// merge readings
			ClientResponse response = jerseyTest
					.resource()
					.path("/reading/mergereadings/first/" + firstNode.getId()
							+ "/second/" + secondNode.getId())
					.type(MediaType.APPLICATION_JSON)
					.post(ClientResponse.class);

			assertEquals(Status.INTERNAL_SERVER_ERROR,
					response.getClientResponseStatus());
			assertEquals(
					"Readings to be merged have to be connected with each other through a relationship",
					response.getEntity(String.class));

			testNumberOfReadings(29);

			testWitnesses();

			result = engine.execute("match (w:WORD {dn15:'his'}) return w");
			nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			firstNode = nodes.next();
			assertTrue(nodes.hasNext());
			secondNode = nodes.next();
			assertFalse(nodes.hasNext());

			tx.success();
		}
	}

	@Test
	public void mergeReadingsWithClassTwoRelationshipsTest() {
		try (Transaction tx = db.beginTx()) {
			ExecutionEngine engine = new ExecutionEngine(db);
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'march'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			Node firstNode = nodes.next();
			assertTrue(nodes.hasNext());
			Node secondNode = nodes.next();
			assertFalse(nodes.hasNext());

			// merge readings
			ClientResponse response = jerseyTest
					.resource()
					.path("/reading/mergereadings/first/" + firstNode.getId()
							+ "/second/" + secondNode.getId())
					.type(MediaType.APPLICATION_JSON)
					.post(ClientResponse.class);

			assertEquals(Status.INTERNAL_SERVER_ERROR,
					response.getClientResponseStatus());
			assertEquals(
					"Readings to be merged cannot contain class 2 relationships (transposition / repetition)",
					response.getEntity(String.class));

			testNumberOfReadings(29);

			testWitnesses();

			result = engine.execute("match (w:WORD {dn15:'march'}) return w");
			nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			firstNode = nodes.next();
			assertTrue(nodes.hasNext());
			secondNode = nodes.next();
			assertFalse(nodes.hasNext());

			tx.success();
		}
	}

	@Test
	public void mergeReadingsWithDifferentTextTest() {
		try (Transaction tx = db.beginTx()) {
			ExecutionEngine engine = new ExecutionEngine(db);
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'showers'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			Node firstNode = nodes.next();

			result = engine.execute("match (w:WORD {dn15:'sweet'}) return w");
			nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			Node secondNode = nodes.next();

			// merge readings
			ClientResponse response = jerseyTest
					.resource()
					.path("/reading/mergereadings/first/" + firstNode.getId()
							+ "/second/" + secondNode.getId())
					.type(MediaType.APPLICATION_JSON)
					.post(ClientResponse.class);

			assertEquals(Status.INTERNAL_SERVER_ERROR,
					response.getClientResponseStatus());
			assertEquals("Readings to be merged do not contain the same text",
					response.getEntity(String.class));

			tx.success();
		}
	}

	@Test
	public void splitReadingTest() {
		Node node;
		ExecutionEngine engine;
		ExecutionResult result;
		Iterator<Node> nodes;
		try (Transaction tx = db.beginTx()) {
			engine = new ExecutionEngine(db);
			result = engine
					.execute("match (w:WORD {dn15:'the root'}) return w");
			nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			node = nodes.next();
			assertFalse(nodes.hasNext());

			assertTrue(node.hasRelationship(ERelations.RELATIONSHIP));

			// delete relationship, so that splitting is possible
			node.getSingleRelationship(ERelations.RELATIONSHIP,
					Direction.INCOMING).delete();

			assertFalse(node.hasRelationship(ERelations.RELATIONSHIP));

			tx.success();
		}

		try (Transaction tx = db.beginTx()) {
			// split reading
			ClientResponse response = jerseyTest
					.resource()
					.path("/reading/splitreading/ofreading/" + node.getId())
					.type(MediaType.APPLICATION_JSON)
					.post(ClientResponse.class);

			assertEquals(Status.OK, response.getClientResponseStatus());

			result = engine.execute("match (w:WORD {dn15:'the'}) return w");
			nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			Node the1 = nodes.next();
			assertTrue(nodes.hasNext());
			Node the2 = nodes.next();
			assertTrue(nodes.hasNext());
			Node the3 = nodes.next();
			assertFalse(nodes.hasNext());

			assertEquals((long) 17, the2.getProperty("dn14"));
			assertEquals((long) 17, the3.getProperty("dn14"));

			result = engine.execute("match (w:WORD {dn15:'root'}) return w");
			nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			Node root1 = nodes.next();
			assertTrue(nodes.hasNext());
			Node root2 = nodes.next();
			assertFalse(nodes.hasNext());

			assertEquals((long) 18, root1.getProperty("dn14"));
			assertEquals((long) 18, root2.getProperty("dn14"));

			// should contain one reading more now
			testNumberOfReadings(30);

			testWitnesses();

			tx.success();
		}
	}

	@Test
	public void splitReadingWithRelationshipTest() {
		try (Transaction tx = db.beginTx()) {

			ExecutionEngine engine = new ExecutionEngine(db);
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'the root'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			Node node = nodes.next();
			assertFalse(nodes.hasNext());

			// split reading
			ClientResponse response = jerseyTest
					.resource()
					.path("/reading/splitreading/ofreading/" + node.getId())
					.type(MediaType.APPLICATION_JSON)
					.post(ClientResponse.class);

			assertEquals(Status.INTERNAL_SERVER_ERROR,
					response.getClientResponseStatus());
			assertEquals(
					"A reading to be splitted cannot be part of any relationship",
					response.getEntity(String.class));

			testNumberOfReadings(29);

			testWitnesses();

			tx.success();
		}
	}

	/**
	 * tests the splitting of a reading when there is no rank-gap after it
	 * should return error
	 */
	@Test
	public void splitReadingNoAvailableRankTest() {
		try (Transaction tx = db.beginTx()) {
			ExecutionEngine engine = new ExecutionEngine(db);
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'unto me'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			Node untoMe = nodes.next();
			assertFalse(nodes.hasNext());

			// split reading
			ClientResponse response = jerseyTest
					.resource()
					.path("/reading/splitreading/ofreading/" + untoMe.getId())
					.type(MediaType.APPLICATION_JSON)
					.post(ClientResponse.class);

			assertEquals(Status.INTERNAL_SERVER_ERROR,
					response.getClientResponseStatus());
			assertEquals(
					"There has to be a rank-gap after a reading to be splitted",
					response.getEntity(String.class));

			testWitnesses();

			tx.success();
		}
	}

	@Test
	public void splitReadingWithOnlyOneWordTest() {
		try (Transaction tx = db.beginTx()) {
			ExecutionEngine engine = new ExecutionEngine(db);
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'showers'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			Node node = nodes.next();
			assertFalse(nodes.hasNext());

			// split reading
			ClientResponse response = jerseyTest
					.resource()
					.path("/reading/splitreading/ofreading/" + node.getId())
					.type(MediaType.APPLICATION_JSON)
					.post(ClientResponse.class);

			assertEquals(Status.INTERNAL_SERVER_ERROR,
					response.getClientResponseStatus());
			assertEquals(
					"A reading to be splitted has to contain at least 2 words",
					response.getEntity(String.class));

			testNumberOfReadings(29);

			testWitnesses();

			tx.success();
		}
	}

	/**
	 * test that all readings of a tradition are returned sorted ascending
	 * according to rank
	 */
	@Test
	public void allReadingsOfTraditionTest() {
		List<ReadingModel> listOfReadings = jerseyTest.resource()
				.path("/reading/getallreadings/fromtradition/" + tradId)
				.get(new GenericType<List<ReadingModel>>() {
				});
		Collections.sort(listOfReadings);

		assertEquals(29, listOfReadings.size());

		String expectedTest = "#START# when april april with his his showers sweet with fruit fruit the to drought march of march drought has pierced teh to unto me rood the root the root #END#";
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
		String falseTradId = tradId + 1;
		ClientResponse response = jerseyTest.resource()
				.path("/reading/getallreadings/fromtradition/" + falseTradId)
				.get(ClientResponse.class);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
				response.getStatus());
		assertEquals("Could not find tradition with this id",
				response.getEntity(String.class));
	}

	@Test
	public void identicalReadingsOneResultTest() {
		List<ReadingModel> identicalReadings = new ArrayList<ReadingModel>();

		List<List<ReadingModel>> listOfIdenticalReadings = jerseyTest
				.resource()
				.path("/reading/getidenticalreadings/fromtradition/" + tradId
						+ "/fromstartrank/3/toendrank/8")
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

		List<List<ReadingModel>> listOfIdenticalReadings = jerseyTest
				.resource()
				.path("/reading/getidenticalreadings/fromtradition/" + tradId
						+ "/fromstartrank/1/toendrank/8")
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
		ClientResponse response = jerseyTest
				.resource()
				.path("/reading/getidenticalreadings/fromtradition/" + tradId
						+ "/fromstartrank/10/toendrank/15")
				.get(ClientResponse.class);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
				response.getStatus());
		assertEquals("no identical readings were found",
				response.getEntity(String.class));
	}

	@Test
	public void couldBeIdenticalReadingsTest() {
		List<List<ReadingModel>> couldBeIdenticalReadings = jerseyTest
				.resource()
				.path("/reading/couldbeidenticalreadings/fromtradition/"
						+ tradId + "/fromstartrank/1/toendrank/15")
				.get(new GenericType<List<List<ReadingModel>>>() {
				});
		assertEquals(2, couldBeIdenticalReadings.size());

		assertEquals(couldBeIdenticalReadings.get(0).get(0).getDn15(),
				couldBeIdenticalReadings.get(0).get(1).getDn15());
		assertEquals("fruit", couldBeIdenticalReadings.get(0).get(0).getDn15());

		assertFalse(couldBeIdenticalReadings.get(0).get(0).getDn14() == couldBeIdenticalReadings
				.get(0).get(1).getDn14());
	}

	/**
	 * should not find any could-be identical readings
	 */
	@Test
	public void couldBeIdenticalReadingsNoResultTest() {
		ClientResponse response = jerseyTest
				.resource()
				.path("/reading/couldbeidenticalreadings/fromtradition/"
						+ tradId + "/fromstartrank/1/toendrank/9")
				.get(ClientResponse.class);

		assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
				response.getStatus());
		assertEquals("no identical readings were found",
				response.getEntity(String.class));
	}

	@Test
	public void compressReadingTest() {
		Node showers, sweet;
		ExecutionEngine engine = new ExecutionEngine(db);
		try (Transaction tx = db.beginTx()) {
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
					.path("/reading/compressreadings/first/" + showers.getId()
							+ "/second/" + sweet.getId()).post(ClientResponse.class);

			assertEquals(Response.Status.OK.getStatusCode(), res.getStatus());
			assertEquals("successfully compressed readings",
					res.getEntity(String.class));

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
			testWitnesses();

			List<ReadingModel> listOfReadings = jerseyTest.resource()
					.path("/reading/getallreadings/fromtradition/" + tradId)
					.get(new GenericType<List<ReadingModel>>() {
					});
			Collections.sort(listOfReadings);

			// tradition still has all the texts
			String expectedTest = "#START# when april april with his his showers sweet with fruit fruit the to drought march of march drought has pierced teh to unto me rood the root the root #END#";
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
		ExecutionEngine engine = new ExecutionEngine(db);
		try (Transaction tx = db.beginTx()) {
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
					.path("/reading/compressreadings/first/" + showers.getId() 
							+ "/second/" + fruit.getId()).post(ClientResponse.class);

			assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
					response.getStatus());
			assertEquals("reading are not neighbors. could not compress",
					response.getEntity(String.class));

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
		ExecutionEngine engine = new ExecutionEngine(db);
		long withReadId;
		try (Transaction tx = db.beginTx()) {
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'with'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assert (nodes.hasNext());
			withReadId = nodes.next().getId();
			tx.success();
		}

		ReadingModel actualResponse = jerseyTest
				.resource()
				.path("/reading/getnextreading/fromwitness/A/ofreading/"
						+ withReadId).get(ReadingModel.class);
		assertEquals("his", actualResponse.getDn15());
	}

	@Test
	public void nextReadingWithTwoWitnessesTest() {
		ExecutionEngine engine = new ExecutionEngine(db);
		long piercedReadId;
		try (Transaction tx = db.beginTx()) {
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'pierced'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assert (nodes.hasNext());
			piercedReadId = nodes.next().getId();
			tx.success();
		}

		ReadingModel actualResponse = jerseyTest
				.resource()
				.path("/reading/getnextreading/fromwitness/A/ofreading/"
						+ piercedReadId).get(ReadingModel.class);
		assertEquals("unto me", actualResponse.getDn15());

		actualResponse = jerseyTest
				.resource()
				.path("/reading/getnextreading/fromwitness/B/ofreading/"
						+ piercedReadId).get(ReadingModel.class);
		assertEquals("to", actualResponse.getDn15());
	}

	@Test
	public void nextReadingLastNodeTest() {
		ExecutionEngine engine = new ExecutionEngine(db);
		long readId;
		try (Transaction tx = db.beginTx()) {
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'the root'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			readId = nodes.next().getId();

			tx.success();
		}

		ClientResponse response = jerseyTest
				.resource()
				.path("/reading/getnextreading/fromwitness/B/ofreading/"
						+ readId).get(ClientResponse.class);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
				response.getStatus());
		assertEquals("this was the last reading of this witness",
				response.getEntity(String.class));
	}

	@Test
	public void previousReadingTest() {
		ExecutionEngine engine = new ExecutionEngine(db);
		long readId;
		try (Transaction tx = db.beginTx()) {
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'with'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assert (nodes.hasNext());
			readId = nodes.next().getId();

			tx.success();
		}
		ReadingModel actualResponse = jerseyTest
				.resource()
				.path("/reading/getpreviousreading/fromwitness/A/ofreading/"
						+ readId).get(ReadingModel.class);
		assertEquals("april", actualResponse.getDn15());
	}

	@Test
	public void previousReadingTwoWitnessesTest() {
		ExecutionEngine engine = new ExecutionEngine(db);
		long ofId;
		try (Transaction tx = db.beginTx()) {
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'of'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assert (nodes.hasNext());
			ofId = nodes.next().getId();

			tx.success();
		}
		ReadingModel actualResponse = jerseyTest
				.resource()
				.path("/reading/getpreviousreading/fromwitness/A/ofreading/"
						+ ofId).get(ReadingModel.class);
		assertEquals("drought", actualResponse.getDn15());

		actualResponse = jerseyTest
				.resource()
				.path("/reading/getpreviousreading/fromwitness/B/ofreading/"
						+ ofId).get(ReadingModel.class);
		assertEquals("march", actualResponse.getDn15());
	}

	@Test
	public void previousReadingFirstNodeTest() {
		ExecutionEngine engine = new ExecutionEngine(db);
		long readId;
		try (Transaction tx = db.beginTx()) {
			ExecutionResult result = engine
					.execute("match (w:WORD {dn15:'when'}) return w");
			Iterator<Node> nodes = result.columnAs("w");
			assertTrue(nodes.hasNext());
			readId = nodes.next().getId();

			tx.success();
		}
		ClientResponse actualResponse = jerseyTest
				.resource()
				.path("/reading/getpreviousreading/fromwitness/A/ofreading/"
						+ readId).get(ClientResponse.class);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
				actualResponse.getStatus());
		assertEquals("there is no previous reading to this reading",
				actualResponse.getEntity(String.class));
	}

	@Test
	public void randomNodeExistsTest() {
		ExecutionEngine engine = new ExecutionEngine(db);
		try (Transaction tx = db.beginTx()) {
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
		try (Transaction tx = db.beginTx()) {
			ResourceIterable<Node> tradNodes = db.findNodesByLabelAndProperty(
					Nodes.TRADITION, "dg1", "Tradition");
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
		db.shutdown();
		jerseyTest.tearDown();
	}

}
