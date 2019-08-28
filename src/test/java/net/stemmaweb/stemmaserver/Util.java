package net.stemmaweb.stemmaserver;

import com.alexmerz.graphviz.ParseException;
import com.alexmerz.graphviz.Parser;
import com.alexmerz.graphviz.objects.Edge;
import com.alexmerz.graphviz.objects.Graph;

import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.DatabaseService;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.client.ClientResponse;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.test.JerseyTest;
import org.json.JSONException;
import org.json.JSONObject;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A collection of testing utility functions that are useful in multiple tests.
 *
 * @author tla
 */
public class Util {

    // Helper method
    public static void assertStemmasEquivalent (String expected, String actual) {
        // Add strategic newlines to each string if it has none.

        StringBuffer expectedStream = new StringBuffer(newline_dot(expected));
        StringBuffer actualStream = new StringBuffer(newline_dot(actual));

        Parser p = new Parser();
        try {
            p.parse(expectedStream);
        } catch (ParseException e) {
            fail();
        }
        ArrayList<Graph> expectedGraphs = p.getGraphs();

        Parser q = new Parser();
        try {
            q.parse(actualStream);
        } catch (ParseException e) {
            fail();
        }
        ArrayList<Graph> actualGraphs = q.getGraphs();

        assertEquals(expectedGraphs.size(), actualGraphs.size());

        // Now check the contents of each graph
        int i = 0;
        while(i < expectedGraphs.size()) {

            Graph expectedStemma = expectedGraphs.get(i);
            Graph actualStemma = actualGraphs.get(i);

            assertEquals(expectedStemma.getType(), actualStemma.getType());  // Same [di]graph declaration

            // Now check for the expected nodes
            HashSet<String> expectedNodes = new HashSet<>();
            expectedStemma.getNodes(false).forEach(e -> expectedNodes.add(getSigil(e)));
            HashSet<String> actualNodes = new HashSet<>();
            actualStemma.getNodes(false).forEach(e -> actualNodes.add(getSigil(e)));
            assertEquals(expectedNodes.size(), actualNodes.size());
            for (String graphNode : actualNodes) {
                assertTrue(expectedNodes.contains(graphNode));
            }

            // ...and the expected edges. Just count them for now; comparison is complicated
            // for undirected graphs.
            HashSet<String> expectedEdges = collectEdges(expectedStemma);
            HashSet<String> actualEdges = collectEdges(actualStemma);
            assertEquals(expectedEdges.size(), actualEdges.size());
            for (String graphEdge : actualEdges) {
                assertTrue(expectedEdges.contains(graphEdge));
            }
            i++;
        }
    }

    private static String getSigil (com.alexmerz.graphviz.objects.Node n) {
        String id = n.getId().getId();
        if (id.equals(""))
            id = n.getId().getLabel();
        return id;
    }

    private static String newline_dot (String dot) {
        if (!dot.contains("\n"))
            dot = dot.replace("  ", "\n");
        return dot;
    }

    private static HashSet<String> collectEdges (Graph stemma) {
        HashSet<String> collected = new HashSet<>();
        // The collector for directed graphs
        Consumer<Edge> edgeCollector = e -> collected.add(
                getSigil(e.getSource().getNode()) + " - " + getSigil(e.getTarget().getNode()));

        // The collector for undirected graphs
        if (stemma.getType() == 1)
            edgeCollector = e -> {
                ArrayList<String> vector = new ArrayList<>();
                vector.add(getSigil(e.getSource().getNode()));
                vector.add(getSigil(e.getTarget().getNode()));
                Collections.sort(vector);
                collected.add(vector.get(0) + " - " + vector.get(1));
                };

        stemma.getEdges().forEach(edgeCollector);
        return collected;
    }

