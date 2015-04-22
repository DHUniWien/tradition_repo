package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.util.Iterator;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Stemma;
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
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.test.TestGraphDatabaseFactory;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.test.framework.JerseyTest;

/**
 * 
 * @author Ramona
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class StemmaTest {
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
	private Stemma stemma;

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
		jerseyTest = JerseyTestServerFactory.newJerseyTestServer().addResource(stemma).create();
		jerseyTest.setUp();
	}
	
	@Test
	public void getStemmaTest()
	{
		String stemmaTitle = "stemma";
		String str = jerseyTest.resource().path("/stemma/" + tradId + "/"+ stemmaTitle).type(MediaType.APPLICATION_JSON).get(String.class);

		String expected = "digraph \"stemma\" {  0 [ class=hypothetical ];  "
				+ "A [ class=hypothetical ];  B [ class=hypothetical ];  "
				+ "C [ class=hypothetical ]; 0 -> A;  0 -> B;  A -> C; }";
		assertEquals(expected, str);
		
		String stemmaTitle2 = "Semstem 1402333041_0";
		String str2 = jerseyTest.resource().path("/stemma/" + tradId + "/"+ stemmaTitle2).type(MediaType.APPLICATION_JSON).get(String.class);
		
		String expected2 = "graph \"Semstem 1402333041_0\" {  0 [ class=hypothetical ];  "
				+ "A [ class=hypothetical ];  B [ class=hypothetical ];  "
				+ "C [ class=hypothetical ]; 0 -- A;  A -- B;  B -- C; }";
		assertEquals(expected2, str2);
		
		ClientResponse getStemmaResponse = jerseyTest.resource().path("/stemma/" + tradId + "/gugus").type(MediaType.APPLICATION_JSON).get(ClientResponse.class);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), getStemmaResponse.getStatus());

	}
	
	@Test
	public void setStemmaTest(){
		
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		try (Transaction tx = mockDbService.beginTx()) {
			ExecutionResult result = engine.execute("match (t:TRADITION {id:'"+ tradId +"'})--(s:STEMMA) return count(s) AS res");
			assertEquals(2L,result.columnAs("res").next());
		
			tx.success();
		}
		
		String input="graph \"Semstem 1402333041_1\" {  0 [ class=hypothetical ];  "
				+ "A [ class=hypothetical ];  B [ class=hypothetical ];  "
				+ "C [ class=hypothetical ]; 0 -- A;  A -- B;  B -- C; }";
		
		ClientResponse actualStemmaResponse = jerseyTest.resource().path("/stemma/"+tradId).type(MediaType.APPLICATION_JSON).post(ClientResponse.class,input);
		assertEquals(Response.ok().build().getStatus(), actualStemmaResponse.getStatus());
		
		try (Transaction tx = mockDbService.beginTx()) {
			ExecutionResult result2 = engine.execute("match (t:TRADITION {id:'"+ tradId +"'})--(s:STEMMA) return count(s) AS res2");
			assertEquals(3L,result2.columnAs("res2").next());
		
			tx.success();
		}
		
		String stemmaTitle = "Semstem 1402333041_1";
		String str = jerseyTest.resource().path("/stemma/" + tradId + "/"+ stemmaTitle).type(MediaType.APPLICATION_JSON).get(String.class);
		
		assertEquals(input, str);

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