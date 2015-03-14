package net.stemmaweb.stemmaserver;


import static org.junit.Assert.*;

import java.util.Iterator;

import javax.ws.rs.core.MediaType;

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

@RunWith(MockitoJUnitRunner.class)
public class UserTest {
	
	@Mock
	private GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
	
	@Mock
	private TestA mockTest = new TestA();
	
	@InjectMocks
	private User userResource;
		
	private JerseyTest jerseyTest;
	
	@Before
	public void setUp() throws Exception {

		GraphDatabaseService dbService = new TestGraphDatabaseFactory().newImpermanentDatabase();
		
    	ExecutionEngine engine = new ExecutionEngine(dbService);
    	try(Transaction tx = dbService.beginTx())
    	{
    		ExecutionResult result = engine.execute("match (n:ROOT) return n");
    		Iterator<Node> nodes = result.columnAs("n");
    		if(!nodes.hasNext())
    		{
    			Node node = dbService.createNode(Nodes.ROOT);
    			node.setProperty("name", "Root node");
    			node.setProperty("LAST_INSERTED_TRADITION_ID", "1000");
    		}
    		tx.success();
    	}
    	
		Mockito.when(dbFactory.newEmbeddedDatabase(Matchers.anyString()))
				.thenReturn(dbService);
		
		jerseyTest = JerseyTestServerBuilder.aJerseyTest().addResource(userResource).build();
		jerseyTest.setUp();
		
		
	}
	
	@Test
	public void SimpleTest(){
		String actualResponse = jerseyTest.resource().path("/user").get(String.class);
		assertEquals(actualResponse, "User!");
	}
	
	@Test
	public void createUserTest(){

    	System.out.println(dbFactory.hashCode());
        String jsonPayload = "{\"isAdmin\":0,\"id\":1337}";
        ClientResponse returnJSON = jerseyTest.resource().path("/user/create")
				.type(MediaType.APPLICATION_JSON).post(ClientResponse.class, jsonPayload);
		assertEquals(returnJSON.toString(), "{\"isAdmin\":0,\"id\":1337}");
	}
	
	@After
	public void tearDown() throws Exception {
		jerseyTest.tearDown();
	}
}
