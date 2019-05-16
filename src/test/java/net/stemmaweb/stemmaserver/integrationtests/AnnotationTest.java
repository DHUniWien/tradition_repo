package net.stemmaweb.stemmaserver.integrationtests;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.test.framework.JerseyTest;
import junit.framework.TestCase;
import net.stemmaweb.model.AnnotationLabelModel;
import net.stemmaweb.model.AnnotationLinkModel;
import net.stemmaweb.model.AnnotationModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.Util;
import org.neo4j.graphdb.*;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnnotationTest extends TestCase {
    private GraphDatabaseService db;
    private JerseyTest jerseyTest;
    private String tradId;
    private HashMap<String,String> readingLookup;

    public void setUp() throws Exception {
        super.setUp();
        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        Util.setupTestDB(db, "1");

        // Create a JerseyTestServer for the necessary REST API calls
        jerseyTest = Util.setupJersey();
        tradId = Util.getValueFromJson(Util.createTraditionFromFileOrString(jerseyTest, "Legend", "LR",
                "1", "src/TestFiles/legendfrag.xml", "stemmaweb"), "tradId");
        readingLookup = Util.makeReadingLookup(jerseyTest, tradId);
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

    private ClientResponse addTestLabel() {
        AnnotationLabelModel alm = returnTestLabel();
        ClientResponse response = jerseyTest.resource().path("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, alm);
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        return response;
    }

    private AnnotationModel returnTestAnnotation() {
        AnnotationModel am = new AnnotationModel();
        am.setLabel("TRANSLATION");
        Map<String, Object> props = new HashMap<>();
        props.put("text", "In Sweden the venerable pontifex St. Henry originating from England");
        props.put("lang", "EN");
        am.setProperties(props);
        AnnotationLinkModel start = new AnnotationLinkModel();
        start.setTarget(Long.valueOf(readingLookup.get("in/1")));
        start.setType("BEGIN");
        start.setFollow("SEQUENCE/witness/A");
        AnnotationLinkModel end = new AnnotationLinkModel();
        end.setTarget(Long.valueOf(readingLookup.get("oriundus/9")));
        end.setType("END");
        am.addLink(start);
        am.addLink(end);
        return am;
    }

    private ClientResponse addTestAnnotation() {
        AnnotationModel am = returnTestAnnotation();
        ClientResponse response = jerseyTest.resource().path("/tradition/" + tradId + "/annotation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, am);
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        return response;
    }

    public void testLookupBogusLabel() {
        // Look up a nonexistent label
        ClientResponse response = jerseyTest.resource().path("/tradition/" + tradId + "/annotationlabel/" + "NOTHERE")
                .get(ClientResponse.class);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

        // Look up a label that belongs to a primary object
        response = jerseyTest.resource().path("/tradition/" + tradId + "/annotationlabel/" + "SECTION")
                .get(ClientResponse.class);
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
        ClientResponse response = jerseyTest.resource().path("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, alm);
        assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());

        alm.setName("USER");
        response = jerseyTest.resource().path("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, alm);
        assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
    }

    public void testCreateAnnotationLabel() {
        // Check that we can set an annotation label
        AnnotationLabelModel alm = returnTestLabel();
        ClientResponse response = addTestLabel();
        AnnotationLabelModel result = response.getEntity(AnnotationLabelModel.class);
        assertEquals(alm.getName(), result.getName());
        assertEquals(alm.getProperties(), result.getProperties());
        assertEquals(alm.getLinks(), result.getLinks());
        for (String k : result.getProperties().keySet()) assertEquals(alm.getProperties().get(k), result.getProperties().get(k));

        // Check that we can retrieve the label
        response = jerseyTest.resource().path("/tradition/" + tradId + "/annotationlabel/" + result.getName())
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        result = response.getEntity(AnnotationLabelModel.class);
        assertEquals(alm.getName(), result.getName());
        assertEquals(alm.getProperties(), result.getProperties());
        assertEquals(alm.getLinks(), result.getLinks());
    }

    public void testChangeAnnotationLabel() {
        AnnotationLabelModel alm = addTestLabel().getEntity(AnnotationLabelModel.class);
        Map<String, String> newProps = new HashMap<>();
        newProps.put("english_text", "String");
        alm.setProperties(newProps);
        ClientResponse response = jerseyTest.resource()
                .path("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, alm);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        alm = response.getEntity(AnnotationLabelModel.class);
        String origName = alm.getName();
        assertEquals("TRANSLATION", origName);
        assertEquals(newProps, alm.getProperties());
        // The links should not have changed
        assertEquals(1, alm.getLinks().size());

        // Try to change the name to something disallowed
        alm.setName("USER");
        response = jerseyTest.resource()
                .path("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, alm);
        assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());

        alm.setName("READING");
        response = jerseyTest.resource()
                .path("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, alm);
        assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());

        // Add a second annotation label
        AnnotationLabelModel newalm = new AnnotationLabelModel();
        newalm.setName("MARKED");
        Map<String,String> newLinks = new HashMap<>();
        newLinks.put("SECTION", "HAS_MARK");
        newalm.setLinks(newLinks);
        response = jerseyTest.resource().path("/tradition/" + tradId + "/annotationlabel/" + newalm.getName())
                .type(MediaType.APPLICATION_JSON_TYPE)
                .put(ClientResponse.class, newalm);
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

        // Try to change the old annotation to match this name
        alm.setName(newalm.getName());
        response = jerseyTest.resource()
                .path("/tradition/" + tradId + "/annotationlabel/" + origName)
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, alm);
        assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());

        // Now change the name to something that isn't a problem
        alm.setName("ENGLISHING");
        response = jerseyTest.resource()
                .path("/tradition/" + tradId + "/annotationlabel/" + origName)
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, alm);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    }

    public void testAddAnnotation() {
        // Label specification and addition
        addTestLabel();

        // Now we use the label
        ClientResponse response = addTestAnnotation();
        AnnotationModel am = response.getEntity(AnnotationModel.class);

        // Check that the graph looks right
        try (Transaction tx = db.beginTx()) {
            Node annoNode = db.getNodeById(Long.valueOf(am.getId()));
            assertTrue(annoNode.hasLabel(Label.label("TRANSLATION")));
            assertEquals(am.getProperties().get("text"), annoNode.getProperty("text"));
            assertEquals(am.getProperties().get("lang"), annoNode.getProperty("lang"));
            HashMap<String, Relationship> links = new HashMap<>();
            links.put("BEGIN", annoNode.getSingleRelationship(RelationshipType.withName("BEGIN"), Direction.OUTGOING));
            links.put("END", annoNode.getSingleRelationship(RelationshipType.withName("END"), Direction.OUTGOING));
            for (AnnotationLinkModel alm : am.getLinks()) {
                Relationship link = links.get(alm.getType());
                assertEquals(link.getType().name(), alm.getType());
                assertEquals(Long.valueOf(link.getEndNode().getId()), alm.getTarget());
                if (alm.getType().equals("START"))
                    assertEquals(alm.getFollow(), link.getProperty("follow").toString());
            }
            Relationship tlink = annoNode.getSingleRelationship(
                    RelationshipType.withName("HAS_ANNOTATION"), Direction.INCOMING);
            assertEquals(tradId, tlink.getStartNode().getProperty("id"));

            tx.success();
        }
    }

    public void testDeleteAnnotation() {
        addTestLabel();
        addTestAnnotation();

        List<AnnotationModel> existing = jerseyTest.resource().path("/tradition/" + tradId + "/annotations")
                .get(new GenericType<List<AnnotationModel>>() {});
        assertEquals(1, existing.size());

        ClientResponse response = jerseyTest.resource().path("/tradition/" + tradId + "/annotation/" + existing.get(0).getId())
                .type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        existing = jerseyTest.resource().path("/tradition/" + tradId + "/annotations")
                .get(new GenericType<List<AnnotationModel>>() {});
        assertEquals(0, existing.size());
    }

    public void testDeleteAnnotationLabel() {
        // Label specification
        AnnotationLabelModel alm = returnTestLabel();

        // Try to delete a nonexistent label
        ClientResponse response = jerseyTest.resource().path("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

        // Make it exist
        addTestLabel();

        // Add an annotation so that we can test deletion conflict
        AnnotationModel am = addTestAnnotation().getEntity(AnnotationModel.class);

        // Try to delete a label that is in use
        response = jerseyTest.resource().path("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class);
        assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());

        // Delete the annotation in question
        response = jerseyTest.resource().path("/tradition/" + tradId + "/annotation/" + am.getId())
                .type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        // Now delete the label for real
        response = jerseyTest.resource().path("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    }

    public void testAddDeleteAnnotationLink() {
        addTestLabel();
        AnnotationModel am = addTestAnnotation().getEntity(AnnotationModel.class);

        AnnotationLinkModel alm = new AnnotationLinkModel();
        alm.setTarget(Long.valueOf(readingLookup.get("venerabilis/3")));
        alm.setType("BEGIN");
        ClientResponse response = jerseyTest.resource()
                .path("/tradition/" + tradId + "/annotation/" + am.getId() + "/link")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, alm);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        // Try it again - we should get a not-modified
        response = jerseyTest.resource()
                .path("/tradition/" + tradId + "/annotation/" + am.getId() + "/link")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, alm);
        assertEquals(Response.Status.NOT_MODIFIED.getStatusCode(), response.getStatus());

        // Now try deleting the link
        response = jerseyTest.resource()
                .path("/tradition/" + tradId + "/annotation/" + am.getId() + "/link")
                .type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class, alm);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    }

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
        ClientResponse response = jerseyTest.resource()
                .path("/tradition/" + tradId + "/annotationlabel/" + pref.getName())
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, pref);
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

        // Make a PERSON annotation label
        AnnotationLabelModel person = new AnnotationLabelModel();
        person.setName("PERSON");
        person.addLink("PERSONREF", "REFERENCED");
        person.addProperty("href", "String");
        response = jerseyTest.resource()
                .path("/tradition/" + tradId + "/annotationlabel/" + person.getName())
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, person);
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

        // Now use them
        AnnotationModel ref1 = new AnnotationModel();
        ref1.setLabel("PERSONREF");
        AnnotationLinkModel prb = new AnnotationLinkModel();
        prb.setType("BEGIN");
        prb.setTarget(Long.valueOf(readingLookup.get("pontifex/4")));
        AnnotationLinkModel pre = new AnnotationLinkModel();
        pre.setType("END");
        pre.setTarget(Long.valueOf(readingLookup.get("Henricus/6")));
        ref1.addLink(prb);
        ref1.addLink(pre);
        response = jerseyTest.resource()
                .path("/tradition/" + tradId + "/annotation/")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, ref1);
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        ref1 = response.getEntity(AnnotationModel.class);

        // Now try to link the PERSONREF to the right PERSON
        AnnotationModel henry = new AnnotationModel();
        henry.setLabel("PERSON");
        henry.addProperty("href", "https://en.wikipedia.org/Saint_Henry");
        prb = new AnnotationLinkModel();
        prb.setTarget(Long.valueOf(ref1.getId()));
        prb.setType("REFERENCED");
        henry.addLink(prb);
        response = jerseyTest.resource()
                .path("/tradition/" + tradId + "/annotation/")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, henry);
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        henry = response.getEntity(AnnotationModel.class);

        // Now add another reference so we can link it to the same person
        AnnotationModel ref2 = new AnnotationModel();
        ref2.setLabel("PERSONREF");
        prb = new AnnotationLinkModel();
        prb.setType("BEGIN");
        prb.setTarget(Long.valueOf(readingLookup.get("luminaribus/4")));
        pre = new AnnotationLinkModel();
        pre.setType("END");
        pre.setTarget(Long.valueOf(readingLookup.get("luminaribus/4")));
        ref2.addLink(prb);
        ref2.addLink(pre);
        response = jerseyTest.resource()
                .path("/tradition/" + tradId + "/annotation/")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, ref2);
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        ref2 = response.getEntity(AnnotationModel.class);

        // Add the link
        prb.setTarget(Long.valueOf(ref2.getId()));
        prb.setType("REFERENCED");
        response = jerseyTest.resource().path("/tradition/" + tradId + "/annotation/" + henry.getId() + "/link")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, prb);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        // Count up our annotations, testing annotation filtering along the way
        WebResource baseQuery = jerseyTest.resource().path("/tradition/" + tradId + "/annotations");
        response = baseQuery.get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<AnnotationModel> anns = response.getEntity(new GenericType<List<AnnotationModel>>() {});
        assertEquals(3, anns.size());
        response = baseQuery.queryParam("label", "PERSONREF").get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        anns = response.getEntity(new GenericType<List<AnnotationModel>>() {});
        assertEquals(2, anns.size());
        response = baseQuery.queryParam("label", "PERSON").get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        anns = response.getEntity(new GenericType<List<AnnotationModel>>() {});
        assertEquals(1, anns.size());

        // See if the structure makes sense
        for (AnnotationModel am : anns) {
            if (am.getLabel().equals("PERSON")) {
                assertEquals(2, am.getLinks().size());
                HashMap<Long,Boolean> found = new HashMap<>();
                found.put(Long.valueOf(ref1.getId()), false);
                found.put(Long.valueOf(ref2.getId()), false);
                for (AnnotationLinkModel alm : am.getLinks()) {
                    assertEquals("REFERENCED", alm.getType());
                    found.put(alm.getTarget(), true);
                }
                assertEquals(2, found.size());
                assertFalse(found.values().contains(false));
            } else {
                for (AnnotationLinkModel alm : am.getLinks()) {
                    ReadingModel target = jerseyTest.resource()
                            .path("/reading/" + alm.getTarget()).get(ReadingModel.class);
                    String rdgtext = target.getText();
                    assertTrue(rdgtext.equals("pontifex")
                            || rdgtext.equals("Henricus") || rdgtext.equals("luminaribus"));
                }
            }
        }

        // Now delete each of the references and see if the person gets deleted
        response = jerseyTest.resource().path("/tradition/" + tradId + "/annotation/" + ref1.getId())
                .delete(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<AnnotationModel> deleted = response.getEntity(new GenericType<List<AnnotationModel>>() {});
        assertEquals(1, deleted.size());
        assertEquals(ref1.getId(), deleted.get(0).getId());

        anns = jerseyTest.resource().path("/tradition/" + tradId + "/annotations")
                .get(new GenericType<List<AnnotationModel>>() {});
        assertEquals(2, anns.size());

        response = jerseyTest.resource().path("/tradition/" + tradId + "/annotation/" + ref2.getId())
                .delete(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        deleted = response.getEntity(new GenericType<List<AnnotationModel>>() {});
        assertEquals(2, deleted.size());
        assertEquals(ref2.getId(), deleted.get(0).getId());
        assertEquals(henry.getId(), deleted.get(1).getId());

        anns = jerseyTest.resource().path("/tradition/" + tradId + "/annotations")
                .get(new GenericType<List<AnnotationModel>>() {});
        assertEquals(0, anns.size());
    }

    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
        super.tearDown();
    }
}
