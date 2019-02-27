package net.stemmaweb.services;

import net.stemmaweb.model.RelationTypeModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.RelationType;
import net.stemmaweb.rest.Tradition;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

import javax.ws.rs.core.Response;
import java.util.HashSet;

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

    public static class ColocationTraverser implements Evaluator {
        private String tradId;
        private HashSet<String> colocatedRels;

        public ColocationTraverser(String tradId) {
            this.tradId = tradId;
            // Populate the list of colocated relations for this tradition.
            Tradition tradRest = new Tradition(tradId);
            for (RelationTypeModel rtm : tradRest.collectRelationTypes())
                if (rtm.getIs_colocation())
                    colocatedRels.add(rtm.getName());
        }

        @Override
        public Evaluation evaluate(Path path) {
            if (path.endNode().equals(path.startNode()))
                return Evaluation.INCLUDE_AND_CONTINUE;
            // Are we following a sequence link?
            if (path.lastRelationship().isType(ERelations.SEQUENCE))
                return Evaluation.EXCLUDE_AND_CONTINUE;
            // Then we are following a relation link.
            String type = path.lastRelationship().getProperty("type").toString();
            // Is it one of our colocated relations?
            if (colocatedRels.contains(type))
                return Evaluation.INCLUDE_AND_CONTINUE;
            return Evaluation.EXCLUDE_AND_PRUNE;
        }
    }
}
