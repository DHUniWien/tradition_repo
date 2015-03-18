package net.stemmaweb.stemmaserver;

import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.util.Iterator;

import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Relations;
import net.stemmaweb.rest.Witness;
import net.stemmaweb.services.DbPathProblemService;
import net.stemmaweb.services.GraphMLToNeo4JParser;

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

@RunWith(MockitoJUnitRunner.class)
public class ReadingUnitTest {

	@Mock
	protected GraphDatabaseFactory mockDbFactory = new GraphDatabaseFactory();

	@Spy
	protected GraphDatabaseService mockDbService = new TestGraphDatabaseFactory()
			.newImpermanentDatabase();

	@InjectMocks
	private GraphMLToNeo4JParser importResource;

	@InjectMocks
	private Witness witness;
	
	@InjectMocks
	private DbPathProblemService problemService;

	@Before
	public void setUp() throws Exception {
		String filename = "src\\TestXMLFiles\\Sapientia.xml";
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

		Mockito.when(mockDbFactory.newEmbeddedDatabase(Matchers.anyString()))
				.thenReturn(mockDbService);

		Mockito.doNothing().when(mockDbService).shutdown();

		try {
			importResource.parseGraphML(filename, "1");
		} catch (FileNotFoundException f) {
			// this error should not occur
			assertTrue(false);
		}
	}

	/**
	 * Import a correct file
	 */
	@Test
	public void getNextReadingTest() {
		// not correct yet!
		assertEquals("next",
				witness.getNextReadingInWitness("1000", "r1008", "readId"));

		traditionNodeExistsTest();
		traditionEndNodeExistsTest();
	}

	/**
	 * test if the tradition node exists
	 */
	public void traditionNodeExistsTest() {
		try (Transaction tx = mockDbService.beginTx()) {
			ResourceIterable<Node> tradNodes = mockDbService
					.findNodesByLabelAndProperty(Nodes.TRADITION, "name",
							"Sapientia");
			Iterator<Node> tradNodesIt = tradNodes.iterator();
			assertTrue(tradNodesIt.hasNext());
			tx.success();
		}
	}

	/**
	 * test if the tradition end node exists
	 */
	public void traditionEndNodeExistsTest() {
		ExecutionEngine engine = new ExecutionEngine(mockDbService);

		ExecutionResult result = engine
				.execute("match (e)-[:NORMAL]->(n:WORD) where n.text='#END#' return n");
		ResourceIterator<Node> tradNodes = result.columnAs("n");
		assertTrue(tradNodes.hasNext());
	}
}
