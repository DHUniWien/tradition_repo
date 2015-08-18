package net.stemmaweb.rest;

import org.neo4j.graphdb.RelationshipType;

/**
 * Lists all possible relationship types we use in Neo4j database.
 *
 * @author PSE FS 2015 Team2
 */
public enum ERelations implements RelationshipType {
    // Directed types
    SYSTEMUSER,     // links the root node to the user(s)
    SEQUENCE,       // the basic link between word sequences in a text
    OWNS_TRADITION, // between user and tradition
    COLLATION,      // between tradition part and START node
    LEMMA_TEXT,     // to indicate word sequence
    HAS_END,        // between tradition part and END node
    
    // Undirected types
    RELATED,        // this type is used to show relationships between readings (undirected)
    STEMMA          // this type is used to make a stemma tree (directed/undirected)
}
