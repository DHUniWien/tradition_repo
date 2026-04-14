package net.stemmaweb.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.Traverser;
import org.neo4j.graphdb.traversal.Uniqueness;

import net.stemmaweb.model.AlignmentModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationTypeModel;
import net.stemmaweb.model.WitnessTokensModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;

public class VariantGraphService {

    /**
     * Check whether a given section actually belongs to the given tradition.
     *
     * @param tx         - the transaction within which we are working
     * @param tradId     - The alleged parent tradition
     * @param aSectionId - The section to check
     * @return - true or false
     */
    public static Boolean sectionInTradition(Transaction tx, String tradId, String aSectionId) {
    	Node traditionNode = getTraditionNode(tx, tradId);
    	if (traditionNode == null)
    		return false;
    	
    	boolean found = false;
		for (Node s : DatabaseService.getRelated(traditionNode, ERelations.PART)) {
			if (s.getElementId().equals(aSectionId)) {
				found = true;
				break;
			}
		}
    	return found;
    }
    
    /**
     * Get the start node of a section, or the first section in a tradition
     *
     * @param tx     the transaction within which we are working
     * @param nodeId the ID of the tradition or section whose start node should be returned
     * @return the start node, or null if there is none.
     * NOTE if there are multiple unordered sections, an arbitrary start node may be returned!
     */
    public static Node getStartNode(Transaction tx, String nodeId) {
    	return getBoundaryNode(tx, nodeId, ERelations.COLLATION);
    }
    
    /**
     * Get the end node of a section, or the last section in a tradition
     *
     * @param tx     the transaction within which we are working
     * @param nodeId the ID of the tradition or section whose end node should be returned
     * @return the end node, or null if there is none
     * NOTE if there are multiple unordered sections, an arbitrary end node may be returned!
     */
    public static Node getEndNode(Transaction tx, String nodeId) {
    	return getBoundaryNode(tx, nodeId, ERelations.HAS_END);
    }
    
    private static Node getBoundaryNode(Transaction tx, String nodeId, ERelations direction) {
        Node boundNode = null;
        // If we have been asked for a tradition node, use either the first or the last of
        // its section nodes instead.
        Node currentNode = getTraditionNode(tx, nodeId);
        if (currentNode != null) {
            ArrayList<Node> sections = getSectionNodes(tx, nodeId);
            if (!sections.isEmpty()) {
                Node relevantSection = direction.equals(ERelations.HAS_END)
                        ? sections.getLast()
                        : sections.getFirst();
                return getBoundaryNode(tx, relevantSection.getElementId(), direction);
            } else return null;
        }

        // If we didn't find a tradition node with the ID, assume we wanted a section node.
		currentNode = tx.getNodeByElementId(nodeId);
		if (currentNode != null && currentNode.hasLabel(Nodes.SECTION))
			boundNode = currentNode.getSingleRelationship(direction, Direction.OUTGOING).getEndNode();

		return boundNode;
    }

