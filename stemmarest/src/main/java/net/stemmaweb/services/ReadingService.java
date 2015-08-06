package net.stemmaweb.services;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.Uniqueness;

/**
 * 
 * Provides helper methods related to readings.
 * 
 * @author PSE FS 2015 Team2
 *
 */
public class ReadingService {

    /**
     * Copies all the properties of a reading to another if the property exists.
     *
     * @param oldReading
     * @param newReading
     * @return
     */
    public static Node copyReadingProperties(Node oldReading, Node newReading) {
        for (String key : oldReading.getPropertyKeys()) {
            if (oldReading.hasProperty(key)) {
                newReading.setProperty(key, oldReading.getProperty(key));
            }
        }
        newReading.addLabel(Nodes.WORD);
        return newReading;
    }

    /**
     * Checks if both readings can be found in the same path through the
     * tradition. If yes when merging these nodes the graph would get cyclic.
     *
     * @param db
     * @param firstReading
     * @param secondReading
     * @return
     */
    public static boolean wouldGetCyclic(GraphDatabaseService db,
                                         Node firstReading,
                                         Node secondReading) {
        Node lowerRankReading, higherRankReading;
        if ((Long) firstReading.getProperty("rank") < (Long) secondReading.getProperty("rank")) {
            lowerRankReading = firstReading;
            higherRankReading = secondReading;
        } else {
            lowerRankReading = secondReading;
            higherRankReading = firstReading;
        }

        // check if higherRankReading is found in one of the paths
        for (Node node : db.traversalDescription()
                .depthFirst()
                .relationships(ERelations.NORMAL, Direction.OUTGOING)
                .uniqueness(Uniqueness.NONE)
                .evaluator(Evaluators.all())
                .traverse(lowerRankReading).nodes()) {
            if (node.equals(higherRankReading)) {
                return true;
            }
        }

        return false;
    }
}
