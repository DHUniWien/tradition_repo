package net.stemmaweb.stemmaserver.integrationtests;

import junit.framework.TestCase;
import net.stemmaweb.model.*;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.ReadingService;
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
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

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

        // Try our own medicine
        r = Util.createTraditionFromFileOrString(jerseyTest, "Multi-section tradition",
                "LR", "me@example.org", "src/TestFiles/legendfrag.xml", "stemmaweb");
        multiTradId = Util.getValueFromJson(r, "tradId");
        Util.addSectionToTradition(jerseyTest, multiTradId, "src/TestFiles/lf2.xml",
                "stemmaweb", "section 2");

    }

    public void testZipOutput() {
        // Just request the tradition and make sure that we get a zip file back out
        Response r = jerseyTest.target("/tradition/" + tradId + "/graphml")
                .request("application/zip").get();
        assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
        try {
            LinkedHashMap<String,File> lm = net.stemmaweb.parser.Util.extractGraphMLZip(r.readEntity(InputStream.class));
            for (File f : lm.values()) {
                assertTrue(new String(Files.readAllBytes(f.toPath()))
                        .contains("<key attr.name=\"neolabel\" attr.type=\"string\" for=\"node\" id=\"dn0\"/>"));
            }
        } catch (Exception e) {
            fail();
        }
    }

    public void testZipInputExistingTradition() {
        Response r = jerseyTest.target("/tradition/" + tradId + "/graphml")
                .request("application/zip").get();
        // Write it to a temp file
        String gmlZip = Util.saveGraphMLTempfile(r);
        assertNotNull(gmlZip);

        r = Util.createTraditionFromFileOrString(jerseyTest, "New-name tradition", "LR",
                "me@example.org", gmlZip, "graphml");
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        assertNotEquals(tradId, Util.getValueFromJson(r, "tradId"));
    }

    public void testZipInput() {
        // Make sure we can parse back in what we spat out.
        Response r = jerseyTest.target("/tradition/" + tradId + "/graphml")
                .request("application/zip").get();
        assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
        String inputFile = Util.saveGraphMLTempfile(r);

        r = jerseyTest.target("/tradition/" + tradId).request().delete();
        assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
        r = Util.createTraditionFromFileOrString(jerseyTest, "New-name tradition", "LR",
                "me@example.org", inputFile, "graphml");
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
                .get(new GenericType<>() {});
        assertEquals(1, s.size());
        assertEquals("DEFAULT", s.get(0).getName());

        // Does the tradition have the right number of readings?
        ArrayList<ReadingModel> rdgs = jerseyTest.target("/tradition/" + tradId + "/readings")
                .request()
                .get(new GenericType<>(){});
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
                .get(new GenericType<>(){});
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
                .get(new GenericType<>(){});
        assertEquals(2, stemmata.size());
        if (stemmata.get(0).getIs_undirected()) {
            Util.assertStemmasEquivalent(directedStemma, stemmata.get(1).getDot());
            Util.assertStemmasEquivalent(undirectedStemma, stemmata.get(0).getDot());
        } else {
            Util.assertStemmasEquivalent(directedStemma, stemmata.get(0).getDot());
            Util.assertStemmasEquivalent(undirectedStemma, stemmata.get(1).getDot());
        }
    }

    public void testZipOutputSingleSection() {
        // Start with a multi-section tradition and get the section IDs
        List<SectionModel> sections = jerseyTest.target("/tradition/" + multiTradId + "/sections")
                .request(MediaType.APPLICATION_JSON_TYPE).get(new GenericType<>() {});
        // See how many witnesses we have to begin with
        List<WitnessModel> witnesses = jerseyTest.target("/tradition/" + multiTradId + "/witnesses")
                .request().get(new GenericType<>() {});
        assertEquals(37, witnesses.size());
        // Get the GraphML output, make sure it has correct # of nodes & edges
        String targetSection = sections.get(0).getId();
        String sectFileName = "section-" + targetSection + ".xml";
        Response r = jerseyTest.target("/tradition/" + multiTradId + "/section/"
                + targetSection + "/graphml")
                .request("application/zip").get();
        assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
        LinkedHashMap<String,File> xmlFiles;
        try {
            xmlFiles = net.stemmaweb.parser.Util.extractGraphMLZip(r.readEntity(InputStream.class));
            // There should be a tradition file and a single section file
            assertEquals(2, xmlFiles.size());
            assertTrue(xmlFiles.containsKey("tradition.xml"));
            assertTrue(xmlFiles.containsKey(sectFileName));
            // The tradition file should contain the tradition node, three relation types, 37 witnesses and a section
            assertEquals(42, getXMLNodeList(xmlFiles.get("tradition.xml")).getLength());
            // The section file should contain a section and 30 readings
            assertEquals(31, getXMLNodeList(xmlFiles.get(sectFileName)).getLength());

        } catch (Exception e) {
            fail();
        }

        // Now add this section again to the tradition and make sure the witnesses aren't doubled
        /* Util.addSectionToTradition(jerseyTest, multiTradId,
                , "graphmlsingle", "Section 3");
        sections = jerseyTest.target("/tradition/" + multiTradId + "/sections")
                .request().get(new GenericType<>() {});
        assertEquals(3, sections.size());
        witnesses = jerseyTest.target("/tradition/" + multiTradId + "/witnesses")
                .request().get(new GenericType<>() {});
        assertEquals(37, witnesses.size()); */
    }

    private NodeList getXMLNodeList(File input) throws Exception {
        InputSource is = new InputSource();
        is.setCharacterStream(new FileReader(input));
        Document traddoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
        return traddoc.getElementsByTagName("node");
    }

    public void testZipInputCreateFromSection() {
        List<SectionModel> ms = jerseyTest.target("/tradition/" + multiTradId + "/sections")
                .request()
                .get(new GenericType<>() {});
        Response r = jerseyTest
                .target("/tradition/" + multiTradId + "/section/" + ms.get(1).getId() + "/graphml")
                .request().get();
        String sectxml = Util.saveGraphMLTempfile(r);

        // Try to create a new tradition with only a section download
        r = Util.createTraditionFromFileOrString(jerseyTest, "legend", "LR",
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
                .get(new GenericType<>() {});
        assertEquals(3, rtypes.size());
        List<String> rnames = rtypes.stream().map(RelationTypeModel::getName).collect(Collectors.toList());
        assertTrue(rnames.contains("collated"));
        assertTrue(rnames.contains("orthographic"));
        assertTrue(rnames.contains("spelling"));
    }

    public void testZipInputNewSectionWitnesses() {
        String florId = Util.getValueFromJson(Util.createTraditionFromFileOrString(jerseyTest, "Florilegium",
                "LR", "me@example.org", "src/TestFiles/florilegium_z.csv", "csv"), "tradId");
        // Get the single-section XML
        Response r = jerseyTest.target("/tradition/" + florId + "/graphml").request().get();
        String chryfile = Util.saveGraphMLTempfile(r);
        assertNotNull(chryfile);
        String chryxml = Util.getConcatenatedGraphML(chryfile);
        assertNotNull(chryxml);
        assertTrue(chryxml.contains("graphdrawing"));

        // Add a second section so we can export it
        String florSect2 = Util.getValueFromJson(Util.addSectionToTradition(jerseyTest, florId,
                "src/TestFiles/florilegium_y.csv", "csv", "Chrysostom"), "sectionId");
        r = jerseyTest.target("/tradition/" + florId + "/section/" + florSect2 + "/graphml")
                .request()
                .get();
        String womenfile = Util.saveGraphMLTempfile(r);
        assertNotNull(womenfile);
        String womenxml = Util.getConcatenatedGraphML(womenfile);
        assertNotNull(womenxml);
        assertTrue(womenxml.contains("graphdrawing"));

        // Check that all our witnesses are there
        List<WitnessModel> wits = jerseyTest.target("/tradition/" + florId + "/witnesses")
                .request()
                .get(new GenericType<>() {});
        assertEquals(13, wits.size());

        // Now make a new tradition with the GraphML
        String flor2Id = Util.getValueFromJson(Util.createTraditionFromFileOrString(jerseyTest, "Floritwo",
                "LR", "me@example.org", chryfile, "graphml"), "tradId");
        // Count the witnesses
        wits = jerseyTest.target("/tradition/" + flor2Id + "/witnesses")
                .request()
                .get(new GenericType<>() {});
        assertEquals(5, wits.size());
        // Add the second section
        r = Util.addSectionToTradition(jerseyTest, flor2Id, womenfile, "graphml", "Appearance of women");
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        // Count the witnesses
        wits = jerseyTest.target("/tradition/" + flor2Id + "/witnesses")
                .request()
                .get(new GenericType<>() {});
        assertEquals(13, wits.size());
    }

    public void testZipInputWithAnnotations() {
        Response r = Util.createTraditionFromFileOrString(jerseyTest, "Matthew 401", "LR",
                "me@example.org", "src/TestFiles/m401-annotated.zip", "graphml");
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        String mattId = Util.getValueFromJson(r, "tradId");
        SectionModel section = Util.getSingleSection(jerseyTest, mattId);
        List<AnnotationModel> annos = jerseyTest.target("/tradition/" + mattId + "/annotations")
                .request().get(new GenericType<>() {});
        // Check that all annotations are there
        assertEquals(24, annos.size());
        assertEquals(14, annos.stream().filter(x -> x.getLabel().equals("TRANSLATION")).count());
        assertEquals(2, annos.stream().filter(x -> x.getLabel().equals("DATEREF")).count());
        assertEquals(2, annos.stream().filter(x -> x.getLabel().equals("TITLE")).count());
        assertEquals(2, annos.stream().filter(x -> x.getLabel().equals("DATING")).count());
        assertEquals(2, annos.stream().filter(x -> x.getLabel().equals("PERSONREF")).count());
        assertEquals(2, annos.stream().filter(x -> x.getLabel().equals("PLACEREF")).count());
        // Get our section ID

        // Spot-check that a few are linked correctly
        Optional<AnnotationModel> testAnno = annos.stream().filter(x -> x.getLabel().equals("TRANSLATION")
                && x.getProperties().get("text").equals("And after 5 years locusts arose in that district, " +
                "like sands of the sea, and ruined the earth.")).findFirst();
        assertTrue(testAnno.isPresent());
        List<ReadingModel> sectionReadings = jerseyTest
                .target("/tradition/" + mattId + "/section/" + section.getId() + "/lemmareadings")
                .request().get(new GenericType<>() {});
        // Find the ranks of the start and finish of this translation
        assertEquals(2, testAnno.get().getLinks().size());
        Long startId = 0L;
        Long endId = 0L;
        for (AnnotationLinkModel lm : testAnno.get().getLinks()) {
            if (lm.getType().equals("BEGIN"))
                startId = lm.getTarget();
            else if (lm.getType().equals("END"))
                endId = lm.getTarget();
        }
        assertFalse(startId == 0L);
        assertFalse(endId == 0L);
        List<ReadingModel> translated = new ArrayList<>();
        boolean in_translation = false;
        for (ReadingModel rm : sectionReadings) {
            if (rm.getId().equals(String.valueOf(startId)))
                in_translation = true;
            if (in_translation) translated.add(rm);
            if (rm.getId().equals(String.valueOf(endId)))
                in_translation = false;
        }
        String expected = "և զկնի հինկ ամին եկեալ մարախ յայնմ գաւառին որպէս զաւազ ծովու և ապականեաց զերկիր.";
        String actual = ReadingService.textOfReadings(translated, true, false);
        assertEquals(expected, actual);
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
