package net.stemmaweb.services;

import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.*;

import java.util.*;
import java.util.stream.Collectors;

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
     */
    public static void copyReadingProperties(Node oldReading, Node newReading) {
        for (String key : oldReading.getPropertyKeys()) {
            if (oldReading.hasProperty(key)) {
                newReading.setProperty(key, oldReading.getProperty(key));
            }
        }
        newReading.addLabel(Nodes.READING);
    }

    /**
     * Returns true if the specified witness (layer) is in the relationship.
     *
     * @param link - the SEQUENCE relationship to test
     * @param sigil - the witness sigil
     * @param witClass - the witness layer
     *
     */
    @SuppressWarnings("WeakerAccess")
    public static boolean hasWitness(Relationship link, String sigil, String witClass) {
        if (link.hasProperty(witClass)) {
            String[] wits = (String[]) link.getProperty(witClass);
            return Arrays.asList(wits).contains(sigil);
        }
        return false;
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
        // First see if we need to add this one
        if (witClass.equals("witnesses") || !hasWitness(link, sigil, "witnesses")) {
            // This is either a main witness or a layer witness where the main witness isn't.
            if (link.hasProperty(witClass)) {
                String[] witList = (String[]) link.getProperty(witClass);
                HashSet<String> currentWits = new HashSet<>(Arrays.asList(witList));
                currentWits.add(sigil);
                link.setProperty(witClass, currentWits.toArray(new String[0]));
            } else {
                String[] witList = {sigil};
                link.setProperty(witClass, witList);
            }
        }
        // Then see if we need to remove a layer
        if (witClass.equals("witnesses")) {
            for (String wc : link.getPropertyKeys()) {
                if (wc.equals(witClass)) continue;
                removeWitnessLink(start, end, sigil, wc);
            }
        }
    }

    /**
     * Remove a witness of the given class (either "witnesses" or some layer) from the connection
     * between the two nodes given.
     * NOTE: for use in a transaction!
     *
     * @param start - the start node
     * @param end   - the end node
     * @param sigil - the witness sigil
     * @param witClass - the witness layer class to use
     */
    public static void removeWitnessLink (Node start, Node end, String sigil, String witClass) {
        Relationship link = null;
        for (Relationship r : start.getRelationships(Direction.OUTGOING, ERelations.SEQUENCE))
            if (r.getEndNode().equals(end))
                link = r;
        if (link == null) return;
        // Look for the given witness in the given layer
        if (link.hasProperty(witClass)) {
            String[] witList = (String[]) link.getProperty(witClass);
            HashSet<String> currentWits = new HashSet<>(Arrays.asList(witList));
            currentWits.remove(sigil);
            // Was this the last witness for the given class?
            if (currentWits.isEmpty()) {
                link.removeProperty(witClass);
                // Was this the last witness class for the given link?
                if (link.getAllProperties().size() == 0)
                    link.delete();
            }
            else
                link.setProperty(witClass, currentWits.toArray(new String[0]));
        }
    }

    public static void transferWitnesses (Node start, Node end, Relationship copyFrom) {
        for (String witclass : copyFrom.getPropertyKeys())
            for (String w : (String[]) copyFrom.getProperty(witclass))
                addWitnessLink(start, end, w, witclass);
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

    public static String textOfReadings(List<ReadingModel> rml, Boolean normal) {
        StringBuilder witnessAsText = new StringBuilder();
        Boolean joinNext = false;
        for (ReadingModel rm : rml) {
            if (rm.getIs_end()) continue;
            if (rm.getIs_lacuna()) continue;
            if (!joinNext && !rm.getJoin_prior()
                    && !witnessAsText.toString().equals("") && !witnessAsText.toString().endsWith(" "))
                witnessAsText.append(" ");
            joinNext = rm.getJoin_next();
            String joinString = normal ? rm.normalized() : rm.getText();
            // if (joinString == null) joinString = "";
            witnessAsText.append(joinString);
        }
        return witnessAsText.toString().trim();
    }

    /* Custom evaluator and traverser that modifies reading rank as it goes */

    private static class RankCalcEvaluate implements Evaluator {

        HashSet<Node> visited = new HashSet<>();
        Map<Long, Set<Node>> colocatedNodes = new HashMap<>();
        Long initialRank = 0L;

        // Constructor - we need to know where we are starting so we can build our list of
        // equivalences and find our starting point.
        RankCalcEvaluate(Node startNode) {
            // Get the list of colocated nodes in this section.
            GraphDatabaseService db = startNode.getGraphDatabase();
            String sectionId = String.valueOf(startNode.getProperty("section_id"));
            String tradId = db.getNodeById(Long.valueOf(sectionId))
                    .getSingleRelationship(ERelations.PART, Direction.INCOMING).getStartNode()
                    .getProperty("id").toString();
            List<Set<Node>> clusters = RelationService.getClusters(sectionId, tradId, startNode.getGraphDatabase());
            assert(clusters != null);
            for (Set<Node> cluster : clusters)
                for (Node n : cluster)
                    colocatedNodes.put(n.getId(), cluster);
            // Figure out what the initial rank needs to be for this starting node - the max of prior node ranks
            startNode.getRelationships(ERelations.SEQUENCE, Direction.INCOMING).forEach(x -> {
                Long r = (Long) x.getStartNode().getProperty("rank", 0L);
                if (r >= initialRank) initialRank = r+1;
            });
        }

        @Override
        public Evaluation evaluate(Path path) {
            // Get the last node on the path and see if all its parents have been ranked yet
            Node thisNode = path.endNode();
            Long thisRank = (Long) thisNode.getProperty("rank");
            Long minRank;

            // If we are here from a sequence relationship, note what our minimum rank must be
            visited.add(thisNode);
            if (path.lastRelationship() == null)
                return Evaluation.EXCLUDE_AND_CONTINUE;
            else if (path.lastRelationship().getType().toString().equals("RELATED")
                    && visited.contains(path.lastRelationship().getStartNode()))
                minRank = (Long) path.lastRelationship().getStartNode().getProperty("rank");
            else
                minRank = (Long) path.lastRelationship().getStartNode().getProperty("rank") + 1;

            // The rank must also be max of colocated readings that have been visited
            Set<Node> colocated = colocatedNodes.get(thisNode.getId());
            if (colocated.size() > 0) {
                // maxRank is the highest rank of all the nodes so far visited, and should be adjusted
                // for all colocated nodes
                Long maxRank;
                List<Long> l = colocated.stream().filter(x -> visited.contains(x))
                        .map(x -> (Long) x.getProperty("rank")).collect(Collectors.toList());
                maxRank = l.isEmpty() ? minRank : Collections.max(l);

                if (maxRank > minRank) {
                    minRank = maxRank;
                    final long mr = minRank;
                    colocated.forEach(x -> x.setProperty("rank", mr));
                }
            }
            if (thisRank.equals(minRank))
                return Evaluation.EXCLUDE_AND_PRUNE;
            else {
                thisNode.setProperty("rank", minRank);
                return Evaluation.INCLUDE_AND_CONTINUE;
            }
        }
    }

    /**
     * Recalculates ranks, starting from startNode, until the ranks stop changing. Note that
     * the rank on startNode needs to be correct before this is run.
     *
     * @param startNode - the reading from which to begin the recalculation
     * @return list of nodes whose ranks were changed
     */
    public static List<Node> recalculateRank (Node startNode) {
        RankCalcEvaluate e = new RankCalcEvaluate(startNode);
        AlignmentTraverse a = new AlignmentTraverse();
        GraphDatabaseService db = startNode.getGraphDatabase();
        ResourceIterable<Node> changed = db.traversalDescription().breadthFirst()
                .expand(a)
                .evaluator(e)
                .uniqueness(Uniqueness.RELATIONSHIP_PATH)
                .traverse(startNode).nodes();
        // Test that our colocated groups are actually colocated
        Node ourSection = db.getNodeById((Long) startNode.getProperty("section_id"));
        String tradId = DatabaseService.getTraditionNode(ourSection, db).getProperty("id").toString();
        List<Set<Node>> clusters = RelationService.getClusters(tradId, String.valueOf(ourSection.getId()), db);
        assert(clusters != null);
        for (Set<Node> cluster : clusters) {
            Long clusterRank = null;
            for (Node n : cluster) {
                if (clusterRank == null)
                    clusterRank = (Long) n.getProperty("rank");
                else
                    assert(clusterRank.equals(n.getProperty("rank")));
            }
        }
        return changed.stream().collect(Collectors.toList());
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

    public static class AlignmentTraverse implements PathExpander {

        private Relationship excludeRel = null;

        public AlignmentTraverse() {}
        AlignmentTraverse(Relationship excludeRel) { this.excludeRel = excludeRel; }

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
            for (Relationship relationship : path.endNode()
                    .getRelationships(dir, ERelations.SEQUENCE, ERelations.LEMMA_TEXT))
                relevantRelations.add(relationship);
            // Get the alignment relationships and filter them
            for (Relationship r : path.endNode().getRelationships(Direction.BOTH, ERelations.RELATED)) {
                if (!r.equals(excludeRel) && r.hasProperty("colocation") &&
                        r.getProperty("colocation").equals(true)) {
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
        // that reading's rank. Also disregard any relation already existing between the two nodes.
        Relationship existingRel = null;
        for (Relationship r : firstReading.getRelationships(ERelations.RELATED)) {
            if (r.getOtherNode(firstReading).equals(secondReading))
                existingRel = r;
        }
        AlignmentTraverse alignmentEvaluator = new AlignmentTraverse(existingRel);
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
