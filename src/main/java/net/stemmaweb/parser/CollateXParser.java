package net.stemmaweb.parser;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.RelationType;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.VariantGraphService;
import org.neo4j.graphdb.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import jakarta.ws.rs.core.Response;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;

/**
 * Parser for CollateX-collated traditions.
 *
 * @author tla
 */
public class CollateXParser {
    private GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private GraphDatabaseService db = dbServiceProvider.getDatabase();

    public Response parseCollateX(InputStream filestream, Node parentNode)
    {
        // Try this the DOM parsing way
        Document doc;
        try {
            DocumentBuilder dbuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            doc = dbuilder.parse(filestream);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        Element rootEl = doc.getDocumentElement();
        rootEl.normalize();
        // Get the data keys
        HashMap<String,String> dataKeys = new HashMap<>();
        NodeList keyNodes = rootEl.getElementsByTagName("key");
        for (int i = 0; i < keyNodes.getLength(); i++) {
            NamedNodeMap keyAttrs = keyNodes.item(i).getAttributes();
            dataKeys.put(keyAttrs.getNamedItem("id").getNodeValue(), keyAttrs.getNamedItem("attr.name").getNodeValue());
        }
        Node traditionNode = VariantGraphService.getTraditionNode(parentNode);
        try (Transaction tx = db.beginTx()) {
            // Create all the nodes from the graphml nodes
            NodeList readingNodes = rootEl.getElementsByTagName("node");
            HashMap<String,Node> createdReadings = new HashMap<>();
            Long highestRank = 0L;
            boolean transpositionSeen = false;
            for (int i = 0; i < readingNodes.getLength(); i++) {
                NamedNodeMap rdgAttrs = readingNodes.item(i).getAttributes();
                String cxId = rdgAttrs.getNamedItem("id").getNodeValue();
                Node reading = db.createNode(Nodes.READING);
                reading.setProperty("section_id", parentNode.getId());

                NodeList dataNodes = ((Element) readingNodes.item(i)).getElementsByTagName("data");
                for (int j = 0; j < dataNodes.getLength(); j++) {
                    NamedNodeMap dataAttrs = dataNodes.item(j).getAttributes();
                    String keyId = dataAttrs.getNamedItem("key").getNodeValue();
                    String keyVal = dataNodes.item(j).getTextContent();
                    if (dataKeys.get(keyId).equals("rank")) {
                        Long rankVal = Long.valueOf(keyVal);
                        reading.setProperty("rank", rankVal);
                        highestRank = rankVal > highestRank ? rankVal : highestRank;
                        // Detect start node
                        if (rankVal == 0) {
                            parentNode.createRelationshipTo(reading, ERelations.COLLATION);
                            reading.setProperty("is_start", true);
                        }
                    } else if (dataKeys.get(keyId).equals("tokens"))
                        reading.setProperty("text", keyVal);
                }
                createdReadings.put(cxId, reading);
            }
            // Identify the end node. Assuming that there is only one.
            final Long hr = highestRank;
            Optional<Node> endNodeOpt = createdReadings.values().stream()
                    .filter(x -> x.getProperty("rank").equals(hr))
                    .findFirst();
            if (!endNodeOpt.isPresent())
                return Response.serverError().entity(Util.jsonerror("No end node found")).build();
            Node endNode = endNodeOpt.get();
            endNode.setProperty("is_end", true);
            parentNode.createRelationshipTo(endNode, ERelations.HAS_END);

            // Create all the sequences and keep track of the witnesses we see
            HashSet<String> seenWitnesses = new HashSet<>();
            NodeList edgeNodes = rootEl.getElementsByTagName("edge");
            for (int i = 0; i < edgeNodes.getLength(); i++) {
                NamedNodeMap edgeAttrs = edgeNodes.item(i).getAttributes();
                String sourceId = edgeAttrs.getNamedItem("source").getNodeValue();
                String targetId = edgeAttrs.getNamedItem("target").getNodeValue();
                Node source = createdReadings.get(sourceId);
                Node target = createdReadings.get(targetId);
                RelationshipType rtype = null;
                String[] witnessList = null;
                NodeList dataNodes = ((Element) edgeNodes.item(i)).getElementsByTagName("data");
                for (int j = 0; j < dataNodes.getLength(); j++) {
                    NamedNodeMap dataAttrs = dataNodes.item(j).getAttributes();
                    String keyId = dataAttrs.getNamedItem("key").getNodeValue();
                    String keyVal = dataNodes.item(j).getTextContent();
                    if (dataKeys.get(keyId).equals("type")) {
                        if (keyVal.equals("path"))
                            rtype = ERelations.SEQUENCE;
                        else
                            rtype = ERelations.RELATED;
                    } else if (dataKeys.get(keyId).equals("witnesses")) {
                        witnessList = keyVal.split(",\\s+");
                        Collections.addAll(seenWitnesses, witnessList);
                    }
                }
                Relationship relation = source.createRelationshipTo(target, rtype);
                if (rtype != null && rtype.equals(ERelations.RELATED)) {
                    transpositionSeen = true;
                    relation.setProperty("source", source.getId());
                    relation.setProperty("target", target.getId());
                    relation.setProperty("type", "transposition");
                    relation.setProperty("reading_a", source.getProperty("text"));
                    relation.setProperty("reading_b", target.getProperty("text"));
                } else {
                    relation.setProperty("witnesses", witnessList);
                }


            }
            // Create all the witnesses
            seenWitnesses.forEach(x -> Util.findOrCreateExtant(traditionNode, x));
            // Calculate the common readings
            VariantGraphService.calculateCommon(parentNode);

            // Create the 'transposition' relation type if it occurred in the data
            if (transpositionSeen) {
                Response rtResult = new RelationType(traditionNode.getProperty("id").toString(), "transposition")
                        .makeDefaultType();
                if (rtResult.getStatus() == Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())
                    return rtResult;
            }

            tx.success();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }

        return Response.status(Response.Status.CREATED).entity(Util.jsonresp("parentId", parentNode.getId())).build();
    }

}
