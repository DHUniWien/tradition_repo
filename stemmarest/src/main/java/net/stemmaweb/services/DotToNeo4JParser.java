package net.stemmaweb.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.alexmerz.graphviz.ParseException;
import com.alexmerz.graphviz.objects.Graph;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.IResource;
import net.stemmaweb.rest.Nodes;

import com.alexmerz.graphviz.Parser;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

/**
 * This class provides methods for exporting Dot File from Neo4J
 * @author PSE FS 2015 Team2
 */
public class DotToNeo4JParser implements IResource
{
    private GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private GraphDatabaseService db = dbServiceProvider.getDatabase();
    private String dot = "";
    private List<Node> nodes = new ArrayList<>();

    public DotToNeo4JParser(GraphDatabaseService db) {
        this.db = db;
    }

    public Response parseDot(String dot, String tradId) {
        this.dot = dot;

        // Try the parser
        StringBuffer dotstream = new StringBuffer(dot);
        Parser p = new Parser();
        try {
            p.parse(dotstream);
        } catch (ParseException e) {
            e.printStackTrace();
            return Response.status(Status.BAD_REQUEST).build();
        }
        ArrayList<Graph> parsedgraphs = p.getGraphs();

        try (Transaction tx = db.beginTx()) {
            while(nextObject(tradId));
            if(nodes.size()==0) {
                return Response.status(Status.NOT_FOUND).build();
            } else {
                nodes.get(0).createRelationshipTo(nodes.get(1), ERelations.STEMMA);
            }
            tx.success();
        } catch(Exception e) {
            e.printStackTrace();
            return Response.status(Status.NOT_FOUND).build();
        }
        return Response.ok(dot).build();
    }

    /**
     * parses a dot into neo4j, object for object, returns false when no more objects exist
     * adds all nodes below tradId
     * @return
     */
    private boolean nextObject(String tradId) throws Exception
    {
        // check either graph or node/edge is next
        int i;

        if(((dot.indexOf('{') == -1) && (dot.indexOf(';') != -1)) ||
                (dot.indexOf(';') < dot.indexOf('{'))) {
            i = dot.indexOf(';');
            String tmp = dot.substring(0, i);
            boolean undirected;

            if((undirected = tmp.contains("--")) || tmp.contains("-&gt;") || tmp.contains("->")) {
                String[] spliced;

                if(undirected) {
                    spliced = tmp.split("--");
                } else {
                    spliced = (tmp.contains("->")) ? tmp.split("->") : tmp.split("-&gt;");
                }
                spliced[0] = spliced[0].replaceAll(" ", "");
                spliced[1] = spliced[1].replaceAll(" ", "");
                Node source = findNodeById(spliced[0]);
                Node target = findNodeById(spliced[1]);
                if(source != null && target != null) {
                    source.createRelationshipTo(target, ERelations.STEMMA);
                }
            } else if(tmp.length()>0) {
                Node node = db.createNode(Nodes.WITNESS);
                if(dot.indexOf('[')!=-1) {
                    String[] spliced = tmp.split("\\[");

                    spliced[0] = spliced[0].replaceAll(" ", "");
                    spliced[1] = spliced[1].replaceAll(" ", "");

                    node.setProperty("id", spliced[0].trim());

                    spliced[1] = spliced[1].replaceAll("\\[", "");
                    spliced[1] = spliced[1].replaceAll("\\]", "");

                    // replace this with the use of the correct delimiter of properties
                    String[] sub = spliced[1].split(",");
                    for(String str : sub) {
                        String[] arr = str.split("=");
                        node.setProperty(arr[0].trim(), arr[1].trim());
                    }
                    nodes.add(node);
                }
            }
            dot = dot.substring(i+1);
        } else if((i = dot.indexOf('{')) < dot.indexOf(';') && i>0) {
            // this holds something like 'graph stemma {' or 'digraph "TM hypothesis" {'
            Node node = db.createNode(Nodes.STEMMA);
            String tmp = dot.substring(0, i);
            if(tmp.contains("digraph")) {
                node.setProperty("type", "digraph");
            } else if(tmp.contains("graph")) {
                node.setProperty("type", "graph");
            }
            String[] declaration = tmp.split(" ");
            if(declaration.length >= 2) {
                String name = String.join(" ", Arrays.copyOfRange(declaration, 1, declaration.length));
                name = name.replaceAll("\"", "");
                node.setProperty("name", name);
            } else {
                throw new Exception("Could not find stemma name in graph declaration");
                // Something went wrong with parsing.
            }

            Node trad = db.findNodes(Nodes.TRADITION, "id", tradId).next();
            if(trad != null) {
                trad.createRelationshipTo(node, ERelations.STEMMA);
            }
            nodes.add(node);
            dot = dot.substring(i+1);
        }
        else {
            return false;
        }
        return true;
    }

    private Node findNodeById(String id)
    {
        for(Node n : nodes) {
            if(n.hasProperty("id") && n.getProperty("id").equals(id)) {
                return n;
            }
        }
        return null;
    }
}