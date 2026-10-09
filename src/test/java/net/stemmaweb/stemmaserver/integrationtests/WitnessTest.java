package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.List;

import net.stemmaweb.services.DatabaseService;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.SectionModel;
import net.stemmaweb.model.TextSequenceModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Root;
import net.stemmaweb.rest.Witness;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;

/**
 * 
 * Contains all tests for the api calls related to witnesses.
 * 
 * @author PSE FS 2015 Team2
 *
 */
public class WitnessTest {
    private String tradId;
    private GraphDatabaseService db;

    /*
     * JerseyTest is the test environment to Test api calls it provides a
     * grizzly http service
     */
    private JerseyTest jerseyTest;


    @Before
    public void setUp() throws Exception {
//        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        DatabaseManagementService dbbuilder = new TestDatabaseManagementServiceBuilder().impermanent().build();
    	db = dbbuilder.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME);
    	new GraphDatabaseServiceProvider(dbbuilder, db);
        Util.setupTestDB(db, "1");

        // Create a JerseyTestServer for the necessary REST API calls

        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(Root.class)
                .create();
        jerseyTest.setUp();

         // load a tradition to the test DB
        tradId = createTraditionFromFile("Tradition", "src/TestFiles/testTradition.xml");
    }

    private String createTraditionFromFile(String tName, String fName) {

        Response jerseyResult = null;
        try {
            jerseyResult = Util.createTraditionFromFileOrString(jerseyTest, tName, "LR", "1", fName, "stemmaweb");
        } catch (Exception e) {
            fail();
        }
        String tradId = Util.getValueFromJson(jerseyResult, "tradId");
        assert(!tradId.isEmpty());
        return tradId;
    }

    @Test
    public void witnessAsTextTestA() {
        String expectedText = "when april with his showers sweet with "
                + "fruit the drought of march has pierced unto the root";
        Response resp = new Witness(tradId, "A").getWitnessAsText();
        assertEquals(expectedText, ((TextSequenceModel) resp.getEntity()).getText());

        String returnedText = jerseyTest
                .target("/tradition/" + tradId + "/witness/A/text")
                .request()
                .get(String.class);
        assertEquals(constructResult(expectedText), returnedText);
    }

    @Test
    public void witnessAsTextNotExistingTest() {
        Response response = jerseyTest
                .target("/tradition/" + tradId + "/witness/D/text")
                .request()
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
                response.getStatus());
        assertEquals("No witness found with sigil D", Util.getValueFromJson(response, "error"));
    }

    @Test
    public void witnessAsTextTestB() {
        String expectedText = "when showers sweet with april fruit the march "
                + "of drought has pierced to the root";
        Response resp = new Witness(tradId, "B").getWitnessAsText();
        assertEquals(expectedText, ((TextSequenceModel) resp.getEntity()).getText());

        String returnedText = jerseyTest
                .target("/tradition/" + tradId + "/witness/B/text")
                .request()
                .get(String.class);
        assertEquals(constructResult(expectedText), returnedText);
    }

    @Test
    public void witnessAsTextWithJoins() {
        String expectedText = "the quick brown fox jumped over the lazy dogs.";
        String foxId = null;
        try {
            foxId = Util.getValueFromJson(
                    Util.createTraditionFromFileOrString(jerseyTest, "quick brown fox", "LR",
                    "1", "src/TestFiles/quick_brown_fox.xml", "collatex"), "tradId");
        } catch (Exception e) {
            fail();
        }
        TextSequenceModel returnedText = (TextSequenceModel) new Witness(foxId, "w1").getWitnessAsText().getEntity();
        assertNotEquals(expectedText, returnedText.getText());

        // Find the reading that is the period
        Node period;
        String periodId;
        try (Transaction tx = db.beginTx()) {
            period = tx.findNode(Nodes.READING, "text", ". ");
            assertNotNull(period);
            periodId = period.getElementId();
            period.setProperty("join_prior", true);
            tx.commit();
        }
        returnedText = (TextSequenceModel) new Witness(foxId, "w1").getWitnessAsText().getEntity();
        assertEquals(expectedText, returnedText.getText());

        // Now find its predecessors and mark them as join_next
        try (Transaction tx = db.beginTx()) {
            period = tx.getNodeByElementId(periodId);
            for (Relationship r : DatabaseService.getRelationships(period, Direction.INCOMING, ERelations.SEQUENCE)) {
                Node n = r.getStartNode();
                n.setProperty("join_next", true);
            }
            tx.commit();
        }
        returnedText = (TextSequenceModel) new Witness(foxId, "w1").getWitnessAsText().getEntity();
        assertEquals(expectedText, returnedText.getText());

        try (Transaction tx = db.beginTx()) {
            period = tx.getNodeByElementId(periodId);
            period.removeProperty("join_prior");
            tx.commit();
        }
        returnedText = (TextSequenceModel) new Witness(foxId, "w1").getWitnessAsText().getEntity();
        assertEquals(expectedText, returnedText.getText());
    }

    @Test
    public void witnessAsListTest() {
        String[] texts = { "when", "april", "with", "his", "showers", "sweet",
                "with", "fruit", "the", "drought", "of", "march", "has",
                "pierced", "unto", "the", "root" };
        List<ReadingModel> listOfReadings = jerseyTest
                .target("/tradition/" + tradId + "/witness/A/readings")
                .request()
                .get(new GenericType<>() {
                });
        assertEquals(texts.length, listOfReadings.size());
        for (int i = 0; i < listOfReadings.size(); i++) {
            assertEquals(texts[i], listOfReadings.get(i).getText());
        }
    }

    @Test
    public void witnessAsListNotExistingTest() {
        Response response = jerseyTest
                .target("/tradition/" + tradId + "/witness/D/readings")
                .request()
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        assertEquals("No witness found with sigil D", Util.getValueFromJson(response, "error"));
    }

    @Test
    public void witnessBetweenRanksTest() {

        String expectedText = constructResult("april with his showers");
        String response = jerseyTest.target("/tradition/" + tradId + "/witness/A/text")
                .queryParam("start", "2")
                .queryParam("end", "5")
                .request()
                .get(String.class);
        assertEquals(expectedText, response);
    }

    @Test
    public void getWitnessTest() {
        // Get a witness
        WitnessModel witnessA = jerseyTest.target("/tradition/" + tradId + "/witness/A")
                .request()
                .get(WitnessModel.class);
        assertEquals("A", witnessA.getSigil());
        assertNotNull(witnessA.getId());
        assertNotEquals("", witnessA.getId());
        assertNotEquals(witnessA.getSigil(), witnessA.getId());

        // Add another tradition with a different witness A
        String secondTradId = createTraditionFromFile("Chaucer", "src/TestFiles/Collatex-16.xml");
        assertNotNull(secondTradId);

        // Now try getting our same witness again
        Response jerseyResponse = jerseyTest.target("/tradition/" + tradId + "/witness/A")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        WitnessModel alsoA = jerseyResponse.readEntity(WitnessModel.class);
        assertEquals(witnessA.getId(), alsoA.getId());
    }

    @Test
    public void lookupWitnessById() {
        // Try it with a good ID
        WitnessModel witnessA = jerseyTest.target("/tradition/" + tradId + "/witness/A")
                .request()
                .get(WitnessModel.class);
        String aId = witnessA.getId();
        WitnessModel aById = jerseyTest.target("/tradition/" + tradId + "/witness/" + aId)
                .request()
                .get(WitnessModel.class);
        assertEquals(witnessA.getSigil(), aById.getSigil());
        assertEquals(witnessA.getId(), aById.getId());

        // Now try it with a bad ID
        Response response = jerseyTest.target("/tradition/" + tradId + "/witness/ABCD")
                .request()
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

        // Now try it with a bad numeric ID
        response = jerseyTest.target("/tradition/" + tradId + "/witness/12345")
                .request()
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void deleteAWitness() {
        // Get all the readings we have
        HashSet<String> remaining = new HashSet<>();
        remaining.addAll(jerseyTest.target("/tradition/" + tradId + "/witness/B/readings")
                .request()
                .get(new GenericType<List<ReadingModel>>() {})
                .stream().map(ReadingModel::getId).toList());
        remaining.addAll(jerseyTest.target("/tradition/" + tradId + "/witness/C/readings")
                .request()
                .get(new GenericType<List<ReadingModel>>() {})
                .stream().map(ReadingModel::getId).toList());
        // Try deleting witness A
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/witness/A")
                .request()
                .delete()) {
            assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
        }
        // Check that it is no longer in the witness list
        assertTrue(jerseyTest.target("/tradition/" + tradId + "/witnesses")
                .request()
                .get(new GenericType<List<WitnessModel>>(){}).stream().noneMatch(x -> x.getSigil().equals("A")));
        // Check that all the remaining readings are in our pre-collected set
        for (ReadingModel rm : jerseyTest.target("/tradition/" + tradId + "/readings")
                .request()
                .get(new GenericType<List<ReadingModel>>() {}))
            if (!rm.getIs_end() && !rm.getIs_start())
                assertTrue(remaining.contains(rm.getId()));

        // Now add a witness out-of-band, that doesn't have any particular data, to make sure we can
        // delete errant witnesses
        String bogusId;
        try (Transaction tx = db.beginTx()) {
            Node traditionNode = tx.findNode(Nodes.TRADITION, "id", tradId);
            Node bogus = DatabaseService.createNode(tx, Nodes.WITNESS);
            bogusId = bogus.getProperty("id").toString();
            bogus.setProperty("hypothetical", false);
            bogus.setProperty("sigil", "n\":\"RJKYRSKZ");
            traditionNode.createRelationshipTo(bogus, ERelations.HAS_WITNESS);
            tx.commit();
        }
        assertNotNull(bogusId);
        assertEquals(3, jerseyTest.target("/tradition/" + tradId + "/witnesses")
                .request()
                .get(new GenericType<List<WitnessModel>>(){}).size());
        try (Response result = jerseyTest.target(String.format("/tradition/%s/witness/%s", tradId, bogusId))
                .request()
                .delete()) {
            assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
        }
        assertEquals(2, jerseyTest.target("/tradition/" + tradId + "/witnesses")
                .request()
                .get(new GenericType<List<WitnessModel>>(){}).size());


        // Now add another tradition with overlapping sigla and try to delete its witness B
        String secondTradId = createTraditionFromFile("Chaucer", "src/TestFiles/Collatex-16.xml");
        assertNotNull(secondTradId);
        try (Response result = jerseyTest.target(String.format("/tradition/%s/witness/B", secondTradId))
                .request()
                .delete()) {
            assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
        }
    }

    @Test
    public void deleteWitnessFromStemma() {
        // Capture witness A's original (managed) id. Witness A is extant in both of the
        // stemmata that come pre-loaded with testTradition.xml.
        WitnessModel witnessA = jerseyTest.target("/tradition/" + tradId + "/witness/A")
                .request()
                .get(WitnessModel.class);
        String originalId = witnessA.getId();
        assertNotNull(originalId);

        // Delete witness A tradition-wide.
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/witness/A")
                .request()
                .delete()) {
            assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
        }

        // Each stemma that used to carry A as extant should now carry a hypothetical
        // replacement witness with its own, distinct, managed id (not null, and not a
        // leftover copy of the deleted witness's id).
        try (Transaction tx = db.beginTx()) {
            Node tradNode = tx.findNode(Nodes.TRADITION, "id", tradId);
            List<Node> stemmaNodes = DatabaseService.getRelated(tradNode, ERelations.HAS_STEMMA);
            assertTrue(stemmaNodes.size() > 0);
            for (Node stemmaNode : stemmaNodes) {
                Node replacement = null;
                for (Node wit : DatabaseService.getRelated(stemmaNode, ERelations.HAS_WITNESS)) {
                    if (wit.getProperty("sigil", "").equals("A")) {
                        replacement = wit;
                        break;
                    }
                }
                assertNotNull(replacement);
                assertTrue((Boolean) replacement.getProperty("hypothetical"));
                assertTrue(replacement.hasProperty("id"));
                String newId = replacement.getProperty("id").toString();
                assertNotNull(newId);
                assertNotEquals(originalId, newId);
            }
        }
    }

    @Test
    public void createWitnessInvalidSigil() {
        Response r = Util.createTraditionFromFileOrString(jerseyTest, "592th", "LR", "1",
                "src/TestFiles/592th.xml", "graphmlsingle");
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        String error = Util.getValueFromJson(r, "error");
        assertTrue(error.startsWith("The sigil \""));
        // ...it might be one of a few sigla. We just care that we get the error
        assertTrue(error.endsWith("is not a valid name: it must start with a letter or underscore and "
                + "contain only letters, digits, underscores, hyphens, or periods thereafter"));
    }

    @Test
    public void sigilNcNameValidationTest() {
        // Reject a numeric sigil.
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), importSingleWitnessTradition("123").getStatus());
        // Reject a sigil starting with a digit.
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), importSingleWitnessTradition("1a").getStatus());
        // Reject a sigil containing a space.
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), importSingleWitnessTradition("my witness").getStatus());
        // Reject a sigil containing an apostrophe.
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), importSingleWitnessTradition("Q1'").getStatus());
        // Accept a sigil starting with a letter.
        assertEquals(Response.Status.CREATED.getStatusCode(), importSingleWitnessTradition("Q1").getStatus());
        // Accept a non-ASCII (Greek) sigil.
        assertEquals(Response.Status.CREATED.getStatusCode(), importSingleWitnessTradition("α").getStatus());
    }

    // Minimal single-witness, single-reading CollateX JSON import, used to test sigil validation
    private Response importSingleWitnessTradition(String sigil) {
        String cxjson = String.format("{\"witnesses\": [\"%s\"], \"table\": [[[{\"t\": \"word\"}]]]}", sigil);
        return Util.createTraditionFromFileOrString(jerseyTest, "SigilTest", "LR", "1", cxjson, "cxjson");
    }

    @Test
    public void putWitnessRenameTest() {
        WitnessModel witnessA = jerseyTest.target("/tradition/" + tradId + "/witness/A")
                .request()
                .get(WitnessModel.class);
        String originalId = witnessA.getId();

        // Rename A -> Z: 200, sigil changed, same id.
        WitnessModel renameRequest = new WitnessModel();
        renameRequest.setSigil("Z");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/witness/A")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(renameRequest))) {
            assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
            WitnessModel renamed = result.readEntity(WitnessModel.class);
            assertEquals("Z", renamed.getSigil());
            assertEquals(originalId, renamed.getId());
        }

        // Renaming a witness to its own current sigil succeeds (200), not 409.
        WitnessModel noopRename = new WitnessModel();
        noopRename.setSigil("Z");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/witness/Z")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(noopRename))) {
            assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
            WitnessModel renamed = result.readEntity(WitnessModel.class);
            assertEquals(originalId, renamed.getId());
        }

        // Renaming to a sigil already in use by another witness conflicts: 409.
        WitnessModel conflictRename = new WitnessModel();
        conflictRename.setSigil("B");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/witness/Z")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(conflictRename))) {
            assertEquals(Response.Status.CONFLICT.getStatusCode(), result.getStatus());
        }

        // An invalid (numeric-only) sigil is rejected: 400.
        WitnessModel invalidRename = new WitnessModel();
        invalidRename.setSigil("123");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/witness/Z")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(invalidRename))) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), result.getStatus());
        }

        // A ref that resolves to nothing, with a body sigil matching the ref, creates a
        // brand-new extant witness under that sigil: 201.
        WitnessModel createRequest = new WitnessModel();
        createRequest.setSigil("NEWWIT");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/witness/NEWWIT")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(createRequest))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), result.getStatus());
            WitnessModel created = result.readEntity(WitnessModel.class);
            assertEquals("NEWWIT", created.getSigil());
            assertNotNull(created.getId());
        }

        // PUT within a section-scoped path is rejected: 400 (rename/create only makes
        // sense tradition-wide).
        List<SectionModel> ourSections = jerseyTest.target("/tradition/" + tradId + "/sections")
                .request()
                .get(new GenericType<List<SectionModel>>() {});
        String sectId = ourSections.getFirst().getId();
        WitnessModel sectionRename = new WitnessModel();
        sectionRename.setSigil("Q");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/section/" + sectId + "/witness/B")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(sectionRename))) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), result.getStatus());
        }
    }

    @Test
    public void putWitnessCreateUsesUrlRefTest() {
        // A body sigil that differs from a URL ref which resolves to nothing must not silently
        // create a witness under the body's sigil (e.g. a typo'd rename): 400.
        WitnessModel mismatched = new WitnessModel();
        mismatched.setSigil("B2");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/witness/Aa")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(mismatched))) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), result.getStatus());
        }
        // ...nor for a numeric ref that resolves to nothing.
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/witness/99999")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(mismatched))) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), result.getStatus());
        }
        // Nothing was created by either attempt.
        List<WitnessModel> witnesses = jerseyTest.target("/tradition/" + tradId + "/witnesses")
                .request()
                .get(new GenericType<>() {});
        assertEquals(3, witnesses.size());
        assertTrue(witnesses.stream().noneMatch(x -> x.getSigil().equals("B2") || x.getSigil().equals("Aa")));

        // No body sigil at all: created under the URL ref.
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/witness/Dnew")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(new WitnessModel()))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), result.getStatus());
            assertEquals("Dnew", result.readEntity(WitnessModel.class).getSigil());
        }
        // Body sigil equal to the URL ref: created under that sigil.
        WitnessModel matching = new WitnessModel();
        matching.setSigil("Enew");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/witness/Enew")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(matching))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), result.getStatus());
            assertEquals("Enew", result.readEntity(WitnessModel.class).getSigil());
        }
    }

    @Test
    public void renameWitnessKeepsTextTest() {
        String expectedText = "when april with his showers sweet with "
                + "fruit the drought of march has pierced unto the root";
        List<String> expectedReadings = jerseyTest.target("/tradition/" + tradId + "/witness/A/readings")
                .request()
                .get(new GenericType<List<ReadingModel>>() {})
                .stream().map(ReadingModel::getId).toList();

        WitnessModel renameRequest = new WitnessModel();
        renameRequest.setSigil("Arenamed");
        try (Response result = jerseyTest.target("/tradition/" + tradId + "/witness/A")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(renameRequest))) {
            assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
        }

        // The witness's text is still reachable under its new sigil...
        Response textResp = jerseyTest.target("/tradition/" + tradId + "/witness/Arenamed/text")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), textResp.getStatus());
        assertEquals(expectedText, textResp.readEntity(TextSequenceModel.class).getText());
        assertEquals(expectedReadings, jerseyTest.target("/tradition/" + tradId + "/witness/Arenamed/readings")
                .request()
                .get(new GenericType<List<ReadingModel>>() {})
                .stream().map(ReadingModel::getId).toList());
        // ...and the old sigil no longer identifies anything.
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
                jerseyTest.target("/tradition/" + tradId + "/witness/A/text").request().get().getStatus());

        // No sequence link anywhere in the tradition still carries the old sigil.
        try (Transaction tx = db.beginTx()) {
            for (Relationship r : net.stemmaweb.services.VariantGraphService
                    .returnEntireTradition(tx, tradId).relationships()) {
                if (!r.isType(ERelations.SEQUENCE) && !r.isType(ERelations.NSEQUENCE)) continue;
                for (Object v : r.getAllProperties().values())
                    if (v instanceof String[] sigla)
                        assertTrue(List.of(sigla).stream().noneMatch("A"::equals));
            }
        }
    }

    @Test
    public void renameWitnessWithLayerAndNormalizationTest() {
        // Florilegium has a.c. layer witnesses; normalize it too, so that NSEQUENCE links exist.
        String florId = createTraditionFromFile("Florilegium", "src/TestFiles/florilegium_graphml.xml");
        String qText = jerseyTest.target("/tradition/" + florId + "/witness/Q/text")
                .request().get(TextSequenceModel.class).getText();
        String qacText = jerseyTest.target("/tradition/" + florId + "/witness/Q/text")
                .queryParam("layer", "a.c.")
                .request().get(TextSequenceModel.class).getText();
        boolean sawNsequence = false;
        try (Transaction tx = db.beginTx()) {
            Node tradNode = tx.findNode(Nodes.TRADITION, "id", florId);
            // Any relation type will do: with no relations of that type, every reading simply
            // represents itself, and the NSEQUENCE shadow graph mirrors the SEQUENCE graph.
            new net.stemmaweb.model.RelationTypeModel("normtest").instantiate(tradNode, tx);
            for (Node section : DatabaseService.getRelated(tradNode, ERelations.PART))
                net.stemmaweb.services.VariantGraphService.normalizeGraph(tx, section, "normtest");
            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }

        WitnessModel renameRequest = new WitnessModel();
        renameRequest.setSigil("Qnew");
        try (Response result = jerseyTest.target("/tradition/" + florId + "/witness/Q")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(renameRequest))) {
            assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
        }
        assertEquals(qText, jerseyTest.target("/tradition/" + florId + "/witness/Qnew/text")
                .request().get(TextSequenceModel.class).getText());
        assertEquals(qacText, jerseyTest.target("/tradition/" + florId + "/witness/Qnew/text")
                .queryParam("layer", "a.c.")
                .request().get(TextSequenceModel.class).getText());

        try (Transaction tx = db.beginTx()) {
            for (Relationship r : net.stemmaweb.services.VariantGraphService
                    .returnEntireTradition(tx, florId).relationships()) {
                if (!r.isType(ERelations.SEQUENCE) && !r.isType(ERelations.NSEQUENCE)) continue;
                if (r.isType(ERelations.NSEQUENCE)) sawNsequence = true;
                for (Object v : r.getAllProperties().values())
                    if (v instanceof String[] sigla)
                        assertTrue(List.of(sigla).stream().noneMatch("Q"::equals));
            }
        }
        assertTrue("fixture should have produced NSEQUENCE links", sawNsequence);
    }

    @Test
    public void witnessCrossTraditionIdTest() {
        // A numeric witness id belonging to another tradition must not resolve within this one.
        String otherTradId = createTraditionFromFile("Chaucer", "src/TestFiles/Collatex-16.xml");
        WitnessModel otherB = jerseyTest.target("/tradition/" + otherTradId + "/witness/B")
                .request().get(WitnessModel.class);
        String otherId = otherB.getId();

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
                jerseyTest.target("/tradition/" + tradId + "/witness/" + otherId).request().get().getStatus());
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
                jerseyTest.target("/tradition/" + tradId + "/witness/" + otherId + "/text").request().get().getStatus());
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/witness/" + otherId).request().delete()) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), r.getStatus());
        }
        WitnessModel renameRequest = new WitnessModel();
        renameRequest.setSigil("Hijacked");
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/witness/" + otherId)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(renameRequest))) {
            assertNotEquals(Response.Status.OK.getStatusCode(), r.getStatus());
        }

        // The other tradition's witness is untouched.
        WitnessModel stillB = jerseyTest.target("/tradition/" + otherTradId + "/witness/" + otherId)
                .request().get(WitnessModel.class);
        assertEquals("B", stillB.getSigil());
        assertEquals(Response.Status.OK.getStatusCode(),
                jerseyTest.target("/tradition/" + otherTradId + "/witness/B/text").request().get().getStatus());
    }

    /**
     * as ranks are adjusted should give same result as previous test
     */
    @Test
    public void witnessBetweenRanksWrongWayTest() {
        String expectedText = constructResult("april with his showers");
        String response = jerseyTest
                .target("/tradition/" + tradId + "/witness/A/text")
                .queryParam("start", "5")
                .queryParam("end", "2")
                .request()
                .get(String.class);
        assertEquals(expectedText, response);
    }

    /**
     * gives same ranks for start and end should return error
     */
    @Test
    public void witnessBetweenRanksSameRanksTest() {
        Response response = jerseyTest
                .target("/tradition/" + tradId + "/witness/A/text")
                .queryParam("start", "5")
                .queryParam("end", "5")
                .request()
                .get();
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("end-rank is equal to start-rank", Util.getValueFromJson(response, "error"));
    }

    //if the end rank is too high, will return all the readings between start rank to end of witness
    @Test
    public void witnessBetweenRanksTooHighEndRankTest() {
        String expectedText = constructResult("showers sweet with fruit the drought of march has pierced unto the root");
        String response = jerseyTest
                .target("/tradition/" + tradId + "/witness/A/text")
                .queryParam("start", "5")
                .queryParam("end", "30")
                .request()
                .get(String.class);
        assertEquals(expectedText, response);
    }
    /**
     * test if the tradition node exists
     */
    @Test
    public void traditionNodeExistsTest() {
        try (Transaction tx = db.beginTx()) {
            ResourceIterator<Node> tradNodesIt = tx.findNodes(Nodes.TRADITION, "name", "Tradition");
            assertTrue(tradNodesIt.hasNext());
        }
    }

    /**
     * test if the tradition end node exists
     */
    @Test
    public void traditionEndNodeExistsTest() {
        try (Transaction tx = db.beginTx()) {
            ResourceIterator<Node> tradNodesIt = tx.findNodes(Nodes.READING, "text", "#END#");
            assertTrue(tradNodesIt.hasNext());
        }
    }

    /**
     * test what text gets returned when a witness correction layer is involved
     */
    @Test
    public void correctedWitnessTextTest() {
        // Our expected values
        String qText = "Ἡ περὶ τοῦ ἁγίου πνεύματος βλασφημία αὐτόθεν ἔχει τὴν λύσιν· ὁ δὲ δεύτερος ἐστὶν οὗτος· ὅτάν τις ἐν ἁμαρτίαις ἐνεχόμενος, ἀκούων δὲ τοῦ κυρίου λέγοντος μὴ κρίνετε φοβούμενος οὐδένα κρίνῃ ἐν τῇ ἐξετάσει τῶν βεβιωμένων ὡς φύλαξ τῆς ἐντολῆς οὐ κρίνεται· εἰ μὴ τὸ γενέσθαι πιστόν, εἰκότως ὅταν ἐν ἁμαρτίαις τίς ὢν οἰκονομῆται ἐκ τῆς προνοίας ἐν συμφοραῖς, ἐν ἀνάγκαις, ἐν νόσοις ὡς οὐκ οἶδε γὰρ διὰ τῶν τοιούτων καθαίρει αὐτὸν ὁ θεός οὖν τῷ ἐν ἀπιστίᾳ τὸν βίον καταλύσαντι οὔτε ἐνταῦθα οὔτε ἐν τῷ μέλλοντι ἀφεθήσεται τῆς ἀπιστίας καὶ ἀθεΐας ἡ ἁμαρτία. νείλου τοῦ νύσσης Ἤκουσά που τῆς ἁγίας γραφῆς κατακρινούσης ἐκείνους, οἳ κατὰ τοῦ θεοῦ βλασφημίας αἴτιοι γίνονται. Οὐαὶ γὰρ φησὶν δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο γὰρ χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν. Νείλου μοναχοῦ Ὄψις γυναικὸς βέλος ἐστὶ πεφαρμακευμένον ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. Θάλλει βοτάνη ἑστῶσα παρ᾽ ὕδατι, καὶ πάθος ἀκολασίας, ἐν συντυχίαις γυναικῶν.";
        String eText = "Ἡ περὶ τῆς τοῦ πνεύματος τοῦ ἁγίου βλασφημίας ἀπορία αὐτόθεν ἔχει τὴν λύσιν· ὁ δὲ δεύτερος ἐστὶν οὗτος· ὅτάν τις ἐν ἁμαρτίαις ἐνεχόμενος, ἀκούων δὲ τοῦ κυρίου λέγοντος μὴ κρίνετε φοβούμενος οὐδένα κρίνει ἐν τῇ ἐξετάσει τῶν βεβιωμένων ὡς φύλαξ τῆς ἐντολῆς οὐ κρίνεται· εἰ μὴ τὸ γενέσθαι πιστόν, εἰκότως ὅταν ἐν ἁμαρτίαις τίς ὢν οἰκονομεῖται ἐκ τῆς προνοίας ἐν συμφοραῖς, ἐν ἀνάγκαις, ἐν νόσοις ὡς οὐκ οἶδε γὰρ διὰ τῶν τοιούτων καθαίρει αὐτὸν ὁ θεός τῷ ἐν ἀπιστίᾳ τὸν βίον κατακλύσαντι οὔτε ἐνταῦθα οὔτε ἐν τῷ μέλλοντι ἀφεθήσεται τῆς ἀπιστίας καὶ ἀθεΐας ἡ ἁμαρτία. Ἰσιδώρου Πηλουσίου Γρηγορίου Νύσης Ἤκουσά που τῆς ἁγίας γραφῆς κατακρινούσης ἐκείνους, οἳ κατὰ τῆς τοῦ θεοῦ βλασφημίας αἴτιοι γίνονται. Οὐαὶ γὰρ φησὶν δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν. Νείλου μοναχοῦ Ὄψις γυναικὸς βέλος ἐστὶ πεφαρμακευμένον ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. Θάλλει βοτάνη ἑστῶσα παρ᾽ ὕδατι, καὶ πάθος ἀκολασίας, ἐν συντυχίαις γυναικῶν.";
        String tText = "Ἡ περὶ τῆς τοῦ πνεύματος τοῦ ἁγίου βλασφημίας ἀπορία αὐτόθι ἔχει τὴν λύσιν· ὁ δὲ δεύτερος ἐστὶν οὗτος· ὅτάν τις ἐν ἁμαρτίαις ἐνεχόμενος, ἀκούων δὲ τοῦ κυρίου λέγοντος μὴ κρίνεται φοβούμενος οὐδένα κρίνει ἐν τῇ ἐξετάσει τῶν βεβιωμένων ὡς φύλαξ τῆς ἐντολῆς οὐ κρίνεται· εἰ μὴ τὸ γενέσθαι πιστόν, εἰκότως ὅταν ἐν ἁμαρτίαις τίς ὢν οἰκονομεῖται ἐκ τῆς προνοίας ἐν συμφοραῖς, ἐν ἀνάγκαις, ἐν νόσοις ὡς οὐκ οἶδε γὰρ διὰ τῶν τοιούτων καθαίρει αὐτὸν ὁ θεός οὖν τῷ ἐν ἀπιστίᾳ τὸν βίον κατακλείσαντι οὔτε ἐνταῦθα οὔτε ἐν τῷ μέλλοντι ἀφεθήσεται τῆς ἀπιστίας καὶ ἀθεΐας ἡ ἁμαρτία. Γρηγορίου Νύσης Ἤκουσά που τῆς ἁγίας γραφῆς κατακρινούσης ἐκείνους, οἳ κατὰ τῆς τοῦ θεοῦ βλασφημίας αἴτιοι γίνονται. Οὐαὶ γὰρ φησὶν δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν. Νείλου μοναχοῦ Ὄψις γυναικὸς μέλος ἐστὶ πεφαρμακευμένον ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. Θάλλει βοτάνη ἐστῶσα παρ᾽ ὕδατι, καὶ πάθος ἀκολασίας, ἐν συντυχίαις γυναικῶν.";
        String qacText = "Ἡ περὶ τοῦ ἁγίου πνεύματος βλασφημία αὐτόθεν ἔχει τὴν λύσιν· ὁ δὲ δεύτερος ἐστὶν οὗτος· ὅτάν τις ἐν ἁμαρτίαις ἐνεχόμενος, ἀκούων δὲ τοῦ κυρίου λέγοντος μὴ κρίνετε φοβούμενος οὐδένα κρίνει ἐν τῇ ἐξετάσει τῶν βεβιωμένων ὡς φύλαξ τῆς ἐντολῆς οὐ κρίνεται· εἰ μὴ τὸ γενέσθαι πιστόν, εἰκότως ὅταν ἐν ἁμαρτίαις τίς ὢν οἰκονομῆται ἐκ τῆς προνοίας ἐν συμφοραῖς, ἐν ἀνάγκαις, ἐν νόσοις ὡς οὐκ οἶδε γὰρ διὰ τῶν τοιούτων καθαίρει αὐτὸν ὁ θεός οὖν τῷ ἐν ἀπιστίᾳ τὸν βίον κατακλείσαντι οὔτε ἐνταῦθα οὔτε ἐν τῷ μέλλοντι ἀφεθήσεται τῆς ἀπιστίας καὶ ἀθεΐας ἡ ἁμαρτία. νείλου τοῦ νύσσης Ἤκουσά που τῆς ἁγίας γραφῆς κατακινούσης ἐκείνους, οἳ κατὰ τοῦ θεοῦ βλασφημίας αἴτιοι γίνονται. Οὐαὶ γὰρ φησὶν δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο γὰρ χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν. Νείλου μοναχοῦ Ὄψις γυναικὸς βέλος ἐστὶ πεφαρμακευμένον ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. Θάλλει βοτάνη ἑστῶσα παρ᾽ ὕδατι, καὶ πάθος ἀκολασίας, ἐν συντυχίαις γυναικῶν.";
        String eacText = "Ἡ περὶ τῆς τοῦ πνεύματος τοῦ ἁγίου βλασφημίας ἀπορία αὐτόθεν ἔχει τὴν λύσιν· ὁ δὲ δεύτερος ἐστὶν οὗτος· ὅτάν τις ἐν ἁμαρτίαις ἐνεχόμενος, ἀκούων δὲ τοῦ κυρίου λέγοντος μὴ κρίνετε φοβούμενος οὐδένα κρίνει ἐν τῇ ἐξετάσει τῶν βεβιωμένων ὡς φύλαξ τῆς ἐντολῆς οὐ κρίνεται· εἰ μὴ τὸ γενέσθαι πιστόν, εἰκότως ὅταν ἐν ἁμαρτίαις τίς ὢν οἰκονομεῖται ἐκ τῆς προνοίας ἐν συμφοραῖς, ἐν ἀνάγκαις, ἐν νόσοις ὡς οὐκ οἶδε γὰρ διὰ τῶν τοιούτων καθαίρει αὐτὸν ὁ θεός τῷ ἐν ἀπιστίᾳ τὸν βίον καταλύσαντι οὔτε ἐνταῦθα οὔτε ἐν τῷ μέλλοντι ἀφεθήσεται τῆς ἀπιστίας καὶ ἀθεΐας ἡ ἁμαρτία. Ἰσιδώρου Πηλουσίου Γρηγορίου Νύσης Ἤκουσά που τῆς ἁγίας γραφῆς κατακρινούσης ἐκείνους, οἳ κατὰ τῆς τοῦ θεοῦ βλασφημίας αἴτιοι γίνονται. Οὐαὶ γὰρ φησὶν δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν. Νείλου μοναχοῦ Ὄψις γυναικὸς βέλος ἐστὶ πεφαρμακευμένον ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. Θάλλει βοτάνη ἑστῶσα παρ᾽ ὕδατι, καὶ πάθος ἀκολασίας, ἐν συντυχίαις γυναικῶν.";
        String tacText = "Ἡ περὶ τῆς τοῦ πνεύματος τοῦ ἁγίου βλασφημίας ἀπορία αὐτόθι ἔχει τὴν λύσιν· ὁ δὲ δεύτερος ἐστὶν οὗτος· ὅτάν τις ἐν ἁμαρτίαις ἐνεχόμενος, ἀκούων δὲ τοῦ κυρίου λέγοντος μὴ κρίνεται φοβούμενος οὐδένα κρίνει ἐν τῇ ἐξετάσει τῶν βεβιωμένων ὡς φύλαξ τῆς ἐντολῆς οὐ κρίνεται· εἰ μὴ τὸ γενέσθαι πιστόν, εἰκότως ὅταν ἐν ἁμαρτίαις τίς ὢν οἰκονομεῖται ἐκ τῆς προνοίας ἐν συμφοραῖς, ἐν ἀνάγκαις, ἐν νόσοις ὡς οὐκ οἶδε γὰρ διὰ τῶν τοιούτων καθαίρει αὐτὸν ὁ θεός οὖν τῷ ἐν ἀπιστίᾳ τὸν βίον κατακλείσαντι οὔτε ἐνταῦθα οὔτε ἐν τῷ μέλλοντι ἀφεθήσεται τῆς ἀπιστίας καὶ ἀθεΐας ἡ ἁμαρτία. Γρηγορίου Νύσης Ἤκουσά που τῆς ἁγίας γραφῆς κατακρινούσης ἐκείνους, οἳ κατὰ τῆς τοῦ θεοῦ βλασφημίας αἴτιοι γίνονται. Οὐαὶ γὰρ φησὶν δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν. Νείλου μοναχοῦ Ὄψις γυναικὸς μέλος ἐστὶ πεφαρμακευμένον ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τὸν ἐχθρῶν Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. Θάλλει βοτάνη ἐστῶσα παρ᾽ ὕδατι, καὶ πάθος ἀκολασίας, ἐν συντυχίαις γυναικῶν.";

        // Create the tradition in question
        String newId = createTraditionFromFile("Florilegium", "src/TestFiles/florilegium_graphml.xml");
        // Now get the witness text for each of our complex sigla.
        String response = jerseyTest
                .target("/tradition/" + newId + "/witness/Q/text")
                .request()
                .get(String.class);
        assertEquals(constructResult(qText), response);
        response = jerseyTest
                .target("/tradition/" + newId + "/witness/E/text")
                .request()
                .get(String.class);
        assertEquals(constructResult(eText), response);
        response = jerseyTest
                .target("/tradition/" + newId + "/witness/T/text")
                .request()
                .get(String.class);
        assertEquals(constructResult(tText), response);

        // Now try to get the uncorrected text.
        response = jerseyTest
                .target("/tradition/" + newId + "/witness/Q/text")
                .queryParam("layer", "a.c.")
                .request()
                .get(String.class);
        assertEquals(constructResult(qacText), response);
        response = jerseyTest
                .target("/tradition/" + newId + "/witness/E/text")
                .queryParam("layer", "a.c.")
                .request()
                .get(String.class);
        assertEquals(constructResult(eacText), response);
        response = jerseyTest
                .target("/tradition/" + newId + "/witness/T/text")
                .queryParam("layer", "a.c.")
                .request()
                .get(String.class);
        assertEquals(constructResult(tacText), response);

    }

    private String constructResult (String text) {
        return String.format("{\"text\":\"%s\"}", text);
    }

    @Test
    public void witnessNonexistentTraditionTest() {
        // GET/PUT/DELETE against a tradition id that doesn't exist returns 404
        String badTradId = "10000";

        Response getResponse = jerseyTest.target("/tradition/" + badTradId + "/witness/A")
                .request()
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), getResponse.getStatus());

        WitnessModel putBody = new WitnessModel();
        putBody.setSigil("Z");
        try (Response putResponse = jerseyTest.target("/tradition/" + badTradId + "/witness/A")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(putBody))) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), putResponse.getStatus());
        }

        try (Response deleteResponse = jerseyTest.target("/tradition/" + badTradId + "/witness/A")
                .request()
                .delete()) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), deleteResponse.getStatus());
        }
    }

    /*
     * Shut down the jersey server
     *
     * @throws Exception
     */
    @After
    public void tearDown() throws Exception {
//        db.shutdown();
    	GraphDatabaseServiceProvider.shutdown();
        jerseyTest.tearDown();
    }
}
