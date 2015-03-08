package net.stemmaweb.stemmaserver;

import static org.junit.Assert.*;
import net.stemmaweb.rest.Witness;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.cypher.ExecutionEngine;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.impl.util.StringLogger;
import org.neo4j.test.TestGraphDatabaseFactory;

public class GetWitnessTest {

	Witness wintess;
	GraphDatabaseService graphDb;

	@Before
	public void prepareTestDatabase() {
		wintess = new Witness();
		graphDb = new TestGraphDatabaseFactory().newImpermanentDatabase();
		ExecutionEngine engine = new ExecutionEngine(graphDb,
				StringLogger.SYSTEM);
		String createWitness = "create (testWintess:Wintess {leximes:'1'}),"
				+ " (word1:Word {content:'this'}),"
				+ " (word2:Word {content:'is'}),"
				+ " (word3:Word {content:'a'}),"
				+ " (word4:Word {content:'witness'}),"
				+ " (word5:Word {content:'test'}),"
				+ " (testWintess)-[:NORMAL]->(word1),"
				+ " (word1)-[:NORMAL]->(word2),"
				+ " (word2)-[:NORMAL]->(word3),"
				+ " (word3)-[:NORMAL]->(word4),"
				+ " (word4)-[:NORMAL]->(word5);";
		try (Transaction tx = graphDb.beginTx()) {
			engine.execute(createWitness);
			tx.success();
		}
	}

	@Test
	public void testWintessAsString() {

		try (Transaction tx = graphDb.beginTx()) {
			assertEquals("this is a witness test",
					wintess.getWitnssAsPlainText("1"));
		}
	}

	@After
	public void destroyTestDatabase() {
		graphDb.shutdown();
		// destroy the test database
	}
}