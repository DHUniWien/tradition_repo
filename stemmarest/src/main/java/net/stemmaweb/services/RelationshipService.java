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
		for (int i = 0; i < 13; i++) {
			String key = "de" + i;
			if (oldRelationship.hasProperty(key))
				newRelationship.setProperty(key, oldRelationship.getProperty(key));
		}
		return newRelationship;
	}
}
