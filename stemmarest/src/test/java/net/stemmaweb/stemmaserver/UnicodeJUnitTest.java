package net.stemmaweb.stemmaserver;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

public class UnicodeJUnitTest {
	
	GraphDatabaseService graphDb;
	
	@Before
	public void prepareTestDatabase()
	{
	    graphDb = new TestGraphDatabaseFactory().newImpermanentDatabase();
	    // create a new Graph Database
	}
		
	@Test
	public void testUnicodeCapability()
	{
		Node n = null;
		try ( Transaction tx = graphDb.beginTx() )
		{
		    n = graphDb.createNode();
		    n.setProperty( "name", "Ã¤Ã¶Ã¼×“×’×›Î±Î²Î³" );
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
		    assertTrue(((String) foundNode.getProperty("name")).equals("Ã¤Ã¶Ã¼×“×’×›Î±Î²Î³"));
		}
	}
	
	@Test
	public void testHebrewCapabilityMatch()
	{
		Node n = null;
		try ( Transaction tx = graphDb.beginTx() )
		{
		    n = graphDb.createNode();
		    n.setProperty( "name", "בדיקה" );
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
		    assertEquals("בדיקה", (String) foundNode.getProperty("name"));
		}
	}
	
	
	@Test
	public void testHebrewCapabilityNoMatch()
	{
		Node n = null;
		try ( Transaction tx = graphDb.beginTx() )
		{
		    n = graphDb.createNode();
		    n.setProperty( "name", "בדיקה" );
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
		    assertFalse(((String) foundNode.getProperty("name")).equals("בליקה"));
		}
	}
	
	@Test
	public void testGreekCapabilityMatch()
	{
		Node n = null;
		try ( Transaction tx = graphDb.beginTx() )
		{
		    n = graphDb.createNode();
		    n.setProperty( "name", "ειπον" );
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
		    assertEquals("ειπον", (String) foundNode.getProperty("name"));
		}
	}
	
	@Test
	public void testGreekCapabilityNoMatch()
	{
		Node n = null;
		try ( Transaction tx = graphDb.beginTx() )
		{
		    n = graphDb.createNode();
		    n.setProperty( "name", "ειπον" );
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
		    assertFalse(((String) foundNode.getProperty("name")).equals("ειπων"));
		}
	}
	
	@After
	public void destroyTestDatabase()
	{
	    graphDb.shutdown();
	    // destroy the test database
	}
}