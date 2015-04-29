package net.stemmaweb.stemmaserver.unittests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;

import javax.ws.rs.core.Response;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.services.Neo4JToDotParser;
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


@RunWith(MockitoJUnitRunner.class)
public class DotExporterUnitTest {
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
	private Neo4JToDotParser parser;

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
	}
	
	@Test
	public void testDotExportNotFoundException()
	{		
			Response response = parser.parseNeo4J("1002");
			assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
					response.getStatus());			
	}

	@Test
	public void testDotExport()
	{
		String filename = "upload/" + "output.dot";
		
		parser.parseNeo4J(tradId);
		String everything = "";
		try(BufferedReader br = new BufferedReader(new FileReader(filename))) {
	        StringBuilder sb = new StringBuilder();
	        String line = br.readLine();

	        while (line != null) {
	            sb.append(line);
	            sb.append(System.lineSeparator());
	            line = br.readLine();
	        }
	        everything = sb.toString();
	    } catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
		assertEquals(expected, everything);
	}
	
	
	@After
	public void destroyTestDatabase()
	{
	    mockDbService.shutdown();
	    // destroy the test database
	}
}