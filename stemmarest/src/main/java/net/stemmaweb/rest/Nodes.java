package net.stemmaweb.rest;

import org.neo4j.graphdb.Label;

/**
 * Lists all possible labels we use in Neo4j database.
 * 
 * @author PSE FS 2015 Team2
 */
public enum Nodes implements Label {
    ROOT,           // is a the root node of the db
    WORD,           // is a reading in the db
    WITNESS,        // is a witness in a stemma tree
    TRADITION,      // is a tradition root node
    STEMMA,         // is a stemma root node
    REFERENCE,      // is an entity reference
    PERSON,         // ...to a person
    PLACE,          // ...to a place
    TIMESPAN,       // ...to a period of time
    USER            // is a user node
}