package net.stemmaweb.stemmaserver.integrationtests;

import junit.framework.TestCase;
import net.stemmaweb.model.*;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.ReadingService;
import net.stemmaweb.services.VariantGraphService;
import net.stemmaweb.stemmaserver.Util;

import org.glassfish.jersey.test.JerseyTest;
import org.neo4j.graphdb.*;
import org.neo4j.test.TestGraphDatabaseFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.ws.rs.client.Entity;
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
            net.stemmaweb.parser.Util.cleanupExtractedZip(lm);
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

    public void testWrongDataForUpload() {
        // Try to add a JSON section to an existing tradition but claim it is GraphML zip
        Response r = Util.addSectionToTradition(jerseyTest, multiTradId, "src/TestFiles/Matthew-418.json",
                "graphml", "bad section");
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        try (Transaction tx = db.beginTx()) {
            Node n = db.findNode(Nodes.TRADITION, "id", multiTradId);
            assertNotNull(n);
            List<Node> sections = new ArrayList<>();
            n.getRelationships(ERelations.PART, Direction.OUTGOING).forEach(x -> sections.add(x.getEndNode()));
            assertEquals(2, sections.size());
            tx.success();
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
            net.stemmaweb.parser.Util.cleanupExtractedZip(xmlFiles);
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

    public void testZipOutputAndInputWithAnnotations() {
        // Create the tradition

        // Set its annotation structure

        // Input the annotations

        // Export it to a zipfile

        // Reimport it; all annotations should be there

        // Add another section

        // Export the single section

        // Reimport it; the annotation label structure should not have changed

        // All the above is the full test; here below is the quick and dirty version
        Response r = Util.createTraditionFromFileOrString(jerseyTest, "Matthew 401", "LR",
                "me@example.org", "src/TestFiles/m401-annotated.zip", "graphml");
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        String mattId = Util.getValueFromJson(r, "tradId");
        SectionModel section = Util.getSingleSection(jerseyTest, mattId);
        List<AnnotationModel> annos = jerseyTest.target("/tradition/" + mattId + "/annotations")
                .request().get(new GenericType<>() {});
        // Check that all annotations are there.
        // BUG: the PERSON/PLACE/DATE annotations did not make it into the test data export.
        // This needs to be explicitly tested for in a more formal manner above.
        assertEquals(24, annos.size());
        assertEquals(14, annos.stream().filter(x -> x.getLabel().equals("TRANSLATION")).count());
        assertEquals(2, annos.stream().filter(x -> x.getLabel().equals("TITLE")).count());
        assertEquals(2, annos.stream().filter(x -> x.getLabel().equals("DATEREF")).count());
        assertEquals(2, annos.stream().filter(x -> x.getLabel().equals("DATING")).count());
        // assertEquals(2, annos.stream().filter(x -> x.getLabel().equals("DATE")).count());
        assertEquals(2, annos.stream().filter(x -> x.getLabel().equals("PERSONREF")).count());
        // assertEquals(1, annos.stream().filter(x -> x.getLabel().equals("PERSON")).count());
        assertEquals(2, annos.stream().filter(x -> x.getLabel().equals("PLACEREF")).count());
        // assertEquals(2, annos.stream().filter(x -> x.getLabel().equals("PLACE")).count());
        // Get our section ID

        // Spot-check that something is linked correctly
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

        // Add a second section to this tradition
        r = Util.addSectionToTradition(jerseyTest, mattId, "src/TestFiles/milestone-591.zip",
                "graphml", "section 591");
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        String s591 = Util.getValueFromJson(r, "sectionId");
        assertNotNull(s591);
        // There should still be the same number of annotation labels, properties, and links in the tradition
        List<AnnotationLabelModel> tradAnnoTypes = jerseyTest.target("/tradition/" + mattId + "/annotationlabels")
                .request().get(new GenericType<>() {});
        assertEquals(13, tradAnnoTypes.size());
        List<AnnotationModel> sectAnnos = jerseyTest.target("/tradition/" + mattId + "/section/" + s591 + "/annotations")
                .request().get(new GenericType<>() {});
        assertEquals(9, sectAnnos.size());
    }

    public void testZipExportAnnotationsAcrossSection() {
        HashMap<String,String> readingLookup = Util.makeReadingLookup(jerseyTest, multiTradId);
        // Write the annotation schema
        AnnotationLabelModel placerefAlm = new AnnotationLabelModel();
        placerefAlm.setName("PLACEREF");
        placerefAlm.addLink("READING", "BEGIN,END");
        placerefAlm.addProperty("authority", "String");
        Response r = jerseyTest.target("/tradition/" + multiTradId + "/annotationlabel/PLACEREF")
                .request().put(Entity.json(placerefAlm));
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        AnnotationLabelModel placeAlm = new AnnotationLabelModel();
        placeAlm.setName("PLACE");
        placeAlm.addProperty("name", "String");
        placeAlm.addLink("PLACEREF", "REFERENCED");
        r = jerseyTest.target("/tradition/" + multiTradId + "/annotationlabel/PLACE")
                .request().put(Entity.json(placeAlm));
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());

        // Add the first place reference "swecia"
        AnnotationModel sect1ref = new AnnotationModel();
        sect1ref.setLabel("PLACEREF");
        sect1ref.addProperty("authority", "tla");
        AnnotationLinkModel s1b = new AnnotationLinkModel();
        s1b.setTarget(Long.parseLong(readingLookup.get("swecia/2")));
        s1b.setType("BEGIN");
        sect1ref.addLink(s1b);
        AnnotationLinkModel s1e = new AnnotationLinkModel();
        s1e.setTarget(Long.parseLong(readingLookup.get("swecia/2")));
        s1e.setType("END");
        sect1ref.addLink(s1e);
        r = jerseyTest.target("/tradition/" + multiTradId + "/annotation/").request().post(Entity.json(sect1ref));
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        sect1ref = r.readEntity(AnnotationModel.class);

        // Add the second place reference ("terre illius")
        AnnotationModel sect2ref = new AnnotationModel();
        sect2ref.setLabel("PLACEREF");
        sect2ref.addProperty("authority", "tla");
        AnnotationLinkModel s2b = new AnnotationLinkModel();
        s2b.setTarget(Long.parseLong(readingLookup.get("terre/6")));
        s2b.setType("BEGIN");
        sect2ref.addLink(s2b);
        AnnotationLinkModel s2e = new AnnotationLinkModel();
        s2e.setTarget(Long.parseLong(readingLookup.get("illius/7")));
        s2e.setType("END");
        sect2ref.addLink(s2e);
        r = jerseyTest.target("/tradition/" + multiTradId + "/annotation").request().post(Entity.json(sect2ref));
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        sect2ref = r.readEntity(AnnotationModel.class);

        // Add the place to which these refer
        AnnotationModel thePlace = new AnnotationModel();
        thePlace.setLabel("PLACE");
        thePlace.addProperty("name", "Sweden");
        AnnotationLinkModel r1 = new AnnotationLinkModel();
        r1.setTarget(Long.parseLong(sect1ref.getId()));
        r1.setType("REFERENCED");
        thePlace.addLink(r1);
        AnnotationLinkModel r2 = new AnnotationLinkModel();
        r2.setTarget(Long.parseLong(sect2ref.getId()));
        r2.setType("REFERENCED");
        thePlace.addLink(r2);
        r = jerseyTest.target("/tradition/" + multiTradId + "/annotation").request().post(Entity.json(thePlace));
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        thePlace = r.readEntity(AnnotationModel.class);

        // Export the zip file
        r = jerseyTest.target("/tradition/" + multiTradId + "/graphml").request().get();
        assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
        String ourZip = Util.saveGraphMLTempfile(r);

        // ...and try reimporting it
        r = Util.createTraditionFromFileOrString(jerseyTest, "new Legend", "LR", "me@example.org", ourZip, "graphml");
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        String newTradId = Util.getValueFromJson(r, "tradId");

        // Ask for section annotations and make sure they are correct
        List<SectionModel> ourSections = jerseyTest.target("/tradition/" + newTradId + "/sections")
                .request().get(new GenericType<>() {});
        assertEquals(2, ourSections.size());
        List<AnnotationModel> allAnnos = jerseyTest.target("/tradition/" + newTradId + "/annotations")
                .request().get(new GenericType<>() {});
        assertEquals(3, allAnnos.size());
        List<AnnotationModel> placeAnnos = jerseyTest.target("/tradition/" + newTradId + "/annotations")
                .queryParam("label", "PLACE")
                .request().get(new GenericType<>() {});
        assertEquals(1, placeAnnos.size());
        assertEquals(thePlace.getLabel(), placeAnnos.get(0).getLabel());
        assertEquals(thePlace.getProperties().get("name"), placeAnnos.get(0).getProperties().get("name"));
        List<AnnotationModel> s1Annos = jerseyTest
                .target("/tradition/" + newTradId + "/section/" + ourSections.get(0).getId() + "/annotations")
                .request().get(new GenericType<>() {});
        assertEquals(1, s1Annos.size());
        List<AnnotationModel> s2Annos = jerseyTest
                .target("/tradition/" + newTradId + "/section/" + ourSections.get(1).getId() + "/annotations")
                .request().get(new GenericType<>() {});
        assertEquals(1, s2Annos.size());

    }

    public void testZipBadSectionInput() {
        Response r = Util.createTraditionFromFileOrString(jerseyTest, "Matthew 401", "LR",
                "me@example.org", "src/TestFiles/m401-annotated.zip", "graphml");
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        String mattId = Util.getValueFromJson(r, "tradId");

        List<SectionModel> sections = jerseyTest.target("/tradition/" + mattId + "/sections/")
                .request().get(new GenericType<>() {});
        assertEquals(1, sections.size());

        // Try to add a file that has been unzipped and rezipped by the user. It should fail
        r = Util.addSectionToTradition(jerseyTest, mattId, "src/TestFiles/milestone-591-BAD.zip",
                "graphml", "591");
        // This should really be BAD REQUEST, but let's see if we can reproduce the problem
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());

        // Try to request the list of sections again. This should succeed
        r = jerseyTest.target("/tradition/" + mattId + "/sections/")
                .request().get();
        assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
        sections = r.readEntity(new GenericType<>() {});
        assertEquals(1, sections.size());
    }

