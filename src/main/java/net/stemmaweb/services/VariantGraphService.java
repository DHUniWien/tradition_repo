package net.stemmaweb.services;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.InitialBranchState;
import org.neo4j.graphdb.traversal.Traverser;
import org.neo4j.graphdb.traversal.Uniqueness;

import net.stemmaweb.model.AlignmentModel;
import net.stemmaweb.model.GraphModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationModel;
import net.stemmaweb.model.RelationTypeModel;
import net.stemmaweb.model.SequenceModel;
import net.stemmaweb.model.WitnessTokensModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.ReadingService.AlignmentTraverse;

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
                for (Relationship r : DatabaseService.getRelationships(n, Direction.INCOMING))
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


    /*
     * Methods extracted from Section for reuse by services and parsers
     */

    /**
     * Collect all witness nodes that appear in the given section of the given tradition.
     *
     * @param tx     the transaction within which we are working
     * @param tradId the tradition ID
     * @param sectId the section ID
     * @return a list of witness nodes
     */
    public static ArrayList<Node> collectSectionWitnesses(Transaction tx, String tradId, String sectId) {
        HashSet<Node> witnessList = new HashSet<>();
        Node traditionNode = getTraditionNode(tx, tradId);
        Node sectionStart = getStartNode(tx, sectId);
        ArrayList<Node> traditionWitnesses = DatabaseService.getRelated(traditionNode, ERelations.HAS_WITNESS);
        for (Relationship relationship : DatabaseService.getRelationships(sectionStart, ERelations.SEQUENCE))
            for (String witClass : relationship.getPropertyKeys())
                for (String sigil : (String[]) relationship.getProperty(witClass))
                    for (Node curWitness : traditionWitnesses)
                        if (sigil.equals(curWitness.getProperty("sigil"))) {
                            witnessList.add(curWitness);
                            traditionWitnesses.remove(curWitness);
                            break;
                        }
        return new ArrayList<>(witnessList);
    }

    /**
     * Collect all relations in a section.
     *
     * @param tx     the transaction within which we are working
     * @param sectId the section ID
     * @return a list of RelationModel objects
     */
    public static ArrayList<RelationModel> sectionRelations(Transaction tx, String sectId) {
        return sectionRelations(tx, sectId, false);
    }

    /**
     * Collect all relations in a section, optionally including full reading information.
     *
     * @param tx              the transaction within which we are working
     * @param sectId          the section ID
     * @param includeReadings whether to include reading data in the relation models
     * @return a list of RelationModel objects
     */
    public static ArrayList<RelationModel> sectionRelations(Transaction tx, String sectId, Boolean includeReadings) {
        ArrayList<RelationModel> relList = new ArrayList<>();

    	Node startNode = getStartNode(tx, sectId);
        tx.traversalDescription().depthFirst()
                .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                .uniqueness(Uniqueness.NODE_GLOBAL)
                .traverse(startNode).nodes().forEach(
                n -> DatabaseService.getRelationships(n, Direction.OUTGOING, ERelations.RELATED).forEach(
                        r -> relList.add(new RelationModel(r, includeReadings)))
        );

        return relList;
    }

    /**
     * Return ReadingModel objects for all the readings in a section, in traversal order.
     *
     * @param tx     - the transaction within which we are working
     * @param sectId - the ID of the section whose readings we are collecting
     * @return the list of its reading models
     * @throws Exception if the section has no start node
     */
    public static List<ReadingModel> sectionReadings(Transaction tx, String sectId) throws Exception {
        ArrayList<ReadingModel> readingModels = new ArrayList<>();
        Node startNode = VariantGraphService.getStartNode(tx, sectId);
        if (startNode == null) throw new Exception("Section " + sectId + " has no start node");
        tx.traversalDescription().depthFirst()
                .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                .relationships(ERelations.EMENDED, Direction.OUTGOING)
                .evaluator(Evaluators.all())
                .uniqueness(Uniqueness.NODE_GLOBAL).traverse(startNode)
                .nodes().forEach(node -> readingModels.add(new ReadingModel(node)));

        return readingModels;
    }

    /**
     * Get all readings which have the same text and the same rank, between the given ranks.
     *
     * @param tx        the transaction within which we are working
     * @param sectId    the section ID
     * @param startRank the rank from where to start the search
     * @param endRank   the rank at which to end the search
     * @return a list of lists of identical readings, or null if none found
     */
    public static ArrayList<List<ReadingModel>> collectIdenticalReadings(
            Transaction tx, String sectId, long startRank, long endRank)
            throws IllegalArgumentException {
        Node startNode = getStartNode(tx, sectId);
        if (startNode == null)
            throw new IllegalArgumentException("No section with ID " + sectId);

        ArrayList<ReadingModel> readingModels =
                getAllReadingsFromSectionBetweenRanks(startNode, startRank, endRank, tx);
        ArrayList<List<ReadingModel>> identicalReadings = identifyIdenticalReadings(readingModels, startRank, endRank);
        return identicalReadings.stream().filter(x -> !x.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    // Retrieve all readings of a tradition between two ranks as ReadingModels
    private static ArrayList<ReadingModel> getAllReadingsFromSectionBetweenRanks(
            Node startNode, long startRank, long endRank, Transaction tx) {
        ArrayList<ReadingModel> readingModels = new ArrayList<>();
        getReadingsBetweenRanks(startRank, endRank, startNode, "", tx)
                .forEach(x -> readingModels.add(new ReadingModel(x)));
        readingModels.sort(Comparator.comparing(ReadingModel::getRank));
        return readingModels;
    }

    // Gets identical readings in a list of ReadingModels sorted by rank.
    private static ArrayList<List<ReadingModel>> identifyIdenticalReadings(
            ArrayList<ReadingModel> readingModels, long startRank, long endRank) {
        ArrayList<List<ReadingModel>> identicalReadingsList = new ArrayList<>();

        HashMap<String, List<ReadingModel>> rankSet = new HashMap<>();
        for (ReadingModel rm : readingModels) {
            String normReading = Normalizer.normalize(rm.getText(), Normalizer.Form.NFC);
            if (rm.getRank() > endRank)
                break;
            if (rm.getRank() > startRank) {
                for (String k : rankSet.keySet())
                    if (rankSet.get(k).size() > 1)
                        identicalReadingsList.add(rankSet.get(k));
                rankSet.clear();
                rankSet.put(normReading, new ArrayList<>(Collections.singletonList(rm)));
                startRank = rm.getRank();
            }
            else if (rankSet.containsKey(normReading))
                rankSet.get(normReading).add(rm);
            else
                rankSet.put(normReading, new ArrayList<>(Collections.singletonList(rm)));
        }
        return identicalReadingsList;
    }

    // Retrieve all readings of a section between two ranks as Nodes
    public static List<Node> getReadingsBetweenRanks(long startRank, long endRank, Node startNode, String limitText, Transaction tx) {
        List<Node> readings;
        PathExpander<Transaction> e = new AlignmentTraverse(startNode);
        Stream<Node> readingStream = StreamSupport.stream(tx.traversalDescription().depthFirst()
        		.expand(e, new InitialBranchState.State<>(tx, tx)).uniqueness(Uniqueness.NODE_GLOBAL)
        		.traverse(startNode).nodes().spliterator(), false)
        		.filter(x -> startRank <= Long.parseLong(x.getProperty("rank").toString()) &&
        		endRank >= Long.parseLong(x.getProperty("rank").toString()));
        if (!limitText.isEmpty())
        	readingStream = readingStream.filter(x -> x.getProperty("text").toString().equals(limitText));
        readings = readingStream.collect(Collectors.toList());

        return readings;
    }


    /*
     * Witness text retrieval methods
     */

    /**
     * Get the list of section nodes to iterate over for a given tradition and optional section.
     *
     * @param tx     the transaction within which we are working
     * @param tradId the tradition ID
     * @param sectId the section ID, or null for all sections
     * @return a list of section nodes
     * @throws NotFoundException if the tradition or section doesn't exist
     */
    public static ArrayList<Node> sectionsRequested(Transaction tx, String tradId, String sectId) {
        Node traditionNode = getTraditionNode(tx, tradId);
        if (traditionNode == null)
            throw new NotFoundException("Requested tradition not found");

        ArrayList<Node> iterationList;
        if (sectId == null) {
            iterationList = getSectionNodes(tx, tradId);
        } else {
            if (!sectionInTradition(tx, tradId, sectId))
                throw new NotFoundException("Requested section not found in this tradition");
            iterationList = new ArrayList<>();
            Node sectionNode = tx.getNodeByElementId(sectId);
            iterationList.add(sectionNode);
        }
        return iterationList;
    }

    /**
     * Traverse the readings for a witness through a section, starting from the given start node.
     *
     * @param tx        the transaction within which we are working
     * @param startNode the start node of the section
     * @param sigil     the witness sigil
     * @param layers    the list of witness layers (empty list for the main layer)
     * @return an ordered list of reading nodes along the witness path
     * @throws IllegalStateException with message "CONFLICT" if the end node is not reached
     */
    public static ArrayList<Node> traverseReadingsOfWitness(Transaction tx, Node startNode, String sigil, List<String> layers) {
        Evaluator e;
        if (layers == null || layers.isEmpty())
            e = new WitnessPath(sigil).getEvalForWitness();
        else
            e = new WitnessPath(sigil, layers).getEvalForWitness();

        ArrayList<Node> result = new ArrayList<>();
        tx.traversalDescription().depthFirst()
                .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                .evaluator(e)
                .uniqueness(Uniqueness.RELATIONSHIP_PATH)
                .traverse(startNode)
                .nodes()
                .forEach(result::add);
        // If the path is nonzero but the end node wasn't reached, we had a conflict.
        if (!result.isEmpty() && !result.getLast().hasProperty("is_end"))
            throw new IllegalStateException("CONFLICT");
        return result;
    }

    /**
     * Get the text of a witness across one or all sections of a tradition.
     *
     * @param tx        the transaction within which we are working
     * @param tradId    the tradition ID
     * @param sectId    the section ID, or null for all sections
     * @param sigil     the witness sigil
     * @param layers    the list of witness layers (empty list for the main layer)
     * @param startRank the starting rank (0 for no lower bound)
     * @param endRank   the ending rank (use Long.MAX_VALUE for no upper bound, to be resolved per section)
     * @return the witness text as a string
     * @throws NotFoundException        if the tradition/section doesn't exist or no witness path is found
     * @throws IllegalArgumentException if rank parameters are invalid
     * @throws IllegalStateException    with message "CONFLICT" if a section's end node cannot be reached
     */
    public static String getWitnessText(Transaction tx, String tradId, String sectId, String sigil,
                                         List<String> layers, long startRank, long endRank) {
        ArrayList<Node> iterationList = sectionsRequested(tx, tradId, sectId);

        ArrayList<Node> witnessReadings = new ArrayList<>();
        for (Node currentSection : iterationList) {
            if (iterationList.size() > 1 && (endRank != Long.MAX_VALUE || startRank != 0))
                throw new IllegalArgumentException("Cannot request specific start/end across sections");

            long sectionEndRank = endRank;
            if (sectionEndRank == Long.MAX_VALUE) {
                // Find the rank of the graph's end.
                Node endNode = DatabaseService.getRelated(currentSection, ERelations.HAS_END).getFirst();
                sectionEndRank = Long.parseLong(endNode.getProperty("rank").toString());
            }

            if (sectionEndRank == startRank)
                throw new IllegalArgumentException("end-rank is equal to start-rank");

            long sr = startRank;
            long er = sectionEndRank;
            if (er < sr) {
                // Swap them around.
                long temp = sr;
                sr = er;
                er = temp;
            }

            Node startNode = getStartNode(tx, currentSection.getElementId());
            final long finalSr = sr;
            final long finalEr = er;
            witnessReadings.addAll(traverseReadingsOfWitness(tx, startNode, sigil, layers).stream()
                    .filter(x -> Long.parseLong(x.getProperty("rank").toString()) >= finalSr
                            && Long.parseLong(x.getProperty("rank").toString()) <= finalEr).toList());
        }

        // If the path is size 0 then we didn't even get to the end node; the witness path doesn't exist.
        if (witnessReadings.isEmpty())
            throw new NotFoundException("No witness path found for this sigil");

        // Construct the text from the node reading models
        return ReadingService.textOfReadings(
                witnessReadings.stream().map(ReadingModel::new).collect(Collectors.toList()), false, false);
    }

    public static void reorderSectionAfter(Transaction tx, String tradId, String sectToMove, String priorSectID) {
        Node thisSection = tx.getNodeByElementId(sectToMove);

        // Check that the requested prior section also exists and is part of the tradition
        Node priorSection = null;   // the requested prior section
        Node latterSection = null;  // the section after the requested prior
        if (priorSectID.equals("none")) {
            // There is no prior section, and the first section will become the latter one. Find it.
            ArrayList<Node> sectionNodes = getSectionNodes(tx, tradId);
            if (sectionNodes.isEmpty())
                throw new IllegalArgumentException("Tradition has no sections");
            for (Node s : sectionNodes) {
                if (!s.hasRelationship(Direction.INCOMING, ERelations.NEXT)) {
                    latterSection = s;
                    break;
                }
            }
            if (latterSection == null)
                throw new RuntimeException("Could not find tradition's first section");

                // If we request the first section to go first, it should be a no-op.
            else if (latterSection.equals(thisSection))
                return;
        } else {
            priorSection = tx.getNodeByElementId(priorSectID);
            if (priorSection == null)
                throw new IllegalArgumentException("Section " + priorSectID + "not found");
            Node pnTradition = getTraditionNode(tx, priorSection);
            if (!pnTradition.getProperty("id").equals(tradId))
                throw new IllegalArgumentException("Section " + priorSectID + " doesn't belong to this tradition");

            if (priorSection.hasRelationship(Direction.OUTGOING, ERelations.NEXT)) {
                Relationship oldSeq = priorSection.getSingleRelationship(ERelations.NEXT, Direction.OUTGOING);
                latterSection = oldSeq.getEndNode();
                oldSeq.delete();
            }
        }

        // Remove our node from its existing sequence
        removeSectionFromSequence(thisSection);

        // Link it up to the prior if it exists
        if (priorSection != null) priorSection.createRelationshipTo(thisSection, ERelations.NEXT);
        // ...and to the old "next" if it exists
        if (latterSection != null) thisSection.createRelationshipTo(latterSection, ERelations.NEXT);
    }

    public static void removeSectionFromSequence (Node aSection) {
        Node priorSection = null;
        Node nextSection = null;
        if (aSection.hasRelationship(Direction.INCOMING, ERelations.NEXT)) {
            Relationship incomingRel = aSection.getSingleRelationship(ERelations.NEXT, Direction.INCOMING);
            priorSection = incomingRel.getStartNode();
            incomingRel.delete();
        }
        if (aSection.hasRelationship(Direction.OUTGOING, ERelations.NEXT)) {
            Relationship outgoingRel = aSection.getSingleRelationship(ERelations.NEXT, Direction.OUTGOING);
            nextSection = outgoingRel.getEndNode();
            outgoingRel.delete();
        }
        if (priorSection != null && nextSection != null) {
            priorSection.createRelationshipTo(nextSection, ERelations.NEXT);
        }
    }

    /*
     * Methods for merging two readings
     */

    /**
     * Validates whether two readings can be merged. Throws IllegalStateException if not.
     *
     * @param tx              the transaction within which we are working
     * @param keepingReading  the reading which stays in the database
     * @param deletingReading the reading which will be deleted from the database
     * @throws IllegalStateException if the readings cannot be merged
     * @throws Exception             if something goes wrong during validation
     */
    public static void validateMerge(Transaction tx, Node keepingReading, Node deletingReading) throws Exception {
        // Ensure that the two readings belong to the same section.
        if (!keepingReading.getProperty("section_id").equals(deletingReading.getProperty("section_id"))) {
            throw new IllegalStateException("Readings must be in the same section!");
        }
        // Test for non-colo relations.
        if (hasNonColoRelations(keepingReading, deletingReading)) {
            throw new IllegalStateException("Readings to be merged cannot contain cross-location relations");
        }
        // If the two readings are aligned, there is no need to test for cycles.
        boolean aligned = false;
        RelationService.RelatedReadingsTraverser rt = new RelationService.RelatedReadingsTraverser(
                tx, keepingReading, RelationTypeModel::getIs_colocation);
        for (Node n : tx.traversalDescription().depthFirst()
                .relationships(ERelations.RELATED)
                .evaluator(rt)
                .uniqueness(Uniqueness.NODE_GLOBAL)
                .traverse(keepingReading).nodes()) {
            if (n.equals(deletingReading)) {
                aligned = true;
                break;
            }
        }
        // Test for cycles.
        if (!aligned) {
            if (ReadingService.wouldGetCyclic(tx, keepingReading, deletingReading)) {
                throw new IllegalStateException("Readings to be merged would make the graph cyclic");
            }
        }
    }

    /**
     * Performs all necessary steps in the database to merge two readings into one.
     *
     * @param stayingReading  the reading which stays in the database
     * @param deletingReading the reading which will be deleted from the database
     * @param traditionId     the tradition ID, needed for relation creation
     * @return a GraphModel describing the changes made
     * @throws IllegalStateException if relation transfer fails due to conflicts
     */
    public static GraphModel mergeReadings(Transaction tx, Node stayingReading, Node deletingReading, String traditionId)
            throws IllegalStateException {
        GraphModel merged = new GraphModel();
        // Remove any existing relations between the readings
        deleteRelationBetweenReadings(stayingReading, deletingReading);
        // Transfer the witnesses of the to-be-deleted reading to the staying reading
        for (Relationship r : DatabaseService.getRelationships(deletingReading, Direction.INCOMING, ERelations.SEQUENCE)) {
            ReadingService.transferWitnesses(r.getStartNode(), stayingReading, r).stream().map(SequenceModel::new)
                    .forEach(merged.getSequences()::add);
            r.delete();
        }
        for (Relationship r : DatabaseService.getRelationships(deletingReading, Direction.OUTGOING, ERelations.SEQUENCE)) {
            ReadingService.transferWitnesses(stayingReading, r.getEndNode(), r).stream().map(SequenceModel::new)
                    .forEach(merged.getSequences()::add);
            r.delete();
        }
        // Transfer any existing reading relations to the node that will remain
        merged.addRelations(addRelationsToStayingReading(tx, stayingReading, deletingReading, traditionId));
        // Delete the redundant reading and record the remaining one
        deletingReading.delete();
        merged.getReadings().add(new ReadingModel(stayingReading));
        return merged;
    }

    // NOTE: as is, this form is only used by callers that don't care about the return value. This may change...
    public static void mergeReadings(Transaction tx, String stayingRdgId, String deletingRdgId, String traditionId)
            throws IllegalStateException {
        mergeReadings(tx, tx.getNodeByElementId(stayingRdgId), tx.getNodeByElementId(deletingRdgId), traditionId);
    }

    /**
     * Checks if the two readings have a relation between them which implies non-colocation.
     *
     * @param stayingReading  the reading which stays in the database
     * @param deletingReading the reading which will be deleted from the database
     * @return true if the readings have a non-colocation relation
     */
    private static boolean hasNonColoRelations(Node stayingReading, Node deletingReading) {
        for (Relationship stayingRel : DatabaseService.getRelationships(stayingReading, ERelations.RELATED)) {
            if (stayingRel.getOtherNode(stayingReading).equals(deletingReading)) {
                return !(stayingRel.hasProperty("colocation") && stayingRel.getProperty("colocation").equals(true));
            }
        }
        return false;
    }

    /**
     * Deletes any RELATED relationship(s) between the two readings.
     *
     * @param stayingReading  the reading which stays in the database
     * @param deletingReading the reading which will be deleted from the database
     */
    private static void deleteRelationBetweenReadings(Node stayingReading, Node deletingReading) {
        for (Relationship firstRel : DatabaseService.getRelationships(stayingReading, ERelations.RELATED)) {
            for (Relationship secondRel : DatabaseService.getRelationships(deletingReading, ERelations.RELATED)) {
                if (firstRel.equals(secondRel)) {
                    firstRel.delete();
                }
            }
        }
    }

    /**
     * Transfers relations from deletingReading to stayingReading.
     *
     * @param stayingReading  the reading which stays in the database
     * @param deletingReading the reading which will be deleted from the database
     * @param traditionId     the tradition ID, needed for relation creation
     * @return the set of relation models that were successfully added
     * @throws IllegalStateException if a conflicting relation prevents the merge
     */
    private static Set<RelationModel> addRelationsToStayingReading(Transaction tx, Node stayingReading, Node deletingReading,
                                                                    String traditionId) throws IllegalStateException {
        Set<RelationModel> addedRels = new HashSet<>();
        // copy any relevant and nonexistent relationships from deletingReading to stayingReading
        for (Relationship oldRel : DatabaseService.getRelationships(deletingReading,
        		Direction.BOTH, ERelations.RELATED)) {
            RelationModel rel = new RelationModel(oldRel);
            if (oldRel.getStartNode().equals(deletingReading))
                rel.setSource(stayingReading.getElementId());
            else
                rel.setTarget(stayingReading.getElementId());
            try {
                GraphModel addResult = RelationService.createLocalRelation(tx, traditionId, rel);
                if (!addResult.getRelations().isEmpty()) {
                    addedRels.add(rel);
                }
            } catch (Exception e) {
                throw new IllegalStateException(String.format("Conflicting %s relation to node %s prevents merge",
                        rel.getType(), oldRel.getOtherNode(deletingReading).getElementId()));
            }
        }
        // Now delete all the relations from deletingReading, including any that were created just now
        // as transitive relation artifacts
        DatabaseService.getRelationships(deletingReading, ERelations.RELATED).forEach(Relationship::delete);
        return addedRels;
    }
}
