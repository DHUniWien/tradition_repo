package net.stemmaweb.services;

import java.util.ArrayList;
import java.util.Iterator;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Entity;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;

import net.stemmaweb.rest.Nodes;

/**
 * Generic helper methods for querying the graph database
 * 
 * @author PSE FS 2015 Team2
 */
public class DatabaseService {

    /**
     * Creates a root node for the entire graph.
     *
     * @param tx: the transaction within which we are working
     *
     */
    public static void createRootNode(Transaction tx) {
        Node result = tx.findNode(Nodes.ROOT, "name", "Root node");
        if (result == null) {
            Node node = tx.createNode(Nodes.ROOT);
            node.setProperty("name", "Root node");
        }
    }

    /**
     * This method can be used to get the list of nodes connected to a given
     * node via a given relation.
     *
     * @param startNode - the node at one end of the relationship
     * @param relType - the relationship type to follow
     * @return a list of all nodes related to startNode by the given relationship
     */
    public static ArrayList<Node> getRelated (Node startNode, RelationshipType relType) {
        ArrayList<Node> result = new ArrayList<>();
        Iterator<Relationship> allRels = startNode.getRelationships(relType).iterator();
        allRels.forEachRemaining(x -> result.add(x.getOtherNode(startNode)));
        return result;
    }

    /**
     * This method can be used to get the existing relationships between two nodes.
     *
     * @param startNode - node 1
     * @param endNode   - node 2
     * @return - a list of relationships between the two, empty if none
     */
    public static ArrayList<Relationship> getRelationshipTo(Node startNode, Node endNode, RelationshipType rtype, Transaction tx) {
        ArrayList<Relationship> found = new ArrayList<>();
        for (Relationship r : startNode.getRelationships(Direction.BOTH, rtype)) {
            if (r.getOtherNode(startNode).equals(endNode)) {
                found.add(r);
            }
        }

        return found;
    }


    /**
     * This method can be used to determine whether a user with given Id exists
     * in the DB
     *
     * @param userId  the user whose existence to check
     * @param tx      the transaction within which we are working
     * @return        boolean
     */
    public static boolean userExists(String userId, Transaction tx) {
        Node extantUser;
        extantUser = tx.findNode(Nodes.USER, "id", userId);
        return extantUser != null;
    }

    /**
     * This method will duplicate properties of one PropertyContainer (Node or Relationship) into another.
     *
     * @param original - the entity from which to copy
     * @param copy - the entity to which to copy
     */
    public static void copyProperties(Entity original, Entity copy) {
        for (String p : original.getPropertyKeys())
            copy.setProperty(p, original.getProperty(p));
    }

}
