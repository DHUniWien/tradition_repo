package net.stemmaweb.stemmaserver.integrationtests;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import net.stemmaweb.model.AnnotationLabelModel;
import net.stemmaweb.model.AnnotationModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.Util;

import static org.junit.Assert.*;

public class AnnotationLabelTest {
    private JerseyTest jerseyTest;
    private String tradId;

    @Before
    public void setUp() throws Exception {
        DatabaseManagementService dbbuilder = new TestDatabaseManagementServiceBuilder().impermanent().build();
        GraphDatabaseService db = dbbuilder.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME);
        new GraphDatabaseServiceProvider(dbbuilder, db);
        Util.setupTestDB(db, "1");

        // Create a JerseyTestServer for the necessary REST API calls
        jerseyTest = Util.setupJersey();

        Response jerseyResult = Util.createTraditionFromFileOrString(jerseyTest, "Tradition", "LR", "1",
                "src/TestFiles/john.csv", "csv");
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        tradId = Util.getValueFromJson(jerseyResult, "tradId");
    }

    private AnnotationLabelModel putLabel(String urlSegment, AnnotationLabelModel alm, Response.Status expectedStatus) {
        AnnotationLabelModel result;
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/annotationlabel/" + urlSegment)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(alm))) {
            assertEquals(expectedStatus.getStatusCode(), r.getStatus());
            result = expectedStatus == Response.Status.OK || expectedStatus == Response.Status.CREATED
                    ? r.readEntity(AnnotationLabelModel.class)
                    : null;
        }
        return result;
    }

    @Test
    public void testPutRenameAppliesNewName() {
        // Create a label under "mylabel"
        AnnotationLabelModel alm = new AnnotationLabelModel();
        alm.setName("mylabel");
        alm.setLinks(new HashMap<>(Map.of("READING", "BEGIN")));
        putLabel("mylabel", alm, Response.Status.CREATED);

        // PUT again to the same URL, with a body whose name differs from the URL segment.
        // This must actually rename the node -- not silently no-op while still returning 200.
        alm.setName("renamedlabel");
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/annotationlabel/mylabel")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(alm))) {
            assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
        }

        // Fetching by the new name succeeds
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/annotationlabel/renamedlabel")
                .request(MediaType.APPLICATION_JSON).get()) {
            assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
            assertEquals("renamedlabel", r.readEntity(AnnotationLabelModel.class).getName());
        }

        // Fetching by the old name now 404s
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/annotationlabel/mylabel")
                .request(MediaType.APPLICATION_JSON).get()) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), r.getStatus());
        }
    }

    @Test
    public void testAnnotationLabelIdAndDualAddressing() {
        AnnotationLabelModel alm = new AnnotationLabelModel();
        alm.setName("testlabel");
        alm.setLinks(new HashMap<>(Map.of("READING", "BEGIN")));
        AnnotationLabelModel created = putLabel("testlabel", alm, Response.Status.CREATED);

        assertNotNull(created.getId());
        assertTrue(DatabaseService.nameIsNumeric(created.getId()));
        String id = created.getId();

        // GET by numeric id works identically to GET by name
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/annotationlabel/" + id)
                .request(MediaType.APPLICATION_JSON).get()) {
            assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
            assertEquals("testlabel", r.readEntity(AnnotationLabelModel.class).getName());
        }

        // PUT (rename) by numeric id
        alm.setName("renamedlabel");
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/annotationlabel/" + id)
                .request(MediaType.APPLICATION_JSON).put(Entity.json(alm))) {
            assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
            assertEquals("renamedlabel", r.readEntity(AnnotationLabelModel.class).getName());
        }

        // DELETE by numeric id
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/annotationlabel/" + id)
                .request().delete()) {
            assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
        }
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/annotationlabel/" + id)
                .request(MediaType.APPLICATION_JSON).get()) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), r.getStatus());
        }
    }

    @Test
    public void testAnnotationLabelNumericNameRejected() {
        // A numeric-only name is rejected on create...
        AnnotationLabelModel alm = new AnnotationLabelModel();
        alm.setName("12345");
        alm.setLinks(new HashMap<>(Map.of("READING", "BEGIN")));
        putLabel("12345", alm, Response.Status.BAD_REQUEST);

        // ...and on rename.
        AnnotationLabelModel legit = new AnnotationLabelModel();
        legit.setName("legit");
        legit.setLinks(new HashMap<>(Map.of("READING", "BEGIN")));
        putLabel("legit", legit, Response.Status.CREATED);

        legit.setName("98765");
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/annotationlabel/legit")
                .request(MediaType.APPLICATION_JSON).put(Entity.json(legit))) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        }
    }

    @Test
    public void testAnnotationLabelRenameCollisionAndSelfRename() {
        AnnotationLabelModel a = new AnnotationLabelModel();
        a.setName("typea");
        a.setLinks(new HashMap<>(Map.of("READING", "BEGIN")));
        putLabel("typea", a, Response.Status.CREATED);

        AnnotationLabelModel b = new AnnotationLabelModel();
        b.setName("typeb");
        b.setLinks(new HashMap<>(Map.of("READING", "BEGIN")));
        putLabel("typeb", b, Response.Status.CREATED);

        // Renaming typeb to a name already used by typea is rejected with 409
        b.setName("typea");
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/annotationlabel/typeb")
                .request(MediaType.APPLICATION_JSON).put(Entity.json(b))) {
            assertEquals(Response.Status.CONFLICT.getStatusCode(), r.getStatus());
        }

        // Renaming typea to its own current name succeeds (200, not 409)
        AnnotationLabelModel anew = new AnnotationLabelModel();
        anew.setName("typea");
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/annotationlabel/typea")
                .request(MediaType.APPLICATION_JSON).put(Entity.json(anew))) {
            assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
        }
    }

    @Test
    public void testReservedAndPrimaryLabelCollisionsStillWork() {
        for (String reserved : new String[]{"USER", "ROOT", "__SYSTEM__", "READING", "SECTION", "WITNESS"}) {
            AnnotationLabelModel alm = new AnnotationLabelModel();
            alm.setName(reserved);
            alm.setLinks(new HashMap<>(Map.of("READING", "BEGIN")));
            try (Response r = jerseyTest.target("/tradition/" + tradId + "/annotationlabel/" + reserved)
                    .request(MediaType.APPLICATION_JSON).put(Entity.json(alm))) {
                assertEquals("expected conflict for " + reserved, Response.Status.CONFLICT.getStatusCode(), r.getStatus());
            }
        }

        // And the same collision check still fires on rename to a reserved/primary name.
        AnnotationLabelModel alm = new AnnotationLabelModel();
        alm.setName("mytype");
        alm.setLinks(new HashMap<>(Map.of("READING", "BEGIN")));
        putLabel("mytype", alm, Response.Status.CREATED);

        alm.setName("READING");
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/annotationlabel/mytype")
                .request(MediaType.APPLICATION_JSON).put(Entity.json(alm))) {
            assertEquals(Response.Status.CONFLICT.getStatusCode(), r.getStatus());
        }
    }

    @Test
    public void annotationLabelNonexistentTraditionTest() {
        // GET/PUT/DELETE against a tradition id that doesn't exist at all (as opposed to an
        // annotation label reference that doesn't exist within a real tradition) must 404, not
        // 500 -- the annotation label node resolution must not NPE when
        // VariantGraphService.getTraditionNode returns null.
        String badTradId = "10000";

        Response getResponse = jerseyTest.target("/tradition/" + badTradId + "/annotationlabel/foo")
                .request(MediaType.APPLICATION_JSON)
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), getResponse.getStatus());

        AnnotationLabelModel putBody = new AnnotationLabelModel();
        putBody.setName("foo");
        try (Response putResponse = jerseyTest.target("/tradition/" + badTradId + "/annotationlabel/foo")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(putBody))) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), putResponse.getStatus());
        }

        try (Response deleteResponse = jerseyTest.target("/tradition/" + badTradId + "/annotationlabel/foo")
                .request()
                .delete()) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), deleteResponse.getStatus());
        }
    }

    @Test
    public void testRenameBlockedWhileUsedByAnnotation() {
        AnnotationLabelModel alm = new AnnotationLabelModel();
        alm.setName("COMMENT");
        Map<String, String> props = new HashMap<>();
        props.put("text", "String");
        alm.setProperties(props);
        alm.setLinks(new HashMap<>(Map.of("READING", "BEGIN")));
        AnnotationLabelModel created = putLabel("COMMENT", alm, Response.Status.CREATED);

        AnnotationModel am = new AnnotationModel();
        am.setLabel("COMMENT");
        Map<String, Object> aprops = new HashMap<>();
        aprops.put("text", "a comment");
        am.setProperties(aprops);
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/annotation")
                .request(MediaType.APPLICATION_JSON).post(Entity.json(am))) {
            assertEquals(Response.Status.CREATED.getStatusCode(), r.getStatus());
        }

        // Renaming the in-use label, by name or by id, is refused.
        alm.setName("REMARK");
        alm.setLinks(new HashMap<>(Map.of("READING", "BEGIN")));
        putLabel("COMMENT", alm, Response.Status.CONFLICT);
        putLabel(created.getId(), alm, Response.Status.CONFLICT);
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/annotationlabel/COMMENT")
                .request(MediaType.APPLICATION_JSON).get()) {
            assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
            assertEquals(props, r.readEntity(AnnotationLabelModel.class).getProperties());
        }
        // ...and the delete guard still protects it.
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/annotationlabel/COMMENT")
                .request().delete()) {
            assertEquals(Response.Status.CONFLICT.getStatusCode(), r.getStatus());
        }

        // Updating it under the same name is still fine.
        alm.setName("COMMENT");
        putLabel("COMMENT", alm, Response.Status.OK);
    }

    @Test
    public void testRenameBlockedWhileLinkedFromOtherLabel() {
        AnnotationLabelModel target = new AnnotationLabelModel();
        target.setName("PERSON");
        target.setLinks(new HashMap<>(Map.of("READING", "BEGIN")));
        putLabel("PERSON", target, Response.Status.CREATED);

        AnnotationLabelModel linker = new AnnotationLabelModel();
        linker.setName("MENTION");
        linker.setLinks(new HashMap<>(Map.of("PERSON", "REFERS_TO")));
        putLabel("MENTION", linker, Response.Status.CREATED);

        // PERSON is referenced by MENTION's link schema, so can't be renamed.
        target.setName("HUMAN");
        putLabel("PERSON", target, Response.Status.CONFLICT);

        // An unused label renames fine, and the rename takes effect.
        AnnotationLabelModel unused = new AnnotationLabelModel();
        unused.setName("PLACE");
        unused.setLinks(new HashMap<>(Map.of("READING", "BEGIN")));
        putLabel("PLACE", unused, Response.Status.CREATED);
        unused.setName("LOCATION");
        assertEquals("LOCATION", putLabel("PLACE", unused, Response.Status.OK).getName());

        // Deleting PERSON removes the dangling link from MENTION's link schema.
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/annotationlabel/PERSON")
                .request().delete()) {
            assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
        }
        try (Response r = jerseyTest.target("/tradition/" + tradId + "/annotationlabel/MENTION")
                .request(MediaType.APPLICATION_JSON).get()) {
            assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
            assertFalse(r.readEntity(AnnotationLabelModel.class).getLinks().containsKey("PERSON"));
        }
    }

    @Test
    public void testAnnotationLabelCrossTraditionId() {
        AnnotationLabelModel alm = new AnnotationLabelModel();
        alm.setName("COMMENT");
        alm.setLinks(new HashMap<>(Map.of("READING", "BEGIN")));
        AnnotationLabelModel created = putLabel("COMMENT", alm, Response.Status.CREATED);
        Response jerseyResult = Util.createTraditionFromFileOrString(jerseyTest, "Other", "LR", "1",
                "src/TestFiles/john.csv", "csv");
        String otherId = Util.getValueFromJson(jerseyResult, "tradId");
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), jerseyTest
                .target("/tradition/" + otherId + "/annotationlabel/" + created.getId()).request().get().getStatus());
        try (Response r = jerseyTest.target("/tradition/" + otherId + "/annotationlabel/" + created.getId())
                .request().delete()) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), r.getStatus());
        }
        assertEquals("COMMENT", jerseyTest.target("/tradition/" + tradId + "/annotationlabel/" + created.getId())
                .request().get(AnnotationLabelModel.class).getName());
    }

    @Test
    public void testAnnotationLabelEmptyNewName() {
        // Set up a label
        AnnotationLabelModel alm = new AnnotationLabelModel();
        alm.setName("COMMENT");
        alm.setLinks(new HashMap<>(Map.of("READING", "BEGIN")));
        AnnotationLabelModel created = putLabel("COMMENT", alm, Response.Status.CREATED);
        // Now try to change its name to nothing
        alm.setName("");
        // We shouldn't be able to make a new label with no name
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/annotationlabel//")
                .request().put(Entity.json(alm))) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), jerseyResult.getStatus());
        }
        // and we shouldn't be able to change the name of an existing label to nothing
        AnnotationLabelModel empty = new AnnotationLabelModel();
        empty.setName("");
        try (Response jerseyResult = jerseyTest.target("/tradition/" + tradId + "/annotationlabel/" + created.getId())
                .request().put(Entity.json(empty))) {
            assertEquals(Response.Status.OK.getStatusCode(), jerseyResult.getStatus());
            assertEquals("COMMENT", jerseyResult.readEntity(AnnotationLabelModel.class).getName());
        }
    }

    @After
    public void tearDown() throws Exception {
        GraphDatabaseServiceProvider.shutdown();
        jerseyTest.tearDown();
    }

}
