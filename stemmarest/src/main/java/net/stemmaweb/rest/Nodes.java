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
    EMENDATION,     // is a reading that appears in no witness
    TRANSLATION,    // is an editor's translation of one or more readings
    WITNESS,        // is a witness in a stemma tree
    TRADITION,      // is a tradition root node
    SECTION,        // is a part of a tradition
    STEMMA,         // is a stemma root node
    RELATION_TYPE,  // is a defined relation type
    REFERENCE,      // is an entity reference
    PERSON,
    PERSONREF,      // ...to a person
    PLACE,
    PLACEREF,       // ...to a place
    DATE,           // is a concrete period of time
    DATEREF,        // is a reference to a date
    DATING,         // is a reference in the text to a period of time
    USER,           // is a user node
    __SYSTEM__      // is a __SYSTEM__ node
}