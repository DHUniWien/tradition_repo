package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Stemma;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.OSDetector;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;

/**
 * 
 * Contains all tests for the api calls related to stemmas.
 * 
 * @author PSE FS 2015 Team2
 *
 */
public class StemmaTest {
	private String tradId;

	GraphDatabaseService db;

	/*
	 * JerseyTest is the test environment to Test api calls it provides a
	 * grizzly http service
	 */
	private JerseyTest jerseyTest;


	@Before
	public void setUp() throws Exception {

		GraphDatabaseServiceProvider.setImpermanentDatabase();
		
		db = new GraphDatabaseServiceProvider().getDatabase();

		Stemma stemma = new Stemma();
		GraphMLToNeo4JParser importResource = new GraphMLToNeo4JParser();
		
		String filename;
		if (OSDetector.isWin())
			filename = "src\\TestXMLFiles\\testTradition.xml";
		else
			filename = "src/TestXMLFiles/testTradition.xml";

		/*
		 * Populate the test database with the root node and a user with id 1
		 */
		try (Transaction tx = db.beginTx()) {
			Result result = db.execute("match (n:ROOT) return n");
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
			importResource.parseGraphML(filename, "1", "Tradition");
		} catch (FileNotFoundException f) {
			// this error should not occur
			assertTrue(false);
		}
		/**
		 * gets the generated id of the inserted tradition
		 */
		try (Transaction tx = db.beginTx()) {
			Result result = db.execute("match (u:USER)--(t:TRADITION) return t");
			Iterator<Node> nodes = result.columnAs("t");
			assertTrue(nodes.hasNext());
			tradId = (String) nodes.next().getProperty("id");

			tx.success();
		}

		/*
		 * Create a JersyTestServer serving the Resource under test
		 */
		jerseyTest = JerseyTestServerFactory.newJerseyTestServer().addResource(stemma).create();
		jerseyTest.setUp();
	}
	
	@Test
	public void getAllStemmataTest()
	{
		List<String> stemmata = jerseyTest.resource().path("/stemma/getallstemmata/fromtradition/" + tradId)
				.get(new GenericType<List<String>>() {});
		assertEquals(2,stemmata.size());
		
		String expected = "digraph \"stemma\" {  0 [ class=hypothetical ];  "
				+ "A [ class=hypothetical ];  B [ class=hypothetical ];  "
				+ "C [ class=hypothetical ]; 0 -> A;  0 -> B;  A -> C; }";
		assertEquals(expected, stemmata.get(0));
		
		String expected2 = "graph \"Semstem 1402333041_0\" {  0 [ class=hypothetical ];  "
				+ "A [ class=hypothetical ];  B [ class=hypothetical ];  "
				+ "C [ class=hypothetical ]; 0 -- A;  A -- B;  B -- C; }";
		assertEquals(expected2, stemmata.get(1));
	}
	
