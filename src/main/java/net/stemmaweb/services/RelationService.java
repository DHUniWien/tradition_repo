package net.stemmaweb.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.Uniqueness;

import net.stemmaweb.model.GraphModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationModel;
import net.stemmaweb.model.RelationTypeModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;

/**
 * 
 * Provides helper methods related to reading relations.
 * 
 * @author PSE FS 2015 Team2
 *
 */
public class RelationService {
    /**
     * Copies all the properties of a relationship to another if the property
     * exists.
     *
     * @param oldRelationship the relationship to copy from
     * @param newRelationship the relationship to copy to
     */
    public static void copyRelationshipProperties(Relationship oldRelationship,
                                                          Relationship newRelationship) {
        for (String key : oldRelationship.getPropertyKeys()) {
            if (oldRelationship.hasProperty(key)) {
                newRelationship.setProperty(key, oldRelationship.getProperty(key));
            }
        }
    }

    /**
     * Creates a relation type with the given name according to default values.
     * Method for use internally, logic intended for Stemmaweb backwards compatibility.
     *
     * @param tx       - the transaction within which we are working
     * @param tradNode - the tradition node to attach the relation type to
     * @param typeName - the name of the relation type to create
     * @return the created RelationTypeModel, or null if the type already exists
     * @throws IllegalArgumentException if the type name is invalid
     * @throws Exception if the type could not be instantiated
     */
    public static RelationTypeModel makeDefaultType(Transaction tx, Node tradNode, String typeName)
            throws Exception {
        Map<String, String> defaultRelations = new HashMap<>() {{
            put("collated", "Internal use only");
            put("orthographic", "These are the same reading, neither unusually spelled.");
            put("punctuation", "These are the same reading apart from punctuation.");
            put("spelling", "These are the same reading, spelled differently.");
            put("grammatical", "These readings share a root (lemma), but have different parts of speech (morphologies).");
            put("lexical", "These readings share a part of speech (morphology), but have different roots (lemmata).");
            put("uncertain", "These readings are related, but a clear category cannot be assigned.");
            put("other", "These readings are related in a way not covered by the existing types.");
            put("transposition", "This is the same (or nearly the same) reading in a different location.");
            put("repetition", "This is a reading that was repeated in one or more witnesses.");
        }};

        RelationTypeModel relType = new RelationTypeModel(typeName);
        // Does this already exist?
        Node extantRelType = relType.lookup(tradNode);
        if (extantRelType != null)
            return null;

        // If we don't have any settings for the requested name, use the settings for "other"
        String useType = typeName;
        if (!defaultRelations.containsKey(typeName)) useType = "other";

        relType.setDescription(defaultRelations.get(useType));
        // Set the bindlevel
        int bindlevel = switch (useType) {
            case "spelling" -> 1;
            case "grammatical", "lexical" -> 2;
            case "collated", "transposition", "repetition" -> 50;
            default -> 0; // orthographic, punctuation, uncertain, other
        };
        relType.setBindlevel(bindlevel);
        // Set the booleans
        relType.setIs_colocation(!(useType.equals("transposition") || useType.equals("repetition")));
        relType.setIs_weak(useType.equals("collated"));
        relType.setIs_transitive(!(useType.equals("uncertain") || useType.equals("other")
                || useType.equals("repetition") || useType.equals("transposition")));
        relType.setIs_generalizable(!(useType.equals("collated")|| useType.equals("uncertain")
                || useType.equals("other")));
        relType.setUse_regular(!useType.equals("orthographic"));
        // Create the node
        Node result = relType.instantiate(tradNode, tx);
        if (result == null)
            throw new Exception("Could not instantiate default relation type");
        return relType;
    }

