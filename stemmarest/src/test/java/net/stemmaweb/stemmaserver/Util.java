package net.stemmaweb.stemmaserver;

import com.alexmerz.graphviz.ParseException;
import com.alexmerz.graphviz.Parser;
import com.alexmerz.graphviz.objects.Graph;

import java.util.ArrayList;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by tla on 06/10/15.
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

        try {
            p.parse(actualStream);
        } catch (ParseException e) {
            assertTrue(false);
        }
        ArrayList<Graph> actualGraphs = p.getGraphs();

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
            HashSet<String> expectedEdges = new HashSet<>();
            expectedStemma.getEdges().forEach(e -> expectedNodes.add(
                    e.getSource().getNode().getId().getId() + " - " +
                            e.getTarget().getNode().getId().getId()));
            HashSet<String> actualEdges = new HashSet<>();
            actualStemma.getEdges().forEach(e -> actualNodes.add(
                    e.getSource().getNode().getId().getId() + " - " +
                            e.getTarget().getNode().getId().getId()));
            assertEquals(expectedEdges.size(), actualEdges.size());
            i++;
        }
    }
}
