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
    OWNS_TRADITION, // between user and tradition
    PART,           // links a tradition with its (section) parts
    NEXT,           // link between sections as ordered in 1 or more witnesses
    PUB_ORDER,      // link between sections to preserve a canonical order
    COLLATION,      // between section part and START node
    HAS_END,        // between section part and END node
    SEQUENCE,       // the basic link between word sequences in a text
    NSEQUENCE,      // a shadow sequence link to handle normalisation logic
    REPRESENTS,     // a temporary relationship used for normalization logic
    LEMMA_TEXT,     // to indicate canonical word sequence
    HAS_WITNESS,    // links text witnesses to the tradition
    HAS_STEMMA,     // this type is used to make a stemma tree (directed/undirected)
    HAS_ARCHETYPE,  // used to point from the stemma node to its archetype
    TRANSMITTED,    // links witnesses in the stemma to each other
    HAS_RELATION_TYPE,  // specifies what reading relations occur in a tradition
    HAS_EMENDATION, // links a section to all the emendations it contains
    EMENDED,        // a SEQUENCE-like link that anchors an emendation into the text
    HAS_ANNOTATION, // links a tradition (or section?) to its textual annotations
    HAS_ANNOTATION_TYPE,    // links a user to his/her defined annotation types
    HAS_PROPERTIES, // links an annotation type to its allowed properties
    HAS_LINKS,      // links an annotation type to its allowed outbound relationships

    // Undirected types
    RELATED,        // this type is used to show relations between readings (undirected)
}