    /**
     * Returns a RelationTypeModel for the given relation type string, associated with
     * the given tradition. Creates the type with default values if it doesn't already exist.
     *
     * @param tx          - The transaction within which we are working
     * @param traditionId - The ID string of the tradition
     * @param relType     - The name of the relation type (e.g. "spelling")
     * @return A RelationTypeModel with the relation type information.
     */
    public static RelationTypeModel returnRelationType(Transaction tx, String traditionId, String relType) {
        Node traditionNode = VariantGraphService.getTraditionNode(tx, traditionId);
        RelationTypeModel rtm = new RelationTypeModel(relType);
        Node extantRelType = rtm.lookup(traditionNode);
        if (extantRelType != null) {
            return new RelationTypeModel(extantRelType);
        }
        // Type doesn't exist; create it with defaults
        try {
            return makeDefaultType(tx, traditionNode, relType);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Returns a list of RelationTypeModels that pertain to a tradition. The lookup can be
     * based on the tradition node, or any section or reading node therein.
     *
     * @param tx            - the transaction within which we are working
     * @param referenceNode - a Tradition, Section or Reading node that belongs to the tradition
     * @return - a list of RelationTypeModels for the tradition in question
     */
   	public static List<RelationTypeModel> ourRelationTypes(Transaction tx, Node referenceNode) {
        List<RelationTypeModel> result = new ArrayList<>();
    	// Must be under control of the same transaction!
    	referenceNode = tx.getNodeByElementId(referenceNode.getElementId());
        // Find the tradition node
        Node traditionNode = null;
        if (referenceNode.hasLabel(Nodes.TRADITION))
            traditionNode = referenceNode;
        else if (referenceNode.hasLabel(Nodes.SECTION))
            traditionNode = VariantGraphService.getTraditionNode(tx, referenceNode);
        else if (referenceNode.hasLabel(Nodes.READING)) {
            Node sectionNode = tx.getNodeByElementId(referenceNode.getProperty("section_id").toString());
            traditionNode = VariantGraphService.getTraditionNode(tx, sectionNode);
        }
        assert(traditionNode != null);
        // ...and query its relation types.
        traditionNode.getRelationships(Direction.OUTGOING, ERelations.HAS_RELATION_TYPE).forEach(
                x -> result.add(new RelationTypeModel(x.getEndNode()))
        );
        return result;
    }

    /**
     * Retrieve clusters of readings, either colocated or non-, from the given section of the given tradition.
     *
     * @param tx          - the transaction within which we are working
     * @param tradId      - the UUID of the relevant tradition
     * @param sectionId   - the ID (as a string) of the relevant section
     * @param colocations - whether we are retrieving colocated clusters or non-colocated ones
     * @return - a list of sets, where each set represents a group of colocated readings
     * @throws Exception - if the relation types can't be collected, or if something goes wrong in the algorithm.
     */
    public static List<Set<Node>> getClusters(
            Transaction tx, String tradId, String sectionId, Boolean colocations)
            throws Exception {
        // Get the tradition node and find the relevant relation types
        HashSet<String> useRelationTypes = new HashSet<>();
        Node traditionNode = VariantGraphService.getTraditionNode(tx, tradId);
        for (RelationTypeModel rtm : ourRelationTypes(tx, traditionNode))
            if (rtm.getIs_colocation() == colocations)
                useRelationTypes.add(rtm.getName());

        // Now run the unionFind algorithm on the relevant subset of relation types
        return collectSpecifiedClusters(tx, sectionId, useRelationTypes);
    }

    /**
     * Retrieve clusters of readings that should be conflated according to the given threshold RelationType.
     *
     * @param tx            - the transaction within which we are working
     * @param tradId        - the UUID of the relevant tradition
     * @param sectionId     - the ID (as a string) of the relevant section
     * @param thresholdName - the name of a RelationType; all of these relations and ones more closely bound will be clustered.
     * @return - a list of sets, where each set represents a group of closely related readings
     * @throws Exception - if the relation types can't be collected, or if something goes wrong with the algorithm
     */
    static List<Set<Node>> getCloselyRelatedClusters(
            Transaction tx, String tradId, String sectionId, String thresholdName)
            throws Exception {
        // Is it a no-op?
        if (thresholdName == null) return new ArrayList<>();
        // Then we have some work to do.
        HashSet<String> closeRelations = new HashSet<>();
        Node traditionNode = VariantGraphService.getTraditionNode(tx, tradId);
        List<RelationTypeModel> rtmlist = ourRelationTypes(tx, traditionNode);
        int bindlevel = 0;
        Optional<RelationTypeModel> thresholdModel = rtmlist.stream().filter(x -> x.getName().equals(thresholdName)).findFirst();
        if (thresholdModel.isPresent())
            bindlevel = thresholdModel.get().getBindlevel();
        for (RelationTypeModel rtm : rtmlist)
            if (rtm.getBindlevel() <= bindlevel)
                closeRelations.add(rtm.getName());

        return collectSpecifiedClusters(tx, sectionId, closeRelations);
    }

    private static List<Set<Node>> collectSpecifiedClusters(
            Transaction tx, String sectionId, Set<String> relatedTypes)
            throws Exception {
        // Now run the unionFind algorithm on the relevant subset of relation types
        List<Set<Node>> result;
            try (ResourceIterator<Node> sectionReadings = tx.findNodes(Nodes.READING, "section_id", sectionId)) {
                // List out the readings for the given section
                List<Node> sectionNodes = sectionReadings.stream().collect(Collectors.toList());
                // Perform a UnionFind over these nodes...
                UnionFind uf = new UnionFind(sectionNodes);
                for (Node sectionNode : sectionNodes)
                    // based on the relevant relations.
                    for (Relationship rel : sectionNode.getRelationships(Direction.OUTGOING, ERelations.RELATED))
                        if (relatedTypes.contains(rel.getProperty("type").toString()))
                            uf.union(sectionNode, rel.getEndNode());
                // Filter the result to remove the sets of size 1 (i.e. the non-clusters)
                result = uf.connectedSets().stream().filter(x -> x.size() > 1).collect(Collectors.toList());

        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Could not detect colocation clusters", e);
        }
        return result;
    }

    static Node findRepresentative(Set<Node> alternatives) {
        // See if this is trivial
        if (alternatives.isEmpty()) return null;
        Node ref = alternatives.stream().findFirst().get();
        if (alternatives.size() == 1) return ref;

        Node representative = null;
        // Go through the alternatives
        // First see if one of the alternatives is a lemma
        Optional<Node> thelemma = alternatives.stream()
                .filter(x -> (Boolean) x.getProperty("is_lemma", false)).findFirst();
        if (thelemma.isPresent())
            representative = thelemma.get();

        // Next sort through the readings with normal forms. If there is a majority
        // normal form, we want the reading that has this form as its text; failing
        // that, we want the majority-witness of these readings.
        else {
            // Do a frequency count of normal forms
            HashMap<String, Integer> normals = new HashMap<>();
            alternatives.stream().filter(x -> x.hasProperty("normal_form"))
                    .map(x -> x.getProperty("normal_form").toString())
                    .forEach(x -> normals.put(x, normals.getOrDefault(x, 0) + 1));
            if (!normals.isEmpty()) {
                String nf = normals.keySet().stream().max(Comparator.comparingInt(normals::get)).get();
                Optional<Node> rep = alternatives.stream().filter(x -> x.getProperty("text").equals(nf)).findFirst();
                if (rep.isPresent())
                    representative = rep.get();
                else {
                    rep = alternatives.stream()
                            .filter(x -> x.getProperty("normal_form", "").equals("nf"))
                            .min(RelationService::byWitnessesDescending);
                    if (rep.isPresent()) representative = rep.get();
                }
            }
        }

        // If that didn't get us an answer, return the most "popular" reading
        if (representative == null)
            representative = alternatives.stream().sorted(RelationService::byWitnessesDescending).toList().getFirst();

        return representative;
    }

    private static int byWitnessesDescending (Node a, Node b) {
        Integer aCount = new ReadingModel(a).getWitnesses().size();
        Integer bCount = new ReadingModel(b).getWitnesses().size();
        return bCount.compareTo(aCount);
    }

    /**
     * Checks if a reading is a "Meta"-reading (lacuna, start, placeholder, or end).
     *
     * @param reading - the reading to check
     * @return true if the reading is a meta reading
     */
    public static boolean isMetaReading(Node reading) {
        return reading != null &&
                ((reading.hasProperty("is_lacuna") && reading.getProperty("is_lacuna").equals(true)) ||
                        (reading.hasProperty("is_start") && reading.getProperty("is_start").equals(true)) ||
                        (reading.hasProperty("is_ph") && reading.getProperty("is_ph").equals(true)) ||
                        (reading.hasProperty("is_end") && reading.getProperty("is_end").equals(true))
                );
    }

    private static String nullToEmptyString(String str) {
        return str == null ? "" : str;
    }

    /**
     * Validates and creates a single relation between two readings.
     *
     * @param tx            - the transaction within which we are working
     * @param tradId        - the tradition ID
     * @param relationModel - the relation specification
     * @return a GraphModel with the created relation and any changed readings, which is empty if the
     *         relation already exists (NOT_MODIFIED case)
     * @throws IllegalStateException if the relation cannot legally be created (CONFLICT cases)
     * @throws Exception on other failures
     */
    public static GraphModel createLocalRelation(Transaction tx, String tradId, RelationModel relationModel)
            throws Exception {
        Node readingA = tx.getNodeByElementId(relationModel.getSource());
        Node readingB = tx.getNodeByElementId(relationModel.getTarget());

        Node ourSection = tx.getNodeByElementId(readingA.getProperty("section_id").toString());
        Node ourTradition = ourSection.getSingleRelationship(ERelations.PART, Direction.INCOMING).getStartNode();
        if (!ourTradition.getProperty("id").equals(tradId))
            throw new IllegalStateException("The specified readings do not belong to the specified tradition");

        if (!readingA.getProperty("section_id").equals(readingB.getProperty("section_id")))
            throw new IllegalStateException("Cannot create relation across tradition sections");

        if (isMetaReading(readingA) || isMetaReading(readingB))
            throw new IllegalStateException("Cannot set relation on a meta reading");

        // Get, or create implicitly, the relation type node for the given type.
        RelationTypeModel rmodel = returnRelationType(tx, tradId, relationModel.getType());
        if (rmodel == null)
            throw new IllegalStateException("Relation type " + relationModel.getType() + " does not exist");

        // Check that the relation type is compatible with the passed relation model
        if (!relationModel.getScope().equals("local") && !rmodel.getIs_generalizable())
            throw new IllegalStateException("Relation type " + rmodel.getName() + " cannot be made outside a local scope");

        // Remove any weak relations that might conflict
        Boolean colocation = rmodel.getIs_colocation();
        if (colocation) {
            ArrayList<Relationship> existing = new ArrayList<>();
            readingA.getRelationships(ERelations.RELATED).forEach(existing::add);
            readingB.getRelationships(ERelations.RELATED).forEach(existing::add);
            for (Relationship r : existing) {
                RelationTypeModel rm = returnRelationType(tx, tradId, r.getProperty("type").toString());
                if (rm == null)
                    throw new IllegalStateException("Already set relation type " + r.getProperty("type") + " does not exist");
                if (rm.getIs_weak())
                    r.delete();
            }
        }

        Boolean isCyclic = ReadingService.wouldGetCyclic(tx, readingA, readingB);
        if (isCyclic && colocation) {
            throw new IllegalStateException("This relation creation is not allowed, it would result in a cyclic graph.");
        } else if (!isCyclic && !colocation) {
            throw new IllegalStateException("This relation creation is not allowed. The two readings can be co-located.");
        }

        // Check if relation already exists
        Iterable<Relationship> relationships = readingA.getRelationships(ERelations.RELATED);
        for (Relationship relationship : relationships) {
            if (relationship.getOtherNode(readingA).equals(readingB)) {
                RelationModel thisRel = new RelationModel(relationship);
                RelationTypeModel rtm = returnRelationType(tx, tradId, thisRel.getType());
                if (rtm == null)
                    throw new IllegalStateException("Parallel relation type " + thisRel.getType() + " does not exist");
                if (thisRel.getType().equals(relationModel.getType())) {
                    // Relation already exists — not modified
                    return new GraphModel(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
                } else if (!rtm.getIs_weak()) {
                    String msg = String.format("Relation of type %s already exists between readings %s and %s",
                            relationModel.getType(), relationModel.getSource(), relationModel.getTarget());
                    throw new IllegalStateException(msg);
                }
            }
        }

        // We are finally ready to write a relation.
        GraphModel readingsAndRelationModel = createSingleRelation(tx, readingA, readingB, relationModel, rmodel);
        // We can also write any transitive relationships.
        propagateRelation(tx, tradId, readingsAndRelationModel, rmodel);
        return readingsAndRelationModel;
    }

    /**
     * Creates the actual Neo4j relationship and recalculates ranks if necessary.
     *
     * @param tx       - the transaction within which we are working
     * @param readingA - the source reading
     * @param readingB - the target reading
     * @param relModel - the RelationModel to set
     * @param rtm      - the RelationTypeModel describing what sort of relation this is
     * @return a GraphModel containing the single relationship plus whatever readings were re-ranked
     * @throws Exception on failure
     */
    public static GraphModel createSingleRelation(Transaction tx, Node readingA, Node readingB,
                                                  RelationModel relModel, RelationTypeModel rtm) throws Exception {
        ArrayList<ReadingModel> changedReadings = new ArrayList<>();
        ArrayList<RelationModel> createdRelations = new ArrayList<>();

        Boolean colocation = rtm.getIs_colocation();
        Relationship relationAtoB = readingA.createRelationshipTo(readingB, ERelations.RELATED);

        relationAtoB.setProperty("type", nullToEmptyString(relModel.getType()));
        relationAtoB.setProperty("scope", nullToEmptyString(relModel.getScope()));
        relationAtoB.setProperty("annotation", nullToEmptyString(relModel.getAnnotation()));
        relationAtoB.setProperty("displayform",
                nullToEmptyString(relModel.getDisplayform()));
        relationAtoB.setProperty("a_derivable_from_b", relModel.getA_derivable_from_b());
        relationAtoB.setProperty("b_derivable_from_a", relModel.getB_derivable_from_a());
        relationAtoB.setProperty("alters_meaning", relModel.getAlters_meaning());
        relationAtoB.setProperty("is_significant", relModel.getIs_significant());
        relationAtoB.setProperty("non_independent", relModel.getNon_independent());
        relationAtoB.setProperty("reading_a", readingA.getProperty("text"));
        relationAtoB.setProperty("reading_b", readingB.getProperty("text"));
        if (colocation) relationAtoB.setProperty("colocation", true);

        // Recalculate the ranks, if necessary
        Long rankA = (Long) readingA.getProperty("rank");
        Long rankB = (Long) readingB.getProperty("rank");
        if (!rankA.equals(rankB) && colocation) {
            // Which one is the lower-ranked reading? Promote it, and recalculate from that point
            Long higherRank = rankA < rankB ? rankB : rankA;
            Node lowerRanked = rankA < rankB ? readingA : readingB;
            lowerRanked.setProperty("rank", higherRank);
            changedReadings.add(new ReadingModel(lowerRanked));
            Set<Node> changedRank = ReadingService.recalculateRank(tx, lowerRanked, false);
            for (Node cr : changedRank)
                if (!cr.equals(lowerRanked))
                    changedReadings.add(new ReadingModel(cr));

        }

        createdRelations.add(new RelationModel(relationAtoB));
        return new GraphModel(changedReadings, createdRelations, new ArrayList<>());
    }

    /**
     * Propagates reading relations according to type specification.
     * Handles transitive relation propagation.
     *
     * @param tx                - the transaction within which we are working
     * @param tradId            - the tradition ID
     * @param newRelationResult - the GraphModel that contains a relation just created
     * @param rtm               - the relation type specification
     * @throws Exception on failure
     */
    public static void propagateRelation(Transaction tx, String tradId, GraphModel newRelationResult,
                                          RelationTypeModel rtm) throws Exception {
        // First see if this relation type should be propagated.
        if (!rtm.getIs_transitive()) return;
        // Now go through all the relations that have been created, and make sure that any
        // transitivity effects have been accounted for.
        for (RelationModel rm : newRelationResult.getRelations()) {
            TransitiveRelationTraverser relTraverser = new TransitiveRelationTraverser(tx, tradId, rtm);
            Node startNode = tx.getNodeByElementId(rm.getSource());
            ArrayList<Node> relatedNodes = new ArrayList<>();
            // Get all the readings that are related by this or a more closely-bound type.
            tx.traversalDescription().depthFirst()
                    .relationships(ERelations.RELATED)
                    .evaluator(relTraverser)
                    .uniqueness(Uniqueness.NODE_GLOBAL)
                    .traverse(startNode).nodes().forEach(relatedNodes::add);
            // Now go through them and make sure the relations are explicit.
            ArrayList<Node> iterateNodes = new ArrayList<>(relatedNodes);
            while (!iterateNodes.isEmpty()) {
                Node readingA = iterateNodes.removeFirst();
                HashSet<Node> alreadyRelated = new HashSet<>();
                readingA.getRelationships(ERelations.RELATED).forEach(x -> alreadyRelated.add(x.getOtherNode(readingA)));
                for (Node readingB : iterateNodes) {
                    if (!alreadyRelated.contains(readingB)) {
                        GraphModel interim = createSingleRelation(tx, readingA, readingB, rm, rtm);
                        newRelationResult.addReadings(interim.getReadings());
                        newRelationResult.addRelations(interim.getRelations());
                    }
                }
            }
            // Now go back through them and make sure that relations to more loosely-bound
            // transitive nodes are marked.
            for (Node sibling : relatedNodes) {
                HashMap<Node, Relationship> connections = new HashMap<>();
                // Get the nodes we are directly related to, and the relations involved, if
                // they meet the criteria
                for (Relationship r : sibling.getRelationships(ERelations.RELATED)) {
                    RelationTypeModel othertm = returnRelationType(tx, tradId, r.getProperty("type").toString());
                    if (othertm == null)
                        throw new IllegalStateException("Sibling relation type " + r.getProperty("type") + " does not exist");
                    if (othertm.getBindlevel() > rtm.getBindlevel() && othertm.getIs_transitive())
                        connections.put(r.getOtherNode(sibling), r);
                }

                HashSet<Node> cousins = new HashSet<>(relatedNodes);
                for (Node n : connections.keySet()) {
                    cousins.remove(n);
                    RelationModel newmodel = new RelationModel(connections.get(n));
                    RelationTypeModel newtm = returnRelationType(tx, tradId, newmodel.getType());
                    if (newtm == null)
                        throw new IllegalStateException("Cousin relation type " + newmodel.getType() + " does not exist");
                    for (Node c : cousins) {
                        ArrayList<Relationship> priorLinks = DatabaseService.getRelationshipTo(n, c, ERelations.RELATED);
                        if (priorLinks.isEmpty()) {
                            // Create a relation based on the looser link
                            GraphModel interim = createSingleRelation(tx, n, c, newmodel, newtm);
                            newRelationResult.addReadings(interim.getReadings());
                            newRelationResult.addRelations(interim.getRelations());
                        }
                    }
                }
            }
        }
    }

    /**
     *
     */
    public static class RelatedReadingsTraverser implements Evaluator {
        private final HashMap<String, RelationTypeModel> ourTypes;
        private final Function<RelationTypeModel, Boolean> criterion;

        public RelatedReadingsTraverser(Transaction tx, Node fromReading) {
            this(tx, fromReading, x -> true);
        }

        public RelatedReadingsTraverser(Transaction tx, Node fromReading, Function<RelationTypeModel, Boolean> criterion) {
            this.criterion = criterion;
            // Make a lookup table of relation types
            ourTypes = new HashMap<>();
            ourRelationTypes(tx, fromReading).forEach(x -> ourTypes.put(x.getName(), x));
        }

        @Override
        public Evaluation evaluate (Path path) {
            // Keep going from the start node
            if (path.endNode().equals(path.startNode()))
                return Evaluation.EXCLUDE_AND_CONTINUE;
            // Check to see if the relation type satisfies our specified criterion
            if (!path.lastRelationship().hasProperty("type"))
                return Evaluation.EXCLUDE_AND_PRUNE;
            RelationTypeModel thisrtm = ourTypes.get(path.lastRelationship().getProperty("type").toString());
            if (criterion.apply(thisrtm))
                return Evaluation.INCLUDE_AND_CONTINUE;
            return Evaluation.EXCLUDE_AND_PRUNE;

        }
    }

    public static class TransitiveRelationTraverser implements Evaluator {
        private final Transaction tx;
        private final String tradId;
        private final RelationTypeModel rtm;

        public TransitiveRelationTraverser(Transaction tx, String tradId, RelationTypeModel reltypemodel) {
            this.tx = tx;
            this.tradId = tradId;
            this.rtm = reltypemodel;
        }

        @Override
        public Evaluation evaluate(Path path) {
            if (path.endNode().equals(path.startNode()))
                return Evaluation.INCLUDE_AND_CONTINUE;
            // If the relation isn't transitive, we don't follow it.
            if (!rtm.getIs_transitive())
                return Evaluation.EXCLUDE_AND_PRUNE;
            // If it's the same relation type, we do follow it.
            if (path.lastRelationship().getProperty("type").equals(rtm.getName()))
                return Evaluation.INCLUDE_AND_CONTINUE;
            // If it's a different relation type, we follow it if it is bound more closely
            // than our type (lower bindlevel) and if that type is also transitive.
            RelationTypeModel othertm = returnRelationType(tx, tradId, path.lastRelationship().getProperty("type").toString());
            if (othertm == null)
                throw new IllegalStateException("Traversed relation type " + path.lastRelationship().getProperty("type") + " is not defined");
            if (rtm.getBindlevel() > othertm.getBindlevel() && othertm.getIs_transitive())
                return Evaluation.INCLUDE_AND_CONTINUE;
            return Evaluation.EXCLUDE_AND_PRUNE;
        }
    }

}
