package net.stemmaweb.rest;


import org.neo4j.graphdb.Label;
public enum Nodes implements Label {
	ROOT,
	WORD,
	WITNESS,
	TRADITION,
	STEMMA,
	USER
}