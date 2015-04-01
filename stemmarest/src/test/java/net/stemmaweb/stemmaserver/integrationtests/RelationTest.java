package net.stemmaweb.stemmaserver.integrationtests;


import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.UserModel;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Relation;
import net.stemmaweb.rest.User;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;

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
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;

/**
 * 
 * @author Jakob
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class RelationTest {
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
	 * The Resource under test. The mockDbFactory will be injected into this resource.
	 */
	@InjectMocks
	private Relation relationResource;
	
	/*
	 * JerseyTest is the test environment to Test api calls it provides a grizzly http service 
	 */
	private JerseyTest jerseyTest;
	
	@Before
	public void setUp() throws Exception {

		/*
		 * Populate the test database with the root node
		 */
    	ExecutionEngine engine = new ExecutionEngine(mockDbService);
    	try(Transaction tx = mockDbService.beginTx())
    	{
    		ExecutionResult result = engine.execute("match (n:ROOT) return n");
    		Iterator<Node> nodes = result.columnAs("n");
    		if(!nodes.hasNext())
    		{
    			Node node = mockDbService.createNode(Nodes.ROOT);
    			node.setProperty("name", "Root node");
    			node.setProperty("LAST_INSERTED_TRADITION_ID", "1000");
    		}
    		tx.success();
    	}
    	
    	/*
    	 * Manipulate the newEmbeddedDatabase method of the mockDbFactory to return 
    	 * new TestGraphDatabaseFactory().newImpermanentDatabase() instead
    	 * of dbFactory.newEmbeddedDatabase("database");
    	 */
		Mockito.when(mockDbFactory.newEmbeddedDatabase(Matchers.anyString())).thenReturn(mockDbService);  
		
		/*
		 * Avoid the Databaseservice to shutdown. (Override the shutdown method with nothing)
		 */
		Mockito.doNothing().when(mockDbService).shutdown();
		
		/*
		 * Create a JersyTestServer serving the Resource under test
		 */
		jerseyTest = JerseyTestServerFactory.newJerseyTestServer().addResource(relationResource).create();
		jerseyTest.setUp();
	}
	
	/**
	 * Test if the Resource is up and running
	 */
	@Test
	public void SimpleTest(){
		String actualResponse = jerseyTest.resource().path("/relation").get(String.class);
		assertEquals("The relation api is up and running",actualResponse);
	}
	
	
	
	/**
	 * Shut down the jersey server
	 * @throws Exception
	 */
	@After
	public void tearDown() throws Exception {
		mockDbService.shutdown();
		jerseyTest.tearDown();
	}
}
