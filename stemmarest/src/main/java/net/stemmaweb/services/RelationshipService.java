package net.stemmaweb.services;

import org.neo4j.graphdb.Relationship;

public class RelationshipService {
	
	/**
	 * Copies all the properties of a relationship to another if the property
	 * exists.
	 * 
	 * @param oldRelationship
	 * @param newRelationship
	 * @return
	 */
	public static Relationship copyRelationshipProperties(Relationship oldRelationship, Relationship newRelationship) {
		for (String key : oldRelationship.getPropertyKeys())
			if (oldRelationship.hasProperty(key))
				newRelationship.setProperty(key, oldRelationship.getProperty(key));
		return newRelationship;
	}
}
