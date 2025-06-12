package net.stemmaweb.parser;

import java.util.*;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.alexmerz.graphviz.ParseException;
import com.alexmerz.graphviz.objects.*;
import net.stemmaweb.model.StemmaModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;

import com.alexmerz.graphviz.Parser;

import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.UnionFind;
import net.stemmaweb.services.VariantGraphService;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.Node;

import static net.stemmaweb.Util.jsonresp;

/**
 * This class provides methods for exporting Dot File from Neo4J
 * @author PSE FS 2015 Team2
 */
public class DotParser {
    private final GraphDatabaseService db;
    private String messageValue = null;

    public DotParser(GraphDatabaseService db) {
        this.db = db;
    }

    /**
     * Parses the dot string in a StemmaModel into a stemma object, and returns an appropriate Response.
     *
     * @param tradId     - The ID of the tradition to which this stemma should be added
     * @param stemmaSpec - A StemmaModel containing the specification for the stemma
     * @return a Response whose entity is a JSON response, either {'name':stemmaName} or {'error':errorMessage}
     */
    public Response importStemmaFromDot(String tradId, StemmaModel stemmaSpec) {
        Status result = null;
        Graph stemma = null;
        try {
            List<Graph> parsedgraphs = parseDot(stemmaSpec.getDot());
            if (parsedgraphs.isEmpty()) {
                messageValue = "No graphs were found in this DOT specification.";
                result = Status.BAD_REQUEST;
            } else if (parsedgraphs.size() > 1) {
                messageValue = "More than one graph was found in this DOT specification.";
                result = Status.BAD_REQUEST;
            }
            stemma = parsedgraphs.get(0);
            // Get its name, in case we still don't have one
            if (stemmaSpec.getIdentifier() == null)
                stemmaSpec.setIdentifier(getDotGraphName(stemma));
        } catch (ParseException e) {
            messageValue = "Error on attempt to parse dot: " + e.getMessage();
            result = Status.BAD_REQUEST;
        }

        // Save the graph into Neo4J.
        if (result == null)
            result = saveToNeo(stemma, tradId, stemmaSpec.getIdentifier(), stemmaSpec.getJobid());

        // Return our answer.
        String returnKey = result == Status.CREATED ? "name" : "error";
        return Response.status(result)
                .entity(jsonresp(returnKey, messageValue))
                .build();
    }

    private Status saveToNeo(Graph stemma, String tradId, String stemmaName, Integer jobid) {
        // Check for the existence of the tradition
        Node traditionNode = VariantGraphService.getTraditionNode(tradId, db);
        if (traditionNode == null)
            return Status.NOT_FOUND;

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
            Map<String, Node> traditionWitnesses = new HashMap<>();
            DatabaseService.getRelated(traditionNode, ERelations.HAS_WITNESS)
                    .forEach(x -> traditionWitnesses.put(x.getProperty("sigil").toString(), x));

            Node stemmaNode;
            Map<Node, Boolean> witnessesVisited = new HashMap<>();
            List<Relationship> stemmaEdges;
            Boolean isDirected = stemma.getType() == 2;
            // Create the stemma in a separate transaction, so we can query it with unionFind to check its shape.
            try (Transaction tx2 = db.beginTx()) {
                // Create the new stemma node
                stemmaNode = db.createNode(Nodes.STEMMA);
                stemmaNode.setProperty("name", stemmaName);
                stemmaNode.setProperty("directed", isDirected);
                if (jobid != null && jobid > 0)
                    stemmaNode.setProperty("from_jobid", jobid);

                // Create the nodes as Witness nodes; use existing witnesses if they exist.
                // Store the collection of them for later traversal.
                for (com.alexmerz.graphviz.objects.Node witness : stemma.getNodes(false)) {
                    String sigil = getNodeSigil(witness);
                    if (witness.getAttribute("class") == null) {
                        messageValue = String.format("Witness %s not marked as either hypothetical or extant", sigil);
                        return Status.BAD_REQUEST;
                    }
                    boolean hypothetical = witness.getAttribute("class").equals("hypothetical");
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
                        // If the witness doesn't exist yet, create it
                        existingWitness = Util.createWitness(traditionNode, sigil, hypothetical);
                        // Does it have a label separate from its ID?
                        String displayLabel = witness.getAttribute("label");
                        if (displayLabel != null) {
                            existingWitness.setProperty("label", displayLabel);
                        }
                        traditionWitnesses.put(sigil, existingWitness);
                        // If the new witness is an extant one, add it to the tradition; apparently
                        // it exists.
                        if (!hypothetical) {
                            traditionNode.createRelationshipTo(existingWitness, ERelations.HAS_WITNESS);
                        }
                    }
                    stemmaNode.createRelationshipTo(existingWitness, ERelations.HAS_WITNESS);
                    witnessesVisited.put(existingWitness, false);
                }

                // Create the edges; each edge has the stemma label as a property.
                stemmaEdges = new ArrayList<>();
                for (Edge transmission : stemma.getEdges()) {
                    Node sourceWit = traditionWitnesses.get(getNodeSigil(transmission.getSource().getNode()));
                    Node targetWit = traditionWitnesses.get(getNodeSigil(transmission.getTarget().getNode()));
                    Relationship txEdge = sourceWit.createRelationshipTo(targetWit, ERelations.TRANSMITTED);
                    txEdge.setProperty("hypothesis", stemmaName);
                    stemmaEdges.add(txEdge);
                }

                // If the stemma we just imported had a jobID that matches the tradition's stemweb_jobid, clear the latter
                if (jobid != null && jobid.equals(
                        traditionNode.getProperty("stemweb_jobid", 0)))
                    traditionNode.removeProperty("stemweb_jobid");

                tx2.success();
            }

