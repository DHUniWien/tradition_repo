package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Tradition;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import org.neo4j.test.TestGraphDatabaseFactory;

/**
 * Contains all tests for the api calls related to stemmas.
 *
 * @author PSE FS 2015 Team2
 */
public class StemmaTest {
    private String tradId;

    private GraphDatabaseService db;

    /*
     * JerseyTest is the test environment to Test api calls it provides a
     * grizzly http service
     */
    private JerseyTest jerseyTest;

    @Before
    public void setUp() throws Exception {

        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();

        GraphMLToNeo4JParser importResource = new GraphMLToNeo4JParser();

		File testfile = new File("src/TestXMLFiles/testTradition.xml");

        /*
         * Populate the test database with the root node and a user with id 1
         */
        DatabaseService.createRootNode(db);
        try (Transaction tx = db.beginTx()) {
            Node rootNode = db.findNode(Nodes.ROOT, "name", "Root node");
            Node node = db.createNode(Nodes.USER);
            node.setProperty("id", "1");
            node.setProperty("role", "admin");

            rootNode.createRelationshipTo(node, ERelations.SEQUENCE);
            tx.success();
        }


        /**
         * load a tradition to the test DB
         */
        try {
            Response r = importResource.parseGraphML(testfile.getPath(), "1", "Tradition");
            tradId = Util.getValueFromJson(r, "tradId");
        } catch (FileNotFoundException f) {
            // this error should not occur
            assertTrue(false);
        }

        /*
         * Create a JersyTestServer serving the Resource under test
         */
        Tradition tradition = new Tradition();
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer().addResource(tradition).create();
        jerseyTest.setUp();
    }

    @Test
	public void getAllStemmataTest() {
        List<String> stemmata = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemmata")
                .get(new GenericType<List<String>>() {
                });
        assertEquals(2, stemmata.size());

        String expected = "digraph \"stemma\" {\n  0 [ class=hypothetical ];  "
                + "A [ class=extant ];  B [ class=extant ];  "
                + "C [ class=extant ]; 0 -> A;  0 -> B;  A -> C; \n}";
        Util.assertStemmasEquivalent(expected, stemmata.get(0));

        String expected2 = "graph \"Semstem 1402333041_0\" {\n  0 [ class=hypothetical ];  "
                + "A [ class=extant ];  B [ class=extant ];  "
                + "C [ class=extant ]; 0 -- A;  A -- B;  B -- C; \n}";
        Util.assertStemmasEquivalent(expected2, stemmata.get(1));
    }

