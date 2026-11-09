package net.stemmaweb.stemmaserver.integrationtests;

import java.util.ArrayList;
import java.util.List;

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
import jakarta.ws.rs.core.Response;
import junit.framework.TestCase;
import net.stemmaweb.model.RelationModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.VariantGraphService;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;

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

    public void testNoLeakAfterExport() {
        String tradId = createCsvTradition("A,B\nx,x\ny,\nz,w");
        String sectId = Util.getSingleSection(jerseyTest, tradId).getId();
        JSONObject cx = fetchSectionCx(tradId, sectId, null, false);
        assertEquals(3, cx.getJSONArray("table").length());
        try (Response response = jerseyTest.target("/tradition/" + tradId).request().delete()) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        }
    }
}
