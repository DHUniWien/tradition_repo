package net.stemmaweb.stemmaserver.integrationtests;

import junit.framework.TestCase;
import net.stemmaweb.model.*;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.VariantGraphService;
import net.stemmaweb.stemmaserver.Util;

import org.glassfish.jersey.test.JerseyTest;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.ws.rs.core.Response;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Tests for our own input/output formats.
 * Created by tla on 17/02/2017.
 */
public class GraphMLInputOutputTest extends TestCase {

    private GraphDatabaseService db;
    private JerseyTest jerseyTest;
    private String tradId;
    private String multiTradId;

    public void setUp() throws Exception {
        super.setUp();
        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        Util.setupTestDB(db, "me@example.org");

        // Create a JerseyTestServer for the necessary REST API calls
        jerseyTest = Util.setupJersey();

        Response r = Util.createTraditionFromFileOrString(jerseyTest, "Tradition",
                    "BI", "me@example.org", "src/TestFiles/testTradition.xml", "stemmaweb");
        tradId = Util.getValueFromJson(r, "tradId");

        r = Util.createTraditionFromFileOrString(jerseyTest, "Multi-section tradition",
                "LR", "me@example.org", "src/TestFiles/legendfrag.xml", "stemmaweb");
        multiTradId = Util.getValueFromJson(r, "tradId");
        Util.addSectionToTradition(jerseyTest, multiTradId, "src/TestFiles/lf2.xml",
                "stemmaweb", "section 2");

    }

    public void testXMLOutput() {
        // Just request the tradition and make sure that we get XML back out
        Response r = jerseyTest.target("/tradition/" + tradId + "/graphml")
                .request(MediaType.APPLICATION_XML_TYPE).get();
        assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
        assertTrue(r.readEntity(String.class)
                .contains("<key attr.name=\"neolabel\" attr.type=\"string\" for=\"node\" id=\"dn0\"/>"));
    }

    public void testXMLInputExistingTradition() {
        Response r = jerseyTest.target("/tradition/" + tradId + "/graphml")
                .request(MediaType.APPLICATION_XML_TYPE).get();
        String graphML = r.readEntity(String.class);

        r = Util.createTraditionFromFileOrString(jerseyTest, "New-name tradition", "LR",
                "me@example.org", graphML, "graphml");
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        assertNotEquals(tradId, Util.getValueFromJson(r, "tradId"));
    }

    public void testXMLInput() {
        // Now we have to be able to parse back in what we spat out.
        Response r = jerseyTest.target("/tradition/" + tradId + "/graphml")
                .request(MediaType.APPLICATION_XML_TYPE).get();
        String graphML = r.readEntity(String.class);

        r = jerseyTest.target("/tradition/" + tradId).request().delete();
        assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
        r = Util.createTraditionFromFileOrString(jerseyTest, "New-name tradition", "LR",
                "me@example.org", graphML, "graphml");
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());

        // Does it have the right tradition ID?
        assertEquals(tradId, Util.getValueFromJson(r, "tradId"));

        // Did it ignore the passed tradition attributes in favor of the XML ones?
        TraditionModel t = jerseyTest.target("/tradition/" + tradId).request().get(TraditionModel.class);
        assertEquals("BI", t.getDirection());
        assertEquals("Tradition", t.getName());

        // Does the tradition have the right number of sections?
        ArrayList<SectionModel> s = jerseyTest.target("/tradition/" + tradId + "/sections")
                .request()
                .get(new GenericType<ArrayList<SectionModel>>() {});
        assertEquals(1, s.size());
        assertEquals("DEFAULT", s.get(0).getName());

        // Does the tradition have the right number of readings?
        ArrayList<ReadingModel> rdgs = jerseyTest.target("/tradition/" + tradId + "/readings")
                .request()
                .get(new GenericType<ArrayList<ReadingModel>>(){});
        assertEquals(26, rdgs.size());

        // Do the readings claim to belong to the new sections and not the old?
        Set<String> sections = jerseyTest.target("/tradition/" + tradId + "/sections")
                .request()
                .get(new GenericType<List<SectionModel>>() {})
                .stream().map(SectionModel::getId).collect(Collectors.toSet());
        try (Transaction tx = db.beginTx()) {
            List<Node> ourReadings = VariantGraphService.returnEntireTradition(tradId, db).nodes().stream()
                    .filter(x -> x.hasLabel(Nodes.READING)).collect(Collectors.toList());
            for (Node rdg : ourReadings)
                assertTrue(sections.contains(rdg.getProperty("section_id").toString()));
            tx.success();
        }

