package net.stemmaweb.services;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.*;

import java.util.ArrayList;

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
     * @param oldReading - the reading to copy from
     * @param newReading - the reading to copy to
     * @return the modified new reading
     */
    public static Node copyReadingProperties(Node oldReading, Node newReading) {
        for (String key : oldReading.getPropertyKeys()) {
            if (oldReading.hasProperty(key)) {
                newReading.setProperty(key, oldReading.getProperty(key));
            }
        }
        newReading.addLabel(Nodes.READING);
        return newReading;
    }


    /* Custom evaluation and expander for checking alignment traversals */

    private static class RankEvaluate implements Evaluator {

        private Long rank;

        RankEvaluate(Long stoprank) {
            rank = stoprank;
        }

        @Override
        public Evaluation evaluate(Path path) {
            Node testNode = path.startNode();
            if (testNode.hasProperty("rank")
                    && testNode.getProperty("rank").equals(rank)) {
                return Evaluation.INCLUDE_AND_PRUNE;
            } else {
                return Evaluation.INCLUDE_AND_CONTINUE;
            }
        }
    }

    private static class AlignmentTraverse implements PathExpander {

        @Override
        public Iterable<Relationship> expand(Path path, BranchState state) {
            return expansion(path, Direction.OUTGOING);
        }

        @Override
        public PathExpander reverse() {
            return new PathExpander() {
                @Override
                public Iterable<Relationship> expand(Path path, BranchState branchState) {
                    return expansion(path, Direction.INCOMING);
                }

                @Override
                public PathExpander reverse() {
                    return new AlignmentTraverse();
                }
            };
        }

        private Iterable<Relationship> expansion(Path path, Direction dir) {
            ArrayList<Relationship> relevantRelations = new ArrayList<>();
            // Get the sequence relationships
            for (Relationship relationship : path.endNode().getRelationships(dir, ERelations.SEQUENCE))
                relevantRelations.add(relationship);
            // Get the alignment relationships and filter them
            for (Relationship r : path.endNode().getRelationships(Direction.BOTH, ERelations.RELATED)) {
                if (r.hasProperty("type") &&
                        !r.getProperty("type").equals("transposition") &&
                        !r.getProperty("type").equals("repetition")) {
                    relevantRelations.add(r);
                }
            }
            return relevantRelations;
        }
    }

    /**
     * Checks if both readings can be found in the same path through the
     * tradition. If yes when merging these nodes the graph would get cyclic.
     * NOTE: For use within a transaction@
     *
     * @param firstReading - a node to merge
     * @param secondReading - the node with which to merge it
     * @return - true or false
     */
    public static boolean wouldGetCyclic(Node firstReading,
                                         Node secondReading) {
        GraphDatabaseService db = firstReading.getGraphDatabase();
        Node lowerRankReading, higherRankReading;
        if ((Long) firstReading.getProperty("rank") < (Long) secondReading.getProperty("rank")) {
            lowerRankReading = firstReading;
            higherRankReading = secondReading;
        } else {
            lowerRankReading = secondReading;
            higherRankReading = firstReading;
        }

        // check if higherRankReading is found in one of the paths, but don't crawl the graph beyond
        // that reading's rank.
        AlignmentTraverse alignmentEvaluator = new AlignmentTraverse();
        RankEvaluate rankEvaluator = new RankEvaluate((Long) higherRankReading.getProperty("rank"));
        for (Node node : db.traversalDescription()
                .depthFirst()
                .evaluator(rankEvaluator)
                .expand(alignmentEvaluator)
                .uniqueness(Uniqueness.RELATIONSHIP_PATH)
                .evaluator(Evaluators.all())
                .traverse(lowerRankReading).nodes()) {
            if (node.equals(higherRankReading)) {
                return true;
            }
        }

        return false;
    }

}
