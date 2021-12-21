package net.stemmaweb.parser;

import net.stemmaweb.model.AnnotationLabelModel;
import net.stemmaweb.model.AnnotationLinkModel;
import net.stemmaweb.model.AnnotationModel;
import net.stemmaweb.model.RelationTypeModel;
import net.stemmaweb.rest.*;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.VariantGraphService;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.Node;
import org.w3c.dom.*;

import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Parser for the GraphML that Stemmarest itself produces.
 *
 * Created by tla on 17/02/2017.
 */
public class GraphMLParser {
    private final GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private final GraphDatabaseService db = dbServiceProvider.getDatabase();

    /**
     * Parses a GraphML file representing either an entire tradition, or a single tradition
     * section. Returns the ID of the object (either tradition or section) that was created.
     *
     * @param filestream - an InputStream with the XML data
     * @param traditionNode - a Node to represent the tradition this data belongs to
     * @return a Response object carrying a JSON dictionary {"parentId": <ID>}
     */

    public Response parseGraphML(InputStream filestream, Node traditionNode)
    {
        // We will use a DOM parser for this
        Document doc = Util.openFileStream(filestream);
        if (doc == null)
            return Response.serverError().entity(Util.jsonerror("No document found")).build();

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

        String parentId = null;
        String parentLabel = null;
        ArrayList<Node> sectionNodes = new ArrayList<>();
        ArrayList<Node> witnessNodes = new ArrayList<>();
        ArrayList<Node> annoLabelNodes = new ArrayList<>();
        HashSet<String> sigla = new HashSet<>();
        // Keep track of XML ID to Neo4J ID mapping for all nodes
        HashMap<String, Node> entityMap = new HashMap<>();

        // Now get to work with node and relationship creation.
        try (Transaction tx = db.beginTx()) {
            // The UUID of the tradition that was passed in for parsing
            String tradId = traditionNode.getProperty("id").toString();
            NodeList entityNodes = rootEl.getElementsByTagName("node");
            // Hold back nodes that were labeled by the user rather than the system, such as annotations,
            // so that we can add them to the graph with the existing verification / sanity checks.
            ArrayList<org.w3c.dom.Node> userLabeledNodes = new ArrayList<>();
            ArrayList<org.w3c.dom.Node> userLabeledEdges = new ArrayList<>();
            for (int i = 0; i < entityNodes.getLength(); i++) {
                org.w3c.dom.Node entityXML = entityNodes.item(i);
                NamedNodeMap entityAttrs = entityXML.getAttributes();
                String xmlId = entityAttrs.getNamedItem("id").getNodeValue();
                NodeList dataNodes = ((Element) entityXML).getElementsByTagName("data");
                HashMap<String, Object> nodeProperties = returnProperties(dataNodes, dataKeys);
                if (!nodeProperties.containsKey("neolabel"))
                    return Response.status(Response.Status.BAD_REQUEST).entity(Util.jsonerror("Node without label found")).build();
                String neolabel = nodeProperties.remove("neolabel").toString();
                String[] entityLabel = neolabel.replace("[", "").replace("]", "").split(",\\s+");

                if (neolabel.contains("TRADITION")) {
                    // We are apparently parsing a whole tradition.
                    // If there is already a different tradition with this tradition ID, we are making a
                    // duplicate and the real ID of this one was set in Root.java; if not, fix our tradition
                    // node to match the one in the GraphML.
                    if (parentLabel != null) {
                        // We apparently have two TRADITION nodes. Abort.
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity(Util.jsonerror("Multiple TRADITION nodes in input")).build();
                    }

                    String fileTraditionId = nodeProperties.get("id").toString();
                    Node existingTradition = db.findNode(Nodes.TRADITION, "id", fileTraditionId);
                    if (existingTradition == null) {
                        // Set the ID of the new tradition node to match the old ID.
                        traditionNode.setProperty("id", fileTraditionId);
                        tradId = fileTraditionId;
                    } // else there is another tradition with the original ID, so this is a duplicate
                    // and needs its new ID.

                    // This node is already created, but we need to reset its properties according to
                    // what is in the GraphML file. We also save this ID as the parent ID that was created.
                    for (String p : nodeProperties.keySet())
                        if (!p.equals("id"))
                            traditionNode.setProperty(p, nodeProperties.get(p));
                    parentId = tradId;
                    parentLabel = "tradition";
                    entityMap.put(xmlId, traditionNode);
                } else {
                    // Now we have the information of the XML, we can create the node.
                    Node entity = db.createNode();
                    for (String l : entityLabel) {
                        try {
                            entity.addLabel(Nodes.valueOf(l));
                            nodeProperties.forEach(entity::setProperty);
                            entityMap.put(xmlId, entity);
                            // Save section node(s), in case we are uploading individual sections and need to connect
                            // them to our tradition node
                            if (neolabel.contains("[SECTION]")) sectionNodes.add(entity);
                            if (neolabel.contains("[WITNESS]")) witnessNodes.add(entity);
                            if (neolabel.contains("[ANNOTATIONLABEL]")) annoLabelNodes.add(entity);
                        } catch (IllegalArgumentException e) {
                            // This is an annotation node, which we will deal with in a separate pass.
                            userLabeledNodes.add(entityXML);
                            entity.delete();
                        }
                    }
                }
            }

            // Check the parent type
            if (parentLabel == null) // i.e. if it hasn't been set to "tradition"
                if (sectionNodes.size() == 0)
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Util.jsonerror("Neither TRADITION nor SECTION found in input")).build();
                else
                    parentLabel = "section";

            // If we have seen multiple sections but no tradition, error out.
            if (sectionNodes.size() > 1 && parentLabel.equals("section"))
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Util.jsonerror("Multiple SECTION nodes but no TRADITION in input")).build();


