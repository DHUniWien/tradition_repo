package net.stemmaweb.stemmaserver.integrationtests;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.multipart.FormDataBodyPart;
import com.sun.jersey.multipart.FormDataMultiPart;
import com.sun.jersey.test.framework.JerseyTest;
import junit.framework.TestCase;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.SectionModel;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;
import org.junit.After;
import org.junit.Before;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.MediaType;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;

/**
 * Tests for text section functionality.
 * Created by tla on 08/02/2017.
 */
public class SectionTest extends TestCase {

    private GraphDatabaseService db;
    private JerseyTest jerseyTest;
    private String tradId;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        Util.setupTestDB(db, "user@example.com");

        // Create a JerseyTestServer for the necessary REST API calls
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(new Root())
                .create();
        jerseyTest.setUp();

        // Create a tradition to use
        ClientResponse jerseyResult = null;
        try {
            jerseyResult = Util.createTraditionFromFileOrString(jerseyTest, "Legend", "LR",
                    "user@example.com", "src/TestFiles/legendfrag.xml", "graphml");
        } catch (Exception e) {
            fail();
        }
        tradId = Util.getValueFromJson(jerseyResult, "tradId");
    }

    private String addSecondSection() {
        FormDataMultiPart form = new FormDataMultiPart();
        form.field("filetype", "graphml");
        form.field("name", "part 2");
        InputStream input = null;
        try {
            input = new FileInputStream("src/TestFiles/lf2.xml");
        } catch (FileNotFoundException f) {
            fail();
        }
        FormDataBodyPart fdp = new FormDataBodyPart("file", input,
                MediaType.APPLICATION_OCTET_STREAM_TYPE);
        form.bodyPart(fdp);
        ClientResponse jerseyResult = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section")
                .type(MediaType.MULTIPART_FORM_DATA_TYPE)
                .post(ClientResponse.class, form);
        assertEquals(ClientResponse.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        return Util.getValueFromJson(jerseyResult, "parentId");
    }

    // test creation of a tradition, that it has a single section
    public void testTraditionCreated() throws Exception {
        List<SectionModel> tSections = jerseyTest.resource().path("/tradition/" + tradId + "/sections")
                .get(new GenericType<List<SectionModel>>() {});
        assertEquals(1, tSections.size());
        assertEquals("DEFAULT", tSections.get(0).getName());
    }

    public void testAddSection() throws Exception {
        addSecondSection();

        List<SectionModel> tSections = jerseyTest.resource().path("/tradition/" + tradId + "/sections")
                .get(new GenericType<List<SectionModel>>() {});
        assertEquals(2, tSections.size());
        assertEquals("part 2", tSections.get(1).getName());

    }

    public void testDeleteSection() throws Exception {
        List<ReadingModel> tReadings = jerseyTest.resource().path("/tradition/" + tradId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});
        assertEquals(30, tReadings.size());

        addSecondSection();
        tReadings = jerseyTest.resource().path("/tradition/" + tradId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});
        assertEquals(78, tReadings.size());

        List<SectionModel> tSections = jerseyTest.resource().path("/tradition/" + tradId + "/sections")
                .get(new GenericType<List<SectionModel>>() {});
        SectionModel firstSection = tSections.get(0);

        ClientResponse jerseyResult = jerseyTest.resource()
                .path("/tradition/" + tradId + "/section/" + firstSection.getId())
                .type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResult.getStatus());

        tReadings = jerseyTest.resource().path("/tradition/" + tradId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});
        assertEquals(48, tReadings.size());
    }

    // test ordering of sections

    // test reordering of sections

    // test merging two sections

    // test splitting a section

    @After
    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
        super.tearDown();
    }

}
