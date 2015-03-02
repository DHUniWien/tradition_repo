package net.stemmaweb.stemmaserver;


import org.neo4j.graphdb.RelationshipType;
public enum Relations implements RelationshipType{
	NORMAL, R_ORTHOGRAPHIC, 
	R_OTHER, R_PUNCTUATION, 
	R_UNCERTAIN, R_SPELLING, 
	R_GRAMMATICAL, R_LEXICAL,
	R_REPETITION, R_TRANSPOSITION;
}