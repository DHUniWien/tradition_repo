package net.stemmaweb.stemmaserver;

import static org.junit.Assert.assertEquals;

import java.util.Iterator;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.model.UserModel;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.User;

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

@RunWith(MockitoJUnitRunner.class)
public class UserUnitTest {

	@Mock
	protected GraphDatabaseFactory mockDbFactory = new GraphDatabaseFactory();
	
	@Spy
	protected GraphDatabaseService mockDbService = new TestGraphDatabaseFactory().newImpermanentDatabase();

	@InjectMocks
	private User userResource;
	
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
    	
		Mockito.when(mockDbFactory.newEmbeddedDatabase(Matchers.anyString())).thenReturn(mockDbService);  
		
		Mockito.doNothing().when(mockDbService).shutdown();
	}
	
	@Test
	public void SimpleTest(){
		String actualResponse = userResource.getIt();
		assertEquals(actualResponse, "User!");
	}
	
	@Test
	public void createUserTest(){
		UserModel userToCreate = new UserModel();
		userToCreate.setId("1337");
		userToCreate.setIsAdmin("0");
		Response response = userResource.create(userToCreate);
		
		assertEquals(response.getStatus(), Response.status(Response.Status.CREATED).build().getStatus());
	}
	

}
