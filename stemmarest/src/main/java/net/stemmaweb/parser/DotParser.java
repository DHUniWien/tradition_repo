package net.stemmaweb.parser;

import java.util.*;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.alexmerz.graphviz.ParseException;
import com.alexmerz.graphviz.objects.*;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;

import com.alexmerz.graphviz.Parser;

import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.Node;

/**
 * This class provides methods for exporting Dot File from Neo4J
 * @author PSE FS 2015 Team2
 */
public class DotParser {
    private GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private GraphDatabaseService db = dbServiceProvider.getDatabase();
    private String messageValue = null;

    public DotParser(GraphDatabaseService db) {
        this.db = db;
    }

    public Response importStemmaFromDot(String dot, String tradId) {
        Status result = null;
        // Split the dot string into separate lines if necessary. Having
        // single-line dot seems to confuse the parser.
        if (dot.indexOf('\n') == -1) {
            dot = dot.replaceAll("; ", ";\n");
            dot = dot.replaceAll("\\{ ", "{\n");
            dot = dot.replaceAll(" \\}", "\n}");
        }
        StringBuffer dotstream = new StringBuffer(dot);
        Parser p = new Parser();
        try {
            p.parse(dotstream);
        } catch (ParseException e) {
            messageValue = e.toString();
            result = Status.BAD_REQUEST;
        }
        ArrayList<Graph> parsedgraphs = p.getGraphs();
        if (parsedgraphs.size() == 0) {
            messageValue = "No graphs were found in this DOT specification.";
            result = Status.BAD_REQUEST;
        } else if (parsedgraphs.size() > 1) {
            messageValue = "More than one graph was found in this DOT specification.";
            result = Status.BAD_REQUEST;
        }

        // Save the graph into Neo4J.
        if (result == null)
            result = saveToNeo(parsedgraphs.get(0), tradId);

        // Return our answer.
        String returnKey = result == Status.CREATED ? "name" : "error";
        return Response.status(result)
                .entity(simpleJSON(returnKey, messageValue))
                .build();
    }

