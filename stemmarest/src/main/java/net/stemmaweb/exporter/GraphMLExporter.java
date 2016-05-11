package net.stemmaweb.exporter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import net.stemmaweb.rest.ERelations;

import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.graphdb.traversal.Uniqueness;
import org.w3c.dom.Document;

import com.sun.xml.txw2.output.IndentingXMLStreamWriter;

/**
 * This class provides methods for exporting GraphMl (XML) File from Neo4J
 *
 * @author PSE FS 2015 Team2
 */
public class GraphMLExporter {
    private GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private GraphDatabaseService db = dbServiceProvider.getDatabase();

    private HashMap<String,String[]> nodeMap = new HashMap<String, String[]>() {
        {
            put("grammar_invalid", new String[]{"dn0", "boolean"});
            put("id", new String[]{"dn1", "string"});
            put("is_common", new String[]{"dn2", "boolean"});
            put("is_end", new String[]{"dn3", "boolean"});
            put("is_lacuna", new String[]{"dn4", "boolean"});
            put("is_lemma", new String[]{"dn5", "boolean"});
            put("is_nonsense", new String[]{"dn6", "boolean"});
            put("is_ph", new String[]{"dn7", "boolean"});
            put("is_start", new String[]{"dn8", "boolean"});
            put("join_next", new String[]{"dn9", "boolean"});
            put("join_prior", new String[]{"dn10", "boolean"});
            put("language", new String[]{"dn11", "string"});
            put("witnesses", new String[]{"dn12", "string"});
            put("normal_form", new String[]{"dn13", "string"});
            put("rank", new String[]{"dn14", "int"});
            put("text", new String[]{"dn15", "string"});
            put("label", new String[]{"dn16", "string"});
            put("name", new String[]{"dn17", "string"});         // Sequence
            put("baselabel", new String[]{"dn18", "string"});    // Sequence
            put("sep_char", new String[]{"dn19", "string"});     // Sequence
        }
    };
    private HashMap<String,String[]> relationMap = new HashMap<String, String[]>() {
        {
            put("a_derivable_from_b", new String[]{"de0", "boolean"});
            put("alters_meaning", new String[]{"de1", "int"});
            put("annotation", new String[]{"de2", "string"});
            put("b_derivable_from_a", new String[]{"de3", "boolean"});
            put("displayform", new String[]{"de4", "string"});
            put("extra", new String[]{"de5", "string"});
            put("is_significant", new String[]{"de6", "string"});
            put("non_independent", new String[]{"de7", "boolean"});
            put("reading_a", new String[]{"de8", "string"});
            put("reading_b", new String[]{"de9", "string"});
            put("scope", new String[]{"de10", "string"});
            put("witness", new String[]{"de11", "string"});
            put("type", new String[]{"de12", "string"});
            put("type_related", new String[]{"de13", "string"});
        }
    };
    private HashMap<String,String[]> graphMap = new HashMap<String, String[]>() {
        {
            put("language", new String[]{"dg0", "string"});
            put("name", new String[]{"dg1", "string"});
            put("public", new String[]{"dg2", "boolean"});
            put("stemmata", new String[]{"dg3", "string"});
            put("stemweb_jobid", new String[]{"dg4", "string"});
            put("user", new String[]{"dg5", "string"});
            put("version", new String[]{"dg6", "string"});
            put("direction", new String[]{"dg7", "string"});
        }
    };

    private long globalRank;

