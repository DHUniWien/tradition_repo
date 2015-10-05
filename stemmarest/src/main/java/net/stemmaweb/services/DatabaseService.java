package net.stemmaweb.services;

import java.util.ArrayList;
import java.util.Iterator;
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
//		ExecutionEngine engine = new ExecutionEngine(db);
        Node startNode;

        /**
         * this query gets the "Start" node of the witness
         */
        String witnessQuery = "match (tradition:TRADITION {id:'" + tradId
                + "'})-[:COLLATION]->(w:READING) return w";

        try (Transaction tx = db.beginTx()) {

            Result result = db.execute(witnessQuery);
            Iterator<Node> nodes = result.columnAs("w");

            if (nodes.hasNext()) {
                startNode = nodes.next();
            } else {
                return null;
            }

            tx.success();
        }catch (Exception e){
            return null;
        }
        return startNode;
    }

    /**
     *
     * @param db: the GraphDatabaseService where the Database should be entered
     *
     */
    public static void createRootNode(GraphDatabaseService db) {
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (n:ROOT) return n");
            Iterator<Node> nodes = result.columnAs("n");
            if (!nodes.hasNext()) {
                Node node = db.createNode(Nodes.ROOT);
                node.setProperty("name", "Root node");
                node.setProperty("LAST_INSERTED_TRADITION_ID", "1000");
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
        Iterator<Relationship> allRels = startNode.getRelationships(relType).iterator();
        while (allRels.hasNext()) {
            result.add(allRels.next().getEndNode());
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
    public static boolean checkIfUserExists(String userId, GraphDatabaseService db) {
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (userId:USER {id:'"
                    + userId + "'}) return userId");
            Iterator<Node> nodes = result.columnAs("userId");
            if (nodes.hasNext()) {
                return true;
            }
            tx.success();
        }catch (Exception e) {
            return false;
        }
        return false;
    }
}
