package net.stemmaweb.rest;


import org.neo4j.graphdb.Label;
public enum Nodes implements Label {
	WORD,
	WITNESS,
	TRADITION,
	READING,
	STEMMA;
}