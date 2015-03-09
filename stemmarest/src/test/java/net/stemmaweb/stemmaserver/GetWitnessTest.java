package net.stemmaweb.stemmaserver;

import static org.junit.Assert.*;
import net.stemmaweb.rest.Witness;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

import org.neo4j.cypher.javacompat.ExecutionEngine;

public class GetWitnessTest {

	Witness wintess;
	GraphDatabaseService graphDb;

	@Before
	public void prepareTestDatabase() {
		graphDb = new TestGraphDatabaseFactory().newImpermanentDatabase();
		ExecutionEngine engine = new ExecutionEngine(graphDb);
		String createWitness = "create (testUser:USER {id:'testUserId'}),"
				+ " (testTradition:TRADITION {name:'testTraditionName'}),"
				+ " (witnessStart:WORD {name:'testTraditionName__STRAT__'}),"
				+ " (word1:WORD {text:'this'})," + " (word2:WORD {text:'is'}),"
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
			engine.execute(createWitness);
			tx.success();
		}
	}

	@Test
	public void testWintessAsString() {

		wintess = new Witness();
		assertEquals("this is a witness test", wintess.getWitnssAsPlainText(
				"testUserId", "testTraditionName", "testLexime"));
	}

	@After
	public void destroyTestDatabase() {
		graphDb.shutdown();
		// destroy the test database
	}
}