package net.stemmaweb.services;

import java.util.ArrayList;
import java.util.Iterator;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.*;
import org.neo4j.graphdb.traversal.Traverser;

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
        // If we have been asked for a tradition node, use either the first or the last of
        // its section nodes instead.
        Node currentNode = getTraditionNode(nodeId, db);
        if (currentNode != null) {
            ArrayList<Node> sections = getSectionNodes(nodeId, db);
            if (sections != null && sections.size() > 0) {
                Node relevantSection = direction.equals(ERelations.HAS_END)
                        ? sections.get(sections.size() - 1)
                        : sections.get(0);
                return getBoundaryNode(String.valueOf(relevantSection.getId()), db, direction);
            } else return null;
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

    /*
     *
     * @param tradId      the tradition being checked
     * @param sectionId   the section being checked
     * @param db          the GraphDatabaseService where the tradition is stored
     * @return            boolean indicating whether the section belongs to the tradition


    public static Boolean sectionInTradition(String tradId, String sectionId, GraphDatabaseService db) {
        ArrayList<Node> sectionNodes = getSectionNodes(tradId, db);
        if (sectionNodes == null) return false;
        Node sectionNode;
        try (Transaction tx = db.beginTx()) {
            sectionNode = db.getNodeById(Long.valueOf(sectionId));
            tx.success();
            if (sectionNode == null) return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return sectionNodes.contains(sectionNode);
    }
    **/

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
     * This method can be used to get the existing relationships between two nodes.
     * @param startNode - node 1
     * @param endNode   - node 2
     * @return - a list of relationships between the two, empty if none
     */
    public static ArrayList<Relationship> getRelationshipTo(Node startNode, Node endNode, RelationshipType rtype) {
        ArrayList<Relationship> found = new ArrayList<>();
        GraphDatabaseService db = startNode.getGraphDatabase();
        try (Transaction tx = db.beginTx()) {
            for (Relationship r : startNode.getRelationships(rtype, Direction.BOTH))
                if (r.getOtherNode(startNode).equals(endNode))
                    found.add(r);
            tx.success();
        }
        return found;
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


    /**
     * Tradition and section crawlers, respectively
     */

    private static Evaluator traditionCrawler = path -> {
        if (path.length() == 0)
            return Evaluation.INCLUDE_AND_CONTINUE;
        if (path.lastRelationship().getType().name().equals(ERelations.OWNS_TRADITION.toString()))
            return Evaluation.EXCLUDE_AND_PRUNE;
        return Evaluation.INCLUDE_AND_CONTINUE;
    };

    private static Evaluator sectionCrawler = path -> {
        if (path.length() == 0)
            return Evaluation.INCLUDE_AND_CONTINUE;
        String type = path.lastRelationship().getType().name();
        if (type.equals(ERelations.PART.toString()) || type.equals(ERelations.NEXT.toString()))
            return Evaluation.EXCLUDE_AND_PRUNE;
        return Evaluation.INCLUDE_AND_CONTINUE;
    };

    private static Evaluator traditionRelations = path -> {
        if (path.length() == 0)
            return Evaluation.INCLUDE_AND_CONTINUE;
        if (path.lastRelationship().getType().name().equals(ERelations.OWNS_TRADITION.toString()))
            return Evaluation.EXCLUDE_AND_PRUNE;
        if (path.lastRelationship().getType().name().equals(ERelations.RELATED.toString()))
            return Evaluation.INCLUDE_AND_CONTINUE;
        return Evaluation.EXCLUDE_AND_CONTINUE;
    };

    private static Traverser returnTraverser (Node startNode, Evaluator e) {
        Traverser tv;
        GraphDatabaseService db = startNode.getGraphDatabase();
        try (Transaction tx = db.beginTx()) {
            tv = db.traversalDescription()
                    .depthFirst()
                    .evaluator(e)
                    .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL)
                    .traverse(startNode);
            tx.success();
        }
        return tv;
    }

    @SuppressWarnings("unused")
    public static Traverser returnEntireTradition(String tradId, GraphDatabaseService db) {
        return returnEntireTradition(getTraditionNode(tradId, db));
    }

    public static Traverser returnEntireTradition(Node traditionNode) {
        return returnTraverser(traditionNode, traditionCrawler);
    }

    public static Traverser returnTraditionSection(String sectionId, GraphDatabaseService db) {
        Traverser tv;
        try (Transaction tx = db.beginTx()) {
            Node sectionNode = db.getNodeById(Long.valueOf(sectionId));
            tv = returnTraditionSection(sectionNode);
            tx.success();
        }
        return tv;
    }

    public static Traverser returnTraditionRelations(Node traditionNode) {
        return returnTraverser(traditionNode, traditionRelations);
    }

    @SuppressWarnings("WeakerAccess")
    public static Traverser returnTraditionSection(Node sectionNode) {
        return returnTraverser(sectionNode, sectionCrawler);
    }
}
