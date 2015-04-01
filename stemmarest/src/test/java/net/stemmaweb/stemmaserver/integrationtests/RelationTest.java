package net.stemmaweb.stemmaserver.integrationtests;


import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.model.ReturnIdModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.UserModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Relation;
import net.stemmaweb.rest.Tradition;
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
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;
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
	private String tradId;
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
	 * The Resource under test. The mockDbFactory will be injected into this
	 * resource.
	 */
	@InjectMocks
	private GraphMLToNeo4JParser importResource;

	@InjectMocks
	private Relation relation;

	/*
	 * JerseyTest is the test environment to Test api calls it provides a
	 * grizzly http service
	 */
	private JerseyTest jerseyTest;

	@Before
	public void setUp() throws Exception {

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
			tx.success();
		}

		/*
		 * Manipulate the newEmbeddedDatabase method of the mockDbFactory to
		 * return new TestGraphDatabaseFactory().newImpermanentDatabase()
		 * instead of dbFactory.newEmbeddedDatabase("database");
		 */
		Mockito.when(mockDbFactory.newEmbeddedDatabase(Matchers.anyString())).thenReturn(mockDbService);

		/*
		 * Avoid the Databaseservice to shutdown. (Override the shutdown method
		 * with nothing)
		 */
		Mockito.doNothing().when(mockDbService).shutdown();

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
		try (Transaction tx = mockDbService.beginTx()) {
			ExecutionResult result = engine.execute("match (u:USER)--(t:TRADITION) return t");
			Iterator<Node> nodes = result.columnAs("t");
			assertTrue(nodes.hasNext());
			tradId = (String) nodes.next().getProperty("id");

			tx.success();
		}

		/*
		 * Create a JersyTestServer serving the Resource under test
		 */
		jerseyTest = JerseyTestServerFactory.newJerseyTestServer().addResource(relation).create();
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
	 * Test if a relation is created properly
	 */
	@Test
	public void createRelationshipTestDH41(){
		RelationshipModel relationship = new RelationshipModel();
		String relationshipId = "";
		relationship.setSource("16");
		relationship.setTarget("24");
		relationship.setDe11("grammatical");
		relationship.setDe1("0");
		relationship.setDe6("true");
		relationship.setDe8("april");
		relationship.setDe9("showers");
		relationship.setDe10("local");
		
		ClientResponse actualResponse = jerseyTest.resource().path("/relation/"+tradId+"/relationships").type(MediaType.APPLICATION_JSON).post(ClientResponse.class,relationship);
		assertEquals(Response.Status.CREATED.getStatusCode(), actualResponse.getStatus());
		
    	try (Transaction tx = mockDbService.beginTx()) 
    	{
    		relationshipId = actualResponse.getEntity(ReturnIdModel.class).getId();
    		Relationship loadedRelationship = mockDbService.getRelationshipById(Long.parseLong(relationshipId));
    		
    		assertEquals(16L, loadedRelationship.getStartNode().getId());
    		assertEquals(24L, loadedRelationship.getEndNode().getId());
    		assertEquals("grammatical",loadedRelationship.getProperty("de11"));
    		assertEquals("0",loadedRelationship.getProperty("de1"));
    		assertEquals("true",loadedRelationship.getProperty("de6"));
    		assertEquals("april",loadedRelationship.getProperty("de8"));
    		assertEquals("showers",loadedRelationship.getProperty("de9"));
    		assertEquals("local",loadedRelationship.getProperty("de10"));
    	} 
	}
	
	/**
	 * Test if an 404 error occurs when an invalid target node was tested
	 */
	@Test
	public void createRelationshipWithInvalidTargetIdTestDH41(){
		RelationshipModel relationship = new RelationshipModel();

		relationship.setSource("16");
		relationship.setTarget("1337");
		relationship.setDe11("grammatical");
		relationship.setDe1("0");
		relationship.setDe6("true");
		relationship.setDe8("april");
		relationship.setDe9("showers");
		relationship.setDe10("local");
		
		ClientResponse actualResponse = jerseyTest.resource().path("/relation/"+tradId+"/relationships").type(MediaType.APPLICATION_JSON).post(ClientResponse.class,relationship);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualResponse.getStatus());
	}
	
	/**
	 * Test if an 404 error occurs when an invalid source node was tested
	 */
	@Test
	public void createRelationshipWithInvalidSourceIdTestDH41(){
		RelationshipModel relationship = new RelationshipModel();

		relationship.setSource("1337");
		relationship.setTarget("24");
		relationship.setDe11("grammatical");
		relationship.setDe1("0");
		relationship.setDe6("true");
		relationship.setDe8("april");
		relationship.setDe9("showers");
		relationship.setDe10("local");
		
		ClientResponse actualResponse = jerseyTest.resource().path("/relation/"+tradId+"/relationships").type(MediaType.APPLICATION_JSON).post(ClientResponse.class,relationship);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualResponse.getStatus());
	}
	
	/**
	 * Test the removal method DELETE /relationship/{tradidtionId}/relationships/{relationshipId}
	 */
	@Test
	public void removeRelationshipTestDH43(){
		/*
		 * Create a relationship
		 */
		RelationshipModel relationship = new RelationshipModel();
		String relationshipId = "";
		relationship.setSource("16");
		relationship.setTarget("24");
		relationship.setDe11("grammatical");
		relationship.setDe1("0");
		relationship.setDe6("true");
		relationship.setDe8("april");
		relationship.setDe9("showers");
		relationship.setDe10("local");
		
		ClientResponse actualResponse = jerseyTest.resource().path("/relation/"+tradId+"/relationships").type(MediaType.APPLICATION_JSON).post(ClientResponse.class,relationship);
		relationshipId = actualResponse.getEntity(ReturnIdModel.class).getId();
		
		//ClientResponse removalResponse =
		
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
