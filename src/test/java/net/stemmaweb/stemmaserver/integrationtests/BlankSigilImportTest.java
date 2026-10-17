package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import net.stemmaweb.model.SectionModel;
import net.stemmaweb.model.StemmaModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;

/**
 * Older versions of Stemmarest allowed a witness with an empty sigil to be created, and such
 * witnesses turn up in legacy exports. No import path may create one any more. The fixtures
 * named {@code blanksigil*} are copies of existing fixtures with a single sigil blanked out.
 * (TEI imports take their sigla from xml:id attributes, which cannot be empty, and the legacy
 * Stemmaweb format is going away, so neither is covered here.)
 */
public class BlankSigilImportTest {

    private static final String FIXTURES = "src/TestFiles/";

    private JerseyTest jerseyTest;

    @Before
    public void setUp() throws Exception {
        DatabaseManagementService dbbuilder = new TestDatabaseManagementServiceBuilder().impermanent().build();
        GraphDatabaseService db = dbbuilder.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME);
        new GraphDatabaseServiceProvider(dbbuilder, db);
        Util.setupTestDB(db, "1");
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(Root.class)
                .create();
        jerseyTest.setUp();
    }

    // Import a file as a new tradition; it should be refused, and nothing should be left behind.
    private void assertTraditionImportRefused(String fixture, String filetype) {
        try (Response r = Util.createTraditionFromFileOrString(jerseyTest, "Blank sigil", "LR", "1",
                FIXTURES + fixture, filetype)) {
            assertRefusedForSigil(r);
        }
        List<TraditionModel> traditions = jerseyTest.target("/user/1/traditions")
                .request().get(new GenericType<>() {});
        assertTrue("A failed import left a tradition behind", traditions.isEmpty());
    }

    // Import a file as a new section of an existing tradition; it should be refused, and the
    // tradition should be exactly as it was.
    private void assertSectionImportRefused(String fixture, String filetype) {
        String tradId = baseTradition();
        List<WitnessModel> witnessesBefore = witnesses(tradId);
        try (Response r = Util.addSectionToTradition(jerseyTest, tradId, FIXTURES + fixture, filetype, "Blank")) {
            assertRefusedForSigil(r);
        }
        List<SectionModel> sections = jerseyTest.target("/tradition/" + tradId + "/sections")
                .request().get(new GenericType<>() {});
        assertEquals("A failed import left a section behind", 1, sections.size());
        assertEquals("A failed import changed the tradition's witnesses",
                witnessesBefore.size(), witnesses(tradId).size());
    }

    // Add a stemma to an existing tradition; it should be refused, and the tradition should be
    // exactly as it was.
    private void assertStemmaImportRefused(StemmaModel sm) {
        String tradId = baseTradition();
        List<WitnessModel> witnessesBefore = witnesses(tradId);
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/stemma")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(sm))) {
            assertRefusedForSigil(r);
        }
        List<StemmaModel> stemmata = jerseyTest.target("/tradition/" + tradId + "/stemmata")
                .request().get(new GenericType<>() {});
        assertTrue("A failed import left a stemma behind", stemmata.isEmpty());
        assertEquals("A failed import changed the tradition's witnesses",
                witnessesBefore.size(), witnesses(tradId).size());
    }

    // A tradition with witnesses A, B, C and no stemmata, to import things into.
    private String baseTradition() {
        try (Response r = Util.createTraditionFromFileOrString(jerseyTest, "Base", "LR", "1",
                FIXTURES + "florilegium_simple.csv", "csv")) {
            assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
            return Util.getValueFromJson(r, "tradId");
        }
    }

    private List<WitnessModel> witnesses(String tradId) {
        return jerseyTest.target("/tradition/" + tradId + "/witnesses")
                .request().get(new GenericType<>() {});
    }

    // A 400, and one that says what the problem was
    private void assertRefusedForSigil(Response r) {
        String body = r.readEntity(String.class);
        assertEquals(body, Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        assertTrue("Error message doesn't mention the sigil: " + body,
                body.toLowerCase().contains("sigil"));
    }

    private StemmaModel stemmaFromDot(String fixture) throws Exception {
        StemmaModel sm = new StemmaModel();
        sm.setName("Blank");
        sm.setDot(Files.readString(Paths.get(FIXTURES + fixture), StandardCharsets.UTF_8));
        return sm;
    }

    // ---- New traditions ----

    @Test
    public void collatexBlankSigil() {
        assertTraditionImportRefused("blanksigil-collatex.xml", "collatex");
    }

    @Test
    public void collatexJsonBlankSigil() {
        assertTraditionImportRefused("blanksigil-cx.json", "cxjson");
    }

    @Test
    public void csvBlankSigil() {
        assertTraditionImportRefused("blanksigil.csv", "csv");
    }

    @Test
    public void xlsxBlankSigil() {
        assertTraditionImportRefused("blanksigil.xlsx", "xlsx");
    }

    @Test
    public void xlsBlankSigil() {
        assertTraditionImportRefused("blanksigil.xls", "xls");
    }

    @Test
    public void graphmlBlankExtantSigil() {
        assertTraditionImportRefused("blanksigil-extant-graphml.zip", "graphml");
    }

    @Test
    public void graphmlBlankHypotheticalSigil() {
        assertTraditionImportRefused("blanksigil-hypothetical-graphml.zip", "graphml");
    }

    @Test
    public void graphmlSingleBlankSigil() {
        assertTraditionImportRefused("blanksigil-section-graphml.xml", "graphmlsingle");
    }

    // ---- New sections of an existing tradition ----

    @Test
    public void sectionCollatexBlankSigil() {
        assertSectionImportRefused("blanksigil-collatex.xml", "collatex");
    }

    @Test
    public void sectionCsvBlankSigil() {
        assertSectionImportRefused("blanksigil.csv", "csv");
    }

    @Test
    public void sectionGraphmlSingleBlankSigil() {
        assertSectionImportRefused("blanksigil-section-graphml.xml", "graphmlsingle");
    }

    // ---- Stemmata ----

    @Test
    public void dotBlankExtantSigil() throws Exception {
        assertStemmaImportRefused(stemmaFromDot("blanksigil-extant.dot"));
    }

    @Test
    public void dotBlankHypotheticalSigil() throws Exception {
        assertStemmaImportRefused(stemmaFromDot("blanksigil-hypothetical.dot"));
    }

    @Test
    public void newickBlankLeafSigil() {
        StemmaModel sm = new StemmaModel();
        sm.setName("Blank");
        sm.setNewick("((A,),C);");
        assertStemmaImportRefused(sm);
    }

    @Test
    public void newickBlankFirstLeafSigil() {
        StemmaModel sm = new StemmaModel();
        sm.setName("Blank");
        sm.setNewick("(,B,C);");
        assertStemmaImportRefused(sm);
    }

    // ---- Whitespace-only sigla (a CollateX XML or GraphML witness *list* cannot express these,
    // as the list separator swallows the whitespace, so only single-value fields are tested) ----

    @Test
    public void collatexJsonSpaceSigil() {
        assertTraditionImportRefused("blanksigil-space-cx.json", "cxjson");
    }

    @Test
    public void csvSpaceSigil() {
        assertTraditionImportRefused("blanksigil-space.csv", "csv");
    }

    @Test
    public void sectionCsvSpaceSigil() {
        assertSectionImportRefused("blanksigil-space.csv", "csv");
    }

    @Test
    public void xlsxSpaceSigil() {
        assertTraditionImportRefused("blanksigil-space.xlsx", "xlsx");
    }

    @Test
    public void graphmlSpaceExtantSigil() {
        assertTraditionImportRefused("blanksigil-space-extant-graphml.zip", "graphml");
    }

    @Test
    public void graphmlSpaceHypotheticalSigil() {
        assertTraditionImportRefused("blanksigil-space-hypothetical-graphml.zip", "graphml");
    }

    @Test
    public void dotSpaceExtantSigil() throws Exception {
        assertStemmaImportRefused(stemmaFromDot("blanksigil-space-extant.dot"));
    }

    @Test
    public void dotSpaceHypotheticalSigil() throws Exception {
        assertStemmaImportRefused(stemmaFromDot("blanksigil-space-hypothetical.dot"));
    }

    @Test
    public void newickSpaceLeafSigil() {
        StemmaModel sm = new StemmaModel();
        sm.setName("Blank");
        sm.setNewick("((A, ),C);");
        assertStemmaImportRefused(sm);
    }

    @After
    public void tearDown() throws Exception {
        GraphDatabaseServiceProvider.shutdown();
        jerseyTest.tearDown();
    }
}
