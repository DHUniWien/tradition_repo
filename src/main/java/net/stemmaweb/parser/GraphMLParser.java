package net.stemmaweb.parser;

import static net.stemmaweb.Util.jsonerror;
import static net.stemmaweb.Util.jsonresp;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import net.stemmaweb.services.AnnotationService;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import jakarta.ws.rs.core.Response;
import net.stemmaweb.model.AnnotationLinkModel;
import net.stemmaweb.model.AnnotationModel;
import net.stemmaweb.model.SequenceModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.RelationService;
import net.stemmaweb.services.VariantGraphService;

/**
 * Parser for the GraphML that Stemmarest itself produces.
 * <p>
 * Created by tla on 17/02/2017.
 */
public class GraphMLParser {
    private final Transaction tx;

    public GraphMLParser(Transaction tx) {
        this.tx = tx;
    }

    /**
     * Parses a single GraphML file (the old style) representing either an entire tradition or a single
     * tradition section. Returns the ID of the object (either tradition or section) that was created.
     *
     * @param filestream      - an InputStream with the XML data
     * @param parentNode      - a Node to represent the parent of this data, either a tradition or a section
     * @param isSingleSection - whether we are parsing a whole tradition or just a single section into
     *                        an existing tradition
     * @return a Response object carrying a JSON dictionary {@code {"parentId": <ID>}}
     */
    public Response parseGraphMLSingle(InputStream filestream, Node parentNode, boolean isSingleSection) {
        // Simulate the expected filenames in the zip file
        String filename = isSingleSection ? "section-new.xml" : "tradition.xml";
        // We won't use this, but the new parser expects it
        HashMap<String, String> idMap = new HashMap<>();
        Response result;
        try {
            // Mimic whether this is a tradition or a section file
            result = parseGraphML(filestream, filename, parentNode, idMap, isSingleSection);
            // Did something go wrong? If so, exit now
            if (result.getStatus() != Response.Status.CREATED.getStatusCode())
                return result;
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }
        return result;
    }

    /**
     * Parses a zipped set of GraphML files representing either an entire tradition, or a single
     * tradition section. Returns the ID of the object (either tradition or section) that was created.
     *
     * @param filestream      - an InputStream with the XML data
     * @param parentNode      - a Node to represent the parent of this data, either a tradition or a section
     * @param isSingleSection - whether we are parsing a whole tradition or just a single section into
     *                        an existing tradition
     * @return a Response object carrying a JSON dictionary {@code {"parentId": <ID>}}
     */