    private void writeKeys(XMLStreamWriter writer, HashMap<String, String[]> currentMap, String kind) {
        try {
            for (Map.Entry<String, String[]> entry : currentMap.entrySet()) {
                String[] values = entry.getValue();
                writer.writeEmptyElement("key");
                writer.writeAttribute("attr.name", entry.getKey());
                writer.writeAttribute("attr.type", values[1]);
                writer.writeAttribute("for", kind);
                writer.writeAttribute("id", values[0]);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }

    }

    private void writeNode(XMLStreamWriter writer, Node node) {
        try {
            Iterable<String> props = node.getPropertyKeys();

            writer.writeStartElement("node");
            writer.writeAttribute("id", String.valueOf(node.getId()));

            writer.writeStartElement("data");
            writer.writeAttribute("key", nodeMap.get("label")[0]);
            writer.writeCharacters(((ArrayList) node.getLabels()).get(0).toString());
            writer.writeEndElement();

            for (String prop : props) {
                if (prop != null && nodeMap.get(prop) != null) {
                    String value;
                    if (prop.equals("rank")) {
                        Long rank;
                        try {
                            rank = ((Long) node.getProperty(prop));
                        } catch (Exception e) {
                            rank = (((Integer) node.getProperty(prop)).longValue());
                        }
                        rank = rank + globalRank;
                        value = rank.toString();
                    } else {
                        value = node.getProperty(prop).toString();
                    }
                    writer.writeStartElement("data");
                    writer.writeAttribute("key", nodeMap.get(prop)[0]);
                    writer.writeCharacters(value);
                    writer.writeEndElement();
                }
            }
            writer.writeEndElement(); // end node
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private void writeEdge(XMLStreamWriter writer, Relationship edge, long edgeId) {
        try {
            String[] witnesses = {""};
            if (edge.hasProperty("witnesses"))
                witnesses = (String[])edge.getProperty("witnesses");
            //String[] witnesses = (String[]) rel.getProperty(property);
            for (String witness : witnesses) {
                writer.writeStartElement("edge");

                writer.writeAttribute("source", edge.getStartNode().getId() + "");
                writer.writeAttribute("target", edge.getEndNode().getId() + "");
                writer.writeAttribute("id", "e" + edgeId + witness);

                writer.writeStartElement("data");
                writer.writeAttribute("key", relationMap.get("type")[0]);
                writer.writeCharacters(edge.getType().name());
                writer.writeEndElement(); // end data

                if (edge.isType(ERelations.SEQUENCE)) {
                    writer.writeStartElement("data");
                    writer.writeAttribute("key", relationMap.get("witness")[0]);
                    writer.writeCharacters(witness);
                    writer.writeEndElement(); // end data key
                } else if (edge.isType(ERelations.RELATED) && edge.hasProperty("type")) {
                    writer.writeStartElement("data");
                    writer.writeAttribute("key", relationMap.get("type_related")[0]);
                    writer.writeCharacters(edge.getProperty("type").toString());
                    writer.writeEndElement(); // end data key
                }

                for (String prop : edge.getPropertyKeys()) {
                    if (!prop.equals("witnesses") && (!prop.equals("type"))) {
                        writer.writeStartElement("data");
                        writer.writeAttribute("key", relationMap.get(prop)[0]);
                        writer.writeCharacters(prop);
                        writer.writeEndElement(); // end data
                    }
                }
                writer.writeEndElement(); // end edge
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public Response parseNeo4J(String tradId) {

        int edgeCountGraph1 = 0;
        int nodeCountGraph1 = 0;
        boolean includeRelatedRelations = false;

        Node traditionStartNode = DatabaseService.getStartNode(tradId, db);
        Node traditionEndNode = DatabaseService.getEndNode(tradId, db);
        if(traditionStartNode==null || traditionEndNode==null) {
            return Response.status(Status.NOT_FOUND).entity("No graph found.").build();
        }

        Node traditionNode = DatabaseService.getTraditionNode(tradId, db);
        ArrayList<Node> sections = DatabaseService.getSectionNodes(tradId, db);
        if (sections == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        if (sections.size() == 0) {
            sections.add(traditionNode);
        }

        File file;
        String result;
        try (Transaction tx = db.beginTx()) {
            file = File.createTempFile("output", ".graphml");
            OutputStream out = new FileOutputStream(file);

            XMLOutputFactory output = XMLOutputFactory.newInstance();
            XMLStreamWriter writer = new IndentingXMLStreamWriter(output.createXMLStreamWriter(out));

            writer.writeStartDocument();

            writer.writeStartElement("graphml");
            writer.writeAttribute("xmlns","http://graphml.graphdrawing.org/xmlns");
            writer.writeAttribute("xmlns:xsi","http://www.w3.org/2001/XMLSchema-instance");
            writer.writeAttribute("xsi:schemaLocation","http://graphml.graphdrawing.org/xmlns http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd");

            // ####### KEYS START #######################################

            writeKeys(writer, graphMap, "graph");
            writeKeys(writer, nodeMap, "node");
            writeKeys(writer, relationMap, "edge");

            // ####### KEYS END #######################################
            // graph 1

            Iterable<String> props;

            writer.writeStartElement("graph");
            // TODO convert tradition name safely into XML Name
            writer.writeAttribute("id", "Tradition");
            writer.writeAttribute("edgedefault", "directed");
            //writer.writeAttribute("id", traditionNode.getProperty("dg1").toString());
            writer.writeAttribute("parse.edgeids", "canonical");
            // THIS IS CHANGED AFTERWARDS
            writer.writeAttribute("parse.edges", 0+"");
            writer.writeAttribute("parse.nodeids", "canonical");
            // THIS IS CHANGED AFTERWARDS
            writer.writeAttribute("parse.nodes", 0+"");
            writer.writeAttribute("parse.order", "nodesfirst");

            props = traditionNode.getPropertyKeys();
            for(String prop : props) {
                if(prop !=null && !prop.equals("id") && !prop.equals("tradition_id") && graphMap.containsKey(prop)) {
                    writer.writeStartElement("data");
                    writer.writeAttribute("key", graphMap.get(prop)[0]);
                    writer.writeCharacters(traditionNode.getProperty(prop).toString());
                    writer.writeEndElement();
                }
            }
            // extract stemmata
            writer.writeStartElement("data");
            writer.writeAttribute("key", "dg3");

            DotExporter parser = new DotExporter(db);

            writer.writeCharacters(parser.getAllStemmataAsDot(tradId));
            writer.writeEndElement(); // end data

            long edgeId = 0;

            TraversalDescription nodeTraversal = db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .relationships(ERelations.LEMMA_TEXT, Direction.OUTGOING)
                    .uniqueness(Uniqueness.NODE_GLOBAL);

            TraversalDescription relTraversal = db.traversalDescription()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .relationships(ERelations.LEMMA_TEXT, Direction.OUTGOING)
                    .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL);
            if (includeRelatedRelations)
                relTraversal = relTraversal.relationships(ERelations.RELATED, Direction.BOTH);

            for (Node sectionNode: sections) {
                long max_rank_section = 0;

                Node sectionStartNode = DatabaseService.getStartNode(String.valueOf(sectionNode.getId()), db);
                writeNode(writer, sectionNode);

                for (Node node : nodeTraversal.traverse(sectionStartNode).nodes()) {
                    nodeCountGraph1++;
                    writeNode(writer, node);
                    if (node.hasProperty("rank")) {
                        Long rank;
                        try {
                            rank = (Long)node.getProperty("rank");
                        } catch (Exception e) {
                            rank = ((Integer)node.getProperty("rank")).longValue();
                        }
                        max_rank_section = Math.max(max_rank_section, rank);
                    }
                }
                globalRank += max_rank_section;
            }


            for (Node sectionNode: sections) {
                // write all outgoing nodes from the current section node
                for (Relationship rel : sectionNode.getRelationships(Direction.OUTGOING)) {
                    writeEdge(writer, rel, edgeId++);
                }

                Node sectionStartNode = DatabaseService.getStartNode(String.valueOf(sectionNode.getId()), db);
                for (Relationship rel : relTraversal.traverse(sectionStartNode).relationships()) {
                    if (rel != null) {
                        edgeCountGraph1++;
                        writeEdge(writer, rel, edgeId++);
                    }
                }
            }
            writer.writeEndElement(); // graph
            writer.writeEndElement(); // end graphml
            writer.flush();
            out.close();

            // Add edge and node count to graphs:

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(file.getAbsolutePath());

            // Get the staff element by tag name directly
            org.w3c.dom.Node graph0 = doc.getElementsByTagName("graph").item(0);

            org.w3c.dom.NamedNodeMap attr = graph0.getAttributes();
            org.w3c.dom.Node edgesCount = attr.getNamedItem("parse.edges");
            edgesCount.setTextContent(edgeCountGraph1 + "");
            org.w3c.dom.Node nodesCount = attr.getNamedItem("parse.nodes");
            nodesCount.setTextContent(nodeCountGraph1+"");

            // Now pull the string back out of the output file.
            byte[] encDot = Files.readAllBytes(file.toPath());
            result = new String(encDot, Charset.forName("utf-8"));

            // Remove the following line, if you want to keep the created file
            Files.deleteIfExists(file.toPath());

            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity("An internal error occured during GraphML-export")
                    .build();
        }
        return Response.ok().entity(result).build();
    }
}