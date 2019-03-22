package net.stemmaweb.services;

import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationTypeModel;
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
            if (oldReading.hasProperty(key) && !key.equals("is_lemma")) {
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
     * @return the SEQUENCE Relationship to which the witness was added
     */
    public static Relationship addWitnessLink (Node start, Node end, String sigil, String witClass) {
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
        return link;
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
        // If we are removing a base witness link, we need to check whether any layers for
        // that witness end at our start node or start at our end node.
        ArrayList<String> orphans = new ArrayList<>();
        if (witClass.equals("witnesses")) {
            for (Relationship r : start.getRelationships(Direction.INCOMING, ERelations.SEQUENCE)) {
                orphans.addAll(findWitLayers(r, sigil));
            }
            for (Relationship r : end.getRelationships(Direction.OUTGOING, ERelations.SEQUENCE)) {
                orphans.addAll(findWitLayers(r, sigil));
            }
            // Any outgoing layers of this witness that arrive via another link are not orphans.
            for (Relationship r : end.getRelationships(Direction.INCOMING, ERelations.SEQUENCE))
                if (!r.getStartNode().equals(start))
                    orphans.removeAll(findWitLayers(r, sigil));
        } // else we are removing a layer explicitly, and needn't worry about orphans.

        // Next, go through the outgoing sequences to find the appropriate link. As before, any
        // incoming orphans that leave through a different link aren't really orphans.
        Relationship link = null;
        for (Relationship r : start.getRelationships(Direction.OUTGOING, ERelations.SEQUENCE)) {
            if (r.getEndNode().equals(end))
                link = r;
            else if (witClass.equals("witnesses")) {
                orphans.removeAll(findWitLayers(r, sigil));
            }
        }
        if (link == null) return;
        // Look for the given witness in the given layer
        if (link.hasProperty(witClass)) {
            String[] witList = (String[]) link.getProperty(witClass);
            HashSet<String> currentWits = new HashSet<>(Arrays.asList(witList));
            currentWits.remove(sigil);
            // Un-orphan any otherwise orphaned sigil layers.
            for (String layer : orphans) {
                link.setProperty(layer, new String[] {sigil});
            }
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

    private static ArrayList<String> findWitLayers (Relationship r, String sigil) {
        ArrayList<String> sigLayers = new ArrayList<>();
        for (String layer : r.getPropertyKeys()) {
            if (layer.equals("witnesses")) continue;
            if (Arrays.asList((String[]) r.getProperty(layer)).contains(sigil))
                sigLayers.add(layer);
        }
        return sigLayers;
    }

    /**
     * Transfers all witness links from the given SEQUENCE relationship to whatever sequence
     * exists between the start and end node. Creates the sequence if necessary.
     *
     * @param start - the first node to link
     * @param end   - the second node to link
     * @param copyFrom  - the SEQUENCE relationship whose witnesses to take over
     */
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

        Map<Long, Set<Node>> colocatedNodes;
        boolean recalculateAll;

        // Constructor - we need to know where we are starting so we can build our list of
        // equivalences and find our starting point. Throws an exception if we can't get
        // the related-reading clusters.
        RankCalcEvaluate(Node startNode, Boolean recalculateAll) throws Exception {
            // Get the list of colocated nodes in this section.
            GraphDatabaseService db = startNode.getGraphDatabase();
            String sectionId = String.valueOf(startNode.getProperty("section_id"));
            String tradId = db.getNodeById(Long.valueOf(sectionId))
                    .getSingleRelationship(ERelations.PART, Direction.INCOMING).getStartNode()
                    .getProperty("id").toString();
            this.colocatedNodes = buildColocationLookup(tradId, sectionId, startNode.getGraphDatabase());
            this.recalculateAll = recalculateAll;
        }

        @Override
        public Evaluation evaluate(Path path) {
            // Get the last node on the path, check if its parents are ranked yet, and
            // delete its rank if it is wrong
            Node thisNode = path.endNode();
            Long maxParentRank = maxParentRank(thisNode);
            // If this is the first node in the sequence, then maxParentRank had better not
            // be null. This should also work for the #START# node.
            assert path.lastRelationship() != null || (maxParentRank != null);

            // If maxParentRank is null, we remove our rank and stop calculating from here
            if (maxParentRank == null) {
                thisNode.removeProperty("rank");
                return Evaluation.EXCLUDE_AND_PRUNE;
            }
            // If we aren't recalculating everything, and this isn't the starting node, and
            // the rank isn't changing, we can stop
            if (!recalculateAll && thisNode.hasProperty("rank") && path.lastRelationship() != null
                    && thisNode.getProperty("rank").equals(maxParentRank + 1))
                return Evaluation.EXCLUDE_AND_PRUNE;

            // Otherwise we set the rank and continue.
            thisNode.setProperty("rank", maxParentRank + 1);
            return Evaluation.INCLUDE_AND_CONTINUE;
        }

        private Long maxParentRank (Node candidate) {
            // Returns true if the parents of this node, and of all its colocated nodes,
            // already have a rank.
            Set<Node> toCheck;
            Long maxRankFound = -1L;
            if (colocatedNodes.containsKey(candidate.getId())) {
                toCheck = colocatedNodes.get(candidate.getId());
            } else {
                toCheck = new HashSet<>();
                toCheck.add(candidate);
            }
            for (Node n : toCheck) {
                ArrayList<Node> parents = new ArrayList<>();
                n.getRelationships(ERelations.SEQUENCE, Direction.INCOMING)
                        .forEach(x -> parents.add(x.getStartNode()));
                for (Node p: parents) {
                    if (!p.hasProperty("rank"))
                        return null;
                    Long thisRank = (Long) p.getProperty("rank");
                    maxRankFound = thisRank > maxRankFound ? thisRank : maxRankFound;
                }
            }
            return maxRankFound;
        }
    }

    /**
     * Recalculates ranks, starting from startNode, until the ranks stop changing. Note that
     * the rank on startNode needs to be correct before this is run.
     *
     * @param startNode - the reading from which to begin the recalculation
     * @return list of nodes whose ranks were changed
     * @throws Exception, if the RankCalcEvaluate initialisation fails
     */

    public static Set<Node> recalculateRank (Node startNode, boolean recalculateAll) throws Exception {
        RankCalcEvaluate e = new RankCalcEvaluate(startNode, recalculateAll);
        AlignmentTraverse a = new AlignmentTraverse(startNode);
        GraphDatabaseService db = startNode.getGraphDatabase();
        // If we are recalculating all ranks, we should remove all ranks first
        if (recalculateAll)
            db.traversalDescription().depthFirst()
                    .expand(new AlignmentTraverse())
                    .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL)
                    .traverse(startNode).nodes().stream().forEach(x -> x.removeProperty("rank"));

        // At this point we can start to reassign ranks
        ResourceIterable<Node> changed = db.traversalDescription().depthFirst()
                .expand(a)
                .evaluator(e)
                .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL)
                .traverse(startNode).nodes();
        Set<Node> result = changed.stream().collect(Collectors.toSet());
        // TEMPORARY: Test that our colocated groups are actually colocated
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
        // END TEMPORARY
        return result;
    }

    public static Set<Node> recalculateRank (Node startNode) throws Exception {
        return recalculateRank(startNode, false);
    }

    /**
     * A traversal expander for crawling an alignment, which includes sequence paths
     * as well as colocated relation paths.
     *
     */
    public static class AlignmentTraverse implements PathExpander {

        private Relationship excludeRel = null;
        private HashSet<String> includeRelationTypes = new HashSet<>();

        // Walk the graph of sequences only
        AlignmentTraverse() {}

        // Walk the graph of sequences and colocated relations
        public AlignmentTraverse(Node referenceNode) throws Exception {
            // Get the colocated types for this node's tradition
            List<RelationTypeModel> rtms = RelationService.ourRelationTypes(referenceNode);
            for (RelationTypeModel rtm : rtms)
                if (rtm.getIs_colocation())
                    includeRelationTypes.add(rtm.getName());
        }

        @Override
        public Iterable<Relationship> expand(Path path, BranchState state) {
            return expansion(path, Direction.OUTGOING);
        }

        @Override
        public PathExpander reverse() {
            return new PathExpander() {
                PathExpander parent = this;
                @Override
                public Iterable<Relationship> expand(Path path, BranchState branchState) {
                    return expansion(path, Direction.INCOMING);
                }

                @Override
                public PathExpander reverse() {
                    return parent;
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
                if (includeRelationTypes.contains(r.getProperty("type").toString()) && !r.equals(excludeRel))
                    relevantRelations.add(r);
            }
            return relevantRelations;
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

    /**
     * Checks if both readings can be found in the same path through the
     * tradition. If yes when merging these nodes the graph would get cyclic.
     * NOTE: For use within a transaction@
     *
     * @param firstReading - a node to merge
     * @param secondReading - the node with which to merge it
     * @return - true or false
     */
    public static boolean wouldGetCyclic(Node firstReading, Node secondReading) throws Exception {
        GraphDatabaseService db = firstReading.getGraphDatabase();
        // Get our list of colocations
        Node sectionNode = db.getNodeById(Long.valueOf(firstReading.getProperty("section_id").toString()));
        Node traditionNode = DatabaseService.getTraditionNode(sectionNode, db);
        Map<Long, Set<Node>> colocatedLookup = buildColocationLookup(
                traditionNode.getProperty("id").toString(), String.valueOf(sectionNode.getId()), db);

        // Get the relevant cluster sets
        Set<Node> firstCluster = colocatedLookup.containsKey(firstReading.getId()) ?
                colocatedLookup.get(firstReading.getId()) : new HashSet<>();
        Set<Node> secondCluster = colocatedLookup.containsKey(secondReading.getId()) ?
                colocatedLookup.get(secondReading.getId()) : new HashSet<>();
        firstCluster.add(firstReading);
        secondCluster.add(secondReading);

        // Find our max rank, as well as whether we need to reverse the search
        boolean reverse = false;
        Long maxRank = (Long) firstReading.getProperty("rank");
        if ( maxRank > (Long) secondReading.getProperty("rank"))
            reverse = true;
        else
            maxRank = (Long) secondReading.getProperty("rank");

        // For each node in the lower cluster, see if we can reach any node in the
        // higher cluster.
        AlignmentTraverse alignmentEvaluator = new AlignmentTraverse(firstReading);
        RankEvaluate rankEvaluator = new RankEvaluate(maxRank);
        for (Node lower : reverse ? secondCluster : firstCluster) {
            boolean followed_sequence = false;
            for (Relationship r : db.traversalDescription()
                    .depthFirst()
                    .evaluator(rankEvaluator)
                    .expand(alignmentEvaluator).traverse(lower).relationships()) {
                if (r.getType().name().equals(ERelations.SEQUENCE.name()))
                    followed_sequence = true;
                if ((reverse ? firstCluster : secondCluster).contains(r.getEndNode()) && followed_sequence)
                    return true;
            }
        }

        return false;
    }

    private static Map<Long, Set<Node>> buildColocationLookup (String tradId, String sectionId, GraphDatabaseService db)
            throws Exception {
        Map<Long, Set<Node>> result = new HashMap<>();
        List<Set<Node>> clusters = RelationService.getClusters(tradId, sectionId, db);
        for (Set<Node> cluster : clusters)
            for (Node n : cluster)
                result.put(n.getId(), cluster);

        return result;
    }

}
