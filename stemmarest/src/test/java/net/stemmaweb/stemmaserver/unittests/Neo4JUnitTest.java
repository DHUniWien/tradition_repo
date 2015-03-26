package net.stemmaweb.stemmaserver.unittests;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

public class Neo4JUnitTest {
	
	GraphDatabaseService graphDb;
	
	@Before
	public void prepareTestDatabase()
	{
	    graphDb = new TestGraphDatabaseFactory().newImpermanentDatabase();
	    // create a new Graph Database
	}
	
	@Test
	public void testNodeCreation()
	{
		Node n = null;
		try ( Transaction tx = graphDb.beginTx() )
		{
		    n = graphDb.createNode();
		    n.setProperty( "name", "Nancy" );
		    tx.success();
		}
	
		// The node should have a valid id
		assertTrue(n.getId()>-1L);
	
		// Retrieve a node by using the id of the created node. The id's and
		// property should match.
		try ( Transaction tx = graphDb.beginTx() )
		{
		    Node foundNode = graphDb.getNodeById( n.getId() );
		    assertTrue(foundNode.getId()==n.getId());
		    assertTrue(((String) foundNode.getProperty("name")).equals("Nancy"));
		}
	}
	
	
	@After
	public void destroyTestDatabase()
	{
	    graphDb.shutdown();
	    // destroy the test database
	}
}