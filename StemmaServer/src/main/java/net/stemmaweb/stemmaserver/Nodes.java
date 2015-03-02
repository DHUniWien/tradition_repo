package net.stemmaweb.stemmaserver;


import org.neo4j.graphdb.Label;
public enum Nodes implements Label {
	WITNESS,
	TRADITION,
	READING,
	STEMMA;
}