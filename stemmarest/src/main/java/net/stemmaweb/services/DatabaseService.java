package net.stemmaweb.services;

import java.util.ArrayList;
import java.util.Iterator;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.Uniqueness;

/**
 * Helper methods for the database
 * 
 * @author PSE FS 2015 Team2
 */
public class DatabaseService {
    /**
     *
     * @param nodeId
     * @param db
     *            the GraphDatabaseService where the tradition is stored
     * @return
     */
    public static Node getStartNode(String nodeId, GraphDatabaseService db) {
        Node startNode = null;
        Node currentNode = getTraditionNode(nodeId, db);
        if (currentNode == null) {
            currentNode = getSectionNode(nodeId, db);
        }
        if (currentNode != null) {
            ArrayList<Node> snList = getRelated(currentNode, ERelations.COLLATION);
            try (Transaction tx = db.beginTx()) {
                if (snList.size() == 0 && currentNode.hasLabel(Nodes.TRADITION)) {
                    ArrayList<Node> sectionNodes = getSectionNodes(nodeId, db);
                    for (Node iterNode : sectionNodes) {
                        startNode = getStartNode(String.valueOf(iterNode.getProperty("id")), db);
                        if (startNode != null) {
                            break;
                        }
                    }
                } else if (snList.size() == 1) {
                    startNode = snList.get(0);
                }
                tx.success();
            }
        }
        return startNode;
    }

    /**
     *
     * @param nodeId
     * @param db
     *            the GraphDatabaseService where the tradition is stored
     * @return
     */
    public static Node getEndNode(String nodeId, GraphDatabaseService db) {
        Node endNode = null;
        Node currentNode = getTraditionNode(nodeId, db);
        if (currentNode == null) {
            currentNode = getSectionNode(nodeId, db);
        }
        if (currentNode != null) {
            ArrayList<Node> snList = getRelated(currentNode, ERelations.HAS_END);
            try (Transaction tx = db.beginTx()) {
                if (snList.size() == 0 && currentNode.hasLabel(Nodes.TRADITION)) {
                    ArrayList<Node> sectionNodes = getSectionNodes(nodeId, db);
                    Node tmpEndNode;
                    for (Node iterNode : sectionNodes) {
                        tmpEndNode = getEndNode(String.valueOf(iterNode.getProperty("id")), db);
                        if (tmpEndNode != null) {
                            endNode = tmpEndNode;
                        }
                    }
                } else if (snList.size() == 1) {
                    endNode = snList.get(0);
                }
                tx.success();
            }
        }
        return endNode;
    }

    /**
     *
     * @param tradId
     * @param db        the GraphDatabaseService where the tradition is stored
     * @return
     */
    public static ArrayList<Node> getSectionNodes(String tradId, GraphDatabaseService db) {
        Node tradition = getTraditionNode(tradId, db);
        if (tradition == null)
            return null;
        ArrayList<Node> sectionNodes = new ArrayList<>();
        ArrayList<Node> sections = DatabaseService.getRelated(tradition, ERelations.PART);
        int size = sections.size();
        try (Transaction tx = db.beginTx()) {
            for(Node n: sections) {
                if (!n.getRelationships(Direction.INCOMING, ERelations.NEXT)
                        .iterator()
                        .hasNext()) {
                    db.traversalDescription()
                            .depthFirst()
                            .relationships(ERelations.NEXT, Direction.OUTGOING)
                            .evaluator(Evaluators.toDepth(size))
                            .uniqueness(Uniqueness.NODE_GLOBAL)
                            .traverse(n)
                            .nodes()
                            .forEach(r -> sectionNodes.add(r));
                    break;
                }
            }
            tx.success();
        }
        return sectionNodes;
    }

    /**
     *
     * @param tradId
     * @param db
     *            the GraphDatabaseService where the tradition is stored
     * @return
     */
    public static Node getTraditionNode(String tradId, GraphDatabaseService db) {
        Node tradition;
        try (Transaction tx = db.beginTx()) {
            tradition = db.findNode(Nodes.TRADITION, "id", tradId);
            tx.success();
        }
        return tradition;
    }

    /**
     *
     * @param sectId
     * @param db
     *            the GraphDatabaseService where the tradition is stored
     * @return
     */
    public static Node getSectionNode(String sectId, GraphDatabaseService db) {
        Node section;
        try (Transaction tx = db.beginTx()) {
            section = db.findNode(Nodes.SECTION, "id", sectId);
            tx.success();
        }
        return section;
    }

    /**
     *
     * @param db: the GraphDatabaseService where the Database should be entered
     *
     */
    public static void createRootNode(GraphDatabaseService db) {
        try (Transaction tx = db.beginTx()) {
            Node result = db.findNode(Nodes.ROOT, "name", "Root node");
            if (result == null) {
                Node node = db.createNode(Nodes.ROOT);
                node.setProperty("name", "Root node");
            }
            tx.success();
        }
    }

    /**
     * This method can be used to get the list of nodes connected to a given
     * node via a given relation.
     *
     * @param startNode
     * @param relType
     * @return ArrayList<Node> result
     */
    public static ArrayList<Node> getRelated (Node startNode, RelationshipType relType) {
        ArrayList<Node> result = new ArrayList<>();
        GraphDatabaseService db = startNode.getGraphDatabase();
        try (Transaction tx = db.beginTx()) {
            Iterator<Relationship> allRels = startNode.getRelationships(relType).iterator();
            while (allRels.hasNext()) {
                result.add(allRels.next().getEndNode());
            }
            tx.success();
        }
        return result;
    }


    /**
     * This method can be used to determine whether a user with given Id exists
     * in the DB
     *
     * @param userId
     * @param db
     * @return
     */
    public static boolean userExists(String userId, GraphDatabaseService db) {
        Node extantUser;
        try (Transaction tx = db.beginTx()) {
            extantUser = db.findNode(Nodes.USER, "id", userId);
            tx.success();
        }
        return extantUser != null;
    }
}
