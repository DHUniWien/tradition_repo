package net.stemmaweb.services;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

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

        HashMap<String, Long> idToNeo4jId = new HashMap<>();

        int depth = 0;
        // 0 root, 1 <graphml>, 2 <graph>, 3 <node>, 4 <data>
        int type_nd = 0;
        // 0 = no, 1 = edge, 2 = node
        HashMap<String, String> map = new HashMap<>();
        // to store all keys of the introduction part

        Node from = null;    // a round-trip store for the start node of a path
        Node to = null;      // a round-trip store for the end node of a path

        LinkedList<String> witnesses = new LinkedList<>();
        // a round-trip store for witness names of a single relationship
        int last_inserted_id;

        String stemmata = ""; // holds Stemmatas for this GraphMl

        try (Transaction tx = db.beginTx()) {
            reader = factory.createXMLStreamReader(xmldata);
            // retrieves the last inserted Tradition id
            String prefix = db.findNodes(Nodes.ROOT, "name", "Root node")
                    .next()
                    .getProperty("LAST_INSERTED_TRADITION_ID")
                    .toString();
            last_inserted_id = Integer.parseInt(prefix);
            last_inserted_id++;
            prefix = String.valueOf(last_inserted_id) + "_";

            Node tradRootNode = null;    // this will be the entry point of the graph

            Node currNode;	// holds the current node
            currNode = db.createNode(Nodes.TRADITION); // create the root node of tradition
            Relationship rel = null;    // holds the current relationship

            int graphNumber = 0;     // holds the current graph number

            int firstNode = 0;    // flag to get START NODE (always == n1) == 2

            label:
            while (true) {
                // START READING THE GRAPHML FILE
                int event = reader.next(); // gets the next <element>

                switch (event) {
                    case XMLStreamConstants.END_ELEMENT:
                        if (reader.getLocalName().equals("graph") ||
                                reader.getLocalName().equals("graphml") ||
                                reader.getLocalName().equals("node") ||
                                reader.getLocalName().equals("edge")) {
                            depth--;
                            type_nd = 0;
                        }
                        break;
                    case XMLStreamConstants.END_DOCUMENT:
                        reader.close();
                        break label;
                    case XMLStreamConstants.START_ELEMENT:
                        String local_name = reader.getLocalName();
                        switch (local_name) {
                            case "data":
                                switch (depth) {
                                    case 3:
                                        if (type_nd == 1 && rel != null) {    // edge
                                            String attr = reader.getAttributeValue(0);
                                            String val = reader.getElementText();

                                            if (map.get(attr) != null) {
                                                if (map.get(attr).equals("id")) {
                                                    rel.setProperty("id", prefix + val);
                                                    rel.setProperty(attr, val);
                                                } else if (map.get(attr).equals("witness")) {
                                                    witnesses.add(val);
                                                } else {
                                                    rel.setProperty(map.get(attr), val);
                                                }
                                            }
                                        } else if (type_nd == 2 && currNode != null) {
                                            String attr = reader.getAttributeValue(0);
                                            if (map.get(attr).equals("rank")) {
                                                currNode.setProperty(map.get(reader.getAttributeValue(0)),
                                                        Long.parseLong(reader.getElementText()));
                                            } else {
                                                currNode.setProperty(map.get(reader.getAttributeValue(0)),
                                                        reader.getElementText());
                                            }
                                            if (map.get(attr).equals("is_start")) {
                                                tradRootNode.createRelationshipTo(currNode, ERelations.COLLATION);
                                            }
                                            if (map.get(attr).equals("is_end")) {
                                                tradRootNode.createRelationshipTo(currNode, ERelations.HAS_END);
                                            }
                                        }
                                        break;
                                    case 2:
                                        String attr = reader.getAttributeValue(0);
                                        String text = reader.getElementText();
                                        // needs implementation of meta data here
                                        String map_attr = map.get(attr);
                                        switch (map_attr) {
                                            case "name":
                                                String tradNameToUse = text;
                                                if (!tradName.equals("")) {
                                                    tradNameToUse = tradName;
                                                }

                                                tradRootNode = currNode;
                                                currNode.setProperty("id", prefix.substring(0,
                                                        prefix.length() - 1));

                                                currNode.setProperty("name", tradNameToUse);
                                                break;
                                            case "stemmata":
                                                stemmata = text;
                                                break;
                                            default:
                                                currNode.setProperty(map.get(attr), text);
                                                break;
                                        }

                                        break;
                                }
                                break;
                            case "edge":
                                // this definitely needs refactoring!
                                String fromNodeName = prefix + reader.getAttributeValue(0);
                                String toNodeName = prefix + reader.getAttributeValue(1);
                                if (from == null || to == null) {
                                    Node fromTmp = null;
                                    if (idToNeo4jId.get(fromNodeName) != null) {
                                        fromTmp = db.getNodeById(idToNeo4jId.get(fromNodeName));
                                    }
                                    Node toTmp = db.getNodeById(idToNeo4jId.get(toNodeName));

                                    if (fromTmp != null && !(fromTmp.equals(from) && toTmp.equals(to))) {
                                        to = toTmp;
                                        from = fromTmp;
                                        if (rel != null) {
                                            //System.out.println(witnesses.toString());
                                            String[] witnessesArray = new String[witnesses.size()];
                                            witnessesArray = witnesses.toArray(witnessesArray);
                                            rel.setProperty("witnesses", witnessesArray);
                                            witnesses.clear();
                                        }
                                        ERelations relKind = (graphNumber <= 1) ?
                                                ERelations.SEQUENCE : ERelations.RELATED;
                                        rel = fromTmp.createRelationshipTo(toTmp, relKind);
                                        rel.setProperty("id", prefix + reader.getAttributeValue(2));
                                    }
                                } else if (!(from.getProperty("id").equals(fromNodeName)
                                            && to.getProperty("id").equals(toNodeName))) {
                                    Node fromTmp = db.getNodeById(idToNeo4jId.get(fromNodeName));
                                    Node toTmp = db.getNodeById(idToNeo4jId.get(toNodeName));
                                    if (!(fromTmp.equals(from) && toTmp.equals(to))) {
                                        to = toTmp;
                                        from = fromTmp;
                                        if (rel != null) {
                                            //System.out.println(witnesses.toString());
                                            String[] witnessesArray = new String[witnesses.size()];
                                            witnessesArray = witnesses.toArray(witnessesArray);
                                            if (witnessesArray.length > 0) {
                                                rel.setProperty("witnesses", witnessesArray);
                                            }
                                            witnesses.clear();
                                        }
                                        ERelations relKind = (graphNumber <= 1) ?
                                                ERelations.SEQUENCE : ERelations.RELATED;
                                        rel = fromTmp.createRelationshipTo(toTmp, relKind);
                                        rel.setProperty("id", prefix + reader.getAttributeValue(2));
                                    }
                                }

                                depth++;
                                type_nd = 1;
                                break;
                            case "node":
                                if (graphNumber <= 1) {
                                    // only store nodes for graph 1, ignore all others (unused)
                                    currNode = db.createNode(Nodes.READING);

                                    currNode.setProperty("id", prefix + reader.getAttributeValue(0));

                                    idToNeo4jId.put(prefix + reader.getAttributeValue(0),
                                            currNode.getId());

                                    if (firstNode <= 1) {
                                        firstNode++;
                                    }
                                }
                                depth++;
                                type_nd = 2;
                                break;
                            case "key":
                                String key = "";
                                String value = "";
                                QName q_attr_name = new QName("attr.name");
                                QName q_id = new QName("id");

                                for (int i = 0; i < reader.getAttributeCount(); i++) {
                                    QName reader_name = reader.getAttributeName(i);
                                    if (reader_name.equals(q_attr_name)) {
                                        value = reader.getAttributeValue(i);
                                    } else if (reader_name.equals(q_id)) {
                                        key = reader.getAttributeValue(i);
                                    }
                                }
                                map.put(key, value);
                                break;
                            case "graphml":
                                depth++;
                                break;
                            case "graph":
                                depth++;
                                graphNumber++;
                                break;
                        }
                        break;
                }
            }
            if(rel!=null) {    	// add relationship props to last relationship
                String[] witnessesArray = new String[witnesses.size()];
                witnessesArray = witnesses.toArray(witnessesArray);
                rel.setProperty("witnesses", witnessesArray);
                witnesses.clear();
            }

            Result result = db.execute("match (n:TRADITION {id:'"+ last_inserted_id
                    +"'})-[:COLLATION]->(s:READING) return s");
            Iterator<Node> nodes = result.columnAs("s");
            Node startNode = nodes.next();
            for (Node node : db.traversalDescription().breadthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .uniqueness(Uniqueness.NODE_GLOBAL)
                    .traverse(startNode)
                    .nodes()) {
                if(node.hasProperty("id")) {
                    node.removeProperty("id");
                }
                for(Relationship relation : node.getRelationships()) {
                    if(relation.hasProperty("id")) {
                        relation.removeProperty("id");
                    }
                }
            }

            Result userNodeSearch = db.execute("match (user:USER {id:'" + userId + "'}) return user");
            Node userNode = (Node) userNodeSearch.columnAs("user").next();
            userNode.createRelationshipTo(tradRootNode, ERelations.OWNS_TRADITION);

            db.findNodes(Nodes.ROOT, "name", "Root node")
                    .next()
                    .setProperty("LAST_INSERTED_TRADITION_ID",
                            prefix.substring(0, prefix.length() - 1));

            tx.success();
        } catch(Exception e) {
            e.printStackTrace();

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error: Tradition could not be imported!")
                    .build();
        }

        String[] graphs = stemmata.split("\\}");

        for(String graph : graphs) {
            DotToNeo4JParser parser = new DotToNeo4JParser(db);
            parser.parseDot(graph, last_inserted_id + "");
        }

        return Response.status(Response.Status.OK)
                .entity("{\"tradId\":" + last_inserted_id + "}")
                .build();
    }
}