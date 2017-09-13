package net.stemmaweb.services;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

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

    /**
     * Add a witness of the given class (either "witnesses" or some layer) connecting the
     * two nodes given.
     * NOTE: for use in a transaction!
     *
     * @param start - the start node
     * @param end   - the end node
     * @param sigil - the witness sigil
     * @param witClass - the witness layer class to use
     */
    public static void addWitnessLink (Node start, Node end, String sigil, String witClass) {
        Relationship link = null;
        for (Relationship r : start.getRelationships(Direction.OUTGOING, ERelations.SEQUENCE))
            if (r.getEndNode().equals(end))
                link = r;
        if (link == null)
            link = start.createRelationshipTo(end, ERelations.SEQUENCE);
        if (link.hasProperty(witClass)) {
            String[] witList = (String[]) link.getProperty(witClass);
            HashSet<String> currentWits = new HashSet<>(Arrays.asList(witList));
            currentWits.add(sigil);
            link.setProperty(witClass, currentWits.toArray(new String[currentWits.size()]));
        } else {
            String[] witList = {sigil};
            link.setProperty(witClass, witList);
        }
    }



    /**
     * Removes a reading from the sequence, matching up links on either side of it.
     * Meant for merging sections and removing placeholder nodes.
     * NOTE: To be used inside a transaction!
     *
     * @param placeholderNode - the node to be removed
     */
    public static void removePlaceholder(Node placeholderNode) throws Exception {
        // Check that the node is indeed a placeholder
        if (!placeholderNode.hasProperty("is_placeholder")
                || !(Boolean) placeholderNode.getProperty("is_placeholder"))
            throw new Exception("Cannot remove a non-placeholder node!");

        // Make a map of class -> witness -> node on the outgoing side
        HashMap<String, Node> readingWitnessToMap = new HashMap<>();
        HashMap<String, HashMap<String, Node>> readingWitnessExtraMap = new HashMap<>();
        for (Relationship r : placeholderNode.getRelationships(Direction.OUTGOING, ERelations.SEQUENCE)) {
            for (String prop : r.getPropertyKeys()) {
                String[] relWits = (String[]) r.getProperty(prop);
                for (String w : relWits)
                    if (prop.equals("witnesses"))
                        readingWitnessToMap.put(w, r.getEndNode());
                    else if (readingWitnessExtraMap.containsKey(w))
                        readingWitnessExtraMap.get(w).put(prop, r.getEndNode());
                    else {
                        HashMap<String, Node> thisMapping = new HashMap<>();
                        thisMapping.put(prop, r.getEndNode());
                        readingWitnessExtraMap.put(w, thisMapping);
                    }
            }
            r.delete();
        }

        // Go through relationships on the incoming side, re-routing them according to the map above.
        // Keep a list of layer readings on the incoming side for matching after the fact.
        HashMap<String, Node> deferredLinks = new HashMap<>();
        for (Relationship r : placeholderNode.getRelationships(Direction.INCOMING, ERelations.SEQUENCE)) {
            Node priorReading = r.getStartNode();
            for (String prop : r.getPropertyKeys()) {
                String[] relWits = (String[]) r.getProperty(prop);
                for (String w : relWits) {
                    if (prop.equals("witnesses")) {
                        addWitnessLink(priorReading, readingWitnessToMap.get(w), w, prop);

                        // If there are special (extra, layered) readings for this witness on the
                        // TO side, we will have to deal with it after we have matched corresponding
                        // special sequence links on the FROM side, which will occur in other
                        // Relationship objects.
                        if (readingWitnessExtraMap.containsKey(w))
                            deferredLinks.put(w, priorReading);
                    } else {
                        // Look for a matching layer-witness reading for our layer-witness
                        if (readingWitnessExtraMap.containsKey(w)
                                && readingWitnessExtraMap.get(w).containsKey(prop)) {
                            addWitnessLink(priorReading, readingWitnessExtraMap.get(w).get(prop), w, prop);
                            // This witness layer has been matched; remove it from later accounting.
                            readingWitnessExtraMap.get(w).remove(prop);
                        }
                        // If there isn't a match, use the "normal" witness reading on the TO side
                        else
                            addWitnessLink(priorReading, readingWitnessToMap.get(w), w, prop);
                    }
                }
            }
            r.delete();
        }
        // Deal with whatever remains in the readingWitnessExtraMap, that hasn't been linked.
        for (String w : readingWitnessExtraMap.keySet()) {
            HashMap<String, Node> thisToExtra = readingWitnessExtraMap.get(w);
            for (String extra : thisToExtra.keySet()) {
                Node priorNode = deferredLinks.get(w);
                addWitnessLink(priorNode, thisToExtra.get(extra), w, extra);
            }
        }
    }


    /* Custom evaluation and expander for checking alignment traversals */

    private static class RankEvaluate implements Evaluator {

        private Long rank;

        RankEvaluate(Long stoprank) {
            rank = stoprank;
        }

        @Override
        public Evaluation evaluate(Path path) {
            if (path.endNode().equals(path.startNode()))
                return Evaluation.INCLUDE_AND_CONTINUE;
            Node testNode = path.lastRelationship().getStartNode();
            if (testNode.hasProperty("rank")
                    && (Long) testNode.getProperty("rank") >= rank) {
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
                // .evaluator(Evaluators.all())
                .traverse(lowerRankReading).nodes()) {
            if (node.equals(higherRankReading)) {
                return true;
            }
        }

        return false;
    }

}
