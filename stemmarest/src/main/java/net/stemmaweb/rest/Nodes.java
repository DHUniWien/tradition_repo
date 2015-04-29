package net.stemmaweb.rest;

import org.neo4j.graphdb.Label;

/**
 * 
 * Lists all possible labels we give to nodes in neo4j.
 *
 */
public enum Nodes implements Label {
	ROOT,
	WORD,
	WITNESS,
	TRADITION,
	STEMMA,
	USER
}