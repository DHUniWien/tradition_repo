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
     * @param nodeId the ID of the tradition or section whose start node should be returned
     * @param db  the GraphDatabaseService where the tradition is stored
     * @return  the start node, or null if there is none.
     *      NOTE if there are multiple unordered sections, an arbitrary start node may be returned!
     */
    public static Node getStartNode(String nodeId, GraphDatabaseService db) {
        return getBoundaryNode(nodeId, db, ERelations.COLLATION);
    }

    /**
     *
     * @param nodeId the ID of the tradition or section whose end node should be returned
     * @param db  the GraphDatabaseService where the tradition is stored
     * @return  the end node, or null if there is none
     *      NOTE if there are multiple unordered sections, an arbitrary end node may be returned!
     */
    public static Node getEndNode(String nodeId, GraphDatabaseService db) {
        return getBoundaryNode(nodeId, db, ERelations.HAS_END);
    }

    private static Node getBoundaryNode(String nodeId, GraphDatabaseService db, ERelations direction) {
        Node boundNode = null;
        // If we have been asked for a tradition node, use the first of its section nodes instead.
        Node currentNode = getTraditionNode(nodeId, db);
        if (currentNode != null) {
            ArrayList<Node> sections = getSectionNodes(nodeId, db);
            if (sections != null && sections.size() > 0)
                return getBoundaryNode(String.valueOf(sections.get(0).getId()), db, direction);
            else
                return null;
        }
        // Were we asked for a nonexistent tradition node (i.e. a non-Long that corresponds to no tradition)?
        Long nodeIndex;
        try {
            nodeIndex = Long.valueOf(nodeId);
        } catch (NumberFormatException e) {
            return null;
        }
        // If we are here, we were asked for a section node.
        try (Transaction tx = db.beginTx()) {
            currentNode = db.getNodeById(nodeIndex);
            if (currentNode != null)
                boundNode = currentNode.getSingleRelationship(direction, Direction.OUTGOING).getEndNode();
            tx.success();
        }
        return boundNode;
    }

    /**
     *
     * @param tradId    the tradition whose sections to return
     * @param db        the GraphDatabaseService where the tradition is stored
     * @return          a list of sections, or null if the tradition doesn't exist
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
                            .forEach(sectionNodes::add);
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
     *            the string ID of the tradition we're hunting
     * @param db
     *            the GraphDatabaseService where the tradition is stored
     * @return
     *            the relevant tradition node
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
     * @param section
     *            the section node whose tradition we're hunting
     * @param db
     *            the GraphDatabaseService where the tradition is stored
     * @return
     *            the relevant tradition node
     */
    public static Node getTraditionNode(Node section, GraphDatabaseService db) {
        Node tradition;
        try (Transaction tx = db.beginTx()) {
            tradition = section.getSingleRelationship(ERelations.PART, Direction.INCOMING).getStartNode();
            tx.success();
        }
        return tradition;
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
     * @param startNode - the node at one end of the relationship
     * @param relType - the relationship type to follow
     * @return ArrayList<Node> all nodes related to startNode by the given relationship
     */
    public static ArrayList<Node> getRelated (Node startNode, RelationshipType relType) {
        ArrayList<Node> result = new ArrayList<>();
        GraphDatabaseService db = startNode.getGraphDatabase();
        try (Transaction tx = db.beginTx()) {
            Iterator<Relationship> allRels = startNode.getRelationships(relType).iterator();
            allRels.forEachRemaining(x -> result.add(x.getOtherNode(startNode)));
            tx.success();
        }
        return result;
    }


    /**
     * This method can be used to determine whether a user with given Id exists
     * in the DB
     *
     * @param userId  the user whose existence to check
     * @param db      the DB in which to check
     * @return        boolean
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
