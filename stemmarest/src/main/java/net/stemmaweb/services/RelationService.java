package net.stemmaweb.services;

import net.stemmaweb.model.RelationTypeModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.RelationType;
import net.stemmaweb.rest.Tradition;
import org.neo4j.graphalgo.UnionFindProc;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

import javax.ws.rs.core.Response;
import java.util.*;
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

    public static List<Set<Node>> getClusters(String tradId, String sectionId, GraphDatabaseService db) {
        // Get the tradition node and find the relevant relation types
        HashSet<String> colocatedRels = new HashSet<>();
        Tradition tradRest = new Tradition(tradId);
        for (RelationTypeModel rtm : tradRest.collectRelationTypes())
            if (rtm.getIs_colocation())
                colocatedRels.add(String.format("\"%s\"", rtm.getName()));
        List<Set<Node>> result = new ArrayList<>();

        try (Transaction tx = db.beginTx()) {
            // Add the unionFind procedure to our object
            GraphDatabaseAPI api = (GraphDatabaseAPI) db;
            api.getDependencyResolver()
                    .resolveDependency(Procedures.class, DependencyResolver.SelectionStrategy.ONLY)
                    .registerProcedure(UnionFindProc.class);

            // Make the arguments
            String cypherNodes = String.format("MATCH (n:READING {section_id:%s}) RETURN id(n) AS id", sectionId);
            String cypherRels = String.format("MATCH (n:READING)-[r:RELATED]-(m) WHERE r.type IN [%s] RETURN id(n) AS source, id(m) AS target",
                    String.join(",", colocatedRels));
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
            return null;
        }
        return result;
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
