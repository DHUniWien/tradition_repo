package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;

import net.stemmaweb.model.GraphModel;
import net.stemmaweb.model.ProposedEmendationModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.SectionModel;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Root;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;

public class DotOutputTest {

    private GraphDatabaseService db;
    private JerseyTest jerseyTest;
    private String tradId;
    private String msTradId;
	private DatabaseManagementService dbbuilder;

    @Before
    public void setUp() throws Exception {

//      db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
    	dbbuilder = new TestDatabaseManagementServiceBuilder().build();
    	dbbuilder.createDatabase("stemmatest");
    	db = dbbuilder.database("stemmatest");
        Util.setupTestDB(db, "user@example.com");

        // Create a JerseyTestServer for the necessary REST API calls

        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(Root.class)
                .create();
        jerseyTest.setUp();

        /*
         * create a tradition inside the test DB
         */
        tradId = Util.getValueFromJson(Util.createTraditionFromFileOrString(jerseyTest, "Tradition",
                "LR", "user@example.com", "src/TestFiles/testTradition.xml", "stemmaweb"),
                "tradId");
        msTradId = Util.getValueFromJson(Util.createTraditionFromFileOrString(jerseyTest, "Tradition",
                        "LR", "user@example.com", "src/TestFiles/legendfrag.xml", "stemmaweb"),
                "tradId");
    }

    @Test
    public void getDotOfNonExistentTraditionTest() {
        Response resp = jerseyTest
                .target("/tradition/10000/dot")
                .request()
                .get();

        assertEquals(Response.status(Response.Status.NOT_FOUND).build().getStatus(), resp.getStatus());
    }

