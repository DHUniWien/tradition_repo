package net.stemmaweb.services;

import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationTypeModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.RelationType;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

import javax.ws.rs.core.Response;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
     * Returns a RelationTypeModel for the given relation type string, associated with
     * the given tradition. Creates the type with default values if it doesn't already exist.
     *
     * @param traditionId   - The ID string of the tradition
     * @param relType       - The name of the relation type (e.g. "spelling")
     * @return A RelationTypeModel with the relation type information.
     */
    public static RelationTypeModel returnRelationType(String traditionId, String relType) {
        RelationType rtRest = new RelationType(traditionId, relType);
        Response rtResult = rtRest.getRelationType();
        if (rtResult.getStatus() == Response.Status.NO_CONTENT.getStatusCode())
            rtResult = rtRest.makeDefaultType();
        return (RelationTypeModel) rtResult.getEntity();
    }

    /**
     * Returns a list of RelationTypeModels that pertain to a tradition. The lookup can be
     * based on the tradition node, or any section or reading node therein.
     *
     * @param referenceNode - a Tradition, Section or Reading node that belongs to the tradition
     * @return - a list of RelationTypeModels for the tradition in question
     * @throws Exception - if the tradition node can't be determined from the referenceNode
     */
    public static List<RelationTypeModel> ourRelationTypes(Node referenceNode) throws Exception {
        GraphDatabaseService db = referenceNode.getGraphDatabase();
        List<RelationTypeModel> result = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            // Find the tradition node
            Node traditionNode = null;
            if (referenceNode.hasLabel(Nodes.TRADITION))
                traditionNode = referenceNode;
            else if (referenceNode.hasLabel(Nodes.SECTION))
                traditionNode = VariantGraphService.getTraditionNode(referenceNode);
            else if (referenceNode.hasLabel(Nodes.READING)) {
                Node sectionNode = db.getNodeById(Long.valueOf(referenceNode.getProperty("section_id").toString()));
                traditionNode = VariantGraphService.getTraditionNode(sectionNode);
            }
            assert(traditionNode != null);
            // ...and query its relation types.
            traditionNode.getRelationships(ERelations.HAS_RELATION_TYPE, Direction.OUTGOING).forEach(
                    x -> result.add(new RelationTypeModel(x.getEndNode()))
            );
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Could not collect relation types", e);
        }
        return result;
    }

    public static List<Relationship> getDislocations(Node r) throws Exception {
        GraphDatabaseService db = r.getGraphDatabase();
        List<Relationship> result = new ArrayList<>();
        List<RelationTypeModel> dislocationTypes = ourRelationTypes(r).stream()
                .filter(x -> !x.getIs_colocation()).collect(Collectors.toList());
        try (Transaction tx = db.beginTx()) {
            // what is this supposed to do?
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Could not collect dislocations", e);
        }
        return result;
    }

    /**
     * Retrieve clusters of readings, either colocated or non-, from the given section of the given tradition.
     *
     * @param tradId - the UUID of the relevant tradition
     * @param sectionId - the ID (as a string) of the relevant section
     * @param db - the GraphDatabaseService to use
     * @param colocations - whether we are retrieving colocated clusters or non-colocated ones
     * @return - a list of sets, where each set represents a group of colocated readings
     * @throws Exception - if the relation types can't be collected, or if something goes wrong in the algorithm.
     */
    public static List<Set<Node>> getClusters(
            String tradId, String sectionId, GraphDatabaseService db, Boolean colocations)
            throws Exception {
        // Get the tradition node and find the relevant relation types
        HashSet<String> useRelationTypes = new HashSet<>();
        Node traditionNode = VariantGraphService.getTraditionNode(tradId, db);
        for (RelationTypeModel rtm : ourRelationTypes(traditionNode))
            if (rtm.getIs_colocation() == colocations)
                useRelationTypes.add(String.format("\"%s\"", rtm.getName()));

        // Now run the unionFind algorithm on the relevant subset of relation types
        return collectSpecifiedClusters(sectionId, db, useRelationTypes);
    }

    /**
     * Retrieve clusters of readings that should be conflated according to the given threshold RelationType.
     *
     * @param tradId - the UUID of the relevant tradition
     * @param sectionId - the ID (as a string) of the relevant section
     * @param db - the GraphDatabaseService to use
     * @param thresholdName - the name of a RelationType; all of these relations and ones more closely bound will be clustered.
     * @return - a list of sets, where each set represents a group of closely related readings
     * @throws Exception - if the relation types can't be collected, or if something goes wrong with the algorithm
     */
    static List<Set<Node>> getCloselyRelatedClusters(
            String tradId, String sectionId, GraphDatabaseService db, String thresholdName)
            throws Exception {
        // Is it a no-op?
        if (thresholdName == null) return new ArrayList<>();
        // Then we have some work to do.
        HashSet<String> closeRelations = new HashSet<>();
        Node traditionNode = VariantGraphService.getTraditionNode(tradId, db);
        List<RelationTypeModel> rtmlist = ourRelationTypes(traditionNode);
        int bindlevel = 0;
        Optional<RelationTypeModel> thresholdModel = rtmlist.stream().filter(x -> x.getName().equals(thresholdName)).findFirst();
        if (thresholdModel.isPresent())
            bindlevel = thresholdModel.get().getBindlevel();
        for (RelationTypeModel rtm : rtmlist)
            if (rtm.getBindlevel() <= bindlevel)
                closeRelations.add(String.format("\"%s\"", rtm.getName()));

        return collectSpecifiedClusters(sectionId, db, closeRelations);
    }

    private static List<Set<Node>> collectSpecifiedClusters(
            String sectionId, GraphDatabaseService db, Set<String> relatedTypes)
            throws Exception {
        // Now run the unionFind algorithm on the relevant subset of relation types
        List<Set<Node>> result = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            // Make the arguments
            String cypherNodes = String.format("MATCH (n:READING {section_id:%s}) RETURN id(n) AS id", sectionId);
            String cypherRels = String.format("MATCH (n:READING)-[r:RELATED]-(m) WHERE r.type IN [%s] RETURN id(n) AS source, id(m) AS target",
                    String.join(",", relatedTypes));
            // A struct to store the results
            Map<Long, Set<Long>> clusters = new HashMap<>();
            // Stream the results and collect the clusters
            Result r = db.execute(String.format("CALL algo.unionFind.stream('%s', '%s', {graph:'cypher'}) YIELD nodeId, setId", cypherNodes, cypherRels));
            while(r.hasNext()) {
                Map<String, Object> row = r.next();
                Long setId = (Long) row.get("setId");
                Set<Long> cl = clusters.getOrDefault(setId, new HashSet<>());
                Long nodeId = (Long) row.get("nodeId");
                cl.add(nodeId);
                clusters.put(setId, cl);
            }

            // Convert the map of setID -> set of nodeIDs into a list of nodesets
            clusters.keySet().stream().filter(x -> clusters.get(x).size() > 1)
                    .forEach(x -> result.add(clusters.get(x).stream().map(db::getNodeById).collect(Collectors.toSet())));
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Could not detect colocation clusters", e);
        }
        return result;
    }

    static Node findRepresentative(Set<Node> alternatives) {
        GraphDatabaseService db;
        // See if this is trivial
        if (alternatives.isEmpty()) return null;
        Node ref = alternatives.stream().findFirst().get();
        if (alternatives.size() == 1) return ref;

        // It's not trivial
        db = ref.getGraphDatabase();
        Node representative = null;
        // Go through the alternatives
        try (Transaction tx = db.beginTx()) {
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
                if (normals.size() > 0) {
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
                representative = alternatives.stream().sorted(RelationService::byWitnessesDescending)
                        .collect(Collectors.toList()).get(0);

            tx.success();
        }
        return representative;
    }

    private static int byWitnessesDescending (Node a, Node b) {
        Integer aCount = new ReadingModel(a).getWitnesses().size();
        Integer bCount = new ReadingModel(b).getWitnesses().size();
        return bCount.compareTo(aCount);
    }

    /**
     *
     */
    public static class RelatedReadingsTraverser implements Evaluator {
        private HashMap<String, RelationTypeModel> ourTypes;
        private Function<RelationTypeModel, Boolean> criterion;

        public RelatedReadingsTraverser(Node fromReading) throws Exception {
            this(fromReading, x -> true);
        }

        public RelatedReadingsTraverser(Node fromReading, Function<RelationTypeModel, Boolean> criterion) throws Exception {
            this.criterion = criterion;
            // Make a lookup table of relation types
            ourTypes = new HashMap<>();
            ourRelationTypes(fromReading).forEach(x -> ourTypes.put(x.getName(), x));
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
        private String tradId;
        private RelationTypeModel rtm;

        public TransitiveRelationTraverser(String tradId, RelationTypeModel reltypemodel) {
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
            RelationTypeModel othertm = returnRelationType(tradId, path.lastRelationship().getProperty("type").toString());
            if (rtm.getBindlevel() > othertm.getBindlevel() && othertm.getIs_transitive())
                return Evaluation.INCLUDE_AND_CONTINUE;
            return Evaluation.EXCLUDE_AND_PRUNE;
        }
    }

}
