package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.model.TraditionMetadataModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Tradition;
import net.stemmaweb.services.DatabaseService;
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
import org.neo4j.graphdb.Transaction;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;

/**
 * 
 * Contains all tests for the api calls related to the tradition.
 * 
 * @author PSE FS 2015 Team2
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class TraditionTest {
	private String tradId;
	
	GraphDatabaseService db;

	/*
	 * JerseyTest is the test environment to Test api calls it provides a
	 * grizzly http service
	 */
	private JerseyTest jerseyTest;
	

	private GraphMLToNeo4JParser importResource;
	private Tradition tradition;


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
	public void getAllTraditionsTest()
	{
		// import a second tradition into the db
		String filename = "";
		if (OSDetector.isWin())
			filename = "src\\TestXMLFiles\\testTradition.xml";
		else
			filename = "src/TestXMLFiles/testTradition.xml";
		
		try {
			importResource.parseGraphML(filename, "1");
		} catch (FileNotFoundException f) {
			// this error should not occur
			assertTrue(false);
		}
		
		TraditionModel trad1 = new TraditionModel();
		trad1.setId("1001");
		trad1.setName("Tradition");
		TraditionModel trad2 = new TraditionModel();
		trad2.setId("1002");
		trad2.setName("Tradition");
		
		List<TraditionModel> traditions = jerseyTest.resource().path("/tradition/getalltraditions")
    			.get(new GenericType<List<TraditionModel>>(){});
    	TraditionModel firstTradition = traditions.get(0);
    	assertEquals(trad1.getId(), firstTradition.getId());
    	assertEquals(trad1.getName(), firstTradition.getName());
    	
    	TraditionModel lastTradition = traditions.get(traditions.size()-1);
    	assertEquals(trad2.getId(), lastTradition.getId());
    	assertEquals(trad2.getName(), lastTradition.getName());
	}
	
	@Test
	public void getAllTraditionsWithParameterNotFoundTest()
	{
		ClientResponse resp = jerseyTest.resource().path("/tradition/getalltraditions/" + 2342)
    			.get(ClientResponse.class);
    	assertEquals(Response.status(Status.NOT_FOUND).build().getStatus(), resp.getStatus());
	}

	@Test
	public void getAllRelationshipsTest() {
		String jsonPayload = "{\"isAdmin\":0,\"id\":1}";
		jerseyTest.resource().path("/user/createuser").type(MediaType.APPLICATION_JSON)
				.post(ClientResponse.class, jsonPayload);

		RelationshipModel rel = new RelationshipModel();
		rel.setSource("16");
		rel.setTarget("27");
		rel.setId("36");
		rel.setReading_a("april");
		rel.setIs_significant("no");
		rel.setReading_b("april");
		rel.setAlters_meaning("0");
		rel.setType("transposition");
		rel.setScope("local");

		List<RelationshipModel> relationships = jerseyTest.resource()
				.path("/tradition/getallrelationships/" + tradId)
				.get(new GenericType<List<RelationshipModel>>() {
				});
		RelationshipModel relLoaded = relationships.get(2);

		assertEquals(rel.getSource(), relLoaded.getSource());
		assertEquals(rel.getTarget(), relLoaded.getTarget());
		assertEquals(rel.getId(), relLoaded.getId());
		assertEquals(rel.getReading_a(), relLoaded.getReading_a());
		assertEquals(rel.getIs_significant(), relLoaded.getIs_significant());
		assertEquals(rel.getReading_b(), relLoaded.getReading_b());
		assertEquals(rel.getAlters_meaning(), relLoaded.getAlters_meaning());
		assertEquals(rel.getType(), relLoaded.getType());
		assertEquals(rel.getScope(), relLoaded.getScope());

	}
	
	@Test
	public void getAllRelationshipsCorrectAmountTest() {

		List<RelationshipModel> relationships = jerseyTest.resource()
				.path("/tradition/getallrelationships/" + tradId)
				.get(new GenericType<List<RelationshipModel>>() {
				});

		assertEquals(3, relationships.size());

	}
	
	@Test
	public void getAllWitnessesTest() {
		
		WitnessModel witA = new WitnessModel();
		witA.setId("A");
		WitnessModel witB = new WitnessModel();
		witB.setId("B");
		WitnessModel witC = new WitnessModel();
		witC.setId("C");

		List<WitnessModel> witnesses = jerseyTest.resource()
				.path("/tradition/getallwitnesses/fromtradition/" + tradId)
				.get(new GenericType<List<WitnessModel>>() {
				});
		WitnessModel witLoaded0 = witnesses.get(0);
		WitnessModel witLoaded1 = witnesses.get(1);
		WitnessModel witLoaded2 = witnesses.get(2);


		assertEquals(witA.getId(),witLoaded0.getId());
		assertEquals(witB.getId(),witLoaded1.getId());
		assertEquals(witC.getId(),witLoaded2.getId());
		

	}
	
	@Test
	public void getAllWitnessesTraditionNotFoundTest() {

		ClientResponse resp = jerseyTest.resource()
				.path("/tradition/getallwitnesses/fromtradition/" + 10000)
				.get(ClientResponse.class);
		
		assertEquals(Response.status(Status.NOT_FOUND).build().getStatus(), resp.getStatus());
		
	}
	
	@Test
	public void getDotOfNonExistentTraditionTest()
	{
		ClientResponse resp = jerseyTest.resource().path("/tradition/getdot/fromtradition/" + 10000).type(MediaType.APPLICATION_JSON).get(ClientResponse.class);
		
		assertEquals(Response.status(Status.NOT_FOUND).build().getStatus(), resp.getStatus());
	}
	
	@Test
	public void getDotTest()
	{
		String str = jerseyTest.resource().path("/tradition/getdot/fromtradition/" + tradId).type(MediaType.APPLICATION_JSON).get(String.class);

		String[] exp = new String[60];
		exp[1]= "digraph { n4 [label=\"#START#\"];n4->n5[label=\"A,B,C\";id=\"e0\"];";
		exp[2]= "n5 [label=\"when\"];";
		exp[3]= "n5->n16[label=\"A\";id=";
		exp[4]= "n5->n24[label=\"B,C\";id=";
		exp[5]= "n16 [label=\"april\"];";
		exp[6]= "n16->n22[label=\"A\";id=";
		exp[7]= "n24 [label=\"showers\"];";
		exp[8]= "n24->n25[label=\"A,B,C\";id=";
		exp[9]= "n22 [label=\"with\"];";
		exp[10]= "n22->n23[label=\"A\";id=";
		exp[11]= "n25 [label=\"sweet\"];";
		exp[12]= "n25->n26[label=\"A,B,C\";id=";
		exp[13]= "n23 [label=\"his\"];";
		exp[14]= "n23->n24[label=\"A\";id=";
		exp[15]= "n26 [label=\"with\"];";
		exp[16]= "n26->n27[label=\"B,C\";id=";
		exp[17]= "n26->n7[label=\"A\";id=";
		exp[18]= "n27 [label=\"april\"];";
		exp[19]= "n27->n7[label=\"B,C\";id=";
		exp[20]= "n7 [label=\"fruit\"];";
		exp[21]= "n7->n9[label=\"C\";id=";
		exp[22]= "n7->n8[label=\"A,B\";id=";
		exp[23]= "n9 [label=\"teh\"];";
		exp[24]= "n9->n11[label=\"C\";id=";
		exp[25]= "n8 [label=\"the\"];";
		exp[26]= "n8->n10[label=\"B\";id=";
		exp[27]= "n8->n11[label=\"A\";id=";
		exp[28]= "n11 [label=\"drought\"];";
		exp[29]= "n11->n12[label=\"A,C\";id=";
		exp[30]= "n10 [label=\"march\"];";
		exp[31]= "n10->n12[label=\"B\";id=";
		exp[32]= "n12 [label=\"of\"];";
		exp[33]= "n12->n14[label=\"B\";id=";
		exp[34]= "n12->n13[label=\"A,C\";id=";
		exp[35]= "n14 [label=\"drought\"];";
		exp[36]= "n14->n15[label=\"B\";id=";
		exp[37]= "n13 [label=\"march\"];";
		exp[38]= "n13->n15[label=\"A,C\";id=";
		exp[39]= "n15 [label=\"has\"];";
		exp[40]= "n15->n17[label=\"A,B,C\";id=";
		exp[41]= "n17 [label=\"pierced\"];";
		exp[42]= "n17->n18[label=\"A\";id=";
		exp[43]= "n17->n19[label=\"B\";id=";
		exp[44]= "n17->n20[label=\"C\";id=";
		exp[45]= "n18 [label=\"unto\"];";
		exp[46]= "n18->n21[label=\"A\";id=";
		exp[47]= "n19 [label=\"to\"];";
		exp[48]= "n19->n21[label=\"B\";id=";
		exp[49]= "n20 [label=\"teh\"];";
		exp[50]= "n20->n28[label=\"C\";id=";
		exp[51]= "n21 [label=\"the\"];";
		exp[52]= "n21->n6[label=\"A,B\";id=";
		exp[53]= "n28 [label=\"rood\"];";
		exp[54]= "n28->n3[label=\"C\";id=";
		exp[55]= "n6 [label=\"root\"];";
		exp[56]= "n6->n3[label=\"A,B\";id=";
		exp[57]= "n3 [label=\"#END#\"];";
		exp[58]= "subgraph { edge [dir=none]n16->n27[style=dotted;label=\"transposition\";id=";
		exp[59]= "n11->n14[style=dotted;label=\"transposition\";id=";
		exp[0]= "n10->n13[style=dotted;label=\"transposition\";id=";
		
		for(int i=0; i<exp.length;i++) {
			assertTrue(str.contains(exp[i]));
		}
	} 
	
	/**
	 * Test if it is possible to change the user of a Tradition
	 */
	@Test
	public void changeMetadataOfATraditionTest(){
		
		/*
		 * Create a second user with id 42
		 */
		ExecutionEngine engine = new ExecutionEngine(db);
		try (Transaction tx = db.beginTx()) {
			ExecutionResult result = engine.execute("match (n:ROOT) return n");
			Iterator<Node> nodes = result.columnAs("n");
			Node rootNode = nodes.next();

			Node node = db.createNode(Nodes.USER);
			node.setProperty("id", "42");
			node.setProperty("isAdmin", "1");

			rootNode.createRelationshipTo(node, ERelations.NORMAL);
			tx.success();
		}

		/*
		 * The user with id 42 has no tradition
		 */
		ExecutionResult result = null;
		try (Transaction tx = db.beginTx()) {
			result = engine.execute("match (n)<-[:NORMAL]-(userId:USER {id:'42'}) return n");
			Iterator<Node> tradIterator = result.columnAs("n");
			assertTrue(!tradIterator.hasNext());

			tx.success();

		}
		
		/*
		 * The user with id 1 has tradition
		 */
		result = null;
		try (Transaction tx = db.beginTx()) {
			result = engine.execute("match (n)<-[:NORMAL]-(userId:USER {id:'1'}) return n");
			Iterator<Node> tradIterator = result.columnAs("n");
			Node tradNode = tradIterator.next();
			TraditionModel tradition = new TraditionModel();
			tradition.setId(tradNode.getProperty("id").toString());
			tradition.setName(tradNode.getProperty("name").toString());

			assertTrue(tradition.getId().equals(tradId));
			assertTrue(tradition.getName().equals("Tradition"));
			
			tx.success();
		}
		
		/*
		 * Change the owner of the tradition 
		 */
		TraditionMetadataModel textInfo = new TraditionMetadataModel();
		textInfo.setName("RenamedTraditionName");
		textInfo.setLanguage("nital");
		textInfo.setIsPublic("0");
		textInfo.setOwnerId("42");
		
		ClientResponse ownerChangeResponse = jerseyTest.resource().path("/tradition/changemetadata/fromtradition/"+tradId).type(MediaType.APPLICATION_JSON).post(ClientResponse.class,textInfo);
		assertEquals(Response.Status.OK.getStatusCode(), ownerChangeResponse.getStatus());
		
		/*
		 * Test if user with id 42 has now the tradition
		 */
		result = null;
		try (Transaction tx = db.beginTx()) {
			result = engine.execute("match (n)<-[:NORMAL]-(userId:USER {id:'42'}) return n");
			Iterator<Node> tradIterator = result.columnAs("n");
			Node tradNode = tradIterator.next();
			TraditionModel tradition = new TraditionModel();
			tradition.setId(tradNode.getProperty("id").toString());
			tradition.setName(tradNode.getProperty("name").toString());

			assertTrue(tradition.getId().equals(tradId));
			assertTrue(tradition.getName().equals("RenamedTraditionName"));

			tx.success();

		}
		
		/*
		 * The user with id 1 has no tradition
		 */
		result = null;
		try (Transaction tx = db.beginTx()) {
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
	public void changeMetadataOfATraditionTestWithWrongUser(){
		ExecutionEngine engine = new ExecutionEngine(db);
		/* Preconditon
		 * The user with id 1 has tradition
		 */
		ExecutionResult result = null;
		try (Transaction tx = db.beginTx()) {
			result = engine.execute("match (n)<-[:NORMAL]-(userId:USER {id:'1'}) return n");
			Iterator<Node> tradIterator = result.columnAs("n");
			Node tradNode = tradIterator.next();
			TraditionModel tradition = new TraditionModel();
			tradition.setId(tradNode.getProperty("id").toString());
			tradition.setName(tradNode.getProperty("name").toString());

			assertTrue(tradition.getId().equals(tradId));
			assertTrue(tradition.getName().equals("Tradition"));
			
			tx.success();
		}
		
		/*
		 * Change the owner of the tradition 
		 */
		TraditionMetadataModel textInfo = new TraditionMetadataModel();
		textInfo.setName("RenamedTraditionName");
		textInfo.setLanguage("nital");
		textInfo.setIsPublic("0");
		textInfo.setOwnerId("1337");
		
		ClientResponse removalResponse = jerseyTest.resource().path("/tradition/changemetadata/fromtradition/"+tradId).type(MediaType.APPLICATION_JSON).post(ClientResponse.class,textInfo);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), removalResponse.getStatus());
		assertEquals(removalResponse.getEntity(String.class), "Error: A user with this id does not exist");
	
		/* PostCondition
		 * The user with id 1 has still tradition
		 */
		TraditionModel tradition = new TraditionModel();
		result = null;
		try (Transaction tx = db.beginTx()) {
			result = engine.execute("match (n)<-[:NORMAL]-(userId:USER {id:'1'}) return n");
			Iterator<Node> tradIterator = result.columnAs("n");
			Node tradNode = tradIterator.next();

			tradition.setId(tradNode.getProperty("id").toString());
			tradition.setName(tradNode.getProperty("name").toString());


			tx.success();
		}
		
		assertTrue(tradition.getId().equals(tradId));
		assertTrue(tradition.getName().equals("Tradition"));
		
	}
	
	/**
	 * Test if it is posibible to change the user of a Tradition with invalid traditionid
	 */
	@Test
	public void changeMetadataOfATraditionTestWithInvalidTradid(){
		
		/*
		 * Create a second user with id 42
		 */
		ExecutionEngine engine = new ExecutionEngine(db);
		try (Transaction tx = db.beginTx()) {
			ExecutionResult result = engine.execute("match (n:ROOT) return n");
			Iterator<Node> nodes = result.columnAs("n");
			Node rootNode = nodes.next();

			Node node = db.createNode(Nodes.USER);
			node.setProperty("id", "42");
			node.setProperty("isAdmin", "1");

			rootNode.createRelationshipTo(node, ERelations.NORMAL);
			tx.success();
		}

		/*
		 * The user with id 42 has no tradition
		 */
		ExecutionResult result = null;
		try (Transaction tx = db.beginTx()) {
			result = engine.execute("match (n)<-[:NORMAL]-(userId:USER {id:'42'}) return n");
			Iterator<Node> tradIterator = result.columnAs("n");
			assertTrue(!tradIterator.hasNext());

			tx.success();

		}
		
		/*
		 * The user with id 1 has tradition
		 */
		result = null;
		try (Transaction tx = db.beginTx()) {
			result = engine.execute("match (n)<-[:NORMAL]-(userId:USER {id:'1'}) return n");
			Iterator<Node> tradIterator = result.columnAs("n");
			Node tradNode = tradIterator.next();
			TraditionModel tradition = new TraditionModel();
			tradition.setId(tradNode.getProperty("id").toString());
			tradition.setName(tradNode.getProperty("name").toString());

			assertTrue(tradition.getId().equals(tradId));
			assertTrue(tradition.getName().equals("Tradition"));
			
			tx.success();
		}
		
		/*
		 * Change the owner of the tradition 
		 */
		TraditionMetadataModel textInfo = new TraditionMetadataModel();
		textInfo.setName("RenamedTraditionName");
		textInfo.setLanguage("nital");
		textInfo.setIsPublic("0");
		textInfo.setOwnerId("42");
		
		ClientResponse removalResponse = jerseyTest.resource().path("/tradition/changemetadata/fromtradition/1337").type(MediaType.APPLICATION_JSON).post(ClientResponse.class,textInfo);
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
		try (Transaction tx = db.beginTx()) {
			result = engine.execute("match (n)<-[:NORMAL]-(userId:USER {id:'1'}) return n");
			Iterator<Node> tradIterator = result.columnAs("n");
			Node tradNode = tradIterator.next();
			TraditionModel tradition = new TraditionModel();
			tradition.setId(tradNode.getProperty("id").toString());
			tradition.setName(tradNode.getProperty("name").toString());

			assertTrue(tradition.getId().equals(tradId));
			assertTrue(tradition.getName().equals("Tradition"));

			tx.success();

		}
		
		/*
		 * The user with id 42 has still no tradition
		 */
		result = null;
		try (Transaction tx = db.beginTx()) {
			result = engine.execute("match (n)<-[:NORMAL]-(userId:USER {id:'42'}) return n");
			Iterator<Node> tradIterator = result.columnAs("n");
			assertTrue(!tradIterator.hasNext());

			tx.success();

		}
	}
	
	/**
	 * Remove a complete Tradition
	 */
	@Test
	public void deleteTraditionByIdTest(){
		ClientResponse removalResponse = jerseyTest.resource().path("/tradition/deletetradition/withid/"+tradId).type(MediaType.APPLICATION_JSON).delete(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(), removalResponse.getStatus());
		

		Node startNode = null;
		try (Transaction tx = db.beginTx()) {
			startNode = DatabaseService.getStartNode(tradId, db);
			
		}
		
		assertTrue(startNode == null);
	}
	
	/**
	 * Test do delete a Tradition with an invalid id deletTraditionById
	 */
	@Test
	public void deleteATraditionWithInvalidIdTest(){
		ExecutionEngine engine = new ExecutionEngine(db);
		/*
		 * Try to remove a tradition with invalid id
		 */
		ClientResponse removalResponse = jerseyTest.resource().path("/tradition/deletetradition/withid/1337").delete(ClientResponse.class);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), removalResponse.getStatus());
		
		/*
		 * Test if user 1 still exists
		 */
		ExecutionResult result = engine.execute("match (userId:USER {id:'1'}) return userId");
		Iterator<Node> nodes = result.columnAs("userId");
		assertTrue(nodes.hasNext());
		
    	/*
    	 * Check if tradition {tradId} still exists
    	 */
		result = engine.execute("match (tradId:TRADITION {id:'"+tradId+"'}) return tradId");
		nodes = result.columnAs("tradId");
		assertTrue(nodes.hasNext());
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