    @Test
    public void getAllStemmataNotFoundErrorTest() {
        ClientResponse getStemmaResponse = jerseyTest
				.resource()
                .path("/tradition/10000/stemmata")
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), getStemmaResponse.getStatus());
    }

    @Test
	public void getAllStemmataStatusTest() {
        ClientResponse resp = jerseyTest
				.resource()
                .path("/tradition/" + tradId + "/stemmata")
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);

        Response expectedResponse = Response.ok().build();
        assertEquals(expectedResponse.getStatus(), resp.getStatus());
    }

    @Test
	public void getStemmaTest() {
        String stemmaTitle = "stemma";
        String str = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma/" + stemmaTitle)
                .type(MediaType.APPLICATION_JSON)
                .get(String.class);

        String expected = "digraph \"stemma\" {\n  0 [ class=hypothetical ];  "
                + "A [ class=extant ];  B [ class=extant ];  "
                + "C [ class=extant ];\n 0 -> A;  0 -> B;  A -> C; \n}";
        Util.assertStemmasEquivalent(expected, str);

        String stemmaTitle2 = "Semstem 1402333041_0";
        String str2 = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma/" + stemmaTitle2)
                .type(MediaType.APPLICATION_JSON)
                .get(String.class);

        String expected2 = "graph \"Semstem 1402333041_0\" {\n  0 [ class=hypothetical ];\n  "
                + "A [ class=extant ];  B [ class=extant ];  "
                + "C [ class=extant ];\n 0 -- A;  A -- B;  B -- C; \n}";
        Util.assertStemmasEquivalent(expected2, str2);

        ClientResponse getStemmaResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma/gugus")
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), getStemmaResponse.getStatus());
    }

    @Test
    public void setStemmaTest() {

        Node traditionNode = DatabaseService.getTraditionNode(tradId, db);
        ArrayList<Node> stemmata = DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA);
        assertEquals(2, stemmata.size());

        String input = "graph \"Semstem 1402333041_1\" {  0 [ class=hypothetical ];  A [ class=extant ];  B [ class=extant ];  C [ class=extant ]; 0 -- A;  A -- B;  A -- C;}";

        ClientResponse actualStemmaResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma"  )
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, input);
        assertEquals(ClientResponse.Status.CREATED.getStatusCode(), actualStemmaResponse.getStatus());

        try (Transaction tx = db.beginTx()) {
            Result result2 = db.execute("match (t:TRADITION {id:'" + tradId +
                    "'})--(s:STEMMA) return count(s) AS res2");
            assertEquals(3L, result2.columnAs("res2").next());

            tx.success();
        }

        String stemmaTitle = "Semstem 1402333041_1";
        String str = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma/" + stemmaTitle)
                .type(MediaType.APPLICATION_JSON)
                .get(String.class);

        // Parse the resulting stemma and make sure it matches
        Util.assertStemmasEquivalent(input, str);
    }

    @Test
    public void setStemmaNotFoundTest() {

        String emptyInput = "";

		ClientResponse actualStemmaResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/stemma" )
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, emptyInput);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), actualStemmaResponse.getStatus());
    }

    @Test
    public void reorientGraphStemmaTest() {
        String stemmaTitle = "Semstem 1402333041_0";
        String newNodeId = "C";
        String secondNodeId = "0";


        try (Transaction tx = db.beginTx()) {
            Result result1 = db.execute("match (t:TRADITION {id:'" +
                    tradId + "'})-[:HAS_STEMMA]->(n:STEMMA { name:'" +
                    stemmaTitle + "'}) return n");
            Iterator<Node> stNodes = result1.columnAs("n");
            assertTrue(stNodes.hasNext());
            Node startNodeStemma = stNodes.next();

            Iterable<Relationship> rel1 = startNodeStemma
                    .getRelationships(Direction.OUTGOING, ERelations.HAS_ARCHETYPE);
            assertFalse(rel1.iterator().hasNext());

            ClientResponse actualStemmaResponse = jerseyTest
                    .resource()
                    .path("/tradition/" + tradId + "/stemma/" + stemmaTitle + "/reorient/" + newNodeId)
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class);
            assertEquals(Response.ok().build().getStatus(), actualStemmaResponse.getStatus());

            Iterable<Relationship> rel2 = startNodeStemma
                    .getRelationships(Direction.OUTGOING, ERelations.HAS_ARCHETYPE);
            assertTrue(rel2.iterator().hasNext());
            assertEquals(newNodeId, rel2.iterator().next().getEndNode().getProperty("sigil").toString());

            ClientResponse actualStemmaResponseSecond = jerseyTest
                    .resource()
                    .path("/tradition/" + tradId + "/stemma/" + stemmaTitle + "/reorient/" + secondNodeId)
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class);
            assertEquals(Response.ok().build().getStatus(), actualStemmaResponseSecond.getStatus());

            tx.success();
        }
    }

    @Test
    public void reorientGraphStemmaNoNodesTest() {

        String stemmaTitle = "Semstem 1402333041_0";
        String falseNode = "X";
        String rightNode = "C";
        String falseTitle = "X";


        try (Transaction tx = db.beginTx()) {

            ClientResponse actualStemmaResponse = jerseyTest
                    .resource()
                    .path("/tradition/" + tradId + "/stemma/" + stemmaTitle + "/reorient/" + falseNode)
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class);
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualStemmaResponse.getStatus());

            ClientResponse actualStemmaResponse2 = jerseyTest
                    .resource()
                    .path("/tradition/" + tradId + "/stemma/" + falseTitle + "/reorient/" + rightNode)
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class);
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualStemmaResponse2.getStatus());

            tx.success();
        }
    }

    @Test
    public void reorientDigraphStemmaTest() {
        String stemmaTitle = "stemma";
        String newNodeId = "C";

        try (Transaction tx = db.beginTx()) {
            Result result1 = db.execute("match (t:TRADITION {id:'" +
                    tradId + "'})-[:HAS_STEMMA]->(n:STEMMA { name:'" +
                    stemmaTitle + "'}) return n");
            Iterator<Node> stNodes = result1.columnAs("n");
            assertTrue(stNodes.hasNext());
            Node startNodeStemma = stNodes.next();

            Iterable<Relationship> relBevor = startNodeStemma
                    .getRelationships(Direction.OUTGOING, ERelations.HAS_ARCHETYPE);
            assertTrue(relBevor.iterator().hasNext());
            assertEquals("0", relBevor.iterator().next().getEndNode().getProperty("sigil").toString());

            ClientResponse actualStemmaResponse = jerseyTest
                    .resource()
                    .path("/tradition/" + tradId + "/stemma/" + stemmaTitle + "/reorient/" + newNodeId)
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class);
            assertEquals(Response.ok().build().getStatus(), actualStemmaResponse.getStatus());

            Iterable<Relationship> relAfter = startNodeStemma
                    .getRelationships(Direction.OUTGOING, ERelations.HAS_ARCHETYPE);
            assertTrue(relAfter.iterator().hasNext());
            assertEquals(relAfter.iterator().next().getEndNode().getProperty("sigil").toString(),
                    newNodeId);

            tx.success();
        }
    }

    @Test
    public void reorientDigraphStemmaNoNodesTest() {

        String stemmaTitle = "stemma";
        String falseNode = "X";
        String rightNode = "C";
        String falseTitle = "X";

        try (Transaction tx = db.beginTx()) {

            ClientResponse actualStemmaResponse = jerseyTest
                    .resource()
                    .path("/tradition/" + tradId + "/stemma/" +
                            stemmaTitle + "/reorient/" + falseNode)
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class);
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualStemmaResponse.getStatus());

            ClientResponse actualStemmaResponse2 = jerseyTest
                    .resource()
                    .path("/tradition/" + tradId + "/stemma/" +
                            falseTitle + "/reorient/" + rightNode)
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class);
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualStemmaResponse2.getStatus());

            tx.success();
        }
    }

    @Test
    public void reorientDigraphStemmaSameNodeAsBeforeTest() {

        String stemmaTitle = "stemma";
        String newNode = "C";

        try (Transaction tx = db.beginTx()) {

            ClientResponse actualStemmaResponse = jerseyTest
                    .resource()
                    .path("/tradition/" + tradId + "/stemma/" +
                            stemmaTitle + "/reorient/" + newNode)
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class);
            assertEquals(Response.ok().build().getStatus(), actualStemmaResponse.getStatus());

            ClientResponse actualStemmaResponse2 = jerseyTest
                    .resource()
                    .path("/tradition/" + tradId + "/stemma/" +
                            stemmaTitle + "/reorient/" + newNode)
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class);
            assertEquals(Response.ok().build().getStatus(), actualStemmaResponse2.getStatus());

            tx.success();
        }
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