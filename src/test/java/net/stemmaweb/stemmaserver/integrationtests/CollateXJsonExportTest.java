package net.stemmaweb.stemmaserver.integrationtests;

import java.io.StringReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.opencsv.CSVReader;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.test.JerseyTest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import junit.framework.TestCase;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationModel;
import net.stemmaweb.model.SectionModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.VariantGraphService;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;

import static org.junit.Assert.assertNotEquals;

/**
 * Tests for the streaming CollateX JSON export of a section.
 */
public class CollateXJsonExportTest extends TestCase {

    private GraphDatabaseService db;
    private JerseyTest jerseyTest;

    public void setUp() throws Exception {
        super.setUp();
        DatabaseManagementService dbbuilder = new TestDatabaseManagementServiceBuilder().impermanent().build();
        db = dbbuilder.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME);
        new GraphDatabaseServiceProvider(dbbuilder, db);
        Util.setupTestDB(db, "1");
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(Root.class)
                .create();
        jerseyTest.setUp();
    }

    public void tearDown() throws Exception {
        GraphDatabaseServiceProvider.shutdown();
        jerseyTest.tearDown();
        super.tearDown();
    }

    // Helpers

    private String createCsvTradition(String csv) {
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "CX test", "LR", "1", csv, "csv");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        return Util.getValueFromJson(response, "tradId");
    }

    private Response getSectionJson(String tradId, String sectId, String conflate, boolean excludeLayers) {
        var target = jerseyTest.target("/tradition/" + tradId + "/section/" + sectId + "/cxjson");
        if (conflate != null) target = target.queryParam("conflate", conflate);
        if (excludeLayers) target = target.queryParam("exclude_layers", "true");
        return target.request().get();
    }

    private JSONObject fetchSectionCx(String tradId, String sectId, String conflate, boolean excludeLayers) {
        Response response = getSectionJson(tradId, sectId, conflate, excludeLayers);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        return new JSONObject(response.readEntity(String.class));
    }

    private static List<String> witnessList(JSONObject cx) {
        List<String> result = new ArrayList<>();
        JSONArray wits = cx.getJSONArray("witnesses");
        for (int i = 0; i < wits.length(); i++) result.add(wits.getString(i));
        return result;
    }

    // Returns the text of the token in a cell, or null if the cell is empty
    private static String cellText(JSONArray row, int col) {
        JSONArray cell = row.getJSONArray(col);
        if (cell.isEmpty()) return null;
        assertEquals(1, cell.length());
        return cell.getJSONObject(0).getString("t");
    }

    // Copied from SectionTest
    private String createBareEmptySection(String tradId) {
        String sectId;
        try (Transaction tx = db.beginTx()) {
            Node tradNode = VariantGraphService.getTraditionNode(tx, tradId);
            Node sectionNode = DatabaseService.createNode(tx, Nodes.SECTION);
            sectionNode.setProperty("name", "Empty section");
            tradNode.createRelationshipTo(sectionNode, ERelations.PART);
            sectId = String.valueOf(sectionNode.getProperty("id"));
            tx.commit();
        }
        return sectId;
    }

    // Tests

    public void testSectionShape() {
        String tradId = createCsvTradition("A,B\nx,x\ny,\nz,w");
        String sectId = Util.getSingleSection(jerseyTest, tradId).getId();
        Response response = getSectionJson(tradId, sectId, null, false);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals("application/json;charset=utf-8", response.getMediaType().toString());
        JSONObject cx = new JSONObject(response.readEntity(String.class));
        assertTrue(cx.has("name"));
        assertEquals(List.of("A", "B"), witnessList(cx));
        JSONArray expected = new JSONArray("[[[{\"t\":\"x\",\"n\":\"x\"}],[{\"t\":\"x\",\"n\":\"x\"}]], "
                + "[[{\"t\":\"y\",\"n\":\"y\"}],[]], "
                + "[[{\"t\":\"z\",\"n\":\"z\"}],[{\"t\":\"w\",\"n\":\"w\"}]]]");
        assertTrue("table was " + cx.getJSONArray("table"), expected.similar(cx.getJSONArray("table")));
    }

    public void testLayerColumns() {
        String tradId = createCsvTradition("A,A (a.c.),B\nx,y,x");
        String sectId = Util.getSingleSection(jerseyTest, tradId).getId();
        JSONObject cx = fetchSectionCx(tradId, sectId, null, false);
        assertEquals(List.of("A", "A (a.c.)", "B"), witnessList(cx));
        JSONArray row = cx.getJSONArray("table").getJSONArray(0);
        assertEquals("x", cellText(row, 0));
        assertEquals("y", cellText(row, 1));
        assertEquals("x", cellText(row, 2));

        cx = fetchSectionCx(tradId, sectId, null, true);
        assertEquals(List.of("A", "B"), witnessList(cx));
        assertEquals(2, cx.getJSONArray("table").getJSONArray(0).length());
    }

    public void testLacunaSpan() {
        String tradId = createCsvTradition("A,B\nx,x\n#LACUNA#,y\n#LACUNA#,z\n#LACUNA#,z2\nw,w\nv,#LACUNA#\n");
        String sectId = Util.getSingleSection(jerseyTest, tradId).getId();
        JSONObject cx = fetchSectionCx(tradId, sectId, null, false);
        assertEquals(List.of("A", "B"), witnessList(cx));
        JSONArray table = cx.getJSONArray("table");
        assertEquals(6, table.length());
        String[] colA = {"x", "#LACUNA#", "#LACUNA#", "#LACUNA#", "w", "v"};
        String[] colB = {"x", "y", "z", "z2", "w", "#LACUNA#"};
        for (int i = 0; i < 6; i++) {
            assertEquals("row " + (i + 1), colA[i], cellText(table.getJSONArray(i), 0));
            assertEquals("row " + (i + 1), colB[i], cellText(table.getJSONArray(i), 1));
        }

        // A lacuna first on a witness's path is written from rank 1
        tradId = createCsvTradition("A,B\n#LACUNA#,x\n#LACUNA#,y\nz,z\n");
        sectId = Util.getSingleSection(jerseyTest, tradId).getId();
        table = fetchSectionCx(tradId, sectId, null, false).getJSONArray("table");
        assertEquals(3, table.length());
        assertEquals("#LACUNA#", cellText(table.getJSONArray(0), 0));
        assertEquals("#LACUNA#", cellText(table.getJSONArray(1), 0));
        assertEquals("z", cellText(table.getJSONArray(2), 0));
    }

    public void testEmptyColumnsAndRowsOmitted() {
        // Witness C has readings in the first section only
        String tradId = createCsvTradition("A,B,C\nx,x,x\ny,y,y\n");
        Response r = Util.addSectionToTradition(jerseyTest, tradId, "A,B\np,p\nq,\n", "csv", "second");
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        String sect2 = Util.getValueFromJson(r, "sectionId");
        JSONObject cx = fetchSectionCx(tradId, sect2, null, false);
        assertEquals(List.of("A", "B"), witnessList(cx));
        assertEquals(2, cx.getJSONArray("table").length());

        // A rank occupied only by a layer witness yields no row when layers are excluded
        tradId = createCsvTradition("A,A (a.c.),B\nx,x,x\n,q,\ny,y,y\n");
        String sectId = Util.getSingleSection(jerseyTest, tradId).getId();
        cx = fetchSectionCx(tradId, sectId, null, false);
        assertEquals(3, cx.getJSONArray("table").length());
        cx = fetchSectionCx(tradId, sectId, null, true);
        assertEquals(List.of("A", "B"), witnessList(cx));
        JSONArray table = cx.getJSONArray("table");
        assertEquals(2, table.length());
        assertEquals("x", cellText(table.getJSONArray(0), 0));
        assertEquals("y", cellText(table.getJSONArray(1), 0));
    }

    public void testEmptySectionIsValidCx() {
        String tradId = createCsvTradition("A,B\nx,x\n");
        String sectId = createBareEmptySection(tradId);
        JSONObject cx = fetchSectionCx(tradId, sectId, null, false);
        assertEquals("Empty section", cx.getString("name"));
        assertTrue(cx.getJSONArray("witnesses").isEmpty());
        assertTrue(cx.getJSONArray("table").isEmpty());
    }

    public void testConflateExport() {
        String tradId = createCsvTradition("A,B\nx,x\ncolour,color\nz,z\n");
        String sectId = Util.getSingleSection(jerseyTest, tradId).getId();
        String colour, color;
        try (Transaction tx = db.beginTx()) {
            colour = tx.findNode(Nodes.READING, "text", "colour").getProperty("id").toString();
            color = tx.findNode(Nodes.READING, "text", "color").getProperty("id").toString();
        }
        RelationModel rel = new RelationModel();
        rel.setSource(colour);
        rel.setTarget(color);
        rel.setType("spelling");
        rel.setScope("local");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/relation")
                .request().post(Entity.json(rel))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), result.getStatus());
        }

        // Unconflated: two different readings at rank 2
        JSONArray row = fetchSectionCx(tradId, sectId, null, false).getJSONArray("table").getJSONArray(1);
        assertEquals("colour", cellText(row, 0));
        assertEquals("color", cellText(row, 1));

        // Conflated: both witnesses carry the representative
        row = fetchSectionCx(tradId, sectId, "spelling", false).getJSONArray("table").getJSONArray(1);
        JSONObject tokA = row.getJSONArray(0).getJSONObject(0);
        JSONObject tokB = row.getJSONArray(1).getJSONObject(0);
        assertEquals(tokA.getString("t"), tokB.getString("t"));
        assertEquals(tokA.getString("n"), tokB.getString("n"));
        assertTrue(List.of("colour", "color").contains(tokA.getString("t")));

        // Unknown relation type
        Response response = getSectionJson(tradId, sectId, "nonsense", false);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        JSONObject err = new JSONObject(response.readEntity(String.class));
        assertTrue(err.has("error"));
        assertFalse(err.has("table"));

        // Unknown section
        response = getSectionJson(tradId, "99999999", null, false);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

        // Unknown tradition
        response = getSectionJson("nosuchtradition", sectId, null, false);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    // Tradition-level export

    private Response getTraditionJson(String tradId, String... sectionIds) {
        var target = jerseyTest.target("/tradition/" + tradId + "/cxjson");
        for (String s : sectionIds) target = target.queryParam("section", s);
        return target.request().get();
    }

    private JSONObject fetchTraditionCx(String tradId, String... sectionIds) {
        Response response = getTraditionJson(tradId, sectionIds);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals("application/json;charset=utf-8", response.getMediaType().toString());
        return new JSONObject(response.readEntity(String.class));
    }

    private List<String> sectionIds(String tradId) {
        List<SectionModel> sections = jerseyTest.target("/tradition/" + tradId + "/sections")
                .request().get(new GenericType<>() {});
        List<String> result = new ArrayList<>();
        for (SectionModel sm : sections) result.add(sm.getId());
        return result;
    }

    private String uploadMultiSectionFixture() {
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "Multi", "LR", "1",
                "src/TestFiles/cxjson_multi.json", "cxjson");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        return Util.getValueFromJson(response, "tradId");
    }

    public void testTraditionWrapper() {
        String tradId = uploadMultiSectionFixture();
        JSONObject cx = fetchTraditionCx(tradId);
        assertEquals("Multi", cx.getString("name"));
        JSONArray sections = cx.getJSONArray("sections");
        assertEquals(5, sections.length());
        String[] names = {"first section", "second section", "third section", "fourth section", "fifth section"};
        for (int i = 0; i < 5; i++) {
            JSONObject section = sections.getJSONObject(i);
            assertEquals(names[i], section.getString("name"));
            assertEquals(List.of("A", "B", "C", "D", "F", "G", "L", "O", "Y"), witnessList(section));
            JSONArray table = section.getJSONArray("table");
            assertFalse(table.isEmpty());
            for (int r = 0; r < table.length(); r++)
                assertEquals(9, table.getJSONArray(r).length());
        }
    }

    public void testSectionFilter() {
        String tradId = uploadMultiSectionFixture();
        List<String> ids = sectionIds(tradId);
        assertEquals(5, ids.size());

        // The filter returns the requested sections in tradition order, whatever the request order
        JSONObject cx = fetchTraditionCx(tradId, ids.get(3), ids.get(1));
        JSONArray sections = cx.getJSONArray("sections");
        assertEquals(2, sections.length());
        assertEquals("second section", sections.getJSONObject(0).getString("name"));
        assertEquals("fourth section", sections.getJSONObject(1).getString("name"));

        // A repeated id is only returned once
        cx = fetchTraditionCx(tradId, ids.get(2), ids.get(2));
        assertEquals(1, cx.getJSONArray("sections").length());

        // A bad section id is a client error
        Response response = getTraditionJson(tradId, ids.get(1), "123456789");
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        JSONObject err = new JSONObject(response.readEntity(String.class));
        assertTrue(err.has("error"));
        assertFalse(err.has("sections"));

        // An unknown tradition is a 404
        response = getTraditionJson("nosuchtradition", ids.get(1));
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    public void testTraditionWithEmptySection() {
        String tradId = createCsvTradition("A,B\nx,x\ny,z\n");
        String lastSection = Util.getSingleSection(jerseyTest, tradId).getId();
        // Add a bare empty section behind the existing one, linked as Tradition.addSection would
        String emptyId;
        try (Transaction tx = db.beginTx()) {
            Node tradNode = VariantGraphService.getTraditionNode(tx, tradId);
            Node sectionNode = DatabaseService.createNode(tx, Nodes.SECTION);
            sectionNode.setProperty("name", "Empty section");
            tradNode.createRelationshipTo(sectionNode, ERelations.PART);
            DatabaseService.findNodeOrThrow(tx, Nodes.SECTION, lastSection)
                    .createRelationshipTo(sectionNode, ERelations.NEXT);
            emptyId = String.valueOf(sectionNode.getProperty("id"));
            tx.commit();
        }
        assertEquals(List.of(lastSection, emptyId), sectionIds(tradId));

        JSONArray sections = fetchTraditionCx(tradId).getJSONArray("sections");
        assertEquals(2, sections.length());
        JSONObject first = sections.getJSONObject(0);
        assertEquals(List.of("A", "B"), witnessList(first));
        assertEquals(2, first.getJSONArray("table").length());
        assertEquals("x", cellText(first.getJSONArray("table").getJSONArray(0), 0));
        assertEquals("z", cellText(first.getJSONArray("table").getJSONArray(1), 1));
        JSONObject empty = sections.getJSONObject(1);
        assertEquals("Empty section", empty.getString("name"));
        assertTrue(empty.getJSONArray("witnesses").isEmpty());
        assertTrue(empty.getJSONArray("table").isEmpty());
    }

    public void testWitnessOnlyInSomeSections() {
        String tradId = createCsvTradition("A,B\nx,x\ny,y\n");
        try (Response r = Util.addSectionToTradition(jerseyTest, tradId, "A,C\np,p\nq,q\n", "csv", "second")) {
            assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        }
        JSONArray sections = fetchTraditionCx(tradId).getJSONArray("sections");
        assertEquals(2, sections.length());
        assertEquals(List.of("A", "B"), witnessList(sections.getJSONObject(0)));
        assertEquals(List.of("A", "C"), witnessList(sections.getJSONObject(1)));
        for (int i = 0; i < 2; i++) {
            JSONArray table = sections.getJSONObject(i).getJSONArray("table");
            assertEquals(2, table.length());
            for (int r = 0; r < 2; r++)
                assertEquals(2, table.getJSONArray(r).length());
        }
    }

    // Repeat the tests from TabularExport to make sure all the complexities are handled correctly

    // ---- Helpers for reading the CollateX JSON produced by /tradition/{id}/cxjson ----

    private JSONObject getTraditionCx(String tradId, String conflate, boolean excludeLayers, String... sections) {
        WebTarget target = jerseyTest.target("/tradition/" + tradId + "/cxjson");
        if (conflate != null) target = target.queryParam("conflate", conflate);
        if (excludeLayers) target = target.queryParam("exclude_layers", "true");
        for (String s : sections) target = target.queryParam("section", s);
        Response response = target.request().get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        return new JSONObject(response.readEntity(String.class));
    }

    private static List<String> cxWitnesses(JSONObject section) {
        List<String> result = new ArrayList<>();
        JSONArray wits = section.getJSONArray("witnesses");
        for (int i = 0; i < wits.length(); i++) result.add(wits.getString(i));
        return result;
    }

    // The text of the token in a CX cell, or null if the cell is empty
    private static String cxCellText(JSONArray row, int col) {
        if (row.isNull(col) || row.getJSONArray(col).isEmpty()) return null;
        return row.getJSONArray(col).getJSONObject(0).getString("t");
    }

    // Witness column -> number of non-empty cells in that column
    private static Map<String, Integer> cxColumnCounts(JSONObject section) {
        List<String> wits = cxWitnesses(section);
        JSONArray table = section.getJSONArray("table");
        Map<String, Integer> counts = new TreeMap<>();
        for (String w : wits) counts.put(w, 0);
        for (int r = 0; r < table.length(); r++) {
            JSONArray row = table.getJSONArray(r);
            assertEquals(wits.size(), row.length());
            for (int c = 0; c < wits.size(); c++)
                if (cxCellText(row, c) != null) counts.merge(wits.get(c), 1, Integer::sum);
        }
        return counts;
    }

    // Check a section's row count and its per-column non-empty cell counts, the latter given in
    // the form "{A=17, B=15}"
    private static void assertCxSection(JSONObject section, int rows, String expectedCounts) {
        assertEquals(rows, section.getJSONArray("table").length());
        Map<String, Integer> expected = new TreeMap<>();
        for (String entry : expectedCounts.replaceAll("[{}]", "").split(", ")) {
            int eq = entry.lastIndexOf('=');
            expected.put(entry.substring(0, eq), Integer.parseInt(entry.substring(eq + 1)));
        }
        assertEquals(expected, cxColumnCounts(section));
    }

    private static final Pattern LAYERED = Pattern.compile("^(.*) \\((.*)\\)$");

    // Join the sections of a CX tradition into one list of cell texts (null for an empty cell) per
    // witness column, as the old single-table JSON export did. A layer column that a section lacks
    // takes its base witness's text for that section; any other absent column is empty.
    private static Map<String, List<String>> cxConcatenatedColumns(JSONObject cx) {
        JSONArray sections = cx.getJSONArray("sections");
        Set<String> allColumns = new TreeSet<>();
        for (int s = 0; s < sections.length(); s++)
            allColumns.addAll(cxWitnesses(sections.getJSONObject(s)));
        Map<String, List<String>> result = new TreeMap<>();
        for (String col : allColumns) result.put(col, new ArrayList<>());
        for (int s = 0; s < sections.length(); s++) {
            JSONObject section = sections.getJSONObject(s);
            List<String> wits = cxWitnesses(section);
            JSONArray table = section.getJSONArray("table");
            for (String col : allColumns) {
                String source = col;
                if (!wits.contains(col)) {
                    Matcher m = LAYERED.matcher(col);
                    source = m.matches() && wits.contains(m.group(1)) ? m.group(1) : null;
                }
                int idx = source == null ? -1 : wits.indexOf(source);
                for (int r = 0; r < table.length(); r++)
                    result.get(col).add(idx < 0 ? null : cxCellText(table.getJSONArray(r), idx));
            }
        }
        return result;
    }

    private static final String TESTTRADITION_COUNTS = "{A=17, B=15, C=14}";
    private static final String GLOBALREL_COUNTS = "{A=10, Ab=9, Ac=8, Ad=8, Ae=8, B=8, Ba=8, Bb=8, Bd=8, Be=8, "
            + "C=8, Ca=8, Cb=8, Cc=8, Cd=8, Ce=8, Cf=8, Da=8, E=8, F=8, G=8, H=8, I=8, J=8, K=8, L=8, M=8, N=8, "
            + "O=8, P=8, R=8, S=8, T=6}";

    // testOutputJSON
    public void testJSONExport() {
        // Set up some data
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/testTradition.xml", "stemmaweb");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        String traditionId = Util.getValueFromJson(response, "tradId");

        // Get it back out
        Response result = jerseyTest
                .target("/tradition/" + traditionId + "/cxjson")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
        // Get the JSON out
        JSONObject cx = new JSONObject(result.readEntity(String.class));
        assertTrue(cx.has("name"));
        assertTrue(cx.has("sections"));
        JSONArray sections = cx.getJSONArray("sections");
        assertEquals(1, sections.length());
        JSONObject section = sections.getJSONObject(0);
        assertEquals(List.of("A", "B", "C"), cxWitnesses(section));
        // 18 ranks, with A having 17 readings, B 15 and C 14
        assertCxSection(section, 18, TESTTRADITION_COUNTS);
    }
    public void testExportSelectedSections() {
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "Florilegium", "LR",
                "1", "src/TestFiles/florilegium_w.csv", "csv");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        String tradId = Util.getValueFromJson(response, "tradId");

        ArrayList<String> tradSections = new ArrayList<>();
        // Get the existing single section ID
        SectionModel firstSection = jerseyTest.target("/tradition/" + tradId + "/sections")
                .request()
                .get(new GenericType<List<SectionModel>>() {}).getFirst();
        assertNotNull(firstSection);
        tradSections.add(firstSection.getId());

        // Add the other three sections
        int i = 0;
        while (i < 3) {
            String fileName = String.format("src/TestFiles/florilegium_%c.csv", 120 + i++);
            String sectId = Util.getValueFromJson(
                    Util.addSectionToTradition(jerseyTest, tradId, fileName, "csv", String.format("part %d", i)),
                    "sectionId");
            tradSections.add(sectId);
        }

        // Expected row counts and per-column reading counts for each section, taken from the
        // output of the former single-table export, queried one section at a time
        int[] expectedRows = {100, 55, 64, 53};
        String[] expectedCounts = {
                "{A=97, C=95, D=95, E=95, F=97, G=60, H=98, K=96, P=95, Q=93, S=95, T=96}",
                "{A=52, B=30, C=54, D=54, E=54, F=51, G=52, H=53, P=54, Q=53, S=54, T=52}",
                "{A=62, B=62, C=62, D=62, E=62, F=62, G=62, H=62, K=62, P=62, Q=62, S=61, T=62}",
                "{A=50, B=53, C=50, P=53, S=53}"
        };

        // Request all sections
        response = jerseyTest
                .target("/tradition/" + tradId + "/cxjson")
                .request(MediaType.APPLICATION_JSON)
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        JSONObject cx = new JSONObject(response.readEntity(String.class));
        JSONArray sections = cx.getJSONArray("sections");
        assertEquals(4, sections.length());
        int totalRows = 0;
        for (int s = 0; s < 4; s++) {
            if (s > 0)
                assertEquals(String.format("part %d", s), sections.getJSONObject(s).getString("name"));
            assertCxSection(sections.getJSONObject(s), expectedRows[s], expectedCounts[s]);
            totalRows += expectedRows[s];
        }
        assertEquals(272, totalRows);
        // Thirteen witnesses in all
        Set<String> allWits = new HashSet<>();
        for (int s = 0; s < 4; s++) allWits.addAll(cxWitnesses(sections.getJSONObject(s)));
        assertEquals(13, allWits.size());

        // Request section 4
        cx = getTraditionCx(tradId, null, false, tradSections.get(3));
        sections = cx.getJSONArray("sections");
        assertEquals(1, sections.length());
        assertCxSection(sections.getJSONObject(0), 53, expectedCounts[3]);
        assertEquals(5, cxWitnesses(sections.getJSONObject(0)).size());

        // Request sections up to 2
        cx = getTraditionCx(tradId, null, false, tradSections.get(0), tradSections.get(1));
        sections = cx.getJSONArray("sections");
        assertEquals(2, sections.length());
        assertCxSection(sections.getJSONObject(0), 100, expectedCounts[0]);
        assertCxSection(sections.getJSONObject(1), 55, expectedCounts[1]);

        // Request section 3
        cx = getTraditionCx(tradId, null, false, tradSections.get(2));
        sections = cx.getJSONArray("sections");
        assertEquals(1, sections.length());
        assertCxSection(sections.getJSONObject(0), 64, expectedCounts[2]);
        assertEquals(13, cxWitnesses(sections.getJSONObject(0)).size());

        // Request sections 3-bad
        response = jerseyTest
                .target("/tradition/" + tradId + "/tsv")
                .queryParam("section", tradSections.get(2))
                .queryParam("section", "123456")
                .request()
                .get();
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("Section 123456 not found in tradition", Util.getValueFromJson(response, "error"));

        // Request sections on nonexistent tradition
        response = jerseyTest
                .target("/tradition/tradId/cxjson")
                .queryParam("section", tradSections.get(1))
                .request()
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

    }

    public void testConflatedJSONExport() {
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/globalrel_test.xml", "stemmaweb");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        String traditionId = Util.getValueFromJson(response, "tradId");
        assertNotNull(traditionId);
        // Get it back out
        Response result = jerseyTest
                .target("/tradition/" + traditionId + "/cxjson")
                .queryParam("conflate", "collated")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
        // Get the JSON out
        JSONObject cx = new JSONObject(result.readEntity(String.class));
        assertEquals(1, cx.getJSONArray("sections").length());
        JSONObject section = cx.getJSONArray("sections").getJSONObject(0);
        assertEquals(33, cxWitnesses(section).size());
        assertCxSection(section, 10, GLOBALREL_COUNTS);
        // Every reading at rank 6 should be identical
        JSONArray table = section.getJSONArray("table");
        HashSet<String> readingsAt6 = new HashSet<>();
        for (int i = 0; i < 33; i++) {
            String r6 = cxCellText(table.getJSONArray(5), i);
            if (r6 != null) readingsAt6.add(r6);
        }
        assertEquals(1, readingsAt6.size());

        // Every reading at rank 9 should be identical
        HashSet<String> readingsAt9 = new HashSet<>();
        for (int i = 0; i < 33; i++) {
            String r9 = cxCellText(table.getJSONArray(8), i);
            if (r9 != null) readingsAt9.add(r9);
        }
        assertEquals(1, readingsAt9.size());

        // Make some spelling relations for the conflation
        HashMap<String,String> readingLookup = Util.makeReadingLookup(jerseyTest, traditionId);
        RelationModel rm = new RelationModel();
        rm.setType("spelling");
        rm.setScope("local");
        rm.setSource(readingLookup.get("Läckämme/7"));
        String[] equivalent = new String[] {"läckömme/7", "Lähkämme/7", "läckämme/7", "Lächkämme/7"};
        for (String r : equivalent) {
            rm.setTarget(readingLookup.get(r));
            response = jerseyTest.target("/tradition/" + traditionId + "/relation")
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(rm));
            assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        }

        // Now conflate according to spelling
        cx = getTraditionCx(traditionId, "spelling", false);
        section = cx.getJSONArray("sections").getJSONObject(0);
        assertCxSection(section, 10, GLOBALREL_COUNTS);
        List<String> wits = cxWitnesses(section);
        assertEquals(33, wits.size());
        table = section.getJSONArray("table");
        // There should be three readings at rank 7
        HashSet<String> readingsAt7 = new HashSet<>();
        for (int i = 0; i < 33; i++) readingsAt7.add(cxCellText(table.getJSONArray(6), i));
        assertEquals(3, readingsAt7.size());
        // The reading at rank 7 of witness A should look right
        assertEquals("A", wits.getFirst());
        assertEquals("Läckämme", cxCellText(table.getJSONArray(6), 0));
    }

    public void testExportMultiSection() throws Exception {
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "Legend", "LR", "1",
                "src/TestFiles/lf2.xml", "stemmaweb");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        String traditionId = Util.getValueFromJson(response, "tradId");
        assertNotNull(traditionId);

        // Get the CSV and check the witness length
        response = jerseyTest.target("/tradition/" + traditionId + "/csv")
                .request()
                .get();
        CSVReader rdr = new CSVReader(new StringReader(response.readEntity(String.class)));
        // See that we have our witnesses
        String[] wits = rdr.readNext();
        assertEquals(34, wits.length);
        assertEquals("B", wits[1]);

        // Add the second section
        try (Response r = Util.addSectionToTradition(jerseyTest, traditionId, "src/TestFiles/legendfrag.xml",
                "stemmaweb", "section 2")) {
            assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        }

        // Export the whole thing to JSON and check the readings. The sections are kept separate,
        // with 21 and 9 ranks respectively (30 in total).
        response = jerseyTest.target("/tradition/" + traditionId + "/cxjson")
                .request()
                .get();
        JSONObject cx = new JSONObject(response.readEntity(String.class));
        JSONArray sections = cx.getJSONArray("sections");
        assertEquals(2, sections.length());
        JSONObject sect1 = sections.getJSONObject(0);
        JSONObject sect2 = sections.getJSONObject(1);
        // The former export gave Vg 9 readings here: 8 words plus a single lacuna reading at rank 9.
        // CollateX JSON writes a lacuna at every rank that it spans, i.e. ranks 9-21, so Vg has 21 cells.
        assertCxSection(sect1, 21, "{A=21, B=20, BA=21, BL=21, BLu=21, BS=21, BSt=21, BU=21, Bc=2, C=21, Dr=21, "
                + "Ef=4, F=17, G=19, Gh=21, H=21, Ho=21, JG=21, K=21, L=17, Li=15, M=20, MN=21, O=21, P=21, "
                + "S=21, Sk=18, St=21, T=21, U=21, V=21, Vg=21, X=21, Y=21}");
        int vgCol = cxWitnesses(sect1).indexOf("Vg");
        for (int r = 0; r < 21; r++) {
            String vgText = cxCellText(sect1.getJSONArray("table").getJSONArray(r), vgCol);
            assertEquals(r < 8, !"#LACUNA#".equals(vgText));
        }
        // Likewise Ab had 3 readings in the former export (2 words and a lacuna at rank 3); the
        // lacuna now fills ranks 3-9, so Ab has 9 cells.
        assertCxSection(sect2, 9, "{A=9, Ab=9, B=9, BA=9, BL=7, BLu=7, BS=9, BSt=9, BU=9, Bc=9, C=9, Dr=9, "
                + "Ef=9, F=9, G=9, Gh=9, H=9, Ho=9, JG=9, K=9, L=8, Li=8, M=9, MN=9, N=9, O=9, P=9, Q=9, S=9, "
                + "Sk=8, St=9, T=9, U=9, V=9, Vg=9, X=9, Y=9}");
        int abCol = cxWitnesses(sect2).indexOf("Ab");
        for (int r = 0; r < 9; r++) {
            String abText = cxCellText(sect2.getJSONArray("table").getJSONArray(r), abCol);
            assertEquals(r < 2, !"#LACUNA#".equals(abText));
        }
        // Witness N has no readings in the first section, so it has no column there; in the
        // second section its first reading is at the first rank.
        assertFalse(cxWitnesses(sect1).contains("N"));
        int nCol = cxWitnesses(sect2).indexOf("N");
        assertTrue(nCol >= 0);
        assertEquals("in", cxCellText(sect2.getJSONArray("table").getJSONArray(0), nCol));
        // Concatenating the sections gives back the old table's shape: 37 witnesses over 30 ranks,
        // with N empty for the first 21 ranks
        Map<String, List<String>> columns = cxConcatenatedColumns(cx);
        assertEquals(37, columns.size());
        List<String> nColumn = columns.get("N");
        assertEquals(30, nColumn.size());
        for (int i = 0; i < 21; i++) assertNull(nColumn.get(i));
        assertEquals("in", nColumn.get(21));


        // Now export the whole thing to CSV
        response = jerseyTest.target("/tradition/" + traditionId + "/csv")
                .request()
                .get();
        rdr = new CSVReader(new StringReader(response.readEntity(String.class)));
        // See that we have our witnesses
        wits = rdr.readNext();
        assertEquals(37, wits.length);
        assertEquals("Ab", wits[1]);
    }

    public void testExportWithLayers() {
        // Take the uncorrected MoE section
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "Chronicle", "LR", "1",
                "src/TestFiles/Matthew-401.json", "cxjson");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        String traditionId = Util.getValueFromJson(response, "tradId");
        assertNotNull(traditionId);

        // Now add the section with corrections
        try (Response r = Util.addSectionToTradition(jerseyTest, traditionId, "src/TestFiles/Matthew-407.json",
                "cxjson", "AM 407")) {
            assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        }

        // Export it to JSON
        response = jerseyTest.target("/tradition/" + traditionId + "/cxjson")
                .request()
                .get();
        JSONObject cx = new JSONObject(response.readEntity(String.class));
        JSONArray sections = cx.getJSONArray("sections");
        assertEquals(2, sections.length());
        // The first section is 153 ranks long and has no corrections
        JSONObject sect1 = sections.getJSONObject(0);
        assertCxSection(sect1, 153, "{A=132, C=128, D=135, E=132, F=132, G=133, I=133, J=132, K=126, V=125, "
                + "W=132, W243=132, W246=124, X=129, Y=127}");
        // The second is 86 ranks long (239 in total) and has a.c. columns
        JSONObject sect2 = sections.getJSONObject(1);
        assertCxSection(sect2, 86, "{A=77, Bz644=77, Bz644 (a.c.)=76, C=76, C (a.c.)=77, D=76, E=76, F=76, "
                + "F (a.c.)=75, G=77, G (a.c.)=76, H=77, I=76, I (a.c.)=77, J=76, J (a.c.)=76, K=76, "
                + "K (a.c.)=75, M2855=77, M2899=77, M3380=77, M6605=76, M6605 (a.c.)=76, O=78, O (a.c.)=79, "
                + "V=77, W243=77, W243 (a.c.)=77, W246=77, X=75, Y=78, Z=77, Z (a.c.)=77}");
        // There should be a.c. columns for these witnesses
        String[] expectedCorr = {"Bz644", "C", "F", "G", "I", "J", "K", "M6605", "O", "W243", "Z"};
        HashSet<String> correctedWits = new HashSet<>(Arrays.asList(expectedCorr));
        HashSet<String> actualCorr = new HashSet<>();
        for (String col : cxWitnesses(sect2)) {
            Matcher m = LAYERED.matcher(col);
            if (m.matches()) {
                assertEquals("a.c.", m.group(2));
                actualCorr.add(m.group(1));
            }
        }
        assertEquals(correctedWits, actualCorr);
        // Altogether there are 34 witness columns
        Map<String, List<String>> columns = cxConcatenatedColumns(cx);
        assertEquals(34, columns.size());
        // The text of C and C (a.c.) should be identical for the first 159 ranks
        List<String> cTokens = columns.get("C");
        List<String> cAcTokens = columns.get("C (a.c.)");
        assertNotNull(cTokens);
        assertNotNull(cAcTokens);
        assertEquals(239, cTokens.size());
        assertEquals(239, cAcTokens.size());
        for (int i=0; i < 159; i++) {
            assertEquals(cTokens.get(i), cAcTokens.get(i));
        }

        // Text export when we exclude the layers
        response = jerseyTest.target("/tradition/" + traditionId + "/cxjson")
                .queryParam("exclude_layers", "true")
                .request().get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        cx = new JSONObject(response.readEntity(String.class));
        sections = cx.getJSONArray("sections");
        assertEquals(2, sections.length());
        assertCxSection(sections.getJSONObject(0), 153, "{A=132, C=128, D=135, E=132, F=132, G=133, I=133, "
                + "J=132, K=126, V=125, W=132, W243=132, W246=124, X=129, Y=127}");
        assertCxSection(sections.getJSONObject(1), 86, "{A=77, Bz644=77, C=76, D=76, E=76, F=76, G=77, H=77, "
                + "I=76, J=76, K=76, M2855=77, M2899=77, M3380=77, M6605=76, O=78, V=77, W243=77, W246=77, "
                + "X=75, Y=78, Z=77}");
        columns = cxConcatenatedColumns(cx);
        assertEquals(23, columns.size());
        for (String col : columns.keySet())
            assertFalse(LAYERED.matcher(col).matches());
    }

    public void testComplexLayerExport() {
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "Florilegium", "LR", "1",
                "src/TestFiles/florilegium_tei_ps.xml", "teips");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        String tradId = Util.getValueFromJson(response, "tradId");

        // Split this into multiple sections - ranks 38, 156, 228
        List<SectionModel> allSections = jerseyTest.target("/tradition/" + tradId + "/sections/")
                .request()
                .get(new GenericType<>() {});
        String section1 = allSections.getFirst().getId();
        Response jerseyResponse = jerseyTest.target("/tradition/" + tradId + "/section/" + section1 + "/splitAtRank/228")
                .request()
                .post(Entity.json(null));
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        String section4 = Util.getValueFromJson(jerseyResponse, "sectionId");
        jerseyResponse = jerseyTest.target("/tradition/" + tradId + "/section/" + section1 + "/splitAtRank/156")
                .request()
                .post(Entity.json(null));
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        // String section3 = Util.getValueFromJson(jerseyResponse, "sectionId");
        jerseyResponse = jerseyTest.target("/tradition/" + tradId + "/section/" + section1 + "/splitAtRank/38")
                .request()
                .post(Entity.json(null));
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        // String section2 = Util.getValueFromJson(jerseyResponse, "sectionId");

        // Section 1 should be missing witnesses B and G
        // Section 4 should be missing all witnesses except A/B/C/P/S
        List<WitnessModel> section1Wits = jerseyTest.target("/tradition/" + tradId + "/section/" + section1 + "/witnesses")
                .request()
                .get(new GenericType<>() {});
        assertEquals(11, section1Wits.size());
        for (WitnessModel wm : section1Wits) {
            assertNotEquals("G", wm.getSigil());
            assertNotEquals("B", wm.getSigil());
        }
        List<WitnessModel> section4Wits = jerseyTest.target("/tradition/" + tradId + "/section/" + section4 + "/witnesses")
                .request()
                .get(new GenericType<>() {});
        assertEquals(5, section4Wits.size());
        List<String> expectedWits = Arrays.asList("A", "B", "C", "P", "S");
        for (WitnessModel wm : section4Wits)
            assertTrue(expectedWits.contains(wm.getSigil()));

        // Now we are ready to export and see what we get. There should be one CX section per
        // section of the tradition, with 37 + 118 + 72 + 53 = 280 ranks in all.
        jerseyResponse = jerseyTest.target("/tradition/" + tradId + "/cxjson")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        JSONObject cx = new JSONObject(jerseyResponse.readEntity(String.class));
        JSONArray cxSections = cx.getJSONArray("sections");
        assertEquals(4, cxSections.length());
        // Expected row and per-column reading counts come from the former single-table export,
        // queried one section at a time
        assertCxSection(cxSections.getJSONObject(0), 37, "{A=36, C=34, D=34, E=35, F=36, H=36, H (s.l.)=36, "
                + "K=36, P=34, Q=32, Q (a.c.)=32, S=35, T=35}");
        assertCxSection(cxSections.getJSONObject(1), 118, "{A=115, B=30, C=117, D=117, E=115, E (a.c.)=116, "
                + "E (s.l.)=116, F=114, G=114, H=116, K=62, P=117, P (a.c.)=114, Q=114, Q (a.c.)=115, "
                + "Q (margin)=115, Q (s.l.)=115, S=116, T=115}");
        assertCxSection(cxSections.getJSONObject(2), 72, "{A=66, B=66, C=66, D=66, E=66, F=72, G=66, H=66, "
                + "K=65, P=64, Q=66, S=65, T=66, T (a.c.)=66}");
        assertCxSection(cxSections.getJSONObject(3), 53, "{A=50, B=53, C=50, P=53, S=53}");
        final int totalRows = 280;
        // Join the sections up into one column per witness (and layer)
        Map<String, List<String>> table = cxConcatenatedColumns(cx);
        assertEquals(21, table.size());

        // Ensure we have all our layers
        ArrayList<String> allExpectedWits = new ArrayList<>(Arrays.asList("D", "E", "E (a.c.)", "E (s.l.)", "F", "G",
                "H", "H (s.l.)", "K", "P (a.c.)", "Q", "Q (a.c.)", "Q (s.l.)", "Q (margin)", "T", "T (a.c.)"));
        allExpectedWits.addAll(expectedWits); // A, B, C, P, S
        for (String sigil : allExpectedWits)
            assertTrue(table.containsKey(sigil));

        // Ensure that each text is correct
        for (Map.Entry<String, List<String>> col : table.entrySet()) {
            String sigil = col.getKey();
            List<String> tableTexts = col.getValue();
            assertEquals(totalRows, tableTexts.size());
            String witness = sigil;
            String layer = null;
            Matcher lm = LAYERED.matcher(sigil);
            if (lm.matches()) {
                witness = lm.group(1);
                layer = lm.group(2);
            }
            WebTarget req = jerseyTest.target("/tradition/" + tradId + "/witness/" + witness + "/readings");
            if (layer != null) {
                req = req.queryParam("layer", layer);
            }
            List<ReadingModel> witReadings = req.request().get(new GenericType<>() {});
            // Check that the first reading is at the correct rank
            Long firstRank = witReadings.getFirst().getRank();
            // Account for section splits, above
            if (witness.equals("B") || witness.equals("G"))
                firstRank += 37;
            int i = 1;
            while (i < firstRank) {
                assertNull(tableTexts.get(i-1));
                i++;
            }
            assertNotNull(tableTexts.get(i-1));
            // Check that the last reading is at the correct rank
            if (!expectedWits.contains(witness))
                for (int j = 227; j < totalRows; j++)
                    assertNull(tableTexts.get(j));

            // Check that the (non-null) reading sequences are identical
            List<String> tableRdgTexts = tableTexts.stream().filter(Objects::nonNull).collect(Collectors.toList());
            List<String> witRdgTexts = witReadings.stream().map(ReadingModel::getText).collect(Collectors.toList());
            assertEquals(sigil, witRdgTexts, tableRdgTexts);
        }
        // Set up variables for our specific witness tests
        List<String> qColumn = table.get("Q"), qAcColumn = table.get("Q (a.c.)");
        List<String> qSlColumn = table.get("Q (s.l.)"), qMarginColumn = table.get("Q (margin)");
        List<String> eColumn = table.get("E"), eAcColumn = table.get("E (a.c.)"), eSlColumn = table.get("E (s.l.)");
        List<String> hColumn = table.get("H"), hSlColumn = table.get("H (s.l.)");
        List<String> pColumn = table.get("P"), pAcColumn = table.get("P (a.c.)");
        List<String> tColumn = table.get("T"), tAcColumn = table.get("T (a.c.)");
        // Specific data sanity tests...
        // Ensure that H (s.l.) differs from H only at rank 32
        // Ensure that Q (a.c.) differs from Q only at ranks 35, 87, 110
        // Ensure that P (a.c.) differs from P only at ranks 69–71
        // Ensure that Q and E (s.l.) differ from bases only at rank 87
        // Ensure that E (a.c.) differs from E only at rank 87
        // Ensure that Q (margin) differs from Q only at rank 101
        // Ensure that T (a.c.) differs from T only at rank 200
        assertNotNull(qColumn);
        assertNotNull(qAcColumn);
        assertNotNull(qSlColumn);
        assertNotNull(qMarginColumn);
        assertNotNull(eColumn);
        assertNotNull(eAcColumn);
        assertNotNull(eSlColumn);
        assertNotNull(hColumn);
        assertNotNull(hSlColumn);
        assertNotNull(pColumn);
        assertNotNull(pAcColumn);
        assertNotNull(tColumn);
        assertNotNull(tAcColumn);
        for (int i = 0; i < totalRows; i++) {
            if (i == 31) {
                assertTrue(checkRdgSame(qColumn, qAcColumn, i));
                assertTrue(checkRdgSame(qColumn, qSlColumn, i));
                assertTrue(checkRdgSame(qColumn, qMarginColumn, i));
                assertTrue(checkRdgSame(eColumn, eAcColumn, i));
                assertTrue(checkRdgSame(eColumn, eSlColumn, i));
                assertFalse(checkRdgSame(hColumn, hSlColumn, i));
                assertTrue(checkRdgSame(pColumn, pAcColumn, i));
                assertTrue(checkRdgSame(tColumn, tAcColumn, i));
            } else if (i == 34) {
                assertFalse(checkRdgSame(qColumn, qAcColumn, i));
                assertTrue(checkRdgSame(qColumn, qSlColumn, i));
                assertTrue(checkRdgSame(qColumn, qMarginColumn, i));
                assertTrue(checkRdgSame(eColumn, eAcColumn, i));
                assertTrue(checkRdgSame(eColumn, eSlColumn, i));
                assertTrue(checkRdgSame(hColumn, hSlColumn, i));
                assertTrue(checkRdgSame(pColumn, pAcColumn, i));
                assertTrue(checkRdgSame(tColumn, tAcColumn, i));
            } else if (i > 67 && i < 71) {
                assertTrue(checkRdgSame(qColumn, qAcColumn, i));
                assertTrue(checkRdgSame(qColumn, qSlColumn, i));
                assertTrue(checkRdgSame(qColumn, qMarginColumn, i));
                assertTrue(checkRdgSame(eColumn, eAcColumn, i));
                assertTrue(checkRdgSame(eColumn, eSlColumn, i));
                assertTrue(checkRdgSame(hColumn, hSlColumn, i));
                assertFalse(checkRdgSame(pColumn, pAcColumn, i));
                assertTrue(checkRdgSame(tColumn, tAcColumn, i));
            } else if (i == 86) {
                assertFalse(checkRdgSame(qColumn, qAcColumn, i));
                assertFalse(checkRdgSame(qColumn, qSlColumn, i));
                assertTrue(checkRdgSame(qColumn, qMarginColumn, i));
                assertFalse(checkRdgSame(eColumn, eAcColumn, i));
                assertFalse(checkRdgSame(eColumn, eSlColumn, i));
                assertTrue(checkRdgSame(hColumn, hSlColumn, i));
                assertTrue(checkRdgSame(pColumn, pAcColumn, i));
                assertTrue(checkRdgSame(tColumn, tAcColumn, i));
            } else if (i == 100) {
                assertTrue(checkRdgSame(qColumn, qAcColumn, i));
                assertTrue(checkRdgSame(qColumn, qSlColumn, i));
                assertFalse(checkRdgSame(qColumn, qMarginColumn, i));
                assertTrue(checkRdgSame(eColumn, eAcColumn, i));
                assertTrue(checkRdgSame(eColumn, eSlColumn, i));
                assertTrue(checkRdgSame(hColumn, hSlColumn, i));
                assertTrue(checkRdgSame(pColumn, pAcColumn, i));
                assertTrue(checkRdgSame(tColumn, tAcColumn, i));
            } else if (i == 109) {
                assertFalse(checkRdgSame(qColumn, qAcColumn, i));
                assertTrue(checkRdgSame(qColumn, qSlColumn, i));
                assertTrue(checkRdgSame(qColumn, qMarginColumn, i));
                assertTrue(checkRdgSame(eColumn, eAcColumn, i));
                assertTrue(checkRdgSame(eColumn, eSlColumn, i));
                assertTrue(checkRdgSame(hColumn, hSlColumn, i));
                assertTrue(checkRdgSame(pColumn, pAcColumn, i));
                assertTrue(checkRdgSame(tColumn, tAcColumn, i));
            } else if (i == 199) {
                assertTrue(checkRdgSame(qColumn, qAcColumn, i));
                assertTrue(checkRdgSame(qColumn, qSlColumn, i));
                assertTrue(checkRdgSame(qColumn, qMarginColumn, i));
                assertTrue(checkRdgSame(eColumn, eAcColumn, i));
                assertTrue(checkRdgSame(eColumn, eSlColumn, i));
                assertTrue(checkRdgSame(hColumn, hSlColumn, i));
                assertTrue(checkRdgSame(pColumn, pAcColumn, i));
                assertFalse(checkRdgSame(tColumn, tAcColumn, i));
            } else {
                assertTrue(checkRdgSame(qColumn, qAcColumn, i));
                assertTrue(checkRdgSame(qColumn, qSlColumn, i));
                assertTrue(checkRdgSame(qColumn, qMarginColumn, i));
                assertTrue(checkRdgSame(eColumn, eAcColumn, i));
                assertTrue(checkRdgSame(eColumn, eSlColumn, i));
                assertTrue(checkRdgSame(hColumn, hSlColumn, i));
                assertTrue(checkRdgSame(pColumn, pAcColumn, i));
                assertTrue(checkRdgSame(tColumn, tAcColumn, i));
            }
        }


        // Test export when we exclude the layers
        response = jerseyTest.target("/tradition/" + tradId + "/cxjson")
                .queryParam("exclude_layers", "true")
                .request().get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        JSONObject unlayeredCx = new JSONObject(response.readEntity(String.class));
        JSONArray unlayeredSections = unlayeredCx.getJSONArray("sections");
        assertEquals(4, unlayeredSections.length());
        assertCxSection(unlayeredSections.getJSONObject(0), 37, "{A=36, C=34, D=34, E=35, F=36, H=36, K=36, "
                + "P=34, Q=32, S=35, T=35}");
        assertCxSection(unlayeredSections.getJSONObject(1), 118, "{A=115, B=30, C=117, D=117, E=115, F=114, "
                + "G=114, H=116, K=62, P=117, Q=114, S=116, T=115}");
        assertCxSection(unlayeredSections.getJSONObject(2), 72, "{A=66, B=66, C=66, D=66, E=66, F=72, G=66, "
                + "H=66, K=65, P=64, Q=66, S=65, T=66}");
        assertCxSection(unlayeredSections.getJSONObject(3), 53, "{A=50, B=53, C=50, P=53, S=53}");
        Map<String, List<String>> unlayered = cxConcatenatedColumns(unlayeredCx);
        assertEquals(13, unlayered.size());
        // Check that the readings are the same as above
        HashMap<String,List<String>> priors = new HashMap<>();
        priors.put("Q", qColumn);
        priors.put("E", eColumn);
        priors.put("H", hColumn);
        priors.put("P", pColumn);
        priors.put("T", tColumn);
        for (Map.Entry<String, List<String>> col : unlayered.entrySet()) {
            assertEquals(totalRows, col.getValue().size());
            if (priors.containsKey(col.getKey())) {
                for (int i = 0; i < totalRows; i++)
                    assertTrue(checkRdgSame(priors.get(col.getKey()), col.getValue(), i));
            }
        }
    }

    private boolean checkRdgSame(List<String> c1, List<String> c2, int index) {
        return Objects.equals(c1.get(index), c2.get(index));
    }

    public void testNoLeakAfterExport() {
        String tradId = createCsvTradition("A,B\nx,x\ny,\nz,w");
        String sectId = Util.getSingleSection(jerseyTest, tradId).getId();
        JSONObject cx = fetchSectionCx(tradId, sectId, null, false);
        assertEquals(3, cx.getJSONArray("table").length());
        try (Response response = jerseyTest.target("/tradition/" + tradId).request().delete()) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        }
    }

    // Round trips

    private Map<String, String> witnessTexts(String tradId, String sectionId, String... layers) {
        List<WitnessModel> wits = jerseyTest.target("/tradition/" + tradId + "/section/" + sectionId + "/witnesses")
                .request().get(new GenericType<>() {});
        Map<String, String> result = new TreeMap<>();
        for (WitnessModel wm : wits) {
            var target = jerseyTest.target("/tradition/" + tradId + "/section/" + sectionId
                    + "/witness/" + wm.getSigil() + "/text");
            for (String l : layers) target = target.queryParam("layer", l);
            Response r = target.request().get();
            assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
            result.put(wm.getSigil(), new JSONObject(r.readEntity(String.class)).getString("text"));
        }
        return result;
    }

    private String reimport(String tradId, String name) {
        Response r = getTraditionJson(tradId);
        assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
        String exported = r.readEntity(String.class);
        Response up = Util.createTraditionFromFileOrString(jerseyTest, name, "LR", "1", exported, "cxjson");
        assertEquals(Response.Status.CREATED.getStatusCode(), up.getStatus());
        return Util.getValueFromJson(up, "tradId");
    }

    private List<SectionModel> sectionModels(String tradId) {
        return jerseyTest.target("/tradition/" + tradId + "/sections")
                .request().get(new GenericType<>() {});
    }

    private void assertSectionTextsEqual(String origId, String newId, String... layers) {
        List<SectionModel> orig = sectionModels(origId);
        List<SectionModel> copy = sectionModels(newId);
        assertEquals(orig.size(), copy.size());
        for (int i = 0; i < orig.size(); i++) {
            assertEquals(orig.get(i).getName(), copy.get(i).getName());
            Map<String, String> expected = witnessTexts(origId, orig.get(i).getId(), layers);
            assertFalse(expected.isEmpty());
            assertEquals("section " + i, expected, witnessTexts(newId, copy.get(i).getId(), layers));
        }
    }

    public void testRoundTripChronicle() {
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "Chronicle", "LR", "1",
                "src/TestFiles/ChronicleOfMatthew.xml", "stemmaweb");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        String origId = Util.getValueFromJson(response, "tradId");
        String newId = reimport(origId, "Chronicle copy");
        assertSectionTextsEqual(origId, newId);
    }

    public void testRoundTripLayers() {
        String origId = createCsvTradition("A,A (a.c.),B\nx,y,x\nz,z,w");
        String newId = reimport(origId, "Layers copy");
        assertSectionTextsEqual(origId, newId);
        assertSectionTextsEqual(origId, newId, "a.c.");
        String sect = sectionModels(newId).getFirst().getId();
        assertEquals("y z", witnessTexts(newId, sect, "a.c.").get("A"));
        assertEquals("x z", witnessTexts(newId, sect).get("A"));
    }

    private List<String> lacunaSignature(String tradId, String sectId) {
        List<ReadingModel> readings = jerseyTest.target("/tradition/" + tradId + "/section/" + sectId + "/readings")
                .request().get(new GenericType<>() {});
        List<String> sig = new ArrayList<>();
        for (ReadingModel rm : readings)
            sig.add(rm.getText() + "|" + rm.getRank() + "|" + rm.getIs_lacuna());
        Collections.sort(sig);
        return sig;
    }

    public void testRoundTripLacunae() {
        String origId = createCsvTradition(
                "A,B\nx,x\n#LACUNA#,y\n#LACUNA#,z\n#LACUNA#,z2\nw,w\nv,#LACUNA#\n");
        String newId = reimport(origId, "Lacunae copy");
        String origSect = sectionModels(origId).getFirst().getId();
        String newSect = sectionModels(newId).getFirst().getId();
        assertEquals(lacunaSignature(origId, origSect), lacunaSignature(newId, newSect));
        assertEquals(witnessTexts(origId, origSect), witnessTexts(newId, newSect));
    }

    public void testRoundTripFixture() {
        String origId = uploadMultiSectionFixture();
        String newId = reimport(origId, "Multi copy");
        assertEquals(5, sectionModels(newId).size());
        assertSectionTextsEqual(origId, newId);
    }
}