    public static String getValueFromJson (Response r, String key) {
        String value = null;
        try {
            JSONObject content = new JSONObject(r.readEntity(String.class));
            if (content.has(key))
                value = String.valueOf(content.get(key));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return value;
    }

    public static String getValueFromJson (ClientResponse r, String key) {
        String value = null;
        InputStream response = r.getEntityStream();
        try {
            JSONObject content = new JSONObject(IOUtils.toString(response, "UTF-8"));
            if (content.has(key))
                value = String.valueOf(content.get(key));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }

    public static String getSpecificReading(JerseyTest jerseyTest, String tradId, String sectId, String reading, Long rank) {
        List<ReadingModel> allReadings = jerseyTest
                .target("/tradition/" + tradId + "/section/" + sectId + "/readings")
                .request()
                .get(new GenericType<List<ReadingModel>>() {});
        for (ReadingModel r : allReadings) {
            if (r.getText().equals(reading) && r.getRank().equals(rank))
                return r.getId();
        }
        return null;
    }

    public static void setupTestDB(GraphDatabaseService db, String userId) {
        // Populate the test database with the root node and a user with id 1
        DatabaseService.createRootNode(db);
        try(Transaction tx = db.beginTx()) {
            Node rootNode = db.findNode(Nodes.ROOT, "name", "Root node");
            Node node = db.createNode(Nodes.USER);
            node.setProperty("id", userId);
            node.setProperty("role", "admin");

            rootNode.createRelationshipTo(node, ERelations.SEQUENCE);
            tx.success();
        }
    }

    public static JerseyTest setupJersey() throws Exception {
//        Root webResource = new Root();
        JerseyTest jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(Root.class)
                .create();
        jerseyTest.setUp();
        return jerseyTest;
    }

    public static Response createTraditionFromFileOrString(JerseyTest jerseyTest, String tName, String tDir,
                                                                 String userId, String fName, String fType) {
        FormDataMultiPart form = new FormDataMultiPart();
        if (fType != null) form.field("filetype", fType);
        if (tName != null) form.field("name", tName);
        if (tDir != null) form.field("direction", tDir);
        if (userId != null) form.field("userId", userId);
        if (fName != null) {
            // It could be a filename or it could be a content string. Try one and then
            // the other.
            InputStream input = getFileOrStringContent(fName);
            FormDataBodyPart fdp = new FormDataBodyPart("file", input,
                    MediaType.APPLICATION_OCTET_STREAM_TYPE);
            form.bodyPart(fdp);
        }
        return  jerseyTest
                .target("/tradition")
                .request()
                .post(Entity.entity(form, form.getMediaType()));
    }

    public static Response addSectionToTradition(JerseyTest jerseyTest, String traditionId, String fileName,
                                                        String fileType, String sectionName) {
        FormDataMultiPart form = new FormDataMultiPart();
        form.field("filetype", fileType);
        form.field("name", sectionName);
        InputStream input = getFileOrStringContent(fileName);
        FormDataBodyPart fdp = new FormDataBodyPart("file", input,
                MediaType.APPLICATION_OCTET_STREAM_TYPE);
        form.bodyPart(fdp);
        return jerseyTest.target("/tradition/" + traditionId + "/section")
                .request(MediaType.MULTIPART_FORM_DATA_TYPE)
                .post(Entity.entity(form, form.getMediaType()));
    }

    private static InputStream getFileOrStringContent(String content) {
        InputStream result;
        try {
            result = new FileInputStream(content);
        } catch (FileNotFoundException e) {
            result = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        }
        return result;
    }

    public static HashMap<String, String> makeReadingLookup (JerseyTest jerseyTest, String tradId) {
        HashMap<String, String> result = new HashMap<>();
        List<ReadingModel> readings = jerseyTest
                .target("/tradition/" + tradId + "/readings")
                .request()
                .get(new GenericType<List<ReadingModel>>() {});
        for (ReadingModel r : readings) {
            String key = String.format("%s/%d", r.getText(), r.getRank());
            result.put(key, r.getId());
        }
        return result;
    }

}
