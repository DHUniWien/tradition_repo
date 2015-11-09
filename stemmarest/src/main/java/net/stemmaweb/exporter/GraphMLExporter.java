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
public class GraphMLExporter {
    private GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private GraphDatabaseService db = dbServiceProvider.getDatabase();

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

            HashMap<String, String> nodeMap = new HashMap<>();
            HashMap<String, String> relationMap = new HashMap<>();
            HashMap<String, String> graphMap = new HashMap<>();

            nodeMap.put("grammar_invalid", "dn0");
            nodeMap.put("id", "dn1");
            nodeMap.put("is_common", "dn2");
            nodeMap.put("is_end", "dn3");
            nodeMap.put("is_lacuna", "dn4");
            nodeMap.put("is_lemma", "dn5");
            nodeMap.put("is_nonsense", "dn6");
            nodeMap.put("is_ph", "dn7");
            nodeMap.put("is_start", "dn8");
            nodeMap.put("join_next", "dn9");
            nodeMap.put("join_prior", "dn10");
            nodeMap.put("language", "dn11");
            nodeMap.put("witnesses", "dn12");
            nodeMap.put("normal_form", "dn13");
            nodeMap.put("rank", "dn14");
            nodeMap.put("text", "dn15");

            relationMap.put("a_derivable_from_b", "de0");
            relationMap.put("alters_meaning", "de1");
            relationMap.put("annotation", "de2");
            relationMap.put("b_derivable_from_a", "de3");
            relationMap.put("displayform", "de4");
            relationMap.put("extra", "de5");
            relationMap.put("is_significant", "de6");
            relationMap.put("non_independent", "de7");
            relationMap.put("reading_a", "de8");
            relationMap.put("reading_b", "de9");
            relationMap.put("scope", "de10");
            relationMap.put("type", "de11");
            relationMap.put("witness", "de12");

