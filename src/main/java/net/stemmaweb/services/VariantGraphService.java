package net.stemmaweb.services;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.*;

import java.util.ArrayList;

public class VariantGraphService {

    /**
     * Check whether a given section actually belongs to the given tradition.
     *
     * @param tradId - The alleged parent tradition
     * @param aSectionId - The section to check
     * @param db - the GraphDatabaseService where the tradition is stored
     * @return - true or false
     */
    public static Boolean sectionInTradition(String tradId, String aSectionId, GraphDatabaseService db) {
        Node traditionNode = getTraditionNode(tradId, db);
        if (traditionNode == null)
            return false;

        boolean found = false;
        try (Transaction tx = db.beginTx()) {
            for (Node s : DatabaseService.getRelated(traditionNode, ERelations.PART)) {
                if (s.getId() == Long.valueOf(aSectionId)) {
                    found = true;
                }
            }
            tx.success();
        }
        return found;
    }

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
        long nodeIndex;
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
     * Return the list of a tradition's sections, ordered by NEXT relationship
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

    private static Traverser returnTraverser (Node startNode, Evaluator ev, PathExpander ex) {
        Traverser tv;
        GraphDatabaseService db = startNode.getGraphDatabase();
        try (Transaction tx = db.beginTx()) {
            tv = db.traversalDescription()
                    .depthFirst()
                    .expand(ex)
                    .evaluator(ev)
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
        return returnTraverser(traditionNode, traditionCrawler, PathExpanders.forDirection(Direction.OUTGOING));
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
        return returnTraverser(traditionNode, traditionRelations, PathExpanders.allTypesAndDirections());
    }

    public static Traverser returnTraditionSection(Node sectionNode) {
        return returnTraverser(sectionNode, sectionCrawler, PathExpanders.forDirection(Direction.OUTGOING));
    }
}
