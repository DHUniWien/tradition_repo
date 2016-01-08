package net.stemmaweb.services;

import java.util.ArrayList;
import java.util.Iterator;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import org.neo4j.graphdb.*;

/**
 * Helper methods for the database
 * 
 * @author PSE FS 2015 Team2
 */
public class DatabaseService {
    /**
     *
     * @param tradId
     * @param db
     *            the GraphDatabaseService where the tradition is stored
     * @return
     */
    public static Node getStartNode(String tradId, GraphDatabaseService db) {
        Node tradition = getTraditionNode(tradId, db);
        if (tradition == null)
            return null;
        ArrayList<Node> snList = getRelated(tradition, ERelations.COLLATION);
        if (snList.size() != 1)
            return null;
        return snList.get(0);
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