/*
    public void testZipArbitraryTradition() {
        // Import the given tradition file and check that it works
        String fn = "src/TestFiles/4aaf8973-7ac9-402a-8df9-19a2a050e364.zip";
        Response r = Util.createTraditionFromFileOrString(jerseyTest, "Arbitrary tradition", "BI",
                "me@example.org", fn, "graphml");
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        String newTradId = Util.getValueFromJson(r, "tradId");
        assertNotNull(newTradId);

        // See if we still have our duplicated annotation labels
        List<AnnotationLabelModel> annoTypes = jerseyTest.target("/tradition/" + newTradId + "/annotationlabels")
                .request().get(new GenericType<>() {});
        assertEquals(13, annoTypes.size());

        // Check that the tradition has the right number of chapter links
        HashMap<String,Integer> chapterDivs = new HashMap<>();
        chapterDivs.put("Book One", 25);
        chapterDivs.put("Book Two", 15);
        chapterDivs.put("Book Three", 14);
        chapterDivs.put("Continuation", 23);

        List<AnnotationModel> ch = jerseyTest.target("/tradition/" + newTradId + "/annotations")
                .queryParam("label", "CHAPTER")
                .request().get(new GenericType<>() {});
        assertEquals(4, ch.size());
        for (AnnotationModel am : ch) {
            String chtitle = am.getProperties().get("title").toString();
            assertEquals(chapterDivs.get(chtitle), Integer.valueOf(am.getLinks().size()));
        }

        // Check that the tradition has the right number of titles, two per section
        List<SectionModel> sectionModels = jerseyTest.target("/tradition/" + newTradId + "/sections")
                .request().get(new GenericType<>() {});
        assertEquals(77, sectionModels.size());
        List<AnnotationModel> titleModels = jerseyTest.target("/tradition/" + newTradId + "/annotations")
                .queryParam("label", "TITLE")
                .request().get(new GenericType<>() {});
        assertEquals(146, titleModels.size());
        for (SectionModel sm : sectionModels) {
            if (titleModels.stream().noneMatch(x -> x.getProperties().get("language").equals("hy")
                    && x.getLinks().get(0).getTarget().equals(Long.valueOf(sm.getId()))))
                System.out.println("No Armenian title found for section " + sm.getName());
            if (titleModels.stream().noneMatch(x -> x.getProperties().get("language").equals("en")
                    && x.getLinks().get(0).getTarget().equals(Long.valueOf(sm.getId()))))
                System.out.println("No English title found for section " + sm.getName());
        }

        // Check that the tradition has the right number of PLACEREFs
        List<AnnotationModel> placerefModels = jerseyTest.target("/tradition/" + newTradId + "/annotations")
                .queryParam("label", "PLACEREF")
                .request().get(new GenericType<>() {});
        assertEquals(148, placerefModels.size());
        // Check that the tradition has the right number of PLACEs
        List<AnnotationModel> placeModels = jerseyTest.target("/tradition/" + newTradId + "/annotations")
                .queryParam("label", "PLACE")
                .request().get(new GenericType<>() {});
        assertEquals(51, placeModels.size());
    }
*/

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
