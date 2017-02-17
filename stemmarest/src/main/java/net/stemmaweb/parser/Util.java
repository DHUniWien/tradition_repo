package net.stemmaweb.parser;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

/**
 * Utility functions for the parsers
 * Created by tla on 14/02/2017.
 */
public class Util {

    // Start and end node creation
    static Node createStartNode(Node parentNode, String tradId) {
        GraphDatabaseService db = parentNode.getGraphDatabase();
        Node startNode = db.createNode(Nodes.READING);
        startNode.setProperty("is_start", true);
        startNode.setProperty("tradition_id", tradId);
        startNode.setProperty("rank", 0L);
        startNode.setProperty("text", "#START#");
        parentNode.createRelationshipTo(startNode, ERelations.COLLATION);
        return startNode;
    }

    // Start and end node creation
    static Node createEndtNode(Node parentNode, String tradId) {
        GraphDatabaseService db = parentNode.getGraphDatabase();
        Node endNode = db.createNode(Nodes.READING);
        endNode.setProperty("is_end", true);
        endNode.setProperty("tradition_id", tradId);
        endNode.setProperty("text", "#END#");
        parentNode.createRelationshipTo(endNode, ERelations.HAS_END);
        return endNode;
    }

    // Witness node creation
    static Node createWitness(Node traditionNode, String sigil, Boolean hypothetical) {
        GraphDatabaseService db = traditionNode.getGraphDatabase();
        Node witnessNode = db.createNode(Nodes.WITNESS);
        witnessNode.setProperty("sigil", sigil);
        witnessNode.setProperty("hypothetical", hypothetical);
        witnessNode.setProperty("quotesigil", !isDotId(sigil));
        return witnessNode;
    }

    static Node createExtant(Node traditionNode, String sigil) {
        Node witnessNode = createWitness(traditionNode, sigil, false);
        traditionNode.createRelationshipTo(witnessNode, ERelations.HAS_WITNESS);
        return witnessNode;
    }

    private static Boolean isDotId (String nodeid) {
        return nodeid.matches("^[A-Za-z][A-Za-z0-9_.]*$")
                || nodeid.matches("^-?(\\.\\d+|\\d+\\.\\d+)$");
    }

    // XML parsing utilities
    static Document openFileStream(InputStream filestream) {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(filestream);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