    @Test
    public void getDotTest() {
        String str = jerseyTest
                .target("/tradition/" + tradId + "/dot")
                .request()
                .get(String.class);

        HashMap<String,String> readingLookup = Util.makeReadingLookup(jerseyTest, tradId);

        String[] exp = new String[64];
        exp[0] = "digraph \"Tradition\" \\{";
        exp[1] = "graph \\[bgcolor=\"none\", rankdir=\"LR\"\\]";
        exp[2] = "node \\[fillcolor=\"white\", fontsize=\"14\", shape=\"ellipse\", style=\"filled\"\\]";
        exp[3] = "edge \\[arrowhead=\"open\", color=\"#000000\", fontcolor=\"#000000\"\\]";
        exp[4] = String.format("subgraph \\{ rank=same %s \"#SILENT#\" \\}", readingLookup.get("#START#/0"));
        exp[5] = "\"#SILENT#\" \\[shape=diamond,color=white,penwidth=0,label=\"\"\\]";
        exp[6] = String.format("%s \\[id=\"__START__\", label=\"#START#\"\\]", readingLookup.get("#START#/0"));
        exp[7] = String.format("%s \\[id=\"n%s\", label=\"when\"\\]", readingLookup.get("when/1"), readingLookup.get("when/1"));
        exp[9] = String.format("%s \\[id=\"n%s\", label=\"april\"\\]", readingLookup.get("april/2"), readingLookup.get("april/2"));
        exp[11] = String.format("%s \\[id=\"n%s\", label=\"showers\"\\]", readingLookup.get("showers/5"), readingLookup.get("showers/5"));
        exp[14] = String.format("%s \\[id=\"n%s\", label=\"with\"\\]", readingLookup.get("with/3"), readingLookup.get("with/3"));
        exp[16] = String.format("%s \\[id=\"n%s\", label=\"sweet\"\\]", readingLookup.get("sweet/6"), readingLookup.get("sweet/6"));
        exp[18] = String.format("%s \\[id=\"n%s\", label=\"his\"\\]", readingLookup.get("his/4"), readingLookup.get("his/4"));
        exp[20] = String.format("%s \\[id=\"n%s\", label=\"with\"\\]", readingLookup.get("with/7"), readingLookup.get("with/7"));
        exp[22] = String.format("%s \\[id=\"n%s\", label=\"april\"\\]", readingLookup.get("april/8"), readingLookup.get("april/8"));
        exp[24] = String.format("%s \\[id=\"n%s\", label=\"fruit\"\\]", readingLookup.get("fruit/9"), readingLookup.get("fruit/9"));
        exp[27] = String.format("%s \\[id=\"n%s\", label=\"teh\"\\]", readingLookup.get("teh/10"), readingLookup.get("teh/10"));
        exp[29] = String.format("%s \\[id=\"n%s\", label=\"the\"\\]", readingLookup.get("the/10"), readingLookup.get("the/10"));
        exp[31] = String.format("%s \\[id=\"n%s\", label=\"drought\"\\]", readingLookup.get("drought/11"), readingLookup.get("drought/11"));
        exp[34] = String.format("%s \\[id=\"n%s\", label=\"march\"\\]", readingLookup.get("march/11"), readingLookup.get("march/11"));
        exp[36] = String.format("%s \\[id=\"n%s\", label=\"of\"\\]", readingLookup.get("of/12"), readingLookup.get("of/12"));
        exp[39] = String.format("%s \\[id=\"n%s\", label=\"drought\"\\]", readingLookup.get("drought/13"), readingLookup.get("drought/13"));
        exp[41] = String.format("%s \\[id=\"n%s\", label=\"march\"\\]", readingLookup.get("march/13"), readingLookup.get("march/13"));
        exp[43] = String.format("%s \\[id=\"n%s\", label=\"has\"\\]", readingLookup.get("has/14"), readingLookup.get("has/14"));
        exp[46] = String.format("%s \\[id=\"n%s\", label=\"pierced\"\\]", readingLookup.get("pierced/15"), readingLookup.get("pierced/15"));
        exp[48] = String.format("%s \\[id=\"n%s\", label=\"to\"\\]", readingLookup.get("to/16"), readingLookup.get("to/16"));
        exp[50] = String.format("%s \\[id=\"n%s\", label=\"unto\"\\]", readingLookup.get("unto/16"), readingLookup.get("unto/16"));
        exp[52] = String.format("%s \\[id=\"n%s\", label=\"teh\"\\]", readingLookup.get("teh/16"), readingLookup.get("teh/16"));
        exp[54] = String.format("%s \\[id=\"n%s\", label=\"the\"\\]", readingLookup.get("the/17"), readingLookup.get("the/17"));
        exp[57] = String.format("%s \\[id=\"n%s\", label=\"rood\"\\]", readingLookup.get("rood/17"), readingLookup.get("rood/17"));
        exp[59] = String.format("%s \\[id=\"n%s\", label=\"root\"\\]", readingLookup.get("root/18"), readingLookup.get("root/18"));
        exp[61] = String.format("%s \\[id=\"__END__\", label=\"#END#\"\\]", readingLookup.get("#END#/19"));
        exp[8] = String.format("%s->%s \\[label=\"A, B, C\", id=\"e\\d+\", penwidth=\"1.4\"\\]", readingLookup.get("#START#/0"), readingLookup.get("when/1"));
        exp[10] = String.format("%s->%s \\[label=\"A\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("when/1"), readingLookup.get("april/2"));
        exp[12] = String.format("%s->%s \\[label=\"A\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("april/2"), readingLookup.get("with/3"));
        exp[13] = String.format("%s->%s \\[label=\"B, C\", id=\"e\\d+\", penwidth=\"1.2\", minlen=\"4\"\\]", readingLookup.get("when/1"), readingLookup.get("showers/5"));
        exp[15] = String.format("%s->%s \\[label=\"A\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("with/3"), readingLookup.get("his/4"));
        exp[17] = String.format("%s->%s \\[label=\"A, B, C\", id=\"e\\d+\", penwidth=\"1.4\"\\]", readingLookup.get("showers/5"), readingLookup.get("sweet/6"));
        exp[19] = String.format("%s->%s \\[label=\"A\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("his/4"), readingLookup.get("showers/5"));
        exp[21] = String.format("%s->%s \\[label=\"A, B, C\", id=\"e\\d+\", penwidth=\"1.4\"\\]", readingLookup.get("sweet/6"), readingLookup.get("with/7"));
        exp[23] = String.format("%s->%s \\[label=\"B, C\", id=\"e\\d+\", penwidth=\"1.2\"\\]", readingLookup.get("with/7"), readingLookup.get("april/8"));
        exp[25] = String.format("%s->%s \\[label=\"A\", id=\"e\\d+\", penwidth=\"1.0\", minlen=\"2\"\\]", readingLookup.get("with/7"), readingLookup.get("fruit/9"));
        exp[26] = String.format("%s->%s \\[label=\"B, C\", id=\"e\\d+\", penwidth=\"1.2\"\\]", readingLookup.get("april/8"), readingLookup.get("fruit/9"));
        exp[28] = String.format("%s->%s \\[label=\"C\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("fruit/9"), readingLookup.get("teh/10"));
        exp[30] = String.format("%s->%s \\[label=\"A, B\", id=\"e\\d+\", penwidth=\"1.2\"\\]", readingLookup.get("fruit/9"), readingLookup.get("the/10"));
        exp[32] = String.format("%s->%s \\[label=\"C\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("teh/10"), readingLookup.get("drought/11"));
        exp[33] = String.format("%s->%s \\[label=\"A\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("the/10"), readingLookup.get("drought/11"));
        exp[35] = String.format("%s->%s \\[label=\"B\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("the/10"), readingLookup.get("march/11"));
        exp[37] = String.format("%s->%s \\[label=\"A, C\", id=\"e\\d+\", penwidth=\"1.2\"\\]", readingLookup.get("drought/11"), readingLookup.get("of/12"));
        exp[38] = String.format("%s->%s \\[label=\"B\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("march/11"), readingLookup.get("of/12"));
        exp[40] = String.format("%s->%s \\[label=\"B\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("of/12"), readingLookup.get("drought/13"));
        exp[42] = String.format("%s->%s \\[label=\"A, C\", id=\"e\\d+\", penwidth=\"1.2\"\\]", readingLookup.get("of/12"), readingLookup.get("march/13"));
        exp[44] = String.format("%s->%s \\[label=\"B\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("drought/13"), readingLookup.get("has/14"));
        exp[45] = String.format("%s->%s \\[label=\"A, C\", id=\"e\\d+\", penwidth=\"1.2\"\\]", readingLookup.get("march/13"), readingLookup.get("has/14"));
        exp[47] = String.format("%s->%s \\[label=\"A, B, C\", id=\"e\\d+\", penwidth=\"1.4\"\\]", readingLookup.get("has/14"), readingLookup.get("pierced/15"));
        exp[49] = String.format("%s->%s \\[label=\"B\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("pierced/15"), readingLookup.get("to/16"));
        exp[51] = String.format("%s->%s \\[label=\"A\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("pierced/15"), readingLookup.get("unto/16"));
        exp[53] = String.format("%s->%s \\[label=\"C\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("pierced/15"), readingLookup.get("teh/16"));
        exp[55] = String.format("%s->%s \\[label=\"A\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("unto/16"), readingLookup.get("the/17"));
        exp[56] = String.format("%s->%s \\[label=\"B\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("to/16"), readingLookup.get("the/17"));
        exp[58] = String.format("%s->%s \\[label=\"C\", id=\"e\\d+\", penwidth=\"1.0\"\\]", readingLookup.get("teh/16"), readingLookup.get("rood/17"));
        exp[60] = String.format("%s->%s \\[label=\"A, B\", id=\"e\\d+\", penwidth=\"1.2\"\\]", readingLookup.get("the/17"), readingLookup.get("root/18"));
        exp[62] = String.format("%s->%s \\[label=\"C\", id=\"e\\d+\", penwidth=\"1.0\", minlen=\"2\"\\]", readingLookup.get("rood/17"), readingLookup.get("#END#/19"));
        exp[63] = String.format("%s->%s \\[label=\"A, B\", id=\"e\\d+\", penwidth=\"1.2\"\\]", readingLookup.get("root/18"), readingLookup.get("#END#/19"));

        for (String anExp : exp) {
            Pattern p = Pattern.compile(anExp);
            Matcher m = p.matcher(str);
            assertTrue("seeking pattern" + m, m.find());
        }
    }

    @Test
    public void testSectionDotOutput() {
        List<String> florIds = Util.importFlorilegium(jerseyTest);
        String florId = florIds.remove(0);
        String targetSection = florIds.get(3);

        String getDot = "/tradition/" + florId + "/dot";
        Response jerseyResult = jerseyTest
                .target(getDot)
                .request(MediaType.TEXT_PLAIN)
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        // Do a basic sanity check of the dot file - does it have the right number of lines?
        String[] dotLines = jerseyResult.readEntity(String.class).split("\n");
        assertEquals(651, dotLines.length);

        String wWord = "Μαξίμου";
        String xWord = "βλασφημεῖται";
        String yWord = "συντυχίας";
        String zWord = "βλασφημοῦντος";

        // Set a normal form on one reading, see if it turns up in dot output.
        // Also stick a double quote inside one of the readings
        try (Transaction tx = db.beginTx()) {
            Node n = tx.findNode(Nodes.READING, "text", "ὄψιν,");
            assertNotNull(n);
            n.setProperty("text", "ὄψ\"ιν,");
            n.setProperty("normal_form", "ὄψιν");
            n = tx.findNode(Nodes.READING, "text", "γυναικῶν.");
            assertNotNull(n);
            n.setProperty("text", "γυν\"αι\"κῶν.");
            tx.commit();
        }

        String getSectionDot = "/tradition/" + florId + "/section/" + florIds.get(2) + "/dot";
        jerseyResult = jerseyTest
                .target(getSectionDot)
                .queryParam("expand_sigla", "true")
                .request(MediaType.TEXT_PLAIN)
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        String dotStr = jerseyResult.readEntity(String.class);
        assertTrue(dotStr.startsWith("digraph \"part 2\""));
        assertFalse(dotStr.contains("majority"));
        assertTrue(dotStr.contains(yWord));
        assertTrue(dotStr.contains("γυν\\\"αι\\\"κῶν."));


        getSectionDot = "/tradition/" + florId + "/section/" + targetSection + "/dot";
        jerseyResult = jerseyTest
                .target(getSectionDot)
                .queryParam("show_normal", "true")
                .request(MediaType.TEXT_PLAIN)
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        dotStr = jerseyResult.readEntity(String.class);
        dotLines = dotStr.split("\n");
        assertEquals(121, dotLines.length);
        assertFalse(dotStr.contains(wWord));
        assertFalse(dotStr.contains(xWord));
        assertFalse(dotStr.contains(yWord));
        assertTrue(dotStr.contains(zWord));
        assertTrue(dotStr.contains("#START#"));
        assertTrue(dotStr.contains("#END#"));
        assertTrue(dotStr.contains("<ὄ&psi;&quot;&iota;&nu;,<BR/><FONT COLOR=\"grey\">ὄ&psi;&iota;&nu;</FONT>>"));

        getSectionDot = "/tradition/" + florId + "/section/" + florIds.get(1) + "/dot";
        jerseyResult = jerseyTest.target(getSectionDot)
                .queryParam("exclude_witness", "A")
                .queryParam("exclude_witness", "B")
                .queryParam("exclude_witness", "F")
                .queryParam("exclude_witness", "G")
                .queryParam("exclude_witness", "Q")
                .queryParam("exclude_witness", "T")
                .request(MediaType.TEXT_PLAIN)
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
        dotStr = jerseyResult.readEntity(String.class);
        dotLines = dotStr.split("\n");
        assertEquals(123, dotLines.length);
        assertFalse(dotStr.contains("νείλου"));
        assertTrue(dotStr.contains(xWord));
        // There should only be one link out from the start
        Optional<String> startNode = Arrays.stream(dotLines).filter(x -> x.contains("__START__")).findFirst();
        assertTrue(startNode.isPresent());
        Long startId = getNodeFromDot(startNode.get());
        assertEquals(1, Arrays.stream(dotLines).filter(x -> x.contains("\t" + startId + "->")).count());
        List<String> gars = Arrays.stream(dotLines).filter(x -> x.contains("γὰρ")).collect(Collectors.toList());
        assertEquals(1, gars.size());
        Long garId = getNodeFromDot(gars.get(0));
        List<String> garLink = Arrays.stream(dotLines).filter(x -> x.contains("\t" + garId + "->"))
                .collect(Collectors.toList());
        assertEquals(2, garLink.size());
        for (String l : garLink) {
            assertFalse(l.contains("F"));
        }
    }

    private Long getNodeFromDot(String dotLine) {
        return Long.valueOf(dotLine.replaceAll("\\s+", "").split("\\[")[0]);
    }

    @Test
    public void testEmendedLemmatisedDot() {
        // Get the section ID
        List<SectionModel> tradSections = jerseyTest
                .target("/tradition/" + msTradId + "/sections")
                .request()
                .get(new GenericType<>() {});
        String sectId = tradSections.get(0).getId();

        // Get the reading hash
        HashMap<String,String> readingLookup = Util.makeReadingLookup(jerseyTest, msTradId);

        // Propose an emendation and set it
        ProposedEmendationModel pem = new ProposedEmendationModel();
        pem.setAuthority("H. Granger");
        pem.setText("alohomora");
        pem.setFromRank(4L);
        pem.setToRank(6L);
        Response response = jerseyTest
                .target("/tradition/" + msTradId + "/section/" + sectId + "/emend")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(pem));
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        GraphModel newEmendation = response.readEntity(GraphModel.class);
        assertEquals(1, newEmendation.getReadings().size());
        ReadingModel eReading = newEmendation.getReadings().iterator().next();

        // Check that the dot contains the emendation and that it is connected in the graph
        response = jerseyTest.target("/tradition/" + msTradId + "/section/" + sectId + "/dot")
                .request().get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        String traditionDot = response.readEntity(String.class);

        // Check that the node is in there and its ID is correct
        assertTrue(traditionDot.contains(String.format("%s [id=\"ne%s\", label=\"%s\"];",
                eReading.getId(), eReading.getId(), eReading.getText())));
        assertFalse(traditionDot.contains("n" + eReading.getId()));
        // Check that the node has its anchoring links
        int anchoringLinks = 0;
        for (String l : traditionDot.split("\n")) {
            if (l.contains(eReading.getId()) && l.contains("->")) {
                anchoringLinks++;
                assertTrue(l.contains("[color=white,penwidth=0,arrowhead=none]"));
            }
        }
        assertEquals(12, anchoringLinks);
        assertTrue(traditionDot.contains(String.format("%s->%s", readingLookup.get("venerabilis/3"), eReading.getId())));

        // Lemmatise the emendation and a string of other readings
        List<String> lemmata = Stream.of("in/1", "swecia/2", "venerabilis/3", "de/7", "anglia/8", "oriundus/9")
                .map(readingLookup::get).collect(Collectors.toList());
        lemmata.add(eReading.getId());
        MultivaluedMap<String, String> lemmaParam = new MultivaluedHashMap<>();
        lemmaParam.add("value", "true");
        for (String rstr : lemmata) {
            response = jerseyTest.target("/reading/" + rstr + "/setlemma")
                    .request()
                    .post(Entity.entity(lemmaParam, MediaType.APPLICATION_FORM_URLENCODED));
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        }

        // Check that we can request the section dot
        response = jerseyTest.target("/tradition/" + msTradId + "/section/" + sectId + "/dot")
                .queryParam("show_normal", "true")
                .request()
                .get(Response.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        String sectionDot = response.readEntity(String.class);
        assertTrue(sectionDot.startsWith("digraph"));

        // Check that the emendation is there as before
        assertTrue(sectionDot.contains(String.format("%s [id=\"ne%s\", label=\"%s\"];",
                eReading.getId(), eReading.getId(), eReading.getText())));
        assertFalse(sectionDot.contains("n" + eReading.getId()));
        // Check that the node has its anchoring links
        anchoringLinks = 0;
        for (String l : sectionDot.split("\n")) {
            if (l.contains(eReading.getId()) && l.contains("->")) {
                anchoringLinks++;
                assertTrue(l.contains("[color=white,penwidth=0,arrowhead=none]"));
            }
        }
        assertEquals(12, anchoringLinks);
        assertTrue(sectionDot.contains(String.format("%s->%s", readingLookup.get("venerabilis/3"), eReading.getId())));

        // Now set the section lemma path
        response = jerseyTest
                .target("/tradition/" + msTradId + "/section/" + sectId + "/setlemma")
                .request(MediaType.APPLICATION_JSON)
                .post(null);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        // Check that we can still request the section dot
        response = jerseyTest.target("/tradition/" + msTradId + "/section/" + sectId + "/dot")
                .queryParam("show_normal", "true")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        sectionDot = response.readEntity(String.class);
        assertTrue(sectionDot.startsWith("digraph"));

        // Now the emendation should be linked via a lemma path
        assertTrue(sectionDot.contains(String.format("%s [id=\"ne%s\", label=\"%s\"];",
                eReading.getId(), eReading.getId(), eReading.getText())));
        assertFalse(sectionDot.contains("n" + eReading.getId()));
        assertTrue(sectionDot.contains(String.format(
                "%s->%s [id=l", readingLookup.get("venerabilis/3"), eReading.getId())));
        assertTrue(sectionDot.contains(String.format(
                "%s->%s [id=l", eReading.getId(), readingLookup.get("de/7"))));

        // Get a normalised version of the dot
        response = jerseyTest.target("/tradition/" + msTradId + "/section/" + sectId + "/dot")
                .queryParam("normalise", "spelling")
                .request().get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        sectionDot = response.readEntity(String.class);
        assertTrue(sectionDot.startsWith("digraph"));

        // Check that the emendation is there
        assertTrue(sectionDot.contains(String.format("%s [id=\"ne%s\", label=\"%s\"];",
                eReading.getId(), eReading.getId(), eReading.getText())));
        assertFalse(sectionDot.contains("n" + eReading.getId()));
        assertTrue(sectionDot.contains(String.format(
                "%s->%s [id=l", readingLookup.get("venerabilis/3"), eReading.getId())));
        assertTrue(sectionDot.contains(String.format(
                "%s->%s [id=l", eReading.getId(), readingLookup.get("de/7"))));

        // Check that there aren't any nodes referred to that shouldn't exist, e.g. for anchor edges
        HashSet<String> nodeIds = new HashSet<>();
        HashSet<String> edgeNodes = new HashSet<>();
        Arrays.stream(sectionDot.split("\n")).forEach( l -> {
            Pattern pNode = Pattern.compile("^\\t(\\d+) \\[id=\"(ne?\\1|__).*");
            Pattern pEdge = Pattern.compile("^\\t(\\d+)->(\\d+).*");
            Matcher mNode = pNode.matcher(l);
            Matcher mEdge = pEdge.matcher(l);
            if (mNode.matches()) {
                nodeIds.add(mNode.group(1));
            }
            if (mEdge.matches()) {
                edgeNodes.add(mEdge.group(1));
                edgeNodes.add(mEdge.group(2));
            }
        });
        assertTrue(nodeIds.containsAll(edgeNodes));
    }

    @Test
    public void testDotNonAscii() {
        String naTradId = Util.getValueFromJson(Util.createTraditionFromFileOrString(jerseyTest, "", "LR",
                "user@example.com", "src/TestFiles/milestone-591.zip", "graphml"), "tradId");
        assertNotNull(naTradId);
        String dotOutput = jerseyTest.target("/tradition/" + naTradId + "/dot").request().get(String.class);
        assertTrue(dotOutput.contains("եկն"));
    }

    @After
    public void tearDown() throws Exception {
//        db.shutdown();
    	if (dbbuilder != null) {
    		dbbuilder.shutdownDatabase(db.databaseName());
    	}
        jerseyTest.tearDown();
    }

}