            graphMap.put("language", "dg0");
            graphMap.put("name", "dg1");
            graphMap.put("public", "dg2");
            graphMap.put("stemmata", "dg3");
            graphMap.put("stemweb_jobid", "dg4");
            graphMap.put("user", "dg5");
            graphMap.put("version", "dg6");
            graphMap.put("direction", "dg7");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "language");
            writer.writeAttribute("attr.type", "string");
            writer.writeAttribute("for", "graph");
            writer.writeAttribute("id", "dg0");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "name");
            writer.writeAttribute("attr.type", "string");
            writer.writeAttribute("for", "graph");
            writer.writeAttribute("id", "dg1");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "public");
            writer.writeAttribute("attr.type", "boolean");
            writer.writeAttribute("for", "graph");
            writer.writeAttribute("id", "dg2");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "stemmata");
            writer.writeAttribute("attr.type", "string");
            writer.writeAttribute("for", "graph");
            writer.writeAttribute("id", "dg3");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "stemweb_jobid");
            writer.writeAttribute("attr.type", "string");
            writer.writeAttribute("for", "graph");
            writer.writeAttribute("id", "dg4");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "user");
            writer.writeAttribute("attr.type", "string");
            writer.writeAttribute("for", "graph");
            writer.writeAttribute("id", "dg5");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "version");
            writer.writeAttribute("attr.type", "string");
            writer.writeAttribute("for", "graph");
            writer.writeAttribute("id", "dg6");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "direction");
            writer.writeAttribute("attr.type", "string");
            writer.writeAttribute("for", "graph");
            writer.writeAttribute("id", "dg7");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "grammar_invalid");
            writer.writeAttribute("attr.type", "boolean");
            writer.writeAttribute("for", "node");
            writer.writeAttribute("id", "dn0");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "id");
            writer.writeAttribute("attr.type", "string");
            writer.writeAttribute("for", "node");
            writer.writeAttribute("id", "dn1");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "is_common");
            writer.writeAttribute("attr.type", "boolean");
            writer.writeAttribute("for", "node");
            writer.writeAttribute("id", "dn2");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "is_end");
            writer.writeAttribute("attr.type", "boolean");
            writer.writeAttribute("for", "node");
            writer.writeAttribute("id", "dn3");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "is_lacuna");
            writer.writeAttribute("attr.type", "boolean");
            writer.writeAttribute("for", "node");
            writer.writeAttribute("id", "dn4");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "is_lemma");
            writer.writeAttribute("attr.type", "boolean");
            writer.writeAttribute("for", "node");
            writer.writeAttribute("id", "dn5");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "is_nonsense");
            writer.writeAttribute("attr.type", "boolean");
            writer.writeAttribute("for", "node");
            writer.writeAttribute("id", "dn6");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "is_ph");
            writer.writeAttribute("attr.type", "boolean");
            writer.writeAttribute("for", "node");
            writer.writeAttribute("id", "dn7");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "is_start");
            writer.writeAttribute("attr.type", "boolean");
            writer.writeAttribute("for", "node");
            writer.writeAttribute("id", "dn8");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "join_next");
            writer.writeAttribute("attr.type", "boolean");
            writer.writeAttribute("for", "node");
            writer.writeAttribute("id", "dn9");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "join_prior");
            writer.writeAttribute("attr.type", "boolean");
            writer.writeAttribute("for", "node");
            writer.writeAttribute("id", "dn10");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "language");
            writer.writeAttribute("attr.type", "string");
            writer.writeAttribute("for", "node");
            writer.writeAttribute("id", "dn11");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "witnesses");
            writer.writeAttribute("attr.type", "string");
            writer.writeAttribute("for", "node");
            writer.writeAttribute("id", "dn12");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "normal_form");
            writer.writeAttribute("attr.type", "string");
            writer.writeAttribute("for", "node");
            writer.writeAttribute("id", "dn13");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "rank");
            writer.writeAttribute("attr.type", "int");
            writer.writeAttribute("for", "node");
            writer.writeAttribute("id", "dn14");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "text");
            writer.writeAttribute("attr.type", "string");
            writer.writeAttribute("for", "node");
            writer.writeAttribute("id", "dn15");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "a_derivable_from_b");
            writer.writeAttribute("attr.type", "boolean");
            writer.writeAttribute("for", "edge");
            writer.writeAttribute("id", "de0");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "alters_meaning");
            writer.writeAttribute("attr.type", "int");
            writer.writeAttribute("for", "edge");
            writer.writeAttribute("id", "de1");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "annotation");
            writer.writeAttribute("attr.type", "string");
            writer.writeAttribute("for", "edge");
            writer.writeAttribute("id", "de2");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "b_derivable_from_a");
            writer.writeAttribute("attr.type", "boolean");
            writer.writeAttribute("for", "edge");
            writer.writeAttribute("id", "de3");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "displayform");
            writer.writeAttribute("attr.type", "string");
            writer.writeAttribute("for", "edge");
            writer.writeAttribute("id", "de4");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "extra");
            writer.writeAttribute("attr.type", "string");
            writer.writeAttribute("for", "edge");
            writer.writeAttribute("id", "de5");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "is_significant");
            writer.writeAttribute("attr.type", "string");
            writer.writeAttribute("for", "edge");
            writer.writeAttribute("id", "de6");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "non_independent");
            writer.writeAttribute("attr.type", "boolean");
            writer.writeAttribute("for", "edge");
            writer.writeAttribute("id", "de7");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "reading_a");
            writer.writeAttribute("attr.type", "string");
            writer.writeAttribute("for", "edge");
            writer.writeAttribute("id", "de8");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "reading_b");
            writer.writeAttribute("attr.type", "string");
            writer.writeAttribute("for", "edge");
            writer.writeAttribute("id", "de9");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "scope");
            writer.writeAttribute("attr.type", "string");
            writer.writeAttribute("for", "edge");
            writer.writeAttribute("id", "de10");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "type");
            writer.writeAttribute("attr.type", "string");
            writer.writeAttribute("for", "edge");
            writer.writeAttribute("id", "de11");

            writer.writeEmptyElement("key");
            writer.writeAttribute("attr.name", "witness");
            writer.writeAttribute("attr.type", "string");
            writer.writeAttribute("for", "edge");
            writer.writeAttribute("id", "de12");

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