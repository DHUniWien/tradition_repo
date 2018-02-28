package net.stemmaweb.exporter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.*;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.model.DisplayOptionModel;
import net.stemmaweb.printer.GraphViz;
import net.stemmaweb.rest.ERelations;

import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Section;
import net.stemmaweb.services.DatabaseService;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.BranchState;
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
        Node traditionNode = DatabaseService.getTraditionNode(tradId, db);
        Node startNode = DatabaseService.getStartNode(tradId, db);
        Node endNode = DatabaseService.getEndNode(tradId, db);
        if(startNode==null || endNode==null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        // Get the list of section nodes
        ArrayList<Node> sections = DatabaseService.getSectionNodes(tradId, db);
        if (sections == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        File output;
        String result;
        try (Transaction tx = db.beginTx()) {
            output = File.createTempFile("graph_", ".dot");
            out = new FileOutputStream(output);

            // Get the graph name - either the section name, or the tradition name if all sections
            // were requested
            String graphName = (sectionId != null) ? sections.get(0).getProperty("name").toString()
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
            Boolean subgraphWritten = false;

            for (Node sectionNode: sections) {
                // Did we request a specific section? If so, put out only that section.
                if (sectionId != null && !String.valueOf(sectionNode.getId()).equals(sectionId))
                    continue;

                // Get the number of witnesses we have
                ArrayList<Node> sectionWits = new Section(tradId, String.valueOf(sectionNode.getId()))
                        .collectSectionWitnesses();
                int numWits = sectionWits.size();

                Node sectionStartNode = DatabaseService.getStartNode(String.valueOf(sectionNode.getId()), db);
                Node sectionEndNode = DatabaseService.getEndNode(String.valueOf(sectionNode.getId()), db);
                // If we have requested a section, then that section's start and end are "the" start and end
                // for the whole graph.z
                if (sectionId != null) {
                    startNode = sectionStartNode;
                    endNode = sectionEndNode;
                }
                // HACK - now that we know which node is functioning as the start node, set the subgraph and
                // the silent node that keeps the graph straight. Make sure we only do this once.
                if (!subgraphWritten) {
                    write("\tsubgraph { rank=same \"n" + startNode.getId() + "\" \"#SILENT#\" }\n");
                    write("\t\"#SILENT#\" [shape=diamond,color=white,penwidth=0,label=\"\"];\n");
                    subgraphWritten = true;
                }

                for (Node node :  db.traversalDescription().breadthFirst()
                        .relationships(ERelations.SEQUENCE,Direction.OUTGOING)
                        .relationships(ERelations.LEMMA_TEXT,Direction.OUTGOING)
                        .uniqueness(Uniqueness.NODE_GLOBAL)
                        .traverse(sectionStartNode)
                        .nodes()) {

                    // Write out the node list in dot format
                    // Skip section start nodes, unless they are overall start nodes. These links will be
                    // tied to "section" i.e. section end nodes instead.
                    if ((!node.equals(sectionStartNode) && !node.equals(sectionEndNode))
                            || node.equals(startNode)
                            || node.equals(endNode)) {
                        write(nodeSpec(node, dm));
                    }

                    // The section end node should be displayed as a "section" node.
                    if (node.equals(sectionEndNode) && !node.equals(endNode)) {
                        String endLine = nodeSpec(node, dm);
                        write(endLine.replace("END", "SECTION_" + sectionNode.getId()));
                        lastSectionEndId = node.getId();
                    } else if (node.equals(sectionStartNode))
                        continue;

                    // Now get the sequence relationships between nodes.
                    for (Relationship rel : node.getRelationships(Direction.INCOMING, ERelations.SEQUENCE, ERelations.LEMMA_TEXT)) {
                        if (rel == null)
                            continue;
                        Node relStartNode = rel.getStartNode();
                        Long relStartNodeId = relStartNode.getId();

                        // Section-boundary sequence handling
                        if (relStartNode.equals(sectionStartNode) && !relStartNode.equals(startNode))
                            relStartNodeId = lastSectionEndId;

                        // LATER consider turning this red instead of labelling it
                        String label = rel.getType().toString().equals("LEMMA_TEXT") ? "(LEMMA)"
                                : sequenceLabel(convertProps(rel), numWits, dm);
                        Long rankDiff = (Long) node.getProperty("rank") - (Long) relStartNode.getProperty("rank");
                        write(relshipText(relStartNodeId, node.getId(), label, edgeId++,
                                calcPenWidth(convertProps(rel)), rankDiff));

                    }

                    // Retrieve reading relationships, if requested
                    if (dm.getIncludeRelated()) {
                        for (Relationship relatedRel : node.getRelationships(Direction.INCOMING, ERelations.RELATED)) {
                            write("\tn" + relatedRel.getStartNode().getId() + "->" + "n" +
                                    relatedRel.getEndNode().getId() + " [style=dotted, constraint=false, arrowhead=none, " +
                                    "label=\"" + relatedRel.getProperty("type").toString() + "\", id=\"e" +
                                    edgeId++ + "\"];\n");
                        }
                    }
                }
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
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity("Could not write file for export")
                    .build();
        }

        // Here is where to generate pictures from the file for debugging.
        // writeFromDot(output, "svg");

        return Response.ok().entity(result).build();
    }

    /*
     * Helper functions for variant graph production
     */

    private static String nodeSpec(Node node, DisplayOptionModel dm) {
        // Get the proper node ID
        String nodeDotId = "n" + node.getId();
        if (node.hasProperty("is_end") && (Boolean) node.getProperty("is_end")) nodeDotId = "__END__";
        else if (node.hasProperty("is_start") && (Boolean) node.getProperty("is_start")) nodeDotId = "__START__";

        // Get the node label
        String nodeLabel = node.getProperty("text").toString();
        if (dm.getShowNormalForm() && node.hasProperty("normal_form")
            && !node.getProperty("normal_form").toString().equals(nodeLabel))
            nodeLabel = "<" + nodeLabel + "<BR/><FONT COLOR=\"grey\">" + node.getProperty("normal_form").toString()
                    + "</FONT>>";
        else
            nodeLabel = "\"" + nodeLabel + "\"";

        // Put it all together
        return("\t" + "n" + node.getId() + " [id=\"" + nodeDotId + "\", label=" + nodeLabel + "];\n");
    }

    private static String sequenceLabel(Map<String, String[]> witnessInfo, int numWits, DisplayOptionModel dm) {
        String[] witnesses = {""};
        StringBuilder lex_str = new StringBuilder();
        String label = null;
        // Get the list of witnesses
        if (witnessInfo.containsKey("witnesses")) {
            witnesses = witnessInfo.get("witnesses");
            Arrays.sort(witnesses);
            label = "majority";
        }
        // Join up the list of witnesses if the label should not be 'majority'
        if (dm.getDisplayAllSigla() || numWits < 7 || witnesses.length <= (numWits / 2)) {
            Iterator<String> it = Arrays.asList(witnesses).iterator();
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
        if (label != null) lex_str.append(label);
        for (String prop : witnessInfo.keySet()) {
            if (prop.equals("witnesses"))
                continue;
            String[] layerwits = witnessInfo.get(prop);
            Arrays.sort(layerwits);
            for (String s : Arrays.asList(layerwits)) {
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

    private static String relshipText(Long sNodeId, Long eNodeId, String label, long edgeId, String pWidth, Long rankDiff)
    {
        String text;
        try {
            text = "\tn" + sNodeId + "->" + "n" + eNodeId + " [label=\"" + label
                    + "\", id=\"e" + edgeId + "\", penwidth=\"" + pWidth + "\"";
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
        if (witness.hasProperty("quotesigil") && (Boolean) witness.getProperty("quotesigil"))
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
        PathExpander e = new PathExpander() {
            @Override
            public java.lang.Iterable expand(Path path, BranchState branchState) {
                ArrayList<Relationship> goodPaths = new ArrayList<>();
                for (Relationship link : path.endNode()
                        .getRelationships(ERelations.TRANSMITTED, useDir)) {
                    if (link.getProperty("hypothesis").equals(stemmaName)) {
                        goodPaths.add(link);
                    }
                }
                return goodPaths;
            }

            @Override
            public PathExpander reverse() {
                return null;
            }
        };
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