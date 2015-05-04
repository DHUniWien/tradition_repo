package net.stemmaweb.rest;


import org.neo4j.graphdb.RelationshipType;

/**
 * 
 * Lists all possible relationship types we give to relatinoships in neo4j.
 * 
 * @author PSE FS 2015 Team2
 *
 */
public enum ERelations implements RelationshipType{
	NORMAL, RELATIONSHIP, STEMMA
}