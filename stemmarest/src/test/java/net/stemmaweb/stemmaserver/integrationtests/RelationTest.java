package net.stemmaweb.stemmaserver.integrationtests;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.model.ReturnIdModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Relation;
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
import com.sun.jersey.api.client.ClientResponse.Status;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;

/**
 * 
 * @author Jakob, Severin
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
		
		ClientResponse actualResponse = jerseyTest.resource().path("/relation/createrelationship/intradition/"+tradId).type(MediaType.APPLICATION_JSON).post(ClientResponse.class,relationship);
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
		
		ClientResponse actualResponse = jerseyTest.resource().path("/relation/createrelationship/intradition/"+tradId).type(MediaType.APPLICATION_JSON).post(ClientResponse.class,relationship);
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
		
		ClientResponse actualResponse = jerseyTest.resource().path("/relation/createrelationship/intradition/"+tradId).type(MediaType.APPLICATION_JSON).post(ClientResponse.class,relationship);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualResponse.getStatus());
	}
	
	/**
	 * Test the removal method DELETE /relationship/{tradidtionId}/relationships/{relationshipId}
	 */
	@Test(expected=NotFoundException.class)
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
		
		ClientResponse removalResponse = jerseyTest.resource().path("/relation/"+tradId+"/relationships/"+relationshipId).delete(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(), removalResponse.getStatus());
		
		try (Transaction tx = mockDbService.beginTx()) 
    	{
    		mockDbService.getRelationshipById(Long.parseLong(relationshipId));
    	} 
	}
	
	/**
	 * Test the removal method DELETE /relationship/{tradidtionId}/relationships/{relationshipId}
	 * Try to remove a relationship that does not exist
	 */
	@Test
	public void removeRelationshipThatDoesNotExistTestDH43(){
		ClientResponse removalResponse = jerseyTest.resource().path("/relation/"+tradId+"/relationships/1337").delete(ClientResponse.class);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), removalResponse.getStatus());
	}
	
	/**
	 * Test the removal method by posting two nodes to /relation/{textid}/relationships/delete
	 */
	@Test(expected=NotFoundException.class)
	public void removeRelationshipTestRemoveAllDH43(){
		/*
		 * Create two relationships between two nodes
		 */
		RelationshipModel relationship = new RelationshipModel();
		String relationshipId1 = "";
		String relationshipId2 = "";
		relationship.setSource("16");
		relationship.setTarget("24");
		relationship.setDe11("grammatical");
		relationship.setDe1("0");
		relationship.setDe6("true");
		relationship.setDe8("april");
		relationship.setDe9("showers");
		relationship.setDe10("local");
		
		ClientResponse actualResponse1 = jerseyTest.resource().path("/relation/"+tradId+"/relationships").type(MediaType.APPLICATION_JSON).post(ClientResponse.class,relationship);
		relationshipId1 = actualResponse1.getEntity(ReturnIdModel.class).getId();
		
		relationship = new RelationshipModel();
		relationship.setSource("16");
		relationship.setTarget("24");
		relationship.setDe11("others");
		relationship.setDe1("0");
		relationship.setDe6("true");
		relationship.setDe8("april");
		relationship.setDe9("showers");
		relationship.setDe10("local");
		
		ClientResponse actualResponse2 = jerseyTest.resource().path("/relation/"+tradId+"/relationships").type(MediaType.APPLICATION_JSON).post(ClientResponse.class,relationship);
		relationshipId2 = actualResponse2.getEntity(ReturnIdModel.class).getId();
		
		/*
		 * Create the model to remove
		 */
		RelationshipModel removeModel = new RelationshipModel();
		removeModel.setSource("16");
		removeModel.setTarget("24");
		removeModel.setDe10("local");
		
		ClientResponse removalResponse = jerseyTest.resource().path("/relation/"+tradId+"/relationships/delete").type(MediaType.APPLICATION_JSON).post(ClientResponse.class,removeModel);
		assertEquals(Response.Status.OK.getStatusCode(), removalResponse.getStatus());
		
		try (Transaction tx = mockDbService.beginTx()) 
    	{
    		mockDbService.getRelationshipById(Long.parseLong(relationshipId1));
    		mockDbService.getRelationshipById(Long.parseLong(relationshipId2));
    	} 
	}
	
	/**
	 * Test the removal method DELETE /relationship/{tradidtionId}/relationships/{relationshipId}
	 */
	@Test(expected=NotFoundException.class)
	public void removeRelationshipDocumentWideTestDH43(){
		/*
		 * Create a relationship
		 */
		RelationshipModel relationship = new RelationshipModel();
		String relationshipId1 = "";
		String relationshipId2 = "";
		relationship.setSource("16");
		relationship.setTarget("17");
		relationship.setDe11("grammatical");
		relationship.setDe1("0");
		relationship.setDe6("true");
		relationship.setDe8("april");
		relationship.setDe9("pierced");
		relationship.setDe10("local");
		
		ClientResponse actualResponse = jerseyTest.resource().path("/relation/"+tradId+"/relationships").type(MediaType.APPLICATION_JSON).post(ClientResponse.class,relationship);
		relationshipId1 = actualResponse.getEntity(ReturnIdModel.class).getId();
		
		relationship.setSource("27");
		relationship.setTarget("17");
		relationship.setDe11("grammatical");
		relationship.setDe1("0");
		relationship.setDe6("true");
		relationship.setDe8("april");
		relationship.setDe9("pierced");
		relationship.setDe10("local");
		
		actualResponse = jerseyTest.resource().path("/relation/"+tradId+"/relationships").type(MediaType.APPLICATION_JSON).post(ClientResponse.class,relationship);
		relationshipId2 = actualResponse.getEntity(ReturnIdModel.class).getId();
		
		relationship.setDe10("document");
		
		ClientResponse removalResponse = jerseyTest.resource().path("/relation/"+tradId+"/relationships/delete").type(MediaType.APPLICATION_JSON).post(ClientResponse.class, relationship);
		assertEquals(Response.Status.OK.getStatusCode(), removalResponse.getStatus());
		
		try (Transaction tx = mockDbService.beginTx()) 
    	{
			Relationship rel1 = mockDbService.getRelationshipById(Long.parseLong(relationshipId1));
    		Relationship rel2 = mockDbService.getRelationshipById(Long.parseLong(relationshipId2));
    		
    		RelationshipModel relMod1 = new RelationshipModel(rel1);
    		assertEquals(rel1, relMod1);
    		
    		RelationshipModel relMod2 = new RelationshipModel(rel2);
    		assertEquals(rel2, relMod2);
    	} 
	}
	
	/**
	 * Test that cross relations may not be made
	 * IMPORTANT!! THIS TEST WILL BE GREEN AFTER DH-39 has been implemented!!
	 */
	@Test
	public void createRelationshipTestWithCrossRelationConstraintDH39(){
		RelationshipModel relationship = new RelationshipModel();
		relationship.setSource("6");
		relationship.setTarget("20");
		relationship.setDe11("grammatical");
		relationship.setDe1("0");
		relationship.setDe6("true");
		relationship.setDe8("root");
		relationship.setDe9("teh");
		
		// This relationship should be makeable
		ClientResponse actualResponse = jerseyTest.resource().path("/relation/"+tradId+"/relationships").type(MediaType.APPLICATION_JSON).post(ClientResponse.class,relationship);
		assertEquals(Response.Status.CREATED.getStatusCode(), actualResponse.getStatus());
		
		relationship.setSource("21");
		relationship.setTarget("28");
		relationship.setDe11("grammatical");
		relationship.setDe1("0");
		relationship.setDe6("true");
		relationship.setDe8("rood");
		relationship.setDe9("the");
		
		// this one should not be makeable, due to the cross-relationship-constraint!
		actualResponse = jerseyTest.resource().path("/relation/"+tradId+"/relationships").type(MediaType.APPLICATION_JSON).post(ClientResponse.class,relationship);
		assertEquals(Status.CONFLICT, actualResponse.getClientResponseStatus());
		// RETURN CONFLICT IF THE CROSS RELATIONSHIP RULE IS TAKING ACTION
		
    	try (Transaction tx = mockDbService.beginTx()) 
    	{
    		Node node28 = mockDbService.getNodeById(28L);
    		Iterator<Relationship> rels = node28.getRelationships(ERelations.RELATIONSHIP).iterator();
    		
    		assertTrue(!rels.hasNext()); // make sure node 28 does not have a relationship now!
    	} 
	}
	
	@Test
	public void createRelationshipTestWithCrossRelationConstraintDH39NotDirectlyCloseToEachOther(){
		RelationshipModel relationship = new RelationshipModel();
		relationship.setSource("6");
		relationship.setTarget("20");
		relationship.setDe11("grammatical");
		relationship.setDe1("0");
		relationship.setDe6("true");
		relationship.setDe8("root");
		relationship.setDe9("teh");
		
		// This relationship should be makeable
		ClientResponse actualResponse = jerseyTest.resource().path("/relation/"+tradId+"/relationships").type(MediaType.APPLICATION_JSON).post(ClientResponse.class,relationship);
		assertEquals(Status.CREATED.getStatusCode(), actualResponse.getStatus());
		
		try (Transaction tx = mockDbService.beginTx()) {
			Relationship rel = mockDbService.getRelationshipById(48);
			assertEquals("root", rel.getStartNode().getProperty("dn15"));
			assertEquals("teh", rel.getEndNode().getProperty("dn15"));
		}

		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		ExecutionResult result = engine.execute("match (w:WORD {dn15:'rood'}) return w");
		Iterator<Node> nodes = result.columnAs("w");
		assertTrue(nodes.hasNext());
		Node node = nodes.next();
		assertFalse(nodes.hasNext());
		
		relationship.setSource(node.getId() + "");

		result = engine.execute("match (w:WORD {dn15:'unto'}) return w");
		nodes = result.columnAs("w");
		assertTrue(nodes.hasNext());
		node = nodes.next();
		assertFalse(nodes.hasNext());

		relationship.setTarget(node.getId() + "");

		relationship.setDe11("grammatical");
		relationship.setDe1("0");
		relationship.setDe6("true");
		relationship.setDe8("rood");
		relationship.setDe9("unto");
		
		// this one should not be makeable, due to the cross-relationship-constraint!
		actualResponse = jerseyTest.resource().path("/relation/"+tradId+"/relationships").type(MediaType.APPLICATION_JSON).post(ClientResponse.class,relationship);
		assertEquals(Status.CONFLICT, actualResponse.getClientResponseStatus());
		// RETURN CONFLICT IF THE CROSS RELATIONSHIP RULE IS TAKING ACTION
		
    	try (Transaction tx = mockDbService.beginTx()) 
    	{
    		Node node21 = mockDbService.getNodeById(21L);
    		Iterator<Relationship> rels = node21.getRelationships(ERelations.RELATIONSHIP).iterator();
    		
    		assertTrue(!rels.hasNext()); // make sure node 21 does not have a relationship now!
    	} 
	}
	
	/**
	 * Test if the get relationship method returns the correct value
	 */
	@Test
	public void getRelationshipTest(){
		
		ClientResponse response = jerseyTest.resource().path("/relation/getallrelationships/formtradition/"+tradId)
				.get(ClientResponse.class);
		List<RelationshipModel> relationships = jerseyTest.resource()
				.path("/relation/"+tradId+"/relationships")
				.get(new GenericType<List<RelationshipModel>>() {
				});
		//assertEquals(Response.ok().build().getStatus(), response.getStatus());
		for(RelationshipModel rel : relationships){
			assertTrue(rel.getId().equals("34")||rel.getId().equals("35")||rel.getId().equals("36"));
			assertTrue(rel.getDe9().equals("april")||rel.getDe9().equals("drought")||rel.getDe9().equals("march"));
			assertTrue(rel.getDe11().equals("transposition")||rel.getDe11().equals("transposition")||rel.getDe11().equals("transposition"));
		}
	}
	
	/**
	 * Test if the get relationship method returns correct state
	 */
	@Test
	public void getRelationshipCorrectStatusTest(){
		
		ClientResponse response = jerseyTest.resource().path("/relation/"+tradId+"/relationships")
				.get(ClientResponse.class);
		assertEquals(Response.ok().build().getStatus(), response.getStatus());
	}
	
	/**
	 * Test if the get relationship method returns the correct not found error on a non existing tradid
	 */
	@Test
	public void getRelationshipExceptionTest(){
		ClientResponse response = jerseyTest.resource().path("/relation/"+6999+"/relationships").type(MediaType.APPLICATION_JSON).get(ClientResponse.class);
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
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
