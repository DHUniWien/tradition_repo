package net.stemmaweb.stemmaserver.integrationtests;

import java.util.*;
import java.util.stream.Collectors;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.model.*;
import net.stemmaweb.rest.*;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.VariantGraphService;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;

import net.stemmaweb.stemmaserver.Util;

import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.neo4j.test.TestGraphDatabaseFactory;

import static org.junit.Assert.*;

/**
 * 
 * Contains all tests for the api calls related to readings.
 * 
 * @author PSE FS 2015 Team2
 *
 */
public class ReadingTest {

    private String tradId;
    private String sectId;

    private String expectedWitnessA = "when april with his showers sweet with fruit the drought of march has pierced unto me the root";
    private String expectedWitnessB = "when april his showers sweet with fruit the march of drought has pierced to the root";
    private String expectedWitnessC = "when showers sweet with fruit to drought of march has pierced teh rood-of-the-world";

    private GraphDatabaseService db;
    private HashMap<String, String> readingLookup = new HashMap<>();

    /*
     * JerseyTest is the test environment to Test api calls it provides a
     * grizzly http service
     */
    private JerseyTest jerseyTest;

    @Before
    public void setUp() throws Exception {

        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();

        /*
         * Populate the test database with the root node and a user with id 1
         */
        DatabaseService.createRootNode(db);
        Util.setupTestDB(db, "1");

        // Create a JerseyTestServer for the necessary REST API calls

        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(Root.class)
                .create();
        jerseyTest.setUp();

        /*
         * load a tradition to the test DB
         * and gets the generated id of the inserted tradition
         */
        Response jerseyResult = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/ReadingstestTradition.xml", "stemmaweb");
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        tradId = Util.getValueFromJson(jerseyResult, "tradId");
        List<SectionModel> testSections = jerseyTest.target("/tradition/" + tradId + "/sections")
                .request()
                .get(new GenericType<>() {});
        sectId = testSections.get(0).getId();
        readingLookup = Util.makeReadingLookup(jerseyTest, tradId);

    }

    /**
     * Contains 29 readings at the beginning.
     *
     * @return a list of readings in the tradition
     */
    private List<ReadingModel> testNumberOfReadingsAndWitnesses(int number) {
        List<ReadingModel> listOfReadings = jerseyTest
                .target("/tradition/" + tradId + "/readings")
                .request()
                .get(new GenericType<>() {});
        assertEquals(number, listOfReadings.size());
        for (ReadingModel rm : listOfReadings)
            if (rm.getIs_start())
                assertEquals(Long.valueOf(0), rm.getRank());
            else
                assertTrue(rm.getRank() > 0);

        TextSequenceModel resp;
        resp = (TextSequenceModel) new Witness(tradId, "A").getWitnessAsText().getEntity();
        assertEquals(expectedWitnessA, resp.getText());

        resp = (TextSequenceModel) new Witness(tradId, "B").getWitnessAsText().getEntity();
        assertEquals(expectedWitnessB, resp.getText());

        resp = (TextSequenceModel) new Witness(tradId, "C").getWitnessAsText().getEntity();
        assertEquals(expectedWitnessC, resp.getText());

        return listOfReadings;
    }

    @Test
    public void accessReadingViaTraditionAndSectionTest() {
        // Choose an arbitrary reading
        String rid = readingLookup.get("april/2");
        ReadingModel rm = jerseyTest.target("/reading/" + rid).request().get(ReadingModel.class);
        assertEquals(rid, rm.getId());

        // Look it up by its tradition
        Response r = jerseyTest.target("/tradition/" + tradId + "/reading/" + rid).request().get();
        assertEquals(Status.OK.getStatusCode(), r.getStatus());
        ReadingModel trm = r.readEntity(ReadingModel.class);
        assertEquals(rm.getId(), trm.getId());
        assertEquals(rm.getText(), trm.getText());

        // Look it up by its section
        r = jerseyTest.target("/tradition/" + tradId + "/section/" + sectId +  "/reading/" + rid).request().get();
        assertEquals(Status.OK.getStatusCode(), r.getStatus());
        ReadingModel srm = r.readEntity(ReadingModel.class);
        assertEquals(rm.getId(), srm.getId());
        assertEquals(rm.getText(), srm.getText());

        // Make a POST call and make sure it works
        ReadingChangePropertyModel rcpm = new ReadingChangePropertyModel();
        rcpm.addProperty(new KeyPropertyModel("normal_form", "April"));
        r = jerseyTest.target("/tradition/" + tradId + "/reading/" + rid)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(rcpm));
        assertEquals(Status.OK.getStatusCode(), r.getStatus());
        trm = r.readEntity(ReadingModel.class);
        assertEquals(rm.getId(), trm.getId());
        assertEquals("April", trm.getNormal_form());

        // Add a second tradition
        String newTradId = Util.getValueFromJson(Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/florilegium_graphml.xml", "stemmaweb"), "tradId");
        assertNotNull(newTradId);

        // Now try to get the same reading via the new tradition
        r = jerseyTest.target("/tradition/" + newTradId + "/reading/" + rid).request().get();
        assertEquals(Status.NOT_FOUND.getStatusCode(), r.getStatus());

        // Add a second section to the first text
        r = Util.addSectionToTradition(jerseyTest, tradId, "src/TestFiles/testTradition.xml", "stemmaweb", "1");
        assertEquals(Status.CREATED.getStatusCode(), r.getStatus());
        String newSectId = Util.getValueFromJson(r, "sectionId");

        // Try to get the reading as above, but from the wrong section
        r = jerseyTest.target("/tradition/" + tradId + "/section/" + newSectId +  "/reading/" + rid).request().get();
        assertEquals(Status.NOT_FOUND.getStatusCode(), r.getStatus());

        // But we can get a reading from the new section that actually belongs to it...?
        readingLookup = Util.makeReadingLookup(jerseyTest, tradId);
        r = jerseyTest.target("/tradition/" + tradId + "/section/" + newSectId +  "/reading/"
                + readingLookup.get("rood/17")).request().get();
        assertEquals(Status.OK.getStatusCode(), r.getStatus());
        srm = r.readEntity(ReadingModel.class);
        assertEquals(17L, Long.parseLong(srm.getRank().toString()));
        assertEquals("rood", srm.getText());
    }

    @Test
    public void changeReadingPropertiesOnePropertyTest() {
        String nodeId = readingLookup.get("showers/5");

        KeyPropertyModel keyModel = new KeyPropertyModel("text", "snow");
        ReadingChangePropertyModel chgModel = new ReadingChangePropertyModel();
        chgModel.addProperty(keyModel);

        Response response = jerseyTest
                .target("/reading/" + nodeId)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(chgModel));

        assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());
        assertEquals("snow", response.readEntity(ReadingModel.class).getText());

        String expectedWitnessA = "when april with his snow sweet with fruit the drought of march has pierced unto me the root";
        TextSequenceModel resp = (TextSequenceModel) new Witness(tradId, "A").getWitnessAsText().getEntity();
        assertEquals(expectedWitnessA, resp.getText());
    }

    @Test
    public void changeReadingPropertiesWrongDatatypeTest() {
        String nodeid = readingLookup.get("showers/5");

        KeyPropertyModel keyModel = new KeyPropertyModel("text", "rainshowers");
        KeyPropertyModel keyModel2 = new KeyPropertyModel("join_next", "true");
        List<KeyPropertyModel> models = new ArrayList<>();
        models.add(keyModel);
        models.add(keyModel2);
        ReadingChangePropertyModel chgModel = new ReadingChangePropertyModel();
        chgModel.setProperties(models);
        Response response = jerseyTest
                .target("/reading/" + nodeid)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(chgModel));
        assertEquals(Status.BAD_REQUEST.getStatusCode(), response.getStatus());

        // Check that the node text didn't change
        try (Transaction tx = db.beginTx()) {
            Node node = db.getNodeById(Long.parseLong(nodeid));
            assertEquals("showers", node.getProperty("text").toString());
            tx.success();
        }
    }

    @Test
    public void changeReadingPropertiesMultiplePropertiesTest() {
        String nodeId = readingLookup.get("showers/5");

        KeyPropertyModel keyModel = new KeyPropertyModel("text", "snow");
        ReadingChangePropertyModel chgModel = new ReadingChangePropertyModel();
        List<KeyPropertyModel> models = new ArrayList<>();
        models.add(keyModel);
        KeyPropertyModel keyModel2 = new KeyPropertyModel("language", "hebrew");
        models.add(keyModel2);
        KeyPropertyModel keyModel3 = new KeyPropertyModel("is_nonsense", true);
        models.add(keyModel3);
        chgModel.setProperties(models);

        Response response = jerseyTest
                .target("/reading/" + nodeId)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(chgModel));

        assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());
        ReadingModel result = response.readEntity(ReadingModel.class);
        assertEquals("snow", result.getText());
        assertEquals("hebrew", result.getLanguage());
        assertTrue(result.getIs_nonsense());
        String expectedWitnessA = "when april with his snow sweet with fruit the drought of march has pierced unto me the root";
        Response resp = new Witness(tradId, "A").getWitnessAsText();
        assertEquals(expectedWitnessA, ((TextSequenceModel) resp.getEntity()).getText());
    }

    @Test
    public void changeReadingPropertiesPropertyKeyNotFoundTest() {
        String nodeId = readingLookup.get("showers/5");

        KeyPropertyModel keyModel = new KeyPropertyModel("test", "snow");
        ReadingChangePropertyModel chgModel = new ReadingChangePropertyModel();
        chgModel.addProperty(keyModel);
        Response response = jerseyTest
                .target("/reading/" + nodeId)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(chgModel));

        assertEquals(Status.BAD_REQUEST.getStatusCode(),
                response.getStatusInfo().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        assertEquals("Reading has no such property 'test'",
                Util.getValueFromJson(response, "error"));
    }

    @Test
    public void getReadingJsonTest() throws JsonProcessingException {
        String nodeId = readingLookup.get("has/13");
        String expected = String.format("{\"id\":\"%s\",\"section\":\"%s\",\"is_common\":true,\"language\":\"Default\"," +
                "\"rank\":13,\"text\":\"has\",\"witnesses\":[\"A\",\"B\",\"C\"]}", nodeId, sectId);

        Response resp = jerseyTest
                .target("/reading/" + nodeId)
                .request(MediaType.APPLICATION_JSON).get();

        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(Include.NON_NULL);
        String json = mapper.writeValueAsString(resp
                .readEntity(ReadingModel.class));

        assertEquals(expected, json);
    }

    @Test
    public void getReadingReadingModelTest() {
        String nodeId = readingLookup.get("showers/5");
        ReadingModel expectedReadingModel;
        try (Transaction tx = db.beginTx()) {
            Node node = db.getNodeById(Long.parseLong(nodeId));
            expectedReadingModel = new ReadingModel(node);
            tx.success();
        }

            ReadingModel readingModel = jerseyTest
                    .target("/reading/" + nodeId)
                    .request(MediaType.APPLICATION_JSON).get(ReadingModel.class);

            assertNotNull(readingModel);
            assertEquals(expectedReadingModel.getRank(), readingModel.getRank());
            assertEquals(expectedReadingModel.getText(), readingModel.getText());
            assertEquals(expectedReadingModel.getWitnesses(), readingModel.getWitnesses());
    }

    @Test
    public void getReadingModelWitnesses() {
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "Legend", "LR", "1",
                "src/TestFiles/florilegium_tei_ps.xml", "teips");
        String newTradId = Util.getValueFromJson(response, "tradId");
        List<SectionModel> sects = jerseyTest.target("/tradition/" + newTradId + "/sections")
                .request()
                .get(new GenericType<>() {});
        assertEquals(1, sects.size());
        String newSectId = sects.get(0).getId();

        // Go through all readings, ensure no witness duplicates
        List<ReadingModel> allreadings = jerseyTest
                .target("/tradition/" + newTradId + "/section/" + newSectId + "/readings")
                .request()
                .get(new GenericType<>() {});

        for (ReadingModel r : allreadings) {
            List<String> witnesses = r.getWitnesses();
            HashSet<String> uniquewits = new HashSet<>(witnesses);
            assertEquals(witnesses.size(), uniquewits.size());

            // Look at reading κρίνετε, check that a.c. witnesses are handled
            if (r.getText().equals("κρίνετε")) {
                HashSet<String> krineiWits = new HashSet<>(Arrays.asList("C", "P", "S", "E", "F", "K", "Q", "H (s.l.)"));
                assertTrue(krineiWits.containsAll(uniquewits));
                assertTrue(uniquewits.containsAll(krineiWits));
            }
        }

    }

    @Test
    public void getReadingWithFalseIdTest() {
        Response response = jerseyTest
                .target("/reading/200")
                .request(MediaType.APPLICATION_JSON).get();

        assertEquals(Status.NO_CONTENT.getStatusCode(),
                response.getStatusInfo().getStatusCode());
    }

    @Test
    public void deleteRelationsOnReadingTest() {
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "John", "LR", "1",
                "src/TestFiles/john.xml", "stemmaweb");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        String newTradId = Util.getValueFromJson(response, "tradId");

        // Get our list of readings to play with
        List<ReadingModel> allreadings = jerseyTest
                .target("/tradition/" + newTradId + "/readings")
                .request()
                .get(new GenericType<>() {});

        // Find a reading with several relations
        Optional<ReadingModel> ourEn = allreadings.stream().filter(
                x -> x.getRank().equals(25L) && x.getText().equals("εν") && x.getWitnesses().contains("P66"))
                .findFirst();
        assertTrue(ourEn.isPresent());
        response = jerseyTest.target("/reading/" + ourEn.get().getId() + "/relations")
                .request()
                .delete();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<RelationModel> deleted = response.readEntity(new GenericType<>() {});
        assertEquals(2, deleted.size());
        assertEquals("orthographic", deleted.get(0).getType());
        assertEquals("orthographic", deleted.get(1).getType());

        // None of the readings at rank 25 should now have any related readings
        for (ReadingModel rm : allreadings.stream().filter(x -> x.getRank().equals(25L)).collect(Collectors.toList())) {
            response = jerseyTest.target("/reading/" + rm.getId() + "/related").request().get();
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            assertEquals(0, response.readEntity(new GenericType<List<ReadingModel>>() {}).size());
        }

        // Find a reading in a relation knot, and make sure that only its relations get deleted
        Optional<ReadingModel> ourEcti = allreadings.stream()
                .filter(x -> x.getRank().equals(28L) && x.getText().equals("εϲτι¯")).findFirst();
        assertTrue(ourEcti.isPresent());
        response = jerseyTest.target("/reading/" + ourEcti.get().getId() + "/relations")
                .request()
                .delete();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        deleted = response.readEntity(new GenericType<>() {});
        assertEquals(2, deleted.size());
        assertEquals("orthographic", deleted.get(0).getType());
        assertEquals("orthographic", deleted.get(1).getType());

        // We should still have two relations at rank 28, both attached to εϲτιν
        Optional<ReadingModel> ourEctin = allreadings.stream()
                .filter(x -> x.getRank().equals(28L) && x.getText().equals("εϲτιν")).findFirst();
        assertTrue(ourEctin.isPresent());
        response = jerseyTest.target("/reading/" + ourEctin.get().getId() + "/related").request().get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(2, response.readEntity(new GenericType<List<ReadingModel>>() {}).size());

    }

    @Test
    public void cannotDeleteReadingTest() {
        String nodeId = readingLookup.get("showers/5");
        Response response = jerseyTest.target("/reading/" + nodeId).request().delete();
        assertEquals(Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        String answer = Util.getValueFromJson(response, "error");
        assertEquals("Only emendation readings can be deleted", answer);
    }

    @Test
    public void deleteEmendationTest() {
        // Get the state of the graph beforehand
        Set<Node> allNodes = new HashSet<>();
        Set<Relationship> allLinks = new HashSet<>();
        try (Transaction tx = db.beginTx()) {
            allNodes = VariantGraphService.returnEntireTradition(tradId, db).nodes().stream().collect(Collectors.toSet());
            allLinks = VariantGraphService.returnEntireTradition(tradId, db).relationships().stream().collect(Collectors.toSet());
            tx.success();
        } catch (Exception e) {
            fail();
        }

        // Propose an emendation
        ProposedEmendationModel pem = new ProposedEmendationModel();
        pem.setAuthority("A. Caesar");
        pem.setText("fructumque");
        pem.setFromRank(7L);
        pem.setToRank(9L);
        GraphModel emendation = jerseyTest
                .target("/tradition/" + tradId + "/section/" + sectId + "/emend")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(pem), GraphModel.class);
        ReadingModel emended = emendation.getReadings().iterator().next();

        // Now try deleting it
        Response resp = jerseyTest.target("/reading/" + emended.getId()).request().delete();
        assertEquals(Status.OK.getStatusCode(), resp.getStatus());
        GraphModel deleted = resp.readEntity(GraphModel.class);

        // What was emended should now equal what was deleted.
        for (ReadingModel rm : emendation.getReadings()) {
            assertTrue(deleted.getReadings().stream().anyMatch(x -> rm.getId().equals(x.getId())));
        }
        for (ReadingModel rm : deleted.getReadings()) {
            assertTrue(emendation.getReadings().stream().anyMatch(x -> rm.getId().equals(x.getId())));
        }

        for (SequenceModel sm : emendation.getSequences()) {
            assertTrue(deleted.getSequences().stream().anyMatch(x -> sm.getId().equals(x.getId())));
        }
        for (SequenceModel sm : deleted.getSequences()) {
            assertTrue(emendation.getSequences().stream().anyMatch(x -> sm.getId().equals(x.getId())));
        }
        // Everything else should be as before.
        try (Transaction tx = db.beginTx()) {
            for (Node n : VariantGraphService.returnEntireTradition(tradId, db).nodes())
                assertTrue(allNodes.contains(n));
            for (Relationship r : VariantGraphService.returnEntireTradition(tradId, db).relationships())
                assertTrue(allLinks.contains(r));
            tx.success();
        } catch (Exception e) {
            fail();
        }

        // Re-add the emendation and lemmatize some text
        emendation = jerseyTest
                .target("/tradition/" + tradId + "/section/" + sectId + "/emend")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(pem), GraphModel.class);
        emended = emendation.getReadings().iterator().next();

        MultivaluedMap<String, String> lemmaParam = new MultivaluedHashMap<>();
        lemmaParam.add("value", "true");
        resp = jerseyTest
                .target("/reading/" + emended.getId() + "/setlemma")
                .request()
                .post(Entity.entity(lemmaParam, MediaType.APPLICATION_FORM_URLENCODED));
        assertEquals(Status.OK.getStatusCode(), resp.getStatus());

        String[] lemmatised = new String[]{"when/1", "april/2", "showers/5", "sweet/6", "the/9", "drought/10",
                "of/11", "march/12", "has/13", "pierced/14", "the root/16"};
        for (String l : lemmatised) {
            resp = jerseyTest
                    .target("/reading/" + readingLookup.get(l) + "/setlemma")
                    .request()
                    .post(Entity.entity(lemmaParam, MediaType.APPLICATION_FORM_URLENCODED_TYPE));
            assertEquals(Status.OK.getStatusCode(), resp.getStatus());
        }
        resp = jerseyTest
                .target("/tradition/" + tradId + "/section/" + sectId + "/setlemma")
                .request()
                .post(Entity.text(null));
        assertEquals(Status.OK.getStatusCode(), resp.getStatus());
        assertEquals("when april showers sweet fructumque the drought of march has pierced the root",
                Util.getValueFromJson(jerseyTest
                        .target("/tradition/" + tradId + "/section/" + sectId + "/lemmatext")
                        .queryParam("final", "true")
                        .request()
                        .get(), "text"));

        // Now delete the emendation again; the lemma links should also be deleted.
        resp = jerseyTest.target("/reading/" + emended.getId()).request().delete();
        assertEquals(Status.OK.getStatusCode(), resp.getStatus());
        GraphModel lemmadeleted = resp.readEntity(GraphModel.class);

        // What was emended should now be deleted.
        for (ReadingModel rm : emendation.getReadings()) {
            assertTrue(lemmadeleted.getReadings().stream().anyMatch(x -> rm.getId().equals(x.getId())));
        }
        for (ReadingModel rm : lemmadeleted.getReadings()) {
            assertTrue(emendation.getReadings().stream().anyMatch(x -> rm.getId().equals(x.getId())));
        }

        for (SequenceModel sm : emendation.getSequences()) {
            assertTrue(lemmadeleted.getSequences().stream().anyMatch(x -> sm.getId().equals(x.getId())));
        }
        int lemmalinkct = 0;
        for (SequenceModel sm : lemmadeleted.getSequences()) {
            if (sm.getType().equals("LEMMA_TEXT"))
                lemmalinkct++;
            else
                assertTrue(emendation.getSequences().stream().anyMatch(x -> sm.getId().equals(x.getId())));
        }
        assertEquals(13, lemmalinkct);

        // Check the lemma text again
        assertEquals("", Util.getValueFromJson(jerseyTest
                        .target("/tradition/" + tradId + "/section/" + sectId + "/lemmatext")
                        .queryParam("final", "true")
                        .request()
                        .get(), "text"));

        // Check that we are back to our original state
        try (Transaction tx = db.beginTx()) {
            for (Node n : VariantGraphService.returnEntireTradition(tradId, db).nodes())
                assertTrue(allNodes.contains(n));
            for (Relationship r : VariantGraphService.returnEntireTradition(tradId, db).relationships())
                assertTrue(allLinks.contains(r));
            tx.success();
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void propagateReadingNormalFormTest() {
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "John", "LR", "1",
                "src/TestFiles/john.xml", "stemmaweb");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        String newTradId = Util.getValueFromJson(response, "tradId");

        // Get our list of readings to play with
        List<ReadingModel> allreadings = jerseyTest
                .target("/tradition/" + newTradId + "/readings")
                .request()
                .get(new GenericType<>() {});

        // Find a set of readings to propagate on
        Optional<ReadingModel> apolusw = allreadings.stream()
                .filter(x -> x.getRank().equals(46L) && x.getText().equals("ἀπολύσω")).findFirst();
        assertTrue(apolusw.isPresent());
        response = jerseyTest
                .target("/reading/" + apolusw.get().getId() + "/normaliseRelated/orthographic")
                .request()
                .post(Entity.text(null));
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<ReadingModel> changed = response.readEntity(new GenericType<>(){});
        assertEquals(2, changed.size());

        // Now all readings at that rank should have the same normal form
        allreadings.stream().filter(x -> x.getRank().equals(46L))
                .map(x -> jerseyTest.target("/reading/" + x.getId()).request().get(ReadingModel.class))
                .forEach(r -> assertEquals("ἀπολύσω", r.getNormal_form()));

        // Change the first reading's normal form and try again
        KeyPropertyModel km = new KeyPropertyModel("normal_form", "Ἀπολύσω");
        ReadingChangePropertyModel rcpm = new ReadingChangePropertyModel();
        rcpm.addProperty(km);
        response = jerseyTest.target("/reading/" + apolusw.get().getId())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(rcpm));
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        // Re-do the operation and make sure all the normal forms changed
        response = jerseyTest.target("/reading/" + apolusw.get().getId() + "/normaliseRelated/orthographic")
                .request()
                .post(Entity.text(null));
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        changed = response.readEntity(new GenericType<>(){});
        assertEquals(2, changed.size());

        // Now all readings at that rank should have the same normal form
        allreadings.stream().filter(x -> x.getRank().equals(46L))
                .map(x -> jerseyTest.target("/reading/" + x.getId()).request().get(ReadingModel.class))
                .forEach(r -> assertEquals("Ἀπολύσω", r.getNormal_form()));
    }

    @Test
    public void insertLacunaTest() {
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "John", "LR", "1",
                "src/TestFiles/john.xml", "stemmaweb");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        // String newTradId = Util.getValueFromJson(response, "tradId");

        // Find our target readings
        String nithia = null;
        String Legei = null;
        ReadingModel umin;
        try (Transaction tx = db.beginTx()) {
            List<ReadingModel> rank1 = db.findNodes(Nodes.READING, "rank", 1L).stream()
                    .map(ReadingModel::new).collect(Collectors.toList());
            for (ReadingModel r : rank1) {
                if (r.getText().equals("ν̣ηθια")) nithia = r.getId();
                if (r.getText().equals("Λεγει")) Legei = r.getId();
            }

            Node uminRdg = db.findNode(Nodes.READING, "rank", 32L);
            assertNotNull(uminRdg);
            umin = new ReadingModel(uminRdg);
            tx.success();
        }
        assertNotNull(nithia);
        assertNotNull(Legei);
        assertNotNull(umin);

        // -- Simple tests ("ν̣ηθια")
        // First try setting the lacuna on the wrong witness
        response = jerseyTest.target("/reading/" + nithia + "/lacunaAfter")
                .queryParam("witness", "w290")
                .request()
                .post(Entity.text(null));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());

        // Now try setting it on several witnesses including the right one
        response = jerseyTest.target("/reading/" + nithia + "/lacunaAfter")
                .queryParam("witness", "w290")
                .queryParam("witness", "P60")
                .request()
                .post(Entity.text(null));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());

        // Now try making the right request.
        response = jerseyTest.target("/reading/" + nithia + "/lacunaAfter")
                .queryParam("witness", "P60")
                .request()
                .post(Entity.text(null));
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        // Check the answer
        GraphModel result = response.readEntity(GraphModel.class);
        assertEquals(1, result.getReadings().size());
        assertEquals(0, result.getRelations().size());
        assertEquals(2, result.getSequences().size());
        String lacunaId = "";
        for (ReadingModel r : result.getReadings()) {
            assertTrue(r.getIs_lacuna());
            assertEquals(Long.valueOf(2), r.getRank());
            lacunaId = r.getId();
        }
        result.getReadings().forEach(x -> assertTrue(x.getIs_lacuna()));
        String following = "";
        for (SequenceModel s : result.getSequences()) {
            if (s.getSource().equals(nithia)) {
                assertEquals(lacunaId, s.getTarget());
            } else {
                assertEquals(lacunaId, s.getSource());
                following = s.getTarget();
            }
        }
        // Check that the following reading is what we expect and that the rank didn't change
        ReadingModel followingRdg = jerseyTest.target("/reading/" + following).request().get(ReadingModel.class);
        assertEquals(umin.getText(), followingRdg.getText());
        assertEquals(umin.getRank(), followingRdg.getRank());

        // -- Multiple-path tests (Λεγει)
        // Make the request
        response = jerseyTest.target("/reading/" + Legei + "/lacunaAfter")
                .queryParam("witness", "w37")
                .queryParam("witness", "w38")
                .request()
                .post(Entity.text(null));
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        // Check the answer
        result = response.readEntity(GraphModel.class);
        assertEquals(1, result.getReadings().size());
        assertEquals(0, result.getRelations().size());
        assertEquals(2, result.getSequences().size());

        for (SequenceModel s : result.getSequences()) {
            assertEquals(2, s.getWitnesses().size());
            assertTrue(s.getWitnesses().contains("w37"));
            assertTrue(s.getWitnesses().contains("w38"));
            // Find the following reading
            if (!s.getSource().equals(Legei))
                following = s.getTarget();
        }
        // Check that the following reading has two inbound paths, one with the rest of
        // the witnesses
        try (Transaction tx = db.beginTx()) {
            for (Relationship inbound : db.getNodeById(Long.parseLong(following)).getRelationships(ERelations.SEQUENCE, Direction.INCOMING)) {
                if (inbound.getStartNode().getId() == Long.parseLong(Legei)) {
                    List<String> sigla = Arrays.asList((String []) inbound.getProperty("witnesses"));
                    assertEquals(5, sigla.size());
                    assertTrue(sigla.contains("w11"));
                    assertTrue(sigla.contains("w2"));
                    assertTrue(sigla.contains("w211"));
                    assertTrue(sigla.contains("w44"));
                    assertTrue(sigla.contains("w54"));
                }
            }
            tx.success();
        }

        // -- No rank gap tests (ὑμῖν)
        // Make the request
        response = jerseyTest
                .target("/reading/" + umin.getId() + "/lacunaAfter")
                .queryParam("witness", "w44")
                .request()
                .post(Entity.text(null));
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        // Check the answer - lots of readings should have changed rank
        result = response.readEntity(GraphModel.class);
        assertEquals(124, result.getReadings().size());
        assertEquals(0, result.getRelations().size());
        assertEquals(2, result.getSequences().size());
        Optional<ReadingModel> newLacuna = result.getReadings().stream().filter(ReadingModel::getIs_lacuna).findFirst();
        assertTrue(newLacuna.isPresent());
        assertEquals(Long.valueOf(33), newLacuna.get().getRank());

        // Check that the following node was re-ranked
        for (SequenceModel s : result.getSequences()) {
            // Find the following reading
            if (!s.getSource().equals(umin.getId()))
                following = s.getTarget();
        }
        followingRdg = jerseyTest.target("/reading/" + following).request().get(ReadingModel.class);
        assertEquals("Ινα", followingRdg.getText());
        assertEquals(Long.valueOf(34), followingRdg.getRank());
    }

    @Test
    public void duplicateTest() {
        String firstNodeId = readingLookup.get("showers/5");
        String secondNodeId = readingLookup.get("sweet/6");

        // get existing relationships
        List<RelationModel> allRels = jerseyTest.target("/tradition/" + tradId + "/relations")
                .request()
                .get(new GenericType<>() {});

        // set a lemma
        Response response = jerseyTest.target("/reading/" + firstNodeId + "/setlemma")
                .queryParam("value", "true")
                .request()
                .post(Entity.entity(null, MediaType.APPLICATION_FORM_URLENCODED));
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        // duplicate reading
        List<String> rdgs = new ArrayList<>();
        rdgs.add(firstNodeId);
        rdgs.add(secondNodeId);
        DuplicateModel jsonPayload = new DuplicateModel();
        jsonPayload.setReadings(rdgs);
        jsonPayload.setWitnesses(new ArrayList<>(Arrays.asList("A", "B")));

        response = jerseyTest
                .target("/reading/" + firstNodeId + "/duplicate")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(jsonPayload));

        // Check that no relationships were harmed by this duplication
        assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());
        GraphModel readingsAndRelationshipsModel = response.readEntity(GraphModel.class);
        assertEquals(0, readingsAndRelationshipsModel.getRelations().size());
        assertEquals(4, readingsAndRelationshipsModel.getSequences().size());

        List<RelationModel> ourRels = jerseyTest.target("/tradition/" + tradId + "/relations")
                .request()
                .get(new GenericType<>() {});
        assertEquals(allRels.size(), ourRels.size());
        for (RelationModel rm : allRels) {
            long found = ourRels.stream()
                    .filter(x -> x.getSource().equals(rm.getSource()) && x.getTarget().equals(rm.getTarget())
                            && x.getType().equals(rm.getType())).count();
            assertEquals(1L, found);
        }

        testNumberOfReadingsAndWitnesses(31);

        // check that orig_reading was set in the model
        List<ReadingModel> readingModels = new ArrayList<>(readingsAndRelationshipsModel.getReadings());
        ReadingModel showersModel;
        ReadingModel sweetModel;
        if (readingModels.get(0).getText().equals("showers")) {
            showersModel = readingModels.get(0);
            sweetModel = readingModels.get(1);
        } else {
            showersModel = readingModels.get(1);
            sweetModel = readingModels.get(0);
        }
        assertEquals(firstNodeId, showersModel.getOrig_reading());
        assertEquals(secondNodeId, sweetModel.getOrig_reading());

        // check that the nodes exist, and that orig_reading was not set on the node
        Node duplicatedShowers = null;
        Node duplicatedSweet = null;
        Node firstNode;
        Node secondNode;
        try (Transaction tx = db.beginTx()) {
            firstNode = db.getNodeById(Long.parseLong(firstNodeId));
            secondNode = db.getNodeById(Long.parseLong(secondNodeId));
            ResourceIterator<Node> showers = db.findNodes(Nodes.READING, "text", "showers");
            while (showers.hasNext()) {
                Node n = showers.next();
                if (!n.equals(firstNode))
                    duplicatedShowers = n;
            }
            assertNotNull(duplicatedShowers);
            assertFalse(duplicatedShowers.hasProperty("orig_reading"));
            assertFalse(duplicatedShowers.hasProperty("is_lemma"));

            ResourceIterator<Node> sweet = db.findNodes(Nodes.READING, "text", "sweet");
            while (sweet.hasNext()) {
                Node n = sweet.next();
                if (!n.equals(secondNode))
                    duplicatedSweet = n;
            }
            assertNotNull(duplicatedSweet);
            assertFalse(duplicatedSweet.hasProperty("orig_reading"));
            assertFalse(duplicatedSweet.hasProperty("is_lemma"));

            // compare original and duplicated
            Iterable<String> keys = firstNode.getPropertyKeys();
            for (String key : keys) {
                if (key.equals("is_lemma")) continue;
                String val1 = firstNode.getProperty(key).toString();
                String val2 = duplicatedShowers.getProperty(key).toString();
                assertEquals(val1, val2);
            }

            keys = secondNode.getPropertyKeys();
            for (String key : keys) {
                String val1 = secondNode.getProperty(key).toString();
                String val2 = duplicatedSweet.getProperty(key).toString();
                assertEquals(val1, val2);
            }
            tx.success();
        }
    }

    @Test
    public void duplicateWithDuplicateForTwoWitnessesTest() {
        // get all relationships
        List<RelationModel> allRels = jerseyTest
                .target("/tradition/" + tradId + "/relations")
                .request()
                .get(new GenericType<>() {});
        // add a relationship between droughts
        RelationModel drel = new RelationModel();
        drel.setSource(String.valueOf(readingLookup.get("drought/10")));
        drel.setTarget(String.valueOf(readingLookup.get("drought/12")));
        drel.setType("transposition");
        drel.setScope("local");
        Response response = jerseyTest
                .target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(drel));
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

        // duplicate reading
        try (Transaction tx = db.beginTx()) {
            Node node = db.findNode(Nodes.READING, "text", "of");
            String jsonPayload = "{\"readings\":[" + node.getId() + "], \"witnesses\":[\"A\",\"C\" ]}";
            response = jerseyTest
                    .target("/reading/" + node.getId() + "/duplicate")
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(jsonPayload));

            assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());

            // check that now-invalid relationships are gone
            GraphModel readingsAndRelationshipsModel = response.readEntity(GraphModel.class);
            ReadingModel firstWord = (ReadingModel) readingsAndRelationshipsModel.getReadings().toArray()[0];
            assertEquals("of", firstWord.getText());
            assertEquals(2, readingsAndRelationshipsModel.getRelations().size());
            List<RelationModel> ourRels = jerseyTest
                    .target("/tradition/" + tradId + "/relations")
                    .request()
                    .get(new GenericType<>() {});
            assertEquals(allRels.size() - 1, ourRels.size());
            for (RelationModel del : readingsAndRelationshipsModel.getRelations()) {
                for (RelationModel kept : ourRels) {
                    assertNotEquals(kept.getSource(), del.getSource());
                    assertNotEquals(kept.getTarget(), del.getTarget());
                }
            }

            testNumberOfReadingsAndWitnesses(30);

            // check that the new reading is really in the database
            List<Node> ofNodes = db.findNodes(Nodes.READING, "text", "of")
                    .stream().collect(Collectors.toList());
            assertEquals(2, ofNodes.size());
            Node duplicatedOf = null;
            for (Node n : ofNodes)
                if (!node.equals(n))
                    duplicatedOf = n;
            assertNotNull(duplicatedOf);

            // test that original sequences are still there
            int numberOfPaths = 0;
            for (Relationship incoming : node.getRelationships(ERelations.SEQUENCE,
                    Direction.INCOMING)) {
                assertEquals("B", ((String[]) incoming.getProperty("witnesses"))[0]);
                numberOfPaths++;
            }
            assertEquals(1, numberOfPaths);

            numberOfPaths = 0;
            for (Relationship outgoing : node.getRelationships(ERelations.SEQUENCE,
                    Direction.OUTGOING)) {
                assertEquals("B", ((String[]) outgoing.getProperty("witnesses"))[0]);
                numberOfPaths++;
            }
            assertEquals(1, numberOfPaths);

            // test witnesses and number of paths
            assertEquals(2, readingsAndRelationshipsModel.getSequences().size());
            for (SequenceModel seq : readingsAndRelationshipsModel.getSequences()) {
                assertNull(seq.getLayers());
                assertEquals(2, seq.getWitnesses().size());
                assertTrue(seq.getWitnesses().contains("A"));
                assertTrue(seq.getWitnesses().contains("C"));
            }

            numberOfPaths = 0;
            for (Relationship outgoing : duplicatedOf.getRelationships(ERelations.SEQUENCE,
                    Direction.OUTGOING)) {
                assertEquals("A", ((String[]) outgoing.getProperty("witnesses"))[0]);
                assertEquals("C", ((String[]) outgoing.getProperty("witnesses"))[1]);
                numberOfPaths++;
            }
            assertEquals(1, numberOfPaths);

            // compare original and duplicated
            Iterable<String> keys = node.getPropertyKeys();
            for (String key : keys) {
                String val1 = node.getProperty(key).toString();
                String val2 = duplicatedOf.getProperty(key).toString();
                assertEquals(val1, val2);
            }
            tx.success();
        }
    }

    @Test
    public void duplicateWithDuplicateForOneWitnessTest() {
        try (Transaction tx = db.beginTx()) {
            Node originalOf = db.findNode(Nodes.READING, "text", "of");
            Node aMarch = db.findNodes(Nodes.READING, "text", "march").next();
            assertNotNull(aMarch);
            String marchId = String.valueOf(aMarch.getId());
            // get all relationships as baseline
            List<RelationModel> origRelations = jerseyTest
                    .target("/tradition/" + tradId + "/relations")
                    .request()
                    .get(new GenericType<>() {});

            // duplicate reading
            String jsonPayload = "{\"readings\":[" + originalOf.getId() + "], \"witnesses\":[\"B\"]}";
            Response response = jerseyTest
                    .target("/reading/" + originalOf.getId() + "/duplicate")
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(jsonPayload));

            assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());

            GraphModel readingsAndRelationshipsModel = response.readEntity(GraphModel.class);
            ReadingModel firstWord = (ReadingModel) readingsAndRelationshipsModel.getReadings().toArray()[0];
            assertEquals("of", firstWord.getText());
            assertEquals(1, readingsAndRelationshipsModel.getRelations().size());
            assertEquals(2, readingsAndRelationshipsModel.getSequences().size());
            // make sure newly-invalid transposition has disappeared
            List<RelationModel> nowRelations = jerseyTest
                    .target("/tradition/" + tradId + "/relations")
                    .request()
                    .get(new GenericType<>() {});
            assertEquals(origRelations.size() - 1, nowRelations.size());
            for (RelationModel nr : nowRelations)
                assertFalse(nr.getSource().equals(marchId) || nr.getTarget().equals(marchId));

            testNumberOfReadingsAndWitnesses(30);

            Node duplicatedOf = db.getNodeById(Long.parseLong(firstWord.getId()));
            // test witnesses and number of paths
            int numberOfPaths = 0;
            for (Relationship incoming : originalOf.getRelationships(ERelations.SEQUENCE,
                    Direction.INCOMING)) {
                assertEquals("A", ((String[]) incoming.getProperty("witnesses"))[0]);
                assertEquals("C", ((String[]) incoming.getProperty("witnesses"))[1]);
                numberOfPaths++;
            }
            assertEquals(1, numberOfPaths);

            numberOfPaths = 0;
            for (Relationship incoming : duplicatedOf.getRelationships(ERelations.SEQUENCE,
                    Direction.INCOMING)) {
                assertEquals("B", ((String[]) incoming.getProperty("witnesses"))[0]);
                numberOfPaths++;
            }
            assertEquals(1, numberOfPaths);

            numberOfPaths = 0;
            for (Relationship outgoing : originalOf.getRelationships(ERelations.SEQUENCE,
                    Direction.OUTGOING)) {
                assertEquals("A", ((String[]) outgoing.getProperty("witnesses"))[0]);
                assertEquals("C", ((String[]) outgoing.getProperty("witnesses"))[1]);
                numberOfPaths++;
            }
            assertEquals(1, numberOfPaths);

            numberOfPaths = 0;
            for (Relationship outgoing : duplicatedOf.getRelationships(ERelations.SEQUENCE,
                    Direction.OUTGOING)) {
                assertEquals("B", ((String[]) outgoing.getProperty("witnesses"))[0]);
                numberOfPaths++;
            }
            assertEquals(1, numberOfPaths);

            // compare original and duplicated
            Iterable<String> keys = originalOf.getPropertyKeys();
            for (String key : keys) {
                String val1 = originalOf.getProperty(key).toString();
                String val2 = duplicatedOf.getProperty(key).toString();
                assertEquals(val1, val2);
            }
            tx.success();
        }
    }

    @Test
    public void duplicateWitnessCrossingTest() {
        // Get our reading
        String ofId = readingLookup.get("of/11");
        // Get the list of relations
        List<RelationModel> rms = jerseyTest
                .target("/tradition/" + tradId + "/relations")
                .request()
                .get(new GenericType<>() {});
        int origRelCt = rms.size();

        // duplicate reading
        String jsonPayload = "{\"readings\":[" + ofId
                + "], \"witnesses\":[\"B\" ]}";
        Response response = jerseyTest
                .target("/reading/" + ofId + "/duplicate")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(jsonPayload));
        assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());
        GraphModel result = response.readEntity(GraphModel.class);

        testNumberOfReadingsAndWitnesses(30);

        // check that our transposition is no longer there
        rms = jerseyTest
                .target("/tradition/" + tradId + "/relations")
                .request()
                .get(new GenericType<>() {});
        assertEquals(origRelCt - 1, rms.size());
        // check that the deleted relationship came back in our result
        assertEquals(1, result.getRelations().size());
        RelationModel delrm = result.getRelations().iterator().next();
        for (RelationModel rm : rms) {
            if (rm.getSource().equals(delrm.getSource()) && rm.getTarget().equals(delrm.getTarget()))
                fail();
        }
    }

    @Test
    public void duplicateWithNoWitnessesInJSONTest() {
        String rwId = readingLookup.get("rood-of-the-world/16");
        // duplicate reading
        String jsonPayload = "{\"readings\":[" + rwId
                + "], \"witnesses\":[]}";
        Response response = jerseyTest
                .target("/reading/" + rwId + "/duplicate")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(jsonPayload));

        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                response.getStatusInfo().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        assertEquals(
                "No witnesses have been assigned to the new reading",
                Util.getValueFromJson(response, "error"));
    }

    @Test
    public void duplicateWithOnlyOneWitnessTest() {
        String rwId = readingLookup.get("rood-of-the-world/16");
        // duplicate reading
        String jsonPayload = "{\"readings\":[" + rwId
                + "], \"witnesses\":[\"C\"]}";
        Response response = jerseyTest
                .target("/reading/" + rwId + "/duplicate")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(jsonPayload));

        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                response.getStatusInfo().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        assertEquals("The reading cannot be split between fewer than two witnesses",
                Util.getValueFromJson(response, "error"));
    }

    @Test
    public void duplicateWithNotAllowedWitnessesTest() {
        String rootId = readingLookup.get("root/17");
        // duplicate reading
        String jsonPayload = "{\"readings\":[" + rootId
                + "], \"witnesses\":[\"C\"]}";
        Response response = jerseyTest
                .target("/reading/" + rootId + "/duplicate")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(jsonPayload));

        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                response.getStatusInfo().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        assertEquals(
                "The reading does not contain the specified witness C",
                Util.getValueFromJson(response, "error"));
    }

    @Test
    public void duplicateLayerTest() {
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "Legend", "LR", "1",
                "src/TestFiles/florilegium_graphml.xml", "stemmaweb");
        String newTradId = Util.getValueFromJson(response, "tradId");
        List<SectionModel> sects = jerseyTest
                .target("/tradition/" + newTradId + "/sections")
                .request()
                .get(new GenericType<>() {});
        assertEquals(1, sects.size());
        String florSectId = sects.get(0).getId();

        // Test one: duplicate a reading that has an a.c. link pointing at it
        // "νόσοις", rank 69
        String nosois = Util.getSpecificReading(jerseyTest, newTradId, florSectId, "νόσοις", 69L);
        String request = "{\"readings\":[" + nosois + "], \"witnesses\":[\"A\", \"C\"]}";
        response = jerseyTest
                .target("/reading/" + nosois + "/duplicate")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(request));
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        GraphModel changedGraph = response.readEntity(GraphModel.class);
        assertEquals(1, changedGraph.getReadings().size());
        assertEquals(0, changedGraph.getRelations().size());
        assertEquals(2, changedGraph.getSequences().size());
        for (SequenceModel seq : changedGraph.getSequences()) {
            assertTrue(seq.getWitnesses().contains("A"));
            assertTrue(seq.getWitnesses().contains("C"));
            assertEquals(2, seq.getWitnesses().size());
        }

        // Test two: duplicate a reading for an a.c. witness
        // "κρίνεται", rank 34
        String krinetai = Util.getSpecificReading(jerseyTest, newTradId, florSectId, "κρίνει", 37L);
        request = "{\"readings\":[" + krinetai + "], \"witnesses\":[\"Q (a.c.)\"]}";
        response = jerseyTest
                .target("/reading/" + krinetai + "/duplicate")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(request));
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        changedGraph = response.readEntity(GraphModel.class);
        assertEquals(1, changedGraph.getReadings().size());
        assertEquals(0, changedGraph.getRelations().size());
        assertEquals(2, changedGraph.getSequences().size());
        for (SequenceModel seq : changedGraph.getSequences()) {
            assertEquals(0, seq.getWitnesses().size());
            assertEquals(1, seq.getLayers().size());
            assertTrue(seq.getLayers().containsKey("a.c."));
            assertEquals(1, seq.getLayers().get("a.c.").size());
            assertTrue(seq.getLayers().get("a.c.").contains("Q"));
        }

        // Test three: duplicate a reading right after an a.c. witness has ended
        // "τῇ", rank 47
        String entautha = Util.getSpecificReading(jerseyTest, newTradId, florSectId, "ἐνταῦθα", 89L);
        request = "{\"readings\":[" + entautha + "], \"witnesses\":[\"Q\"]}";
        response = jerseyTest
                .target("/reading/" + entautha + "/duplicate")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(request));
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        // Check the new sequences
        changedGraph = response.readEntity(GraphModel.class);
        assertEquals(1, changedGraph.getReadings().size());
        assertEquals(2, changedGraph.getSequences().size());
        for (SequenceModel seq : changedGraph.getSequences()) {
            assertEquals(1, seq.getWitnesses().size());
            assertNull(seq.getLayers());
            assertTrue(seq.getWitnesses().contains("Q"));
        }
        // The new 'entautha' has Q, but the old 'entautha' should not
        ReadingModel origTe = jerseyTest
                .target("/reading/" + entautha)
                .request()
                .get(ReadingModel.class);
        assertFalse(origTe.getWitnesses().contains("Q"));
        assertFalse(origTe.getWitnesses().contains("Q (a.c.)"));

    }

    @Test
    public void duplicateLayerConsistencyTest() {
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "570", "LR", "1",
                "src/TestFiles/milestone-570a.xml", "graphmlsingle");
        String newTradId = Util.getValueFromJson(response, "tradId");
        List<SectionModel> sects = jerseyTest
                .target("/tradition/" + newTradId + "/sections")
                .request()
                .get(new GenericType<>() {});
        assertEquals(1, sects.size());
        String msSectId = sects.get(0).getId();

        // Test three: duplicate a reading that has only a witness and the beginning of its a.c. layer
        String brnjin = Util.getSpecificReading(jerseyTest, newTradId, msSectId, "դաւ", 50L);
        String request = "{\"readings\":[" + brnjin + "], \"witnesses\":[\"A\"]}";
        response = jerseyTest
                .target("/reading/" + brnjin + "/duplicate")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(request));
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        // Find the new reading ID
        GraphModel result = response.readEntity(GraphModel.class);
        Optional<ReadingModel> duplicated = result.getReadings().stream()
                .filter(x -> x.getOrig_reading().equals(brnjin)).findFirst();
        assertTrue(duplicated.isPresent());
        String dupId = duplicated.get().getId();

        checkRdgConsistency();

        // Now try merging them again
        response = jerseyTest
                .target("/reading/" + brnjin + "/merge/" + dupId)
                .request()
                .post(Entity.text(null));
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        checkRdgConsistency();

        // Now try duplicating the reading where the layer ends
        String thi = Util.getSpecificReading(jerseyTest, newTradId, msSectId, "թի", 52L);
        request = "{\"readings\":[" + thi + "], \"witnesses\":[\"A\"]}";
        response = jerseyTest
                .target("/reading/" + brnjin + "/duplicate")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(request));
        assertEquals(Status.OK.getStatusCode(), response.getStatus());

        checkRdgConsistency();
    }

    private void checkRdgConsistency () {
        try (Transaction tx = db.beginTx()) {
            for (ResourceIterator<Node> it = db.findNodes(Nodes.READING); it.hasNext(); ) {
                Node r = it.next();
                if (r.hasProperty("is_start")) continue;
                assertTrue("dangling reading " + r.getId(), r.getRelationships(ERelations.SEQUENCE, Direction.INCOMING).iterator().hasNext());
            }
            tx.success();
        }
    }

    @Test
    public void mergeReadingsTest() {
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'fruit'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node firstNode = nodes.next();
            assertTrue(nodes.hasNext());
            Node secondNode = nodes.next();
            assertFalse(nodes.hasNext());

            // Save the model of the reading we'll lose
            ReadingModel drm = new ReadingModel(secondNode);

            // merge readings
            Response response = jerseyTest
                    .target("/reading/" + firstNode.getId()
                            + "/merge/" + secondNode.getId())
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.text(null));

            assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());
            GraphModel ourResult = response.readEntity(GraphModel.class);
            assertEquals(1, ourResult.getReadings().size());
            ourResult.getReadings().forEach(x -> assertEquals(String.valueOf(firstNode.getId()), x.getId()));
            assertEquals(0, ourResult.getRelations().size());
            assertEquals(2, ourResult.getSequences().size());
            for (SequenceModel seq : ourResult.getSequences()) {
                assertEquals("SEQUENCE", seq.getType());
                if (seq.getTarget().equals(String.valueOf(firstNode.getId()))) {
                    ReadingModel before = new ReadingModel(db.getNodeById(Long.parseLong(seq.getSource())));
                    assertEquals("with", before.getText());
                    assertEquals(Long.valueOf(7), before.getRank());
                } else if (seq.getSource().equals(String.valueOf(firstNode.getId()))) {
                    ReadingModel after = new ReadingModel(db.getNodeById(Long.parseLong(seq.getTarget())));
                    assertEquals(Long.valueOf(9), after.getRank());
                    assertEquals("the", after.getText());
                    assertTrue(after.getWitnesses().containsAll(Arrays.asList("A", "B")));
                } else
                    fail();
            }

            for (String sigil : drm.getWitnesses()) {
                response = jerseyTest
                        .target("/tradition/" + tradId + "/witness/" + sigil + "/text")
                        .request()
                        .get();
                assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());
            }

            // should contain one reading less now
            testNumberOfReadingsAndWitnesses(28);

            result = db.execute("match (w:READING {text:'fruit'}) return w");
            nodes = result.columnAs("w");
            assertTrue(nodes.hasNext());
            Node stayingNode = nodes.next();
            assertFalse(nodes.hasNext());

            // test witnesses
            Relationship incoming = stayingNode.getSingleRelationship(
                    ERelations.SEQUENCE, Direction.INCOMING);
            assertEquals("A", ((String[]) incoming.getProperty("witnesses"))[0]);
            assertEquals("B", ((String[]) incoming.getProperty("witnesses"))[1]);
            assertEquals("C", ((String[]) incoming.getProperty("witnesses"))[2]);

            int counter = 0;
            for (Relationship outgoing : stayingNode.getRelationships(
                    ERelations.SEQUENCE, Direction.OUTGOING)) {
                counter++;
                if (outgoing.getOtherNode(stayingNode).getProperty("text").equals("the")) {
                    assertEquals("A", ((String[]) outgoing.getProperty("witnesses"))[0]);
                    assertEquals("B", ((String[]) outgoing.getProperty("witnesses"))[1]);
                }
                if (outgoing.getOtherNode(stayingNode).getProperty("text").equals("to")) {
                    assertEquals("C", ((String[]) outgoing.getProperty("witnesses"))[0]);
                }
            }
            assertEquals(2, counter);

            // test relationships
            int numberOfRelationships = 0;
            for (Relationship rel : stayingNode.getRelationships(ERelations.RELATED)) {
                numberOfRelationships++;
                // test that relationships have been preserved
                if (rel.getOtherNode(stayingNode).getProperty("text").equals("the root")) {
                    assertEquals("transposition", rel.getProperty("type"));
                    assertEquals("the root", rel.getOtherNode(stayingNode).getProperty("text"));
                }

                // test that relationship between the two readings has been deleted
                assertNotSame(rel.getOtherNode(stayingNode), stayingNode);
            }
            assertEquals(1, numberOfRelationships);
            tx.success();
        }
    }

    @Test
    public void mergeRelatedReadingsTest() {
        // Find the 'april' nodes, make sure they can be merged
        List<ReadingModel> ourRdgs = jerseyTest
                .target("/tradition/" + tradId + "/readings")
                .request()
                .get(new GenericType<>() {});
        Optional<ReadingModel> aprilA = ourRdgs.stream()
                .filter(x -> x.getText().equals("april") && x.getWitnesses().contains("A")).findFirst();
        Optional<ReadingModel> aprilB = ourRdgs.stream()
                .filter(x -> x.getText().equals("april") && x.getWitnesses().contains("B")).findFirst();
        assertTrue(aprilA.isPresent());
        assertTrue(aprilB.isPresent());
        Response result = jerseyTest
                .target("/reading/" + aprilA.get().getId() + "/merge/" + aprilB.get().getId())
                .request()
                .post(Entity.text(null));
        assertEquals(Status.OK.getStatusCode(), result.getStatus());

        // Make a relation between 'his' nodes and make sure they can be merged the other way
        Optional<ReadingModel> hisA = ourRdgs.stream()
                .filter(x -> x.getText().equals("his") && x.getWitnesses().contains("A")).findFirst();
        Optional<ReadingModel> hisB = ourRdgs.stream()
                .filter(x -> x.getText().equals("his") && x.getWitnesses().contains("B")).findFirst();
        assertTrue(hisA.isPresent());
        assertTrue(hisB.isPresent());
        RelationModel link = new RelationModel();
        link.setSource(hisB.get().getId());
        link.setTarget(hisA.get().getId());
        link.setType("spelling");
        link.setScope("local");
        result = jerseyTest
                .target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(link));
        assertEquals(Status.CREATED.getStatusCode(), result.getStatus());

        result = jerseyTest
                .target("/reading/" + link.getTarget() + "/merge/" + link.getSource())
                .request()
                .post(Entity.text(null));
        assertEquals(Status.OK.getStatusCode(), result.getStatus());

        // Change 'teh' to 'the', make relation from each to 'to', make sure they can be merged
        ReadingModel rmThe, rmTeh, rmTo;
        try (Transaction tx = db.beginTx()) {
            Optional<Node> the = db.findNodes(Nodes.READING, "text", "the").stream().filter(x -> x.getProperty("rank").equals(16L)).findFirst();
            assertTrue(the.isPresent());
            rmThe = new ReadingModel(the.get());
            Node teh = db.findNode(Nodes.READING, "text", "teh");
            assertNotNull(teh);
            teh.setProperty("text", "the");
            rmTeh = new ReadingModel(teh);
            Optional<Node> to = db.findNodes(Nodes.READING, "text", "to").stream().filter(x -> x.getProperty("rank").equals(15L)).findFirst();
            assertTrue(to.isPresent());
            rmTo = new ReadingModel(to.get());
            tx.success();
        }
        link.setSource(rmTeh.getId());
        link.setTarget(rmTo.getId());
        link.setType("lexical");
        result = jerseyTest
                .target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(link));
        assertEquals(Status.CREATED.getStatusCode(), result.getStatus());

        link.setSource(rmThe.getId());
        result = jerseyTest
                .target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(link));
        assertEquals(Status.CREATED.getStatusCode(), result.getStatus());

        result = jerseyTest
                .target("/reading/" + rmThe.getId()+ "/merge/" + rmTeh.getId())
                .request()
                .post(Entity.text(null));
        assertEquals(Status.OK.getStatusCode(), result.getStatus());
        // test result - no new relation should have been created
        GraphModel ourResult = result.readEntity(GraphModel.class);
        assertEquals(0, ourResult.getRelations().size());
    }

    @Test
    public void mergeReadingsRelationTransfer() {
        HashMap<String,String> readingLookup = Util.makeReadingLookup(jerseyTest, tradId);
        // Set the to-be-deleted text to match for the merge
        ReadingChangePropertyModel rcpm = new ReadingChangePropertyModel();
        rcpm.addProperty(new KeyPropertyModel("text", "the"));
        String toDelete = readingLookup.get("teh/15");
        String toKeep = readingLookup.get("the/16");
        Response result = jerseyTest.target("/reading/" + toDelete)
                .request().put(Entity.json(rcpm));
        assertEquals(Status.OK.getStatusCode(), result.getStatus());

        // Set a same-rank lexical relation
        RelationModel rel = new RelationModel();
        rel.setSource(toDelete);
        rel.setTarget(readingLookup.get("to/15"));
        rel.setType("lexical");
        rel.setScope("local");
        result = jerseyTest.target("/tradition/" + tradId + "/relation")
                .request().post(Entity.json(rel));
        assertEquals(Status.CREATED.getStatusCode(), result.getStatus());

        // Do the merge
        result = jerseyTest
                .target("/reading/" + toKeep + "/merge/" + toDelete)
                .request().post(Entity.text(null));
        assertEquals(Status.OK.getStatusCode(), result.getStatus());
        GraphModel ourResult = result.readEntity(GraphModel.class);
        assertEquals(1, ourResult.getRelations().size());
        RelationModel newRel = ourResult.getRelations().iterator().next();
        assertEquals(rel.getTarget(), newRel.getTarget());
        assertEquals(toKeep, newRel.getSource());
        assertEquals(rel.getType(), newRel.getType());
        assertEquals(rel.getScope(), newRel.getScope());
    }

    @Test
    public void mergeReadingsRelationConflict() {
        HashMap<String,String> readingLookup = Util.makeReadingLookup(jerseyTest, tradId);
        // Set the to-be-deleted text to match for the merge
        ReadingChangePropertyModel rcpm = new ReadingChangePropertyModel();
        rcpm.addProperty(new KeyPropertyModel("text", "the"));
        String toDelete = readingLookup.get("teh/15");
        String toKeep = readingLookup.get("the/16");
        Response result = jerseyTest.target("/reading/" + toDelete)
                .request().put(Entity.json(rcpm));
        assertEquals(Status.OK.getStatusCode(), result.getStatus());

        // Set a same-rank lexical relation
        RelationModel rel = new RelationModel();
        rel.setSource(toDelete);
        rel.setTarget(readingLookup.get("to/15"));
        rel.setType("lexical");
        rel.setScope("local");
        result = jerseyTest.target("/tradition/" + tradId + "/relation")
                .request().post(Entity.json(rel));
        assertEquals(Status.CREATED.getStatusCode(), result.getStatus());

        // Set a grammatical relation to our merge target
        rel.setSource(toKeep);
        rel.setType("grammatical");
        result = jerseyTest.target("/tradition/" + tradId + "/relation")
                .request().post(Entity.json(rel));
        assertEquals(Status.CREATED.getStatusCode(), result.getStatus());

        // Try the merge
        result = jerseyTest
                .target("/reading/" + toKeep + "/merge/" + toDelete)
                .request().post(Entity.text(null));
        assertEquals(Status.CONFLICT.getStatusCode(), result.getStatus());
        assertEquals(String.format("Conflicting lexical relation to node %s prevents merge", rel.getTarget()),
                Util.getValueFromJson(result, "error"));
    }

    @Test
    public void mergeReadingsGetsCyclicTest() {
        String drought1 = readingLookup.get("drought/10");
        String drought2 = readingLookup.get("drought/12");
        // merge readings
        Response response = jerseyTest
                .target("/reading/" + drought1 + "/merge/" + drought2)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.text(null));

        assertEquals(Status.CONFLICT.getStatusCode(),
                response.getStatusInfo().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        assertEquals("Readings to be merged would make the graph cyclic",
                Util.getValueFromJson(response, "error"));

        testNumberOfReadingsAndWitnesses(29);
        // Make sure we still have two droughts
        List<ReadingModel> remaining = jerseyTest
                .target("/tradition/" + tradId + "/readings")
                .request()
                .get(new GenericType<>() {});
        assertEquals(2, remaining.stream().filter(x -> x.getText().equals("drought")).count());
    }

    @Test
    public void mergeReadingsGetsCyclicWithNodesFarApartTest() {
        String to1 = readingLookup.get("to/9");
        String to2 = readingLookup.get("to/15");
        // merge readings
        Response response = jerseyTest
                .target("/reading/" + to2 + "/merge/" + to1)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.text(null));

        assertEquals(Status.CONFLICT.getStatusCode(),
                response.getStatusInfo().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        assertEquals("Readings to be merged would make the graph cyclic",
                Util.getValueFromJson(response, "error"));

        testNumberOfReadingsAndWitnesses(29);

        // Make sure we still have two tos
        List<ReadingModel> remaining = jerseyTest
                .target("/tradition/" + tradId + "/readings")
                .request()
                .get(new GenericType<>() {});
        assertEquals(2, remaining.stream().filter(x -> x.getText().equals("to")).count());
    }

    @Test
    public void mergeReadingsWithClassTwoRelationshipsTest() {
        String march1 = readingLookup.get("march/10");
        String march2 = readingLookup.get("march/12");
        // merge readings
        Response response = jerseyTest
                .target("/reading/" + march1 + "/merge/" + march2)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.text(null));

        assertEquals(Status.CONFLICT.getStatusCode(),
                response.getStatusInfo().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        assertEquals(
                "Readings to be merged cannot contain cross-location relations",
                Util.getValueFromJson(response, "error"));

        testNumberOfReadingsAndWitnesses(29);

        // Make sure we still have two marches
        List<ReadingModel> remaining = jerseyTest
                .target("/tradition/" + tradId + "/readings")
                .request()
                .get(new GenericType<>() {});
        assertEquals(2, remaining.stream().filter(x -> x.getText().equals("march")).count());
    }

    @Test
    public void splitReadingTest() {
        Node node;
        Node endNode;
        try (Transaction tx = db.beginTx()) {
            node = db.findNode(Nodes.READING, "text", "the root");
            assertTrue(node.hasRelationship(ERelations.RELATED));
            endNode = db.findNode(Nodes.READING, "is_end", true);

            // delete relationship, so that splitting is possible
            node.getSingleRelationship(ERelations.RELATED,
                    Direction.INCOMING).delete();

            assertFalse(node.hasRelationship(ERelations.RELATED));
            tx.success();
        }

        // split reading
        ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
        readingBoundaryModel.setCharacter(" ");
        Response response = jerseyTest
                .target("/reading/" + node.getId()
                        + "/split/0")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(readingBoundaryModel));

        assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());

        // Check the return value; there should be two changed readings and two rewritten relationships.
        GraphModel readingsAndRelationsModel = response
                .readEntity(GraphModel.class);
        // Check the readings
        assertEquals(2, readingsAndRelationsModel.getReadings().size());
        HashMap<String, String> rdgWords = new HashMap<>();
        readingsAndRelationsModel.getReadings().forEach(x -> rdgWords.put(x.getText(), x.getId()));
        assertTrue(rdgWords.containsKey("the"));
        assertTrue(rdgWords.containsKey("root"));
        for (ReadingModel rm : readingsAndRelationsModel.getReadings()) {
            if (rm.getText().equals("the"))
                assertEquals(Long.valueOf(16), rm.getRank());
            if (rm.getText().equals("root"))
                assertEquals(Long.valueOf(17), rm.getRank());
        }
        // Check the relationships
        assertEquals(2, readingsAndRelationsModel.getSequences().size());
        HashSet<String> relPaths = new HashSet<>();
        readingsAndRelationsModel.getSequences().forEach(x -> relPaths.add(x.getSource() + "->" + x.getTarget()));
        assertTrue(relPaths.contains(rdgWords.get("the") + "->" + rdgWords.get("root")));
        assertTrue(relPaths.contains(rdgWords.get("root") + "->" + endNode.getId()));

        testNumberOfReadingsAndWitnesses(30);

    }

    @Test
    public void splitReadingWithOtherSeparatorAndMultipleWordsTest() {
        String rotw = readingLookup.get("rood-of-the-world/16");
            // split reading
        ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
        readingBoundaryModel.setCharacter("-");
        Response response = jerseyTest
                .target("/reading/" + rotw
                        + "/split/0")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(readingBoundaryModel));

        assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());

        expectedWitnessC = "when showers sweet with fruit to drought of march has pierced teh rood of the world";

        testNumberOfReadingsAndWitnesses(32);
    }

    @Test
    public void splitReadingWithSlashAsSeparatorTest() {
        // prepare the database for the test
        String rotw;
        try (Transaction tx = db.beginTx()) {
            Node node = db.findNode(Nodes.READING, "text", "rood-of-the-world");
            assertNotNull(node);
            node.setProperty("text", "rood/of/the/world");
            rotw = String.valueOf(node.getId());
            tx.success();
        }

        // split reading
        ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
        readingBoundaryModel.setCharacter("/");
        Response response = jerseyTest
                .target("/reading/" + rotw + "/split/0")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(readingBoundaryModel));

        assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());

        expectedWitnessC = "when showers sweet with fruit to drought of march has pierced teh rood of the world";

        testNumberOfReadingsAndWitnesses(32);
    }

    @Test
    public void splitReadingWithOtherSeparatorAndMultipleWordsAndIndexTest() {
        String rotw = readingLookup.get("rood-of-the-world/16");
        // split reading
        ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
        readingBoundaryModel.setCharacter("-");
        Response response = jerseyTest
                .target("/reading/" + rotw + "/split/4")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(readingBoundaryModel));

        assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());

        expectedWitnessC = "when showers sweet with fruit to drought of march has pierced teh rood of-the-world";

        testNumberOfReadingsAndWitnesses(30);
    }

    @Test
    public void splitReadingWithLongSeparatorAndIndexTest() {
        String rotw = readingLookup.get("rood-of-the-world/16");
        // split reading
        ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
        readingBoundaryModel.setCharacter("-of-");
        Response response = jerseyTest
                .target("/reading/" + rotw + "/split/4")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(readingBoundaryModel));

        assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());

        expectedWitnessC = "when showers sweet with fruit to drought of march has pierced teh rood the-world";

        testNumberOfReadingsAndWitnesses(30);
    }


    @Test
    public void splitReadingWithNotExistingSeparatorInWordTest() {
        String rotw = readingLookup.get("rood-of-the-world/16");

        // split reading
        ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
        readingBoundaryModel.setCharacter("/");
        Response response = jerseyTest
                .target("/reading/" + rotw + "/split/2")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(readingBoundaryModel));

        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                response.getStatusInfo().getStatusCode());
        assertEquals("no such separator exists",
                Util.getValueFromJson(response, "error"));
    }

    @Test
    public void splitReadingWithNotExistingSeparatorInIndexTest() {
        // prepare the database for the test
        String root = readingLookup.get("root/17");
        ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
        readingBoundaryModel.setCharacter("t");
        Response response = jerseyTest
                .target("/reading/" + root + "/split/2")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(readingBoundaryModel));

        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                response.getStatusInfo().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        assertEquals("The separator does not appear in the index location in the text",
                Util.getValueFromJson(response, "error"));
    }

    @Test
    public void splitReadingWithExistingSeparatorInIndexTest() {
        String root = readingLookup.get("root/17");

        // split reading
        ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
        readingBoundaryModel.setCharacter("oo");
        Response response = jerseyTest
                .target("/reading/" + root + "/split/1")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(readingBoundaryModel));


        assertEquals(Status.OK.getStatusCode(),
                response.getStatusInfo().getStatusCode());
        expectedWitnessA = "when april with his showers sweet with fruit the drought of march has pierced unto me the r t";

        testNumberOfReadingsAndWitnesses(30);
    }

    @Test
    public void splitReadingWithExistingSeparatorInIndexOneCharTest() {
        String root = readingLookup.get("root/17");

        // split reading
        ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
        readingBoundaryModel.setCharacter("o");
        Response response = jerseyTest
                .target("/reading/" + root + "/split/1")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(readingBoundaryModel));


        assertEquals(Status.OK.getStatusCode(),
                response.getStatusInfo().getStatusCode());
        expectedWitnessA = "when april with his showers sweet with fruit the drought of march has pierced unto me the r ot";

        testNumberOfReadingsAndWitnesses(30);
    }

    @Test
    public void splitReadingWithQuotesAsSeparatorTest() {
        // prepare the database for the test
        String rotw;
        try (Transaction tx = db.beginTx()) {
            Node node = db.findNode(Nodes.READING, "text", "rood-of-the-world");
            assertNotNull(node);
            node.setProperty("text", "rood\"of\"the\"world");
            rotw = String.valueOf(node.getId());
            tx.success();
        }

        // split reading
        ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
        readingBoundaryModel.setCharacter("\"");
        Response response = jerseyTest
                .target("/reading/" + rotw + "/split/0")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(readingBoundaryModel));

        assertEquals(Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());

        // check that the text is right
        expectedWitnessC = "when showers sweet with fruit to drought of march has pierced teh rood of the world";

        // check that the end node has the right rank
        SectionModel ourSection = jerseyTest
                .target("/tradition/" + tradId + "/section/" + sectId)
                .request()
                .get(SectionModel.class);
        assertEquals(Long.valueOf(20), ourSection.getEndRank());
    }

    @Test
    public void splitReadingWithWrongIndexTest() {
        String root = readingLookup.get("root/17");

        // split reading
        ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
        readingBoundaryModel.setCharacter("");
        Response response = jerseyTest
                .target("/reading/" + root + "/split/7")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(readingBoundaryModel));

        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                response.getStatusInfo().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        assertEquals("The index must be smaller than the text length",
                Util.getValueFromJson(response, "error"));

        testNumberOfReadingsAndWitnesses(29);
    }

    @Test
    public void splitReadingWithRelationshipTest() {
        String root = readingLookup.get("the root/16");
        // split reading
        ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
        readingBoundaryModel.setCharacter(" ");
        Response response = jerseyTest
                .target("/reading/" + root + "/split/0")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(readingBoundaryModel));

        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                response.getStatusInfo().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        assertEquals(
                "A reading to be split cannot be part of any relation",
                Util.getValueFromJson(response, "error"));

        testNumberOfReadingsAndWitnesses(29);
    }

    /**
     * tests the rank reassignment of readings that follow a split, when there wasn't a prior
     * gap in the ranks
     */
    @Test
    public void splitReadingNoRankGapTest() {
        try (Transaction tx = db.beginTx()) {
            Node untoMe = db.findNode(Nodes.READING, "text", "unto me");
            assertNotNull(untoMe);

            // find the rank of this reading and the following ones
            Object thisRank = untoMe.getProperty("rank");
            HashMap<Long, Long> readingRanks = new HashMap<>();
            for (Relationship r : untoMe.getRelationships(ERelations.SEQUENCE, Direction.OUTGOING)) {
                Node next = r.getEndNode();
                readingRanks.put(next.getId(), Long.valueOf(next.getProperty("rank").toString()));
            }

            // split reading
            ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
            readingBoundaryModel.setCharacter("");
            Response response = jerseyTest
                    .target("/reading/" + untoMe.getId() + "/split/0")
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(readingBoundaryModel));

            assertEquals(Status.OK.getStatusCode(), response.getStatus());

            // check the re-ranking
            assertEquals(thisRank, untoMe.getProperty("rank"));
            for (Long nid : readingRanks.keySet()) {
                Long savedRank = readingRanks.get(nid);
                Node n = db.getNodeById(nid);
                if (savedRank == (Long) thisRank + 1) {
                    assertEquals(savedRank + 1, n.getProperty("rank"));
                } else
                    assertEquals(savedRank, n.getProperty("rank"));
            }
            tx.success();
        }
    }

    /**
     * test a reading split where the text should not be separated
     */
    @Test
    public void splitReadingSeparateFalseTest() {
        try (Transaction tx = db.beginTx()) {
            Node untome = db.findNode(Nodes.READING, "text", "unto me");
            assertNotNull(untome);

            ReadingBoundaryModel rbm = new ReadingBoundaryModel();
            rbm.setSeparate(false);
            rbm.setCharacter("");
            Response response = jerseyTest
                    .target("/reading/" + untome.getId() + "/split/2")
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(rbm));
            assertEquals(Status.OK.getStatusCode(), response.getStatus());

            // Find the new nodes
            assertEquals("un", untome.getProperty("text"));
            Node follower = db.findNode(Nodes.READING, "text", "to me");
            assertNotNull(follower);
            assertTrue(follower.hasProperty("join_prior"));
            assertEquals(true, follower.getProperty("join_prior"));
            tx.success();
        }
    }

    @Test
    public void splitReadingZeroLengthRegexTest() {
        try (Transaction tx = db.beginTx()) {
            Node rood = db.findNode(Nodes.READING, "text", "rood-of-the-world");
            assertNotNull(rood);

            // Try it with a non-matching regex
            ReadingBoundaryModel rbm = new ReadingBoundaryModel();
            rbm.setSeparate(false);
            rbm.setCharacter("(?=0)");
            rbm.setIsRegex(true);
            Response response = jerseyTest
                    .target("/reading/" + rood.getId() + "/split/0")
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(rbm));
            assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
            assertEquals("The given regular expression does not match the original text",
                    Util.getValueFromJson(response, "error"));

            // Now try one that matches
            rbm.setCharacter("(?=-)");
            response = jerseyTest
                    .target("/reading/" + rood.getId() + "/split/0")
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(rbm));
            assertEquals(Status.OK.getStatusCode(), response.getStatus());
            GraphModel result = response.readEntity(GraphModel.class);
            assertEquals(4, result.getReadings().size());
            assertEquals(4, result.getSequences().size());

            Node nof = db.findNode(Nodes.READING, "text", "-of");
            assertNotNull(nof);
            assertEquals(true, nof.getProperty("join_prior"));

            Response witText = new Witness(tradId, "C").getWitnessAsText();
            assertEquals(expectedWitnessC, ((TextSequenceModel) witText.getEntity()).getText());

            tx.success();
        }
    }

    /**
     * test that all readings of a tradition are returned sorted ascending
     * according to rank
     */
    @Test(expected = org.junit.ComparisonFailure.class)
    public void allReadingsOfTraditionTest() {
        List<ReadingModel> listOfReadings = jerseyTest
                .target("/tradition/" + tradId + "/readings")
                .request()
                .get(new GenericType<>() {
                });
        Collections.sort(listOfReadings);

        assertEquals(29, listOfReadings.size());

        String expectedTest = "#START# when april april with his his showers sweet with fruit fruit the to drought march of march drought has pierced teh to unto me rood-of-the-world the root the root #END#";
        List<String> words = listOfReadings.stream().map(ReadingModel::getText).collect(Collectors.toList());
        String text = String.join(" ", words);
        assertEquals(expectedTest, text);

        int[] expectedRanks = { 0, 1, 2, 2, 3, 4, 4, 5, 6, 7, 8, 9, 10, 10, 11,
                11, 12, 13, 13, 14, 15, 16, 16, 16, 17, 17, 17, 18, 21 };
        for (int i = 0; i < listOfReadings.size(); i++) {
            assertEquals(expectedRanks[i], (int) (long) listOfReadings.get(i).getRank());
        }
    }

    @Test
    public void allReadingsOfTraditionNotFoundTest() {
        String falseTradId = "I don't exist";
        Response response = jerseyTest.target("/tradition/" + falseTradId + "/readings")
                .request()
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
                response.getStatus());
        assertEquals("application/json;charset=utf-8", response.getMediaType().toString());
        assertEquals("There is no tradition with this id",
                Util.getValueFromJson(response, "error"));
    }

    @Test
    public void identicalReadingsOneResultTest() {
        List<ReadingModel> identicalReadings;

        List<List<ReadingModel>> listOfIdenticalReadings = jerseyTest
                .target("/tradition/" + tradId + "/section/" + sectId + "/identicalreadings/3/9")
                .request()
                .get(new GenericType<>() {});
        assertEquals(1, listOfIdenticalReadings.size());
        identicalReadings = listOfIdenticalReadings.get(0);
        assertEquals(2, identicalReadings.size());
        assertEquals("fruit", identicalReadings.get(1).getText());

        assertEquals(identicalReadings.get(0).getText(),
                identicalReadings.get(1).getText());
    }

    @Test
    public void identicalReadingsTwoResultsTest() {
        List<ReadingModel> identicalReadings;

        List<List<ReadingModel>> listOfIdenticalReadings = jerseyTest
                .target("/tradition/" + tradId + "/section/" + sectId + "/identicalreadings/1/9")
                .request()
                .get(new GenericType<>() {});
        assertEquals(2, listOfIdenticalReadings.size());

        identicalReadings = listOfIdenticalReadings.get(0);
        assertEquals(2, identicalReadings.size());
        assertEquals("april", identicalReadings.get(1).getText());
        assertEquals(identicalReadings.get(0).getText(), identicalReadings.get(1).getText());

        identicalReadings = listOfIdenticalReadings.get(1);
        assertEquals(2, identicalReadings.size());
        assertEquals("fruit", identicalReadings.get(1).getText());
        assertEquals(identicalReadings.get(0).getText(),
                identicalReadings.get(1).getText());
    }

    @Test
    public void identicalReadingsNoResultTest() {
        Response response = jerseyTest
                .target("/tradition/" + tradId + "/section/" + sectId + "/identicalreadings/10/15")
                .request()
                .get(Response.class);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
                response.getStatus());
        assertEquals("no identical readings were found", Util.getValueFromJson(response, "error"));
    }

    @Test
    public void couldBeIdenticalReadingsTest() {
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "Legend", "LR", "1",
                "src/TestFiles/legendfrag.xml", "stemmaweb");
        String newTradId = Util.getValueFromJson(response, "tradId");
        List<SectionModel> sects = jerseyTest
                .target("/tradition/" + newTradId + "/sections")
                .request()
                .get(new GenericType<>() {});
        assertEquals(1, sects.size());
        String newSectId = sects.get(0).getId();

        // Remove the 'collated' relationship that prevents merging
        try (Transaction tx = db.beginTx()) {
            db.getAllRelationships().stream()
                    .filter(x -> x.isType(ERelations.RELATED) && x.getProperty("type").equals("collated"))
                    .forEach(Relationship::delete);
            tx.success();
        }

        // Now we should have mergeable readings
        List<List<ReadingModel>> couldBeIdenticalReadings = jerseyTest
                .target("/tradition/" + newTradId + "/section/" + newSectId + "/mergeablereadings/2/9")
                .request()
                .get(new GenericType<>() {});
        assertEquals(4, couldBeIdenticalReadings.size());
        HashSet<String> expectedIdentical = new HashSet<>(Arrays.asList("beatus", "pontifex", "venerabilis", "henricus"));
        for (List<ReadingModel> cbi : couldBeIdenticalReadings) {
            assertTrue(expectedIdentical.contains(cbi.get(0).getText()));
        }

        // Check that we can ask for them individually
        List<List<ReadingModel>> mergeableHenrys = jerseyTest.target("/tradition/" + newTradId + "/section/" + newSectId + "/mergeablereadings/2/9")
                .queryParam("text", "henricus")
                .request()
                .get(new GenericType<>() {});
        assertEquals(1, mergeableHenrys.size());
        for (List<ReadingModel> mh : mergeableHenrys) {
            assertEquals("henricus", mh.get(0).getText());
            assertEquals("henricus", mh.get(1).getText());
        }
    }

    // same as above, but on a different text
    @Test
    public void mergeableReadingsTest() {
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "Legend", "LR", "1",
                "src/TestFiles/legendfrag.xml", "stemmaweb");
        String newTradId = Util.getValueFromJson(response, "tradId");
        List<SectionModel> sects = jerseyTest.target("/tradition/" + newTradId + "/sections")
                .request()
                .get(new GenericType<>() {});
        assertEquals(1, sects.size());
        String newSectId = sects.get(0).getId();
        String firstId = "";
        String secondId = "";
        try (Transaction tx = db.beginTx()) {
            // Find the venerabili
            ResourceIterator<Node> ri = db.findNodes(Nodes.READING, "text", "venerabilis");
            while (ri.hasNext()) {
                Node n = ri.next();
                if (n.getProperty("rank").equals(3L))
                    firstId = String.valueOf(n.getId());
                if (n.getProperty("rank").equals(5L))
                    secondId = String.valueOf(n.getId());
            }
            // Get rid of all the "collated" relationships
            db.getAllRelationships().stream()
                    .filter(x -> x.isType(ERelations.RELATED) && x.getProperty("type").equals("collated"))
                    .forEach(Relationship::delete);
            tx.success();
        }

        // Merge the venerabili
        response = jerseyTest
                .target("/reading/" + firstId + "/merge/" + secondId)
                .request()
                .post(Entity.text(null));
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        // Check that the ranks are correct
        try (Transaction tx = db.beginTx()) {
            Node remain = db.getNodeById(Long.parseLong(firstId));
            assertEquals(5L, remain.getProperty("rank"));
            Node capv = db.findNode(Nodes.READING, "text", "Venerabilis");
            assertEquals(5L, capv.getProperty("rank"));
            Node uene = db.findNode(Nodes.READING, "text", "uenerabilis");
            assertEquals(5L, uene.getProperty("rank"));
            tx.success();
        }

        // Check that the pontifices are mergeable
        response = jerseyTest
                .target("/tradition/" + newTradId + "/section/" + newSectId + "/mergeablereadings/3/10")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<List<ReadingModel>> r = response.readEntity(new GenericType<>() {});
        assertEquals(1, r.size());
        assertEquals("pontifex", r.get(0).get(0).getText());
    }

    /**
     * should not find any could-be identical readings
     */
    @Test
    public void couldBeIdenticalReadingsNoResultTest() {
        Response response = Util.createTraditionFromFileOrString(jerseyTest, "Legend", "LR", "1",
                "src/TestFiles/legendfrag.xml", "stemmaweb");
        String newTradId = Util.getValueFromJson(response, "tradId");
        List<SectionModel> sects = jerseyTest
                .target("/tradition/" + newTradId + "/sections")
                .request()
                .get(new GenericType<>() {});
        assertEquals(1, sects.size());
        String newSectId = sects.get(0).getId();

        response = jerseyTest
                .target("/tradition/" + newTradId + "/section/" + newSectId + "/mergeablereadings/2/9")
                .request()
                .get();

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<List<ReadingModel>> result =
                response.readEntity(new GenericType<>() {});
        assertEquals(0, result.size());
    }

    // compress with separate set to 1, but the empty string between words TODO what do we want here?
    @Ignore
    @Test
    public void compressReadingsNoConcatenatingNoTextTest() {
        String showers = readingLookup.get("showers/5");
        String sweet = readingLookup.get("sweet/6");

        ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
        readingBoundaryModel.setCharacter("");
        Response res = jerseyTest
                .target("/reading/" + showers + "/concatenate/" + sweet)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(readingBoundaryModel));

        assertEquals(Response.Status.OK.getStatusCode(), res.getStatus());
        // Get the reading and check its properties
        ReadingModel showerssweet = jerseyTest
                .target("/reading/" + showers)
                .request()
                .get(ReadingModel.class);
        assertEquals("showerssweet", showerssweet.getText());
        assertEquals(Long.valueOf(5), showerssweet.getRank());

        // there is one reading less in the tradition and witnesses have not
        // been changed
        List<ReadingModel> listOfReadings = testNumberOfReadingsAndWitnesses(28);
        Collections.sort(listOfReadings);

        // tradition still has all the texts
        String expectedTest = "#START# when april april with his his showers sweet with fruit fruit the to drought march of march drought has pierced teh to unto me rood-of-the-world the root the root #END#";
        List<String> words = listOfReadings.stream().map(ReadingModel::getText).collect(Collectors.toList());
        String text = String.join(" ", words);
        assertEquals(expectedTest, text);

        // no more reading with rank 6
        int[] expectedRanks = { 0, 1, 2, 2, 3, 4, 4, 5, 7, 8, 9, 10, 10,
                11, 11, 12, 13, 13, 14, 15, 16, 16, 16, 17, 17, 17, 18, 21 };
        for (int i = 0; i < listOfReadings.size(); i++) {
            assertEquals(expectedRanks[i],
                    (int) (long) listOfReadings.get(i).getRank());
        }
    }

    // compress with separate set to 0: no space between words
    @Test
    public void compressReadingsNoConcatenatingWithTextTest() {
        String showers = readingLookup.get("showers/5");
        String sweet = readingLookup.get("sweet/6");

        ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
        readingBoundaryModel.setSeparate(false);
        readingBoundaryModel.setCharacter("shouldNotBeDesplayd");
        Response res = jerseyTest
                .target("/reading/" + showers + "/concatenate/" + sweet)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(readingBoundaryModel));
        assertEquals(Response.Status.OK.getStatusCode(), res.getStatus());
        GraphModel ourResult = res.readEntity(GraphModel.class);
        assertEquals(1, ourResult.getReadings().size());
        assertEquals(1, ourResult.getSequences().size());
        assertEquals(0, ourResult.getRelations().size());
        ReadingModel ourCompressed = ourResult.getReadings().iterator().next();
        assertEquals(showers, ourCompressed.getId());
        assertEquals("showerssweet", ourCompressed.getText());
        SequenceModel ourLinkOut = ourResult.getSequences().iterator().next();
        assertEquals(showers, ourLinkOut.getSource());
        assertEquals(readingLookup.get("with/7"), ourLinkOut.getTarget());
        for (String w : Arrays.asList("A", "B", "C"))
            assertTrue(ourLinkOut.getWitnesses().contains(w));

        // there is one fewer reading in the tradition and witnesses now read slightly differently
        expectedWitnessA = expectedWitnessA.replace("showers sweet", "showerssweet");
        expectedWitnessB = expectedWitnessB.replace("showers sweet", "showerssweet");
        expectedWitnessC = expectedWitnessC.replace("showers sweet", "showerssweet");

        List<ReadingModel> listOfReadings = testNumberOfReadingsAndWitnesses(28);
        for (ReadingModel rm : listOfReadings) {
            if (rm.getText().equals("showers")) fail();
            if (rm.getText().equals("sweet")) fail();
            // Check that the ranks adjusted
            if (rm.getIs_end()) assertEquals(Long.valueOf(17), rm.getRank());
            if (rm.getText().equals("with") && rm.getWitnesses().size() == 3)
                assertEquals(Long.valueOf(6), rm.getRank());
        }
    }

    // compress with text between the readings' texts
    @Test
    public void compressReadingsWithConcatenatingWithConTextTest() {
        String showers = readingLookup.get("showers/5");
        String sweet = readingLookup.get("sweet/6");

        ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
        readingBoundaryModel.setCharacter("test");
        Response res = jerseyTest
                .target("/reading/" + showers + "/concatenate/" + sweet)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(readingBoundaryModel));

        assertEquals(Response.Status.OK.getStatusCode(), res.getStatus());
        GraphModel ourResult = res.readEntity(GraphModel.class);
        ReadingModel ourCompressed = ourResult.getReadings().iterator().next();
        assertNotNull(ourCompressed);
        assertEquals(showers, ourCompressed.getId());
        assertEquals("showerstestsweet", ourCompressed.getText());

        expectedWitnessA = expectedWitnessA.replace("showers sweet", "showerstestsweet");
        expectedWitnessB = expectedWitnessB.replace("showers sweet", "showerstestsweet");
        expectedWitnessC = expectedWitnessC.replace("showers sweet", "showerstestsweet");
        List<ReadingModel> listOfReadings = testNumberOfReadingsAndWitnesses(28);
        for (ReadingModel rm : listOfReadings) {
            if (rm.getText().equals("showers")) fail();
            if (rm.getText().equals("sweet")) fail();
            if (rm.getText().equals(("showerstestsweet"))) assertEquals(Long.valueOf(5), rm.getRank());
            // Check that the ranks adjusted
            if (rm.getIs_end()) assertEquals(Long.valueOf(17), rm.getRank());
            if (rm.getText().equals("with") && rm.getWitnesses().size() == 3)
                assertEquals(Long.valueOf(6), rm.getRank());
        }
        expectedWitnessA = "when april with his showerstestsweet with fruit the drought of march has pierced unto me the root";
        Response resp = new Witness(tradId, "A").getWitnessAsText();
        assertEquals(expectedWitnessA, ((TextSequenceModel) resp.getEntity()).getText());
    }

    // compress with " between the readings' texts
    @Test
    public void compressReadingsWithConcatenatingWithQuotationAsTextTest() {
        String showers = readingLookup.get("showers/5");
        String sweet = readingLookup.get("sweet/6");

        ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
        readingBoundaryModel.setCharacter("\"");
        Response res = jerseyTest
                .target("/reading/" + showers + "/concatenate/" + sweet)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(readingBoundaryModel));

        assertEquals(Response.Status.OK.getStatusCode(), res.getStatus());
        GraphModel ourResult = res.readEntity(GraphModel.class);
        ReadingModel ourCompressed = ourResult.getReadings().iterator().next();
        assertNotNull(ourCompressed);
        assertEquals(showers, ourCompressed.getId());
        assertEquals("showers\"sweet", ourCompressed.getText());

        expectedWitnessA = "when april with his showers\"sweet with fruit the drought of march has pierced unto me the root";
        Response resp = new Witness(tradId, "A").getWitnessAsText();
        assertEquals(expectedWitnessA, ((TextSequenceModel) resp.getEntity()).getText());
    }

    // compress with / between the readings' texts
    @Test
    public void compressReadingsWithConcatenatingWithSlashAsTextTest() {
        String showers = readingLookup.get("showers/5");
        String sweet = readingLookup.get("sweet/6");

        ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
        readingBoundaryModel.setCharacter("/");
        Response res = jerseyTest
                .target("/reading/" + showers + "/concatenate/" + sweet)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(readingBoundaryModel));

        assertEquals(Response.Status.OK.getStatusCode(), res.getStatus());

        expectedWitnessB = "when april his showers/sweet with fruit the march of drought has pierced to the root";
        Response resp = new Witness(tradId, "B").getWitnessAsText();
        assertEquals(expectedWitnessB, ((TextSequenceModel) resp.getEntity()).getText());
    }

    /**
     * the given reading are not neighbors Should return error tests that
     * readings were not compressed
     */
    @Test
    public void notNeighborsCompressReadingTest() {
        String showers = readingLookup.get("showers/5");
        String with = readingLookup.get("with/7");

        ReadingBoundaryModel readingBoundaryModel = new ReadingBoundaryModel();
        readingBoundaryModel.setCharacter("shouldNotBeDesplayd");
        Response response = jerseyTest
                .target("/reading/" + showers + "/concatenate/" + with)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(readingBoundaryModel));

        assertEquals(Response.Status.CONFLICT.getStatusCode(),
                response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        assertEquals("reading are not contiguous. could not compress",
                Util.getValueFromJson(response, "error"));
        testNumberOfReadingsAndWitnesses(29);
    }

    @Test
    public void splitCompressJoinedReadingTest() {
        try (Transaction tx = db.beginTx()) {
            Node rood = db.findNode(Nodes.READING, "text", "rood-of-the-world");
            assertNotNull(rood);

            // First split it, unseparated
            ReadingBoundaryModel rbm = new ReadingBoundaryModel();
            rbm.setSeparate(false);
            rbm.setCharacter("-");
            Response response = jerseyTest
                    .target("/reading/" + rood.getId() + "/split/0")
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(rbm));
            assertEquals(Status.OK.getStatusCode(), response.getStatus());
            GraphModel result = response.readEntity(GraphModel.class);
            HashMap<String, String> text2id = new HashMap<>();
            for (ReadingModel rm : result.getReadings()) {
                text2id.put(rm.getText(), rm.getId());
                if (rm.getId().equals(String.valueOf(rood.getId())))
                    assertEquals("rood", rm.getText());
                else
                    assertTrue(rm.getJoin_prior());
            }

            // Then join it with defaults; the separation should be overridden by join_prior settings
            rbm = new ReadingBoundaryModel();
            response = jerseyTest
                    .target("/reading/" + text2id.get("rood") + "/concatenate/" + text2id.get("of"))
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(rbm));
            assertEquals(Status.OK.getStatusCode(), response.getStatus());

            // Check the reading of C
            TextSequenceModel resp = (TextSequenceModel) new Witness(tradId, "C").getWitnessAsText().getEntity();
            String expC = "when showers sweet with fruit to drought of march has pierced teh roodoftheworld";
            assertEquals(expC, resp.getText());
            tx.success();
        }
    }

    @Test
    public void compressReadingsCheckRankTest() {
        Response jerseyResult = Util.createTraditionFromFileOrString(jerseyTest, "Sapientia", "LR", "1",
                "src/TestFiles/sapientia_2.xml", "stemmaweb");
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        String sapId = Util.getValueFromJson(jerseyResult, "tradId");
        List<SectionModel> testSections = jerseyTest
                .target("/tradition/" + sapId + "/sections")
                .request()
                .get(new GenericType<>() {});
        String sapSectId = testSections.get(0).getId();
        try (Transaction tx = db.beginTx()) {
            // Identify the first five nodes by rank
            Node n1 = db.findNode(Nodes.READING, "text", "Verbum");
            Node n2 = db.findNode(Nodes.READING, "text", "Ista");
            Optional<Node> n3o = db.findNodes(Nodes.READING, "text", "sequencia").stream()
                    .filter(x -> (Long) x.getProperty("rank") == 3L).findFirst();
            assertTrue(n3o.isPresent());
            Node n3 = n3o.get();
            HashSet<Long> fourth = new HashSet<>();
            db.findNodes(Nodes.READING, "rank", 6L).stream()
                    .filter(x -> x.getProperty("section_id").toString().equals(sapSectId))
                    .forEach(x -> fourth.add(x.getId()));
            assertEquals(3, fourth.size());

            ReadingBoundaryModel rbm = new ReadingBoundaryModel();
            Response response = jerseyTest
                    .target("/reading/" + n1.getId() + "/concatenate/" + n2.getId())
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(rbm));
            assertEquals(Status.OK.getStatusCode(), response.getStatus());
            response = jerseyTest
                    .target("/reading/" + n1.getId() + "/concatenate/" + n3.getId())
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(rbm));
            assertEquals(Status.OK.getStatusCode(), response.getStatus());
            GraphModel ourResult = response.readEntity(GraphModel.class);
            assertEquals(1, ourResult.getReadings().size());
            assertEquals(2, ourResult.getSequences().size());
            ourResult.getReadings().forEach(x -> assertEquals("Verbum Ista sequencia", x.getText()));
            ourResult.getSequences().forEach(x -> {
                assertEquals("SEQUENCE", x.getType());
                assertEquals(n1.getId(), Long.parseLong(x.getSource()));
            });

            for (Long nid : fourth) {
                Node n = db.getNodeById(nid);
                ReadingModel rm = jerseyTest.target("/reading/" + n.getId())
                        .request(MediaType.APPLICATION_JSON).get(ReadingModel.class);
                assertEquals(Long.valueOf(4), rm.getRank());
            }

            tx.success();
        }
    }

    @Test
    public void concatenateAllFormsTest() {
        Response jerseyResult = Util.createTraditionFromFileOrString(jerseyTest, "M407", "LR",
                "1", "src/TestFiles/Matthew-407.json", "cxjson");
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        String first_id = null;
        String second_id = null;
        try (Transaction tx = db.beginTx()) {
            Result tomerge = db.execute("MATCH (a:READING {rank:3})-[:SEQUENCE {witnesses:['D']}]->(b:READING {rank:4}) RETURN id(a), id(b)");
            while (tomerge.hasNext()) {
                Map<String,Object> row = tomerge.next();
                first_id = String.valueOf(row.get("id(a)"));
                second_id = String.valueOf(row.get("id(b)"));
            }
            tx.success();
        }
        assertNotNull(first_id);
        assertNotNull(second_id);
        ReadingBoundaryModel rbm = new ReadingBoundaryModel();
        jerseyResult = jerseyTest.target("/reading/" + first_id + "/concatenate/" + second_id)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(rbm));
        assertEquals(Status.OK.getStatusCode(), jerseyResult.getStatus());
        GraphModel ourResult = jerseyResult.readEntity(GraphModel.class);
        ReadingModel compressed = ourResult.getReadings().iterator().next();
        assertNotNull(compressed);
        assertEquals("թվկնութես հայո՛ց", compressed.getText());
        assertEquals("թ<O>վկ</O>նութ<O>ես</O> հայո՛ց", compressed.getDisplay());
        assertEquals("թուականութեանս հայո՛ց", compressed.getNormal_form());
    }

    @Test
    public void nextReadingTest() {
        long withReadId;
        try (Transaction tx = db.beginTx()) {
            withReadId = db.findNodes(Nodes.READING, "text", "with").next().getId();
            tx.success();
        }

        ReadingModel actualResponse = jerseyTest
                .target("/reading/" + withReadId + "/next/A"
                       )
                .request()
                .get(ReadingModel.class);
        assertEquals("his", actualResponse.getText());
    }

    // tests that the next reading is correctly returned according to witness
    @Test
    public void nextReadingWithTwoWitnessesTest() {
        String piercedReadId = readingLookup.get("pierced/14");

        ReadingModel actualResponse = jerseyTest
                .target("/reading/" + piercedReadId +"/next/A")
                .request()
                .get(ReadingModel.class);
        assertEquals("unto me", actualResponse.getText());

        actualResponse = jerseyTest
                .target("/reading/" + piercedReadId + "/next/B")
                .request()
                .get(ReadingModel.class);
        assertEquals("to", actualResponse.getText());
    }

    // the given reading is the last reading in a witness
    // should return error
    @Test
    public void nextReadingLastNodeTest() {
        String readId = readingLookup.get("the root/16");

        Response response = jerseyTest
                .target("/reading/" + readId + "/next/B" )
                .request()
                .get(Response.class);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
                response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        assertEquals("this was the last reading for this witness",
                Util.getValueFromJson(response, "error"));
    }

    @Test
    public void previousReadingTest() {
        String readId = readingLookup.get("with/3");
        ReadingModel actualResponse = jerseyTest
                .target("/reading/" + readId + "/prior/A")
                .request()
                .get(ReadingModel.class);
        assertEquals("april", actualResponse.getText());
    }

    // tests that the previous reading is correctly returned according to
    // witness
    @Test
    public void previousReadingTwoWitnessesTest() {
        String ofId = readingLookup.get("of/11");
        ReadingModel actualResponse = jerseyTest
                .target("/reading/" + ofId + "/prior/A")
                .request()
                .get(ReadingModel.class);
        assertEquals("drought", actualResponse.getText());

        actualResponse = jerseyTest
                .target("/reading/" + ofId + "/prior/B")
                .request()
                .get(ReadingModel.class);
        assertEquals("march", actualResponse.getText());
    }

    // the given reading is the first reading in a witness
    // should return error
    @Test
    public void previousReadingFirstNodeTest() {
        String readId = readingLookup.get("when/1");
        Response actualResponse = jerseyTest
                .target("/reading/" + readId + "/prior/A")
                .request()
                .get(Response.class);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(),
                actualResponse.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, actualResponse.getMediaType());
        assertEquals("this was the first reading for this witness",
                Util.getValueFromJson(actualResponse, "error"));
    }

    @Test
    public void relatedReadingsTest() {
        // Find the "fruit/8" of witness A/C
        String readId = null;
        for (ReadingModel rm : testNumberOfReadingsAndWitnesses(29)) {
            if (rm.getText().equals("fruit") && rm.getWitnesses().contains("A"))
                readId = rm.getId();
        }
        assertNotNull(readId);

        Response jerseyResponse = jerseyTest.target("/reading/" + readId + "/related")
                .queryParam("types", "transposition")
                .queryParam("types", "repetition")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());
        List<ReadingModel> relatedReadings = jerseyResponse.readEntity(new GenericType<>() {});
        assertEquals(1, relatedReadings.size());
        assertEquals("the root", relatedReadings.get(0).getText());
        List<ReadingModel> allRels = jerseyTest
                .target("/reading/" + readId + "/related")
                .request()
                .get(new GenericType<>() {});
        assertEquals(2, allRels.size());
    }

