package net.stemmaweb.stemmaserver;

import com.alexmerz.graphviz.ParseException;
import com.alexmerz.graphviz.Parser;
import com.alexmerz.graphviz.objects.Edge;
import com.alexmerz.graphviz.objects.Graph;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataBodyPart;
import com.sun.jersey.multipart.FormDataMultiPart;
import com.sun.jersey.test.framework.JerseyTest;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.DatabaseService;
import org.apache.commons.io.IOUtils;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

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
            JSONObject content = new JSONObject((String) r.getEntity());
            if (content.has(key))
                value = String.valueOf(content.get(key));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return value;
    }

    public static String getValueFromJson (ClientResponse r, String key) {
        String value = null;
        InputStream response = r.getEntityInputStream();
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
        List<ReadingModel> allReadings = jerseyTest.resource().path("/tradition/" + tradId + "/section/" + sectId + "/readings")
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
        Root webResource = new Root();
        JerseyTest jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(webResource)
                .create();
        jerseyTest.setUp();
        return jerseyTest;
    }

    public static Response createTraditionDirectly(String tName, String tDir,
                                                   String userId, String fName, String fType) {
        Root appRest = new Root();
        InputStream input = null;
        FormDataContentDisposition fdcd = null;
        String empty = "true";
        if (fName != null) {
            empty = null;
            input = getFileOrStringContent(fName);
            fdcd = new FormDataBodyPart("file", input,
                    MediaType.APPLICATION_OCTET_STREAM_TYPE).getFormDataContentDisposition();
        }

        return appRest.importGraphMl(tName, userId, "false", "Default",
                tDir, empty, fType, input, fdcd);
    }

    public static ClientResponse createTraditionFromFileOrString(JerseyTest jerseyTest, String tName, String tDir,
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
        return  jerseyTest.resource()
                .path("/tradition")
                .type(MediaType.MULTIPART_FORM_DATA_TYPE)
                .post(ClientResponse.class, form);
    }

    public static ClientResponse addSectionToTradition(JerseyTest jerseyTest, String traditionId, String fileName,
                                                        String fileType, String sectionName) {
        FormDataMultiPart form = new FormDataMultiPart();
        form.field("filetype", fileType);
        form.field("name", sectionName);
        InputStream input = getFileOrStringContent(fileName);
        FormDataBodyPart fdp = new FormDataBodyPart("file", input,
                MediaType.APPLICATION_OCTET_STREAM_TYPE);
        form.bodyPart(fdp);
        return jerseyTest.resource()
                .path("/tradition/" + traditionId + "/section")
                .type(MediaType.MULTIPART_FORM_DATA_TYPE)
                .post(ClientResponse.class, form);
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
        List<ReadingModel> readings = jerseyTest.resource().path("/tradition/" + tradId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});
        for (ReadingModel r : readings) {
            String key = String.format("%s/%d", r.getText(), r.getRank());
            result.put(key, r.getId());
        }
        return result;
    }

}
