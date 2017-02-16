package net.stemmaweb.rest;

import org.neo4j.graphdb.Label;

/**
 * Lists all possible labels we use in Neo4j database.
 * 
 * @author PSE FS 2015 Team2
 */
public enum Nodes implements Label {
    ROOT,           // is a the root node of the db
    READING,        // is a reading in the db
    WITNESS,        // is a witness in a stemma tree
    TRADITION,      // is a tradition root node
    SECTION,        // is a part of a tradition
    STEMMA,         // is a stemma root node
    USER,           // is a user node
    ANNOTATION,     // is a user-defined annotation on the graph
    USERENTITY,     // is a user-defined entity for annotations
    USERREL,        // is a user-defined relationship for annotations
}