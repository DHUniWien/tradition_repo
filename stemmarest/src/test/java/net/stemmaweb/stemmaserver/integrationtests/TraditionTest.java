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
import net.stemmaweb.model.TextInfoModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Reading;
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
import com.sun.jersey.api.client.ClientResponse.Status;
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
		String jsonPayload = "{\"readings\":[16, 18], \"witnesses\":[\"B\", \"C\"]}";
		ClientResponse response = jerseyTest.resource().path("/tradition/duplicate/" + tradId)
				.type(MediaType.APPLICATION_JSON)
				.post(ClientResponse.class, jsonPayload);

		// read result from database
		ReadingModel original = null;
		ReadingModel duplicate = null;

		try (Transaction tx = mockDbService.beginTx()) {
			Node nextNode = mockDbService.getNodeById(16);
			original = Reading.readingModelFromNode(nextNode);
			Iterable<Relationship> rels = nextNode.getRelationships(ERelations.NORMAL, Direction.BOTH);
			for (Relationship relationship : rels)
				assertEquals("A", ((String[]) relationship.getProperty("lexemes"))[0]);
			assertEquals(16, nextNode.getId());
			assertEquals("16", original.getDn1());
			assertEquals("0", original.getDn2());
			assertEquals("Default", original.getDn11());
			assertEquals(2, original.getDn14().longValue());
			assertEquals("april", original.getDn15());

			nextNode = mockDbService.getNodeById(29);
			duplicate = Reading.readingModelFromNode(nextNode);
			rels = nextNode.getRelationships(ERelations.NORMAL, Direction.BOTH);
			for (Relationship relationship : rels) {
				assertEquals("B", ((String[]) relationship.getProperty("lexemes"))[0]);
				assertEquals("C", ((String[]) relationship.getProperty("lexemes"))[1]);
			}
			assertEquals(29, nextNode.getId());
			assertEquals("29", duplicate.getDn1());
			assertEquals("0", duplicate.getDn2());
			assertEquals("Default", duplicate.getDn11());
			assertEquals(2, duplicate.getDn14().longValue());
			assertEquals("april", duplicate.getDn15());


			nextNode = mockDbService.getNodeById(18);
			original = Reading.readingModelFromNode(nextNode);
			rels = nextNode.getRelationships(ERelations.NORMAL, Direction.BOTH);

			for (Relationship relationship : rels)
				assertEquals("A", ((String[]) relationship.getProperty("lexemes"))[0]);
			assertEquals(18, nextNode.getId());
			assertEquals("18", original.getDn1());
			assertEquals("0", original.getDn2());
			assertEquals("Default", original.getDn11());
			assertEquals(16, original.getDn14().longValue());
			assertEquals("unto", original.getDn15());

			nextNode = mockDbService.getNodeById(30);
			duplicate = Reading.readingModelFromNode(nextNode);
			rels = nextNode.getRelationships(ERelations.NORMAL, Direction.BOTH);
			for (Relationship relationship : rels) {
				assertEquals("B", ((String[]) relationship.getProperty("lexemes"))[0]);
				assertEquals("C", ((String[]) relationship.getProperty("lexemes"))[1]);
			}
			assertEquals(30, nextNode.getId());
			assertEquals("30", duplicate.getDn1());
			assertEquals("0", duplicate.getDn2());
			assertEquals("Default", duplicate.getDn11());
			assertEquals(16, duplicate.getDn14().longValue());
			assertEquals("unto", duplicate.getDn15());

			tx.success();
		}

		assertEquals(Status.OK, response.getClientResponseStatus());
	}

	@Test
	public void mergeReadingsTest() {
		// duplicate reading
		String jsonPayload = "{\"readings\":[16, 18], \"witnesses\":[\"B\", \"C\"]}";
		jerseyTest.resource().path("/tradition/duplicate/" + tradId).type(MediaType.APPLICATION_JSON)
				.post(ClientResponse.class, jsonPayload);
		// merge readings again
		ClientResponse response = jerseyTest.resource().path("/tradition/merge/" + tradId + "/16/29")
				.type(MediaType.APPLICATION_JSON).post(ClientResponse.class);

		// read result from database
		ReadingModel merged = null;

		try (Transaction tx = mockDbService.beginTx()) {
			Node nextNode = mockDbService.getNodeById(16);
			merged = Reading.readingModelFromNode(nextNode);
			Iterable<Relationship> rels = nextNode.getRelationships(ERelations.NORMAL, Direction.BOTH);
			for (Relationship relationship : rels) {
				assertEquals("A", ((String[]) relationship.getProperty("lexemes"))[0]);
				assertEquals("B", ((String[]) relationship.getProperty("lexemes"))[1]);
				assertEquals("C", ((String[]) relationship.getProperty("lexemes"))[2]);
			}
			assertEquals(16, nextNode.getId());
			assertEquals("16", merged.getDn1());
			assertEquals("0", merged.getDn2());
			assertEquals("Default", merged.getDn11());
			assertEquals(2, merged.getDn14().longValue());
			assertEquals("april", merged.getDn15());

			tx.success();
		}

		assertEquals(Status.OK, response.getClientResponseStatus());
	}

	@Test(expected = NotFoundException.class)
	public void mergeReadingsAbsenceOfMergedNodeTest() {
		// duplicate reading
		jerseyTest.resource().path("/tradition/duplicate/" + tradId + "/16/A/BC").type(MediaType.APPLICATION_JSON)
				.post(ClientResponse.class);
		// merge readings again
		ClientResponse response = jerseyTest.resource().path("/tradition/merge/" + tradId + "/16/29")
				.type(MediaType.APPLICATION_JSON)
				.post(ClientResponse.class);

		try (Transaction tx = mockDbService.beginTx()) {
			mockDbService.getNodeById(29);

			tx.success();
		}

		assertEquals(Status.NOT_FOUND, response.getClientResponseStatus());
	}

	// TODO not fully implemented yet waiting for compress to be implemented
	@Test
	public void splitReadingTest() {
		// compress readings

		// split reading again
		ClientResponse response = jerseyTest.resource().path("/tradition/split/" + tradId + "/16")
				.type(MediaType.APPLICATION_JSON).post(ClientResponse.class);
		System.out.println(response);

		// assertEquals(Status.OK, response.getClientResponseStatus());
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
	 * Test whether all readings are returned correctly
	 */
	@Test
	public void getTraditionReadings(){
	   
    	ReadingModel readFirst = new ReadingModel();
    	readFirst.setDn15("when");
    	readFirst.setDn11("Default");
    	readFirst.setDn14(new Long(1));
    	
    	ReadingModel readLast = new ReadingModel();
    	readLast.setDn15("root");
    	readLast.setDn11("Default");
    	readLast.setDn14(new Long(1));
    	
    	
    	List<ReadingModel> readings = jerseyTest.resource().path("/tradition/readings/" + tradId)
    			.get(new GenericType<List<ReadingModel>>(){});
    	ReadingModel firstReading = readings.get(0);
    	assertEquals(readFirst.getDn15(), firstReading.getDn15());
    	assertEquals(readFirst.getDn11(), firstReading.getDn11());
    	
    	ReadingModel lastReading = readings.get(readings.size()-1);
    	assertEquals(readLast.getDn15(), lastReading.getDn15());
    	assertEquals(readLast.getDn11(), lastReading.getDn11());
    	
	}
	
	@Test
	public void getDot()
	{
		String str = jerseyTest.resource().path("/tradition/getdot/" + tradId).type(MediaType.APPLICATION_JSON).get(String.class);

		String expected = "digraph { \n" +
			"n4 [label=\"#START#\"];\n" +
			"n4->n5[label=\"A,B,C\";id=\"e0\"];\n" +
			"n5 [label=\"when\"];\n" +
			"n5->n24[label=\"B,C\";id=\"e1\"];\n" +
			"n5->n16[label=\"A\";id=\"e2\"];\n" +
			"n24 [label=\"showers\"];\n" +
			"n24->n25[label=\"A,B,C\";id=\"e3\"];\n" +
			"n16 [label=\"april\"];\n" +
			"n16->n22[label=\"A\";id=\"e4\"];\n" +
			"n25 [label=\"sweet\"];\n" +
			"n25->n26[label=\"A,B,C\";id=\"e6\"];\n" +
			"n22 [label=\"with\"];\n" +
			"n22->n23[label=\"A\";id=\"e7\"];\n" +
			"n26 [label=\"with\"];\n" +
			"n26->n7[label=\"A\";id=\"e8\"];\n" +
			"n26->n27[label=\"B,C\";id=\"e9\"];\n" +
			"n23 [label=\"his\"];\n" +
			"n23->n24[label=\"A\";id=\"e10\"];\n" +
			"n7 [label=\"fruit\"];\n" +
			"n7->n8[label=\"A,B\";id=\"e11\"];\n" +
			"n7->n9[label=\"C\";id=\"e12\"];\n" +
			"n27 [label=\"april\"];\n" +
			"n27->n7[label=\"B,C\";id=\"e13\"];\n" +
			"n8 [label=\"the\"];\n" +
			"n8->n11[label=\"A\";id=\"e14\"];\n" +
			"n8->n10[label=\"B\";id=\"e15\"];\n" +
			"n9 [label=\"teh\"];\n" +
			"n9->n11[label=\"C\";id=\"e16\"];\n" +
			"n11 [label=\"drought\"];\n" +
			"n11->n12[label=\"A,C\";id=\"e17\"];\n" +
			"n10 [label=\"march\"];\n" +
			"n10->n12[label=\"B\";id=\"e19\"];\n" +
			"n12 [label=\"of\"];\n" +
			"n12->n13[label=\"A,C\";id=\"e21\"];\n" +
			"n12->n14[label=\"B\";id=\"e22\"];\n" +
			"n13 [label=\"march\"];\n" +
			"n13->n15[label=\"A,C\";id=\"e23\"];\n" +
			"n14 [label=\"drought\"];\n" +
			"n14->n15[label=\"B\";id=\"e24\"];\n" +
			"n15 [label=\"has\"];\n" +
			"n15->n17[label=\"A,B,C\";id=\"e25\"];\n" +
			"n17 [label=\"pierced\"];\n" +
			"n17->n20[label=\"C\";id=\"e26\"];\n" +
			"n17->n19[label=\"B\";id=\"e27\"];\n" +
			"n17->n18[label=\"A\";id=\"e28\"];\n" +
			"n20 [label=\"teh\"];\n" +
			"n20->n28[label=\"C\";id=\"e29\"];\n" +
			"n19 [label=\"to\"];\n" +
			"n19->n21[label=\"B\";id=\"e30\"];\n" +
			"n18 [label=\"unto\"];\n" +
			"n18->n21[label=\"A\";id=\"e31\"];\n" +
			"n28 [label=\"rood\"];\n" +
			"n28->n3[label=\"C\";id=\"e32\"];\n" +
			"n21 [label=\"the\"];\n" +
			"n21->n6[label=\"A,B\";id=\"e33\"];\n" +
			"n3 [label=\"#END#\"];\n" +
			"n6 [label=\"root\"];\n" +
			"n6->n3[label=\"A,B\";id=\"e34\"];\n" +
			"subgraph { edge [dir=none]\n" +
			"n16->n27[style=dotted;label=\"transposition\";id=\"e5\"];\n" +
			"n11->n14[style=dotted;label=\"transposition\";id=\"e18\"];\n" +
			"n10->n13[style=dotted;label=\"transposition\";id=\"e20\"];\n" +
			" } }\n";
		assertEquals(expected, str);
		
	}
	
	/**
	 * Test if it is posibible to change the user of a Tradition
	 */
	@Test
	public void changeOwnerOfATraditionTestDH44(){
		
		/*
		 * Create a second user with id 42
		 */
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		try (Transaction tx = mockDbService.beginTx()) {
			ExecutionResult result = engine.execute("match (n:ROOT) return n");
			Iterator<Node> nodes = result.columnAs("n");
			Node rootNode = nodes.next();

			Node node = mockDbService.createNode(Nodes.USER);
			node.setProperty("id", "42");
			node.setProperty("isAdmin", "1");

			rootNode.createRelationshipTo(node, ERelations.NORMAL);
			tx.success();
		}

		/*
		 * The user with id 42 has no tradition
		 */
		ExecutionResult result = null;
		try (Transaction tx = mockDbService.beginTx()) {
			result = engine.execute("match (n)<-[:NORMAL]-(userId:USER {id:'42'}) return n");
			Iterator<Node> tradIterator = result.columnAs("n");
			assertTrue(!tradIterator.hasNext());

			tx.success();

		}
		
		/*
		 * The user with id 1 has tradition
		 */
		result = null;
		try (Transaction tx = mockDbService.beginTx()) {
			result = engine.execute("match (n)<-[:NORMAL]-(userId:USER {id:'1'}) return n");
			Iterator<Node> tradIterator = result.columnAs("n");
			Node tradNode = tradIterator.next();
			TraditionModel tradition = new TraditionModel();
			tradition.setId(tradNode.getProperty("id").toString());
			tradition.setName(tradNode.getProperty("dg1").toString());

			assertTrue(tradition.getId().equals(tradId));
			assertTrue(tradition.getName().equals("Tradition"));
			
			tx.success();
		}
		
		/*
		 * Change the owner of the tradition 
		 */
		TextInfoModel textInfo = new TextInfoModel();
		textInfo.setName("RenamedTraditionName");
		textInfo.setLanguage("nital");
		textInfo.setIsPublic("0");
		textInfo.setOwnerId("42");
		
		ClientResponse ownerChangeResponse = jerseyTest.resource().path("/tradition/"+tradId).type(MediaType.APPLICATION_JSON).post(ClientResponse.class,textInfo);
		assertEquals(Response.Status.OK.getStatusCode(), ownerChangeResponse.getStatus());
		
		/*
		 * Test if user with id 42 has now the tradition
		 */
		result = null;
		try (Transaction tx = mockDbService.beginTx()) {
			result = engine.execute("match (n)<-[:NORMAL]-(userId:USER {id:'42'}) return n");
			Iterator<Node> tradIterator = result.columnAs("n");
			Node tradNode = tradIterator.next();
			TraditionModel tradition = new TraditionModel();
			tradition.setId(tradNode.getProperty("id").toString());
			tradition.setName(tradNode.getProperty("dg1").toString());

			assertTrue(tradition.getId().equals(tradId));
			assertTrue(tradition.getName().equals("Tradition"));

			tx.success();

		}
		
		/*
		 * The user with id 1 has no tradition
		 */
		result = null;
		try (Transaction tx = mockDbService.beginTx()) {
			result = engine.execute("match (n)<-[:NORMAL]-(userId:USER {id:'1'}) return n");
			Iterator<Node> tradIterator = result.columnAs("n");
			assertTrue(!tradIterator.hasNext());

			tx.success();

		}
	}
	
	/**
	 * Test if there is the correct error when trying to change a tradition with an invalid userid
	 */
	@Test
	public void changeOwnerOfATraditionTestWithWrongUserDH44(){
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		/* Preconditon
		 * The user with id 1 has tradition
		 */
		ExecutionResult result = null;
		try (Transaction tx = mockDbService.beginTx()) {
			result = engine.execute("match (n)<-[:NORMAL]-(userId:USER {id:'1'}) return n");
			Iterator<Node> tradIterator = result.columnAs("n");
			Node tradNode = tradIterator.next();
			TraditionModel tradition = new TraditionModel();
			tradition.setId(tradNode.getProperty("id").toString());
			tradition.setName(tradNode.getProperty("dg1").toString());

			assertTrue(tradition.getId().equals(tradId));
			assertTrue(tradition.getName().equals("Tradition"));
			
			tx.success();
		}
		
		/*
		 * Change the owner of the tradition 
		 */
		TextInfoModel textInfo = new TextInfoModel();
		textInfo.setName("RenamedTraditionName");
		textInfo.setLanguage("nital");
		textInfo.setIsPublic("0");
		textInfo.setOwnerId("1337");
		
		ClientResponse removalResponse = jerseyTest.resource().path("/tradition/"+tradId).type(MediaType.APPLICATION_JSON).post(ClientResponse.class,textInfo);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), removalResponse.getStatus());
		assertEquals(removalResponse.getEntity(String.class), "Error: A user with this id does not exist");
	
		/* PostCondition
		 * The user with id 1 has still tradition
		 */
		result = null;
		try (Transaction tx = mockDbService.beginTx()) {
			result = engine.execute("match (n)<-[:NORMAL]-(userId:USER {id:'1'}) return n");
			Iterator<Node> tradIterator = result.columnAs("n");
			Node tradNode = tradIterator.next();
			TraditionModel tradition = new TraditionModel();
			tradition.setId(tradNode.getProperty("id").toString());
			tradition.setName(tradNode.getProperty("dg1").toString());

			assertTrue(tradition.getId().equals(tradId));
			assertTrue(tradition.getName().equals("Tradition"));
			
			tx.success();
		}
	}
	
	/**
	 * Test if it is posibible to change the user of a Tradition with invalid traditionid
	 */
	@Test
	public void changeOwnerOfATraditionTestWithInvalidTradidDH44(){
		
		/*
		 * Create a second user with id 42
		 */
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		try (Transaction tx = mockDbService.beginTx()) {
			ExecutionResult result = engine.execute("match (n:ROOT) return n");
			Iterator<Node> nodes = result.columnAs("n");
			Node rootNode = nodes.next();

			Node node = mockDbService.createNode(Nodes.USER);
			node.setProperty("id", "42");
			node.setProperty("isAdmin", "1");

			rootNode.createRelationshipTo(node, ERelations.NORMAL);
			tx.success();
		}

		/*
		 * The user with id 42 has no tradition
		 */
		ExecutionResult result = null;
		try (Transaction tx = mockDbService.beginTx()) {
			result = engine.execute("match (n)<-[:NORMAL]-(userId:USER {id:'42'}) return n");
			Iterator<Node> tradIterator = result.columnAs("n");
			assertTrue(!tradIterator.hasNext());

			tx.success();

		}
		
		/*
		 * The user with id 1 has tradition
		 */
		result = null;
		try (Transaction tx = mockDbService.beginTx()) {
			result = engine.execute("match (n)<-[:NORMAL]-(userId:USER {id:'1'}) return n");
			Iterator<Node> tradIterator = result.columnAs("n");
			Node tradNode = tradIterator.next();
			TraditionModel tradition = new TraditionModel();
			tradition.setId(tradNode.getProperty("id").toString());
			tradition.setName(tradNode.getProperty("dg1").toString());

			assertTrue(tradition.getId().equals(tradId));
			assertTrue(tradition.getName().equals("Tradition"));
			
			tx.success();
		}
		
		/*
		 * Change the owner of the tradition 
		 */
		TextInfoModel textInfo = new TextInfoModel();
		textInfo.setName("RenamedTraditionName");
		textInfo.setLanguage("nital");
		textInfo.setIsPublic("0");
		textInfo.setOwnerId("42");
		
		ClientResponse removalResponse = jerseyTest.resource().path("/tradition/1337").type(MediaType.APPLICATION_JSON).post(ClientResponse.class,textInfo);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), removalResponse.getStatus());
		assertEquals(removalResponse.getEntity(String.class),"Tradition not found");
		
		/*
		 * Post condition nothing has changed
		 * 
		 */
		
		/*
		 * Test if user with id 1 has still the old tradition
		 */
		result = null;
		try (Transaction tx = mockDbService.beginTx()) {
			result = engine.execute("match (n)<-[:NORMAL]-(userId:USER {id:'1'}) return n");
			Iterator<Node> tradIterator = result.columnAs("n");
			Node tradNode = tradIterator.next();
			TraditionModel tradition = new TraditionModel();
			tradition.setId(tradNode.getProperty("id").toString());
			tradition.setName(tradNode.getProperty("dg1").toString());

			assertTrue(tradition.getId().equals(tradId));
			assertTrue(tradition.getName().equals("Tradition"));

			tx.success();

		}
		
		/*
		 * The user with id 42 has still no tradition
		 */
		result = null;
		try (Transaction tx = mockDbService.beginTx()) {
			result = engine.execute("match (n)<-[:NORMAL]-(userId:USER {id:'42'}) return n");
			Iterator<Node> tradIterator = result.columnAs("n");
			assertTrue(!tradIterator.hasNext());

			tx.success();

		}
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
