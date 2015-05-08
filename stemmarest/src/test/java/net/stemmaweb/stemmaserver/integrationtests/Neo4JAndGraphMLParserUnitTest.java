package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Iterator;

import javax.ws.rs.core.Response;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.services.Neo4JToGraphMLParser;
import net.stemmaweb.stemmaserver.OSDetector;

import org.junit.Before;
import org.junit.Test;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.ResourceIterable;
import org.neo4j.graphdb.Transaction;

/**
 * 
 * @author PSE FS 2015 Team2
 *
 */
public class Neo4JAndGraphMLParserUnitTest {

	GraphDatabaseService db;

	private GraphMLToNeo4JParser importResource;
	private Neo4JToGraphMLParser exportResource;
	
	@Before
	public void setUp() throws Exception {
		
		GraphDatabaseServiceProvider.setImpermanentDatabase();
		
		db = new GraphDatabaseServiceProvider().getDatabase();
		
		importResource = new GraphMLToNeo4JParser();
		exportResource = new Neo4JToGraphMLParser();
		
		/*
		 * Populate the test database with the root node and a user with id 1
		 */
    	ExecutionEngine engine = new ExecutionEngine(db);
    	try(Transaction tx = db.beginTx())
    	{
    		ExecutionResult result = engine.execute("match (n:ROOT) return n");
    		Iterator<Node> nodes = result.columnAs("n");
    		Node rootNode = null;
    		if(!nodes.hasNext())
    		{
    			rootNode = db.createNode(Nodes.ROOT);
    			rootNode.setProperty("name", "Root node");
    			rootNode.setProperty("LAST_INSERTED_TRADITION_ID", "1000");
    		}
    		
    		Node node = db.createNode(Nodes.USER);
			node.setProperty("id", "1");
			node.setProperty("isAdmin", "1");

			rootNode.createRelationshipTo(node, ERelations.NORMAL);
    		tx.success();
    	}
	}
	
	/**
	 * Try to import a non existent file
	 */
	@Test
	public void graphMLImportFileNotFoundExceptionTest()
	{
		String filename = "";
		if(OSDetector.isWin())
			filename = "src\\TestXMLFiles\\SapientiaFileNotExisting.xml";
		else 
			filename = "src/TestXMLFiles/SapientiaFileNotExisting.xml";
		try
		{
			importResource.parseGraphML(filename, "1", "Tradition");
			
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
		String filename = "";
		if(OSDetector.isWin())
			filename = "src\\TestXMLFiles\\SapientiaWithError.xml";
		else 
			filename = "src/TestXMLFiles/SapientiaWithError.xml";
		try
		{
			actualResponse = importResource.parseGraphML(filename, "1", "Tradition");
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
		String filename = "";
		if(OSDetector.isWin())
			filename = "src\\TestXMLFiles\\testTradition.xml";
		else 
			filename = "src/TestXMLFiles/testTradition.xml";
		try
		{
			actualResponse = importResource.parseGraphML(filename, "1", "Tradition");
		}
		catch(FileNotFoundException f)
		{
			// this error should not occur
			assertTrue(false);
		}
		
		assertEquals(Response.status(Response.Status.OK).build().getStatus(),
				actualResponse.getStatus());
		
		traditionNodeExistsTest();
	}
	
	/**
	 * test if the tradition node exists
	 */
	public void traditionNodeExistsTest(){
		try(Transaction tx = db.beginTx())
    	{
			ResourceIterable<Node> tradNodes = db.findNodesByLabelAndProperty(Nodes.TRADITION, "name", "Tradition");
			Iterator<Node> tradNodesIt = tradNodes.iterator();
			assertTrue(tradNodesIt.hasNext());
			tx.success();
    	}
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
		String filename = "";
		if(OSDetector.isWin())
			filename = "src\\TestXMLFiles\\testTradition.xml";
		else 
			filename = "src/TestXMLFiles/testTradition.xml";
		try
		{
			importResource.parseGraphML(filename, "1", "Tradition");
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
			actualResponse = importResource.parseGraphML(filename, "1", "Tradition");
		}
		catch(FileNotFoundException f)
		{
			// this error should not occur
			assertTrue(false);
		}
		
		assertEquals(Response.status(Response.Status.OK).build().getStatus(),
				actualResponse.getStatus());
		
		traditionNodeExistsTest();
	}
	
}
