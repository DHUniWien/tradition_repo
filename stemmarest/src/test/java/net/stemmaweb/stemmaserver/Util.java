package net.stemmaweb.stemmaserver;

import com.alexmerz.graphviz.ParseException;
import com.alexmerz.graphviz.Parser;
import com.alexmerz.graphviz.objects.Edge;
import com.alexmerz.graphviz.objects.Graph;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.multipart.FormDataBodyPart;
import com.sun.jersey.multipart.FormDataMultiPart;
import com.sun.jersey.test.framework.JerseyTest;
import org.apache.commons.io.IOUtils;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
            assertTrue(false);
        }
        ArrayList<Graph> expectedGraphs = p.getGraphs();

        Parser q = new Parser();
        try {
            q.parse(actualStream);
        } catch (ParseException e) {
            assertTrue(false);
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
            expectedStemma.getNodes(false).forEach(e -> expectedNodes.add(e.getId().getId()));
            HashSet<String> actualNodes = new HashSet<>();
            actualStemma.getNodes(false).forEach(e -> actualNodes.add(e.getId().getId()));
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

    private static String newline_dot (String dot) {
        if (!dot.contains("\n"))
            dot = dot.replace("  ", "\n");
        return dot;
    }

    private static HashSet<String> collectEdges (Graph stemma) {
        HashSet<String> collected = new HashSet<>();
        // The collector for directed graphs
        Consumer<Edge> edgeCollector = e -> collected.add(
                e.getSource().getNode().getId().getId() + " - " +
                        e.getTarget().getNode().getId().getId());

        // The collector for undirected graphs
        if (stemma.getType() == 1)
            edgeCollector = e -> {
                ArrayList<String> vector = new ArrayList<>();
                vector.add(e.getSource().getNode().getId().getId());
                vector.add(e.getTarget().getNode().getId().getId());
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

    public static ClientResponse createTraditionFromFile(JerseyTest jerseyTest, String tName, String tDir, String userId,
                                                         String fName, String fType) throws FileNotFoundException {
        FormDataMultiPart form = new FormDataMultiPart();
        if (fType != null) form.field("filetype", fType);
        if (tName != null) form.field("name", tName);
        if (tDir != null) form.field("direction", tDir);
        if (userId != null) form.field("userId", userId);
        if (fName != null) {
            FormDataBodyPart fdp = new FormDataBodyPart("file",
                    new FileInputStream(fName),
                    MediaType.APPLICATION_OCTET_STREAM_TYPE);
            form.bodyPart(fdp);
        }
        return  jerseyTest.resource()
                .path("/tradition")
                .type(MediaType.MULTIPART_FORM_DATA_TYPE)
                .put(ClientResponse.class, form);
    }

}
