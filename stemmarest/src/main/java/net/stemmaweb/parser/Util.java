package net.stemmaweb.parser;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;

/**
 * Utility functions for the parsers
 * Created by tla on 14/02/2017.
 */
public class Util {

     static Node createWitness(Node traditionNode, String sigil, Boolean hypothetical) {
        GraphDatabaseService db = traditionNode.getGraphDatabase();
        Node witnessNode = db.createNode(Nodes.WITNESS);
        witnessNode.setProperty("sigil", sigil);
        witnessNode.setProperty("hypothetical", hypothetical);
        witnessNode.setProperty("quotesigil", !isDotId(sigil));
        traditionNode.createRelationshipTo(witnessNode, ERelations.HAS_WITNESS);
        return witnessNode;
    }

    static Node createExtant(Node traditionNode, String sigil) {
         return createWitness(traditionNode, sigil, true);
    }

    private static Boolean isDotId (String nodeid) {
        return nodeid.matches("^[A-Za-z][A-Za-z0-9_.]*$")
                || nodeid.matches("^-?(\\.\\d+|\\d+\\.\\d+)$");
    }

}
