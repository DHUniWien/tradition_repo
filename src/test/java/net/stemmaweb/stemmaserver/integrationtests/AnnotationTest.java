package net.stemmaweb.stemmaserver.integrationtests;

import junit.framework.TestCase;
import net.stemmaweb.model.*;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.Util;

import org.glassfish.jersey.test.JerseyTest;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.*;
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

    private Response addTestLabel() {
        AnnotationLabelModel alm = returnTestLabel();
        Response response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(alm));
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

    private Response addTestAnnotation() {
        AnnotationModel am = returnTestAnnotation();
        Response response = jerseyTest
                .target("/tradition/" + tradId + "/annotation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(am));
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        return response;
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
        Response response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(alm));
        assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());

        alm.setName("USER");
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(alm));
        assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
    }

    public void testCreateAnnotationLabel() {
        // Check that we can set an annotation label
        AnnotationLabelModel alm = returnTestLabel();
        Response response = addTestLabel();
        AnnotationLabelModel result = response.readEntity(AnnotationLabelModel.class);
        assertEquals(alm.getName(), result.getName());
        assertEquals(alm.getProperties(), result.getProperties());
        assertEquals(alm.getLinks(), result.getLinks());
        for (String k : result.getProperties().keySet()) assertEquals(alm.getProperties().get(k), result.getProperties().get(k));

        // Check that we can retrieve the label
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + result.getName())
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        result = response.readEntity(AnnotationLabelModel.class);
        assertEquals(alm.getName(), result.getName());
        assertEquals(alm.getProperties(), result.getProperties());
        assertEquals(alm.getLinks(), result.getLinks());
    }

    public void testChangeAnnotationLabel() {
        AnnotationLabelModel alm = addTestLabel().readEntity(AnnotationLabelModel.class);
        Map<String, String> newProps = new HashMap<>();
        newProps.put("english_text", "String");
        alm.setProperties(newProps);
        Response response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(alm));
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        alm = response.readEntity(AnnotationLabelModel.class);
        String origName = alm.getName();
        assertEquals("TRANSLATION", origName);
        assertEquals(newProps, alm.getProperties());
        // The links should not have changed
        assertEquals(1, alm.getLinks().size());

        // Try to change the name to something disallowed
        alm.setName("USER");
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(alm));
        assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());

        alm.setName("READING");
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(alm));
        assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());

        // Add a second annotation label
        AnnotationLabelModel newalm = new AnnotationLabelModel();
        newalm.setName("MARKED");
        Map<String,String> newLinks = new HashMap<>();
        newLinks.put("SECTION", "HAS_MARK");
        newalm.setLinks(newLinks);
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + newalm.getName())
                .request(MediaType.APPLICATION_JSON_TYPE)
                .put(Entity.json(newalm));
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

        // Try to change the old annotation to match this name
        alm.setName(newalm.getName());
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + origName)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(alm));
        assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());

        // Now change the name to something that isn't a problem
        alm.setName("ENGLISHING");
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + origName)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(alm));
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    }

    public void testAddAnnotation() {
        // Label specification and addition
        addTestLabel();

        // Now we use the label
        Response response = addTestAnnotation();
        AnnotationModel am = response.readEntity(AnnotationModel.class);

        // Check that the graph looks right
        try (Transaction tx = db.beginTx()) {
            Node annoNode = db.getNodeById(Long.parseLong(am.getId()));
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

        List<AnnotationModel> existing = jerseyTest
                .target("/tradition/" + tradId + "/annotations")
                .request()
                .get(new GenericType<>() {});
        assertEquals(1, existing.size());

        Response response = jerseyTest
                .target("/tradition/" + tradId + "/annotation/" + existing.get(0).getId())
                .request(MediaType.APPLICATION_JSON)
                .delete();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

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
        Response response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .request()
                .delete();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

        // Make it exist
        addTestLabel();

        // Add an annotation so that we can test deletion conflict
        AnnotationModel am = addTestAnnotation().readEntity(AnnotationModel.class);

        // Try to delete a label that is in use
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .request()
                .delete();
        assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());

        // Delete the annotation in question
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotation/" + am.getId())
                .request()
                .delete();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        // Now delete the label for real
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                .request(MediaType.APPLICATION_JSON)
                .delete();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        // Check that the label is really gone
        List<AnnotationLabelModel> labels = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabels")
                .request(MediaType.APPLICATION_JSON)
                .get(new GenericType<>() {});
        assertEquals(0, labels.size());
    }

    public void testAddDeleteAnnotationLink() {
        addTestLabel();
        AnnotationModel am = addTestAnnotation().readEntity(AnnotationModel.class);

        AnnotationLinkModel alm = new AnnotationLinkModel();
        alm.setTarget(Long.valueOf(readingLookup.get("venerabilis/3")));
        alm.setType("BEGIN");
        Response response = jerseyTest
                .target("/tradition/" + tradId + "/annotation/" + am.getId() + "/link")
                .request()
                .post(Entity.json(alm));
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        // There should now be two BEGIN links
        am = response.readEntity(AnnotationModel.class);
        assertEquals(3, am.getLinks().size());
        assertEquals(2, am.getLinks().stream().filter(x -> x.getType().equals("BEGIN")).count());


        // Try it again - we should get a not-modified
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotation/" + am.getId() + "/link")
                .request()
                .post(Entity.json(alm));
        assertEquals(Response.Status.NOT_MODIFIED.getStatusCode(), response.getStatus());

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
        Response response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + pref.getName())
                .request()
                .put(Entity.json(pref));
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

        // Make a PERSON annotation label
        AnnotationLabelModel person = new AnnotationLabelModel();
        person.setName("PERSON");
        person.addLink("PERSONREF", "REFERENCED");
        person.addProperty("href", "String");
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabel/" + person.getName())
                .request()
                .put(Entity.json(person));
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

        // Check that we can retrieve all the labels we made
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotationlabels")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<AnnotationLabelModel> allLabels = response.readEntity(new GenericType<>() {});
        assertEquals(2, allLabels.size());
        assertTrue(allLabels.stream().anyMatch(x -> x.getName().equals("PERSON")));
        assertTrue(allLabels.stream().anyMatch(x -> x.getName().equals("PERSONREF")));

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
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotation/")
                .request()
                .post(Entity.json(ref1));
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        ref1 = response.readEntity(AnnotationModel.class);

        // Now try to link the PERSONREF to the right PERSON
        AnnotationModel henry = new AnnotationModel();
        henry.setLabel("PERSON");
        henry.setPrimary(true);
        henry.addProperty("href", "https://en.wikipedia.org/Saint_Henry");
        prb = new AnnotationLinkModel();
        prb.setTarget(Long.valueOf(ref1.getId()));
        prb.setType("REFERENCED");
        henry.addLink(prb);
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotation/")
                .request()
                .post(Entity.json(henry));
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        henry = response.readEntity(AnnotationModel.class);

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
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotation/")
                .request()
                .post(Entity.json(ref2));
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        ref2 = response.readEntity(AnnotationModel.class);

        // Add the link
        prb.setTarget(Long.valueOf(ref2.getId()));
        prb.setType("REFERENCED");
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotation/" + henry.getId() + "/link")
                .request()
                .post(Entity.json(prb));
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        // Count up our annotations, testing annotation filtering along the way
        WebTarget baseQuery = jerseyTest.target("/tradition/" + tradId + "/annotations");
        response = baseQuery.request().get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<AnnotationModel> anns = response.readEntity(new GenericType<>() {});
        assertEquals(3, anns.size());
        response = baseQuery.queryParam("label", "PERSONREF").request().get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        anns = response.readEntity(new GenericType<>() {});
        assertEquals(2, anns.size());
        response = baseQuery.queryParam("label", "PERSON").request().get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        anns = response.readEntity(new GenericType<>() {});
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
        response = jerseyTest.target("/tradition/" + tradId + "/annotation/" + ref1.getId())
                .request()
                .delete();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<AnnotationModel> deleted = response.readEntity(new GenericType<>() {});
        assertEquals(1, deleted.size());
        assertEquals(ref1.getId(), deleted.get(0).getId());

        anns = jerseyTest.target("/tradition/" + tradId + "/annotations")
                .request()
                .get(new GenericType<>() {});
        assertEquals(2, anns.size());

        response = jerseyTest
                .target("/tradition/" + tradId + "/annotation/" + ref2.getId())
                .request()
                .delete();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        deleted = response.readEntity(new GenericType<>() {});
        assertEquals(1, deleted.size());
        assertEquals(ref2.getId(), deleted.get(0).getId());

        anns = jerseyTest
                .target("/tradition/" + tradId + "/annotations")
                .request()
                .get(new GenericType<>() {});
        assertEquals(1, anns.size());

        // Now delete the PERSON explicitly, which should work
        response = jerseyTest
                .target("/tradition/" + tradId + "/annotation/" + henry.getId())
                .request()
                .delete();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        deleted = response.readEntity(new GenericType<>() {});
        assertEquals(1, deleted.size());
        assertEquals(henry.getId(), deleted.get(0).getId());

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
            Response response = jerseyTest
                    .target("/tradition/" + tradId + "/annotationlabel/" + alm.getName())
                    .request(MediaType.APPLICATION_JSON)
                    .put(Entity.json(alm));
            assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        }

        // Now try using each of these annotations
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

        for (String k : nameToType.keySet()) {
            AnnotationModel am = new AnnotationModel();
            am.setLabel(k);
            Map<String, Object> props = new HashMap<>();
            props.put("value", nameToValue.get(k));
            am.setProperties(props);
            AnnotationLinkModel start = new AnnotationLinkModel();
            start.setTarget(Long.valueOf(readingLookup.get("in/1")));
            start.setType("ATTACHED");
            am.addLink(start);

            // Try making each of these annotations
            Response response = jerseyTest
                    .target("/tradition/" + tradId + "/annotation")
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(am));
            assertEquals("creation of " + k + " annotation", Response.Status.CREATED.getStatusCode(), response.getStatus());
        }

        // Check that they come back out
        Response response = jerseyTest.target("/tradition/" + tradId + "/annotations")
                .request().get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<AnnotationModel> ourAnnotations = response.readEntity(new GenericType<>() {});
        assertEquals(nameToType.size(), ourAnnotations.size());
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
        assertTrue(tradXmlOutput.contains("[ANNOTATIONLABEL]"));
        assertTrue(tradXmlOutput.contains("[LINKS]"));
        assertTrue(tradXmlOutput.contains("[TRANSLATION]"));
        assertTrue(tradXmlOutput.contains(translation));

        // ...also for the individual section.
        List<SectionModel> sects = jerseyTest.target("/tradition/" + tradId + "/sections")
                .request().get(new GenericType<>() {});
        String sectId = sects.get(0).getId();
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
        assertEquals("TRANSLATION", am.get(0).getLabel());
        assertTrue(am.get(0).getProperties().containsKey("text"));
        assertEquals(translation, am.get(0).getProperties().get("text"));

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
        db.shutdown();
        jerseyTest.tearDown();
        super.tearDown();
    }
}
