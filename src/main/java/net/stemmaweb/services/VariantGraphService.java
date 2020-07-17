package net.stemmaweb.services;

import net.stemmaweb.model.AlignmentModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationTypeModel;
import net.stemmaweb.model.WitnessTokensModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class VariantGraphService {

    /**
     * Check whether a given section actually belongs to the given tradition.
     *
     * @param tradId - The alleged parent tradition
     * @param aSectionId - The section to check
     * @param db - the GraphDatabaseService where the tradition is stored
     * @return - true or false
     */
    public static Boolean sectionInTradition(String tradId, String aSectionId, GraphDatabaseService db) {
        Node traditionNode = getTraditionNode(tradId, db);
        if (traditionNode == null)
            return false;

        boolean found = false;
        try (Transaction tx = db.beginTx()) {
            for (Node s : DatabaseService.getRelated(traditionNode, ERelations.PART)) {
                if (s.getId() == Long.parseLong(aSectionId)) {
                    found = true;
                }
            }
            tx.success();
        }
        return found;
    }

    /**
     * Get the start node of a section, or the first section in a tradition
     *
     * @param nodeId the ID of the tradition or section whose start node should be returned
     * @param db  the GraphDatabaseService where the tradition is stored
     * @return  the start node, or null if there is none.
     *      NOTE if there are multiple unordered sections, an arbitrary start node may be returned!
     */
    public static Node getStartNode(String nodeId, GraphDatabaseService db) {
        return getBoundaryNode(nodeId, db, ERelations.COLLATION);
    }

    /**
     * Get the end node of a section, or the last section in a tradition
     *
     * @param nodeId the ID of the tradition or section whose end node should be returned
     * @param db  the GraphDatabaseService where the tradition is stored
     * @return  the end node, or null if there is none
     *      NOTE if there are multiple unordered sections, an arbitrary end node may be returned!
     */
    public static Node getEndNode(String nodeId, GraphDatabaseService db) {
        return getBoundaryNode(nodeId, db, ERelations.HAS_END);
    }

    private static Node getBoundaryNode(String nodeId, GraphDatabaseService db, ERelations direction) {
        Node boundNode = null;
        // If we have been asked for a tradition node, use either the first or the last of
        // its section nodes instead.
        Node currentNode = getTraditionNode(nodeId, db);
        if (currentNode != null) {
            ArrayList<Node> sections = getSectionNodes(nodeId, db);
            if (sections != null && sections.size() > 0) {
                Node relevantSection = direction.equals(ERelations.HAS_END)
                        ? sections.get(sections.size() - 1)
                        : sections.get(0);
                return getBoundaryNode(String.valueOf(relevantSection.getId()), db, direction);
            } else return null;
        }
        // Were we asked for a nonexistent tradition node (i.e. a non-Long that corresponds to no tradition)?
        long nodeIndex;
        try {
            nodeIndex = Long.parseLong(nodeId);
        } catch (NumberFormatException e) {
            return null;
        }
        // If we are here, we were asked for a section node.
        try (Transaction tx = db.beginTx()) {
            currentNode = db.getNodeById(nodeIndex);
            if (currentNode != null)
                boundNode = currentNode.getSingleRelationship(direction, Direction.OUTGOING).getEndNode();
            tx.success();
        }
        return boundNode;
    }

    /**
     * Return the list of a tradition's sections, ordered by NEXT relationship
     *
     * @param tradId    the tradition whose sections to return
     * @param db        the GraphDatabaseService where the tradition is stored
     * @return          a list of sections, or null if the tradition doesn't exist
     */
    public static ArrayList<Node> getSectionNodes(String tradId, GraphDatabaseService db) {
        Node tradition = getTraditionNode(tradId, db);
        if (tradition == null)
            return null;
        ArrayList<Node> sectionNodes = new ArrayList<>();
        ArrayList<Node> sections = DatabaseService.getRelated(tradition, ERelations.PART);
        int size = sections.size();
        try (Transaction tx = db.beginTx()) {
            for(Node n: sections) {
                if (!n.getRelationships(Direction.INCOMING, ERelations.NEXT)
                        .iterator()
                        .hasNext()) {
                    db.traversalDescription()
                            .depthFirst()
                            .relationships(ERelations.NEXT, Direction.OUTGOING)
                            .evaluator(Evaluators.toDepth(size))
                            .uniqueness(Uniqueness.NODE_GLOBAL)
                            .traverse(n)
                            .nodes()
                            .forEach(sectionNodes::add);
                    break;
                }
            }
            tx.success();
        }
        return sectionNodes;
    }

    /**
     * Get the node of the specified tradition
     *
     * @param tradId  the string ID of the tradition we're hunting
     * @param db      the GraphDatabaseService where the tradition is stored
     * @return        the relevant tradition node
     */
    public static Node getTraditionNode(String tradId, GraphDatabaseService db) {
        Node tradition;
        try (Transaction tx = db.beginTx()) {
            tradition = db.findNode(Nodes.TRADITION, "id", tradId);
            tx.success();
        }
        return tradition;
    }

    /**
     * Get the tradition node that the specified section belongs to
     *
     * @param section  the section node whose tradition we're hunting
     * @return         the relevant tradition node
     */
    public static Node getTraditionNode(Node section) {
        Node tradition;
        GraphDatabaseService db = section.getGraphDatabase();
        try (Transaction tx = db.beginTx()) {
            tradition = section.getSingleRelationship(ERelations.PART, Direction.INCOMING).getStartNode();
            tx.success();
        }
        return tradition;
    }

    /**
     * Calculate the common readings within a section, either in normalized view or not
     *
     * @param sectionNode - The section for which to perform the calculation
     */
    public static void calculateCommon(Node sectionNode) {
        GraphDatabaseService db = sectionNode.getGraphDatabase();
        // Get an AlignmentModel for the given section, and go rank by rank to find
        // the common nodes.
        AlignmentModel am = new AlignmentModel(sectionNode);
        Node startNode = VariantGraphService.getStartNode(String.valueOf(sectionNode.getId()), db);
        try (Transaction tx = db.beginTx()) {
            // See which kind of flag we are setting
            String propName = startNode.hasRelationship(ERelations.NSEQUENCE, Direction.OUTGOING) ? "ncommon" : "is_common";
            // Go through the table rank by rank - if a given rank has only a single reading
            // apart from lacunae, and no gaps, it is common
            for (AtomicInteger i = new AtomicInteger(0); i.get() < am.getLength(); i.getAndIncrement()) {
                List<ReadingModel> readingsAtRank = am.getAlignment().stream()
                        .map(x -> x.getTokens().get(i.get())).collect(Collectors.toList());
                HashSet<Long> distinct = new HashSet<>();
                for (ReadingModel rm : readingsAtRank) {
                    if (rm == null) distinct.add(0L);
                    else if (!rm.getIs_lacuna()) distinct.add(Long.valueOf(rm.getId()));
                }
                // Set the commonality property. It is true if the size of the 'distinct' set is 1.
                distinct.stream().filter(x -> x > 0)
                        .forEach(x -> db.getNodeById(x).setProperty(propName, distinct.size() == 1));
            }
            tx.success();
        }
    }


    /*
     * Methods for calcuating and removing shadow graphs - normalization and majority text
     */

    /**
     * Make a graph normalization sequence on the given section according to the given relation type,
     * creating NSEQUENCE and REPRESENTS relationships between readings where appropriate, and return
     * a map of each section node to its representative node.
     *
     * @param sectionNode     The section to be normalized
     * @param normalizeType   The (string) name of the type on which we are normalizing
     * @return                A HashMap of nodes to their representatives
     *
     * @throws                Exception, if clusters cannot be got, if the requested relation type doesn't
     *                        exist, or if something goes wrong with the transaction
     */

    public static HashMap<Node,Node> normalizeGraph(Node sectionNode, String normalizeType) throws Exception {
        HashMap<Node,Node> representatives = new HashMap<>();
        GraphDatabaseService db = sectionNode.getGraphDatabase();
        // Make sure the relation type exists
        Node tradition = getTraditionNode(sectionNode);
        Node relType = new RelationTypeModel(normalizeType).lookup(tradition);
        if (relType == null)
            throw new Exception("Relation type " + normalizeType + " does not exist in this tradition");

        try (Transaction tx = db.beginTx()) {
            Node sectionStart = sectionNode.getSingleRelationship(ERelations.COLLATION, Direction.OUTGOING).getEndNode();
            // Get the list of all readings in this section
            Set<Node> sectionNodes = returnTraditionSection(sectionNode).nodes().stream()
                    .filter(x -> x.hasLabel(Label.label("READING"))).collect(Collectors.toSet());

            // Find the normalisation clusters and nominate a representative for each
            String tradId = tradition.getProperty("id").toString();
            String sectionId = String.valueOf(sectionNode.getId());
            for (Set<Node> cluster : RelationService.getCloselyRelatedClusters(
                    tradId, sectionId, db, normalizeType)) {
                if (cluster.size() == 0) continue;
                Node representative = RelationService.findRepresentative(cluster);
                if (representative == null)
                    throw new Exception("No representative found for cluster");
                // Set the representative for all cluster members.
                for (Node n : cluster) {
                    representatives.put(n, representative);
                    if (!n.equals(representative))
                        representative.createRelationshipTo(n, ERelations.REPRESENTS);
                    if (!sectionNodes.remove(n))
                        throw new Exception("Tried to make equivalence for node (" + n.getId()
                                + ": " + n.getAllProperties().toString()
                                + ") that was not in sectionNodes");
                }
            }

            // All remaining un-clustered readings are represented by themselves
            sectionNodes.forEach(x -> representatives.put(x, x));

            // Make sure we didn't have any accidental recursion in representation
            for (Node n : representatives.values()) {
                if (n.hasRelationship(ERelations.REPRESENTS, Direction.INCOMING))
                    throw new Exception("Recursive representation was created on node " + n.getId() + ": " + n.getAllProperties().toString());
            }

            // Now that we have done this, make the shadow sequence
            for (Relationship r : db.traversalDescription().breadthFirst()
                    .relationships(ERelations.SEQUENCE,Direction.OUTGOING)
                    .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL).traverse(sectionStart).relationships()) {
                Node repstart = representatives.getOrDefault(r.getStartNode(), r.getStartNode());
                Node repend = representatives.getOrDefault(r.getEndNode(), r.getEndNode());
                ReadingService.transferWitnesses(repstart, repend, r, ERelations.NSEQUENCE);
            }
            // and calculate the common readings.
            calculateCommon(sectionNode);
            tx.success();
        }

        return representatives;

    }

    /**
     * Clean up after performing normalizeGraph. Removes all NSEQUENCE and REPRESENTS relationships within a section.
     *
     * @param sectionNode  the section to clean up
     * @throws             Exception, if anything was missed
     */

    public static void clearNormalization(Node sectionNode) throws Exception {
        GraphDatabaseService db = sectionNode.getGraphDatabase();
        try (Transaction tx = db.beginTx()) {
            Node sectionStartNode = sectionNode.getSingleRelationship(ERelations.COLLATION, Direction.OUTGOING).getEndNode();
            sectionStartNode.removeProperty("ncommon");
            db.traversalDescription().breadthFirst()
                    .relationships(ERelations.NSEQUENCE,Direction.OUTGOING)
                    .relationships(ERelations.REPRESENTS, Direction.OUTGOING)
                    .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL)
                    .traverse(sectionStartNode).relationships()
                    .forEach(x -> {
                        x.getEndNode().removeProperty("ncommon");
                        x.delete();
                    });

            // TEMPORARY: Check that we aren't polluting the graph DB
            if (VariantGraphService.returnTraditionSection(sectionNode).relationships()
                    .stream().anyMatch(x -> x.isType(ERelations.NSEQUENCE) || x.isType(ERelations.REPRESENTS)))
                throw new Exception("Data consistency error on normalization cleanup of section " + sectionNode.getId());
            tx.success();
        }
    }

    /**
     * Return a list of nodes which constitutes the majority text for a section.
     *
     * @param  sectionNode - The section to calculate
     * @return an ordered List of READING nodes that make up the majority text
     */
    public static List<Node> calculateMajorityText(Node sectionNode) {
        // Get the IDs of our majority readings by going through the alignment table rank by rank
        AlignmentModel am = new AlignmentModel(sectionNode);
        ArrayList<Long> majorityReadings = new ArrayList<>();
        for (int rank = 1; rank <= am.getLength(); rank++) {
            int numNulls = 0;
            ArrayList<ReadingModel> rankReadings = new ArrayList<>();
            for (WitnessTokensModel wtm : am.getAlignment()) {
                ReadingModel rdgAtRank = wtm.getTokens().get(rank - 1);
                if (rdgAtRank == null)
                    numNulls++;
                else
                    rankReadings.add(rdgAtRank);
            }
            // Now find the winner
            Optional<ReadingModel> winner = rankReadings.stream().max(Comparator.comparingInt(x -> x.getWitnesses().size()));
            if (winner.isPresent() && winner.get().getWitnesses().size() >= numNulls) {
                majorityReadings.add(Long.valueOf(winner.get().getId()));
            }
        }

        // Now make the relations between them
        GraphDatabaseService db = sectionNode.getGraphDatabase();
        ArrayList<Node> result = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            // Go through the alignment model rank by rank, finding the majority reading for each rank
            String sectionId = String.valueOf(sectionNode.getId());
            result.add(getStartNode(sectionId, db));
            majorityReadings.forEach(x -> result.add(db.getNodeById(x)));
            result.add(getEndNode(sectionId, db));
            tx.success();
        }
        return result;
    }

    /*
     * Tradition and section crawlers, respectively
     */

    private static final Evaluator traditionCrawler = path -> {
        if (path.length() == 0)
            return Evaluation.INCLUDE_AND_CONTINUE;
        if (path.lastRelationship().getType().name().equals(ERelations.OWNS_TRADITION.toString()))
            return Evaluation.EXCLUDE_AND_PRUNE;
        return Evaluation.INCLUDE_AND_CONTINUE;
    };

    private static final Evaluator sectionCrawler = path -> {
        if (path.length() == 0)
            return Evaluation.INCLUDE_AND_CONTINUE;
        String type = path.lastRelationship().getType().name();
        if (type.equals(ERelations.PART.toString()) || type.equals(ERelations.NEXT.toString()))
            return Evaluation.EXCLUDE_AND_PRUNE;
        return Evaluation.INCLUDE_AND_CONTINUE;
    };

    private static final Evaluator traditionRelations = path -> {
        if (path.length() == 0)
            return Evaluation.INCLUDE_AND_CONTINUE;
        if (path.lastRelationship().getType().name().equals(ERelations.OWNS_TRADITION.toString()))
            return Evaluation.EXCLUDE_AND_PRUNE;
        if (path.lastRelationship().getType().name().equals(ERelations.RELATED.toString()))
            return Evaluation.INCLUDE_AND_CONTINUE;
        return Evaluation.EXCLUDE_AND_CONTINUE;
    };

    @SuppressWarnings("rawtypes")
    private static Traverser returnTraverser (Node startNode, Evaluator ev, PathExpander ex) {
        Traverser tv;
        GraphDatabaseService db = startNode.getGraphDatabase();
        try (Transaction tx = db.beginTx()) {
            tv = db.traversalDescription()
                    .depthFirst()
                    .expand(ex)
                    .evaluator(ev)
                    .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL)
                    .traverse(startNode);
            tx.success();
        }
        return tv;
    }

    /**
     * Return a traverser that includes all nodes and relationships for everything in a tradition.
     *
     * @param tradId  the string ID of the tradition to crawl
     * @param db      the relevant GraphDatabaseService
     * @return        an org.neo4j.graphdb.traversal.Traverser object for the whole tradition
     */
    public static Traverser returnEntireTradition(String tradId, GraphDatabaseService db) {
        return returnEntireTradition(getTraditionNode(tradId, db));
    }

    /**
     * Return a traverser that includes all nodes and relationships for everything in a tradition.
     *
     * @param traditionNode   the Node object of the tradition to crawl
     * @return                an org.neo4j.graphdb.traversal.Traverser object for the whole tradition
     */
    public static Traverser returnEntireTradition(Node traditionNode) {
        return returnTraverser(traditionNode, traditionCrawler, PathExpanders.forDirection(Direction.OUTGOING));
    }

    /**
     * Return a traverser that includes all nodes and relationships for a particular section.
     *
     * @param sectionId  the string ID of the section to crawl
     * @param db         the relevant GraphDatabaseService
     * @return           an org.neo4j.graphdb.traversal.Traverser object for the section
     */
    public static Traverser returnTraditionSection(String sectionId, GraphDatabaseService db) {
        Traverser tv;
        try (Transaction tx = db.beginTx()) {
            Node sectionNode = db.getNodeById(Long.parseLong(sectionId));
            tv = returnTraditionSection(sectionNode);
            tx.success();
        }
        return tv;
    }

    /**
     * Return a traverser that includes all nodes and relationships for a particular section.
     *
     * @param sectionNode  the Node object of the section to crawl
     * @return             an org.neo4j.graphdb.traversal.Traverser object for the section
     */
    public static Traverser returnTraditionSection(Node sectionNode) {
        return returnTraverser(sectionNode, sectionCrawler, PathExpanders.forDirection(Direction.OUTGOING));
    }

    /**
     * Return a traverser that includes all RELATED relationships in a tradition.
     *
     * @param traditionNode the Node object of the tradition to crawl
     * @return             an org.neo4j.graphdb.traversal.Traverser object containing the relations
     */
    public static Traverser returnTraditionRelations(Node traditionNode) {
        return returnTraverser(traditionNode, traditionRelations, PathExpanders.allTypesAndDirections());
    }
}
