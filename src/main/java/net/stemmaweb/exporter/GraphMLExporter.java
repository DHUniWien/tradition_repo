package net.stemmaweb.exporter;

import java.io.StringWriter;
import java.util.*;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Section;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.VariantGraphService;
import org.neo4j.graphdb.*;

import com.sun.xml.txw2.output.IndentingXMLStreamWriter;

/**
 * This class provides methods for exporting GraphMl (XML) File from Neo4J
 *
 * @author PSE FS 2015 Team2
 */
public class GraphMLExporter {
    private final GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private final GraphDatabaseService db = dbServiceProvider.getDatabase();

    private HashMap<String,String[]> nodeMap;
    private HashMap<String,String[]> edgeMap;

    private void writeKeys(XMLStreamWriter writer, HashMap<String, String[]> currentMap, String kind)
            throws XMLStreamException{
            for (Map.Entry<String, String[]> entry : currentMap.entrySet()) {
                String[] values = entry.getValue();
                String key = String.format("d%s%s", kind.substring(0, 1), values[0]);
                writer.writeEmptyElement("key");
                writer.writeAttribute("attr.name", entry.getKey());
                writer.writeAttribute("attr.type", values[1]);
                writer.writeAttribute("for", kind);
                writer.writeAttribute("id", key);
            }
    }

    private void writeNode(XMLStreamWriter writer, Node node) {
        try {
            writer.writeStartElement("node");
            writer.writeAttribute("id", String.valueOf(node.getId()));

            // Write out the labels
            writer.writeStartElement("data");
            writer.writeAttribute("key", "dn" + nodeMap.get("neolabel")[0]);
            writer.writeCharacters(node.getLabels().toString());
            writer.writeEndElement();

            // Write out the properties
            writeProperties(writer, node, nodeMap);
            // End the node
            writer.writeEndElement();
        } catch (XMLStreamException e) {
            e.printStackTrace();
        }
    }

    private void writeEdge(XMLStreamWriter writer, Relationship edge) {
        try {
            writer.writeStartElement("edge");
            writer.writeAttribute("id", String.valueOf(edge.getId()));
            writer.writeAttribute("source", String.valueOf(edge.getStartNode().getId()));
            writer.writeAttribute("target", String.valueOf(edge.getEndNode().getId()));

            // Write out the type
            writer.writeStartElement("data");
            writer.writeAttribute("key", "de" + edgeMap.get("neolabel")[0]);
            writer.writeCharacters(edge.getType().name());
            writer.writeEndElement();

            // Write out the properties
            writeProperties(writer, edge, edgeMap);
            // End the node
            writer.writeEndElement();
        } catch (XMLStreamException e) {
            e.printStackTrace();
        }
    }

    // TODO check for cases where the same property name has different types in different containers
    private void writeProperties(XMLStreamWriter writer, PropertyContainer ent, HashMap<String, String[]> collection)
            throws XMLStreamException {
        String prefix = collection.equals(nodeMap) ? "dn" : "de";
        for (String prop : ent.getPropertyKeys()) {
            if (collection.containsKey(prop)) {
                writer.writeStartElement("data");
                writer.writeAttribute("key", prefix + collection.get(prop)[0]);
                String propValue;
                if (collection.get(prop)[1].equals("stringarray"))
                    propValue = new ArrayList<>(Arrays.asList((String[]) ent.getProperty(prop))).toString();
                else
                    propValue = ent.getProperty(prop).toString();
                writer.writeCharacters(propValue);
                writer.writeEndElement();
            }
        }
    }


    // To be used inside a transaction
    // These datatypes need to be kept in sync with parser.GraphMLParser
    private void collectProperties (PropertyContainer ent, HashMap<String, String[]> collection) {
        int ctr = collection.size();
        for (String p : ent.getPropertyKeys()) {
            String type = "string";
            Object prop = ent.getProperty(p);
            if (prop instanceof Long) type = "long";
            else if (prop instanceof Boolean) type = "boolean";
            else if (prop instanceof String[]) type = "stringarray";
            if (!collection.containsKey(p) || !collection.get(p)[1].equals(type)) {
                collection.put(p, new String[]{String.valueOf(ctr++), type});
            }
        }
    }

    public Response writeNeo4J(String tradId) {
        return writeNeo4J(tradId, null, false);
    }


