package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import net.stemmaweb.model.*;
import net.stemmaweb.rest.*;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.services.Neo4JToGraphMLParser;

import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.Uniqueness;
import org.neo4j.test.TestGraphDatabaseFactory;

/**
 *
 * @author PSE FS 2015 Team2
 *
 */
public class Neo4JAndGraphMLParserUnitTest {

    private GraphDatabaseService db;

	private GraphMLToNeo4JParser importResource;
	private Neo4JToGraphMLParser exportResource;

    private JerseyTest jerseyTest;

    @Before
	public void setUp() throws Exception {

		db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
		
		importResource = new GraphMLToNeo4JParser();
		exportResource = new Neo4JToGraphMLParser();
		
        // Populate the test database with the root node and a user with id 1
        DatabaseService.createRootNode(db);
    	try(Transaction tx = db.beginTx())
    	{
            Node rootNode = db.findNode(Nodes.ROOT, "name", "Root node");
    		Node node = db.createNode(Nodes.USER);
			node.setProperty("id", "1");
			node.setProperty("role", "admin");

			rootNode.createRelationshipTo(node, ERelations.SEQUENCE);
    		tx.success();
    	}

        // Create a JerseyTestServer for the necessary REST API calls
        Reading reading = new Reading();
        Relation relation = new Relation();
        Stemma stemma = new Stemma();
        Tradition tradition = new Tradition();
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(reading)
                .addResource(relation)
                .addResource(stemma)
                .addResource(tradition)
                .create();
        jerseyTest.setUp();

    }
	