    private Status saveToNeo(Graph stemma, String tradId) {
        // Check for the existence of the tradition
        Node traditionNode = DatabaseService.getTraditionNode(tradId, db);
        if (traditionNode == null)
            return Status.NOT_FOUND;

        String stemmaName = stemma.getId().getLabel();
        // If the stemma name wasn't quoted, it will be an id.
        if (stemmaName.equals("")) {
            stemmaName = stemma.getId().getId();
        }
        try (Transaction tx = db.beginTx()) {
            // First check that no stemma with this name already exists for this tradition,
            // unless we intend to replace it.
            for (Node priorStemma : DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA)) {
                if (priorStemma.getProperty("name").equals(stemmaName)) {
                    messageValue = "A stemma by this name already exists for this tradition.";
                    return Status.CONFLICT;
                }
            }
            // Get a list of the existing (extant) tradition witnesses
            HashMap<String, Node> traditionWitnesses = new HashMap<>();
            DatabaseService.getRelated(traditionNode, ERelations.HAS_WITNESS)
                    .forEach(x -> traditionWitnesses.put(x.getProperty("sigil").toString(), x));

            // Create the new stemma node
            Node stemmaNode = db.createNode(Nodes.STEMMA);
            stemmaNode.setProperty("name", stemmaName);
            Boolean isDirected = stemma.getType() == 2;
            stemmaNode.setProperty("directed", isDirected);

            // Create the nodes as Witness nodes; use existing witnesses if they exist.
            // Store the collection of them for later traversal.
            HashMap<Node, Boolean> witnessesVisited = new HashMap<>();
            for (com.alexmerz.graphviz.objects.Node witness : stemma.getNodes(false)) {
                String sigil = getNodeSigil(witness);
                // If the witness ID is empty then the sigil was the label, probably
                // Unicode, and needs to be quoted on output.
                boolean quoteSigil = witness.getId().getId().equals("");
                if (witness.getAttribute("class") == null) {
                    messageValue = String.format("Witness %s not marked as either hypothetical or extant", sigil);
                    return Status.BAD_REQUEST;
                }
                Boolean hypothetical = witness.getAttribute("class").equals("hypothetical");
                // Check for the existence of a node by this name
                Node existingWitness = traditionWitnesses.getOrDefault(sigil, null);
                if (existingWitness != null) {
                    // Check that the requested witness isn't hypothetical unless the
                    // existing one is!
                    if (hypothetical && !((Boolean) existingWitness.getProperty("hypothetical"))) {
                        messageValue = "The extant tradition witness " + sigil
                                + " cannot be a hypothetical stemma node.";
                        return Status.CONFLICT;
                    }
                } else {
                    existingWitness = db.createNode(Nodes.WITNESS);
                    existingWitness.setProperty("sigil", sigil);
                    existingWitness.setProperty("hypothetical", hypothetical);
                    existingWitness.setProperty("quotesigil", quoteSigil);
                    // Does it have a label separate from its ID?
                    String displayLabel = witness.getAttribute("label");
                    if (displayLabel != null) {
                        existingWitness.setProperty("label", displayLabel);
                    }
                    traditionWitnesses.put(sigil, existingWitness);
                }
                stemmaNode.createRelationshipTo(existingWitness, ERelations.HAS_WITNESS);
                witnessesVisited.put(existingWitness, false);
            }

            // Create the edges; each edge has the stemma label as a property.
            for (Edge transmission : stemma.getEdges()) {
                Node sourceWit = traditionWitnesses.get(getNodeSigil(transmission.getSource().getNode()));
                Node targetWit = traditionWitnesses.get(getNodeSigil(transmission.getTarget().getNode()));
                Relationship txEdge = sourceWit.createRelationshipTo(targetWit, ERelations.TRANSMITTED);
                txEdge.setProperty("hypothesis", stemmaName);
            }

            // If the graph is directed, we need to identify the archetype and
            // make sure that there is only one.
            if (isDirected) {
                Node rootNode = null;
                // For each node, check that it is connected
                witnesscheck:
                for (Node witness : witnessesVisited.keySet()) {
                    // If this witness has already been visited in another traversal, skip it.
                    if (witnessesVisited.get(witness))
                        continue;
                    ResourceIterable<Node> pathNodes = db.traversalDescription().depthFirst()
                            .relationships(ERelations.TRANSMITTED, Direction.INCOMING)
                            .traverse(witness).nodes();
                    Node pathEnd = null;
                    for (Node pNode : pathNodes) {
                        // If we have been to this point of a path before we can stop checking it.
                        if (witnessesVisited.get(pNode))
                            break witnesscheck;
                        // Otherwise, record that we have been here.
                        witnessesVisited.put(pNode, true);
                        pathEnd = pNode;
                    }
                    if (rootNode == null) {
                        rootNode = pathEnd;
                    } else if (!rootNode.equals(pathEnd)) {
                        assert pathEnd != null;
                        messageValue = "Multiple archetype nodes found in this stemma: "
                                + rootNode.getProperty("sigil") + " and "
                                + pathEnd.getProperty("sigil");
                        return Status.BAD_REQUEST;
                    }
                }
                // We have a single root node; mark it.
                stemmaNode.createRelationshipTo(rootNode, ERelations.HAS_ARCHETYPE);
            }

            // Save the stemma to the tradition.
            traditionNode.createRelationshipTo(stemmaNode, ERelations.HAS_STEMMA);

            tx.success();
        } catch (Exception e) {
            messageValue = e.toString();
            return Status.INTERNAL_SERVER_ERROR;
        }
        messageValue = stemmaName;
        return Status.CREATED;
    }

    private static String getNodeSigil (com.alexmerz.graphviz.objects.Node n) {
        String sigil = n.getId().getId();
        // If the sigil is in quotes it will be a label, not an ID.
        if (sigil.equals("")) {
            sigil = n.getId().getLabel();
        }
        return sigil;
    }

    private static String simpleJSON (String key, String value) {
        return String.format("{\"%s\":\"%s\"}", key, value );
    }

}