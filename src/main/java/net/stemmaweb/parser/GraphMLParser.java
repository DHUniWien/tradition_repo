package net.stemmaweb.parser;

import net.stemmaweb.model.AnnotationLabelModel;
import net.stemmaweb.model.AnnotationLinkModel;
import net.stemmaweb.model.AnnotationModel;
import net.stemmaweb.rest.*;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.VariantGraphService;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.Node;
import org.w3c.dom.*;

import javax.ws.rs.core.Response;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static net.stemmaweb.parser.Util.jsonerror;
import static net.stemmaweb.parser.Util.jsonresp;

/**
 * Parser for the GraphML that Stemmarest itself produces.
 *
 * Created by tla on 17/02/2017.
 */
public class GraphMLParser {
    private final GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private final GraphDatabaseService db = dbServiceProvider.getDatabase();

    /**
     * Parses a zipped set of GraphML files representing either an entire tradition, or a single
     * tradition section. Returns the ID of the object (either tradition or section) that was created.
     *
     * @param filestream - an InputStream with the XML data
     * @param parentNode - a Node to represent the parent of this data, either a tradition or a section
     * @param isSingleSection - whether we are parsing a whole tradition or just a single section
     *
     * @return a Response object carrying a JSON dictionary {"parentId": <ID>}
     */

    public Response parseGraphMLZip(InputStream filestream, Node parentNode, boolean isSingleSection) {
        // Keep track of GraphML ID -> created node ID
        HashMap<String,Long> idMap = new HashMap<>();
        // Initialise our response
        String ret;
        // Unzip the file and send each XML file therein to the "real" parser
        try (Transaction tx = db.beginTx()) {
            // Set the parentId for our response
            ret = Util.jsonresp("parentId", parentNode.getId());
            // Get the XML files out of the zip stream
            ArrayList<File> inputXML = Util.parseGraphMLZip(filestream);
            for (File infile : inputXML) {
                FileInputStream fi = new FileInputStream(infile.getAbsolutePath());
                Response result = parseGraphML(fi, infile.getName(), parentNode, idMap, isSingleSection);
                // Did something go wrong? If so, exit now
                if (result.getStatus() != Response.Status.CREATED.getStatusCode())
                    return result;
                // Otherwise move on, recording the last section created
                ret = (String) result.getEntity();
            }
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }
        return Response.status(Response.Status.CREATED).entity(ret).build();
    }

