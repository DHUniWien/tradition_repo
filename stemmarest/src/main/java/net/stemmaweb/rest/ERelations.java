package net.stemmaweb.rest;


import org.neo4j.graphdb.RelationshipType;

/**
 * 
 * Lists all possible relationshiptypes we give to relatinoships in neo4j.
 *
 */
public enum ERelations implements RelationshipType{
	NORMAL, RELATIONSHIP, STEMMA
}