        // Do the witness texts match?
        ArrayList<WitnessModel> wits = jerseyTest.target("/tradition/" + tradId + "/witnesses")
                .request()
                .get(new GenericType<ArrayList<WitnessModel>>(){});
        assertEquals(3, wits.size());
        String textA = "when april with his showers sweet with fruit the drought of march has pierced unto the root";
        String textB = "when showers sweet with april fruit the march of drought has pierced to the root";
        String textC = "when showers sweet with april fruit teh drought of march has pierced teh rood";
        String restPath = "/tradition/" + tradId + "/witness/%s/text";
        assertEquals(textA, Util.getValueFromJson(jerseyTest.target(String.format(restPath, "A")).request().get(), "text"));
        assertEquals(textB, Util.getValueFromJson(jerseyTest.target(String.format(restPath, "B")).request().get(), "text"));
        assertEquals(textC, Util.getValueFromJson(jerseyTest.target().path(String.format(restPath, "C")).request().get(), "text"));

        // Do the stemmas match?
        String directedStemma = "digraph \"stemma\" {  0 [ class=hypothetical ];  A [ class=extant ];  " +
                "B [ class=extant ];  C [ class=extant ]; 0 -> A;  0 -> B;  A -> C;}";
        String undirectedStemma = "graph \"Semstem 1402333041_0\" {  0 [ class=hypothetical ];  A [ class=extant ];  " +
                "B [ class=extant ];  C [ class=extant ]; 0 -- A;  A -- B;  B -- C;}";

        ArrayList<StemmaModel> stemmata = jerseyTest.target("/tradition/" + tradId + "/stemmata")
                .request()
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

    public void testXMLOutputSingleSection() {
        // Start with a multi-section tradition and get the section IDs
        List<SectionModel> sections = jerseyTest.target("/tradition/" + multiTradId + "/sections")
                .request(MediaType.APPLICATION_JSON_TYPE).get(new GenericType<List<SectionModel>>() {});
        // See how many witnesses we have to begin with
        List<WitnessModel> witnesses = jerseyTest.target("/tradition/" + multiTradId + "/witnesses")
                .request().get(new GenericType<List<WitnessModel>>() {});
        assertEquals(37, witnesses.size());
        // Get the GraphML output, make sure it has correct # of nodes & edges
        Response r = jerseyTest.target("/tradition/" + multiTradId + "/section/"
                + sections.get(0).getId() + "/graphml")
                .request(MediaType.APPLICATION_XML_TYPE).get();
        assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
        String xmlresp = r.readEntity(String.class);
        assertTrue(xmlresp.contains("<key attr.name=\"neolabel\" attr.type=\"string\" for=\"node\" id=\"dn0\"/>"));

        InputSource is = new InputSource();
        is.setCharacterStream(new StringReader(xmlresp));
        Document graphdoc = null;
        try {
            graphdoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
        } catch (Exception e) {
            fail();
        }
        assertNotNull(graphdoc);
        NodeList nodes = graphdoc.getElementsByTagName("node");
        assertEquals(31, nodes.getLength());

        // Get the GraphML output with witnesses included, make sure it is correct
        xmlresp = jerseyTest.target("/tradition/" + multiTradId + "/section/"
                + sections.get(1).getId() + "/graphml")
                .queryParam("include_witnesses", "true")
                .request(MediaType.APPLICATION_XML_TYPE).get(String.class);

        is = new InputSource();
        is.setCharacterStream(new StringReader(xmlresp));
        graphdoc = null;
        try {
            graphdoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
        } catch (Exception e) {
            fail();
        }
        assertNotNull(graphdoc);
        nodes = graphdoc.getElementsByTagName("node");
        assertEquals(82, nodes.getLength());

        // Now add this section again to the tradition and make sure the witnesses aren't doubled
        Util.addSectionToTradition(jerseyTest, multiTradId,
                xmlresp, "graphml", "Section 3");
        sections = jerseyTest.target("/tradition/" + multiTradId + "/sections")
                .request().get(new GenericType<List<SectionModel>>() {});
        assertEquals(3, sections.size());
        witnesses = jerseyTest.target("/tradition/" + multiTradId + "/witnesses")
                .request().get(new GenericType<List<WitnessModel>>() {});
        assertEquals(37, witnesses.size());
    }

