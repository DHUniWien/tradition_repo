package net.stemmaweb.rest;

import org.neo4j.graphdb.RelationshipType;

/**
 * Lists all possible relationship types we use in Neo4j database.
 *
 * @author PSE FS 2015 Team2
 */
public enum ERelations implements RelationshipType {
    // Directed types
    NORMAL,         // this type is used to make a tradition tree [DEPRECATE!]
    OWNS_TRADITION, // between user and tradition
    HAS_TEXT,       // between tradition and START node
    NEXT,           // to indicate word sequence
    
    
    // Undirected types
    RELATIONSHIP,   // this type is used to show relationships between readings (undirected)
    STEMMA,          // this type is used to make a stemma tree (directed/undirected)
    COPIED_FROM
}