    /**
     * Parses the contents of the XML file stream, returning ...something
     *
     * @param filestream - The unzipped XML file stream
     * @param fileName   - The name of the XML file we are working on
     * @param parentNode - The tradition node (if !isSingleSection), or the section node
     * @param idMap      - A map of node xml:id -> Neo4J node ID
     * @param isSingleSection - Whether we are parsing a new tradition, or adding a section to an existing one
     *
     * @return a Response indicating the result
     */
    private Response parseGraphML(InputStream filestream, String fileName, Node parentNode,
                                  Map<String,Long> idMap, boolean isSingleSection)
    {
        // We will use a DOM parser for this
        Document doc = Util.openFileStream(filestream);
        if (doc == null)
            return Response.serverError().entity(jsonerror("No document found")).build();

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

        String parentId = String.valueOf(parentNode.getId());

        // Get the tradition node
        Node traditionNode = isSingleSection ? VariantGraphService.getTraditionNode(parentNode) : parentNode;

        // Now get to work with node and relationship creation.
        try (Transaction tx = db.beginTx()) {
            // The UUID of the tradition node that was passed in to receive the parsed data
            String tradId = traditionNode.getProperty("id").toString();
            NodeList entityNodes = rootEl.getElementsByTagName("node");
            // If this is a single section upload, get the existing tradition metadata nodes
            // to make sure we don't duplicate them
            List<Node> existingMeta = isSingleSection ?
                    VariantGraphService.returnTraditionMeta(traditionNode).nodes().stream().collect(Collectors.toList()) :
                    new ArrayList<>();
            List<Relationship> existingMetaRel = isSingleSection ?
                    VariantGraphService.returnTraditionMeta(traditionNode).relationships().stream().collect(Collectors.toList()) :
                    new ArrayList<>();

            // We will hold back nodes that were labeled by the user rather than the system, such as annotations,
            // so that we can add them to the graph with the existing verification / sanity checks.
            HashMap<String,org.w3c.dom.Node> userLabeledNodes = new HashMap<>();
            ArrayList<org.w3c.dom.Node> userLabeledEdges = new ArrayList<>();

            // Now process the XML 'node' elements.
            for (int i = 0; i < entityNodes.getLength(); i++) {
                org.w3c.dom.Node entityXML = entityNodes.item(i);
                NamedNodeMap entityAttrs = entityXML.getAttributes();
                String xmlId = entityAttrs.getNamedItem("id").getNodeValue();
                NodeList dataNodes = ((Element) entityXML).getElementsByTagName("data");
                HashMap<String, Object> nodeProperties = returnProperties(dataNodes, dataKeys);
                if (!nodeProperties.containsKey("neolabel"))
                    return Response.status(Response.Status.BAD_REQUEST).entity(jsonerror("Node without label found")).build();
                String neolabel = nodeProperties.remove("neolabel").toString();
                String[] entityLabel = neolabel.replace("[", "").replace("]", "").split(",\\s+");

                if (neolabel.contains("TRADITION")) {
                    if (fileName.equals("tradition.xml") && !isSingleSection) {
                        // Otherwise, if there is already a different tradition with this tradition ID, we are making a
                        // duplicate and the effective UUID of this one was already set in Root.java. If there is not yet
                        // a different tradition with this UUID, we need to retain the UUID in the GraphML.

                        String fileTraditionId = nodeProperties.get("id").toString();
                        Node existingTradition = db.findNode(Nodes.TRADITION, "id", fileTraditionId);
                        if (existingTradition != null) // There is another tradition with this UUID; don't re-use it.
                            nodeProperties.remove("id");
                        else  // There is no other tradition with this UUID; update the tradId to match the XML.
                            tradId = fileTraditionId;

                        // This node is already created, but we need to reset its properties according to
                        // what is in the GraphML file. We also save this ID as the parent ID that was created.
                        for (String p : nodeProperties.keySet())
                            traditionNode.setProperty(p, nodeProperties.get(p));
                        parentId = tradId;
                    } // else
                        // If we are parsing the tradition meta-info for a single section, or if we are parsing a
                        // section XML file, we ignore the data for this node and we will keep the parent ID as the
                        // ID of the section node.
                    // Now set the tradition node to be our real tradition node, whether from this file or
                    // from the existing tradition for this section
                    idMap.put(xmlId, traditionNode.getId());

                } else {
                    // Now we have the information of the XML, we can create the new node if we need to.
                    // We don't need to...
                    // - if it is already in the idMap
                    // - if it is the SECTION node
                    // - if this is a single section upload, we are in the tradition.xml file, and a node
                    //   with identical properties already exists

                    if (!idMap.containsKey(xmlId)) {
                        // Is this the single-section section node?
                        Node entity = null;
                        boolean exists = false;
                        if (neolabel.contains("SECTION") && isSingleSection)
                            entity = parentNode;
                        else if (fileName.equals("tradition.xml")) {
                            // Does the node already exist in the tradition's metadata?
                            exists = true;
                            for (Node ex : existingMeta) {
                                for (String p : nodeProperties.keySet()) {
                                    exists = exists && nodeProperties.get(p).equals(ex.getProperty(p, null));
                                }
                                if (exists) {
                                    // The node already exists, so we add it to the idMap and move on.
                                    entity = ex;
                                    break;
                                }
                            }
                        }
                        // If we haven't found a Neo4J node to match this XML node, we need to create one.
                        if (entity == null) entity = db.createNode();
                        // Record the XML -> n4j correlation
                        idMap.put(xmlId, entity.getId());
                        // and, if the node didn't already exist, update its labels and properties.
                        if (!exists)
                            for (String l : entityLabel) {
                                try {
                                    entity.addLabel(Nodes.valueOf(l));
                                    nodeProperties.forEach(entity::setProperty);
                                } catch (IllegalArgumentException e) {
                                    // This is an annotation node, which we will deal with in a separate pass.
                                    // Remove the node and its entry in the idMap.
                                    userLabeledNodes.put(xmlId, entityXML);
                                    idMap.remove(xmlId);
                                    entity.delete();
                                }
                            }
                        }
                    }
                }

            // Next go through all the edges and create them between the nodes. Keep track of the
            // relation types we have seen.
            NodeList edgeNodes = rootEl.getElementsByTagName("edge");
            for (int i = 0; i < edgeNodes.getLength(); i++) {
                NamedNodeMap edgeAttrs = edgeNodes.item(i).getAttributes();
                String sourceXmlId = edgeAttrs.getNamedItem("source").getNodeValue();
                String targetXmlId = edgeAttrs.getNamedItem("target").getNodeValue();
                // Is this an annotation edge? If so, add it to userLabeledEdges for later processing
                Node source;
                Node target;
                try {
                    source = db.getNodeById(idMap.get(sourceXmlId));
                    target = db.getNodeById(idMap.get(targetXmlId));
                } catch (NullPointerException e) {
                    // If the source or the target is in userLabeledNodes, we know this is a userLabeledEdge, i.e.
                    // an annotation edge. Skip it for later addition through the annotation framework
                    if (userLabeledNodes.containsKey(sourceXmlId) || userLabeledNodes.containsKey(targetXmlId)) {
                        userLabeledEdges.add(edgeNodes.item(i));
                        continue;
                    }
                    else
                        throw e;
                }

                NodeList dataNodes = ((Element) edgeNodes.item(i)).getElementsByTagName("data");
                HashMap<String, Object> edgeProperties = returnProperties(dataNodes, dataKeys);
                if (!edgeProperties.containsKey("neolabel"))
                    return Response.serverError().entity(jsonerror("Node without label found")).build();
                String neolabel = edgeProperties.remove("neolabel").toString();

                // If we are parsing the tradition meta for a new section, check whether the relationship
                // in question already exists
                Relationship newRel = null;
                if (fileName.equals("tradition.xml") && isSingleSection) {
                    // Does the node already exist in the tradition's metadata?
                    boolean exists = true;
                    for (Relationship ex : existingMetaRel.stream()
                            .filter(x -> x.getStartNode().equals(source) && x.getEndNode().equals(target))
                            .collect(Collectors.toList())) {
                        for (String p : edgeProperties.keySet()) {
                            exists = exists && edgeProperties.get(p).equals(ex.getProperty(p, null));
                        }
                        if (exists) {
                            // The node already exists, so we add it to the idMap and move on.
                            newRel = ex;
                            break;
                        }
                    }
                }
                if (newRel == null) { // If it exists already we don't touch it.
                    try {
                        newRel = source.createRelationshipTo(target, ERelations.valueOf(neolabel));
                        edgeProperties.forEach(newRel::setProperty);
                    } catch (IllegalArgumentException e) {
                        // We are either here because we tried to link an annotation (which doesn't yet exist)
                        // to the tradition via a HAS_ANNOTATION link, or because we tried to use a user-defined
                        // relationship label in the context of an annotation where both nodes already existed.
                        // If the former, ignore it (we will add these links later); if the latter, warn because
                        // I really don't expect this.
                        if (!neolabel.equals("HAS_ANNOTATION")) {
                            System.out.println("Found annotation edge between two non-annotation nodes?!");
                            userLabeledEdges.add(edgeNodes.item(i));
                        }
                    }
                }
            }

            // Reset the section IDs stored on each reading to the ID of the newly created node
            for (Node r : idMap.values().stream().map(db::getNodeById)
                    .filter(x -> x.hasLabel(Nodes.READING)).collect(Collectors.toList())) {
                String rSectId = r.getProperty("section_id").toString();
                r.setProperty("section_id", idMap.get(rSectId));
            }

            // Now add user-labeled nodes separately, via the existing validation infrastructure.
            List<AnnotationModel> annotationsToAdd = new ArrayList<>();
            for (org.w3c.dom.Node xn : userLabeledNodes.values()) {
                AnnotationModel am = new AnnotationModel();
                // Get the properties on this annotation node.
                HashMap<String,Object> props = returnProperties(((Element) xn).getElementsByTagName("data"), dataKeys);
                // We already know from the first pass that this label exists
                String annLabel = props.remove("neolabel").toString();
                annLabel = annLabel.substring(1, annLabel.length() - 1);
                // Is it marked as a primary annotation?
                boolean isPrimary = props.containsKey("__primary")
                        && props.remove("__primary").toString().equals("true");
                // Fill out the annotation model
                am.setLabel(annLabel);
                am.setPrimary(isPrimary);
                am.setProperties(props);
                // Add the links, from our collected edges
                for (org.w3c.dom.Node xe : userLabeledEdges) {
                    NamedNodeMap edgeAttrs = xe.getAttributes();
                    String sourceXmlId = edgeAttrs.getNamedItem("source").getNodeValue();
                    String targetXmlId = edgeAttrs.getNamedItem("target").getNodeValue();
                    HashMap<String,Object> edgeProps = returnProperties(((Element) xe).getElementsByTagName("data"), dataKeys);
                    if (sourceXmlId.equals(((Element) xn).getAttribute("id"))) {
                        // It is a link that belongs to this source. For now set the XML element ID as the
                        // target; this will need to be converted progressively into real node IDs.
                        AnnotationLinkModel alm = new AnnotationLinkModel();
                        alm.setTarget(Long.valueOf(targetXmlId));
                        alm.setType(edgeProps.get("neolabel").toString());
                        if (edgeProps.containsKey("follow")) alm.setFollow(edgeProps.get("follow").toString());
                        am.addLink(alm);
                    }
                }
                annotationsToAdd.add(am);
            }
            while (annotationsToAdd.size() > 0) {
                Tradition tradService = new Tradition(tradId);
                List<AnnotationModel> toRemove = new ArrayList<>();
                for (AnnotationModel am : annotationsToAdd) {
                    // Look at the links and see if the targets exist yet
                    boolean targetsExist = true;
                    for (Long target : am.getLinks().stream().map(AnnotationLinkModel::getTarget).collect(Collectors.toList())) {
                        targetsExist = targetsExist && idMap.containsKey(target.toString());
                    }
                    if (targetsExist) {
                        // We can update the links with the "real" nodes and create the annotation.
                        for (AnnotationLinkModel alm : am.getLinks()) {
                            Node nodeTarget = db.getNodeById(idMap.get(alm.getTarget().toString()));
                            alm.setTarget(nodeTarget.getId());
                        }
                        Response result = tradService.addAnnotation(am);
                        if (result.getStatus() != Response.Status.CREATED.getStatusCode()) {
                            throw new UnsupportedOperationException(String.format(
                                    "Error on adding user annotation %s/%s: %s",
                                    am.getId(), am.getLabel(), result.getEntity()));
                        }
                        toRemove.add(am);
                    }
                }
                // Guard against infinite loops
                if (toRemove.isEmpty())
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(jsonerror("Annotations in XML could not all be resolved")).build();
                annotationsToAdd.removeAll(toRemove);
            }

            // Sanity check: if we created any relationship-less nodes, delete them again.
            idMap.values().stream().map(db::getNodeById)
                    .filter(n -> !n.hasRelationship()).forEach(Node::delete);

            tx.success();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(jsonerror(e.getMessage())).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }

        return Response.status(Response.Status.CREATED).entity(jsonresp("parentId", parentId)).build();
    }

