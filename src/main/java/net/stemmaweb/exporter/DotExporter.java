package net.stemmaweb.exporter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.model.DisplayOptionModel;
import net.stemmaweb.printer.GraphViz;
import net.stemmaweb.rest.ERelations;

import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Section;

import static net.stemmaweb.parser.Util.getExpander;
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.VariantGraphService;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Uniqueness;


/**
 * This class provides methods for exporting Dot File from Neo4J
 *
 * @author PSE FS 2015 Team2
 */
public class DotExporter
{
    private GraphDatabaseService db;

    private OutputStream out = null;

    private static DecimalFormat df2 = new DecimalFormat(".##");

    public DotExporter(GraphDatabaseService db){
        this.db = db;
    }

    public Response writeNeo4J(String tradId, DisplayOptionModel dm) {
        return writeNeo4J(tradId, null, dm);
    }

    public Response writeNeo4J(String tradId, String sectionId, DisplayOptionModel dm)
    {
        // Get the start and end node of the whole tradition
        Node traditionNode = VariantGraphService.getTraditionNode(tradId, db);
        Node startNode = VariantGraphService.getStartNode(tradId, db);
        Node endNode = VariantGraphService.getEndNode(tradId, db);
        if(startNode==null || endNode==null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        // Get the list of section nodes
        ArrayList<Node> sections = VariantGraphService.getSectionNodes(tradId, db);
        if (sections == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        File output;
        String result;
        try (Transaction tx = db.beginTx()) {
            output = File.createTempFile("graph_", ".dot");
            out = new FileOutputStream(output);

            Node requestedSection = null;
            if (sectionId != null)
                requestedSection = db.getNodeById(Long.valueOf(sectionId));
            if (requestedSection != null) {
                if (!sections.contains(requestedSection))
                    return Response.status(Status.BAD_REQUEST)
                            .entity(String.format("Section %s not found in tradition %s", sectionId, tradId))
                            .build();
                sections.clear();
                sections.add(requestedSection);
            }
            // Get the graph name - either the requested section name, or the tradition name
            // if all sections were requested
            String graphName = (requestedSection != null) ? requestedSection.getProperty("name").toString()
                    : traditionNode.getProperty("name").toString();

            // Write the graph with the tradition name
            write("digraph \"" + graphName + "\" { \n");
            String direction = traditionNode.getProperty("direction").toString();
            // Set the direction of the graph
            if(!direction.equals("BI")) {
                write("\tgraph [bgcolor=\"none\", rankdir=\"" + direction + "\"];\n");
            } else {
                write("\tgraph [bgcolor=\"none\"]; \n");
            }
            // Set node and edge visual defaults
            write("\tnode [fillcolor=\"white\", fontsize=\"14\", shape=\"ellipse\", style=\"filled\"];\n");
            write("\tedge [arrowhead=\"open\", color=\"#000000\", fontcolor=\"#000000\"];\n");
            long edgeId = 0;
            Long lastSectionEndId = null;
            boolean subgraphWritten = false;

            // Keep track of which nodes were written out (modulo witness filter) and which
            // relations should therefore be written out
            HashSet<Node> writtenNodes = new HashSet<>();
            ArrayList<Relationship> relsToWrite = new ArrayList<>();

            for (Node sectionNode: sections) {
                // Get the number of witnesses we have
                ArrayList<Node> sectionWits = new Section(tradId, String.valueOf(sectionNode.getId()))
                        .collectSectionWitnesses();
                int numWits = sectionWits.size();
                if (dm.getExcludeWitnesses().size() > 0) {
                    numWits -= dm.getExcludeWitnesses().size();
                }
                Node sectionStartNode = VariantGraphService.getStartNode(String.valueOf(sectionNode.getId()), db);
                Node sectionEndNode = VariantGraphService.getEndNode(String.valueOf(sectionNode.getId()), db);
                // If we have requested a section, then that section's start and end are "the" start and end
                // for the whole graph.
                if (sectionId != null) {
                    startNode = sectionStartNode;
                    endNode = sectionEndNode;
                }
                // HACK - now that we know which nodes are functioning as the start and end nodes, set the
                // subgraph and the silent node that keeps the graph straight. Make sure we only do this once.
                if (!subgraphWritten) {
                    write("\tsubgraph { rank=same " + startNode.getId() + " \"#SILENT#\" }\n");
                    write("\t\"#SILENT#\" [shape=diamond,color=white,penwidth=0,label=\"\"];\n");
                    write("\t" + endNode.getId() + "->\"#SILENT#\" [color=white,penwidth=0];\n");
                    subgraphWritten = true;
                }

                // Find our representative nodes, in case we are producing a normalised form of the graph
                HashMap<Node, Node> representatives = getRepresentatives(sectionNode, dm.getNormaliseOn());
                RelationshipType seqLabel = dm.getNormaliseOn() == null ? ERelations.SEQUENCE : ERelations.NSEQUENCE;

                // Collect any lemma edge pairs
                HashMap<Node, Node> lemmaLinks = new HashMap<>();
                db.traversalDescription().breadthFirst()
                        .relationships(ERelations.LEMMA_TEXT,Direction.OUTGOING)
                        .uniqueness(Uniqueness.NODE_GLOBAL)
                        .traverse(sectionStartNode).relationships()
                        .forEach(r -> {
                            // We don't display lemma edges to emendations, for now; emendations are not in
                            // the representatives list.
                            if (representatives.containsKey(r.getStartNode()) && representatives.containsKey(r.getEndNode()))
                                lemmaLinks.put(representatives.get(r.getStartNode()), representatives.get(r.getEndNode()));
                        });

                // Now start writing some dot.
                for (Node node : new HashSet<>(representatives.values())) {

                    // Write out the node list in dot format
                    String nodeSpec = nodeSpec(node, dm);

                    // Skip section start/end nodes, unless they are overall start/end nodes. These links
                    // will be tied to "section" i.e. section end nodes instead.
                    // Intermediate section end nodes should be displayed as a "section" node.
                    if (node.equals(sectionEndNode) && !node.equals(endNode)) {
                        nodeSpec = nodeSpec(node, dm).replace("END", "SECTION_" + sectionNode.getId());
                    } else if (node.equals(sectionStartNode) && !node.equals(startNode))
                        continue;

                    // Now get the sequence relationships between nodes.
                    ArrayList<String> seqSpecs = new ArrayList<>();
                    // This node is automatically in a requested witness if it is the start node, or if there
                    // is no witness filter.
                    boolean inRequestedWitness = node.equals(sectionStartNode) || dm.getExcludeWitnesses().size() == 0;
                    for (Relationship rel : node.getRelationships(Direction.INCOMING, seqLabel)) {
                        if (rel == null)
                            continue;
                        Node relStartNode = rel.getStartNode();
                        Long relStartNodeId = relStartNode.getId();

                        boolean witnessLink = false; // Does the witness filter need this sequence?
                        if (node.equals(sectionStartNode) || dm.getExcludeWitnesses().size() == 0)
                            witnessLink = true;
                        else
                            for (Object v : rel.getAllProperties().values())
                                for (String s : (String[]) v)
                                    if (!dm.getExcludeWitnesses().contains(s))
                                        witnessLink = true;

                        if (witnessLink)
                            inRequestedWitness = true;
                        else
                            continue;

                        // Section-boundary sequence handling
                        if (relStartNode.equals(sectionStartNode) && !relStartNode.equals(startNode))
                            relStartNodeId = lastSectionEndId;

                        // Does this edge coincide with a lemma edge?
                        boolean edge_is_lemma = false;
                        if (lemmaLinks.containsKey(relStartNode) && lemmaLinks.get(relStartNode).equals(node)) {
                            edge_is_lemma = true;
                            lemmaLinks.remove(relStartNode);
                        }
                        // Get the label
                        String label = sequenceLabel(convertProps(rel), numWits, dm);
                        Long rankDiff = (Long) node.getProperty("rank") - (Long) relStartNode.getProperty("rank");
                        seqSpecs.add(relshipText(relStartNodeId, node.getId(), label, edgeId++,
                                calcPenWidth(convertProps(rel)), rankDiff, edge_is_lemma));

                    }

                    // Write out the node & sequence specifications we have gathered
                    if (inRequestedWitness) {
                        writtenNodes.add(node);
                        write(nodeSpec);
                        for (String seqSpec : seqSpecs) {
                            write(seqSpec);
                        }

                        // Retrieve reading relations, if requested
                        if (dm.getIncludeRelated()) {
                            for (Relationship relatedRel : node.getRelationships(Direction.INCOMING, ERelations.RELATED)) {
                                // Only include the relations that are on our representative nodes
                                if (dm.getNormaliseOn() != null) {
                                    if (!representatives.getOrDefault(relatedRel.getStartNode(), relatedRel.getStartNode())
                                            .equals(relatedRel.getStartNode()))
                                        continue;
                                }
                                relsToWrite.add(relatedRel);
                            }
                        }
                    }
                }

                // Now that all the nodes are processed, set this section's end node as the last one seen
                lastSectionEndId = sectionEndNode.getId();

                // Write out reading relationships that survived the node filter
                if (dm.getIncludeRelated())
                    for (Relationship relatedRel : relsToWrite) {
                        if (writtenNodes.contains(relatedRel.getStartNode())
                                && writtenNodes.contains(relatedRel.getEndNode()))
                            write("\t" + relatedRel.getStartNode().getId() + "->" +
                                    relatedRel.getEndNode().getId() + " [style=dotted, constraint=false, arrowhead=none, " +
                                    "label=\"" + relatedRel.getProperty("type").toString() + "\", id=\"e" +
                                    edgeId++ + "\"];\n");
                    }

                // Write any remaining lemma links
                for (Node n : lemmaLinks.keySet()) {
                    write(String.format("\t%d->%d [ id=l%d ];\n",
                            n.getId(), lemmaLinks.get(n).getId(), edgeId++));
                }

                // Clean up after ourselves
                if (seqLabel.equals(ERelations.NSEQUENCE))
                    VariantGraphService.clearNormalization(sectionNode);
            }

            write("}\n");

            out.flush();
            out.close();

            // Now pull the string back out of the output file.
            byte[] encDot = Files.readAllBytes(output.toPath());
            result = new String(encDot, Charset.forName("utf-8"));

            // Remove the following line, if you want to keep the created file
            Files.deleteIfExists(output.toPath());

            tx.success();
        } catch (IOException e) {
            e.printStackTrace();
            return Response.serverError().entity("Could not write file for export").build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(e.getMessage()).build();
        }

        // Here is where to generate pictures from the file for debugging.
        // writeFromDot(output, "svg");

        return Response.ok().entity(result).build();
    }

    /*
     * Helper functions for variant graph production
     */

    private static HashMap<Node, Node> getRepresentatives(Node sectionNode, String normaliseOn)
            throws Exception {
        if (normaliseOn == null) {
            HashMap<Node, Node> representatives = new HashMap<>();
            List<Node> sectionNodes = VariantGraphService.returnTraditionSection(sectionNode).nodes().stream()
                    .filter(x -> x.hasLabel(Label.label("READING"))).collect(Collectors.toList());
            for (Node n: sectionNodes) {
                representatives.put(n, n);
            }
            return representatives;
        } else {
            return VariantGraphService.normalizeGraph(sectionNode, normaliseOn);
        }
    }

    private static String nodeSpec(Node node, DisplayOptionModel dm) {
        // Get the proper node ID
        String nodeDotId = "n" + node.getId();
        if (node.getProperty("is_end", false).equals(true)) nodeDotId = "__END__";
        else if (node.getProperty("is_start", false).equals(true)) nodeDotId = "__START__";

        // Get the node label. If there is a 'display' property, strip any leading / trailing angle
        // brackets, because if we are also showing normal forms, we have to wedge more into the
        // HTML specification.
        boolean hasHTML = node.hasProperty("display");
        String nodeLabel = hasHTML ? node.getProperty("display").toString()
                : node.getProperty("text").toString();
        if (node.getProperty("is_lacuna", false).equals(true)) {
            hasHTML = true;
            nodeLabel = "<B>[ ... ]</B>";
        }
        if (dm.getShowRank())
            nodeLabel = String.format("%s%s(%s)", nodeLabel, hasHTML ? "&nbsp;" : " ", node.getProperty("rank").toString());
        if (dm.getShowNormalForm() && node.hasProperty("normal_form")
            && !node.getProperty("normal_form").toString().equals(node.getProperty("text").toString())) {
            String labelExtra = "<BR/><FONT COLOR=\"grey\">"
                    + escapeHtml4(node.getProperty("normal_form").toString()) + "</FONT>";
            if (hasHTML)
                // We have to glom the normal_form HTML onto the existing HTML label
                nodeLabel = String.format("<%s>", nodeLabel + labelExtra);
            else
                // Do URL escaping of any labels
                nodeLabel = String.format("<%s>", escapeHtml4(nodeLabel) + labelExtra);
        }
        else if (hasHTML)
            // Wrap it in angle brackets
            nodeLabel = String.format("<%s>", nodeLabel);
        else
            // Escape double quotes since we are wrapping in double quotes
            nodeLabel = "\"" + nodeLabel.replace("\"", "\\\"") + "\"";

        // Put it all together
        return("\t" + node.getId() + " [id=\"" + nodeDotId + "\", label=" + nodeLabel + "];\n");
    }

    private static String sequenceLabel(Map<String, String[]> witnessInfo, int numWits, DisplayOptionModel dm) {
        String[] witnesses = {""};
        StringBuilder lex_str = new StringBuilder();
        String label = "";
        // Get the list of witnesses
        if (witnessInfo.containsKey("witnesses")) {
            witnesses = witnessInfo.get("witnesses");
            Arrays.sort(witnesses);
            label = "majority";
        }
        // Join up the list of witnesses if the label should not be 'majority'
        if (dm.getDisplayAllSigla() || numWits < 7 || witnesses.length <= (numWits / 2)) {
            Iterator<String> it = Arrays.stream(witnesses)
                    .filter(x -> !dm.getExcludeWitnesses().contains(x))
                    .iterator();
            while (it.hasNext()) {
                lex_str.append(it.next());
                if (it.hasNext()) {
                    lex_str.append(", ");
                }
            }
            label = lex_str.toString();
        }
        // Add on the layer witnesses where applicable
        lex_str = new StringBuilder();
        lex_str.append(label);
        for (String prop : witnessInfo.keySet()) {
            if (prop.equals("witnesses"))
                continue;
            String[] layerwits = witnessInfo.get(prop);
            Arrays.sort(layerwits);
            for (String s : layerwits) {
                if (lex_str.length() > 0) lex_str.append(", ");
                lex_str.append(s);
                lex_str.append(" (");
                lex_str.append(prop);
                lex_str.append(")");
            }
        }
        label = lex_str.toString();

        return(label);
    }

    private static String calcPenWidth(Map<String, String[]> witnessInfo)
    {
        int hits = 0;
        for (String prop : witnessInfo.keySet())
            hits += (witnessInfo.get(prop)).length;
        return df2.format(0.8 + 0.2 * hits);
    }

    private static String relshipText(Long sNodeId, Long eNodeId, String label, long edgeId, String pWidth, Long rankDiff, boolean isLemmaLink)
    {
        String text;
        try {
            String idStr = isLemmaLink ? "l" : "e";
            idStr += edgeId;
            text = "\t" + sNodeId + "->" + eNodeId + " [label=\"" + label
                    + "\", id=\"" + idStr + "\", penwidth=\"" + pWidth + "\"";
            if (rankDiff > 1)
                text += ", minlen=\"" + rankDiff + "\"";
            text += "];\n";
        } catch (Exception e) {
            text = null;
        }
        return text;
    }

    private static Map<String, String[]> convertProps(Relationship rel) {
        Map<String, String[]> result = new HashMap<>();
        for (String prop : rel.getPropertyKeys()) {
            String[] witList = (String[]) rel.getProperty(prop);
            result.put(prop, witList);
        }
        return result;
    }

    /**
     *
     * Parses a Stemma of a tradition in a JSON string in DOT format
     * don't throw error far enough
     *
     * @param tradId - the ID of the tradition in question
     * @param stemmaTitle - the desired stemma
     * @param singleLine - whether the result needs to omit linebreaks
     * @return a Response with the result
     */

    public Response writeNeo4JStemma(String tradId, String stemmaTitle, Boolean singleLine)
    {
        ArrayList<String> outputLines = new ArrayList<>();

        try (Transaction tx = db.beginTx()) {
            Node traditionNode = db.findNode(Nodes.TRADITION, "id", tradId);
            Node startNodeStemma = null;
            for (Node stemma : DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA)) {
                if (stemma.getProperty("name").equals(stemmaTitle)) {
                    startNodeStemma = stemma;
                    break;
                }
            }
            if(startNodeStemma == null) {
                return Response.status(Status.NOT_FOUND).build();
            }

            String stemmaType = (Boolean) startNodeStemma.getProperty("directed") ? "digraph" : "graph";
            String edgeGlyph = (Boolean) startNodeStemma.getProperty("directed") ? "->" : "--";
            outputLines.add(String.format("%s \"%s\" {", stemmaType, stemmaTitle));

            // Output all the nodes associated with this stemma.
            for (Node witness : DatabaseService.getRelated(startNodeStemma, ERelations.HAS_WITNESS)) {
                String witnessSigil = sigilDotString(witness);
                Boolean hypothetical = (Boolean) witness.getProperty("hypothetical");

                // Get the witness class and, if it exists, label.
                String witnessAttr = hypothetical ? "[class=hypothetical" : "[class=extant";
                if (witness.hasProperty("label")) {
                    witnessAttr += " label=\"" + witness.getProperty("label") + '"';
                }
                witnessAttr += "]";
                outputLines.add(String.format("\t%s %s;", witnessSigil, witnessAttr));
            }

            // Now output all the edges associated with this stemma, starting with the
            // archetype if we have one.
            ArrayList<Node> foundRoots = DatabaseService.getRelated(startNodeStemma, ERelations.HAS_ARCHETYPE);
            if (foundRoots.isEmpty()) {
                // No archetype; just output the list of edges in any order.
                Result txEdges = db.execute("MATCH (a:WITNESS)-[:TRANSMITTED {hypothesis:'" +
                        stemmaTitle + "'}]->(b:WITNESS) RETURN a, b");
                while (txEdges.hasNext()) {
                    Map<String, Object> vector = txEdges.next();
                    String source = sigilDotString((Node) vector.get("a"));
                    String target = sigilDotString((Node) vector.get("b"));
                    outputLines.add(String.format("\t%s %s %s;", source, edgeGlyph, target));
                }
            } else {
                // We have an archetype; start there and traverse the graph.
                Node stemmaRoot = foundRoots.get(0);  // There should be only one.
                for (String edge : traverseStemma(startNodeStemma, stemmaRoot)) {
                    String[] v = edge.split(" : ");
                    outputLines.add(String.format("\t%s %s %s;", v[0], edgeGlyph, v[1]));
                }
            }
            outputLines.add("}");
            tx.success();
        }

        String joinString = singleLine ? "  " : "\n";
        String output = String.join(joinString, outputLines);
        writeFromDot(output);

        return Response.ok(output).build();
    }

    /*
     * Helper functions for stemma graph production
     */

    // Helper function to get the correctly-quote sigil for a Witness node.
    private static String sigilDotString(Node witness) {
        String witnessSigil = witness.getProperty("sigil").toString();
        if (witness.getProperty("quotesigil", false).equals(true))
            witnessSigil = String.format("\"%s\"", witnessSigil);
        return witnessSigil;
    }

    /**
     * Returns all the stemmata associated with a tradition, in a format
     * suitable for inclusion in an XML file.
     *
     * @param tradId - the ID of the tradition
     * @return a string full of stemma dotfiles, one per line
     */
    String getAllStemmataAsDot(String tradId) {
        ArrayList<String> stemmaList = new ArrayList<>();

        try(Transaction tx = db.beginTx()) {
            //ExecutionEngine engine = new ExecutionEngine(db);
            // find all Stemmata associated with this tradition
            Result result = db.execute("match (t:TRADITION {id:'"+ tradId +
                    "'})-[:HAS_STEMMA]->(s:STEMMA) return s");

            Iterator<Node> stemmata = result.columnAs("s");
            while(stemmata.hasNext()) {
                String stemma = stemmata.next().getProperty("name").toString();
                Response resp = writeNeo4JStemma(tradId, stemma, true);

                stemmaList.add(resp.getEntity().toString());
            }
            tx.success();
        }

        return String.join("\n", stemmaList);
    }

    private Set<String> traverseStemma(Node stemma, Node archetype) {
        String stemmaName = (String) stemma.getProperty("name");
        Set<String> allPaths = new HashSet<>();

        // If the stemma is contaminated then we must pay attention to direction of link;
        // it isn't deterministic from archetype identification.
        Direction useDir = stemma.hasProperty("is_contaminated") ? Direction.OUTGOING : Direction.BOTH;

        // We need to traverse only those paths that belong to this stemma.
        PathExpander e = getExpander(useDir, stemmaName);
        for (Path nodePath: db.traversalDescription().breadthFirst()
                .expand(e)
                .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL)
                .traverse(archetype)) {
            Iterator<Node> orderedNodes = nodePath.nodes().iterator();
            Node sourceNode = orderedNodes.next();
            while (orderedNodes.hasNext()) {
                Node targetNode = orderedNodes.next();
                String source = sigilDotString(sourceNode);
                String target = sigilDotString(targetNode);
                allPaths.add(String.format("%s : %s", source, target));
                sourceNode = targetNode;
            }
        }
        return allPaths;
    }


    private void write(String str) throws IOException
    {
        out.write(str.getBytes());
    }


    private void writeFromDot(String dot)
    {
        GraphViz gv = new GraphViz();
        gv.add(dot);
        File result;
        try {
            result = File.createTempFile("graph_", ".svg");
        } catch (Exception e) {
            System.out.println("Could not write " + "svg" + " to temporary file");
            return;
        }
        gv.writeGraphToFile(gv.getGraph(gv.getDotSource(), "svg"), result);
    }

}