    public void testXMLInputCreateFromSection() {
        List<SectionModel> ms = jerseyTest.target("/tradition/" + multiTradId + "/sections")
                .request()
                .get(new GenericType<List<SectionModel>>() {});
        String sectxml = jerseyTest
                .target("/tradition/" + multiTradId + "/section/" + ms.get(1).getId() + "/graphml")
                .request()
                .get(String.class);
        assertTrue(sectxml.contains("graphdrawing"));

        // Try to create a new tradition with only a section download
        Response r = Util.createTraditionFromFileOrString(jerseyTest, "legend", "LR",
                "me@example.org", sectxml, "graphml");
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        String legendId = Util.getValueFromJson(r, "tradId");
        String aWitnessText = Util.getValueFromJson(jerseyTest.target("/tradition/" + legendId + "/witness/B/text")
                .request()
                .get(), "text");
        assertEquals("quasi duobus magnis luminaribus populus terre illius ad veri dei noticiam & cultum magisque illustrabatur iugiter ac informabatur Sanctus autem", aWitnessText);
        WitnessModel aWitness = jerseyTest.target("/tradition/" + legendId + "/witness/B").request().get(WitnessModel.class);
        assertEquals("B", aWitness.getSigil());

        // Do the readings have the right section ID set?
        Set<String> newSections = jerseyTest.target("/tradition/" + legendId + "/sections")
                .request()
                .get(new GenericType<List<SectionModel>>() {})
                .stream().map(SectionModel::getId).collect(Collectors.toSet());
        try (Transaction tx = db.beginTx()) {
            List<Node> ourReadings = VariantGraphService.returnEntireTradition(legendId, db).nodes().stream()
                    .filter(x -> x.hasLabel(Nodes.READING)).collect(Collectors.toList());
            for (Node rdg : ourReadings)
                assertTrue(newSections.contains(rdg.getProperty("section_id").toString()));
            tx.success();
        }

        // Does the tradition have any relation types defined?
        List<RelationTypeModel> rtypes = jerseyTest.target("/tradition/" + legendId + "/relationtypes")
                .request()
                .get(new GenericType<List<RelationTypeModel>>() {});
        assertEquals(3, rtypes.size());
        List<String> rnames = rtypes.stream().map(RelationTypeModel::getName).collect(Collectors.toList());
        assertTrue(rnames.contains("collated"));
        assertTrue(rnames.contains("orthographic"));
        assertTrue(rnames.contains("spelling"));
    }

    public void testXMLInputNewSectionWitnesses() {
        String florId = Util.getValueFromJson(Util.createTraditionFromFileOrString(jerseyTest, "Florilegium",
                "LR", "me@example.org", "src/TestFiles/florilegium_z.csv", "csv"), "tradId");
        // Get the single-section XML
        String chryxml = jerseyTest.target("/tradition/" + florId + "/graphml").request().get(String.class);
        assertTrue(chryxml.contains("graphdrawing"));

        // Add a second section so we can export it
        String florSect2 = Util.getValueFromJson(Util.addSectionToTradition(jerseyTest, florId,
                "src/TestFiles/florilegium_y.csv", "csv", "Chrysostom"), "parentId");
        String womenxml = jerseyTest.target("/tradition/" + florId + "/section/" + florSect2 + "/graphml")
                .request()
                .get(String.class);
        assertTrue(womenxml.contains("graphdrawing"));

        // Check that all our witnesses are there
        List<WitnessModel> wits = jerseyTest.target("/tradition/" + florId + "/witnesses")
                .request()
                .get(new GenericType<List<WitnessModel>>() {});
        assertEquals(13, wits.size());

        // Now make a new tradition with the GraphML
        String flor2Id = Util.getValueFromJson(Util.createTraditionFromFileOrString(jerseyTest, "Floritwo",
                "LR", "me@example.org", chryxml, "graphml"), "tradId");
        // Count the witnesses
        wits = jerseyTest.target("/tradition/" + flor2Id + "/witnesses")
                .request()
                .get(new GenericType<List<WitnessModel>>() {});
        assertEquals(5, wits.size());
        // Add the second section
        Response r = Util.addSectionToTradition(jerseyTest, flor2Id, womenxml, "graphml", "Appearance of women");
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        // Count the witnesses
        wits = jerseyTest.target("/tradition/" + flor2Id + "/witnesses")
                .request()
                .get(new GenericType<List<WitnessModel>>() {});
        assertEquals(13, wits.size());
    }

    // testXMLUserNodes

    // testXMLDataTypes

    // testXMLTooManySections

    // testXMLNoSections

    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
        super.tearDown();
    }
}
