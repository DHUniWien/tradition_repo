package net.stemmaweb.rest;

import org.neo4j.graphdb.RelationshipType;

/**
 * Lists all possible relationship types we use in Neo4j database.
 * 
 * @author PSE FS 2015 Team2
 */
public enum ERelations implements RelationshipType{
	NORMAL, 		// this type is used to make a tradition tree (directed)
	RELATIONSHIP,   // this type is used to show relationships between readings (undirected)
	STEMMA			// this type is used to make a stemma tree (directed/undirected)
}