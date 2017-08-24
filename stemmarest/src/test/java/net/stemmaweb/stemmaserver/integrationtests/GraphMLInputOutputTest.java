package net.stemmaweb.stemmaserver.integrationtests;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import junit.framework.TestCase;
import net.stemmaweb.model.*;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.Util;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.MediaType;
import java.util.ArrayList;

/**
 * Tests for our own input/output formats.
 * Created by tla on 17/02/2017.
 */
public class GraphMLInputOutputTest extends TestCase {

    private GraphDatabaseService db;
    private JerseyTest jerseyTest;
    private String tradId;

    public void setUp() throws Exception {
        super.setUp();
        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        Util.setupTestDB(db, "me@example.org");

        // Create a JerseyTestServer for the necessary REST API calls
        jerseyTest = Util.setupJersey();

        ClientResponse r = Util.createTraditionFromFileOrString(jerseyTest, "Tradition",
                    "BI", "me@example.org", "src/TestFiles/testTradition.xml", "stemmaweb");
        tradId = Util.getValueFromJson(r, "tradId");

    }

    public void testXMLOutput() throws Exception {
        // Just request the tradition and make sure that we get XML back out
        ClientResponse r = jerseyTest.resource().path("/tradition/" + tradId + "/graphml")
                .type(MediaType.APPLICATION_XML_TYPE).get(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), r.getStatus());
        assertTrue(r.getEntity(String.class)
                .contains("<key attr.name=\"neolabel\" attr.type=\"string\" for=\"node\" id=\"dn0\"/>"));
    }

    public void testXMLInputExistingTradition() throws Exception {
        ClientResponse r = jerseyTest.resource().path("/tradition/" + tradId + "/graphml")
                .type(MediaType.APPLICATION_XML_TYPE).get(ClientResponse.class);
        String graphML = r.getEntity(String.class);

        r = Util.createTraditionFromFileOrString(jerseyTest, "New-name tradition", "LR",
                "me@example.org", graphML, "graphml");
        assertEquals(ClientResponse.Status.CONFLICT.getStatusCode(), r.getStatus());
    }

    public void testXMLInput() throws Exception {
        // Now we have to be able to parse back in what we spat out.
        ClientResponse r = jerseyTest.resource().path("/tradition/" + tradId + "/graphml")
                .type(MediaType.APPLICATION_XML_TYPE).get(ClientResponse.class);
        String graphML = r.getEntity(String.class);

        r = jerseyTest.resource().path("/tradition/" + tradId).delete(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), r.getStatus());
        r = Util.createTraditionFromFileOrString(jerseyTest, "New-name tradition", "LR",
                "me@example.org", graphML, "graphml");
        assertEquals(ClientResponse.Status.CREATED.getStatusCode(), r.getStatus());

        // Does it have the right tradition ID?
        assertEquals(tradId, Util.getValueFromJson(r, "tradId"));

        // Did it ignore the passed tradition attributes in favor of the XML ones?
        TraditionModel t = jerseyTest.resource().path("/tradition/" + tradId).get(TraditionModel.class);
        assertEquals("BI", t.getDirection());
        assertEquals("Tradition", t.getName());

        // Does the tradition have the right number of sections?
        ArrayList<SectionModel> s = jerseyTest.resource().path("/tradition/" + tradId + "/sections")
                .get(new GenericType<ArrayList<SectionModel>>(){});
        assertEquals(1, s.size());
        assertEquals("DEFAULT", s.get(0).getName());

        // Does the tradition have the right number of readings?
        ArrayList<ReadingModel> rdgs = jerseyTest.resource().path("/tradition/" + tradId + "/readings")
                .get(new GenericType<ArrayList<ReadingModel>>(){});
        assertEquals(26, rdgs.size());

        // Do the witness texts match?
        ArrayList<WitnessModel> wits = jerseyTest.resource().path("/tradition/" + tradId + "/witnesses")
                .get(new GenericType<ArrayList<WitnessModel>>(){});
        assertEquals(3, wits.size());
        String textA = "when april with his showers sweet with fruit the drought of march has pierced unto the root";
        String textB = "when showers sweet with april fruit the march of drought has pierced to the root";
        String textC = "when showers sweet with april fruit teh drought of march has pierced teh rood";
        String restPath = "/tradition/" + tradId + "/witness/%s/text";
        assertEquals(textA, Util.getValueFromJson(jerseyTest.resource().path(String.format(restPath, "A")).get(ClientResponse.class), "text"));
        assertEquals(textB, Util.getValueFromJson(jerseyTest.resource().path(String.format(restPath, "B")).get(ClientResponse.class), "text"));
        assertEquals(textC, Util.getValueFromJson(jerseyTest.resource().path(String.format(restPath, "C")).get(ClientResponse.class), "text"));

        // Do the stemmas match?
        String directedStemma = "digraph \"stemma\" {  0 [ class=hypothetical ];  A [ class=extant ];  " +
                "B [ class=extant ];  C [ class=extant ]; 0 -> A;  0 -> B;  A -> C;}";
        String undirectedStemma = "graph \"Semstem 1402333041_0\" {  0 [ class=hypothetical ];  A [ class=extant ];  " +
                "B [ class=extant ];  C [ class=extant ]; 0 -- A;  A -- B;  B -- C;}";

        ArrayList<StemmaModel> stemmata = jerseyTest.resource().path("/tradition/" + tradId + "/stemmata")
                .get(new GenericType<ArrayList<StemmaModel>>(){});
        assertEquals(2, stemmata.size());
        if (stemmata.get(0).getIs_undirected()) {
            Util.assertStemmasEquivalent(directedStemma, stemmata.get(1).getDot());
            Util.assertStemmasEquivalent(undirectedStemma, stemmata.get(0).getDot());
        } else {
            Util.assertStemmasEquivalent(directedStemma, stemmata.get(0).getDot());
            Util.assertStemmasEquivalent(undirectedStemma, stemmata.get(1).getDot());
        }
    }

    // testXMLUserNodes

    // testXMLDataTypes

    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
        super.tearDown();
    }
}
