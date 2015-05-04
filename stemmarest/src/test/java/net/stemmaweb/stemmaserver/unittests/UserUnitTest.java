package net.stemmaweb.stemmaserver.unittests;

import static org.junit.Assert.assertEquals;

import java.util.Iterator;

import javax.ws.rs.core.Response;

import net.stemmaweb.model.UserModel;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.User;
import net.stemmaweb.services.GraphDatabaseServiceProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

@RunWith(MockitoJUnitRunner.class)
public class UserUnitTest {

	GraphDatabaseService db;

	private User userResource;
	
	@Before
	public void setUp() throws Exception {
		
		GraphDatabaseServiceProvider.setImpermanentDatabase();
		
		db = new GraphDatabaseServiceProvider().getDatabase();
		
		userResource = new User();
		
		/*
		 * Populate the test database with the root node
		 */
    	ExecutionEngine engine = new ExecutionEngine(db);
    	try(Transaction tx = db.beginTx())
    	{
    		ExecutionResult result = engine.execute("match (n:ROOT) return n");
    		Iterator<Node> nodes = result.columnAs("n");
    		if(!nodes.hasNext())
    		{
    			Node node = db.createNode(Nodes.ROOT);
    			node.setProperty("name", "Root node");
    			node.setProperty("LAST_INSERTED_TRADITION_ID", "1000");
    		}
    		tx.success();
    	}

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