    /**
     * Return the list of a tradition's sections, ordered by NEXT relationship
     *
     * @param tx     the transaction within which we are working
     * @param tradId the tradition whose sections to return
     * @return a list of sections, which is empty if the tradition doesn't exist
     */
    public static ArrayList<Node> getSectionNodes(Transaction tx, String tradId) {
        Node tradition = getTraditionNode(tx, tradId);
        ArrayList<Node> sectionNodes = new ArrayList<>();
        if (tradition == null)
            return sectionNodes;
        ArrayList<Node> sections = DatabaseService.getRelated(tradition, ERelations.PART);
        int size = sections.size();
        for(Node n: sections) {
            // Look for the node that has no incoming NEXT relationship. That is the first section
            try (ResourceIterator<Relationship> iter = n.getRelationships(Direction.INCOMING, ERelations.NEXT).iterator()) {
                if (!iter.hasNext()) {
                    // Found it; traverse the NEXT chain to get the sections in order.
                    tx.traversalDescription()
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
        }
        return sectionNodes;
    }

    /**
     * Get the node of the specified tradition
     *
     * @param tx     the transaction within which we are working
     * @param tradId the string ID of the tradition we're hunting
     * @return the relevant tradition node
     */
    public static Node getTraditionNode(Transaction tx, String tradId) {
    	Node tradition;
    	tradition = tx.findNode(Nodes.TRADITION, "id", tradId);
    	return tradition;
    }
    
    /**
     * Get the tradition node that the specified section belongs to
     *
     * @param section the section node whose tradition we're hunting
     * @return the relevant tradition node
     */
    public static Node getTraditionNode(Transaction tx, Node section) {
        Node tradition;
    	section = tx.getNodeByElementId(section.getElementId());
        tradition = section.getSingleRelationship(ERelations.PART, Direction.INCOMING).getStartNode();

        return tradition;
    }

    /**
     * Calculate the common readings within a section, either in normalized view or not
     *
     * @param tx - The transaction within which we are working
     * @param sectionNode - The section for which to perform the calculation
     */
    public static void calculateCommon(Transaction tx, Node sectionNode) {
//        GraphDatabaseService db = sectionNode.getGraphDatabase();
        // Get an AlignmentModel for the given section, and go rank by rank to find
        // the common nodes.
        AlignmentModel am = new AlignmentModel(sectionNode, tx);
    	Node startNode = VariantGraphService.getStartNode(tx, sectionNode.getElementId());
        // See which kind of flag we are setting
        String propName = startNode.hasRelationship(Direction.OUTGOING, ERelations.NSEQUENCE) ? "ncommon" : "is_common";
        // Go through the table rank by rank - if a given rank has only a single reading
        // apart from lacunae, and no gaps, it is common
        for (AtomicInteger i = new AtomicInteger(0); i.get() < am.getLength(); i.getAndIncrement()) {
            List<ReadingModel> readingsAtRank = am.getAlignment().stream()
                    .map(x -> x.getTokens().get(i.get())).toList();
            HashSet<String> distinct = new HashSet<>();
            for (ReadingModel rm : readingsAtRank) {
                if (rm == null) distinct.add("");
                else if (!rm.getIs_lacuna()) distinct.add(rm.getId());
            }
            // Set the commonality property. It is true if the size of the 'distinct' set is 1.
            distinct.stream().filter(x -> !x.isEmpty())
                    .forEach(x -> tx.getNodeByElementId(x).setProperty(propName, distinct.size() == 1));
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
     * @param tx            The transaction within which we are working
     * @param sectionNode   The section to be normalized
     * @param normalizeType The (string) name of the type on which we are normalizing
     * @return A HashMap of nodes to their representatives
     * @throws Exception if clusters cannot be got, if the requested relation type doesn't
     *                   exist, or if something goes wrong with the transaction
     */

    public static HashMap<Node,Node> normalizeGraph(Transaction tx, Node sectionNode, String normalizeType) throws Exception {
        HashMap<Node,Node> representatives = new HashMap<>();
        // Make sure the relation type exists
        Node tradition = getTraditionNode(tx, sectionNode);
        Node relType = new RelationTypeModel(normalizeType).lookup(tradition);
        if (relType == null)
            throw new Exception("Relation type " + normalizeType + " does not exist in this tradition");

        Node sectionStart = sectionNode.getSingleRelationship(ERelations.COLLATION, Direction.OUTGOING).getEndNode();
        // Get the list of all readings in this section
		Set<Node> sectionNodes = StreamSupport
				.stream(returnTraditionSection(tx, sectionNode).nodes().spliterator(), false)
				.filter(x -> x.hasLabel(Label.label("READING"))).collect(Collectors.toSet());

        // Find the normalisation clusters and nominate a representative for each
        String tradId = tradition.getProperty("id").toString();
        String sectionId = sectionNode.getElementId();
        for (Set<Node> cluster : RelationService.getCloselyRelatedClusters(
                tx, tradId, sectionId, normalizeType)) {
            if (cluster.isEmpty()) continue;
            Node representative = RelationService.findRepresentative(cluster);
            if (representative == null)
                throw new Exception("No representative found for cluster");
            // Set the representative for all cluster members.
            for (Node n : cluster) {
                representatives.put(n, representative);
                if (!n.equals(representative))
                    representative.createRelationshipTo(n, ERelations.REPRESENTS);
                if (!sectionNodes.remove(n))
                    throw new Exception("Tried to make equivalence for node (" + n.getElementId()
                            + ": " + n.getAllProperties().toString()
                            + ") that was not in sectionNodes");
            }
        }

        // All remaining un-clustered readings are represented by themselves
        sectionNodes.forEach(x -> representatives.put(x, x));

        // Make sure we didn't have any accidental recursion in representation
        for (Node n : representatives.values()) {
            if (n.hasRelationship(Direction.INCOMING, ERelations.REPRESENTS))
                throw new Exception("Recursive representation was created on node " + n.getElementId() + ": " + n.getAllProperties().toString());
        }

        // Now that we have done this, make the shadow sequence
        for (Relationship r : tx.traversalDescription().breadthFirst()
                .relationships(ERelations.SEQUENCE,Direction.OUTGOING)
                .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL).traverse(sectionStart).relationships()) {
            Node repstart = representatives.getOrDefault(r.getStartNode(), r.getStartNode());
            Node repend = representatives.getOrDefault(r.getEndNode(), r.getEndNode());
            ReadingService.transferWitnesses(repstart, repend, r, ERelations.NSEQUENCE);
        }
        // and calculate the common readings.
        calculateCommon(tx, sectionNode);
        return representatives;

    }

    
    /**
     * Return a list of nodes which constitutes the majority text for a section.
     *
     * @param tx          - The transaction within which we are working
     * @param sectionNode - The section to calculate
     * @return an ordered List of READING nodes that make up the majority text
     */
    public static List<Node> calculateMajorityText(Transaction tx, Node sectionNode) {
        // Get the IDs of our majority readings by going through the alignment table rank by rank
        AlignmentModel am = new AlignmentModel(sectionNode, tx);
        ArrayList<String> majorityReadings = new ArrayList<>();
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
                majorityReadings.add(winner.get().getId());
            }
        }

        // Now make the relations between them
        ArrayList<Node> result = new ArrayList<>();
        // Go through the alignment model rank by rank, finding the majority reading for each rank
        String sectionId = sectionNode.getElementId();
        result.add(getStartNode(tx, sectionId));
        majorityReadings.forEach(x -> result.add(tx.getNodeByElementId(x)));
        result.add(getEndNode(tx, sectionId));

        return result;
    }

    /**
     * Collect all annotations, recursively, on the set of nodes that has been passed in.
     *
     * @param nodeSet          - The nodes on which we are collecting annotations
     * @param collectReferents - Whether to recursively collect annotations on annotations
     * @return The annotation nodes that point (ultimately) to the nodes in question
     */
    public static List<Node> collectAnnotationsOnSet(Transaction tx, List<Node> nodeSet, boolean collectReferents) {
        ArrayList<Node> annotationNodes;
        // We want to find all annotation nodes that are linked both to the tradition node
        // and (perhaps indirectly through other annotations) to some node in this set.
        HashSet<Node> foundAnns = new HashSet<>();
        for (Node n : nodeSet) {
            if (collectReferents) {
                Traverser theseAnnotations = returnTraverser(tx, n, nodeAnnotations, PathExpanders.forDirection(Direction.INCOMING));
                theseAnnotations.nodes().forEach(foundAnns::add);
            } else {
                for (Relationship r : n.getRelationships(Direction.INCOMING))
                    if (r.getStartNode().hasRelationship(Direction.INCOMING, ERelations.HAS_ANNOTATION))
                        foundAnns.add(r.getStartNode());
            }
        }
        annotationNodes = new ArrayList<>(foundAnns);
        return annotationNodes;
    }


    /*
     * Tradition and section crawlers, respectively
     */

    // Returns every node pointing outward from a TRADITION.
    private static final Evaluator traditionCrawler = path -> {
        if (path.length() == 0)
            return Evaluation.INCLUDE_AND_CONTINUE;
        if (path.lastRelationship().getType().name().equals(ERelations.OWNS_TRADITION.toString()))
            return Evaluation.EXCLUDE_AND_PRUNE;
        return Evaluation.INCLUDE_AND_CONTINUE;
    };

    // Returns every node pointing outward from a TRADITION, stopping at PART relationships and
    // HAS_ANNOTATION relationships to exclude sections and annotations.
    private static final Evaluator traditionMetaCrawler = path -> {
        if (path.length() == 0)
            return Evaluation.INCLUDE_AND_CONTINUE;
        if (path.lastRelationship().getType().name().equals(ERelations.OWNS_TRADITION.toString()))
            return Evaluation.EXCLUDE_AND_PRUNE;
        // Stop at the sections, inclusively
        if (path.lastRelationship().getStartNode().hasLabel(Nodes.SECTION)) {
            // We want to keep the relationship if it is a NEXT or a PUB_ORDER one, otherwise truncate.
            ArrayList<String> allowed = new ArrayList<>(Arrays.asList("NEXT", "PUB_ORDER"));
            if (allowed.contains(path.lastRelationship().getType().name()))
                return Evaluation.INCLUDE_AND_PRUNE;
            else
                return Evaluation.EXCLUDE_AND_PRUNE;
        }
        // Also exclude any annotations
        if (path.lastRelationship().getType().name().equals(ERelations.HAS_ANNOTATION.toString()))
            return Evaluation.EXCLUDE_AND_PRUNE;
        return Evaluation.INCLUDE_AND_CONTINUE;
    };

    private static final Evaluator sectionCrawler = path -> {
        if (path.length() == 0)
            return Evaluation.INCLUDE_AND_CONTINUE;
        String type = path.lastRelationship().getType().name();
        if (type.equals(ERelations.PART.toString()) || type.equals(ERelations.NEXT.toString())
                || type.equals(ERelations.PUB_ORDER.toString()))
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

    private static final Evaluator nodeAnnotations = path -> {
        // Don't include the node in question
        if (path.length() == 0)
            return Evaluation.EXCLUDE_AND_CONTINUE;
        // Truncate before we get back to the tradition itself
        if (path.lastRelationship().getType().name().equals(ERelations.HAS_ANNOTATION.toString()))
            return Evaluation.EXCLUDE_AND_PRUNE;
        // Do follow through any annotation nodes, identified by the existence of that relationship
        if (path.lastRelationship().getStartNode().hasRelationship(Direction.INCOMING, ERelations.HAS_ANNOTATION))
            return Evaluation.INCLUDE_AND_CONTINUE;
        // Don't follow anything else
        return Evaluation.EXCLUDE_AND_PRUNE;
    };

    private static final Evaluator sequenceLinks = path -> {
        final Set<String> sequenceTypes = new HashSet<>();
        sequenceTypes.add("SEQUENCE");
        sequenceTypes.add("LEMMA_TEXT");
        sequenceTypes.add("NSEQUENCE"); // this really shouldn't be found though
        sequenceTypes.add("MAJORITY");  // nor this
        sequenceTypes.add("EMENDED");
        // Don't include the start node
        if (path.length() == 0)
            return Evaluation.EXCLUDE_AND_CONTINUE;
        Relationship lr = path.lastRelationship();
        // If we are on a tradition node or a sequence node we need to traverse down to the sequence start nodes
        if (lr.getStartNode().hasLabel(Label.label("TRADITION")))
            return lr.getType().name().equals("PART")
                    ? Evaluation.EXCLUDE_AND_CONTINUE : Evaluation.EXCLUDE_AND_PRUNE;
        if (lr.getStartNode().hasLabel(Label.label("SECTION")))
            return lr.getType().name().equals("HAS_COLLATION")
                    ? Evaluation.EXCLUDE_AND_CONTINUE : Evaluation.EXCLUDE_AND_PRUNE;
        // By this point we should have got to the section start. Follow all readings, knowing that emendations
        // are also readings.
        if (lr.getStartNode().hasLabel(Label.label("READING")))
            return sequenceTypes.contains(lr.getType().name())
                    ? Evaluation.INCLUDE_AND_CONTINUE : Evaluation.EXCLUDE_AND_PRUNE;
        // If we are in any other situation, cut it off.
        return Evaluation.EXCLUDE_AND_PRUNE;
    };

    @SuppressWarnings("rawtypes")
    private static Traverser returnTraverser (Transaction tx, Node startNode, Evaluator ev, PathExpander ex) {
        return tx.traversalDescription()
                .depthFirst()
                .expand(ex)
                .evaluator(ev)
                .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL)
                .traverse(startNode);
    }

    /**
     * Return a traverser that includes all nodes and relationships for everything in a tradition.
     *
     * @param tx     the transaction within which we are working
     * @param tradId the string ID of the tradition to crawl
     * @return an org.neo4j.graphdb.traversal.Traverser object for the whole tradition
     */
    public static Traverser returnEntireTradition(Transaction tx, String tradId) {
        return returnEntireTradition(tx, getTraditionNode(tx, tradId));
    }

    /**
     * Return a traverser that includes all nodes and relationships for everything in a tradition.
     *
     * @param tx              the transaction within which we are working
     * @param traditionNode   the Node object of the tradition to crawl
     * @return                an org.neo4j.graphdb.traversal.Traverser object for the whole tradition
     */
    public static Traverser returnEntireTradition(Transaction tx, Node traditionNode) {
        return returnTraverser(tx, traditionNode, traditionCrawler, PathExpanders.forDirection(Direction.OUTGOING));
    }

    /**
     * Return a traverser that includes the meta-information (stemmata, witnesses, annotation types, relation types,
     * annotations on any of the above) for the given tradition.
     *
     * @param tx              the transaction within which we are working
     * @param traditionNode   the Node object of the tradition to crawl
     * @return                an org.neo4j.graphdb.traversal.Traverser object for the whole tradition
     */
    public static Traverser returnTraditionMeta(Transaction tx, Node traditionNode) {
        return returnTraverser(tx, traditionNode, traditionMetaCrawler, PathExpanders.forDirection(Direction.OUTGOING));
    }

    /**
     * Return a traverser that includes all nodes and relationships for a particular section.
     *
     * @param tx        the transaction within which we are working
     * @param sectionId the string ID of the section to crawl
     * @return an org.neo4j.graphdb.traversal.Traverser object for the section
     */
    public static Traverser returnTraditionSection(Transaction tx, String sectionId) {
        Node sectionNode = tx.getNodeByElementId(sectionId);
        return returnTraditionSection(tx, sectionNode);
    }

    /**
     * Return a traverser that includes all nodes and relationships for a particular section.
     *
     * @param tx           the transaction within which we are working
     * @param sectionNode  the Node object of the section to crawl
     * @return             an org.neo4j.graphdb.traversal.Traverser object for the section
     */
    public static Traverser returnTraditionSection(Transaction tx, Node sectionNode) {
        return returnTraverser(tx, sectionNode, sectionCrawler, PathExpanders.forDirection(Direction.OUTGOING));
    }

    /**
     * Return a traverser that includes all RELATED relationships in a tradition.
     *
     * @param tx            the transaction within which we are working
     * @param traditionNode the Node object of the tradition to crawl
     * @return              an org.neo4j.graphdb.traversal.Traverser object containing the relations
     */
    public static Traverser returnTraditionRelations(Transaction tx, Node traditionNode) {
        return returnTraverser(tx, traditionNode, traditionRelations, PathExpanders.allTypesAndDirections());
    }

    /**
     * Return a traverser that includes all sequence-like relations in a tradition or section.
     * It can start from the tradition node, the section node, or the section start node.
     *
     * @param tx        the transaction within which we are working
     * @param startNode the Node object of the tradition or section to crawl
     * @return          an org.neo4j.graphdb.traversal.Traverser object containing the sequences
     */
    public static Traverser returnAllSequences(Transaction tx, Node startNode) {
        return returnTraverser(tx, startNode, sequenceLinks, PathExpanders.forDirection(Direction.OUTGOING));
    }
}
