package net.stemmaweb.services;

import net.stemmaweb.model.RelationTypeModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import org.neo4j.graphdb.*;

/**
 * 
 * Provides helper methods related to relationships.
 * 
 * @author PSE FS 2015 Team2
 *
 */
public class RelationshipService {

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
     * Returns a RelationTypeModel for the given relationship type string, associated with
     * the given tradition. Creates the type with default values if it doesn't already exist.
     *
     * @param traditionNode - The Neo4J node representing the tradition
     * @param relType       - The name of the relationship type (e.g. "spelling")
     * @return A RelationTypeModel with the relationship type information.
     */
    public static RelationTypeModel returnRelationType(Node traditionNode, String relType) {
        RelationTypeModel rtDefault = new RelationTypeModel(relType);
        Node relTypeNode = rtDefault.lookup(traditionNode);
        if (relTypeNode == null) {
            rtDefault.instantiate(traditionNode);
            return rtDefault;
        } else {
            return new RelationTypeModel(relTypeNode);
        }
    }
}
