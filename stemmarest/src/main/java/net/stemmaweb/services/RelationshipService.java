package net.stemmaweb.services;

import org.neo4j.graphdb.Relationship;

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
     * @param oldRelationship
     * @param newRelationship
     * @return
     */
    public static Relationship copyRelationshipProperties(Relationship oldRelationship,
                                                          Relationship newRelationship) {
        for (String key : oldRelationship.getPropertyKeys()) {
            if (oldRelationship.hasProperty(key)) {
                newRelationship.setProperty(key, oldRelationship.getProperty(key));
            }
        }
        return newRelationship;
    }
}