    public Response parseGraphMLZip(InputStream filestream, Node parentNode, boolean isSingleSection) {
        // Keep track of GraphML ID -> created node ID
        HashMap<String, String> idMap = new HashMap<>();
        // Initialise our response
        String tradId = null;
        String firstSectionId = null;
        // Unzip the file and send each XML file therein to the "real" parser
        try {
            // Get the XML files out of the zip stream
            LinkedHashMap<String, InputStream> inputXML = Util.extractGraphMLZip(filestream);
            // Make sure we actually got some files
            if (inputXML.isEmpty())
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(jsonerror("No files found in GraphML zip input")).build();
            // Make sure the tradition.xml file is first
            boolean seenTrad = false;

            for (String filename : inputXML.keySet()) {
                seenTrad = seenTrad || filename.equals("tradition.xml");
                if (!seenTrad)
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(jsonerror("Bad zipfile input - is tradition.xml not first?")).build();
                InputStream fi = inputXML.get(filename);
                Response result = parseGraphML(fi, filename, parentNode, idMap, isSingleSection);
                // Did something go wrong? If so, exit now
                if (result.getStatus() != Response.Status.CREATED.getStatusCode())
                    return result;
                // If we haven't picked up a return object ID yet, pick it up on the first pass for the
                // tradition ID, or the second pass if we want to return the section ID
                if (filename.equals("tradition.xml"))
                    tradId = result.getEntity().toString();
                else if (firstSectionId == null)
                    firstSectionId = result.getEntity().toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }
        String ret = isSingleSection ? firstSectionId : tradId;
        return Response.status(Response.Status.CREATED).entity(ret).build();
    }

    /**
     * Parses the contents of the XML file stream, returning ...something
     *
     * @param filestream      - The unzipped XML file stream
     * @param fileName        - The name of the XML file we are working on
     * @param parentNode      - The tradition node (if !isSingleSection), or the section node
     * @param idMap           - A map of node xml:id -> Neo4J node ID
     * @param isSingleSection - Whether we are parsing a new tradition, or adding a section to an existing one
     * @return a Response indicating the result
     */
    private Response parseGraphML(InputStream filestream, String fileName, Node parentNode,
                                  Map<String, String> idMap, boolean isSingleSection) {
        // We will use a DOM parser for this
        Document doc = Util.openFileStream(filestream);
        if (doc == null)
            return Response.serverError().entity(jsonerror("No document found")).build();

        Element rootEl = doc.getDocumentElement();
        rootEl.normalize();

        // Get the data keys and their types; the map entries are e.g.
        // "dn0" -> ["neolabel", "string"]
        HashMap<String, String[]> dataKeys = getDataKeyMap(rootEl);

        String parentId;

        // Now get to work with node and relationship creation.
    	parentNode = tx.getNodeByElementId(parentNode.getElementId());
    	// Get the tradition node
    	Node traditionNode = isSingleSection ? VariantGraphService.getTraditionNode(tx, parentNode) : parentNode;
        // The UUID of the tradition node that was passed in to receive the parsed data
        String tradId = traditionNode.getProperty("id").toString();
        // The Neo4J node that contains our section, if we are parsing a section
        Node thisSection = null;
        // The list of readings that come out of this file, so that we can update their section
        ArrayList<Node> thisReadings = new ArrayList<>();
        // The <node/> elements in the XML
        NodeList entityNodes = rootEl.getElementsByTagName("node");
        // The parentId we return should be the ID of the parent node for whatever we just processed –
        // either the tradition ID if we are parsing a new tradition, or the section ID if we are
        // parsing a new section into a tradition. LATER does this make sense??
        parentId = fileName.equals("tradition.xml") ? tradId : parentNode.getElementId();
        // If we are parsing a new section into an existing tradition, get the tradition metadata nodes
        // to make sure we don't duplicate them. This should only happen if we have isSingleSection set,
        // since otherwise the relevant nodes should already be in idMap.
		List<Node> existingMeta = isSingleSection ? StreamSupport
                .stream(VariantGraphService.returnTraditionMeta(tx, traditionNode).nodes().spliterator(), false).toList()
				: new ArrayList<>();
        List<Relationship> existingMetaRel = isSingleSection ? StreamSupport
                .stream(VariantGraphService.returnTraditionMeta(tx, traditionNode).relationships().spliterator(), false).toList()
				: new ArrayList<>();

        // We will hold back nodes that were labeled by the user rather than the system, such as annotations,
        // so that we can add them to the graph with the existing verification / sanity checks.
        HashMap<String, org.w3c.dom.Node> userLabeledNodes = new HashMap<>();
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
                    // If there is already a different tradition with this tradition ID, we are making a duplicate
                    // and the effective UUID of this one was already set in Root.java. If there is not yet a
                    // different tradition with this UUID, we need to retain the UUID in the GraphML.

                    String fileTraditionId = nodeProperties.get("id").toString();
                    Node existingTradition = tx.findNode(Nodes.TRADITION, "id", fileTraditionId);
                    if (existingTradition != null) // There is another tradition with this UUID; don't re-use it.
                        nodeProperties.remove("id");
                    else { // There is no other tradition with this UUID; update the tradId to match the XML.
                        tradId = fileTraditionId;
                        parentId = tradId;
                    }
                    // This node is already created, but we need to reset its properties according to
                    // what is in the GraphML file. We also save this ID as the parent ID that was created.
                    for (String p : nodeProperties.keySet())
                        traditionNode.setProperty(p, nodeProperties.get(p));
                } // else
                    // If we are parsing the tradition meta-info for a single section, or if we are parsing a
                    // section XML file, we ignore the data for this node and we will keep the parent ID as the
                    // ID of the section node.
                // Now set the tradition node to be our real tradition node, whether from this file or
                // from the existing tradition for this section
                idMap.put(xmlId, traditionNode.getElementId());

            } else {
                // Now we have the information of the XML, we can create the new node if we need to.
                // We don't need to...
                // - if it is already in the idMap
                // - if it is the SECTION node
                // - if this is a single section upload, we are in the tradition.xml file, and a node
                //   with identical properties already exists
                Node entity = null;
                if (idMap.containsKey(xmlId)) {
                    entity = tx.getNodeByElementId(idMap.get(xmlId));
                } else {
                    // Is this the single-section section node?
                    boolean exists = false;
                    if (neolabel.contains("SECTION") && isSingleSection)
                        entity = parentNode;
                    else if (!existingMeta.isEmpty()) {
                        // Does the node already exist in the tradition's metadata?
                        for (Node ex : existingMeta) {
                            exists = true;
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
                    if (entity == null) entity = tx.createNode();
                    // Record the XML -> n4j correlation
                    idMap.put(xmlId, entity.getElementId());
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
                // Notice if it is a section node
                if (neolabel.contains("SECTION")) thisSection = entity;
                if (neolabel.contains("READING")) thisReadings.add(entity);
            }
        }

        // Next go through all the edges and create them between the nodes. Keep track of the
        // relation types we have seen.
        HashSet<String> relationTypesUsed = new HashSet<>();
        // Any witness sigla found herein, so that we can ensure that the witnesses exist. This
        // is mostly useful when we are creating a tradition from a single exported section.
        HashSet<String> witnessSigla = new HashSet<>();
        NodeList edgeNodes = rootEl.getElementsByTagName("edge");
        for (int i = 0; i < edgeNodes.getLength(); i++) {
            NamedNodeMap edgeAttrs = edgeNodes.item(i).getAttributes();
            String sourceXmlId = edgeAttrs.getNamedItem("source").getNodeValue();
            String targetXmlId = edgeAttrs.getNamedItem("target").getNodeValue();
            // Is this an annotation edge? If so, add it to userLabeledEdges for later processing
            Node source;
            Node target;
            try {
                source = tx.getNodeByElementId(idMap.get(sourceXmlId));
                target = tx.getNodeByElementId(idMap.get(targetXmlId));
            } catch (IllegalArgumentException e) {
                // If the source or the target is in userLabeledNodes, we know this is a userLabeledEdge, i.e.
                // an annotation edge. Skip it for later addition through the annotation framework
                if (userLabeledNodes.containsKey(sourceXmlId) || userLabeledNodes.containsKey(targetXmlId)) {
                    userLabeledEdges.add(edgeNodes.item(i));
                    continue;
                } else
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
            if (!existingMetaRel.isEmpty()) {
                // Does the node already exist in the tradition's metadata?
                boolean exists = true;
                for (Relationship ex : existingMetaRel.stream()
                        .filter(x -> x.getStartNode().equals(source) && x.getEndNode().equals(target))
                        .toList()) {
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
            if (newRel == null && !relationshipExists(source, neolabel)) { // If it exists already we don't touch it.
                try {
                	newRel = source.createRelationshipTo(target, ERelations.valueOf(neolabel));
                    edgeProperties.forEach(newRel::setProperty);
                    // Catch any relation types and witnesses that were used, so that we can ensure
                    // their existence when we are done
                    if (neolabel.equals("RELATED"))
                        relationTypesUsed.add(edgeProperties.get("type").toString());
                    else if (neolabel.equals("SEQUENCE")) {
                        SequenceModel sm = new SequenceModel(newRel);
                        witnessSigla.addAll(sm.getWitnesses());
                        if (sm.getLayers() != null)
                            sm.getLayers().values().forEach(witnessSigla::addAll);
                    }
                } catch (IllegalArgumentException e) {
                    // We are either here because we tried to link an annotation (which doesn't yet exist)
                    // to the tradition via a HAS_ANNOTATION link, or because we tried to use a user-defined
                    // relationship label in the context of an annotation where both nodes already existed.
                    // If the former, ignore it (we will add these links below  `); if the latter, it is a case
                    // where the annotation node was already created (e.g. the same annotation is present in
                    // multiple sections) but we still need to link it through proper channels below.
                    if (!neolabel.equals("HAS_ANNOTATION"))
                        userLabeledEdges.add(edgeNodes.item(i));
                }
            }
        }

        // Reset the section IDs stored on each reading to the ID of the newly created node
        for (Node r : thisReadings) {
            if (thisSection == null) {
                return Response.status(Response.Status.BAD_REQUEST).entity(
                        jsonerror("Reading nodes found in a file without a section")).build();
            }
            r.setProperty("section_id", thisSection.getElementId());
        }

        // Ensure that the tradition and section are linked
        if (thisSection != null)
            Util.ensureSectionLink(tx, traditionNode, thisSection);

        // Ensure that all witnesses exist
        witnessSigla.forEach(x -> Util.findOrCreateExtant(tx, traditionNode, x));

        // Ensure that all relation types exist
        for (String rt : relationTypesUsed)
            RelationService.returnRelationType(tx, tradId, rt);

        // Now add user-labeled nodes separately, via the existing validation infrastructure.
        HashMap<String,AnnotationModel> annotationsToAdd = new HashMap<>();
        for (org.w3c.dom.Node xn : userLabeledNodes.values()) {
            AnnotationModel am = new AnnotationModel();
            // Get the properties on this annotation node.
            HashMap<String, Object> props = returnProperties(((Element) xn).getElementsByTagName("data"), dataKeys);
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
                HashMap<String, Object> edgeProps = returnProperties(((Element) xe).getElementsByTagName("data"), dataKeys);
                if (sourceXmlId.equals(((Element) xn).getAttribute("id"))) {
                    // It is a link that belongs to this source. For now set the XML element ID as the
                    // target; this will need to be converted progressively into real node IDs.
                    AnnotationLinkModel alm = new AnnotationLinkModel();
                    alm.setTarget(targetXmlId);
                    alm.setType(edgeProps.get("neolabel").toString());
                    if (edgeProps.containsKey("follow")) alm.setFollow(edgeProps.get("follow").toString());
                    am.addLink(alm);
                }
            }
            annotationsToAdd.put(((Element) xn).getAttribute("id"), am);
        }
        while (!annotationsToAdd.isEmpty()) {
            List<String> toRemove = new ArrayList<>();
            for (String amid : annotationsToAdd.keySet()) {
                AnnotationModel am = annotationsToAdd.get(amid);
                // Look at the links and see if the targets exist yet
                boolean targetsExist = true;
                for (String target : am.getLinks().stream().map(AnnotationLinkModel::getTarget).toList()) {
                    targetsExist = targetsExist && idMap.containsKey(target);
                }
                if (targetsExist) {
                    // We can update the links with the "real" nodes and create the annotation.
                    for (AnnotationLinkModel alm : am.getLinks()) {
                        Node nodeTarget = tx.getNodeByElementId(idMap.get(alm.getTarget()));
                        alm.setTarget(nodeTarget.getElementId());
                    }
                    AnnotationModel newAnno;
                    try {
                        newAnno = AnnotationService.addAnnotationToTradition(tx, traditionNode, am);
                    } catch (IllegalArgumentException e) {
                        throw new UnsupportedOperationException(String.format(
                                "Error on adding user annotation %s/%s: %s",
                                am.getId(), am.getLabel(), e.getMessage()));
                    }
                    // Add the new annotation node to the idMap so that it is there for any
                    // dependent annotations
                    idMap.put(amid, newAnno.getId());
                    // Mark this annotation to be removed from the queue
                    toRemove.add(amid);
                }
            }
            // Guard against infinite loops
            if (toRemove.isEmpty())
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(jsonerror("Annotations in XML could not all be resolved")).build();
            toRemove.forEach(annotationsToAdd::remove);
        }

        // Sanity check: if we created any relationship-less nodes, delete them again.
        idMap.values().stream().map(tx::getNodeByElementId)
                .filter(n -> !n.hasRelationship()).forEach(Node::delete);

        return Response.status(Response.Status.CREATED).entity(jsonresp("parentId", parentId)).build();
    }

    private static HashMap<String, String[]> getDataKeyMap(Element rootEl) {
        HashMap<String, String[]> dataKeys = new HashMap<>();
        NodeList keyNodes = rootEl.getElementsByTagName("key");
        for (int i = 0; i < keyNodes.getLength(); i++) {
            NamedNodeMap keyAttrs = keyNodes.item(i).getAttributes();
            String[] dataInfo = new String[]{keyAttrs.getNamedItem("attr.name").getNodeValue(),
                    keyAttrs.getNamedItem("attr.type").getNodeValue()};
            dataKeys.put(keyAttrs.getNamedItem("id").getNodeValue(), dataInfo);
        }
        return dataKeys;
    }

    /**
     * Checks for ANNOTATIONLABEL nodes whether a relationship already exists for type and direction.
     * 
     * @param source	the node
     * @param type		relationship type
     * @return a boolean to indicate whether the relationship exists
     */
	private boolean relationshipExists(Node source, String type) {
		boolean exists = false;

		if (StreamSupport.stream(source.getLabels().spliterator(), false).anyMatch(label -> Nodes.ANNOTATIONLABEL.name().equals(label.name()))
				&& (type.equals(ERelations.HAS_PROPERTIES.name()) || type.equals(ERelations.HAS_LINKS.name()))) {
			Relationship rel = source.getSingleRelationship(ERelations.valueOf(type), Direction.OUTGOING);
			if (rel != null && rel.getStartNode().equals(source)) {
				exists = true;
			}
		}
		return exists;
	}

    private HashMap<String, Object> returnProperties(NodeList dataNodes, HashMap<String, String[]> dataKeys) {
        HashMap<String, Object> nodeProperties = new HashMap<>();
        for (int j = 0; j < dataNodes.getLength(); j++) {
            org.w3c.dom.Node datumXML = dataNodes.item(j);
            String keyCode = datumXML.getAttributes().getNamedItem("key").getNodeValue();
            String keyVal = datumXML.getTextContent();
            String[] keyInfo = dataKeys.get(keyCode);
            Object propValue = switch (keyInfo[1]) {
                case "boolean" -> Boolean.valueOf(keyVal);
                case "long" -> Long.valueOf(keyVal);
                case "int" -> Integer.valueOf(keyVal);
                case "stringarray" -> keyVal.replace("[", "").replace("]", "").split(",\\s+");
                default -> // e.g. "string"
                        keyVal;
            };
            // These datatypes need to be kept in sync with exporter.GraphMLExporter
            nodeProperties.put(keyInfo[0], propValue);
        }
        return nodeProperties;
    }

}
