package net.stemmaweb.services;

import java.util.*;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.crypto.Data;

import com.alexmerz.graphviz.ParseException;
import com.alexmerz.graphviz.objects.*;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.IResource;
import net.stemmaweb.rest.Nodes;

import com.alexmerz.graphviz.Parser;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.Node;

/**
 * This class provides methods for exporting Dot File from Neo4J
 * @author PSE FS 2015 Team2
 */
public class DotToNeo4JParser implements IResource
{
    private GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private GraphDatabaseService db = dbServiceProvider.getDatabase();
    private String errorMessage = null;

    public DotToNeo4JParser(GraphDatabaseService db) {
        this.db = db;
    }

    public Response importStemmaFromDot(String dot, String tradId, Boolean replace) {
        Status result = Status.NO_CONTENT;

        // Split the dot string into separate lines if necessary. Having
        // single-line dot seems to confuse the parser.
        if (dot.indexOf('\n') == -1)
            dot = dot.replaceAll("; ", ";\n");
        StringBuffer dotstream = new StringBuffer(dot);
        Parser p = new Parser();
        try {
            p.parse(dotstream);
        } catch (ParseException e) {
            errorMessage = e.toString();
            result = Status.BAD_REQUEST;
        }
        ArrayList<Graph> parsedgraphs = p.getGraphs();
        if (parsedgraphs.size() == 0) {
            errorMessage = "No graphs were found in this DOT specification.";
            result = Status.BAD_REQUEST;
        }

        // Save each graph into Neo4J. TODO will there ever be more than one?
        for (Graph stemma : parsedgraphs) {
            result = saveToNeo(stemma, tradId, replace);
            if (!result.equals(Status.OK) && !result.equals(Status.CREATED)) {
                break;
            }
        }

        if (errorMessage != null)
            return Response.status(result).entity(errorMessage).build();
        else
            return Response.status(result).build();
    }

    private Status saveToNeo(Graph stemma, String tradId, Boolean replace) {
        // Check for the existence of the tradition
        Node traditionNode = DatabaseService.getTraditionNode(tradId, db);
        if (traditionNode == null)
            return Status.NOT_FOUND;

        String stemmaName = stemma.getId().getLabel();
        // Sometimes the stemma name will be an ID instead of a label. (Quotes?)
        if (stemmaName.equals("")) {
            stemmaName = stemma.getId().getId();
        }
        try (Transaction tx = db.beginTx()) {
            // First check that no stemma with this name already exists for this tradition,
            // unless we intend to replace it.
            for (Node priorStemma : DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA)) {
                if (priorStemma.getProperty("name").equals(stemmaName)) {
                    if (replace) {
                        // TODO Remove the relationships from this stemma and the hypothetical nodes no longer connected.
                        errorMessage = "Replacement of stemmas not yet implemented.";
                        return Status.BAD_REQUEST;
                    } else {
                        errorMessage = "A stemma by this name already exists for this tradition.";
                        return Status.CONFLICT;
                    }
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
                Boolean hypothetical = witness.getAttribute("class").equals("hypothetical");
                // Check for the existence of a node by this name
                Node existingWitness = traditionWitnesses.getOrDefault(sigil, null);
                if (existingWitness != null) {
                    // Check that the requested witness isn't hypothetical unless the
                    // existing one is!
                    if (hypothetical && !((Boolean) existingWitness.getProperty("hypothetical"))) {
                        errorMessage = "The extant tradition witness " + sigil
                                + " cannot be a hypothetical stemma node.";
                        return Status.CONFLICT;
                    }
                } else {
                    existingWitness = db.createNode(Nodes.WITNESS);
                    existingWitness.setProperty("sigil", sigil);
                    existingWitness.setProperty("hypothetical", hypothetical);
                    existingWitness.setProperty("quotesigil", quoteSigil);
                    // Does it have a label separate from its ID?
                    // TODO check for a Unicode ID and separate label
                    String displayLabel = witness.getId().getLabel();
                    if (!displayLabel.equals("") && !quoteSigil) {
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
                        errorMessage = "Multiple archetype nodes found in this stemma: "
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
            errorMessage = e.toString();
            return Status.INTERNAL_SERVER_ERROR;
        }

        if(replace)
            return Status.OK;
        else
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

}