            // Next go through all the edges and create them between the nodes. Keep track of the
            // relation types we have seen.
            NodeList edgeNodes = rootEl.getElementsByTagName("edge");
            HashSet<String> seenRelationTypes = new HashSet<>();
            for (int i = 0; i < edgeNodes.getLength(); i++) {
                NamedNodeMap edgeAttrs = edgeNodes.item(i).getAttributes();
                String sourceXmlId = edgeAttrs.getNamedItem("source").getNodeValue();
                String targetXmlId = edgeAttrs.getNamedItem("target").getNodeValue();
                Node source = entityMap.get(sourceXmlId);
                Node target = entityMap.get(targetXmlId);
                NodeList dataNodes = ((Element) edgeNodes.item(i)).getElementsByTagName("data");
                HashMap<String, Object> edgeProperties = returnProperties(dataNodes, dataKeys);
                if (!edgeProperties.containsKey("neolabel"))
                    return Response.serverError().entity(Util.jsonerror("Node without label found")).build();
                String neolabel = edgeProperties.remove("neolabel").toString();
                // If this is a SEQUENCE relation, track the sigla so we can be sure the witnesses
                // exist (they are not exported for sections.)
                if (neolabel.equals("SEQUENCE")) {
                    for (String layer : edgeProperties.keySet()) {
                        sigla.addAll(Arrays.asList((String[]) edgeProperties.get(layer)));
                    }
                } else if (neolabel.equals("RELATED")) {
                    if (!edgeProperties.containsKey("type"))
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity(Util.jsonerror("Relation defined without a type")).build();
                    seenRelationTypes.add(edgeProperties.get("type").toString());
                }
                Relationship newRel;
                try {
                    newRel = source.createRelationshipTo(target, ERelations.valueOf(neolabel));
                    edgeProperties.forEach(newRel::setProperty);
                } catch (IllegalArgumentException e) {
                    // We are either here because we tried to link an annotation (which doesn't yet exist)
                    // to the tradition via a HAS_ANNOTATION link, or because we tried to use a user-defined
                    // relationship label in the context of an annotation.
                    // If the former, ignore it (we will add these links later); if the latter, add it to
                    // our list of user-defined entities that should be dealt with later.
                    if (!neolabel.equals("HAS_ANNOTATION")) userLabeledEdges.add(edgeNodes.item(i));
                }
            }

            // Connect our new section to an existing tradition node, and to the last existing section,
            // if this is a section-only upload.
            if (parentLabel.equals("section")) {
                Node newSection = sectionNodes.get(0);
                ArrayList<Node> existingSections = VariantGraphService.getSectionNodes(tradId, db);
                assert (existingSections != null); // We should have already errored if this will be null.
                if (existingSections.size() > 0) {
                    Node lastExisting = existingSections.get(existingSections.size() - 1);
                    lastExisting.createRelationshipTo(newSection, ERelations.NEXT);
                }
                traditionNode.createRelationshipTo(newSection, ERelations.PART);
                parentId = String.valueOf(newSection.getId());
            }

