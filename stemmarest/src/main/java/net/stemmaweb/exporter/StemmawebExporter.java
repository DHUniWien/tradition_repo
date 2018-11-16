package net.stemmaweb.exporter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
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
import org.neo4j.graphdb.traversal.Uniqueness;
import org.w3c.dom.Document;

import com.sun.xml.txw2.output.IndentingXMLStreamWriter;

/**
 * This class provides methods for exporting GraphMl (XML) File from Neo4J in the old Stemmaweb format
 *
 * @author PSE FS 2015 Team2
 */

public class StemmawebExporter {
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
            put("base_label", new String[]{"dn18", "string"});    // Sequence
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
            put("layerlabel", new String[]{"dg8", "string"});
        }
    };

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

    public Response writeNeo4J(String tradId) {

        int edgeCountGraph1 = 0;
        int nodeCountGraph1 = 0;
        int edgeCountGraph2 = 0;
        int nodeCountGraph2 = 0;

        Node traditionNode = DatabaseService.getTraditionNode(tradId, db);
        if(traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity("No tradition found for this ID").build();
        Node traditionStartNode = DatabaseService.getStartNode(tradId, db);
        if(traditionStartNode == null)
            return Response.status(Status.NOT_FOUND).entity("No graph found for this tradition.").build();

        File file;
        try (Transaction tx = db.beginTx()) {

            file = File.createTempFile("output", ".xml");
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
                if(prop !=null && !prop.equals("id") && !prop.equals("section_id")) {
                    writer.writeStartElement("data");
                    writer.writeAttribute("key", graphMap.get(prop)[0]);
                    writer.writeCharacters(traditionNode.getProperty(prop).toString());
                    writer.writeEndElement();
                }
            }
            // backwards compatibility: write layer label "a.c."
            writer.writeStartElement("data");
            writer.writeAttribute("key", "dg8");
            writer.writeCharacters("a.c.");
            writer.writeEndElement();

            // extract stemmata
            writer.writeStartElement("data");
            writer.writeAttribute("key", graphMap.get("stemmata")[0]);

            DotExporter parser = new DotExporter(db);

            writer.writeCharacters(parser.getAllStemmataAsDot(tradId));
            writer.writeEndElement();

            long nodeId = 0;
            long edgeId = 0;
            for (Node node : db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .uniqueness(Uniqueness.NODE_GLOBAL)
                    .traverse(traditionStartNode).nodes()) {
                nodeCountGraph1++;
                props = node.getPropertyKeys();
                writer.writeStartElement("node");
                writer.writeAttribute("id", String.valueOf(node.getId()));
                writer.writeStartElement("data");
                writer.writeAttribute("key", nodeMap.get("id")[0]);
                writer.writeCharacters("n" + nodeId++);
                writer.writeEndElement();

                for(String prop : props) {
                    if(prop!=null && nodeMap.containsKey(prop)) {
                        writer.writeStartElement("data");
                        writer.writeAttribute("key",nodeMap.get(prop)[0]);
                        writer.writeCharacters((prop.equals("a.c.")) ? "(a.c.)" :node.getProperty(prop).toString());
                        writer.writeEndElement();
                    }
                }
                writer.writeEndElement(); // end node
            }

            String startNode;
            String endNode;
            for ( Relationship rel : db.traversalDescription()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL)
                    .traverse(traditionStartNode)
                    .relationships() ) {
                if(rel!=null) {
                    edgeCountGraph1++;
                    for (String property : rel.getPropertyKeys()) {
                        String[] witnesses = (String[]) rel.getProperty(property);
                        for (String witness : witnesses) {
                            writer.writeStartElement("edge");

                            writer.writeAttribute("source", rel.getStartNode().getId() + "");
                            writer.writeAttribute("target", rel.getEndNode().getId() + "");
                            writer.writeAttribute("id", "e" + edgeId++);

                            if (!property.equals("witnesses") && relationMap.containsKey(property)) {
                                writer.writeStartElement("data");
                                writer.writeAttribute("key", relationMap.get(property)[0]);
                                writer.writeCharacters(property);
                                writer.writeEndElement();
                            }

                            writer.writeStartElement("data");
                            writer.writeAttribute("key", relationMap.get("witness")[0]);
                            writer.writeCharacters(witness);
                            writer.writeEndElement(); // end data key
                            writer.writeEndElement(); // end edge
                        }

                    }
                }
            }
            writer.writeEndElement(); // graph

            // graph 2
            // get the same nodes again, but this time we will later also search for reading relations

            writer.writeStartElement("graph");
            writer.writeAttribute("edgedefault", "directed");
            writer.writeAttribute("id", "relationships");
            writer.writeAttribute("parse.edgeids", "canonical");
            // THIS IS CHANGED AFTERWARDS
            writer.writeAttribute("parse.edges", 0 + "");
            writer.writeAttribute("parse.nodeids", "canonical");
            // THIS IS CHANGED AFTERWARDS
            writer.writeAttribute("parse.nodes", 0 + "");
            writer.writeAttribute("parse.order", "nodesfirst");

            nodeId = 0;
            edgeId = 0;
            for (Node node : db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .uniqueness(Uniqueness.NODE_GLOBAL)
                    .traverse(traditionStartNode)
                    .nodes()) {
                nodeCountGraph2++;
                writer.writeStartElement("node");
                writer.writeAttribute("id", node.getId() + "");
                writer.writeStartElement("data");
                writer.writeAttribute("key", nodeMap.get("id")[0]);
                writer.writeCharacters("n" + nodeId++);
                writer.writeEndElement();
                writer.writeEndElement(); // end node
            }

            for (Node node : db.traversalDescription()
                    .depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .uniqueness(Uniqueness.NODE_GLOBAL)
                    .traverse(traditionStartNode)
                    .nodes()) {

                Iterable<Relationship> rels;
                rels = node.getRelationships(ERelations.RELATED, Direction.OUTGOING);
                for(Relationship rel : rels) {
                    edgeCountGraph2++;
                    props = rel.getPropertyKeys();
                    writer.writeStartElement("edge");
                    startNode = rel.getStartNode().getId() + "";
                    endNode = rel.getEndNode().getId() + "";
                    writer.writeAttribute("source", startNode);
                    writer.writeAttribute("target", endNode);
                    writer.writeAttribute("id", "e" + edgeId++);
                    for(String prop : props) {
                        // Skip internal properties like "colocation" on RELATED links
                        if (rel.hasProperty(prop) && relationMap.containsKey(prop)) {
                            String value = rel.getProperty(prop).toString();
                            if (!value.equals("")) {
                                writer.writeStartElement("data");
                                String keyId = relationMap.get(prop)[0];
                                writer.writeAttribute("key", keyId);
                                writer.writeCharacters(value);
                                writer.writeEndElement();
                            }
                        }
                    }
                    writer.writeEndElement(); // end edge
                }
            }
            writer.writeEndElement(); // end graph
            writer.writeEndElement(); // end graphml
            writer.flush();
            out.close();

            // Add edge and node count to graphs:

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(file.getAbsolutePath());

            // Get the staff element by tag name directly
            org.w3c.dom.Node graph0 = doc.getElementsByTagName("graph").item(0);

            org.w3c.dom.Node graph1 = doc.getElementsByTagName("graph").item(1);

            org.w3c.dom.NamedNodeMap attr = graph0.getAttributes();
            org.w3c.dom.Node edgesCount = attr.getNamedItem("parse.edges");
            edgesCount.setTextContent(edgeCountGraph1 + "");
            org.w3c.dom.Node nodesCount = attr.getNamedItem("parse.nodes");
            nodesCount.setTextContent(nodeCountGraph1+"");

            attr = graph1.getAttributes();
            edgesCount = attr.getNamedItem("parse.edges");
            edgesCount.setTextContent(edgeCountGraph2 + "");
            nodesCount = attr.getNamedItem("parse.nodes");
            nodesCount.setTextContent(nodeCountGraph2 + "");

            // TODO What is the point of this transformer call?
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult resultFile = new StreamResult(file);
            transformer.transform(source, resultFile);
            tx.success();
        } catch(Exception e) {
            e.printStackTrace();

            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error: Tradition could not be exported!")
                    .build();
        }

        return Response.ok(file.toString(), MediaType.APPLICATION_XML).build();
    }
}