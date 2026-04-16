package net.stemmaweb.services;

import java.util.ArrayList;
import java.util.List;

import org.neo4j.graphdb.*;

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
        try (ResourceIterator<Relationship> allRels = startNode.getRelationships(relType).iterator()) {
            allRels.forEachRemaining(x -> result.add(x.getOtherNode(startNode)));
        }
        return result;
    }

    /**
     * This method can be used to get the existing relationships between two nodes.
     *
     * @param startNode - node 1
     * @param endNode   - node 2
     * @return - a list of relationships between the two, empty if none
     */
    public static ArrayList<Relationship> getRelationshipTo(Node startNode, Node endNode, RelationshipType rtype) {
        ArrayList<Relationship> found = new ArrayList<>();
        for (Relationship r : getRelationships(startNode, Direction.BOTH, rtype)) {
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
     * @param tx     the transaction within which we are working
     * @param userId the user whose existence to check
     * @return boolean
     */
    public static boolean userExists(Transaction tx, String userId) {
        Node extantUser;
        extantUser = tx.findNode(Nodes.USER, "id", userId);
        return extantUser != null;
    }

    //

    /*
     * Convenience functions for getting contents of Neo4J ResourceIterables from node.getRelationships()
     * and closing the resources. Takes the same arguments as .getRelationships()
     */
    public static List<Relationship> getRelationships(Node node) {
        try (ResourceIterable<Relationship> rels = node.getRelationships()) {
            return rels.stream().toList();
        }
    }

    public static List<Relationship> getRelationships(Node node, Direction direction) {
        try (ResourceIterable<Relationship> rels = node.getRelationships(direction)) {
            return rels.stream().toList();
        }
    }

    public static List<Relationship> getRelationships(Node node, RelationshipType... types) {
        try (ResourceIterable<Relationship> rels = node.getRelationships(types)) {
            return rels.stream().toList();
        }
    }

    public static List<Relationship> getRelationships(Node node, Direction direction, RelationshipType... types) {
        try (ResourceIterable<Relationship> rels = node.getRelationships(direction, types)) {
            return rels.stream().toList();
        }
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
