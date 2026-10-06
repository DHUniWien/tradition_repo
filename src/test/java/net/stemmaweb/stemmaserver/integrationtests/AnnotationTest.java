package net.stemmaweb.stemmaserver.integrationtests;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.glassfish.jersey.test.JerseyTest;
import org.jspecify.annotations.NonNull;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import junit.framework.TestCase;
import net.stemmaweb.model.AnnotationLabelModel;
import net.stemmaweb.model.AnnotationLinkModel;
import net.stemmaweb.model.AnnotationModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.SectionModel;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.Util;

public class AnnotationTest extends TestCase {
    private GraphDatabaseService db;
    private JerseyTest jerseyTest;
    private String tradId;
    private HashMap<String,String> readingLookup;

    public void setUp() throws Exception {
        super.setUp();
//        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        DatabaseManagementService dbbuilder = new TestDatabaseManagementServiceBuilder().impermanent().build();
    	db = dbbuilder.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME);
    	new GraphDatabaseServiceProvider(dbbuilder, db);
        Util.setupTestDB(db, "1");

        // Create a JerseyTestServer for the necessary REST API calls
        jerseyTest = Util.setupJersey();
        tradId = Util.getValueFromJson(Util.createTraditionFromFileOrString(jerseyTest, "Legend", "LR",
                "1", "src/TestFiles/legendfrag.xml", "stemmaweb"), "tradId");
        readingLookup = Util.makeReadingLookup(jerseyTest, tradId);
    }

    // AnnotationLinkModel.target is a deliberate exception that is NOT migrated to the new
    // Reading id (see the entity-id-system design spec): it stays an elementId string even
    // though the same reading's own "id" property is now numeric. Looks up a reading's
    // current elementId given its numeric id, for use when building an annotation link.
    private String elementIdOf(String readingId) {
        try (Transaction tx = db.beginTx()) {
            return DatabaseService.findNodeOrThrow(tx, Nodes.READING, readingId).getElementId();
        }
    }

    // Same deliberate exception as elementIdOf() above, but for an annotation link that
    // targets another annotation (e.g. a PERSON annotation referenced by a PERSONREF):
    // looks up that annotation's current elementId given its numeric id.
    private String annotationElementIdOf(String annotationId) {
        try (Transaction tx = db.beginTx()) {
            return DatabaseService.findNodeOrThrow(tx, Nodes.ANNOTATION, annotationId).getElementId();
        }
    }

    private AnnotationLabelModel returnTestLabel() {
        AnnotationLabelModel alm = new AnnotationLabelModel();
        alm.setName("TRANSLATION");
        Map<String, String> aprop = new HashMap<>();
        aprop.put("text", "String");
        aprop.put("lang", "String");
        Map<String, String> alink = new HashMap<>();
        alink.put("READING", "BEGIN,END");
        alm.setProperties(aprop);
        alm.setLinks(alink);
        return alm;
    }

    private AnnotationLabelModel addTestLabel() {
        AnnotationLabelModel alm = returnTestLabel();
        AnnotationLabelModel result;
        try (Response response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(alm))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
            result = response.readEntity(AnnotationLabelModel.class);
        }
        return result;
    }

    private AnnotationModel returnTestAnnotation() {
        AnnotationModel am = new AnnotationModel();
        am.setLabel("TRANSLATION");
        Map<String, Object> props = new HashMap<>();
        props.put("text", "In Sweden the venerable pontifex St. Henry originating from England");
        props.put("lang", "EN");
        am.setProperties(props);
        AnnotationLinkModel start = new AnnotationLinkModel();
        start.setTarget(elementIdOf(readingLookup.get("in/1")));
        start.setType("BEGIN");
        start.setFollow("SEQUENCE/witness/A");
        AnnotationLinkModel end = new AnnotationLinkModel();
        end.setTarget(elementIdOf(readingLookup.get("oriundus/9")));
        end.setType("END");
        am.addLink(start);
        am.addLink(end);
        return am;
    }

    private AnnotationModel addTestAnnotation() {
        AnnotationModel am = returnTestAnnotation();
        AnnotationModel result;
        try (Response response = jerseyTest
                .target("/tradition/" + tradId + "/annotation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(am))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
            result = response.readEntity(AnnotationModel.class);
        }
        return result;
    }

    public void testLookupBogusLabel() {
        // Look up a nonexistent label
        Response response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + "NOTHERE")
                .request()
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

        // Look up a label that belongs to an internal object
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + "SECTION")
                .request()
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    public void testCreateReservedLabel() {
        AnnotationLabelModel alm = new AnnotationLabelModel();
        alm.setName("WITNESS");
        Map<String, String> aprop = new HashMap<>();
        aprop.put("sigil", "String");
        aprop.put("lang", "String");
        Map<String, String> alink = new HashMap<>();
        alink.put("WITNESS", "BEGIN,END");
        alm.setProperties(aprop);
        alm.setLinks(alink);
        try (Response response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(alm))) {
            assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
        }

        alm.setName("USER");
        try (Response response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(alm))) {
            assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
        }
    }

    public void testCreateAnnotationLabel() {
        // Check that we can set an annotation label
        AnnotationLabelModel alm = returnTestLabel();
        AnnotationLabelModel result = addTestLabel();
        assertEquals(alm.getName(), result.getName());
        assertEquals(alm.getProperties(), result.getProperties());
        assertEquals(alm.getLinks(), result.getLinks());
        for (String k : result.getProperties().keySet()) assertEquals(alm.getProperties().get(k), result.getProperties().get(k));

        // Check that we can retrieve the label
        Response response2 = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + result.getName())
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), response2.getStatus());
        result = response2.readEntity(AnnotationLabelModel.class);
        assertEquals(alm.getName(), result.getName());
        assertEquals(alm.getProperties(), result.getProperties());
        assertEquals(alm.getLinks(), result.getLinks());
    }

    public void testChangeAnnotationLabel() {
        AnnotationLabelModel alm = addTestLabel();
        Map<String, String> newProps = new HashMap<>();
        newProps.put("english_text", "String");
        alm.setProperties(newProps);
        try (Response response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(alm))) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            alm = response.readEntity(AnnotationLabelModel.class);
        }
        String origName = alm.getName();
        assertEquals("TRANSLATION", origName);
        assertEquals(newProps, alm.getProperties());
        // The links should not have changed
        assertEquals(1, alm.getLinks().size());

        // Try to change the name to something disallowed
        alm.setName("USER");
        try (Response response2 = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(alm))) {
            assertEquals(Response.Status.CONFLICT.getStatusCode(), response2.getStatus());
        }

        alm.setName("READING");
        try (Response response3 = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(alm))) {
            assertEquals(Response.Status.CONFLICT.getStatusCode(), response3.getStatus());
        }

        // Add a second annotation label
        AnnotationLabelModel newalm = new AnnotationLabelModel();
        newalm.setName("MARKED");
        Map<String,String> newLinks = new HashMap<>();
        newLinks.put("SECTION", "HAS_MARK");
        newalm.setLinks(newLinks);
        try (Response response4 = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + newalm.getName())
                .request(MediaType.APPLICATION_JSON_TYPE)
                .put(Entity.json(newalm))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), response4.getStatus());
        }

        // Try to change the old annotation to match this name
        alm.setName(newalm.getName());
        try (Response response5 = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + origName)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(alm))) {
            assertEquals(Response.Status.CONFLICT.getStatusCode(), response5.getStatus());
        }

        // Now change the name to something that isn't a problem
        alm.setName("ENGLISHING");
        try (Response response6 = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + origName)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(alm))) {
            assertEquals(Response.Status.OK.getStatusCode(), response6.getStatus());
        }
    }

    public void testAddAnnotation() {
        // Label specification and addition
        addTestLabel();

        // Now we use the label
        AnnotationModel am = addTestAnnotation();

        // Check that the graph looks right
        try (Transaction tx = db.beginTx()) {
            Node annoNode = DatabaseService.findNodeOrThrow(tx, Nodes.ANNOTATION, am.getId());
            assertTrue(annoNode.hasLabel(Label.label("TRANSLATION")));
            assertEquals(am.getProperties().get("text"), annoNode.getProperty("text"));
            assertEquals(am.getProperties().get("lang"), annoNode.getProperty("lang"));
            HashMap<String, Relationship> links = new HashMap<>();
            links.put("BEGIN", annoNode.getSingleRelationship(RelationshipType.withName("BEGIN"), Direction.OUTGOING));
            links.put("END", annoNode.getSingleRelationship(RelationshipType.withName("END"), Direction.OUTGOING));
            for (AnnotationLinkModel alm : am.getLinks()) {
                Relationship link = links.get(alm.getType());
                assertEquals(link.getType().name(), alm.getType());
                assertEquals(link.getEndNode().getElementId(), alm.getTarget());
                if (alm.getType().equals("START"))
                    assertEquals(alm.getFollow(), link.getProperty("follow").toString());
            }
            Relationship tlink = annoNode.getSingleRelationship(
                    RelationshipType.withName("HAS_ANNOTATION"), Direction.INCOMING);
            assertEquals(tradId, tlink.getStartNode().getProperty("id"));
        }
    }

    public void testAnnotationCreatedViaRestEndpointGetsNumericId() {
        // POST /tradition/{id}/annotation (the real REST endpoint, i.e. Tradition.addAnnotation,
        // NOT the GraphML reimport path) should produce an annotation whose id is a plain
        // numeric string, not a Neo4j elementId.
        addTestLabel();
        AnnotationModel am = addTestAnnotation();
        Long.parseLong(am.getId());
    }

    public void testAnnotationRetainsIdAfterUpdate() {
        addTestLabel();
        AnnotationModel am = addTestAnnotation();
        String originalId = am.getId();

        // PUT an update to the annotation, which exercises updateAnnotation's label-reset loop
        AnnotationModel update = returnTestAnnotation();
        update.addProperty("text", "An updated translation text");
        AnnotationModel updated;
        try (Response response = jerseyTest
                .target("/tradition/" + tradId + "/annotation/" + originalId)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(update))) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            updated = response.readEntity(AnnotationModel.class);
        }
        assertEquals(originalId, updated.getId());
        assertEquals("An updated translation text", updated.getProperties().get("text"));

        // The node should still satisfy the ANNOTATION uniqueness constraint -- a fresh GET
        // by the same id should still work, and a second update should too.
        try (Response response2 = jerseyTest
                .target("/tradition/" + tradId + "/annotation/" + originalId)
                .request()
                .get()) {
            assertEquals(Response.Status.OK.getStatusCode(), response2.getStatus());
            AnnotationModel refetched = response2.readEntity(AnnotationModel.class);
            assertEquals(originalId, refetched.getId());
        }

        update.addProperty("text", "Yet another translation text");
        try (Response response3 = jerseyTest
                .target("/tradition/" + tradId + "/annotation/" + originalId)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(update))) {
            assertEquals(Response.Status.OK.getStatusCode(), response3.getStatus());
            updated = response3.readEntity(AnnotationModel.class);
        }
        assertEquals(originalId, updated.getId());
    }

    public void testGetAnnotationWithMalformedIdMatchesCurrentBehavior() {
        // GET .../annotation/not-a-number -- a malformed id is not caught anywhere along this
        // path (annotationNotFound only catches NotFoundException, and getAnnotation() has no
        // catch clause at all), so it propagates as an unmapped exception, which Jersey turns
        // into a 500. This matches the pre-entity-id behavior for a malformed elementId at the
        // same endpoint (also an uncaught IllegalArgumentException -> 500).
        try (Response response = jerseyTest
                .target("/tradition/" + tradId + "/annotation/not-a-number")
                .request()
                .get()) {
            assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        }
    }

    public void testDeleteAnnotation() {
        addTestLabel();
        addTestAnnotation();

        List<AnnotationModel> existing = jerseyTest
                .target("/tradition/" + tradId + "/annotations")
                .request()
                .get(new GenericType<>() {});
        assertEquals(1, existing.size());

        try (Response response = jerseyTest
                .target("/tradition/" + tradId + "/annotation/" + existing.getFirst().getId())
                .request(MediaType.APPLICATION_JSON)
                .delete()) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        }

        existing = jerseyTest
                .target("/tradition/" + tradId + "/annotations")
                .request()
                .get(new GenericType<>() {});
        assertEquals(0, existing.size());
    }

    public void testDeleteAnnotationLabel() {
        // Label specification
        AnnotationLabelModel alm = returnTestLabel();

        // Try to delete a nonexistent label
        try (Response response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .request()
                .delete()) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        }

        // Make it exist
        addTestLabel();

        // Add an annotation so that we can test deletion conflict
        AnnotationModel am = addTestAnnotation();

        // Try to delete a label that is in use
        try (Response response2 = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .request()
                .delete()) {
            assertEquals(Response.Status.CONFLICT.getStatusCode(), response2.getStatus());
        }

        // Delete the annotation in question
        try (Response response3 = jerseyTest
                .target("/tradition/" + tradId + "/annotation/" + am.getId())
                .request()
                .delete()) {
            assertEquals(Response.Status.OK.getStatusCode(), response3.getStatus());
        }

        // Now delete the label for real
        try (Response response4 = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .request(MediaType.APPLICATION_JSON)
                .delete()) {
            assertEquals(Response.Status.OK.getStatusCode(), response4.getStatus());
        }

        // Check that the label is really gone
        List<AnnotationLabelModel> labels = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabels")
                .request(MediaType.APPLICATION_JSON)
                .get(new GenericType<>() {});
        assertEquals(0, labels.size());
    }

    public void testAddDeleteAnnotationLink() {
        addTestLabel();
        AnnotationModel am = addTestAnnotation();

        AnnotationLinkModel alm = new AnnotationLinkModel();
        alm.setTarget(elementIdOf(readingLookup.get("venerabilis/3")));
        alm.setType("BEGIN");
        try (Response response = jerseyTest
                .target("/tradition/" + tradId + "/annotation/" + am.getId() + "/link")
                .request()
                .post(Entity.json(alm))) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            // There should now be two BEGIN links
            am = response.readEntity(AnnotationModel.class);
        }
        assertEquals(3, am.getLinks().size());
        assertEquals(2, am.getLinks().stream().filter(x -> x.getType().equals("BEGIN")).count());


        // Try it again - we should get a not-modified
        try (Response response2 = jerseyTest
                .target("/tradition/" + tradId + "/annotation/" + am.getId() + "/link")
                .request()
                .post(Entity.json(alm))) {
            assertEquals(Response.Status.NOT_MODIFIED.getStatusCode(), response2.getStatus());
        }

        // Now try deleting the link
        /*
         * skipping the test since I don't find any solution for delete with parameters
         * 
         * jerseyTest.client().property(ClientProperties.
         * SUPPRESS_HTTP_COMPLIANCE_VALIDATION, true);
         * 
         * response = jerseyTest .target("/tradition/" + tradId + "/annotation/" +
         * am.getId() + "/link") .request() .method("DELETE", Entity.json(alm));
         * 
         * assertEquals(Response.Status.OK.getStatusCode(), response.getStatus()); am =
         * response.readEntity(AnnotationModel.class); // The link shouldn't be there
         * anymore assertEquals(2, am.getLinks().size()); assertEquals(1,
         * am.getLinks().stream().filter(x -> x.getType().equals("BEGIN")).count());
         */    }

    public void testAddComplexAnnotation() {
        // Add our second section
        Util.addSectionToTradition(jerseyTest, tradId, "src/TestFiles/lf2.xml",
                "stemmaweb", "sect2");
        // Regenerate our reading lookup
        readingLookup = Util.makeReadingLookup(jerseyTest, tradId);

        // Make a PERSONREF annotation label
        AnnotationLabelModel pref = new AnnotationLabelModel();
        pref.setName("PERSONREF");
        pref.addLink("READING", "BEGIN,END");
        try (Response response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + pref.getName())
                .request()
                .put(Entity.json(pref))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        }

        // Make a PERSON annotation label
        AnnotationLabelModel person = new AnnotationLabelModel();
        person.setName("PERSON");
        person.addLink("PERSONREF", "REFERENCED");
        person.addProperty("href", "String");
        try (Response response2 = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + person.getName())
                .request()
                .put(Entity.json(person))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), response2.getStatus());
        }

        // Check that we can retrieve all the labels we made
        Response response3 = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabels")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), response3.getStatus());
        List<AnnotationLabelModel> allLabels = response3.readEntity(new GenericType<>() {});
        assertEquals(2, allLabels.size());
        assertTrue(allLabels.stream().anyMatch(x -> x.getName().equals("PERSON")));
        assertTrue(allLabels.stream().anyMatch(x -> x.getName().equals("PERSONREF")));

        // Now use them
        AnnotationModel ref1 = new AnnotationModel();
        ref1.setLabel("PERSONREF");
        AnnotationLinkModel prb = new AnnotationLinkModel();
        prb.setType("BEGIN");
        prb.setTarget(elementIdOf(readingLookup.get("pontifex/4")));
        AnnotationLinkModel pre = new AnnotationLinkModel();
        pre.setType("END");
        pre.setTarget(elementIdOf(readingLookup.get("Henricus/6")));
        ref1.addLink(prb);
        ref1.addLink(pre);
        try (Response response4 = jerseyTest
                .target("/tradition/" + tradId + "/annotation/")
                .request()
                .post(Entity.json(ref1))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), response4.getStatus());
            ref1 = response4.readEntity(AnnotationModel.class);
        }

        // Now try to link the PERSONREF to the right PERSON
        AnnotationModel henry = new AnnotationModel();
        henry.setLabel("PERSON");
        henry.setPrimary(true);
        henry.addProperty("href", "https://en.wikipedia.org/Saint_Henry");
        prb = new AnnotationLinkModel();
        prb.setTarget(annotationElementIdOf(ref1.getId()));
        prb.setType("REFERENCED");
        henry.addLink(prb);
        try (Response response5 = jerseyTest
                .target("/tradition/" + tradId + "/annotation/")
                .request()
                .post(Entity.json(henry))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), response5.getStatus());
            henry = response5.readEntity(AnnotationModel.class);
        }

        // Now add another reference so we can link it to the same person
        AnnotationModel ref2 = new AnnotationModel();
        ref2.setLabel("PERSONREF");
        prb = new AnnotationLinkModel();
        prb.setType("BEGIN");
        prb.setTarget(elementIdOf(readingLookup.get("luminaribus/4")));
        pre = new AnnotationLinkModel();
        pre.setType("END");
        pre.setTarget(elementIdOf(readingLookup.get("luminaribus/4")));
        ref2.addLink(prb);
        ref2.addLink(pre);
        try (Response response6 = jerseyTest
                .target("/tradition/" + tradId + "/annotation/")
                .request()
                .post(Entity.json(ref2))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), response6.getStatus());
            ref2 = response6.readEntity(AnnotationModel.class);
        }

        // Add the link
        prb.setTarget(annotationElementIdOf(ref2.getId()));
        prb.setType("REFERENCED");
        try (Response response7 = jerseyTest
                .target("/tradition/" + tradId + "/annotation/" + henry.getId() + "/link")
                .request()
                .post(Entity.json(prb))) {
            assertEquals(Response.Status.OK.getStatusCode(), response7.getStatus());
        }

        // Count up our annotations, testing annotation filtering along the way
        WebTarget baseQuery = jerseyTest.target("/tradition/" + tradId + "/annotations");
        Response qr = baseQuery.request().get();
        assertEquals(Response.Status.OK.getStatusCode(), qr.getStatus());
        List<AnnotationModel> anns = qr.readEntity(new GenericType<>() {});
        assertEquals(3, anns.size());
        qr = baseQuery.queryParam("label", "PERSONREF").request().get();
        assertEquals(Response.Status.OK.getStatusCode(), qr.getStatus());
        anns = qr.readEntity(new GenericType<>() {});
        assertEquals(2, anns.size());
        qr = baseQuery.queryParam("label", "PERSON").request().get();
        assertEquals(Response.Status.OK.getStatusCode(), qr.getStatus());
        anns = qr.readEntity(new GenericType<>() {});
        assertEquals(1, anns.size());

        // See if the structure makes sense
        for (AnnotationModel am : anns) {
            if (am.getLabel().equals("PERSON")) {
                assertEquals(2, am.getLinks().size());
                HashMap<String,Boolean> found = new HashMap<>();
                found.put(annotationElementIdOf(ref1.getId()), false);
                found.put(annotationElementIdOf(ref2.getId()), false);
                for (AnnotationLinkModel alm : am.getLinks()) {
                    assertEquals("REFERENCED", alm.getType());
                    found.put(alm.getTarget(), true);
                }
                assertEquals(2, found.size());
                assertFalse(found.containsValue(false));
            } else {
                for (AnnotationLinkModel alm : am.getLinks()) {
                    ReadingModel target = jerseyTest
                            .target("/reading/" + alm.getTarget()).request().get(ReadingModel.class);
                    String rdgtext = target.getText();
                    assertTrue(rdgtext.equals("pontifex")
                            || rdgtext.equals("Henricus") || rdgtext.equals("luminaribus"));
                }
            }
        }

        // Now delete each of the references and make sure the PERSON didn't get deleted,
        // since it is a primary object
        List<AnnotationModel> deleted;
        try (Response response8 = jerseyTest.target("/tradition/" + tradId + "/annotation/" + ref1.getId())
                .request()
                .delete()) {
            assertEquals(Response.Status.OK.getStatusCode(), response8.getStatus());
            deleted = response8.readEntity(new GenericType<>() {
            });
        }
        assertEquals(1, deleted.size());
        assertEquals(ref1.getId(), deleted.getFirst().getId());

        anns = jerseyTest.target("/tradition/" + tradId + "/annotations")
                .request()
                .get(new GenericType<>() {});
        assertEquals(2, anns.size());

        try (Response response9 = jerseyTest
                .target("/tradition/" + tradId + "/annotation/" + ref2.getId())
                .request()
                .delete()) {
            assertEquals(Response.Status.OK.getStatusCode(), response9.getStatus());
            deleted = response9.readEntity(new GenericType<>() {
            });
        }
        assertEquals(1, deleted.size());
        assertEquals(ref2.getId(), deleted.getFirst().getId());

        anns = jerseyTest
                .target("/tradition/" + tradId + "/annotations")
                .request()
                .get(new GenericType<>() {});
        assertEquals(1, anns.size());

        // Now delete the PERSON explicitly, which should work
        try (Response response10 = jerseyTest
                .target("/tradition/" + tradId + "/annotation/" + henry.getId())
                .request()
                .delete()) {
            assertEquals(Response.Status.OK.getStatusCode(), response10.getStatus());
            deleted = response10.readEntity(new GenericType<>() {
            });
        }
        assertEquals(1, deleted.size());
        assertEquals(henry.getId(), deleted.getFirst().getId());

        anns = jerseyTest
                .target("/tradition/" + tradId + "/annotations")
                .request()
                .get(new GenericType<>() {});
        assertEquals(0, anns.size());
    }

    public void testAnnotationTypes() {
        // ArrayList<String> allowedValues = new ArrayList<>(Arrays.asList("Boolean", "Long", "Double",
        //                        "Character", "String", "LocalDate", "OffsetTime", "LocalTime", "ZonedDateTime",
        //                        "LocalDateTime", "TemporalAmount"));
        // We've already tested strings
        HashMap<String,Class<?>> nameToType = new HashMap<>();
        nameToType.put("SOMEBOOL", Boolean.class);
        nameToType.put("SOMELONG", Long.class);
        nameToType.put("SOMEDOUBLE", Double.class);
        nameToType.put("SOMECHAR", Character.class);
        nameToType.put("SOMELDATE", LocalDate.class);
        nameToType.put("SOMEOFFSET", OffsetTime.class);
        nameToType.put("SOMELTIME", LocalTime.class);
        nameToType.put("SOMEZDTIME", ZonedDateTime.class);
        nameToType.put("SOMELDTIME", LocalDateTime.class);
        nameToType.put("SOMEDURATION", Duration.class);
        nameToType.put("SOMEPERIOD", Period.class);

        HashMap<String,AnnotationLabelModel> annsToTest = new HashMap<>();
        for (String k : nameToType.keySet()) {
            AnnotationLabelModel alm = new AnnotationLabelModel();
            alm.setName(k);
            Map<String, String> aprop = new HashMap<>();
            String[] classNameParts = nameToType.get(k).getName().split("\\.");
            aprop.put("value", classNameParts[classNameParts.length - 1]);
            Map<String, String> alink = new HashMap<>();
            alink.put("READING", "ATTACHED");
            alm.setProperties(aprop);
            alm.setLinks(alink);
            annsToTest.put(k, alm);
        }

        // Try making each of these annotation labels
        for (AnnotationLabelModel alm : annsToTest.values()) {
            try (Response response = jerseyTest
                    .target("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                    .request(MediaType.APPLICATION_JSON)
                    .put(Entity.json(alm))) {
                assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
            }
        }

        // Now try using each of these annotations
        HashMap<String, Object> nameToValue = createNameToValue();

        for (String k : nameToType.keySet()) {
            AnnotationModel am = new AnnotationModel();
            am.setLabel(k);
            Map<String, Object> props = new HashMap<>();
            props.put("value", nameToValue.get(k));
            am.setProperties(props);
            AnnotationLinkModel start = new AnnotationLinkModel();
            start.setTarget(elementIdOf(readingLookup.get("in/1")));
            start.setType("ATTACHED");
            am.addLink(start);

            // Try making each of these annotations
            try (Response response = jerseyTest
                    .target("/tradition/" + tradId + "/annotation")
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(am))) {
                assertEquals("creation of " + k + " annotation", Response.Status.CREATED.getStatusCode(), response.getStatus());
            }
        }

        // Check that they come back out
        Response response = jerseyTest.target("/tradition/" + tradId + "/annotations")
                .request().get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<AnnotationModel> ourAnnotations = response.readEntity(new GenericType<>() {});
        assertEquals(nameToType.size(), ourAnnotations.size());
    }

    private static @NonNull HashMap<String, Object> createNameToValue() {
        HashMap<String,Object> nameToValue = new HashMap<>();
        nameToValue.put("SOMEBOOL", true);
        nameToValue.put("SOMELONG", "1");
        nameToValue.put("SOMEDOUBLE", "1.0");
        nameToValue.put("SOMECHAR", "a");
        nameToValue.put("SOMELDATE", "2007-12-03");
        nameToValue.put("SOMEOFFSET", "10:15:30+01:00");
        nameToValue.put("SOMELTIME", "10:15");
        nameToValue.put("SOMEZDTIME", "2007-12-03T10:15:30+01:00");
        nameToValue.put("SOMELDTIME", "2007-12-03T10:15:30");
        nameToValue.put("SOMEDURATION", Duration.ofHours(3).toString());
        nameToValue.put("SOMEPERIOD", Period.ofDays(3).toString());
        return nameToValue;
    }

    public void testExportWithAnnotations() {
        addTestLabel();
        addTestAnnotation();
        Response response = jerseyTest.target("/tradition/" + tradId + "/graphml")
                .request("application/zip").get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        // Save the result into a temp file so that we can reimport it for the second half of this test
        String tradXmlOutput = "";
        String graphMLPath = "";
        try {
            graphMLPath = Util.saveGraphMLTempfile(response);
            tradXmlOutput = Util.getConcatenatedGraphML(graphMLPath);
        } catch (Exception e) {
            fail();
        }
        // Unzip the result and check that the annotation is represented somewhere. Do this first
        // by concatenating all the XML int one big string

        String translation = returnTestAnnotation().getProperties().get("text").toString();
        assertNotNull(tradXmlOutput);
        assertTrue(tradXmlOutput.contains("[ANNOTATIONLABEL]"));
        assertTrue(tradXmlOutput.contains("[LINKS]"));
        assertTrue(tradXmlOutput.contains("[TRANSLATION]"));
        assertTrue(tradXmlOutput.contains(translation));

        // ...also for the individual section.
        List<SectionModel> sects = jerseyTest.target("/tradition/" + tradId + "/sections")
                .request().get(new GenericType<>() {});
        String sectId = sects.getFirst().getId();
        response = jerseyTest.target("/tradition/" + tradId + "/section/" + sectId + "/graphml")
                .request().get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        String sectXmlOutput = "";
        String sectMLPath = "";
        try {
            sectMLPath = Util.saveGraphMLTempfile(response);
            sectXmlOutput = Util.getConcatenatedGraphML(graphMLPath);
        } catch (Exception e) {
            fail();
        }
        assertNotNull(sectXmlOutput);
        assertTrue(sectXmlOutput.contains("[ANNOTATIONLABEL]"));
        assertTrue(sectXmlOutput.contains("[LINKS]"));
        assertTrue(sectXmlOutput.contains("[TRANSLATION]"));
        assertTrue(sectXmlOutput.contains(translation));

        // Check that it gets re-imported correctly
        response = Util.createTraditionFromFileOrString(jerseyTest, "reimported", "LR", "1",
                graphMLPath, "graphml");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        String newTradId = Util.getValueFromJson(response, "tradId");
        response = jerseyTest.target("/tradition/" + newTradId + "/annotations")
                .request().get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<AnnotationModel> am = response.readEntity(new GenericType<>() {});
        assertEquals(1, am.size());
        assertEquals("TRANSLATION", am.getFirst().getLabel());
        assertTrue(am.getFirst().getProperties().containsKey("text"));
        assertEquals(translation, am.getFirst().getProperties().get("text"));

        // Check that the individual section can be added to the existing tradition
        response = Util.addSectionToTradition(jerseyTest, newTradId, sectMLPath, "graphml", "duplicate");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        response = jerseyTest.target("/tradition/" + newTradId + "/annotations").request().get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        am = response.readEntity(new GenericType<>() {});
        // There should be two of them now
        assertEquals(2, am.size());
        assertTrue(am.stream().allMatch(x -> x.getLabel().equals("TRANSLATION")));
    }

    public void tearDown() throws Exception {
//        db.shutdown();
    	GraphDatabaseServiceProvider.shutdown();
        jerseyTest.tearDown();
        super.tearDown();
    }
}
