package net.stemmaweb.stemmaserver;


import static org.junit.Assert.*;

import java.util.Iterator;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.User;

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
import com.sun.jersey.test.framework.JerseyTest;

/**
 * 
 * @author Jakob
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class UserTest {
	
	/*
	 * Create a Mock object for the dbFactory. 
	 */
	@Mock
	private GraphDatabaseFactory mockDbFactory = new GraphDatabaseFactory();
	
	
	/*
	 * Create a Spy object for dbService.
	 */
	@Spy
	GraphDatabaseService mockDbService = new TestGraphDatabaseFactory().newImpermanentDatabase();
		
	/*
	 * The Resource under test. The mockDbFactory will be injected into this resource.
	 */
	@InjectMocks
	private User userResource;
	
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
		jerseyTest = JerseyTestServerFactory.NewJerseyTestServer().addResource(userResource).create();
		jerseyTest.setUp();
	}
	
	@Test
	public void SimpleTest(){
		String actualResponse = jerseyTest.resource().path("/user").get(String.class);
		assertEquals(actualResponse, "User!");
	}
	
	@Test
	public void createUserTest(){

        String jsonPayload = "{\"isAdmin\":0,\"id\":1337}";
        ClientResponse returnJSON = jerseyTest.resource().path("/user/create")
				.type(MediaType.APPLICATION_JSON).post(ClientResponse.class, jsonPayload);
		assertEquals(returnJSON.getStatus(), Response.status(Response.Status.CREATED).build().getStatus());
	}
	
	@Test
	public void createConflictingUserTest(){

        String jsonPayload = "{\"isAdmin\":0,\"id\":42}";
        ClientResponse returnJSON = jerseyTest.resource().path("/user/create")
				.type(MediaType.APPLICATION_JSON).post(ClientResponse.class, jsonPayload);
        returnJSON = jerseyTest.resource().path("/user/create")
				.type(MediaType.APPLICATION_JSON).post(ClientResponse.class, jsonPayload);
		assertEquals(returnJSON.getStatus(), Response.status(Response.Status.CONFLICT).build().getStatus());
	}
	
	@Test
	public void getUserTest(){
		String jsonPayload = "{\"isAdmin\":\"0\",\"id\":\"43\"}";
		ClientResponse returnJSON = jerseyTest.resource().path("/user/create")
				.type(MediaType.APPLICATION_JSON).post(ClientResponse.class, jsonPayload);
		
		String actualResponse = jerseyTest.resource().path("/user/43").get(String.class);
		assertEquals(actualResponse, "{\"isAdmin\":\"0\",\"id\":\"43\"}");
	}
	
	@After
	public void tearDown() throws Exception {
		mockDbService.shutdown();
		jerseyTest.tearDown();
	}
}
