package net.stemmaweb.stemmaserver.integrationtests;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.model.*;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;

import net.stemmaweb.stemmaserver.Util;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.*;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import org.neo4j.test.TestGraphDatabaseFactory;

import static org.junit.Assert.*;

/**
 * Contains all tests for the api calls related to the tradition.
 *
 * @author PSE FS 2015 Team2
 */
public class TraditionTest {
    private String tradId;

    private GraphDatabaseService db;

    /*
     * JerseyTest is the test environment to Test api calls it provides a
     * grizzly http service
     */
    private JerseyTest jerseyTest;
    private HashMap<String, String> readingLookup;

    @Before
    public void setUp() throws Exception {

        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory()
                .newImpermanentDatabase())
                .getDatabase();
        Util.setupTestDB(db, "1");

        // Create a JerseyTestServer for the necessary REST API calls
        Root webResource = new Root();
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(webResource)
                .create();
        jerseyTest.setUp();

        /*
         * create a tradition inside the test DB
         */
        tradId = createTraditionFromFile("Tradition", "src/TestFiles/testTradition.xml", "1");
        /*
         * get a mapping of reading text/rank to ID
         */
        List<ReadingModel> allReadings = jerseyTest.resource().path("/tradition/" + tradId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});
        readingLookup = new HashMap<>();
        for (ReadingModel rm : allReadings) {
            String key = rm.getText() + "/" + rm.getRank().toString();
            readingLookup.put(key, rm.getId());
        }

    }

    private String createTraditionFromFile(String tName, String fName, String userId) {

        ClientResponse jerseyResult = null;
        try {
            jerseyResult = Util.createTraditionFromFileOrString(jerseyTest, tName, "LR", userId, fName, "stemmaweb");
        } catch (Exception e) {
            fail();
        }
        return Util.getValueFromJson(jerseyResult, "tradId");
    }

    @Test
    public void getAllTraditionsTest() {
        HashSet<String> expectedIds = new HashSet<>();
        expectedIds.add(tradId);

        // import a second tradition into the db
        String testfile = "src/TestFiles/testTradition.xml";
        expectedIds.add(createTraditionFromFile("Tradition", testfile, "1"));

        List<TraditionModel> traditions = jerseyTest.resource().path("/traditions")
                .get(new GenericType<List<TraditionModel>>() {});
        for (TraditionModel returned : traditions) {
            assertTrue(expectedIds.contains(returned.getId()));
            assertEquals("Tradition", returned.getName());
        }
    }

    @Test
    public void getAllTraditionsWithParameterNotFoundTest() {
        ClientResponse resp = jerseyTest
                .resource()
                .path("/traditions/" + 2342)
                .get(ClientResponse.class);
        assertEquals(Response.status(Status.NOT_FOUND).build().getStatus(), resp.getStatus());
    }

    /* TODO: This test needs to be fixed - it expects the relationships to be returned in
       an order that is not guaranteed. */
    @Test(expected = org.junit.ComparisonFailure.class)
    public void getAllRelationshipsTest() {
        String jsonPayload = "{\"role\":\"user\",\"id\":1}";
        jerseyTest.resource()
                .path("/user/1")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, jsonPayload);

        RelationModel rel = new RelationModel();
        rel.setSource("27");
        rel.setTarget("16");
        rel.setId("36");
        rel.setIs_significant("no");
        rel.setAlters_meaning(0L);
        rel.setType("transposition");
        rel.setScope("local");

        List<RelationModel> relationships = jerseyTest.resource()
                .path("/tradition/" + tradId + "/relations")
                .get(new GenericType<List<RelationModel>>() {});
        RelationModel relLoaded = relationships.get(2);

        assertEquals(rel.getSource(), relLoaded.getSource());
        assertEquals(rel.getTarget(), relLoaded.getTarget());
        assertEquals(rel.getId(), relLoaded.getId());
        assertEquals(rel.getIs_significant(), relLoaded.getIs_significant());
        assertEquals(rel.getAlters_meaning(), relLoaded.getAlters_meaning());
        assertEquals(rel.getType(), relLoaded.getType());
        assertEquals(rel.getScope(), relLoaded.getScope());
    }

    @Test
    public void getAllRelationshipsCorrectAmountTest() {

        List<RelationModel> relationships = jerseyTest.resource()
                .path("/tradition/" + tradId + "/relations")
                .get(new GenericType<List<RelationModel>>() {});

        assertEquals(3, relationships.size());
    }

    @Test
    public void getAllWitnessesTest() {
        Set<String> expectedWitnesses = new HashSet<>(Arrays.asList("A", "B", "C"));
        List<WitnessModel> witnesses = jerseyTest.resource()
                .path("/tradition/" + tradId + "/witnesses")
                .get(new GenericType<List<WitnessModel>>() {});
        assertEquals(expectedWitnesses.size(), witnesses.size());
        for (WitnessModel w: witnesses) {
            assertTrue(expectedWitnesses.contains(w.getSigil()));
        }
    }

    @Test
    public void getAllWitnessesTraditionNotFoundTest() {
        ClientResponse resp = jerseyTest.resource()
                .path("/tradition/10000/witnesses")
                .get(ClientResponse.class);

        assertEquals(Response.status(Status.NOT_FOUND).build().getStatus(), resp.getStatus());
    }

    @Test
    public void getDotOfNonExistentTraditionTest() {
        ClientResponse resp = jerseyTest
                .resource()
                .path("/tradition/10000/dot")
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);

        assertEquals(Response.status(Status.NOT_FOUND).build().getStatus(), resp.getStatus());
    }

    @Test
    public void getDotTest() {
        String str = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/dot")
                .type(MediaType.APPLICATION_JSON)
                .get(String.class);

        String[] exp = new String[64];
        exp[0] = "digraph \"Tradition\" \\{";
        exp[1] = "graph \\[bgcolor=\"none\", rankdir=\"LR\"\\]";
        exp[2] = "node \\[fillcolor=\"white\", fontsize=\"14\", shape=\"ellipse\", style=\"filled\"\\]";
        exp[3] = "edge \\[arrowhead=\"open\", color=\"#000000\", fontcolor=\"#000000\"\\]";
        exp[4] = String.format("subgraph \\{ rank=same \"n%s\" \"#SILENT#\" \\}", readingLookup.get("#START#/0"));
        exp[5] = "\"#SILENT#\" \\[shape=diamond,color=white,penwidth=0,label=\"\"\\]";
        exp[6] = String.format("n%s \\[id=\"__START__\", label=\"#START#\"\\]", readingLookup.get("#START#/0"));
        exp[7] = String.format("n%s \\[id=\"n%s\", label=\"when\"\\]", readingLookup.get("when/1"), readingLookup.get("when/1"));
        exp[9] = String.format("n%s \\[id=\"n%s\", label=\"april\"\\]", readingLookup.get("april/2"), readingLookup.get("april/2"));
        exp[11] = String.format("n%s \\[id=\"n%s\", label=\"showers\"\\]", readingLookup.get("showers/5"), readingLookup.get("showers/5"));
        exp[14] = String.format("n%s \\[id=\"n%s\", label=\"with\"\\]", readingLookup.get("with/3"), readingLookup.get("with/3"));
        exp[16] = String.format("n%s \\[id=\"n%s\", label=\"sweet\"\\]", readingLookup.get("sweet/6"), readingLookup.get("sweet/6"));
        exp[18] = String.format("n%s \\[id=\"n%s\", label=\"his\"\\]", readingLookup.get("his/4"), readingLookup.get("his/4"));
        exp[20] = String.format("n%s \\[id=\"n%s\", label=\"with\"\\]", readingLookup.get("with/7"), readingLookup.get("with/7"));
        exp[22] = String.format("n%s \\[id=\"n%s\", label=\"april\"\\]", readingLookup.get("april/8"), readingLookup.get("april/8"));
        exp[24] = String.format("n%s \\[id=\"n%s\", label=\"fruit\"\\]", readingLookup.get("fruit/9"), readingLookup.get("fruit/9"));
        exp[27] = String.format("n%s \\[id=\"n%s\", label=\"teh\"\\]", readingLookup.get("teh/10"), readingLookup.get("teh/10"));
        exp[29] = String.format("n%s \\[id=\"n%s\", label=\"the\"\\]", readingLookup.get("the/10"), readingLookup.get("the/10"));
        exp[31] = String.format("n%s \\[id=\"n%s\", label=\"drought\"\\]", readingLookup.get("drought/11"), readingLookup.get("drought/11"));
        exp[34] = String.format("n%s \\[id=\"n%s\", label=\"march\"\\]", readingLookup.get("march/11"), readingLookup.get("march/11"));
        exp[36] = String.format("n%s \\[id=\"n%s\", label=\"of\"\\]", readingLookup.get("of/12"), readingLookup.get("of/12"));
        exp[39] = String.format("n%s \\[id=\"n%s\", label=\"drought\"\\]", readingLookup.get("drought/13"), readingLookup.get("drought/13"));
        exp[41] = String.format("n%s \\[id=\"n%s\", label=\"march\"\\]", readingLookup.get("march/13"), readingLookup.get("march/13"));
        exp[43] = String.format("n%s \\[id=\"n%s\", label=\"has\"\\]", readingLookup.get("has/14"), readingLookup.get("has/14"));
        exp[46] = String.format("n%s \\[id=\"n%s\", label=\"pierced\"\\]", readingLookup.get("pierced/15"), readingLookup.get("pierced/15"));
        exp[48] = String.format("n%s \\[id=\"n%s\", label=\"to\"\\]", readingLookup.get("to/16"), readingLookup.get("to/16"));
        exp[50] = String.format("n%s \\[id=\"n%s\", label=\"unto\"\\]", readingLookup.get("unto/16"), readingLookup.get("unto/16"));
        exp[52] = String.format("n%s \\[id=\"n%s\", label=\"teh\"\\]", readingLookup.get("teh/16"), readingLookup.get("teh/16"));
        exp[54] = String.format("n%s \\[id=\"n%s\", label=\"the\"\\]", readingLookup.get("the/17"), readingLookup.get("the/17"));
        exp[57] = String.format("n%s \\[id=\"n%s\", label=\"rood\"\\]", readingLookup.get("rood/17"), readingLookup.get("rood/17"));
        exp[59] = String.format("n%s \\[id=\"n%s\", label=\"root\"\\]", readingLookup.get("root/18"), readingLookup.get("root/18"));
        exp[61] = String.format("n%s \\[id=\"__END__\", label=\"#END#\"\\]", readingLookup.get("#END#/19"));
        exp[8] = String.format("n%s->n%s \\[label=\"A, B, C\", id=\"e\\d+\", penwidth=\"1.4\"\\]", readingLookup.get("#START#/0"), readingLookup.get("when/1"));
        exp[10] = String.format("n%s->n%s \\[label=\"A\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("when/1"), readingLookup.get("april/2"));
        exp[12] = String.format("n%s->n%s \\[label=\"A\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("april/2"), readingLookup.get("with/3"));
        exp[13] = String.format("n%s->n%s \\[label=\"B, C\", id=\"e\\d+\", penwidth=\"1.2\", minlen=\"4\"\\]", readingLookup.get("when/1"), readingLookup.get("showers/5"));
        exp[15] = String.format("n%s->n%s \\[label=\"A\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("with/3"), readingLookup.get("his/4"));
        exp[17] = String.format("n%s->n%s \\[label=\"A, B, C\", id=\"e\\d+\", penwidth=\"1.4\"\\]", readingLookup.get("showers/5"), readingLookup.get("sweet/6"));
        exp[19] = String.format("n%s->n%s \\[label=\"A\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("his/4"), readingLookup.get("showers/5"));
        exp[21] = String.format("n%s->n%s \\[label=\"A, B, C\", id=\"e\\d+\", penwidth=\"1.4\"\\]", readingLookup.get("sweet/6"), readingLookup.get("with/7"));
        exp[23] = String.format("n%s->n%s \\[label=\"B, C\", id=\"e\\d+\", penwidth=\"1.2\"\\]", readingLookup.get("with/7"), readingLookup.get("april/8"));
        exp[25] = String.format("n%s->n%s \\[label=\"A\", id=\"e\\d+\", penwidth=\"1.0\", minlen=\"2\"\\]", readingLookup.get("with/7"), readingLookup.get("fruit/9"));
        exp[26] = String.format("n%s->n%s \\[label=\"B, C\", id=\"e\\d+\", penwidth=\"1.2\"\\]", readingLookup.get("april/8"), readingLookup.get("fruit/9"));
        exp[28] = String.format("n%s->n%s \\[label=\"C\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("fruit/9"), readingLookup.get("teh/10"));
        exp[30] = String.format("n%s->n%s \\[label=\"A, B\", id=\"e\\d+\", penwidth=\"1.2\"\\]", readingLookup.get("fruit/9"), readingLookup.get("the/10"));
        exp[32] = String.format("n%s->n%s \\[label=\"C\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("teh/10"), readingLookup.get("drought/11"));
        exp[33] = String.format("n%s->n%s \\[label=\"A\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("the/10"), readingLookup.get("drought/11"));
        exp[35] = String.format("n%s->n%s \\[label=\"B\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("the/10"), readingLookup.get("march/11"));
        exp[37] = String.format("n%s->n%s \\[label=\"A, C\", id=\"e\\d+\", penwidth=\"1.2\"\\]", readingLookup.get("drought/11"), readingLookup.get("of/12"));
        exp[38] = String.format("n%s->n%s \\[label=\"B\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("march/11"), readingLookup.get("of/12"));
        exp[40] = String.format("n%s->n%s \\[label=\"B\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("of/12"), readingLookup.get("drought/13"));
        exp[42] = String.format("n%s->n%s \\[label=\"A, C\", id=\"e\\d+\", penwidth=\"1.2\"\\]", readingLookup.get("of/12"), readingLookup.get("march/13"));
        exp[44] = String.format("n%s->n%s \\[label=\"B\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("drought/13"), readingLookup.get("has/14"));
        exp[45] = String.format("n%s->n%s \\[label=\"A, C\", id=\"e\\d+\", penwidth=\"1.2\"\\]", readingLookup.get("march/13"), readingLookup.get("has/14"));
        exp[47] = String.format("n%s->n%s \\[label=\"A, B, C\", id=\"e\\d+\", penwidth=\"1.4\"\\]", readingLookup.get("has/14"), readingLookup.get("pierced/15"));
        exp[49] = String.format("n%s->n%s \\[label=\"B\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("pierced/15"), readingLookup.get("to/16"));
        exp[51] = String.format("n%s->n%s \\[label=\"A\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("pierced/15"), readingLookup.get("unto/16"));
        exp[53] = String.format("n%s->n%s \\[label=\"C\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("pierced/15"), readingLookup.get("teh/16"));
        exp[55] = String.format("n%s->n%s \\[label=\"A\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("unto/16"), readingLookup.get("the/17"));
        exp[56] = String.format("n%s->n%s \\[label=\"B\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("to/16"), readingLookup.get("the/17"));
        exp[58] = String.format("n%s->n%s \\[label=\"C\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("teh/16"), readingLookup.get("rood/17"));
        exp[60] = String.format("n%s->n%s \\[label=\"A, B\", id=\"e\\d+\", penwidth=\"1.2\"\\]", readingLookup.get("the/17"), readingLookup.get("root/18"));
        exp[62] = String.format("n%s->n%s \\[label=\"C\", id=\"e\\d+\", penwidth=\"1.0\", minlen=\"2\"\\]", readingLookup.get("rood/17"), readingLookup.get("#END#/19"));
        exp[63] = String.format("n%s->n%s \\[label=\"A, B\", id=\"e\\d+\", penwidth=\"1.2\"\\]", readingLookup.get("root/18"), readingLookup.get("#END#/19"));

        for (String anExp : exp) {
            Pattern p = Pattern.compile(anExp);
            Matcher m = p.matcher(str);
            assertTrue(m.find());
        }
    }

    /**
     * Test if it is possible to change the user of a Tradition
     */
    @Test
    public void changeMetadataOfATraditionTest() {

        Result result;
        Node newUser;
        /*
         * Create a second user with id 42
         */
        try (Transaction tx = db.beginTx()) {
            Node rootNode = db.findNode(Nodes.ROOT, "name", "Root node");
            newUser = db.createNode(Nodes.USER);
            newUser.setProperty("id", "42");
            newUser.setProperty("role", "admin");

            rootNode.createRelationshipTo(newUser, ERelations.SYSTEMUSER);
            tx.success();
        }

        /*
         * The user with id 42 has no tradition
         */
        try (Transaction tx = db.beginTx()) {
            Iterable<Relationship> ownedTraditions = newUser.getRelationships(ERelations.OWNS_TRADITION);
            tx.success();
            assertFalse(ownedTraditions.iterator().hasNext());
        }

        /*
         * Verify that user 1 has tradition
         */
        try (Transaction tx = db.beginTx()) {
            Node origUser = db.findNode(Nodes.USER, "id", "1");
            Iterable<Relationship> ownership = origUser.getRelationships(ERelations.OWNS_TRADITION);
            assertTrue(ownership.iterator().hasNext());
            Node tradNode = ownership.iterator().next().getEndNode();
            TraditionModel tradition = new TraditionModel(tradNode);

            assertEquals(tradId, tradition.getId());
            assertEquals("Tradition", tradition.getName());

            tx.success();
        } catch (Exception e) {
            fail();
        }

        /*
         * Change the owner of the tradition, and another of its properties
         */
        TraditionModel textInfo = new TraditionModel();
        textInfo.setName("RenamedTraditionName");
        textInfo.setLanguage("nital");
        textInfo.setDirection("RL");
        textInfo.setIs_public(false);
        textInfo.setOwner("42");

        ClientResponse ownerChangeResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId)
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, textInfo);
        assertEquals(Status.OK.getStatusCode(), ownerChangeResponse.getStatus());

        /*
         * Test if user with id 42 has now the tradition
         */
        try (Transaction tx = db.beginTx()) {
            Node tradNode = db.findNode(Nodes.TRADITION, "id", tradId);
            TraditionModel tradition = new TraditionModel(tradNode);

            assertEquals("42", tradition.getOwner());
            assertEquals(tradId, tradition.getId());
            assertEquals("RenamedTraditionName", tradition.getName());
            assertEquals("RL", tradition.getDirection());
            tx.success();

        }

        /*
         * The user with id 1 has no tradition
         */
        try (Transaction tx = db.beginTx()) {
            result = db.execute("match (n)<-[:OWNS_TRADITION]-(userId:USER {id:'1'}) return n");
            Iterator<Node> tradIterator = result.columnAs("n");
            assertTrue(!tradIterator.hasNext());

            tx.success();
        }
    }


    /**
     * Test if there is the correct error when trying to change a tradition with an invalid userid
     */
	@Test
    public void changeMetadataOfATraditionTestWithWrongUser() {
        /* Preconditon
         * The user with id 1 has tradition
         */
        ClientResponse jerseyResult = jerseyTest.resource().path("/user/1/traditions").get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        List<TraditionModel> tradList = jerseyResult.getEntity(new GenericType<List<TraditionModel>>() {});
        assertEquals(1, tradList.size());
        assertEquals(tradId, tradList.get(0).getId());

        /*
         * Change the owner of the tradition
         */
        TraditionModel textInfo = new TraditionModel();
        textInfo.setName("RenamedTraditionName");
        textInfo.setLanguage("nital");
        textInfo.setIs_public(false);
        textInfo.setOwner("1337");

        ClientResponse removalResponse = jerseyTest.resource()
                .path("/tradition/" + tradId)
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, textInfo);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), removalResponse.getStatus());
        assertEquals("A user with this id does not exist", Util.getValueFromJson(removalResponse, "error"));

        /* PostCondition
         * The user with id 1 has still tradition
         */
        jerseyResult = jerseyTest.resource().path("/user/1/traditions").get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        tradList = jerseyResult.getEntity(new GenericType<List<TraditionModel>>() {});
        assertEquals(1, tradList.size());
        assertEquals(tradId, tradList.get(0).getId());
        assertEquals("Tradition", tradList.get(0).getName());

    }

    /**
     * Test if it is posibible to change the user of a Tradition with invalid traditionid
     */
    @Test
    public void changeMetadataOfATraditionTestWithInvalidTradid() {

        /*
         * Create a second user with id 42
         */
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (n:ROOT) return n");
            Iterator<Node> nodes = result.columnAs("n");
            Node rootNode = nodes.next();

            Node node = db.createNode(Nodes.USER);
            node.setProperty("id", "42");
            node.setProperty("role", "admin");

            rootNode.createRelationshipTo(node, ERelations.SYSTEMUSER);
            tx.success();
        }

        /*
         * The user with id 42 has no tradition
         */
        ClientResponse jerseyResult = jerseyTest.resource().path("/user/42/traditions").get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        assertEquals(0, jerseyResult.getEntity(new GenericType<List<TraditionModel>>() {}).size());

        /*
         * The user with id 1 has tradition
         */
        jerseyResult = jerseyTest.resource().path("/user/1/traditions").get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        List<TraditionModel> tradList = jerseyResult.getEntity(new GenericType<List<TraditionModel>>() {});
        assertEquals(1, tradList.size());
        assertEquals(tradId, tradList.get(0).getId());

        /*
         * Change the owner of the tradition
         */
        TraditionModel textInfo = new TraditionModel();
        textInfo.setName("RenamedTraditionName");
        textInfo.setLanguage("nital");
        textInfo.setIs_public(false);
        textInfo.setOwner("42");

        ClientResponse removalResponse = jerseyTest
                .resource()
                .path("/tradition/1337")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, textInfo);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), removalResponse.getStatus());
        assertEquals("There is no Tradition with this id", Util.getValueFromJson(removalResponse, "error"));

        /*
         * Post condition nothing has changed
         *
         */

        /*
         * Test if user with id 1 has still the old tradition
         */
        jerseyResult = jerseyTest.resource().path("/user/1/traditions").get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        tradList = jerseyResult.getEntity(new GenericType<List<TraditionModel>>() {});
        assertEquals(1, tradList.size());
        assertEquals(tradId, tradList.get(0).getId());
        assertEquals("Tradition", tradList.get(0).getName());

        /*
         * The user with id 42 has still no tradition
         */
        jerseyResult = jerseyTest.resource().path("/user/42/traditions").get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        assertEquals(0, jerseyResult.getEntity(new GenericType<List<TraditionModel>>() {}).size());
    }

    /**
     * Remove a complete Tradition
     */
    @Test
    public void deleteTraditionByIdTest() {
        ClientResponse removalResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId)
                .type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), removalResponse.getStatus());


        Node startNode = DatabaseService.getStartNode(tradId, db);

        assertNull(startNode);
    }

    /**
     * Test that all the nodes of a tradition have been removed
     */
    @Test
    public void deleteTraditionCompletelyTest() {
        // create a new user
        UserModel userModel = new UserModel();
        userModel.setId("user@example.org");
        userModel.setRole("user");
        ClientResponse jerseyResponse = jerseyTest.resource()
                .path("/user/user@example.org")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, userModel);
        assertEquals(Status.CREATED.getStatusCode(), jerseyResponse.getStatus());

        // count the total number of nodes
        AtomicInteger numNodes = new AtomicInteger(0);
        try (Transaction tx = db.beginTx()) {
            db.execute("match (n) return n").forEachRemaining(x -> numNodes.getAndIncrement());
            tx.success();
        }
        int originalNodeCount = numNodes.get();

        // upload the florilegium
        String testfile = "src/TestFiles/florilegium_graphml.xml";
        String florId = createTraditionFromFile("Florilegium", testfile, userModel.getId());

        // give it a stemma
        String newStemma = null;
        try {
            byte[] encStemma = Files.readAllBytes(Paths.get("src/TestFiles/florilegium.dot"));
            newStemma = new String(encStemma, Charset.forName("utf-8"));
        } catch (IOException e) {
            fail();
        }
        jerseyResponse = jerseyTest
                .resource()
                .path("/tradition/" + florId + "/stemma")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newStemma);
        assertEquals(ClientResponse.Status.CREATED.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());

        // re-root the stemma
        jerseyResponse = jerseyTest
                .resource()
                .path("/tradition/" + florId + "/stemma/Stemma/reorient/2")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newStemma);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());

        // give it some relationships - rank 37, rank 13, ranks 217/219
        try (Transaction tx = db.beginTx()) {
            int[] alignRanks = {77, 110};
            for (int r : alignRanks) {
                ResourceIterator<Node> atRank = db.findNodes(Nodes.READING, "rank", r);
                assertTrue(atRank.hasNext());
                ReadingModel rdg1 = new ReadingModel(atRank.next());
                ReadingModel rdg2 = new ReadingModel(atRank.next());
                RelationModel rel = new RelationModel();
                rel.setType("grammatical");
                rel.setScope("local");
                rel.setSource(rdg1.getId());
                rel.setTarget(rdg2.getId());
                jerseyResponse = jerseyTest.resource()
                        .path("/tradition/" + florId + "/relation")
                        .type(MediaType.APPLICATION_JSON)
                        .post(ClientResponse.class, rel);
                assertEquals(ClientResponse.Status.CREATED.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());
            }

            // and a transposition, for kicks
            Node tx1 = db.findNode(Nodes.READING, "rank", 217);
            Node tx2 = db.findNode(Nodes.READING, "rank", 219);
            RelationModel txrel = new RelationModel();
            txrel.setType("transposition");
            txrel.setScope("local");
            txrel.setSource(String.valueOf(tx1.getId()));
            txrel.setTarget(String.valueOf(tx2.getId()));
            jerseyResponse = jerseyTest.resource()
                    .path("/tradition/" + florId + "/relation")
                    .type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, txrel);
            assertEquals(ClientResponse.Status.CREATED.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());
            tx.success();
        }

        // now count the nodes
        numNodes.set(0);
        try (Transaction tx = db.beginTx()) {
            db.execute("match (n) return n").forEachRemaining(x -> numNodes.getAndIncrement());
            tx.success();
        }
        assertTrue(numNodes.get() > originalNodeCount + 200);

        // delete the florilegium
        jerseyResponse = jerseyTest
                .resource()
                .path("/tradition/" + florId)
                .type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());

        // nodes should be back to original number
        numNodes.set(0);
        try (Transaction tx = db.beginTx()) {
            db.execute("match (n) return n").forEachRemaining(x -> numNodes.getAndIncrement());
            tx.success();
        }
        assertEquals(originalNodeCount, numNodes.get());
    }

    /**
     * Test do delete a Tradition with an invalid id deletTraditionById
     */
    @Test
    public void deleteATraditionWithInvalidIdTest() {
        try (Transaction tx = db.beginTx()) {
            /*
             * Try to remove a tradition with invalid id
             */
            ClientResponse removalResponse = jerseyTest
                    .resource()
                    .path("/tradition/1337")
                    .delete(ClientResponse.class);
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), removalResponse.getStatus());

            /*
             * Test if user 1 still exists
             */
            Result result = db.execute("match (userId:USER {id:'1'}) return userId");
            Iterator<Node> nodes = result.columnAs("userId");
            assertTrue(nodes.hasNext());

            /*
             * Check if tradition {tradId} still exists
             */
            result = db.execute("match (t:TRADITION {id:'" + tradId + "'}) return t");
            nodes = result.columnAs("t");
            assertTrue(nodes.hasNext());
            tx.success();
        }
    }

    /*
     * Shut down the jersey server
     *
     * @throws Exception
     */
    @After
    public void tearDown() throws Exception {
        jerseyTest.tearDown();
        db.shutdown();
    }
}