	/**
	 * Try to import a non existent file
	 */
	@Test
	public void graphMLImportFileNotFoundExceptionTest()
	{
		File testfile = new File("src/TestXMLFiles/SapientiaFileNotExisting.xml");
		try
		{
			importResource.parseGraphML(testfile.getPath(), "1", "Tradition");
			
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
		File testfile = new File("src/TestXMLFiles/SapientiaWithError.xml");
		try
		{
			actualResponse = importResource.parseGraphML(testfile.getPath(), "1", "Tradition");
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
		File testfile = new File("src/TestXMLFiles/testTradition.xml");
		try
		{
			actualResponse = importResource.parseGraphML(testfile.getPath(), "1", "Tradition");
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
			ResourceIterator<Node> tradNodesIt = db.findNodes(Nodes.TRADITION, "name", "Tradition");
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
		assertTrue(file.delete());
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
		File testfile = new File("src/TestXMLFiles/testTradition.xml");
		try
		{
			importResource.parseGraphML(testfile.getPath(), "1", "Tradition");
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
		try
		{
			actualResponse = importResource.parseGraphML(file.getPath(), "1", "Tradition");
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
     * import a tradition with Unicode sigla
     */
    @Test
    public void unicodeSigilTest() {
        File testfile = new File("src/TestXMLFiles/john.xml");
        Response parseResponse = null;
        try {
            parseResponse = importResource.parseGraphML(testfile.getPath(), "1", "John tradition");
        } catch(FileNotFoundException f) {
            // this error should not occur
            assertTrue(false);
        }
        assertEquals(Response.status(Response.Status.OK).build().getStatus(),
                parseResponse.getStatus());

        // Check that we have witness α
        Node alpha;
        try (Transaction tx = db.beginTx()) {
            alpha = db.findNode(Nodes.WITNESS, "sigil", "α");
            assertNotNull(alpha);
            // Check that witness α is marked as needing quotes
            assertTrue((Boolean) alpha.getProperty("quotesigil"));
            tx.success();
        }
    }

	/**
	 * Ports of test suite from Perl Text::Tradition::Parser::Self.
	 *
	 * #1: parse a file, check for the correct number of readings, paths, and witnesses
	 */

    @Test
	public void importFlorilegiumTest () {
		Response parseResponse = null;
		File testfile = new File("src/TestXMLFiles/florilegium_graphml.xml");
		try
		{
			parseResponse = importResource.parseGraphML(testfile.getPath(), "1", "Tradition");
		}
		catch(FileNotFoundException f)
		{
			// this error should not occur
			assertTrue(false);
		}

        // Check for success and get the tradition id
		assertEquals(Response.status(Response.Status.OK).build().getStatus(),
                parseResponse.getStatus());
        String traditionId = "NONE";
		try {
            JSONObject content = new JSONObject((String) parseResponse.getEntity());
            traditionId = String.valueOf(content.get("tradId"));
		} catch (JSONException e) {
			assertTrue(false);
		}

		// Check for the correct number of reading nodes
        List<ReadingModel> readings = jerseyTest
                .resource()
                .path("/reading/getallreadings/fromtradition/" + traditionId)
                .type(MediaType.APPLICATION_JSON)
                .get(new GenericType<List<ReadingModel>>() {});
        assertEquals(319, readings.size()); // really 319

        // Check for the correct number of sequence paths. Do this with a traversal.
        AtomicInteger sequenceCount = new AtomicInteger(0);
        Node startNode = DatabaseService.getStartNode(traditionId, db);
        assertNotNull(startNode);
        try (Transaction tx = db.beginTx()) {
            db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .evaluator(Evaluators.all())
                    .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL).traverse(startNode)
                    .relationships().forEach(x -> sequenceCount.getAndIncrement());
            tx.success();
        }
        assertEquals(376, sequenceCount.get()); // should be 376


        // Check for the correct number of witnesses
        List<WitnessModel> witnesses = jerseyTest
                .resource()
                .path("/tradition/getallwitnesses/fromtradition/" + traditionId)
                .type(MediaType.APPLICATION_JSON)
                .get(new GenericType<List<WitnessModel>>() {});
        assertEquals(13, witnesses.size());  // should be 13

        // Check for the correct number of relationships
        List<RelationshipModel> relations = jerseyTest
                .resource()
                .path("/tradition/getallrelationships/fromtradition/" + traditionId)
                .type(MediaType.APPLICATION_JSON)
                .get(new GenericType<List<RelationshipModel>>() {});
        assertEquals(7, relations.size());

        // Spot-check a correct relationship setup
        try(Transaction tx = db.beginTx()) {
            // With this query we are working around some obnoxious problems with divergent
            // Unicode renderings of some Greek letters.
            Result result = db.execute("match (q:READING {text:'πνεύματος'})-->(bs:READING {text:'βλασφημίας'})-->(a:READING {text:'ἀπορία'}), " +
                    "(q)-->(b:READING {text:'βλασφημία'}) return bs, a, b");
            assertTrue(result.hasNext());
            tx.success();
        }

    }


    /**
     * #2: parse a file, mess around with the tradition, export it, check the result
     */

    @Test
    public void exportFlorilegiumTest () {
        Response parseResponse = null;
        File testfile = new File("src/TestXMLFiles/florilegium_graphml.xml");
        try {
            parseResponse = importResource.parseGraphML(testfile.getPath(), "1", "Tradition");
        } catch (FileNotFoundException f) {
            // this error should not occur
            assertTrue(false);
        }

        // Check for success and get the tradition id
        assertEquals(Response.status(Response.Status.OK).build().getStatus(),
                parseResponse.getStatus());
        String traditionId = "NONE";
        try {
            JSONObject content = new JSONObject((String) parseResponse.getEntity());
            traditionId = String.valueOf(content.get("tradId"));
        } catch (JSONException e) {
            assertTrue(false);
        }

        /* // Set the language
        String jsonPayload = "{\"language\":\"Greek\"}";
        ClientResponse jerseyResponse = jerseyTest
                .resource()
                .path("/tradition/changemetadata/fromtradition/" + traditionId)
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, jsonPayload);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());
        */

        // Add a stemma
        String newStemma = "digraph Stemma {\n" +
                "    \"α\" [ class=hypothetical ];\n" +
                "    \"γ\" [ class=hypothetical ];\n" +
                "    \"δ\" [ class=hypothetical ];\n" +
                "    2 [ class=hypothetical,label=\"*\" ];\n" +
                "    3 [ class=hypothetical,label=\"*\" ];\n" +
                "    4 [ class=hypothetical,label=\"*\" ];\n" +
                "    5 [ class=hypothetical,label=\"*\" ];\n" +
                "    7 [ class=hypothetical,label=\"*\" ];\n" +
                "    A [ class=extant ];\n" +
                "    B [ class=extant ];\n" +
                "    C [ class=extant ];\n" +
                "    D [ class=extant ];\n" +
                "    E [ class=extant ];\n" +
                "    F [ class=extant ];\n" +
                "    G [ class=extant ];\n" +
                "    H [ class=extant ];\n" +
                "    K [ class=extant ];\n" +
                "    P [ class=extant ];\n" +
                "    Q [ class=extant ];\n" +
                "    S [ class=extant ];\n" +
                "    T [ class=extant ];\n" +
                "    \"α\" -> A;\n" +
                "    \"α\" -> T;\n" +
                "    \"α\" -> \"δ\";\n" +
                "    \"δ\" -> 2;\n" +
                "    2 -> C;\n" +
                "    2 -> B;\n" +
                "    B -> P;\n" +
                "    B -> S;\n" +
                "    \"δ\" -> \"γ\";\n" +
                "    \"γ\" -> 3;\n" +
                "    3 -> F;\n" +
                "    3 -> H;\n" +
                "    \"γ\" -> 4;\n" +
                "    4 -> D;\n" +
                "    4 -> 5;\n" +
                "    5 -> Q;\n" +
                "    5 -> K;\n" +
                "    5 -> 7;\n" +
                "    7 -> E;\n" +
                "    7 -> G;\n" +
                "}\n";
        ClientResponse jerseyResponse = jerseyTest
                .resource()
                .path("/stemma/newstemma/intradition/" + traditionId)
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newStemma);
        assertEquals(ClientResponse.Status.CREATED.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());

        // Merge a couple of nodes
        Node blasphemias;
        Node aporia;
        Node blasphemia;
        try(Transaction tx = db.beginTx()) {
            // With this query we are working around some obnoxious problems with divergent
            // Unicode renderings of some Greek letters.
            Result result = db.execute("match (q:READING {text:'πνεύματος'})-->(bs:READING {text:'βλασφημίας'})-->(a:READING {text:'ἀπορία'}), " +
                    "(q)-->(b:READING {text:'βλασφημία'}) return bs, a, b");
            assertTrue (result.hasNext());
            Map<String, Object> row = result.next();
            blasphemias = (Node) row.get("bs");
            aporia = (Node) row.get("a");
            blasphemia = (Node) row.get("b");
            tx.success();
        }

        CharacterModel characterModel = new CharacterModel();
        characterModel.setCharacter(" ");
        jerseyResponse = jerseyTest
                .resource()
                .path("/reading/compressreadings/read1id/"
                        + blasphemias.getId() + "/read2id/" + aporia.getId() + "/concatenate/1")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, characterModel);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        try(Transaction tx = db.beginTx()) {
            assertEquals("βλασφημίας ἀπορία", blasphemias.getProperty("text"));
            tx.success();
        }

        // Add a new
        RelationshipModel relationship = new RelationshipModel();
        relationship.setSource(String.valueOf(blasphemias.getId()));
        relationship.setTarget(String.valueOf(blasphemia.getId()));
        relationship.setType("lexical");
        relationship.setAlters_meaning("0");
        relationship.setIs_significant("true");
        relationship.setReading_a("βλασφημίας ἀπορία");
        relationship.setReading_b("βλασφημία");

        jerseyResponse = jerseyTest
                .resource()
                .path("/relation/createrelationship")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, relationship);
        assertEquals(ClientResponse.Status.CREATED.getStatusCode(), jerseyResponse.getStatus());

        // Export the GraphML
        removeOutputFile();
        parseResponse = exportResource.parseNeo4J(traditionId);
        assertEquals(Response.ok().build().getStatus(), parseResponse.getStatus());
        File outputFile = new File("upload/output.xml");
        assertTrue(outputFile.exists());

        // Re-import and test the result
        try {
            parseResponse = importResource.parseGraphML(outputFile.getPath(), "1", "Tradition 2");
        } catch(FileNotFoundException f) {
            // this error should not occur
            assertTrue(false);
        }

        // Check for success and get the tradition id
        assertEquals(Response.status(Response.Status.OK).build().getStatus(),
                parseResponse.getStatus());
        traditionId = "NONE";
        try {
            JSONObject content = new JSONObject((String) parseResponse.getEntity());
            traditionId = String.valueOf(content.get("tradId"));
        } catch (JSONException e) {
            assertTrue(false);
        }

        // Check for the correct number of reading nodes
        List<ReadingModel> readings = jerseyTest
                .resource()
                .path("/reading/getallreadings/fromtradition/" + traditionId)
                .type(MediaType.APPLICATION_JSON)
                .get(new GenericType<List<ReadingModel>>() {});
        assertEquals(318, readings.size());

        // Check for the correct number of sequence paths. Do this with a traversal.
        AtomicInteger sequenceCount = new AtomicInteger(0);
        Node startNode = DatabaseService.getStartNode(traditionId, db);
        assertNotNull(startNode);
        try (Transaction tx = db.beginTx()) {
            db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .evaluator(Evaluators.all())
                    .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL).traverse(startNode)
                    .relationships().forEach(x -> sequenceCount.getAndIncrement());
            tx.success();
        }
        assertEquals(375, sequenceCount.get());


        // Check for the correct number of witnesses
        List<WitnessModel> witnesses = jerseyTest
                .resource()
                .path("/tradition/getallwitnesses/fromtradition/" + traditionId)
                .type(MediaType.APPLICATION_JSON)
                .get(new GenericType<List<WitnessModel>>() {});
        assertEquals(13, witnesses.size());

        // Check for the correct number of relationships
        List<RelationshipModel> relations = jerseyTest
                .resource()
                .path("/tradition/getallrelationships/fromtradition/" + traditionId)
                .type(MediaType.APPLICATION_JSON)
                .get(new GenericType<List<RelationshipModel>>() {
                });
        assertEquals(8, relations.size());

        // Check for the existence of the stemma
        List<String> stemmata = jerseyTest
                .resource()
                .path("/stemma/getallstemmata/fromtradition/" + traditionId)
                .get(new GenericType<List<String>>() {
                });
        assertEquals(1, stemmata.size());

        Util.assertStemmasEquivalent(newStemma, stemmata.get(0));

        // Check for the correct language setting
    }

    /**
     * Shut down the jersey server
     *
     * @throws Exception
     */
    @After
    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
    }
}
