package net.stemmaweb.stemmaserver.unittests;

import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.util.Iterator;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.DotToNeo4JParser;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.stemmaserver.OSDetector;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

@RunWith(MockitoJUnitRunner.class)
public class DotImporterUnitTest {
	private String tradId;

	GraphDatabaseService db;
	

	private GraphMLToNeo4JParser importResource;
	private DotToNeo4JParser parser;
	
	@Before
	public void setUp() throws Exception {
		
		
		GraphDatabaseServiceProvider.setImpermanentDatabase();
		
		db = new GraphDatabaseServiceProvider().getDatabase();
		
		importResource = new GraphMLToNeo4JParser();
		parser = new DotToNeo4JParser(db);

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
	}

	@Test
	public void testDotImport()
	{
		String dot = "digraph \"stemma\" {  O [ class=hypothetical ];  a [ class=hypothetical ]; "
				+ " b [ class=hypothetical ];  c [ class=hypothetical ];  d [ class=hypothetical ];"
				+ "  e [ class=hypothetical ];  f [ class=hypothetical ];  g [ class=hypothetical ];"
				+ "  h [ class=hypothetical ];  An74 [ class=extant ];  Au318 [ class=extant ];"
				+ "  Ba96 [ class=extant ];  Er16 [ class=extant ];  Go325 [ class=extant ];"
				+ "  Gr314 [ class=extant ];  Kf133 [ class=extant ];  Krems185 [ class=extant ];"
				+ "  Krems299 [ class=extant ];  Mu11475 [ class=extant ];  Mu22405 [ class=extant ];"
				+ "  Mu28315 [ class=extant ];  MuU151 [ class=extant ];  Sg524 [ class=extant ];"
				+ "  Wi3181 [ class=extant ];  Krems185 -> b;  Krems299 -> Mu22405;  "
				+ "O -> a;  O -> b;  a -> Au318;  a -> Go325;  a -> Gr314;  a -> f;  a -> g;"
				+ "  b -> Au318;  b -> Ba96;  b -> Go325;  b -> Gr314;  b -> Sg524;  b -> c;"
				+ "  c -> An74;  c -> MuU151;  c -> d;  d -> Mu11475;  d -> e;  e -> Er16;"
				+ "  e -> Mu28315;  f -> Krems299;  f -> h;  g -> Mu22405;  g -> Wi3181;"
				+ "  h -> Kf133;  h -> Krems185;}";
		
		parser.parseDot(dot,tradId);
		
		dot = "graph \"Semstem 1402333041_0\" {  0 [ class=hypothetical ];  1 [ class=hypothetical ];"
				+ "  10 [ class=hypothetical ];  2 [ class=hypothetical ];  3 [ class=hypothetical ];"
				+ "  4 [ class=hypothetical ];  5 [ class=hypothetical ];  6 [ class=hypothetical ];"
				+ "  7 [ class=hypothetical ];  8 [ class=hypothetical ];  9 [ class=hypothetical ];"
				+ "  MuU151 [ class=hypothetical ];  An74 [ class=extant ];  Au318 [ class=extant ];"
				+ "  Ba96 [ class=extant ];  Er16 [ class=extant ];  Go325 [ class=extant ];"
				+ "  Gr314 [ class=extant ];  Kf133 [ class=extant ];  Kr185 [ class=extant ];"
				+ "  Kr299 [ class=extant ];  Mu11475 [ class=extant ];  Mu22405 [ class=extant ];"
				+ "  Mu28315 [ class=extant ];  Sg524 [ class=extant ];  Wi3818 [ class=extant ];"
				+ "  10 -- Ba96;  10 -- Sg524;  1 -- 10;  1 -- 2;  2 -- 3;  2 -- Mu11475;  4 -- 3;"
				+ "  5 -- 4;  6 -- 5;  7 -- 6;  7 -- 8;  7 -- Go325;  7 -- Wi3818;  8 -- Mu22405;"
				+ "  9 -- 5;  9 -- Au318;  9 -- Kr185;  An74 -- 4;  Er16 -- 3;  Gr314 -- 8;"
				+ "  Kf133 -- 6;  Kr299 -- 6;  Mu28315 -- 3;  MuU151 -- 0;  MuU151 -- 1;}";
		
		parser.parseDot(dot,tradId);
		
		try(Transaction tx = db.beginTx())
		{
			Node stemmaNode = db.findNodesByLabelAndProperty(Nodes.STEMMA, "name", "stemma").iterator().next();
		
			assert(stemmaNode!=null);
			
			Node firstNode = db.findNodesByLabelAndProperty(Nodes.WITNESS, "id", "0").iterator().next();
			
			assert(firstNode!=null);
			
			Node nodeB = db.findNodesByLabelAndProperty(Nodes.WITNESS, "id", "b").iterator().next();
			
			assert(nodeB!=null);
			
			boolean checker = false;
			for(Relationship rel : firstNode.getRelationships(Direction.OUTGOING))
			{
				if(rel.getEndNode().equals(nodeB))
					checker = true;
			}
			assert(checker==true);
			
			stemmaNode = db.findNodesByLabelAndProperty(Nodes.STEMMA, "name", "Semstem 1402333041_0").iterator().next();
			
			assert(stemmaNode!=null);
			
			Iterator<Node> nodes = db.findNodesByLabelAndProperty(Nodes.WITNESS, "id", "0").iterator();
			nodes.next();
			assert(nodes.next()!=null);
		}
		catch(Exception e)
		{
			assert(false);
		}
		
	}
	
	
	@After
	public void destroyTestDatabase()
	{
	    db.shutdown();
	    // destroy the test database
	}
}