	@Test
	public void getAllStemmataNotFoundErrorTest()
	{
		ClientResponse getStemmaResponse = jerseyTest.resource().path("/stemma/getallstemmata/fromtradition/" + 10000).type(MediaType.APPLICATION_JSON).get(ClientResponse.class);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), getStemmaResponse.getStatus());
	}
	
	@Test
	public void getAllStemmataStatusTest()
	{
		ClientResponse resp = jerseyTest.resource().path("/stemma/getallstemmata/fromtradition/" + tradId).type(MediaType.APPLICATION_JSON).get(ClientResponse.class);

		Response expectedResponse = Response.ok().build();
		assertEquals(expectedResponse.getStatus(), resp.getStatus());
	}
	
	@Test
	public void getStemmaTest()
	{
		String stemmaTitle = "stemma";
		String str = jerseyTest.resource().path("/stemma/getstemma/fromtradition/" + tradId + "/withtitle/"+ stemmaTitle).type(MediaType.APPLICATION_JSON).get(String.class);

		String expected = "digraph \"stemma\" {  0 [ class=hypothetical ];  "
				+ "A [ class=hypothetical ];  B [ class=hypothetical ];  "
				+ "C [ class=hypothetical ]; 0 -> A;  0 -> B;  A -> C; }";
		assertEquals(expected, str);
		
		String stemmaTitle2 = "Semstem 1402333041_0";
		String str2 = jerseyTest.resource().path("/stemma/getstemma/fromtradition/" + tradId + "/withtitle/"+ stemmaTitle2).type(MediaType.APPLICATION_JSON).get(String.class);
		
		String expected2 = "graph \"Semstem 1402333041_0\" {  0 [ class=hypothetical ];  "
				+ "A [ class=hypothetical ];  B [ class=hypothetical ];  "
				+ "C [ class=hypothetical ]; 0 -- A;  A -- B;  B -- C; }";
		assertEquals(expected2, str2);
		
		ClientResponse getStemmaResponse = jerseyTest.resource().path("/stemma/getstemma/fromtradition/" + tradId + "/withtitle/gugus").type(MediaType.APPLICATION_JSON).get(ClientResponse.class);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), getStemmaResponse.getStatus());

	}
	
	@Test
	public void setStemmaTest(){
		
		try (Transaction tx = db.beginTx()) {
			Result result = db.execute("match (t:TRADITION {id:'"+ tradId +"'})--(s:STEMMA) return count(s) AS res");
			assertEquals(2L,result.columnAs("res").next());
		
			tx.success();
		}
		
		String input="graph \"Semstem 1402333041_1\" {  0 [ class=hypothetical ];  "
				+ "A [ class=hypothetical ];  B [ class=hypothetical ];  "
				+ "C [ class=hypothetical ]; 0 -- A;  A -- B;  B -- C; }";
		
		ClientResponse actualStemmaResponse = jerseyTest.resource().path("/stemma/newstemma/intradition/"+tradId).type(MediaType.APPLICATION_JSON).post(ClientResponse.class,input);
		assertEquals(Response.ok().build().getStatus(), actualStemmaResponse.getStatus());
		
		try (Transaction tx = db.beginTx()) {
			Result result2 = db.execute("match (t:TRADITION {id:'"+ tradId +"'})--(s:STEMMA) return count(s) AS res2");
			assertEquals(3L,result2.columnAs("res2").next());
		
			tx.success();
		}
		
		String stemmaTitle = "Semstem 1402333041_1";
		String str = jerseyTest.resource().path("/stemma/getstemma/fromtradition/" + tradId + "/withtitle/"+ stemmaTitle).type(MediaType.APPLICATION_JSON).get(String.class);
		
		assertEquals(input, str);

	}
	
	@Test
	public void setStemmaNotFoundTest(){
		
		String emptyInput="";
		
		ClientResponse actualStemmaResponse = jerseyTest.resource().path("/stemma/newstemma/intradition/"+tradId).type(MediaType.APPLICATION_JSON).post(ClientResponse.class,emptyInput);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualStemmaResponse.getStatus());
	}
	
	@Test
	public void reorientGraphStemmaTest()
	{
		 String stemmaTitle = "Semstem 1402333041_0";
		 String newNodeId = "C";
		 String secondNodeId = "0";


		try (Transaction tx = db.beginTx()) {
			Result result1 = db.execute("match (t:TRADITION {id:'"+
					tradId + "'})-[:STEMMA]->(n:STEMMA { name:'" + 
					stemmaTitle +"'}) return n");
    		Iterator<Node> stNodes = result1.columnAs("n");
    		assertTrue(stNodes.hasNext());
			Node startNodeStemma = stNodes.next();
			
			Iterable<Relationship> rel1 = startNodeStemma.getRelationships(Direction.OUTGOING,ERelations.STEMMA);
			assertTrue(rel1.iterator().hasNext());
			assertEquals("0",rel1.iterator().next().getEndNode().getProperty("id").toString());
			
			ClientResponse actualStemmaResponse = jerseyTest.resource().path("/stemma/reorientstemma/fromtradition/"+tradId+"/withtitle/"+stemmaTitle+"/withnewrootnode/"+ newNodeId).type(MediaType.APPLICATION_JSON).post(ClientResponse.class);
			assertEquals(Response.ok().build().getStatus(), actualStemmaResponse.getStatus());
			
			Iterable<Relationship> rel2 = startNodeStemma.getRelationships(Direction.OUTGOING,ERelations.STEMMA);
			assertTrue(rel2.iterator().hasNext());
			assertEquals(newNodeId,rel2.iterator().next().getEndNode().getProperty("id").toString());
			
			ClientResponse actualStemmaResponseSecond = jerseyTest.resource().path("/stemma/reorientstemma/fromtradition/"+tradId+"/withtitle/"+stemmaTitle+"/withnewrootnode/"+ secondNodeId).type(MediaType.APPLICATION_JSON).post(ClientResponse.class);
			assertEquals(Response.ok().build().getStatus(), actualStemmaResponseSecond.getStatus());

			tx.success();
		}

	}
	
	@Test
	public void reorientGraphStemmaNoNodesTest()
	{
		
		 String stemmaTitle = "Semstem 1402333041_0";
		 String falseNode = "X";
		 String rightNode = "C";
		 String falseTitle = "X";


		try (Transaction tx = db.beginTx()) {
			
			ClientResponse actualStemmaResponse = jerseyTest.resource().path("/stemma/reorientstemma/fromtradition/"+tradId+"/withtitle/"+stemmaTitle+"/withnewrootnode/"+ falseNode).type(MediaType.APPLICATION_JSON).post(ClientResponse.class);
			assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualStemmaResponse.getStatus());
		
			ClientResponse actualStemmaResponse2 = jerseyTest.resource().path("/stemma/reorientstemma/fromtradition/"+tradId+"/withtitle/"+falseTitle+"/withnewrootnode/"+ rightNode).type(MediaType.APPLICATION_JSON).post(ClientResponse.class);
			assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualStemmaResponse2.getStatus());
			
			tx.success();
		}

	}
	
	@Test
	public void reorientDigraphStemmaTest()
	{
		 String stemmaTitle = "stemma";
		 String newNodeId = "C";

		try (Transaction tx = db.beginTx()) {
			Result result1 = db.execute("match (t:TRADITION {id:'"+
					tradId + "'})-[:STEMMA]->(n:STEMMA { name:'" + 
					stemmaTitle +"'}) return n");
    		Iterator<Node> stNodes = result1.columnAs("n");
    		assertTrue(stNodes.hasNext());
			Node startNodeStemma = stNodes.next();
			
			Iterable<Relationship> relBevor = startNodeStemma.getRelationships(Direction.OUTGOING,ERelations.STEMMA);
			assertTrue(relBevor.iterator().hasNext());
			assertEquals("0",relBevor.iterator().next().getEndNode().getProperty("id").toString());
			
			ClientResponse actualStemmaResponse = jerseyTest.resource().path("/stemma/reorientstemma/fromtradition/"+tradId+"/withtitle/"+stemmaTitle+"/withnewrootnode/"+ newNodeId).type(MediaType.APPLICATION_JSON).post(ClientResponse.class);
			assertEquals(Response.ok().build().getStatus(), actualStemmaResponse.getStatus());
			
			Iterable<Relationship> relAfter = startNodeStemma.getRelationships(Direction.OUTGOING,ERelations.STEMMA);
			assertTrue(relAfter.iterator().hasNext());
			assertEquals(newNodeId,relAfter.iterator().next().getEndNode().getProperty("id").toString());
		
			tx.success();
		}

	}
	
	@Test
	public void reorientDigraphStemmaNoNodesTest()
	{
		
		 String stemmaTitle = "stemma";
		 String falseNode = "X";
		 String rightNode = "C";
		 String falseTitle = "X";


		try (Transaction tx = db.beginTx()) {
			
			ClientResponse actualStemmaResponse = jerseyTest.resource().path("/stemma/reorientstemma/fromtradition/"+tradId+"/withtitle/"+stemmaTitle+"/withnewrootnode/"+ falseNode).type(MediaType.APPLICATION_JSON).post(ClientResponse.class);
			assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualStemmaResponse.getStatus());
		
			ClientResponse actualStemmaResponse2 = jerseyTest.resource().path("/stemma/reorientstemma/fromtradition/"+tradId+"/withtitle/"+falseTitle+"/withnewrootnode/"+ rightNode).type(MediaType.APPLICATION_JSON).post(ClientResponse.class);
			assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualStemmaResponse2.getStatus());
			
			tx.success();
		}

	}
	
	@Test
	public void reorientDigraphStemmaSameNodeAsBeforeTest()
	{
		
		 String stemmaTitle = "stemma";
		 String newNode = "C";
		
		try (Transaction tx = db.beginTx()) {
			
			ClientResponse actualStemmaResponse = jerseyTest.resource().path("/stemma/reorientstemma/fromtradition/"+tradId+"/withtitle/"+stemmaTitle+"/withnewrootnode/"+ newNode).type(MediaType.APPLICATION_JSON).post(ClientResponse.class);
			assertEquals(Response.ok().build().getStatus(), actualStemmaResponse.getStatus());
		
			ClientResponse actualStemmaResponse2 = jerseyTest.resource().path("/stemma/reorientstemma/fromtradition/"+tradId+"/withtitle/"+stemmaTitle+"/withnewrootnode/"+ newNode).type(MediaType.APPLICATION_JSON).post(ClientResponse.class);
			assertEquals(Response.ok().build().getStatus(), actualStemmaResponse2.getStatus());
			
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
		db.shutdown();
		jerseyTest.tearDown();
	}

}