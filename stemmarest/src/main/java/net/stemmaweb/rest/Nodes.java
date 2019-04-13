package net.stemmaweb.rest;

import org.neo4j.graphdb.Label;

/**
 * Lists all possible labels we use in Neo4j database.
 * 
 * @author PSE FS 2015 Team2
 */
public enum Nodes implements Label {
    ROOT,            // is a the root node of the db
    READING,         // is a reading in the db
    EMENDATION,      // is a reading that appears in no witness
    WITNESS,         // is a witness in a stemma tree
    TRADITION,       // is a tradition root node
    SECTION,         // is a part of a tradition
    STEMMA,          // is a stemma root node
    RELATION_TYPE,   // is a defined relation type
    ANNOTATIONLABEL, // is a definition of an annotation label
    PROPERTIES,      // is the properties that a particular annotation node can have
    LINKS,           // is the relationships that a particular annotation node can have
    USER,            // is a user node
    __SYSTEM__       // is a __SYSTEM__ node
}