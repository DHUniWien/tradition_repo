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
				+ " (witnessStart:WORD {name:'testTraditionName__START__', text:''}),"
				+ " (word1:WORD {text:'this', rank:'1'}),"
				+ " (word2:WORD {text:'is', rank:'2'}),"
				+ " (word3:WORD {text:'a', rank:'3'}),"
				+ " (word4:WORD {text:'witness', rank:'4'}),"
				+ " (word5:WORD {text:'test', rank:'5'}),"
				+ " (testUser)-[:NORMAL]->(testTradition),"
				+ " (testTradition)-[:NORMAL]->(witnessStart),"
				+ " (witnessStart)-[:NORMAL {lexemes:'testLexime'}]->(word1),"
				+ " (word1)-[:NORMAL {lexemes:'testLexime'}]->(word2),"
				+ " (word2)-[:NORMAL {lexemes:'testLexime'}]->(word3),"
				+ " (word3)-[:NORMAL {lexemes:'testLexime'}]->(word4),"
				+ " (word4)-[:NORMAL {lexemes:'testLexime'}]->(word5);";
		try (Transaction tx = graphDb.beginTx()) {
			beforeEngine.execute(createWitness);
			tx.success();
		}
	}

	@Test
	public void testNodeCreation() {

		ExecutionEngine engine = new ExecutionEngine(graphDb);
		String nodeCreationQuarry = "match (n:WORD {text:'this'}) return n";

		try (Transaction tx = graphDb.beginTx()) {
			ExecutionResult result = engine.execute(nodeCreationQuarry);
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

	@Test
	public void testWintessAsStringWithRanks() {

		wintess = new Witness();
		wintess.setDb(graphDb);

		assertEquals("is a witness", wintess.getWitnssAsPlainText("testUserId",
				"testTraditionName", "testLexime", "2", "4"));
	}

	@After
	public void destroyTestDatabase() {
		graphDb.shutdown();
		// destroy the test database
	}
}