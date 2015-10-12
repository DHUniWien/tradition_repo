package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import net.stemmaweb.model.*;
import net.stemmaweb.rest.*;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.services.Neo4JToGraphMLParser;

import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
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
        try(Transaction tx = db.beginTx())
        {
            Result result = db.execute("match (n:ROOT) return n");
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
    public void graphMLImportFileNotFoundExceptionTest() {

        File testfile = new File("src/TestXMLFiles/SapientiaFileNotExisting.xml");
        try {
            importResource.parseGraphML(testfile.getPath(), "1", "Tradition");

            assertTrue(false); // This line of code should never execute
        }
        catch(FileNotFoundException f) {
            assertTrue(true);
        }
    }

    /**
     * Try to import a file with errors
     */
    @Test
    public void graphMLImportXMLStreamErrorTest() {

        Response actualResponse = null;
        File testfile = new File("src/TestXMLFiles/SapientiaWithError.xml");
        try {
            actualResponse = importResource.parseGraphML(testfile.getPath(), "1", "Tradition");
        }
        catch(FileNotFoundException f) {
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
    public void graphMLImportSuccessTest() {
        Response actualResponse = null;
        File testfile = new File("src/TestXMLFiles/testTradition.xml");
        try {
            actualResponse = importResource.parseGraphML(testfile.getPath(), "1", "Tradition");
        }
        catch(FileNotFoundException f) {
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
        try(Transaction tx = db.beginTx()) {
            ResourceIterator<Node> tradNodesIt = db.findNodes(Nodes.TRADITION, "name", "Tradition");
            assertTrue(tradNodesIt.hasNext());
            tx.success();
        }
    }

    /**
     * remove output file
     */
    private void removeOutputFile() {
        String filename = "upload/output.xml";
        File file = new File(filename);
        file.delete();
    }

    /**
     * try to export a non existent tradition
     */
    @Test
    public void graphMLExportTraditionNotFoundTest() {

        Response actualResponse = exportResource.parseNeo4J("1002");
        assertEquals(Response.status(Response.Status.NOT_FOUND).build().getStatus(), actualResponse.getStatus());
    }

    /**
     * try to export a correct tradition
     */
    @Test
    public void graphMLExportSuccessTest() {

        removeOutputFile();
        File testfile = new File("src/TestXMLFiles/testTradition.xml");
        try {
            importResource.parseGraphML(testfile.getPath(), "1", "Tradition");
        }
        catch(FileNotFoundException f) {
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
        try {
            actualResponse = importResource.parseGraphML(filename, "1", "Tradition");
        }
        catch(FileNotFoundException f) {
            // this error should not occur
            assertTrue(false);
        }

        assertEquals(Response.status(Response.Status.OK).build().getStatus(),
                actualResponse.getStatus());

        traditionNodeExistsTest();
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
        int sequenceCount = 0;
        try (Transaction tx = db.beginTx()) {
            Node startNode = null;
            for (ReadingModel reading : readings) {
                if (reading.getIs_start() != null && reading.getIs_start().equals("1")) {
                    startNode = db.getNodeById(Long.valueOf(reading.getId()));
                    break;
                }
            }
            assertNotNull(startNode);
            for (Relationship sequence : db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .evaluator(Evaluators.all())
                    .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL).traverse(startNode)
                    .relationships()) {
                sequenceCount++;
            }
            tx.success();
        }
        assertEquals(376, sequenceCount); // should be 376


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

        // Set the language
        String jsonPayload = "{\"language\":\"Greek\"}";
        ClientResponse jerseyResponse = jerseyTest
                .resource()
                .path("/tradition/changemetadata/fromtradition/" + traditionId)
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, jsonPayload);
        // assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());

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
        jerseyResponse = jerseyTest
                .resource()
                .path("/stemma/newstemma/intradition/" + traditionId)
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newStemma);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());

        // Merge a couple of nodes - TODO why does Cypher not return both instances of πνεύματος?
        Node blasphemias;
        Node aporia;
        Node blasphemia;
        try(Transaction tx = db.beginTx()) {
            Result result = db.execute("match (q:READING {text:'πνεύματος'})-->(bs:READING {text:'βλασφημίας'})-->(a:READING {text:'ἀπορία'})," +
                    " (q)-->(b:READING {text:'βλασφημία'}) return bs, a, b");
            blasphemias = db.getNodeById(46);
            aporia = db.getNodeById(57);
            blasphemia = db.getNodeById(35);
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
                .get(new GenericType<List<ReadingModel>>() {
                });
        assertEquals(318, readings.size());

        // Check for the correct number of sequence paths. Do this with a traversal.
        int sequenceCount = 0;
        try (Transaction tx = db.beginTx()) {
            Node startNode = null;
            for (ReadingModel reading : readings) {
                if (reading.getIs_start() != null && reading.getIs_start().equals("1")) {
                    startNode = db.getNodeById(Long.valueOf(reading.getId()));
                    break;
                }
            }
            assertNotNull(startNode);
            for (Relationship sequence : db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .evaluator(Evaluators.all())
                    .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL).traverse(startNode)
                    .relationships()) {
                sequenceCount++;
            }
            tx.success();
        }
        assertEquals(375, sequenceCount);


        // Check for the correct number of witnesses
        List<WitnessModel> witnesses = jerseyTest
                .resource()
                .path("/tradition/getallwitnesses/fromtradition/" + traditionId)
                .type(MediaType.APPLICATION_JSON)
                .get(new GenericType<List<WitnessModel>>() {
                });
        assertEquals(13, witnesses.size());


        // Check for the correct number of relationships
        List<RelationshipModel> relations = jerseyTest
                .resource()
                .path("/tradition/getallrelationships/fromtradition/" + traditionId)
                .type(MediaType.APPLICATION_JSON)
                .get(new GenericType<List<RelationshipModel>>() {});
        assertEquals(8, relations.size());

        // Check for the existence of the stemma

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