    public Response writeNeo4J(String tradId, String sectionId, Boolean includeWitnesses) {
        // Get the tradition node
        Node traditionNode = VariantGraphService.getTraditionNode(tradId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).build();

        StringWriter result = new StringWriter();
        XMLOutputFactory output = XMLOutputFactory.newInstance();
        XMLStreamWriter writer;
        try {
            writer = new IndentingXMLStreamWriter(output.createXMLStreamWriter(result));
        } catch (XMLStreamException e) {
            e.printStackTrace();
            return Response.serverError().build();
        }
        ResourceIterable<Node> traditionNodes = sectionId == null ?
                VariantGraphService.returnEntireTradition(traditionNode).nodes() :
                VariantGraphService.returnTraditionSection(sectionId, db).nodes();
        ResourceIterable<Relationship> traditionEdges = sectionId == null ?
                VariantGraphService.returnEntireTradition(traditionNode).relationships() :
                VariantGraphService.returnTraditionSection(sectionId, db).relationships();

        // Collect any extra nodes that should go into the list for whatever reason.
        // So far this only applies if we are requesting a single section.

        List<Node> extraNodes = new ArrayList<>();
        List<Relationship> extraRels = new ArrayList<>();
        if (sectionId != null) {
            // We also want to include the annotation nodes that appear in this section,
            // and relevant witness nodes if requested.
            Section sectionService = new Section(tradId, sectionId);
            extraNodes.addAll(sectionService.collectSectionAnnotations());
            if (!extraNodes.isEmpty()) {
                try (Transaction tx = db.beginTx()) {
                    // Add the relationships pointing from the annotations to the section and to each other
                    extraNodes.forEach(x -> x.getRelationships(Direction.OUTGOING).forEach(extraRels::add));
                    // Add in the annotation spec for this tradition, so that the annotations we just collected
                    // can be validated when this file is parsed again.
                    traditionNode.getRelationships(ERelations.HAS_ANNOTATION_TYPE, Direction.OUTGOING)
                            .forEach(x -> {
                                extraNodes.add(x.getEndNode());
                                x.getEndNode().getRelationships(Direction.OUTGOING).forEach(y -> {
                                    extraRels.add(y);
                                    extraNodes.add(y.getEndNode());
                                });
                            });
                    tx.success();
                } catch (Exception e) {
                    e.printStackTrace();
                    return Response
                            .status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity("Error in user-defined node generation")
                            .build();
                }
            }
            if (includeWitnesses) extraNodes.addAll(sectionService.collectSectionWitnesses());
        }


        try (Transaction tx = db.beginTx()) {
            // First we have to go through all nodes and edges in the tradition or section we want,
            // compiling a list of node and edge attributes.
            nodeMap = new HashMap<>();
            nodeMap.put("neolabel", new String[]{"0", "string"});
            edgeMap = new HashMap<>();
            edgeMap.put("neolabel", new String[]{"0", "string"});
            int nodeCount = 0;
            int edgeCount = 0;
            for (Node n : traditionNodes) {
                collectProperties(n, nodeMap);
                nodeCount++;
            }

            for (Relationship e : traditionEdges) {
                collectProperties(e, edgeMap);
                edgeCount++;
            }

            for (Node n : extraNodes) {
                collectProperties(n, nodeMap);
                nodeCount++;
            }

            for (Relationship e : extraRels) {
                collectProperties(e, edgeMap);
                edgeCount++;
            }

            writer.writeStartDocument();

            writer.writeStartElement("graphml");
            writer.writeAttribute("xmlns", "http://graphml.graphdrawing.org/xmlns");
            writer.writeAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
            writer.writeAttribute("xsi:schemaLocation", "http://graphml.graphdrawing.org/xmlns " +
                    "http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd");

            // ####### KEYS START #######################################

            writeKeys(writer, nodeMap, "node");
            writeKeys(writer, edgeMap, "edge");

            // ####### KEYS END #######################################

            // Write out the <graph> opening tag
            writer.writeStartElement("graph");
            writer.writeAttribute("id", traditionNode.getProperty("name").toString());
            writer.writeAttribute("edgedefault", "directed");
            writer.writeAttribute("parse.edgeids", "canonical");
            writer.writeAttribute("parse.edges", String.valueOf(edgeCount));
            writer.writeAttribute("parse.nodeids", "canonical");
            writer.writeAttribute("parse.nodes", String.valueOf(nodeCount));
            writer.writeAttribute("parse.order", "nodesfirst");

            // Now list out all the nodes, checking against duplicates in the traversal
            HashSet<Long> addedNodes = new HashSet<>();
            for (Node n : traditionNodes)
                if (!addedNodes.contains(n.getId())) {
                    writeNode(writer, n);
                    addedNodes.add(n.getId());
                }

            for (Node n : extraNodes)
                if (!addedNodes.contains(n.getId())) {
                    writeNode(writer, n);
                    addedNodes.add(n.getId());
                }

            // And list out all the edges, which should already be unique in the traversal
            traditionEdges.forEach(x -> writeEdge(writer, x));
            extraRels.forEach(x -> writeEdge(writer, x));


            writer.writeEndElement(); // graph
            writer.writeEndElement(); // end graphml
            writer.flush();

            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error: Tradition could not be exported!")
                    .build();
        }
        return Response.ok(result.toString(), MediaType.APPLICATION_XML).build();
    }
}