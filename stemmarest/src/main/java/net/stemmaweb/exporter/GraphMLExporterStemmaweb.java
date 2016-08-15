package net.stemmaweb.exporter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import net.stemmaweb.rest.ERelations;

import net.stemmaweb.rest.Nodes;
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
 * This class provides methods for exporting GraphMl (XML) File from Neo4J
 *
 * @author PSE FS 2015 Team2
 */

public class GraphMLExporterStemmaweb {
    private GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private GraphDatabaseService db = dbServiceProvider.getDatabase();

    private HashMap<String, String> nodeMap = new HashMap<String, String>() {
        {
            put("grammar_invalid", "dn0");
            put("id", "dn1");
            put("is_common", "dn2");
            put("is_end", "dn3");
            put("is_lacuna", "dn4");
            put("is_lemma", "dn5");
            put("is_nonsense", "dn6");
            put("is_ph", "dn7");
            put("is_start", "dn8");
            put("join_next", "dn9");
            put("join_prior", "dn10");
            put("language", "dn11");
            put("witnesses", "dn12");
            put("normal_form", "dn13");
            put("rank", "dn14");
            put("text", "dn15");
        }
    };
    private HashMap<String, String> relationMap = new HashMap<String, String>() {
        {
            put("a_derivable_from_b", "de0");
            put("alters_meaning", "de1");
            put("annotation", "de2");
            put("b_derivable_from_a", "de3");
            put("displayform", "de4");
            put("extra", "de5");
            put("is_significant", "de6");
            put("non_independent", "de7");
            put("reading_a", "de8");
            put("reading_b", "de9");
            put("scope", "de10");
            put("type", "de11");
            put("witness", "de12");
        }
    };
    private HashMap<String, String> graphMap = new HashMap<String, String>() {
        {
            put("language", "dg0");
            put("name", "dg1");
            put("public", "dg2");
            put("stemmata", "dg3");
            put("stemweb_jobid", "dg4");
            put("user", "dg5");
            put("version", "dg6");
            put("direction", "dg7");
        }
    };

    private boolean writeKey(XMLStreamWriter writer, String name_val, String type_val, String for_val, String id_val) {
        try {
            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", name_val);
            writer.writeAttribute("attr.type", type_val);
            writer.writeAttribute("for", for_val);
            writer.writeAttribute("id", id_val);
        } catch (XMLStreamException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public Response parseNeo4J(String tradId) {

        int edgeCountGraph1 = 0;
        int nodeCountGraph1 = 0;
        int edgeCountGraph2 = 0;
        int nodeCountGraph2 = 0;

        Node traditionNode;
        Node traditionStartNode = DatabaseService.getStartNode(tradId, db);
        if(traditionStartNode == null) {
            return Response.status(Status.NOT_FOUND).entity("No graph found.").build();
        }

        try (Transaction tx = db.beginTx()) {
            traditionNode = db.findNode(Nodes.TRADITION, "id", tradId);
            if(traditionNode == null) {
                return Response.status(Status.NOT_FOUND).build();
            }
            tx.success();
        }

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

            writeKey(writer, "language", "string", "graph", "dg0");
            writeKey(writer, "name", "string", "graph", "dg1");
            writeKey(writer, "public", "boolean", "graph", "dg2");
            writeKey(writer, "stemmata", "string", "graph", "dg3");
            writeKey(writer, "stemweb_jobid", "string", "graph", "dg4");
            writeKey(writer, "user", "string", "graph", "dg5");
            writeKey(writer, "version", "string", "graph", "dg6");
            writeKey(writer, "direction", "string", "graph", "dg7");

            writeKey(writer, "grammar_invalid", "boolean", "node", "dn0");
            writeKey(writer, "id", "string", "node", "dn1");
            writeKey(writer, "is_common", "boolean", "node", "dn2");
            writeKey(writer, "is_end", "boolean", "node", "dn3");
            writeKey(writer, "is_lacuna", "boolean", "node", "dn4");
            writeKey(writer, "is_lemma", "boolean", "node", "dn5");
            writeKey(writer, "is_nonsense", "boolean", "node", "dn6");
            writeKey(writer, "is_ph", "boolean", "node", "dn7");
            writeKey(writer, "is_start", "boolean", "node", "dn8");
            writeKey(writer, "join_next", "boolean", "node", "dn9");
            writeKey(writer, "join_prior", "boolean", "node", "dn10");
            writeKey(writer, "language", "string", "node", "dn11");
            writeKey(writer, "witnesses", "string", "node", "dn12");
            writeKey(writer, "normal_form", "string", "node", "dn13");
            writeKey(writer, "rank", "int", "node", "dn14");
            writeKey(writer, "text", "string", "node", "dn15");

            writeKey(writer, "a_derivable_from_b", "boolean", "edge", "de0");
            writeKey(writer, "alters_meaning", "int", "edge", "de1");
            writeKey(writer, "annotation", "string", "edge", "de2");
            writeKey(writer, "b_derivable_from_a", "boolean", "edge", "de3");
            writeKey(writer, "displayform", "string", "edge", "de4");
            writeKey(writer, "extra", "string", "edge", "de5");
            writeKey(writer, "is_significant", "string", "edge", "de6");
            writeKey(writer, "non_independent", "boolean", "edge", "de7");
            writeKey(writer, "reading_a", "string", "edge", "de8");
            writeKey(writer, "reading_b", "string", "edge", "de9");
            writeKey(writer, "scope", "string", "edge", "de10");
            writeKey(writer, "type", "string", "edge", "de11");
            writeKey(writer, "witness", "string", "edge", "de12");

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
                if(prop !=null && !prop.equals("id") && !prop.equals("tradition_id")) {
                    writer.writeStartElement("data");
                    writer.writeAttribute("key", graphMap.get(prop));
                    writer.writeCharacters(traditionNode.getProperty(prop).toString());
                    writer.writeEndElement();
                }
            }
            // extract stemmata
            writer.writeStartElement("data");
            writer.writeAttribute("key", "dg3");

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
                writer.writeAttribute("key","dn1");
                writer.writeCharacters("n" + nodeId++);
                writer.writeEndElement();

                for(String prop : props) {
                    if(prop!=null && !prop.equals("tradition_id")) {
                        writer.writeStartElement("data");
                        writer.writeAttribute("key",nodeMap.get(prop));
                        writer.writeCharacters(node.getProperty(prop).toString());
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

                            if (!property.equals("witnesses")) {
                                writer.writeStartElement("data");
                                writer.writeAttribute("key", "de5");
                                writer.writeCharacters(property);
                                writer.writeEndElement();
                            }

                            writer.writeStartElement("data");
                            writer.writeAttribute("key", "de12");
                            writer.writeCharacters(witness);
                            writer.writeEndElement(); // end data key
                            writer.writeEndElement(); // end edge
                        }

                    }
                }
            }
            writer.writeEndElement(); // graph

            // graph 2
            // get the same nodes again, but this time we will later also search for other relationships

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
                writer.writeAttribute("key", "dn1");
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
                        if (rel.hasProperty(prop)) {
                            String value = rel.getProperty(prop).toString();
                            if (!value.equals("")) {
                                writer.writeStartElement("data");
                                String keyId = relationMap.get(prop);
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