/*  Write this test when we are sure what we need to test!
    @Test
    public void readingWitnessTest() {
        long readId;

    }
*/

    @Test
    public void compressReadingsAcrossTraditionsTest() {
        HashMap<String,String> r1lookup = Util.makeReadingLookup(jerseyTest, tradId);
        String secondTrad = Util.getValueFromJson(Util.createTraditionFromFileOrString(jerseyTest,
                "Next copy", "LR", "1","src/TestFiles/testTradition.xml", "stemmaweb"), "tradId");
        HashMap<String,String> r2lookup = Util.makeReadingLookup(jerseyTest, secondTrad);
        Response r = jerseyTest.target("/reading/" + r1lookup.get("april/2") + "/merge/" + r2lookup.get("april/2"))
                .request().post(Entity.text(null));
        assertEquals(Status.CONFLICT.getStatusCode(), r.getStatus());

    }

    @Test
    public void randomNodeExistsTest() {
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (w:READING {text:'april'}) return w");
            Iterator<Node> nodes = result.columnAs("w");
            assert (nodes.hasNext());
            long rank = 2;
            assertEquals(rank, nodes.next().getProperty("rank"));
            tx.success();
        }
    }

    /**
     * test if the tradition node exists
     */
    @Test
    public void traditionNodeExistsTest() {
        try (Transaction tx = db.beginTx()) {
            ResourceIterator<Node> tradNodesIt = db.findNodes(Nodes.TRADITION, "name", "Tradition");
            assertTrue(tradNodesIt.hasNext());
            tx.success();
        }
    }

    /**
     * test if the tradition end node exists
     */
    @Test
    public void traditionEndNodeExistsTest() {
        try (Transaction tx = db.beginTx()) {
            ResourceIterator<Node> tradNodesIt = db.findNodes(Nodes.READING, "text", "#END#");
            assertTrue(tradNodesIt.hasNext());
            tx.success();
        }
    }

    /*
     * Shut down the jersey server
     *
     * @throws Exception
     */
    @After
    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
    }

}
