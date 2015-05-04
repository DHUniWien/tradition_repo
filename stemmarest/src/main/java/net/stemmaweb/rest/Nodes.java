package net.stemmaweb.rest;

import org.neo4j.graphdb.Label;

/**
 * 
 * Lists all possible labels we give to nodes in neo4j.
 * 
 * @author PSE FS 2015 Team2
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