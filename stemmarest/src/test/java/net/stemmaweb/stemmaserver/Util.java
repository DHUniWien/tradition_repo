package net.stemmaweb.stemmaserver;

import com.alexmerz.graphviz.ParseException;
import com.alexmerz.graphviz.Parser;
import com.alexmerz.graphviz.objects.Edge;
import com.alexmerz.graphviz.objects.Graph;

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

    // TODO add tests to check all the possible stemma creation/edition return paths

    // Helper method
    public static void assertStemmasEquivalent (String expected, String actual) {
        StringBuffer expectedStream = new StringBuffer(expected);
        StringBuffer actualStream = new StringBuffer(actual);

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
}
