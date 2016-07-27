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

import net.stemmaweb.printer.GraphViz;
import net.stemmaweb.rest.ERelations;

import net.stemmaweb.rest.Nodes;
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

    private String calcPenWidth(String lexString)
    {
        return df2.format(0.8 + 0.2 * (lexString.length() - lexString.replace(",", "").length()+1));
    }

    private String relshipText(Long sNodeId, Long eNodeId, String label, long edgeId, String pWidth, Long rankDiff)
    {
        String text;
        try {
            text = "n" + sNodeId + "->" + "n" + eNodeId + " [label=\"" + label
                    + "\", id=\"e" + edgeId + "\", penwidth=\"" + pWidth + "\"";
            if (rankDiff > 1)
                text += ", minlen=\"" + rankDiff + "\"";
            text += "];";
        } catch (Exception e) {
            text = null;
        }
        return text;
    }

    public Response parseNeo4J(String tradId)
    {
        Node startNode = DatabaseService.getStartNode(tradId, db);
        Node endNode = DatabaseService.getEndNode(tradId, db);
        if(startNode==null || endNode==null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        // Get the section node IDs, if there are any - otherwise get tradition ID string
        ArrayList<Node> sections = DatabaseService.getSectionNodes(tradId, db);
        if (sections == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        ArrayList<String> sectionIds = new ArrayList<>();
        if (sections.size() == 0) {
            sectionIds.add(tradId);
        } else {
            sections.forEach(x -> sectionIds.add(String.valueOf(x.getId())));
        }

        File output;
        String result;
        boolean includeRelatedRelationships = true;
        try (Transaction tx = db.beginTx()) {
            output = File.createTempFile("graph_", ".dot");
            out = new FileOutputStream(output);

            write("digraph { ");
            write("graph [rankdir=\"LR\"] ");
            long edgeId = 0;
            long global_rank = 0;
            Hashtable<String, Long[]> knownWitnesses = new Hashtable<>();

            for (String sectionId: sectionIds) {
                Node sectionStartNode = DatabaseService.getStartNode(String.valueOf(sectionId), db);
                Node sectionEndNode = DatabaseService.getEndNode(String.valueOf(sectionId), db);
                for (Node node :  db.traversalDescription().breadthFirst()
                        .relationships(ERelations.SEQUENCE,Direction.OUTGOING)
                        .relationships(ERelations.LEMMA_TEXT,Direction.OUTGOING)
                        .uniqueness(Uniqueness.NODE_GLOBAL)
                        .traverse(sectionStartNode)
                        .nodes()) {

                    if ((!node.equals(sectionStartNode) && !node.equals(sectionEndNode))
                            || node.equals(startNode)
                            || node.equals(endNode)) {
                        write("n" + node.getId()
                                + " [label=\"" + node.getProperty("text").toString() + "\"];");
                    }

                    if (node.equals(sectionStartNode) || node.equals(sectionEndNode))
                        continue;
//                    nodes_in_section += 1L;
                    for (Relationship rel : node.getRelationships(Direction.INCOMING, ERelations.SEQUENCE, ERelations.LEMMA_TEXT)) {
                        if (rel == null)
                            continue;
                        Node relStartNode = rel.getStartNode();
                        Long relStartNodeId = relStartNode.getId();
                        Long node_rank;
                        try {
                            node_rank = (Long) node.getProperty("rank");
                        } catch (Exception e) {
                            node_rank = ((Integer)node.getProperty("rank")).longValue();
                        }

                        if (!relStartNode.equals(sectionStartNode)) {
                            String[] witnesses = {""};
                            String lex_str = "";
                            if (rel.hasProperty("witnesses")) {
                                witnesses = (String[]) rel.getProperty("witnesses");
                                Arrays.sort(witnesses);
                            }
                            Iterator<String> it = Arrays.asList(witnesses).iterator();
                            while (it.hasNext()) {
                                lex_str += it.next();
                                if (it.hasNext()) {
                                    lex_str += ",";
                                }
                            }

                            write(relshipText(relStartNodeId, node.getId(), lex_str, edgeId++, calcPenWidth(lex_str), 1L));
                        } else {
                            Hashtable<Long, String> sectionWitnesses = new Hashtable<>();
                            Hashtable<Long, Long> sectionRanks = new Hashtable<>();
                            String[] witnesses = {""};
                            if (rel.hasProperty("witnesses")) {
                                witnesses = (String[]) rel.getProperty("witnesses");
                                Arrays.sort(witnesses);
                            }
                            Iterator<String> it = Arrays.asList(witnesses).iterator();
                            while (it.hasNext()) {
                                String sigil = it.next();
                                if (!knownWitnesses.containsKey(sigil)) {
                                    Long[] dummy = {startNode.getId(), 0L};
                                    knownWitnesses.put(sigil, dummy);
                                }
                                Long[] predNodeInfo = knownWitnesses.get(sigil);
                                Long predNodeId = predNodeInfo[0];
                                Long predNodeRank = predNodeInfo[1];
                                if (!sectionWitnesses.containsKey(predNodeId)) {
                                    sectionWitnesses.put(predNodeId, sigil);
                                    sectionRanks.put(predNodeId, predNodeRank);
                                } else {
                                    String existingWitnesses = sectionWitnesses.get(predNodeId);
                                    sectionWitnesses.replace(predNodeId, existingWitnesses + "," + sigil);
                                }
                            }
                            Enumeration e = sectionWitnesses.keys();

                            while (e.hasMoreElements()) {
                                relStartNodeId = (Long)e.nextElement();
                                String relText = sectionWitnesses.get(relStartNodeId);
                                Long rankDiff = (global_rank + node_rank) - sectionRanks.get(relStartNodeId);
                                write(relshipText(relStartNodeId, rel.getEndNode().getId(), relText, edgeId++, calcPenWidth(relText), rankDiff));
                            }
                        }
                    }
                    // retrieve information for the subgraph (Related Relations)
                    if (includeRelatedRelationships) {
                        for (Relationship relatedRel : node.getRelationships(Direction.INCOMING, ERelations.RELATED)) {
                            write("n" + relatedRel.getStartNode().getId() + "->" + "n" +
                                    relatedRel.getEndNode().getId() + " [style=dotted, label=\"" +
                                    relatedRel.getProperty("type").toString() + "\",id=\"e" +
                                    edgeId++ + "\"];");
                        }
                    }
                }
                // finalize the section:
                // for each witness, calculate and store the global rank of the last node used
                // this is necessary, since the rank start with 0 in each section
                // we use this to connect the necessary nodes of the next session with the stored nodes.
                long section_max_rank = 0;
                for (Relationship rel : sectionEndNode.getRelationships(Direction.INCOMING, ERelations.SEQUENCE, ERelations.LEMMA_TEXT)) {
                    if (rel == null)
                        continue;
                    String[] witnesses = {""};
                    Node relStartNode = rel.getStartNode();
                    Long relStartNodeId = relStartNode.getId();

                    // get section's highest rank for the current witness(es)
                    Long witness_section_max_rank;
                    try {
                        witness_section_max_rank = (Long)relStartNode.getProperty("rank");
                    } catch (Exception e) {
                        witness_section_max_rank = ((Integer)relStartNode.getProperty("rank")).longValue();
                    }
                    section_max_rank = Math.max(witness_section_max_rank, section_max_rank);

                    if (rel.hasProperty("witnesses")) {
                        witnesses = (String[]) rel.getProperty("witnesses");
                        Arrays.sort(witnesses);
                    }
                    for (String s : Arrays.asList(witnesses)) {
                        Long[] dummy = {relStartNodeId, global_rank + witness_section_max_rank};
                        knownWitnesses.replace(s, dummy);
                    }
                }
                global_rank += section_max_rank;
            }

            // finalize the graph by connecting all used witnesses to the end-node
            Hashtable<Long, String> usedWitnesses = new Hashtable<>();
            Hashtable<Long, Long> usedWitnessesRanks = new Hashtable<>();
            Enumeration e = knownWitnesses.keys();
            while (e.hasMoreElements()) {
                String sigil = (String)e.nextElement();
                Long sNodeId = knownWitnesses.get(sigil)[0];
                if (!usedWitnesses.containsKey(sNodeId)) {
                    usedWitnesses.put(sNodeId, sigil);
                    usedWitnessesRanks.put(sNodeId, knownWitnesses.get(sigil)[1]);
                } else {
                    String existingWitnesses = usedWitnesses.get(sNodeId);
                    usedWitnesses.replace(sNodeId, existingWitnesses + "," + sigil);
                }
            }

            e = usedWitnesses.keys();
            while (e.hasMoreElements()) {
                Long sNodeId = (Long)e.nextElement();
                String relText = usedWitnesses.get(sNodeId);
                Long rankDiff = global_rank - usedWitnessesRanks.get(sNodeId);
                write(relshipText(sNodeId, endNode.getId(), relText, edgeId++, calcPenWidth(relText), rankDiff));
            }
            write(" }");

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

    public Response parseNeo4JStemma(String tradId, String stemmaTitle, Boolean singleLine)
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
        writeFromDot(output, "svg");

        return Response.ok(output).build();
    }

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
    public String getAllStemmataAsDot(String tradId) {
        ArrayList<String> stemmaList = new ArrayList<>();

        try(Transaction tx = db.beginTx()) {
            //ExecutionEngine engine = new ExecutionEngine(db);
            // find all Stemmata associated with this tradition
            Result result = db.execute("match (t:TRADITION {id:'"+ tradId +
                    "'})-[:HAS_STEMMA]->(s:STEMMA) return s");

            Iterator<Node> stemmata = result.columnAs("s");
            while(stemmata.hasNext()) {
                String stemma = stemmata.next().getProperty("name").toString();
                Response resp = parseNeo4JStemma(tradId, stemma, true);

                stemmaList.add(resp.getEntity().toString());
            }
            tx.success();
        }

        return String.join("\n", stemmaList);
    }

    private Set<String> traverseStemma(Node stemma, Node archetype) {
        String stemmaName = (String) stemma.getProperty("name");
        Set<String> allPaths = new HashSet<>();

        // We need to traverse only those paths that belong to this stemma.
        PathExpander e = new PathExpander() {
            @Override
            public java.lang.Iterable expand(Path path, BranchState branchState) {
                ArrayList<Relationship> goodPaths = new ArrayList<>();
                for (Relationship link : path.endNode()
                        .getRelationships(ERelations.TRANSMITTED, Direction.BOTH)) {
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
                .uniqueness(Uniqueness.NODE_PATH)
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


    private File writeFromDot(String dot, String format)
    {
        GraphViz gv = new GraphViz();
        gv.add(dot);
        File result;
        try {
            result = File.createTempFile("graph_", "." + format);
        } catch (Exception e) {
            System.out.println("Could not write " + format + " to temporary file");
            return null;
        }
        gv.writeGraphToFile(gv.getGraph(gv.getDotSource(), format), result);
        return result;
    }

}