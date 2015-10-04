package net.stemmaweb.services;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.*;

import javax.ws.rs.core.Response;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.IResource;
import net.stemmaweb.rest.Nodes;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Uniqueness;

/**
 * This class provides a method for importing GraphMl (XML) File into Neo4J
 * 
 * @author PSE FS 2015 Team2
 */
public class GraphMLToNeo4JParser implements IResource
{
    private GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private GraphDatabaseService db = dbServiceProvider.getDatabase();

    public Response parseGraphML(String filename, String userId, String tradName)
        throws FileNotFoundException {
        File file = new File(filename);
        InputStream in = new FileInputStream(file);
        return parseGraphML(in, userId, tradName);
    }

    /**
     * Reads xml file input stream and imports it into Neo4J Database
     *
     * @param xmldata
     *            - the GraphML file stream
     * @param userId
     *            - the user id who will own the tradition
     * @param tradName
     *            tradition name that should be used
     * @return Http Response with the id of the imported tradition
     * @throws FileNotFoundException
     * @throws XMLStreamException
     */
    public Response parseGraphML(InputStream xmldata, String userId, String tradName)
            throws FileNotFoundException {
        XMLInputFactory factory;
        XMLStreamReader reader;
        factory = XMLInputFactory.newInstance();

        // Some variables to collect information
        HashMap<String, Long> idToNeo4jId = new HashMap<>();
        HashMap<String, String> keymap = new HashMap<>();   // to store data key mappings
        HashMap<String, Boolean> witnesses = new HashMap<>();  // to store witnesses found
        String stemmata = ""; // holds Stemmatas for this GraphMl

        // Some state variables
        int last_inserted_id;
        Node graphRoot;
        Node traditionNode = null;    // this will be the entry point of the graph
        Node currentNode = null;	     // holds the current node
        String currentGraph = null;
        Relationship currentRel = null; // holds the current relationship

        try (Transaction tx = db.beginTx()) {
            // retrieves the last inserted tradition ID and increments it
            // TODO maybe this should be a UUID.
            graphRoot = db.findNode(Nodes.ROOT, "name", "Root node");
            String prefix = graphRoot.getProperty("LAST_INSERTED_TRADITION_ID").toString();
            last_inserted_id = Integer.parseInt(prefix);
            last_inserted_id++;
            prefix = String.valueOf(last_inserted_id) + "_";

            traditionNode = db.createNode(Nodes.TRADITION); // create the root node of tradition
            traditionNode.setProperty("id", String.valueOf(last_inserted_id));

            reader = factory.createXMLStreamReader(xmldata);
            outer:
            while (true) {
                // START READING THE GRAPHML FILE
                int event = reader.next(); // gets the next <element>

                switch (event) {
                    case XMLStreamConstants.END_ELEMENT:
                        if (reader.getLocalName().equals("graph")) {
                            // Clear out the currentGraph string.
                            currentGraph = null;
                        } else if (reader.getLocalName().equals("node")){
                            // Finished working on currentNode
                            currentNode = null;
                        } else if (reader.getLocalName().equals("edge")) {
                            // Finished working on currentRel
                            currentRel = null;
                        }
                        break;
                    case XMLStreamConstants.END_DOCUMENT:
                        reader.close();
                        break outer;
                    case XMLStreamConstants.START_ELEMENT:
                        String local_name = reader.getLocalName();
                        switch (local_name) {
                            case "data":
                                if (currentRel != null) {
                                    // We are working on a relationship node. Apply the data.
                                    String attr = keymap.get(reader.getAttributeValue("", "key"));
                                    String val = reader.getElementText();

                                    if (attr.equals("id"))
                                        currentRel.setProperty("id", prefix + val);
                                    else if (attr.equals("witness"))
                                    {
                                        // Check that this is a sequence relationship
                                        assert currentRel.isType(ERelations.SEQUENCE);
                                        // Add the witness to the current relationship's "witnesses" array
                                        String[] witList = (String[]) currentRel.getProperty("witnesses");
                                        ArrayList<String> currentWits = new ArrayList<>(Arrays.asList(witList));
                                        currentWits.add(val);
                                        currentRel.setProperty("witnesses", currentWits.toArray(new String[currentWits.size()]));

                                        // Store the existence of this witness
                                        // TODO implement a.c. / p.c. logic
                                        witnesses.put(val, true);
                                    }
                                    else
                                        currentRel.setProperty(attr, val);
                                } else if (currentNode != null) {
                                    // Working on either the tradition itself, or a node.
                                    String attr = keymap.get(reader.getAttributeValue("", "key"));
                                    String text = reader.getElementText();
                                    switch (attr) {
                                        // Tradition node attributes
                                        case "name":
                                            if (text.equals(""))
                                                text = tradName;
                                            currentNode.setProperty(attr, text);
                                            break;
                                        case "stemmata":
                                            stemmata = text;
                                            break;
                                        case "user":
                                            // Use the user ID from the file if we are asked to
                                            if (userId.equals("FILE"))
                                                userId = text;
                                            break;

                                        // Reading node attributes
                                        case "id":  // We don't use the old reading IDs
                                            break;
                                        case "rank":
                                            currentNode.setProperty(attr, Long.parseLong(text));
                                            break;
                                        case "is_start":
                                            traditionNode.createRelationshipTo(currentNode, ERelations.COLLATION);
                                            currentNode.setProperty(attr, text);
                                            break;
                                        case "is_end":
                                            traditionNode.createRelationshipTo(currentNode, ERelations.HAS_END);
                                            currentNode.setProperty(attr, text);
                                            break;
                                        default:
                                            currentNode.setProperty(attr, text);
                                    }
                                }
                                break;
                            case "edge":
                                // TODO why are namespaces not being used?
                                String sourceName = prefix + reader.getAttributeValue("", "source");
                                String targetName = prefix + reader.getAttributeValue("", "target");
                                Node from = db.getNodeById(idToNeo4jId.get(sourceName));;
                                Node to = db.getNodeById(idToNeo4jId.get(targetName));
                                ERelations relKind = (currentGraph.equals("relationships")) ?
                                        ERelations.RELATED : ERelations.SEQUENCE;
                                // See if there is a (probably sequence) relationship already
                                if (from.hasRelationship(relKind, Direction.BOTH)) {
                                    Iterator<Relationship> existingRels = from.getRelationships(relKind, Direction.BOTH).iterator();
                                    while (existingRels.hasNext()) {
                                        Relationship qr = existingRels.next();
                                        if (qr.getStartNode().equals(to) || qr.getEndNode().equals(to)) {
                                            // TODO sanity check that the relationships match?
                                            // If a RELATED link appears twice, the second one will override.
                                            currentRel = qr;
                                            break;
                                        }
                                    }
                                }
                                // If not, create it (with an empty witness list for SEQUENCEs.)
                                if (currentRel == null) {
                                    currentRel = from.createRelationshipTo(to, relKind);
                                    if (relKind.equals(ERelations.SEQUENCE)) {
                                        String[] witList = {};
                                        currentRel.setProperty("witnesses", witList);
                                    }
                                }
                                break;
                            case "node":
                                if (!currentGraph.equals("relationships")) {
                                    // only store nodes for the sequence graph
                                    currentNode = db.createNode(Nodes.READING);
                                    String nodeId = prefix + reader.getAttributeValue("", "id");

                                    idToNeo4jId.put(nodeId, currentNode.getId());
                                }
                                break;
                            case "key":
                                String key = reader.getAttributeValue("", "id");
                                String value = reader.getAttributeValue("", "attr.name");
                                keymap.put(key, value);
                                break;
                            case "graph":
                                currentGraph = reader.getAttributeValue("", "id");
                                currentNode = traditionNode;
                                break;
                        }
                        break;
                }
            }

            // Create the witness nodes
            for (String sigil: witnesses.keySet()) {
                Node witnessNode = db.createNode(Nodes.WITNESS);
                witnessNode.setProperty("sigil", sigil);
                traditionNode.createRelationshipTo(witnessNode, ERelations.HAS_WITNESS);
            }

            // Set the user if it exists in the system; auto-create the user if it doesn't exist
            Node userNode = db.findNode(Nodes.USER, "id", userId);
            if (userNode == null) {
                userNode = db.createNode(Nodes.USER);
                userNode.setProperty("id", userId);
                graphRoot.createRelationshipTo(userNode, ERelations.SYSTEMUSER);
            }
            userNode.createRelationshipTo(traditionNode, ERelations.OWNS_TRADITION);

            graphRoot.setProperty("LAST_INSERTED_TRADITION_ID",
                            prefix.substring(0, prefix.length() - 1));

            tx.success();
        } catch(Exception e) {
            e.printStackTrace();

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error: Tradition could not be imported!")
                    .build();
        }

        String[] graphs = stemmata.split("\n");

        for(String graph : graphs) {
            DotToNeo4JParser parser = new DotToNeo4JParser(db);
            parser.parseDot(graph, last_inserted_id + "");
        }

        return Response.status(Response.Status.OK)
                .entity("{\"tradId\":" + last_inserted_id + "}")
                .build();
    }
}