            // Now that the stemma is in the database, let's check out its shape.
            // See if the stemma is a strict tree or not. First the simple filter...
            boolean contaminated = traditionWitnesses.size() - stemmaEdges.size() != 1;

            // ...then the UnionFind check if we need it. If the number of edges is correct, and there
            // is a single connected group, it has to be a tree. Otherwise, it is a contaminated tree and
            // one or more unconnected witnesses.
            if (!contaminated) {
                // ...then the UnionFind-based check.
                UnionFind uf = new UnionFind(witnessesVisited.keySet());
                for (Relationship edge : stemmaEdges) {
                    if (!uf.union(edge.getStartNode(), edge.getEndNode())) {
                        contaminated = true;
                        break;
                    }
                }
            }

            if (contaminated)
                stemmaNode.setProperty("is_contaminated", true);

            // If the graph is directed, we also need to identify the archetype and
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
                            .expand(Util.getExpander(Direction.INCOMING, stemmaName))
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
            e.printStackTrace();
            messageValue = e.toString();
            return Status.INTERNAL_SERVER_ERROR;
        }
        messageValue = stemmaName;
        return Status.CREATED;
    }

    public static String getDotGraphName (String dotSpec) throws ParseException {
        // Handle the case where we didn't provide a name in the StemmaModel, e.g. when parsing
        // bare dot strings from the old Stemmaweb
        List<Graph> allGraphs = parseDot(dotSpec);
        if (allGraphs.isEmpty()) return null;
        return getDotGraphName(allGraphs.get(0));
    }

    private static String getDotGraphName (Graph dotStemma) {
        String stemmaName = dotStemma.getId().getLabel();
        // If the stemma name wasn't quoted, it will be an id.
        if (stemmaName.isEmpty()) {
            stemmaName = dotStemma.getId().getId();
        }
        return stemmaName;
    }

    private static List<Graph> parseDot (String dot) throws ParseException {
        // Split the dot string into separate lines if necessary. Having
        // single-line dot seems to confuse the parser.
        if (dot.indexOf('\n') == -1) {
            dot = dot.replaceAll("; ", ";\n");
            dot = dot.replaceAll("\\{ ", "{\n");
            dot = dot.replaceAll(" }", "\n}");
        }
        StringBuffer dotstream = new StringBuffer(dot);
        Parser p = new Parser();
        p.parse(dotstream);
        return p.getGraphs();
    }

    private static String getNodeSigil (com.alexmerz.graphviz.objects.Node n) {
        String sigil = n.getId().getId();
        // If the sigil is in quotes it will be a label, not an ID.
        if (sigil.isEmpty()) {
            sigil = n.getId().getLabel();
        }
        return sigil;
    }
}