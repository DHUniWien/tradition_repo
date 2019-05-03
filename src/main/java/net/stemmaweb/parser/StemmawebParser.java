package net.stemmaweb.parser;

import java.io.InputStream;
import java.util.*;

import javax.ws.rs.core.Response;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import net.stemmaweb.model.RelationModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;

import net.stemmaweb.rest.RelationType;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.ReadingService;
import org.neo4j.graphdb.*;

/**
 * This class provides a method for importing GraphMl (XML) File into Neo4J
 * 
 * @author PSE FS 2015 Team2
 */
public class StemmawebParser {
    private GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private GraphDatabaseService db = dbServiceProvider.getDatabase();

    /* public Response parseGraphML(String filename, Node parentNode)
        throws FileNotFoundException {
        File file = new File(filename);
        InputStream in = new FileInputStream(file);
        return parseGraphML(in, parentNode);
    }*/

    /**
     * Reads xml file input stream and imports it into Neo4J Database. This method assumes
     * that the GraphML describes a valid graph as exported from the legacy Stemmaweb.
     *
     * @param xmldata - the GraphML file stream
     * @param parentNode - the node to which the collation should be attached
     * @return Http Response with the id of the imported tradition
     */
    public Response parseGraphML(InputStream xmldata, Node parentNode) {
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
        // The information on the relevant tradition
        Node traditionNode = DatabaseService.getTraditionNode(parentNode, db);
        String tradId;

        // Some variables to collect information
        HashMap<String, Long> idToNeo4jId = new HashMap<>();
        HashMap<String, String> keymap = new HashMap<>();   // to store data key mappings
        HashMap<String, String> keytypes = new HashMap<>(); // to store data key value types
        HashMap<String, Boolean> witnesses = new HashMap<>();  // to store witnesses found
        HashSet<String> relationtypes = new HashSet<>(); // to note the relation types we've seen
        String stemmata = ""; // holds Stemmatas for this GraphMl

        // Some state variables
        Node currentNode = null;        // holds the current
        String currentGraph = null;     // holds the ID of the current XML graph element
        RelationModel currentRelModel = null;
        String edgeWitness = null;
        String witnessClass = "witnesses";

        try (Transaction tx = db.beginTx()) {
            tradId = traditionNode.getProperty("id").toString();
            outer:
            while (true) {
                // START READING THE GRAPHML FILE
                int event = reader.next(); // gets the next <element>

                switch (event) {
                    case XMLStreamConstants.END_ELEMENT:
                        switch (reader.getLocalName()) {
                            case "graph":
                                // Clear out the currentGraph string.
                                currentGraph = null;
                                break;
                            case "node":
                                // Finished working on currentNode
                                currentNode = null;
                                break;
                            case "edge":
                                assert currentRelModel != null;
                                Node from = db.getNodeById(idToNeo4jId.get(currentRelModel.getSource()));
                                Node to = db.getNodeById(idToNeo4jId.get(currentRelModel.getTarget()));

                                ERelations relKind = (currentRelModel.getType() != null) ?
                                        ERelations.RELATED : ERelations.SEQUENCE;
                                Relationship relship = null;
                                // Sequence relationships are specified multiple times in the GraphML, once
                                // per witness. Reading relationships should be specified only once.
                                if (from.hasRelationship(relKind, Direction.BOTH)) {
                                    for (Relationship qr : from.getRelationships(relKind, Direction.BOTH)) {
                                        if (qr.getStartNode().equals(to) || qr.getEndNode().equals(to)) {
                                            // If a RELATED link already exists, we have a problem.
                                            if (relKind.equals(ERelations.RELATED))
                                                return Response.status(Response.Status.BAD_REQUEST)
                                                        .entity("Error: Tradition specifies the reading relation " +
                                                                currentRelModel.getScope() + " -- " + currentRelModel.getTarget() +
                                                                "twice")
                                                        .build();
                                            // It's a SEQUENCE link, so we are good.
                                            relship = qr;
                                            break;
                                        }
                                    }
                                }
                                // If not, create it.
                                if (relship == null)
                                    relship = from.createRelationshipTo(to, relKind);

                                if (relKind.equals(ERelations.RELATED)) {
                                    // Set the n4j relationship properties
                                    if (currentRelModel.getType() != null) {
                                        String typeName = currentRelModel.getType();
                                        relship.setProperty("type", typeName);
                                        // Make sure this relationship type exists
                                        if (!relationtypes.contains(typeName)) {
                                            Response rtResult = new RelationType(tradId, typeName).makeDefaultType();
                                            if (rtResult.getStatus() == Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())
                                                return rtResult;
                                            else relationtypes.add(typeName);
                                        }
                                    }
                                    if (currentRelModel.getA_derivable_from_b() != null)
                                        relship.setProperty("a_derivable_from_b", currentRelModel.getA_derivable_from_b());
                                    if (currentRelModel.getAlters_meaning() != null)
                                        relship.setProperty("alters_meaning", currentRelModel.getAlters_meaning());
                                    if (currentRelModel.getB_derivable_from_a() != null)
                                        relship.setProperty("b_derivable_from_a", currentRelModel.getB_derivable_from_a());
                                    if (currentRelModel.getDisplayform() != null)
                                        relship.setProperty("displayform", currentRelModel.getDisplayform());
                                    if (currentRelModel.getIs_significant() != null)
                                        relship.setProperty("is_significant", currentRelModel.getIs_significant());
                                    if (currentRelModel.getNon_independent() != null)
                                        relship.setProperty("non_independent", currentRelModel.getNon_independent());
                                    if (currentRelModel.getScope() != null)
                                        relship.setProperty("scope", currentRelModel.getScope());
                                } else {
                                    // If this is an edge relationship, record the witness information
                                    // either in "witnesses" or in the field indicated by "extra"

                                    String[] witList = {};
                                    if (relship.hasProperty(witnessClass))
                                        witList = (String[]) relship.getProperty(witnessClass);
                                    ArrayList<String> currentWits = new ArrayList<>(Arrays.asList(witList));
                                    currentWits.add(edgeWitness);
                                    relship.setProperty(witnessClass, currentWits.toArray(new String[0]));
                                }
                                // Finished working on currentRel
                                witnessClass = "witnesses";
                                currentRelModel = null;
                                break;
                        }
                        break;
                    case XMLStreamConstants.END_DOCUMENT:
                        reader.close();
                        break outer;
                    case XMLStreamConstants.START_ELEMENT:
                        String local_name = reader.getLocalName();
                        switch (local_name) {
                            case "data":
                                if (currentRelModel != null) {
                                    // We are working on a relation node. Apply the data.
                                    String attr = keymap.get(reader.getAttributeValue("", "key"));
                                    String keytype = keytypes.get(attr);
                                    String val = reader.getElementText();
                                    switch (attr) {
                                        case "id":
                                            break;
                                        case "a_derivable_from_b":
                                            if (val.equals("1"))
                                                currentRelModel.setA_derivable_from_b(true);
                                            else
                                                currentRelModel.setA_derivable_from_b(false);
                                            break;
                                        case "alters_meaning":
                                            currentRelModel.setAlters_meaning(Long.parseLong(val));
                                            break;
                                        case "annotation":
                                            currentRelModel.setAnnotation(val);
                                            break;
                                        case "b_derivable_from_a":
                                            if (val.equals("1"))
                                                currentRelModel.setB_derivable_from_a(true);
                                            else
                                                currentRelModel.setB_derivable_from_a(false);
                                            break;
                                        case "displayform":
                                            currentRelModel.setDisplayform(val);
                                            break;
                                        case "extra":
  //                                          assert currentRel.isType(ERelations.SEQUENCE);
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
                                            currentRelModel.setType(val);
                                            break;
                                        case "is_significant":
                                            currentRelModel.setIs_significant(val);
                                            break;
                                        case "non_independent":
                                            if (val.equals("1"))
                                                currentRelModel.setNon_independent(true);
                                            else
                                                currentRelModel.setNon_independent(false);
                                            break;
                                        case "scope":
                                            currentRelModel.setScope(val);
                                            break;
                                        case "type":
                                            currentRelModel.setType(val);
                                            break;
                                        case "witness":
                                            edgeWitness = val;
                                            // Store the existence of this witness
                                            witnesses.put(val, true);
                                            break;
                                        default:
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
                                            // I don't think this condition is ever met, and not sure why a label property would be set
                                            // if (currentNode.hasProperty("label") && currentNode.getProperty("label").equals("TRADITION"))
                                            //     break;
                                            if (currentNode.hasProperty("name")
                                                    && ((String)currentNode.getProperty("name")).length() == 0
                                                    && text.length() > 0) {
                                                currentNode.setProperty(attr, text);
                                            }
                                            break;
                                        case "stemmata":
                                            stemmata = text;
                                            break;
                                        case "user": // We ignore the old GraphML user IDs
                                            break;

                                        // Reading node attributes
                                        case "id":  // We don't use the old reading IDs
                                            break;
                                        case "is_start":
                                            if(text.equals("1") || text.equals("true"))
                                                parentNode.createRelationshipTo(currentNode, ERelations.COLLATION);
                                            setTypedProperty(currentNode, attr, keytype, text);
                                            break;
                                        case "is_end":
                                            if (text.equals("1") || text.equals("true"))
                                                parentNode.createRelationshipTo(currentNode, ERelations.HAS_END);
                                            setTypedProperty(currentNode, attr, keytype, text);
                                            break;
                                        case "public": // This is overridden in the upload API
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
                                currentRelModel = new RelationModel();
                                currentRelModel.setSource(reader.getAttributeValue("", "source"));
                                currentRelModel.setTarget(reader.getAttributeValue("", "target"));
                                currentRelModel.setA_derivable_from_b(null);
                                currentRelModel.setAlters_meaning(null);
                                currentRelModel.setB_derivable_from_a(null);
                                currentRelModel.setNon_independent(null);
/*
                                String sourceName = reader.getAttributeValue("", "source");
                                String targetName = reader.getAttributeValue("", "target");
                                Node from = db.getNodeById(idToNeo4jId.get(sourceName));
                                Node to = db.getNodeById(idToNeo4jId.get(targetName));
                                ERelations relKind = (currentGraph.equals("relationships")) ?
                                        ERelations.RELATED : ERelations.SEQUENCE;
                                // Sequence relationships are specified multiple times in the graph, once
                                // per witness. Reading relationships should be specified only once.
                                if (from.hasRelationship(relKind, Direction.BOTH)) {
                                    for (Relationship qr : from.getRelations(relKind, Direction.BOTH)) {
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
*/
                                break;
                            case "node":
                                assert(currentGraph != null);
                                if (!currentGraph.equals("relationships")) {
                                    // only store nodes for the sequence graph
                                    currentNode = db.createNode(Nodes.READING);
                                    currentNode.setProperty("section_id", parentNode.getId());
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

            // Re-rank the entire tradition
            Node sectionStart = DatabaseService.getStartNode(String.valueOf(parentNode.getId()), db);
            ReadingService.recalculateRank(sectionStart, true);

            // Create the witness nodes.
            witnesses.keySet().forEach(x -> Util.findOrCreateExtant(traditionNode, x));
            // Set colocation information on relation types
            Util.setColocationFlags(traditionNode);
            tx.success();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
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
                .entity(String.format("{\"parentId\":\"%d\"}", parentNode.getId()))
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
}