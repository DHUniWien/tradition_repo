package net.stemmaweb.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.alexmerz.graphviz.ParseException;
import com.alexmerz.graphviz.objects.Edge;
import com.alexmerz.graphviz.objects.Graph;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.IResource;
import net.stemmaweb.rest.Nodes;

import com.alexmerz.graphviz.Parser;

import org.neo4j.graphdb.*;

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
        // Try the parser
        Status result = Status.NO_CONTENT;

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
        String stemmaName = stemma.getId().getLabel();
        try (Transaction tx = db.beginTx()) {
            Node traditionNode = db.findNode(Nodes.TRADITION, "id", tradId);
            // First check that no stemma with this name already exists for this tradition,
            // unless we intend to replace it.
            for (Relationship stemmaRel : traditionNode.getRelationships(ERelations.HAS_STEMMA)) {
                Node priorStemma = stemmaRel.getEndNode();
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

            // Create the new stemma node
            Node stemmaNode = db.createNode(Nodes.STEMMA);
            stemmaNode.setProperty("name", stemmaName);
            Boolean isDirected = stemma.getType() == 2;
            stemmaNode.setProperty("directed", isDirected);

            // Create the nodes as Witness nodes; use existing witnesses if they exist.
            // Store the collection of them for later traversal.
            HashMap<Node, Boolean> witnessesVisited = new HashMap<>();
            for (com.alexmerz.graphviz.objects.Node witness : stemma.getNodes(false)) {
                String sigil = witness.getId().getId();
                // Check for the existence of a node by this name
                Node existingWitness = db.findNode(Nodes.WITNESS, "sigil", sigil);
                if (existingWitness != null) {
                    // Check that the requested witness isn't hypothetical unless the
                    // existing one is!
                    if (witness.getAttribute("class").equals("hypothetical") &&
                            !((Boolean) existingWitness.getProperty("hypothetical"))) {
                        errorMessage = "The tradition witness " + sigil + " cannot be hypothetical.";
                        return Status.CONFLICT;
                    }
                } else {
                    existingWitness = db.createNode(Nodes.WITNESS);
                    existingWitness.setProperty("sigil", sigil);
                    existingWitness.setProperty("hypothetical", witness.getAttribute("class").equals("hypothetical"));
                }
                stemmaNode.createRelationshipTo(existingWitness, ERelations.HAS_WITNESS);
                witnessesVisited.put(existingWitness, false);
            }

            // Create the edges; each edge has the stemma label as a property.
            for (Edge transmission : stemma.getEdges()) {
                Node sourceWit = db.findNode(Nodes.WITNESS, "sigil", transmission.getSource().getNode().getId().getId());
                Node targetWit = db.findNode(Nodes.WITNESS, "sigil", transmission.getTarget().getNode().getId().getId());
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

}