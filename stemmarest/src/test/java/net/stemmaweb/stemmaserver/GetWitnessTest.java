package net.stemmaweb.stemmaserver;

import static org.junit.Assert.*;

import java.util.Iterator;

import net.stemmaweb.rest.Witness;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.test.TestGraphDatabaseFactory;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;

public class GetWitnessTest {

	private Witness wintess;
	private GraphDatabaseService graphDb;

	@Before
	public void prepareTestDatabase() {
		graphDb = new TestGraphDatabaseFactory().newImpermanentDatabase();
		ExecutionEngine beforeEngine = new ExecutionEngine(graphDb);
		String createWitness = "create (testUser:USER {id:'testUserId'}),"
				+ " (testTradition:TRADITION {name:'testTraditionName'}),"
				+ " (witnessStart:WORD {name:'testTraditionName__STRAT__'}),"
				+ " (word1:WORD {text:'this'}),"
				+ " (word2:WORD {text:'is'}),"
				+ " (word3:WORD {text:'a'}),"
				+ " (word4:WORD {text:'witness'}),"
				+ " (word5:WORD {text:'test'}),"
				+ " (testUser)-[:TEST_REALTIONSHIP]->(testTradition),"
				+ " (testTradition)-[:TEST_REALTIONSHIP]->(witnessStart),"
				+ " (witnessStart)-[:TEST_REALTIONSHIP {leximes:'testLexime'}]->(word1),"
				+ " (word1)-[:TEST_REALTIONSHIP {leximes:'testLexime'}]->(word2),"
				+ " (word2)-[:TEST_REALTIONSHIP {leximes:'testLexime'}]->(word3),"
				+ " (word3)-[:TEST_REALTIONSHIP {leximes:'testLexime'}]->(word4),"
				+ " (word4)-[:TEST_REALTIONSHIP {leximes:'testLexime'}]->(word5);";
		try (Transaction tx = graphDb.beginTx()) {
			beforeEngine.execute(createWitness);
			tx.success();
		}
	}

	@Test
	public void testNodeCreation() {

		ExecutionEngine engine = new ExecutionEngine(graphDb);
		String nodeCreationQuary = "match (n:WORD {text:'this'}) return n";

		try (Transaction tx = graphDb.beginTx()) {
			ExecutionResult result = engine.execute(nodeCreationQuary);
			Iterator<Node> nodes = result.columnAs("n");
			String textProperty = (String) nodes.next().getProperty("text");
			assertEquals("this", textProperty);
		}
	}

	@Test
	public void testWintessAsString() {

		wintess = new Witness();
		wintess.setDb(graphDb);
		
		assertEquals("this is a witness test", wintess.getWitnssAsPlainText(
				"testUserId", "testTraditionName", "testLexime"));
	}

	@After
	public void destroyTestDatabase() {
		graphDb.shutdown();
		// destroy the test database
	}
}