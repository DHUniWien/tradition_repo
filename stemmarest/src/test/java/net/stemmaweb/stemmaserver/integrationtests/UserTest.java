package net.stemmaweb.stemmaserver.integrationtests;


import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.UserModel;
import net.stemmaweb.rest.Nodes;
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
public class UserTest {
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
		jerseyTest = JerseyTestServerFactory.newJerseyTestServer().addResource(userResource).create();
		jerseyTest.setUp();
	}
	
	/**
	 * Test if the Resource is up and running
	 */
	@Test
	public void SimpleTest(){
		String actualResponse = jerseyTest.resource().path("/user").get(String.class);
		assertEquals("User!",actualResponse);
	}
	
	/**
	 * Test the ability to create a user
	 */
	@Test
	public void createUserTest(){

		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		ExecutionResult result = null;
		try (Transaction tx = mockDbService.beginTx()) {
			result = engine.execute("match (userId:USER {id:'1337'}) return userId");
			Iterator<Node> nodes = result.columnAs("userId");
			assertFalse(nodes.hasNext());
			tx.success();
		}
		
        String jsonPayload = "{\"isAdmin\":0,\"id\":1337}";
        ClientResponse returnJSON = jerseyTest.resource().path("/user/create")
				.type(MediaType.APPLICATION_JSON).post(ClientResponse.class, jsonPayload);
		assertEquals(Response.status(Response.Status.CREATED).build().getStatus(), returnJSON.getStatus());
		

		try (Transaction tx = mockDbService.beginTx()) {
			result = engine.execute("match (userId:USER {id:'1337'}) return userId");
			Iterator<Node> nodes = result.columnAs("userId");
			assertTrue(nodes.hasNext());
			tx.success();
		}
	}
	
	
	/**
	 * Test the behavior when creating a second user with the same id
	 */
	@Test
	public void createConflictingUserTest(){

        String jsonPayload = "{\"isAdmin\":0,\"id\":42}";
        ClientResponse returnJSON = jerseyTest.resource().path("/user/create")
				.type(MediaType.APPLICATION_JSON).post(ClientResponse.class, jsonPayload);
        returnJSON = jerseyTest.resource().path("/user/create")
				.type(MediaType.APPLICATION_JSON).post(ClientResponse.class, jsonPayload);
		assertEquals(Response.status(Response.Status.CONFLICT).build().getStatus(),returnJSON.getStatus());
	}
	
	/**
	 * Test if the representation of a user is correct
	 */
	@Test
	public void getUserTest(){
		UserModel userModel = new UserModel();
		userModel.setId("43");
		userModel.setIsAdmin("0");
		jerseyTest.resource().path("/user/create").type(MediaType.APPLICATION_JSON).post(ClientResponse.class, userModel);
		
		UserModel actualResponse = jerseyTest.resource().path("/user/43").get(UserModel.class);
		assertEquals("43",actualResponse.getId());
		assertEquals("0",actualResponse.getIsAdmin());
	}
	
	/**
	 * Test if the traditions of a user are listed well
	 */
	@Test
	public void getUserTraditions(){
        String jsonPayload = "{\"isAdmin\":0,\"id\":837462}";
        jerseyTest.resource().path("/user/create").type(MediaType.APPLICATION_JSON).post(ClientResponse.class, jsonPayload);
        
	    ExecutionEngine engine = new ExecutionEngine(mockDbService);
    	try(Transaction tx = mockDbService.beginTx())
    	{
    		// Add the new ownership
    		String createTradition = "CREATE (tradition:TRADITION { id:'842' })";
    		engine.execute(createTradition);
    		String createNewRelationQuery = "MATCH(user:USER {id:'837462'}) "
    				+ "MATCH(tradition: TRADITION {id:'842'}) "
    						+ "SET tradition.dg1 = 'TestTradition' "
    								+ "SET tradition.public = '0' "
    										+ "CREATE (tradition)<-[r:NORMAL]-(user) RETURN r, tradition";
    		engine.execute(createNewRelationQuery);
    		
    		tx.success();
    	} 
    	TraditionModel trad = new TraditionModel();
    	trad.setId("842");
    	trad.setName("TestTradition");
    	List<TraditionModel> traditions = jerseyTest.resource().path("/user/traditions/837462")
    			.get(new GenericType<List<TraditionModel>>(){});
    	TraditionModel tradLoaded = traditions.get(0);
    	assertEquals(trad.getId(), tradLoaded.getId());
    	assertEquals(trad.getName(), tradLoaded.getName());
    	
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
