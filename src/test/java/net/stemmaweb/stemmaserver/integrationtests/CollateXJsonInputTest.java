package net.stemmaweb.stemmaserver.integrationtests;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.test.JerseyTest;
import org.json.JSONObject;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Traverser;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import junit.framework.TestCase;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.SectionModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.VariantGraphService;
import net.stemmaweb.stemmaserver.Util;

public class CollateXJsonInputTest extends TestCase {

    private GraphDatabaseService db;
    private JerseyTest jerseyTest;

    private String tradId;
    private String sectId;


    public void setUp() throws Exception {
        super.setUp();
//        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        DatabaseManagementService dbbuilder = new TestDatabaseManagementServiceBuilder().impermanent().build();
        db = dbbuilder.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME);
        new GraphDatabaseServiceProvider(dbbuilder, db);
        Util.setupTestDB(db, "1");

        // Create a JerseyTestServer for the necessary REST API calls
        jerseyTest = Util.setupJersey();

        Response jerseyResult = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/Matthew-407.json", "cxjson");
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        tradId = Util.getValueFromJson(jerseyResult, "tradId");
        List<SectionModel> testSections = jerseyTest
                .target("/tradition/" + tradId + "/sections")
                .request()
                .get(new GenericType<>() {});
        sectId = testSections.getFirst().getId();
    }

    public void testParseCollateX() {
        // Check that the name came through
        SectionModel theSection = jerseyTest
                .target("/tradition/" + tradId + "/section/" + sectId)
                .request()
                .get(SectionModel.class);
        assertEquals(theSection.getId(), sectId);
        assertEquals("Anno CDVII", theSection.getName());
        // Check for correct number of readings and ranks
        List<ReadingModel> allreadings = jerseyTest
                .target("/tradition/" + tradId + "/readings")
                .request()
                .get(new GenericType<>() {});
        assertEquals(381, allreadings.size());
        int common = 0;
        for (ReadingModel r: allreadings) {
            if (r.getIs_end())
                assertEquals(87L, r.getRank(), 0);
            else if (r.getIs_common() && !r.getIs_start())
                common++;
        }
        assertEquals(15, common);

        // Check for witnesses and their layers
        List<WitnessModel> allwits = jerseyTest
                .target("/tradition/" + tradId + "/witnesses")
                .request()
                .get(new GenericType<>() {});
        assertEquals(22, allwits.size());


        // Dive into the database and check that there are no redundant witness paths
        try (Transaction tx = db.beginTx()) {
            List<Relationship> sequences =
//            		VariantGraphService.returnTraditionSection(sectId, db).relationships().stream()
            		StreamSupport.stream(VariantGraphService.returnTraditionSection(tx, sectId).relationships().spliterator(), false)
            		.filter(x -> x.getType().toString().equals("SEQUENCE")).collect(Collectors.toList());
            for (Relationship r : sequences) {
                if (r.hasProperty("witnesses")) {
                    ArrayList<String> mainwits = new ArrayList<>(Arrays.asList((String[]) r.getProperty("witnesses")));
                    for (String p : r.getPropertyKeys()) {
                        if (p.equals("witnesses")) continue;
                        for (String w : (String[]) r.getProperty(p)) {
                            if (mainwits.contains(w)) fail();
                        }
                    }
                }
            }
            tx.commit();
        }

        // Check a witness text
        String witExpected = "Դրձեալ ի թվականուես հայոց. ի ն՟է՟. ամին. զօրաժողով լինէր ազգն արապաց. ուռհա և ամ եդեսացւոց " +
                "աշխարհն ահագին բազմուբ անցեալ ընդ մեծ գետն եփրատ. և եկեալ ի վր ամուր քաղաքին որ կոչի սամուսատ. և " +
                "ե֊լաներ ի պատերազմն զօրապետն հոռոմոց որում անուն ասէին պառա֊կամանոս. այր զօրաւոր և քաջ. և ի դուռն " +
                "քաղաքին բաղխէին զմիմեանս. և աւուր այնմիկ հարին տաճկունք զօրսն հոռոմոց և արարին կոտորած մեծ առ դրան " +
                "քաղաքին և յետ աւուրց ինչ առաւ քաղաքն սամուսատ մերձ ի քաղաքն ուռհա։";
        String witAcExpected = "Դրձեալ ի թվականուես հայոց. ի ն՟է՟. ամին. զօրաժողով լինէր ազգն արապաց. ուռհա և ամ եդեսացւոց " +
                "աշխարհն ահագին բազմուբ անցեալ ընդ մեծ գետն եփրատ. և եկեալ ի վր ամուր քաղաքին որ կոչի սամուսատ. և " +
                "ե֊լաներ ի պատերազմն զօրապետն հոռոմոց որում անուն ասէին պառա֊կամանոս. այր զօրաւոր և քաջ. և ի դուռն " +
                "քաղաքին բաղխէին զմիմեանս. և աւուր այնմիկ տաճկունք զօրսն հոռոմոց և արարին կոտորած մեծ առ դրան քաղաքին և " +
                "յետ աւուրց ինչ առաւ քաղաքն սամուսատ մերձ ի քաղաքն ուռհա։";
        String witObtained = Util.getValueFromJson(jerseyTest
                .target("/tradition/" + tradId + "/section/" + sectId + "/witness/G/text")
                .request()
                .get(), "text");
        String witAcObtained = Util.getValueFromJson(jerseyTest
                .target("/tradition/" + tradId + "/section/" + sectId + "/witness/G/text")
                .queryParam("layer", "a.c.")
                .request()
                .get(), "text");
        assertEquals(witExpected, witObtained);
        assertEquals(witAcExpected, witAcObtained);

        // Check that page information etc. is preserved
        String firstPage = "004";
        String firstLine = "l102379579";
        String firstContext = "text/body/ab";
        List<ReadingModel> gReadings = jerseyTest
                .target("/tradition/" + tradId + "/witness/G/readings")
                .request()
                .get(new GenericType<>() {});
        assertEquals(77, gReadings.size());
        JSONObject token = new JSONObject(gReadings.get(0).getExtra());
        assertEquals(firstPage, token.getJSONObject("G").getJSONObject("page").getString("n"));
        assertEquals(firstLine, token.getJSONObject("G").getJSONObject("line").getString("xml:id"));
        assertEquals(firstContext, token.getJSONObject("G").getString("context"));

        // Check that the display token is used in the .dot output, if it exists
        Response resp = jerseyTest
                .target("/tradition/" + tradId + "/section/" + sectId + "/dot")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), resp.getStatus());
        String dotStr = resp.readEntity(String.class);
        assertTrue(dotStr.contains("label=\"Դարձեալ\"];")); // no HTML
        assertTrue(dotStr.contains("label=\"&\"];")); // no HTML, no escaping
        assertTrue(dotStr.contains("label=<Դարձ<O>ել</O>>];")); // HTML, no quotes

        // Now check that everything works with normal forms turned on
        resp = jerseyTest
                .target("/tradition/" + tradId + "/section/" + sectId + "/dot")
                .queryParam("show_normal", "true")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), resp.getStatus());
        dotStr = resp.readEntity(String.class);
        assertTrue(dotStr.contains("label=<<FONT COLOR=\"red\">Դ</FONT>արձեալ>];")); // HTML, no normal form
        assertTrue(dotStr.contains("label=<Դ<O>ր</O>ձլ<BR/><FONT COLOR=\"grey\">Դարձեալ</FONT>>];")); // HTML, normal form
        assertTrue(dotStr.contains("label=\"Դարձեալ\"];"));  // no HTML, no normal form
        assertTrue(dotStr.contains("label=<Դարձեալ<BR/><FONT COLOR=\"grey\">դարձեալ</FONT>>];")); // no HTML, normal form
        assertTrue(dotStr.contains("label=<&amp;<BR/><FONT COLOR=\"grey\">և</FONT>>];")); // no HTML / HTML escape, normal form

    }

    public void testParseCollateXSectionNameInRequest() {
        String newTradId;
        try (Response r = Util.createTraditionFromFileOrString(jerseyTest, "Chronicle of Matthew",
                "Anno 401", "LR", "1", "src/TestFiles/Matthew-401.json", "cxjson"))
        {
            assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
            newTradId = Util.getValueFromJson(r, "tradId");
        }
        try (Response r = jerseyTest.target("/tradition/" + newTradId + "/sections").request().get()) {
            assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
            List<SectionModel> allSections = r.readEntity(new GenericType<>() {});
            assertEquals(1, allSections.size());
            assertEquals("Anno 401", allSections.getFirst().getName());
        }
    }

    public void testParseCollateXSectionNameDefault() {
        String newTradId;
        try (Response r = Util.createTraditionFromFileOrString(jerseyTest, "Chronicle of Matthew",
                 "LR", "1", "src/TestFiles/Matthew-401.json", "cxjson"))
        {
            assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
            newTradId = Util.getValueFromJson(r, "tradId");
        }
        try (Response r = jerseyTest.target("/tradition/" + newTradId + "/sections").request().get()) {
            assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
            List<SectionModel> allSections = r.readEntity(new GenericType<>() {});
            assertEquals(1, allSections.size());
            assertEquals("Chronicle of Matthew", allSections.getFirst().getName());
        }
    }

    public void testNoRedundantWitnesses() {
        try (Transaction tx = db.beginTx()) {
        	Traverser sTrav = VariantGraphService.returnTraditionSection(tx, sectId);
            for (Relationship r : sTrav.relationships())
                if (r.getType().equals(ERelations.SEQUENCE) && r.hasProperty("witnesses")) {
                    Iterable<String> layers = r.getPropertyKeys();
                    for (String w : (String[]) r.getProperty("witnesses"))
                        for (String l : layers)
                            if (!l.equals("witnesses")) {
                                ArrayList<String> ew = new ArrayList<>(Arrays.asList((String[]) r.getProperty(l)));
                                assertFalse(ew.contains(w));
                            }
                }
            tx.commit();
        }
    }

    public void testAddSection() {
        // Add another section
        String newSectId = Util.getValueFromJson(Util.addSectionToTradition(jerseyTest, tradId,
                "src/TestFiles/Matthew-401.json", "cxjson", "AM 401"), "sectionId");
        Response response = jerseyTest
                .target("/tradition/" + tradId + "/section/" + sectId + "/orderAfter/" + newSectId)
                .request()
                .put(Entity.text(""));
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<SectionModel> ourSections = jerseyTest
                .target("/tradition/" + tradId + "/sections/")
                .request()
                .get(new GenericType<>() {});
        assertEquals(2, ourSections.size());
        assertEquals(newSectId, ourSections.get(0).getId());
        assertEquals(sectId, ourSections.get(1).getId());
        assertEquals("AM 401", ourSections.get(0).getName());

        List<WitnessModel> allwits = jerseyTest
                .target("/tradition/" + tradId + "/witnesses")
                .request()
                .get(new GenericType<>() {});
        assertEquals(23, allwits.size());
    }

    public void testSectionBySection() {
        FormDataMultiPart form = new FormDataMultiPart();
        // Make an empty tradition
        form.field("empty", "true");
        form.field("name", "ժամանակագրութիւն Մատթէոսի Ուռհայեցւոյ");
        form.field("userId", "1");
        String newTradId = Util.getValueFromJson(jerseyTest
                .target("/tradition")
                .request()
                .post(Entity.entity(form, MediaType.MULTIPART_FORM_DATA_TYPE)), "tradId");
        List<WitnessModel> allwits = jerseyTest
                .target("/tradition/" + newTradId + "/witnesses")
                .request()
                .get(new GenericType<>() {});
        assertEquals(0, allwits.size());

        // Add the first section and check number of witnesses
        String firstSect = Util.getValueFromJson(Util.addSectionToTradition(jerseyTest, newTradId,
                "src/TestFiles/Matthew-401.json", "cxjson", "AM 401"), "sectionId");
        allwits = jerseyTest
                .target("/tradition/" + newTradId + "/witnesses")
                .request()
                .get(new GenericType<>() {});
        assertEquals(15, allwits.size());

        // Add the second section and do the same
        String secondSect = Util.getValueFromJson(Util.addSectionToTradition(jerseyTest, newTradId,
                "src/TestFiles/Matthew-407.json", "cxjson", "AM 407"), "sectionId");
        allwits = jerseyTest
                .target("/tradition/" + newTradId + "/witnesses")
                .request()
                .get(new GenericType<>() {});
        assertEquals(23, allwits.size());

        // Check section ordering
        List<SectionModel> ourSections = jerseyTest
                .target("/tradition/" + newTradId + "/sections/")
                .request()
                .get(new GenericType<>() {});
        assertEquals(2, ourSections.size());
        assertEquals(firstSect, ourSections.get(0).getId());
        assertEquals(secondSect, ourSections.get(1).getId());
        // Check that section names were according to the REST request
        assertEquals("AM 401", ourSections.get(0).getName());
        assertEquals("AM 407", ourSections.get(1).getName());
    }

    public void testInputInput() {
        Response jerseyResult = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/collatex-input.json", "cxjson");
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), jerseyResult.getStatus());
        String msg = Util.getValueFromJson(jerseyResult, "error");
        assertEquals("Bad format: is this CollateX JSON input instead of output?", msg);
    }

    public void testMissingFiletype() {
        // Upload a file with no filetype parameter — should get 400 with jsonerror, not 500
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "TestTradition", "LR", "1",
                "src/TestFiles/Matthew-407.json", null);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        String errorMsg = Util.getValueFromJson(response, "error");
        assertTrue(errorMsg != null && errorMsg.contains("filetype"));
    }

    public void testNullCells() {
        Response r = Util.createTraditionFromFileOrString(jerseyTest, "Nulls", "LR", "1",
                "src/TestFiles/repro/cx_null_cell.json", "cxjson");
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        String newTradId = Util.getValueFromJson(r, "tradId");
        String[][] expected = {
                {"B", "Իսկ ընդ աւուրսն և ի թվականութիւն ՆԱ"},
                {"D", "Իսկ ընդ ի թուականու՟ի ՆԱ"},
                {"Q", "Իսկ ընդ աւուրսնս այսոսիկ"}};
        for (String[] e : expected) {
            Response tr = jerseyTest.target("/tradition/" + newTradId + "/witness/" + e[0] + "/text")
                    .request().get();
            assertEquals(Response.Status.OK.getStatusCode(), tr.getStatus());
            assertEquals(e[1], Util.getValueFromJson(tr, "text"));
        }
    }

    public void testNKeyIsNormalForm() {
        String cx = "{\"witnesses\":[\"A\",\"B\"],\"table\":[[[{\"t\":\"Colour \",\"n\":\"color\"}],"
                + "[{\"t\":\"color \",\"n\":\"color\"}]]]}";
        Response r = Util.createTraditionFromFileOrString(jerseyTest, "NKey", "LR", "1", cx, "cxjson");
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        String newTradId = Util.getValueFromJson(r, "tradId");
        List<ReadingModel> rdgs = jerseyTest.target("/tradition/" + newTradId + "/readings")
                .request().get(new GenericType<>() {});
        int seen = 0;
        for (ReadingModel rm : rdgs) {
            if (rm.getIs_start() || rm.getIs_end()) continue;
            seen++;
            assertEquals("color", rm.getNormal_form());
            assertNull(rm.getExtra());
        }
        assertEquals(2, seen);
    }

    public void testBadCxInputIs400() {
        int before = jerseyTest.target("/traditions").request().get(new GenericType<List<TraditionModel>>() {}).size();
        String[] bad = {
                "[]",
                "{\"witnesses\":[],\"table\":[]}",
                "{\"witnesses\":[\"A\",\"B\"],\"table\":[[[{\"t\":\"x\"}]]]}",
                "{\"witnesses\":[\"A\"],\"table\":[[[{\"t\":\"x\"}],[{\"t\":\"y\"}]]]}",
                "{\"witnesses\":[\"A\"],\"table\":[[\"notarray\"]]}",
                "{\"witnesses\":",
                "src/TestFiles/repro/cx_no_rank.xml",
                "src/TestFiles/repro/tei_app_only.xml"};
        for (String b : bad) {
            Response r = Util.createTraditionFromFileOrString(jerseyTest, "Bad", "LR", "1", b, "cxjson");
            assertEquals(b, Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
            String msg = Util.getValueFromJson(r, "error");
            assertNotNull(b, msg);
            int after = jerseyTest.target("/traditions").request().get(new GenericType<List<TraditionModel>>() {}).size();
            assertEquals(b, before, after);
        }
    }

    public void testLacunaRunsMatchCsvImport() {
        Response csv = Util.createTraditionFromFileOrString(jerseyTest, "Lacuna CSV", "LR", "1",
                "A,B\nx,x\n#LACUNA#,y\n#LACUNA#,z\n#LACUNA#,z2\nw,w\nv,#LACUNA#\n", "csv");
        assertEquals(Response.Status.CREATED.getStatusCode(), csv.getStatus());
        String csvId = Util.getValueFromJson(csv, "tradId");
        Response cx = Util.createTraditionFromFileOrString(jerseyTest, "Lacuna CX", "LR", "1",
                "{\"witnesses\":[\"A\",\"B\"],\"table\":[[[{\"t\":\"x\"}],[{\"t\":\"x\"}]],"
                        + "[[{\"t\":\"#LACUNA#\"}],[{\"t\":\"y\"}]],[[{\"t\":\"#LACUNA#\"}],[{\"t\":\"z\"}]],"
                        + "[[{\"t\":\"#LACUNA#\"}],[{\"t\":\"z2\"}]],[[{\"t\":\"w\"}],[{\"t\":\"w\"}]],"
                        + "[[{\"t\":\"v\"}],[{\"t\":\"#LACUNA#\"}]]]}", "cxjson");
        assertEquals(Response.Status.CREATED.getStatusCode(), cx.getStatus());
        String cxId = Util.getValueFromJson(cx, "tradId");

        List<ReadingModel> csvReadings = jerseyTest.target("/tradition/" + csvId + "/readings")
                .request().get(new GenericType<>() {});
        List<ReadingModel> cxReadings = jerseyTest.target("/tradition/" + cxId + "/readings")
                .request().get(new GenericType<>() {});
        assertEquals(summarise(csvReadings), summarise(cxReadings));
        List<ReadingModel> lacunae = cxReadings.stream().filter(r -> Boolean.TRUE.equals(r.getIs_lacuna())).toList();
        assertEquals(2, lacunae.size());
        assertTrue(lacunae.stream().anyMatch(r -> r.getRank() == 2L));
        assertTrue(lacunae.stream().anyMatch(r -> r.getRank() == 6L));
    }

    private static final String MULTI = "src/TestFiles/cxjson_multi.json";

    private Response uploadWrapper(String tName, String content) {
        return Util.createTraditionFromFileOrString(jerseyTest, tName, "LR", "1", content, "cxjson");
    }

    public void testMultiSectionUpload() throws Exception {
        Response r = uploadWrapper("", MULTI);
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        String newId = Util.getValueFromJson(r, "tradId");
        List<SectionModel> sections = jerseyTest.target("/tradition/" + newId + "/sections")
                .request().get(new GenericType<>() {});
        assertEquals(List.of("first section", "second section", "third section", "fourth section", "fifth section"),
                sections.stream().map(SectionModel::getName).toList());
        for (SectionModel sm : sections) {
            List<WitnessModel> wits = jerseyTest.target("/tradition/" + newId + "/section/" + sm.getId() + "/witnesses")
                    .request().get(new GenericType<>() {});
            assertEquals(List.of("A", "B", "C", "D", "F", "G", "L", "O", "Y"),
                    wits.stream().map(WitnessModel::getSigil).sorted().toList());
        }
        TraditionModel tm = jerseyTest.target("/tradition/" + newId).request().get(TraditionModel.class);
        assertEquals("many sections in collatex", tm.getName());

        // Check witness A's text in section 1 against the fixture
        JSONObject fixture = new JSONObject(java.nio.file.Files.readString(java.nio.file.Path.of(MULTI)));
        org.json.JSONArray table = fixture.getJSONArray("sections").getJSONObject(0).getJSONArray("table");
        List<String> toks = new ArrayList<>();
        for (int i = 0; i < table.length(); i++) {
            org.json.JSONArray cell = table.getJSONArray(i).getJSONArray(0);
            for (int j = 0; j < cell.length(); j++) toks.add(cell.getJSONObject(j).getString("t"));
        }
        String expected = String.join(" ", toks.stream().map(String::trim).toList()).trim();
        String got = Util.getValueFromJson(jerseyTest.target("/tradition/" + newId + "/section/"
                + sections.getFirst().getId() + "/witness/A/text").request().get(), "text");
        assertEquals(expected, got);
    }

    public void testWrapperNameDoesNotOverrideForm() {
        Response r = uploadWrapper("Mine", MULTI);
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        String newId = Util.getValueFromJson(r, "tradId");
        TraditionModel tm = jerseyTest.target("/tradition/" + newId).request().get(TraditionModel.class);
        assertEquals("Mine", tm.getName());
    }

    public void testWrapperUnnamedSectionGetsTraditionName() {
        Response r = uploadWrapper("T",
                "{\"sections\":[{\"witnesses\":[\"A\"],\"table\":[[[{\"t\":\"x\"}]]]}]}");
        assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        String newId = Util.getValueFromJson(r, "tradId");
        List<SectionModel> sections = jerseyTest.target("/tradition/" + newId + "/sections")
                .request().get(new GenericType<>() {});
        assertEquals(1, sections.size());
        assertEquals("T", sections.getFirst().getName());
    }

    public void testBadSectionRollsBack() {
        int before = jerseyTest.target("/traditions").request().get(new GenericType<List<TraditionModel>>() {}).size();
        Response r = uploadWrapper("Bad",
                "{\"sections\":[{\"witnesses\":[\"A\"],\"table\":[[[{\"t\":\"x\"}]]]},"
                        + "{\"witnesses\":[\"A\"],\"table\":[[\"notarray\"]]}]}");
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        assertTrue(r.readEntity(String.class).contains("section 2"));
        assertEquals(before, jerseyTest.target("/traditions").request()
                .get(new GenericType<List<TraditionModel>>() {}).size());

        Response empty = uploadWrapper("Empty", "{\"sections\":[]}");
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), empty.getStatus());
        assertEquals(before, jerseyTest.target("/traditions").request()
                .get(new GenericType<List<TraditionModel>>() {}).size());
    }

    public void testWrapperRejectedOnAddSection() {
        Response r = Util.addSectionToTradition(jerseyTest, tradId, MULTI, "cxjson", "extra");
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        List<SectionModel> sections = jerseyTest.target("/tradition/" + tradId + "/sections")
                .request().get(new GenericType<>() {});
        assertEquals(1, sections.size());
    }

    private static List<String> summarise(List<ReadingModel> readings) {
        return readings.stream()
                .map(r -> r.getText() + "|" + r.getRank() + "|" + r.getIs_lacuna() + "|" + r.getIs_common())
                .sorted().toList();
    }

    /** For diagnostic use when parsing a section fails
    public void testSomething() throws Exception {
        String newSectId = Util.getValueFromJson(Util.addSectionToTradition(jerseyTest, tradId,
                "src/TestFiles/Matthew-418.json", "cxjson", "AM 401"), "parentId");
        ClientResponse response = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section/" + sectId + "/orderAfter/" + newSectId)
                .put(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    } **/

    public void tearDown() throws Exception {
//        db.shutdown();
    	GraphDatabaseServiceProvider.shutdown();
        jerseyTest.tearDown();
        super.tearDown();
    }
}
