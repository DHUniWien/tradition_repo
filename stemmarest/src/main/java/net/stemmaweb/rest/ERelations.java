package net.stemmaweb.rest;


import org.neo4j.graphdb.RelationshipType;
public enum ERelations implements RelationshipType{
	NORMAL, RELATIONSHIP, STEMMA
}