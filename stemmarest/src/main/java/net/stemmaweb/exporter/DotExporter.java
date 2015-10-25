package net.stemmaweb.exporter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
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

    public DotExporter(GraphDatabaseService db){
        this.db = db;
    }

    public Response parseNeo4J(String tradId)
    {
        Node startNode = DatabaseService.getStartNode(tradId, db);
        if(startNode==null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        File output;
        String result;
        try (Transaction tx = db.beginTx()) {
            output = File.createTempFile("graph_", "dot");
            out = new FileOutputStream(output);

            write("digraph { ");

            long edgeId = 0;
            String subgraph = "";
            for (Node node : db.traversalDescription().breadthFirst()
                    .relationships(ERelations.SEQUENCE,Direction.OUTGOING)
                    .uniqueness(Uniqueness.NODE_GLOBAL)
                    .traverse(startNode)
                    .nodes()) {

                write("n" + node.getId() + " [label=\"" + node.getProperty("text").toString()
                        + "\"];");

                for(Relationship rel : node.getRelationships(Direction.OUTGOING,ERelations.SEQUENCE)) {
                    if(rel != null && rel.hasProperty("witnesses")) {
                        String[] witnesses = (String[]) rel.getProperty("witnesses");
                        String lex_str = "";
                        Iterator<String> it = Arrays.asList(witnesses).iterator();
                        while(it.hasNext()) {
                            lex_str += "" + it.next() + "";
                            if(it.hasNext()) {
                                lex_str += ",";
                            }
                        }
                        write("n" + rel.getStartNode().getId() + "->" + "n" +
                                rel.getEndNode().getId() + " [label=\""+ lex_str +"\", id=\"e"+
                                edgeId++ +"\"];");
                    }
                }
                for(Relationship rel : node.getRelationships(Direction.OUTGOING,
                        ERelations.RELATED)) {
                    subgraph += "n" + rel.getStartNode().getId() + "->" + "n" +
                            rel.getEndNode().getId() + " [style=dotted, label=\""+
                            rel.getProperty("type").toString() +"\", id=\"e"+ edgeId++ +"\"];";
                }
            }

            write("subgraph { edge [dir=none]");
            write(subgraph);
            write(" } }");

            out.flush();
            out.close();

            // Now pull the string back out of the output file.
            byte[] encDot = Files.readAllBytes(output.toPath());
            result = new String(encDot, Charset.forName("utf-8"));

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

        String joinString = singleLine ? " " : "\n";
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

    public Response parseNeo4JStemma(String tradId, String stemmaTitle) {
        return parseNeo4JStemma(tradId, stemmaTitle, false);
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
            public Iterable<Relationship> expand(Path path, BranchState branchState) {
                ArrayList<Relationship> goodPaths = new ArrayList<>();
                Iterator<Relationship> stemmaLinks = path.endNode()
                        .getRelationships(ERelations.TRANSMITTED, Direction.BOTH).iterator();
                while(stemmaLinks.hasNext()) {
                    Relationship link = stemmaLinks.next();
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