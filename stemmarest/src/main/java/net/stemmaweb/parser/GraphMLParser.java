package net.stemmaweb.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.*;

import javax.ws.rs.core.Response;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;

import net.stemmaweb.services.GraphDatabaseServiceProvider;
import org.neo4j.graphdb.*;

/**
 * This class provides a method for importing GraphMl (XML) File into Neo4J
 * 
 * @author PSE FS 2015 Team2
 */
public class GraphMLParser {
    private GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private GraphDatabaseService db = dbServiceProvider.getDatabase();

    public Response parseGraphML(String filename, String userId, String tradId)
        throws FileNotFoundException {
        File file = new File(filename);
        InputStream in = new FileInputStream(file);
        return parseGraphML(in, userId, tradId);
    }

    /**
     * Reads xml file input stream and imports it into Neo4J Database. This method assumes
     * that the GraphML describes a valid graph as exported from the legacy Stemmaweb.
     *
     * @param xmldata - the GraphML file stream
     * @param tradId - the tradition's ID
     * @return Http Response with the id of the imported tradition
     * @throws FileNotFoundException
     */
    public Response parseGraphML(InputStream xmldata, String userId, String tradId)
            throws FileNotFoundException {
        XMLInputFactory factory;
        XMLStreamReader reader;
        factory = XMLInputFactory.newInstance();
        try {
            reader = factory.createXMLStreamReader(xmldata);
        } catch (XMLStreamException e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Error: Parsing of tradition file failed!")
                    .build();
        }
        // Some variables to collect information
        HashMap<String, Long> idToNeo4jId = new HashMap<>();
        HashMap<String, String> keymap = new HashMap<>();   // to store data key mappings
        HashMap<String, String> keytypes = new HashMap<>(); // to store data key value types
        HashMap<String, Boolean> witnesses = new HashMap<>();  // to store witnesses found
        String stemmata = ""; // holds Stemmatas for this GraphMl

        // Some state variables
        Node traditionNode;             // this will be the entry point of the graph
        Node currentNode = null;        // holds the current node
        String currentGraph = null;     // holds the ID of the current XML graph element
        Relationship currentRel = null; // holds the current relationship
        String edgeWitness = null;
        String witnessClass = "witnesses";
        String userIdFile = null;

        try (Transaction tx = db.beginTx()) {
            traditionNode = db.findNode(Nodes.TRADITION, "id", tradId);

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
                            // If this is an edge relationship, record the witness information
                            // either in "witnesses" or in the field indicated by "extra"
                            if (currentRel.isType(ERelations.SEQUENCE)) {
                                String[] witList = {};
                                if (currentRel.hasProperty(witnessClass))
                                    witList = (String []) currentRel.getProperty(witnessClass);
                                ArrayList<String> currentWits = new ArrayList<>(Arrays.asList(witList));
                                currentWits.add(edgeWitness);
                                currentRel.setProperty(witnessClass, currentWits.toArray(new String[currentWits.size()]));
                            }

                            // Finished working on currentRel
                            witnessClass = "witnesses";
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
                                    String keytype = keytypes.get(attr);
                                    String val = reader.getElementText();

                                    switch (attr) {
                                        case "id":
                                            break;
                                        case "witness":
                                            // Check that this is a sequence relationship
                                            assert currentRel.isType(ERelations.SEQUENCE);
                                            edgeWitness = val;
                                            // Store the existence of this witness
                                            witnesses.put(val, true);
                                            break;
                                        case "extra":
                                            assert currentRel.isType(ERelations.SEQUENCE);
                                            // If the key type is a Boolean, the witness class is always a.c.;
                                            // otherwise it is the value of val.
                                            if (keytype.equals("boolean"))
                                                witnessClass = "a.c.";
                                            else
                                                witnessClass = val;
                                            break;
                                        case "relationship":
                                            // This is the relationship type, a.k.a. "type" in this system.
                                            // Backwards compatibility for legacy XML
                                            assert currentRel.isType(ERelations.RELATED);
                                            attr = "type";
                                            setTypedProperty(currentRel, attr, keytype, val);
                                            break;
                                        default:
                                            assert currentRel.isType(ERelations.RELATED);
                                            setTypedProperty(currentRel, attr, keytype, val);
                                            break;
                                    }
                                } else if (currentNode != null) {
                                    // Working on either the tradition itself, or a node.
                                    String attr = keymap.get(reader.getAttributeValue("", "key"));
                                    String keytype = keytypes.get(attr);
                                    String text = reader.getElementText();
                                    switch (attr) {
                                        // Tradition node attributes
                                        case "name":
                                            // TODO is this redundant?
                                            if (((String)currentNode.getProperty("name")).length() == 0
                                                    && text.length() > 0) {
                                                currentNode.setProperty(attr, text);
                                            }
                                            break;
                                        case "stemmata":
                                            stemmata = text;
                                            break;
                                        case "user":
                                            // Use the user ID from the file if we are asked to
                                            if (userId.equals("FILE"))
                                                userIdFile = text;
                                            break;

                                        // Reading node attributes
                                        case "id":  // We don't use the old reading IDs
                                            break;
                                        case "is_start":
                                            if(text.equals("1") || text.equals("true"))
                                                traditionNode.createRelationshipTo(currentNode, ERelations.COLLATION);
                                            setTypedProperty(currentNode, attr, keytype, text);
                                            break;
                                        case "is_end":
                                            if (text.equals("1") || text.equals("true"))
                                                traditionNode.createRelationshipTo(currentNode, ERelations.HAS_END);
                                            setTypedProperty(currentNode, attr, keytype, text);
                                            break;
                                        case "rank": // These are set as strings in some XML and shouldn't be
                                            keytype = "int";
                                            setTypedProperty(currentNode, attr, keytype, text);
                                            break;
                                        default:
                                            setTypedProperty(currentNode, attr, keytype, text);
                                    }
                                }
                                break;
                            case "edge":
                                String sourceName = reader.getAttributeValue("", "source");
                                String targetName = reader.getAttributeValue("", "target");
                                Node from = db.getNodeById(idToNeo4jId.get(sourceName));
                                Node to = db.getNodeById(idToNeo4jId.get(targetName));
                                ERelations relKind = (currentGraph.equals("relationships")) ?
                                        ERelations.RELATED : ERelations.SEQUENCE;
                                // Sequence relationships are specified multiple times in the graph, once
                                // per witness. Reading relationships should be specified only once.
                                if (from.hasRelationship(relKind, Direction.BOTH)) {
                                    for (Relationship qr : from.getRelationships(relKind, Direction.BOTH)) {
                                        if (qr.getStartNode().equals(to) || qr.getEndNode().equals(to)) {
                                            // If a RELATED link already exists, we have a problem.
                                            if (relKind.equals(ERelations.RELATED))
                                                return Response.status(Response.Status.BAD_REQUEST)
                                                        .entity("Error: Tradition specifies the reading relationship " +
                                                                sourceName + " -- " + targetName +
                                                                "twice")
                                                        .build();
                                            // It's a SEQUENCE link, so we are good.
                                            currentRel = qr;
                                            break;
                                        }
                                    }
                                }
                                // If not, create it.
                                if (currentRel == null)
                                    currentRel = from.createRelationshipTo(to, relKind);
                                break;
                            case "node":
                                if (!currentGraph.equals("relationships")) {
                                    // only store nodes for the sequence graph
                                    currentNode = db.createNode(Nodes.READING);
                                    currentNode.setProperty("tradition_id", tradId);
                                    String nodeId = reader.getAttributeValue("", "id");
                                    idToNeo4jId.put(nodeId, currentNode.getId());
                                }
                                break;
                            case "key":
                                String key = reader.getAttributeValue("", "id");
                                String value = reader.getAttributeValue("", "attr.name");
                                String type = reader.getAttributeValue("", "attr.type");
                                keymap.put(key, value);
                                keytypes.put(value, type);
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
                witnessNode.setProperty("hypothetical", false);
                witnessNode.setProperty("quotesigil", !isDotId(sigil));
                traditionNode.createRelationshipTo(witnessNode, ERelations.HAS_WITNESS);
            }

