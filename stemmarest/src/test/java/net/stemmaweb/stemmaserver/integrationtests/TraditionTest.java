package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Reading;
import net.stemmaweb.rest.Relations;
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
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.test.TestGraphDatabaseFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;

/**
 * 
 * @author Severin
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class TraditionTest {
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

			rootNode.createRelationshipTo(node, Relations.NORMAL);
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
	public void getReadingTest() throws JsonProcessingException {

		String expected = "{\"dn1\":\"16\",\"dn2\":\"0\",\"dn11\":\"Default\",\"dn14\":2,\"dn15\":\"april\"}";

		Response resp = tradition.getReading(tradId, 16);

		ObjectMapper mapper = new ObjectMapper();
		mapper.setSerializationInclusion(Include.NON_NULL);
		String json = mapper.writeValueAsString(resp.getEntity());

		assertEquals(expected, json);

	}

	@Test
	public void duplicateReadingTest() {
		// duplicate reading
		jerseyTest.resource().path("/tradition/duplicate/" + tradId + "/16/A/B,C")
				.type(MediaType.APPLICATION_JSON)
				.get(ClientResponse.class);

		// read result from database
		ReadingModel original = null;
		ReadingModel duplicate = null;

		try (Transaction tx = mockDbService.beginTx()) {
			Node nextNode = mockDbService.getNodeById(16);
			original = Reading.readingModelFromNode(nextNode);
			Iterable<Relationship> rels = nextNode.getRelationships(Relations.NORMAL, Direction.BOTH);
			for (Relationship relationship : rels)
				assertEquals("A", relationship.getProperty("lexemes"));

			nextNode = mockDbService.getNodeById(29);
			duplicate = Reading.readingModelFromNode(nextNode);
			rels = nextNode.getRelationships(Relations.NORMAL, Direction.BOTH);
			for (Relationship relationship : rels)
				assertEquals("B,C", relationship.getProperty("lexemes"));
			
			tx.success();
		}

		// test properties
		assertEquals("16", original.getDn1());
		assertEquals("0", original.getDn2());
		assertEquals("Default", original.getDn11());
		assertEquals(2, original.getDn14().longValue());
		assertEquals("april", original.getDn15());

		assertEquals("29", duplicate.getDn1());
		assertEquals("0", duplicate.getDn2());
		assertEquals("Default", duplicate.getDn11());
		assertEquals(2, duplicate.getDn14().longValue());
		assertEquals("april", duplicate.getDn15());
	}

	@Test
	public void mergeReadingsTest() {
		// duplicate reading
		jerseyTest.resource().path("/tradition/duplicate/" + tradId + "/16/A/BC").type(MediaType.APPLICATION_JSON)
				.get(ClientResponse.class);
		// merge readings again
		jerseyTest.resource().path("/tradition/merge/" + tradId + "/16/29")
				.type(MediaType.APPLICATION_JSON).get(ClientResponse.class);

		// read result from database
		ReadingModel merged = null;

		try (Transaction tx = mockDbService.beginTx()) {
			Node nextNode = mockDbService.getNodeById(16);
			merged = Reading.readingModelFromNode(nextNode);
			Iterable<Relationship> rels = nextNode.getRelationships(Relations.NORMAL, Direction.BOTH);
			for (Relationship relationship : rels)
				assertEquals("ABC", relationship.getProperty("lexemes"));

			tx.success();
		}

		// test properties
		assertEquals("16", merged.getDn1());
		assertEquals("0", merged.getDn2());
		assertEquals("Default", merged.getDn11());
		assertEquals(2, merged.getDn14().longValue());
		assertEquals("april", merged.getDn15());
	}

	@Test(expected = NotFoundException.class)
	public void mergeReadingsAbsenceOfMergedNodeTest() {
		// duplicate reading
		jerseyTest.resource().path("/tradition/duplicate/" + tradId + "/16/A/BC").type(MediaType.APPLICATION_JSON)
				.get(ClientResponse.class);
		// merge readings again
		jerseyTest.resource().path("/tradition/merge/" + tradId + "/16/29").type(MediaType.APPLICATION_JSON)
				.get(ClientResponse.class);

		try (Transaction tx = mockDbService.beginTx()) {
			mockDbService.getNodeById(29);

			tx.success();
		}
	}

	// TODO not fully implemented yet
	@Test
	public void splitReadingTest() {
		ClientResponse response = jerseyTest.resource().path("/tradition/split/" + tradId + "/16")
				.type(MediaType.APPLICATION_JSON).get(ClientResponse.class);
		System.out.println(response);
	}

	@Test
	public void getAllRelationshipsTest() {
		String jsonPayload = "{\"isAdmin\":0,\"id\":1}";
		jerseyTest.resource().path("/user/create").type(MediaType.APPLICATION_JSON)
				.post(ClientResponse.class, jsonPayload);

		RelationshipModel rel = new RelationshipModel();
		rel.setSource("16");
		rel.setTarget("27");
		rel.setId("36");
		rel.setDe8("april");
		rel.setDe6("no");
		rel.setDe9("april");
		rel.setDe1("0");
		rel.setDe11("transposition");
		rel.setDe10("local");

		List<RelationshipModel> relationships = jerseyTest.resource()
				.path("/tradition/relation/" + tradId + "/relationships")
				.get(new GenericType<List<RelationshipModel>>() {
				});
		RelationshipModel relLoaded = relationships.get(2);

		assertEquals(rel.getSource(), relLoaded.getSource());
		assertEquals(rel.getTarget(), relLoaded.getTarget());
		assertEquals(rel.getId(), relLoaded.getId());
		assertEquals(rel.getDe8(), relLoaded.getDe8());
		assertEquals(rel.getDe6(), relLoaded.getDe6());
		assertEquals(rel.getDe9(), relLoaded.getDe9());
		assertEquals(rel.getDe1(), relLoaded.getDe1());
		assertEquals(rel.getDe11(), relLoaded.getDe11());
		assertEquals(rel.getDe10(), relLoaded.getDe10());

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
