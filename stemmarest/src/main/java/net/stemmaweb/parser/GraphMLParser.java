package net.stemmaweb.parser;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.Node;
import org.w3c.dom.*;

import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.*;

/**
 * Parser for the GraphML that Stemmarest itself produces.
 *
 * Created by tla on 17/02/2017.
 */
public class GraphMLParser {

    private GraphDatabaseService db;

    public GraphMLParser(GraphDatabaseService db) {
        this.db = db;
    }

    public Response parseGraphML(InputStream filestream)
    {
        // We will use a DOM parser for this
        Document doc = Util.openFileStream(filestream);
        if (doc == null)
            return Response.serverError().entity("No document found").build();

        Element rootEl = doc.getDocumentElement();
        rootEl.normalize();

        // Get the data keys and their types; the map entries are e.g.
        // "dn0" -> ["neolabel", "string"]
        HashMap<String, String[]> dataKeys = new HashMap<>();
        NodeList keyNodes = rootEl.getElementsByTagName("key");
        for (int i = 0; i < keyNodes.getLength(); i++) {
            NamedNodeMap keyAttrs = keyNodes.item(i).getAttributes();
            String[] dataInfo = new String[]{keyAttrs.getNamedItem("attr.name").getNodeValue(),
                    keyAttrs.getNamedItem("attr.type").getNodeValue()};
            dataKeys.put(keyAttrs.getNamedItem("id").getNodeValue(), dataInfo);
        }

        String tradId = null;
        // Keep track of XML ID to Neo4J ID mapping for all nodes
        HashMap<String, Node> entityMap = new HashMap<>();

        // Now get to work with node and relationship creation.
        try (Transaction tx = db.beginTx()) {
            NodeList entityNodes = rootEl.getElementsByTagName("node");
            for (int i = 0; i < entityNodes.getLength(); i++) {
                org.w3c.dom.Node entityXML = entityNodes.item(i);
                NamedNodeMap entityAttrs = entityXML.getAttributes();
                String xmlId = entityAttrs.getNamedItem("id").getNodeValue();
                NodeList dataNodes = ((Element) entityXML).getElementsByTagName("data");
                HashMap<String, Object> nodeProperties = returnProperties(dataNodes, dataKeys);
                if (!nodeProperties.containsKey("neolabel"))
                    return Response.serverError().entity("Node without label found").build();
                String neolabel = nodeProperties.remove("neolabel").toString();
                String[] entityLabel = neolabel.replace("[", "").replace("]", "").split(",\\s+");

                // Check for a duplicate tradition ID
                if (neolabel.contains("TRADITION")) {
                    tradId = nodeProperties.get("id").toString();
                    Node existingTradition = db.findNode(Nodes.TRADITION, "id", tradId);
                    if (existingTradition != null) {
                        // Delete any nodes we already created
                        entityMap.values().forEach(Node::delete);
                        // ...and get out of here.
                        return Response.status(Response.Status.CONFLICT).entity("A tradition with ID " + tradId
                                + " already exists").build();
                    }
                }

                // Now we have the information of the XML, we can create the node.
                Node entity = db.createNode();
                for (String l : entityLabel)
                    entity.addLabel(Nodes.valueOf(l));
                nodeProperties.forEach(entity::setProperty);
                entityMap.put(xmlId, entity);
                // Was it the tradition node?
            }

            // Next go through all the edges and create them between the nodes.
            NodeList edgeNodes = rootEl.getElementsByTagName("edge");
            for (int i = 0; i < edgeNodes.getLength(); i++) {
                NamedNodeMap edgeAttrs = edgeNodes.item(i).getAttributes();
                String sourceXmlId = edgeAttrs.getNamedItem("source").getNodeValue();
                String targetXmlId = edgeAttrs.getNamedItem("target").getNodeValue();
                Node source = entityMap.get(sourceXmlId);
                Node target = entityMap.get(targetXmlId);
                NodeList dataNodes = ((Element) edgeNodes.item(i)).getElementsByTagName("data");
                HashMap<String, Object> edgeProperties = returnProperties(dataNodes, dataKeys);
                if (!edgeProperties.containsKey("neolabel"))
                    return Response.serverError().entity("Node without label found").build();
                String neolabel = edgeProperties.remove("neolabel").toString();
                Relationship newRel = source.createRelationshipTo(target, ERelations.valueOf(neolabel));
                edgeProperties.forEach(newRel::setProperty);
            }
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }

        String response = String.format("{\"tradId\":\"%s\"}", tradId);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }


    private HashMap<String, Object> returnProperties (NodeList dataNodes, HashMap<String, String[]> dataKeys) {
        HashMap<String, Object> nodeProperties = new HashMap<>();
        for (int j = 0; j < dataNodes.getLength(); j++) {
            org.w3c.dom.Node datumXML = dataNodes.item(j);
            String keyCode = datumXML.getAttributes().getNamedItem("key").getNodeValue();
            String keyVal = datumXML.getTextContent();
            String[] keyInfo = dataKeys.get(keyCode);
            Object propValue;
            // These datatypes need to be kept in sync with exporter.GraphMLExporter
            switch (keyInfo[1]) {
                case "boolean":
                    propValue = Boolean.valueOf(keyVal);
                    break;
                case "long":
                    propValue = Long.valueOf(keyVal);
                    break;
                case "stringarray":
                    propValue = keyVal.replace("[", "").replace("]", "").split(",\\s+");
                    break;
                default: // e.g. "string"
                    propValue = keyVal;

            }
            nodeProperties.put(keyInfo[0], propValue);
        }
        return nodeProperties;
    }

}
