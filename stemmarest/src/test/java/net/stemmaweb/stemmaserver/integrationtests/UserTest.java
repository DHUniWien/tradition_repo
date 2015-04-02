package net.stemmaweb.stemmaserver.integrationtests;


import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.UserModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.User;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.OSDetector;

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
	private GraphMLToNeo4JParser importResource;
	
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
	 * Test if a user is correctly removed with all his subgraphs
	 */
	@Test
	public void deleteUserTest(){
		
		/*
		 * Populate the Database with users and traditions
		 */
		String tradId;
		String filename = "";
		if (OSDetector.isWin())
			filename = "src\\TestXMLFiles\\testTradition.xml";
		else
			filename = "src/TestXMLFiles/testTradition.xml";

		/*
		 * Populate the test database with the root node and a user with id 1
		 */
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		try (Transaction tx = mockDbService.beginTx()) {
			ExecutionResult result = engine.execute("match (n:ROOT) return n");
			Iterator<Node> nodes = result.columnAs("n");
			Node rootNode = null;
			if (!nodes.hasNext()) {
				rootNode = mockDbService.createNode(Nodes.ROOT);
				rootNode.setProperty("name", "Root node");
				rootNode.setProperty("LAST_INSERTED_TRADITION_ID", "1000");
			}

			Node node = mockDbService.createNode(Nodes.USER);
			node.setProperty("id", "1");
			node.setProperty("isAdmin", "1");

			rootNode.createRelationshipTo(node, ERelations.NORMAL);
			
			Node node2 = mockDbService.createNode(Nodes.USER);
			node2.setProperty("id", "2");
			node2.setProperty("isAdmin", "1");
			
			rootNode.createRelationshipTo(node2, ERelations.NORMAL);
			
			tx.success();
		}
		
		/**
		 * load a tradition to user 1
		 */
		try {
			importResource.parseGraphML(filename, "1");
		} catch (FileNotFoundException f) {
			// this error should not occur
			assertTrue(false);
		}

		/**
		 * load a tradition to user 2
		 */
		try {
			importResource.parseGraphML(filename, "2");
		} catch (FileNotFoundException f) {
			// this error should not occur
			assertTrue(false);
		}
		
		/**
		 * gets the generated id of the inserted tradition
		 */
		try (Transaction tx = mockDbService.beginTx()) {
			ExecutionResult result = engine.execute("match (u:USER)--(t:TRADITION) return t");
			Iterator<Node> nodes = result.columnAs("t");
			assertTrue(nodes.hasNext());
			tradId = (String) nodes.next().getProperty("id");

			tx.success();
		}

		/*
		 * Now remove one user with all his traditions
		 */
		ClientResponse actualResponse = jerseyTest.resource().path("/user/1").delete(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
		
//		/*
//		 * Test if the 
//		 */
//		RelationshipModel rel = new RelationshipModel();
//		rel.setSource("16");
//		rel.setTarget("27");
//		rel.setId("36");
//		rel.setDe8("april");
//		rel.setDe6("no");
//		rel.setDe9("april");
//		rel.setDe1("0");
//		rel.setDe11("transposition");
//		rel.setDe10("local");
//
//		List<RelationshipModel> relationships = jerseyTest.resource()
//				.path("/tradition/relation/" + tradId + "/relationships")
//				.get(new GenericType<List<RelationshipModel>>() {
//				});
//		RelationshipModel relLoaded = relationships.get(2);
//
//		assertEquals(rel.getSource(), relLoaded.getSource());
//		assertEquals(rel.getTarget(), relLoaded.getTarget());
//		assertEquals(rel.getId(), relLoaded.getId());
//		assertEquals(rel.getDe8(), relLoaded.getDe8());
//		assertEquals(rel.getDe6(), relLoaded.getDe6());
//		assertEquals(rel.getDe9(), relLoaded.getDe9());
//		assertEquals(rel.getDe1(), relLoaded.getDe1());
//		assertEquals(rel.getDe11(), relLoaded.getDe11());
//		assertEquals(rel.getDe10(), relLoaded.getDe10());

		
	}
	
	/**
	 * Test user Removal with invalid userId
	 */
	
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