            // Check any witness nodes we created and connect them to the tradition node if they wouldn't
            // be duplicates
            for (Node w : witnessNodes) {
                if (!witnessExists(traditionNode, w) && w.getProperty("hypothetical").equals(false))
                    traditionNode.createRelationshipTo(w, ERelations.HAS_WITNESS);
            }

            // Check any annotation label nodes & associates we created and connect them to the tradition
            // node if they wouldn't be duplicates
            for (Node al : annoLabelNodes) {
                if (!annoLabelExists(traditionNode, al)) {
                    traditionNode.createRelationshipTo(al, ERelations.HAS_ANNOTATION_TYPE);
                } else {
                    // This is a redundant annotation label, so delete it as well as its links and properties.
                    al.getRelationships(Direction.OUTGOING).forEach(x -> {
                        x.getEndNode().delete();
                        x.delete();
                    });
                    al.delete();
                }
            }

            // Reset the section IDs stored on each reading to the ID of the newly created node
            for (Node r : entityMap.values().stream().filter(x -> x.hasLabel(Nodes.READING)).collect(Collectors.toList())) {
                String rSectId = r.getProperty("section_id").toString();
                r.setProperty("section_id", entityMap.get(rSectId).getId());
            }

            // Ensure that all witnesses we have encountered actually exist.
            for (String sigil : sigla) {
                Util.findOrCreateExtant(traditionNode, sigil);
            }

            // Ensure that all the relation types we have encountered actually exist.
            ArrayList<String> existingTypes = new ArrayList<>();
            traditionNode.getRelationships(ERelations.HAS_RELATION_TYPE, Direction.OUTGOING)
                    .forEach(x -> existingTypes.add(x.getEndNode().getProperty("name").toString()));
            for (String rtype : seenRelationTypes) {
                if (!existingTypes.contains(rtype)) {
                    RelationTypeModel rtm = new RelationTypeModel();
                    rtm.setName(rtype);
                    rtm.setDefaultsettings(true);
                    Response rtResult = new RelationType(tradId, rtype).create(rtm);
                    if (rtResult.getStatus() == Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())
                        return rtResult;
                }
            }

            // Now add user-labeled nodes separately, via the existing validation infrastructure.
            List<AnnotationModel> annotationsToAdd = new ArrayList<>();
            for (org.w3c.dom.Node xn : userLabeledNodes) {
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
                        targetsExist = targetsExist && entityMap.containsKey(target.toString());
                    }
                    if (targetsExist) {
                        // We can update the links with the "real" nodes and create the annotation.
                        for (AnnotationLinkModel alm : am.getLinks()) {
                            Node nodeTarget = entityMap.get(alm.getTarget().toString());
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
                            .entity(Util.jsonerror("Annotations in XML could not all be resolved")).build();
                annotationsToAdd.removeAll(toRemove);
            }

            // Sanity check: if we created any relationship-less nodes, delete them again.
            entityMap.values().stream().filter(n -> !n.hasRelationship()).forEach(Node::delete);

            tx.success();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Util.jsonerror(e.getMessage())).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }

        String response = String.format("{\"parentId\":\"%s\",\"parentLabel\":\"%s\"}", parentId, parentLabel);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    // Return true if the tradition already has a witness with the given sigil.
    // LATER think about consistency checks, in case witness information conflicts
    private boolean witnessExists(Node tradition, Node witness) {
        String sigil = witness.getProperty("sigil").toString();
        return DatabaseService.getRelated(tradition, ERelations.HAS_WITNESS).stream()
                .anyMatch(x -> x.getProperty("sigil", "").equals(sigil));
    }

    // Return true if the tradition already has an annotation under the given name.
    // Throws an IllegalArgumentException if the annotation information conflicts.
    private boolean annoLabelExists(Node tradition, Node alabel) {
        String name = alabel.getProperty("name").toString();
        Optional<Node> matching = DatabaseService.getRelated(tradition, ERelations.HAS_WITNESS).stream()
                .filter(x -> x.getProperty("name", "").equals(name)).findFirst();
        if (!matching.isPresent()) return false;

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