            // Change the userID, in case we should use the file's userID
            if (userIdFile != null) {
                Node userNode = db.findNode(Nodes.USER, "id", userId);
                if (userNode != null) {
                    userNode.setProperty("id", userIdFile);
                }
            }
            tx.success();
        } catch(Exception e) {
            e.printStackTrace();

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error: Tradition could not be imported!")
                    .build();
        }

        if( !stemmata.isEmpty() ) {
            String[] graphs = stemmata.split("\n");

            for (String graph : graphs) {
                DotParser parser = new DotParser(db);
                parser.importStemmaFromDot(graph, tradId);
            }
        }

        return Response.status(Response.Status.CREATED)
                .entity("{\"tradId\":\"" + tradId + "\"}")
                .build();
    }

    private void setTypedProperty( PropertyContainer ent, String attr, String type, String val ) {
        Object realval;
        switch (type) {
            case "int":
                realval = Long.valueOf(val);
                break;
            case "boolean":
                realval = val.equals("1") || val.equals("true");
                break;
            default:
                realval = val;
        }
        ent.setProperty(attr, realval);
    }

    private Boolean isDotId (String nodeid) {
        return nodeid.matches("^[A-Za-z][A-Za-z0-9_.]*$")
                || nodeid.matches("^-?(\\.\\d+|\\d+\\.\\d+)$");
    }
}