    // Return true if the tradition already has an annotation under the given name.
    // Throws an IllegalArgumentException if the annotation information conflicts.
    // TODO do we still need this?
    private boolean annoLabelExists(Node tradition, Node alabel) {
        String name = alabel.getProperty("name").toString();
        Optional<Node> matching = DatabaseService.getRelated(tradition, ERelations.HAS_WITNESS).stream()
                .filter(x -> x.getProperty("name", "").equals(name)).findFirst();
        if (matching.isEmpty()) return false;

        // Check for a mismatch of properties and links. The existing model may contain a superset of
        // allowed properties and links.
        AnnotationLabelModel existing = new AnnotationLabelModel(matching.get());
        AnnotationLabelModel added = new AnnotationLabelModel(alabel);
        for (String k : added.getProperties().keySet()) {
            if (!added.getProperties().get(k).equals(existing.getProperties().getOrDefault(k,null)))
                throw new UnsupportedOperationException(String.format(
                        "Mismatch between existing and input properties for annotation label %s", name));
        }
        for (String k : added.getLinks().keySet()) {
            if (!existing.getLinks().containsKey(k))
                throw new UnsupportedOperationException(String.format(
                        "Existing annotation label %s is missing link for node type %s", name, k));
            List<String> linkTypes = Arrays.asList(added.getLinks().get(k).split(","));
            List<String> existingLTypes = Arrays.asList(existing.getLinks().get(k).split(","));
            if (!existingLTypes.containsAll(linkTypes))
                throw new UnsupportedOperationException(String.format(
                        "Existing annotation label %s has different links to node type %s", name, k));
        }

        // If we didn't throw any errors then we can continue.
        return true;
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
