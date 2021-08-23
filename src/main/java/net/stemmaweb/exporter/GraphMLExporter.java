package net.stemmaweb.exporter;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.VariantGraphService;
import org.apache.commons.compress.utils.IOUtils;
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
                String key = String.format("d%s%s", kind.charAt(0), values[0]);
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

    private void outputXMLToStream(XMLStreamWriter writer,
                                   String idLabel,
                                   List<Node> collectionNodes,
                                   List<Relationship> collectionEdges) throws XMLStreamException {
        try (Transaction tx = db.beginTx()) {
            // First we have to go through all nodes and edges in the tradition or section we want,
            // compiling a list of node and edge attributes.
            nodeMap = new HashMap<>();
            nodeMap.put("neolabel", new String[]{"0", "string"});
            edgeMap = new HashMap<>();
            edgeMap.put("neolabel", new String[]{"0", "string"});
            int nodeCount = 0;
            int edgeCount = 0;
            for (Node n : collectionNodes) {
                collectProperties(n, nodeMap);
                nodeCount++;
            }

            for (Relationship e : collectionEdges) {
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
            writer.writeAttribute("id", idLabel);
            writer.writeAttribute("edgedefault", "directed");
            writer.writeAttribute("parse.edgeids", "canonical");
            writer.writeAttribute("parse.edges", String.valueOf(edgeCount));
            writer.writeAttribute("parse.nodeids", "canonical");
            writer.writeAttribute("parse.nodes", String.valueOf(nodeCount));
            writer.writeAttribute("parse.order", "nodesfirst");

            // Now list out all the nodes, checking against duplicates in the traversal
            HashSet<Long> addedNodes = new HashSet<>();
            for (Node n : collectionNodes)
                if (!addedNodes.contains(n.getId())) {
                    writeNode(writer, n);
                    addedNodes.add(n.getId());
                }

            // And list out all the edges, which should already be unique in the traversal
            collectionEdges.forEach(x -> writeEdge(writer, x));

            writer.writeEndElement(); // graph
            writer.writeEndElement(); // end graphml
            writer.flush();

            tx.success();
        }
    }

    /**
     * Write a tradition, or a single section thereof, out to GraphML format. The result will be
     * a zip file of XML files, one for the tradition metadata and one for each section.
     *
     * @param tradId - The tradition to export
     * @param sectionId - The section to export; 'null' means export the whole tradition.
     *
     * @return a Response containing a zip file download of the requested tradition/section
     */
    public Response writeNeo4J(String tradId, String sectionId) {
        // Get the tradition node
        Node traditionNode = VariantGraphService.getTraditionNode(tradId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).build();

        // Set up our output directory and the initial XML file stream
        File tmpdirfh;
        String tmpdir;
        try {
            tmpdirfh = Files.createTempDirectory(tradId).toFile();
            tmpdir = tmpdirfh.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return Response.serverError().build();
        }
        ArrayList<String> outputFiles = new ArrayList<>();
        // Get the tradition meta-info
        ResourceIterable<Node> collectionNodes = VariantGraphService.returnTraditionMeta(traditionNode).nodes();
        ResourceIterable<Relationship> collectionEdges =
                VariantGraphService.returnTraditionMeta(traditionNode).relationships();

        // Get any annotations pertaining to the tradition node itself and its metadata
        List<Node> extraNodes;
        List<Relationship> extraRels = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            extraNodes = new ArrayList<>(VariantGraphService.collectAnnotationsOnSet(db, collectionNodes));
            // Add the relationships pointing from the annotations to the section and to each other
            extraNodes.forEach(x -> x.getRelationships(Direction.OUTGOING).forEach(extraRels::add));
            // Add the links back from the annotations to the tradition node
            extraNodes.forEach(x -> extraRels.add(
                    x.getSingleRelationship(ERelations.HAS_ANNOTATION, Direction.INCOMING)));
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }

        List<Node> allNodes = collectionNodes.stream().collect(Collectors.toList());
        allNodes.addAll(extraNodes);
        List<Relationship> allEdges = collectionEdges.stream().collect(Collectors.toList());
        allEdges.addAll(extraRels);

        // Set up the XML output stream to the first file
        XMLOutputFactory output = XMLOutputFactory.newInstance();
        try {
            String fileName = "tradition.xml";
            FileWriter traditionMeta = new FileWriter(tmpdir + "/" + fileName);
            XMLStreamWriter writer = new IndentingXMLStreamWriter(output.createXMLStreamWriter(traditionMeta));
            outputXMLToStream(writer, tradId, allNodes, allEdges);
            outputFiles.add(fileName);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }

        // Now do it all over again for each section we want to output.
        List<Node> allSections = new ArrayList<>();
        if (sectionId != null) {
            try (Transaction tx = db.beginTx()) {
                allSections.add(db.getNodeById(Long.parseLong(sectionId)));
                tx.success();
            } catch (Exception e) {
                e.printStackTrace();
                return Response.serverError().build();
            }
        } else {
            allSections = VariantGraphService.getSectionNodes(tradId, db);
        }
        assert allSections != null; // We won't have got this far if it is null
        for (Node s : allSections) {
            String sectId = String.valueOf(s.getId());
            // Gather the section-relevant nodes
            ResourceIterable<Node> sectionNodes = VariantGraphService.returnTraditionSection(s).nodes();
            ResourceIterable<Relationship> sectionEdges = VariantGraphService.returnTraditionSection(s).relationships();

            // Collect relevant annotations
            List<Node> extraSectNodes;
            List<Relationship> extraSectRels = new ArrayList<>();
            try (Transaction tx = db.beginTx()) {
                extraSectNodes = new ArrayList<>(VariantGraphService.collectAnnotationsOnSet(db, sectionNodes));
                // Add the relationships pointing from the annotations to the section and to each other
                extraSectNodes.forEach(x -> x.getRelationships(Direction.OUTGOING).forEach(extraSectRels::add));
                tx.success();
            } catch (Exception e) {
                e.printStackTrace();
                return Response.serverError().build();
            }

            // Assemble main and extra into single lists
            List<Node> allSectNodes = sectionNodes.stream().collect(Collectors.toList());
            allSectNodes.addAll(extraSectNodes);
            List<Relationship> allSectEdges = sectionEdges.stream().collect(Collectors.toList());
            allSectEdges.addAll(extraSectRels);

            // Setup the file stream
            XMLOutputFactory sectionOutput = XMLOutputFactory.newInstance();
            try {
                String fileName = String.format("section-%s.xml", sectId);
                FileWriter traditionSection = new FileWriter(tmpdir + "/" + fileName);
                XMLStreamWriter writer = new IndentingXMLStreamWriter(sectionOutput.createXMLStreamWriter(traditionSection));
                outputXMLToStream(writer, sectId, allSectNodes, allSectEdges);
                outputFiles.add(fileName);
            } catch (Exception e) {
                e.printStackTrace();
                return Response.serverError().build();
            }

        }

        // Finally, assemble the contents of tmpdir into a zip file.
        ByteArrayOutputStream result;
        try {
            result = new ByteArrayOutputStream();
            BufferedOutputStream bos = new BufferedOutputStream(result);
            ZipOutputStream zipOut = new ZipOutputStream(bos);
            for (String fn : outputFiles) {
                zipOut.putNextEntry(new ZipEntry(fn));
                FileInputStream fis = new FileInputStream(tmpdir + "/" + fn);

                IOUtils.copy(fis, zipOut);
                fis.close();
                zipOut.closeEntry();
            }
            zipOut.finish();
            zipOut.flush();
            IOUtils.closeQuietly(zipOut);
            IOUtils.closeQuietly(bos);
            IOUtils.closeQuietly(result);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }

        // Cleanup. These will get deleted in reverse order of registration.
        tmpdirfh.deleteOnExit();
        for (String fn : outputFiles) {
            File fh = new File(tmpdir + "/" + fn);
            fh.deleteOnExit();
        }

        String sectionAppend = sectionId != null ? "-section-" + sectionId : "";
        String cdisp = String.format("attachment; filename=\"%s%s.zip\"", tradId, sectionAppend);
        return Response.ok(result.toByteArray(), "application/zip").header("Content-Disposition", cdisp).build();
    }
}