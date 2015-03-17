package net.stemmaweb.stemmaserver;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Iterator;

import javax.ws.rs.core.Response;
import javax.xml.stream.XMLStreamException;

import net.stemmaweb.model.UserModel;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Relations;
import net.stemmaweb.rest.User;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.services.Neo4JToGraphMLParser;

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
import org.neo4j.graphdb.ResourceIterable;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.test.TestGraphDatabaseFactory;

@RunWith(MockitoJUnitRunner.class)
public class Neo4JAndGraphMLParserUnitTest {

	@Mock
	protected GraphDatabaseFactory mockDbFactory = new GraphDatabaseFactory();
	
	@Spy
	protected GraphDatabaseService mockDbService = new TestGraphDatabaseFactory().newImpermanentDatabase();

	@InjectMocks
	private GraphMLToNeo4JParser importResource;
	
	@InjectMocks
	private Neo4JToGraphMLParser exportResource;
	
	@Before
	public void setUp() throws Exception {
		
		/*
		 * Populate the test database with the root node and a user with id 1
		 */
    	ExecutionEngine engine = new ExecutionEngine(mockDbService);
    	try(Transaction tx = mockDbService.beginTx())
    	{
    		ExecutionResult result = engine.execute("match (n:ROOT) return n");
    		Iterator<Node> nodes = result.columnAs("n");
    		Node rootNode = null;
    		if(!nodes.hasNext())
    		{
    			rootNode = mockDbService.createNode(Nodes.ROOT);
    			rootNode.setProperty("name", "Root node");
    			rootNode.setProperty("LAST_INSERTED_TRADITION_ID", "1000");
    		}
    		
    		Node node = mockDbService.createNode(Nodes.USER);
			node.setProperty("id", "1");
			node.setProperty("isAdmin", "1");

			rootNode.createRelationshipTo(node, Relations.NORMAL);
    		tx.success();
    	}
    	
		Mockito.when(mockDbFactory.newEmbeddedDatabase(Matchers.anyString())).thenReturn(mockDbService);  
		
		Mockito.doNothing().when(mockDbService).shutdown();
	}
	
	/**
	 * Try to import a non existent file
	 */
	@Test
	public void graphMLImportFileNotFoundExceptionTest()
	{
		String filename = "src/TestXMLFiles/SapientiaFileNotExisting.xml";
		try
		{
			Response actualResponse = importResource.parseGraphML(filename, "1");
			
			assertTrue(false); // This line of code should never execute
		}
		catch(FileNotFoundException f)
		{
			assertTrue(true);
		}
	}
	
	/**
	 * Try to import a file with errors
	 */
	@Test
	public void graphMLImportXMLStreamErrorTest()
	{
		Response actualResponse = null;
		String filename = "src/TestXMLFiles/SapientiaWithError.xml";
		try
		{
			actualResponse = importResource.parseGraphML(filename, "1");
		}
		catch(FileNotFoundException f)
		{
			// this error should not occur
			assertTrue(false);
		}
		
		assertEquals(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build().getStatus(),
				actualResponse.getStatus());
	}
	
	/**
	 * Import a correct file
	 */
	@Test
	public void graphMLImportSuccessTest(){
		Response actualResponse = null;
		String filename = "src/TestXMLFiles/Sapientia.xml";
		try
		{
			actualResponse = importResource.parseGraphML(filename, "1");
		}
		catch(FileNotFoundException f)
		{
			// this error should not occur
			assertTrue(false);
		}
		
		assertEquals(Response.status(Response.Status.OK).build().getStatus(),
				actualResponse.getStatus());
		
		traditionNodeExistsTest();
		traditionEndNodeExistsTest();
	}
	
	/**
	 * test if the tradition node exists
	 */
	public void traditionNodeExistsTest(){
		try(Transaction tx = mockDbService.beginTx())
    	{
			ResourceIterable<Node> tradNodes = mockDbService.findNodesByLabelAndProperty(Nodes.TRADITION, "name", "Sapientia");
			Iterator<Node> tradNodesIt = tradNodes.iterator();
			assertTrue(tradNodesIt.hasNext());
			tx.success();
    	}
	}
	
	/**
	 * test if the tradition end node exists
	 */
	public void traditionEndNodeExistsTest(){
		ExecutionEngine engine = new ExecutionEngine(mockDbService);
		
		ExecutionResult result = engine.execute("match (e)-[:NORMAL]->(n:WORD) where n.text='#END#' return n");
		ResourceIterator<Node> tradNodes = result.columnAs("n");
		assertTrue(tradNodes.hasNext());
	}
	
	/**
	 * remove output file
	 */
	private void removeOutputFile(){
		String filename = "upload/output.xml";
		File file = new File(filename);
		file.delete();
	}
	
	/**
	 * try to export a non existent tradition 
	 */
	@Test
	public void graphMLExportTraditionNotFoundTest(){
		
		Response actualResponse = exportResource.parseNeo4J("1002");
		assertEquals(Response.status(Response.Status.NOT_FOUND).build().getStatus(), actualResponse.getStatus());
	}
	
	/**
	 * try to export a correct tradition
	 */
	@Test
	public void graphMLExportSuccessTest(){
		
		removeOutputFile();
		
		String filename = "src/TestXMLFiles/Sapientia.xml";
		try
		{
			importResource.parseGraphML(filename, "1");
		}
		catch(FileNotFoundException f)
		{
			// this error should not occur
			assertTrue(false);
		}
		
		Response actualResponse = exportResource.parseNeo4J("1001");
		
		assertEquals(Response.ok().build().getStatus(), actualResponse.getStatus());
		
		String outputFile = "upload/output.xml";
		File file = new File(outputFile);
		
		assertTrue(file.exists());
	}
	
	/**
	 * try to import an exported tradition
	 */
	@Test
	public void graphMLExportImportTest(){
		
		String filename = "upload/output.xml";
		Response actualResponse = null;
		try
		{
			actualResponse = importResource.parseGraphML(filename, "1");
		}
		catch(FileNotFoundException f)
		{
			// this error should not occur
			assertTrue(false);
		}
		
		assertEquals(Response.status(Response.Status.OK).build().getStatus(),
				actualResponse.getStatus());
		
		traditionNodeExistsTest();
		traditionEndNodeExistsTest();
	}
	
}
