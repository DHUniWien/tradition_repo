package net.stemmaweb.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.schema.ConstraintDefinition;
import org.neo4j.graphdb.schema.Schema;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;

/**
 * Generic helper methods for querying the graph database
 *
 * @author PSE FS 2015 Team2
 */
public class DatabaseService {

    /**
     * Maps the node labels that get an application-assigned, sequential "id"
     * property to the name of the ROOT-node counter property that feeds it.
     */
    private static final Map<Label, String> MANAGED_COUNTERS = Map.of(
            Nodes.READING, "next_reading_id",
            Nodes.SECTION, "next_section_id",
            Nodes.ANNOTATION, "next_annotation_id",
            Nodes.WITNESS, "next_witness_id",
            Nodes.STEMMA, "next_stemma_id",
            Nodes.RELATION_TYPE, "next_relationtype_id",
            Nodes.ANNOTATIONLABEL, "next_annotationlabel_id"
    );

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
     * Ensures that the node-uniqueness constraints on "id" for READING, SECTION,
     * and ANNOTATION, and the relationship-uniqueness constraint on "id" for
     * RELATED, all exist. Safe to call repeatedly (e.g. on every application
     * startup) -- a constraint that already exists is left alone rather than
     * re-created. Must be run in its own transaction, separate from any data
     * writes.
     *
     * @param tx the transaction within which we are working
     */
    public static void ensureConstraints(Transaction tx) {
        Schema schema = tx.schema();
        for (Label l : MANAGED_COUNTERS.keySet())
            ensureNodePropertyUniqueness(schema, l, "id");
        ensureRelationshipPropertyUniqueness(schema, ERelations.RELATED, "id");
    }

    private static void ensureNodePropertyUniqueness(Schema schema, Label label, String property) {
        for (ConstraintDefinition constraint : schema.getConstraints(label)) {
            for (String key : constraint.getPropertyKeys()) {
                if (key.equals(property)) return;
            }
        }
        schema.constraintFor(label).assertPropertyIsUnique(property).create();
    }

    private static void ensureRelationshipPropertyUniqueness(Schema schema, RelationshipType type, String property) {
        for (ConstraintDefinition constraint : schema.getConstraints(type)) {
            for (String key : constraint.getPropertyKeys()) {
                if (key.equals(property)) return;
            }
        }
        schema.constraintFor(type).assertPropertyIsUnique(property).create();
    }

    /**
     * Reads the named counter property off the ROOT node (default 0 if absent),
     * increments it by 1, writes the incremented value back, and returns it.
     *
     * @param tx the transaction within which we are working
     * @param counterProperty the name of the counter property on the ROOT node
     * @return the newly-incremented counter value
     */
    public static long nextId(Transaction tx, String counterProperty) {
        Node root = tx.findNode(Nodes.ROOT, "name", "Root node");
        // Acquire a write lock on the ROOT node before reading the counter, so that two
        // concurrent transactions creating entities of the same managed type cannot both
        // read the same starting value and race to the same "next" value (which would
        // otherwise surface as an uncaught ConstraintViolationException / 500 at commit
        // time for whichever transaction commits second).
        tx.acquireWriteLock(root);
        long current = root.hasProperty(counterProperty) ? (long) root.getProperty(counterProperty) : 0L;
        long next = current + 1;
        root.setProperty(counterProperty, next);
        return next;
    }

    /**
     * If the given node has one of the managed labels (READING, SECTION,
     * ANNOTATION), assigns it the next value from that label's counter as its
     * "id" property. No-op for any other label. Safe to call once, right after
     * a node's final managed label is in place, regardless of whether that was
     * at creation time or via a later addLabel.
     *
     * @param tx the transaction within which we are working
     * @param node the node to assign an id to, if managed
     */
    public static void assignIdIfManaged(Transaction tx, Node node) {
        for (Map.Entry<Label, String> entry : MANAGED_COUNTERS.entrySet()) {
            if (node.hasLabel(entry.getKey())) {
                node.setProperty("id", nextId(tx, entry.getValue()));
                return;
            }
        }
    }

    /**
     * Returns whether the given label is one of the managed labels (READING, SECTION,
     * ANNOTATION) that gets an application-assigned "id" property and uniqueness
     * constraint. Compares by label name, so this works equally for a label obtained
     * via the {@code Nodes} enum or via {@code Label.label(someString)}.
     *
     * @param label the label to check
     * @return true if the label is managed
     */
    public static boolean isManagedLabel(Label label) {
        for (Label managed : MANAGED_COUNTERS.keySet()) {
            if (managed.name().equals(label.name())) return true;
        }
        return false;
    }

    /**
     * Returns the name of the managed label (READING, SECTION, or ANNOTATION) that the
     * given node carries, or null if it carries none of them. A node is expected to carry
     * at most one of these three -- e.g. an Emendation node also carries READING (but
     * EMENDATION is not itself a managed label), and an Annotation node also carries its
     * dynamic per-tradition type label (which is not itself a managed label either) -- so
     * there is no ambiguity to resolve here.
     *
     * @param node the node to check
     * @return the managed label's name, or null
     */
    public static String managedLabelOf(Node node) {
        for (Label managed : MANAGED_COUNTERS.keySet()) {
            if (node.hasLabel(managed)) return managed.name();
        }
        return null;
    }

    /**
     * Creates a node with the given labels, assigning it an application-level
     * "id" property if one of the labels is managed (READING, SECTION,
     * ANNOTATION).
     *
     * @param tx the transaction within which we are working
     * @param labels the labels to create the node with
     * @return the newly-created node
     */
    public static Node createNode(Transaction tx, Label... labels) {
        Node node = tx.createNode(labels);
        assignIdIfManaged(tx, node);
        return node;
    }

    /**
     * Creates a RELATED relationship from one node to another, assigning it an
     * "id" property from the next_relation_id counter. Does not set any other
     * property -- callers set type/scope/etc. themselves, as today.
     *
     * @param tx the transaction within which we are working
     * @param from the node the relationship starts from
     * @param to the node the relationship points to
     * @return the newly-created relationship
     */
    // TODO generalize this to all relationship creation
    public static Relationship createRelatedRelationship(Transaction tx, Node from, Node to) {
        Relationship rel = from.createRelationshipTo(to, ERelations.RELATED);
        rel.setProperty("id", nextId(tx, "next_relation_id"));
        return rel;
    }

    /**
     * Finds the node with the given label and application-level "id" property.
     *
     * @param tx the transaction within which we are working
     * @param label the label of the node to find
     * @param idStr the "id" property value, as a string
     * @return the matching node
     * @throws NumberFormatException if idStr is not a valid long
     * @throws NotFoundException if no such node exists
     */
    public static Node findNodeOrThrow(Transaction tx, Label label, String idStr) {
        long id = Long.parseLong(idStr);
        Node node = tx.findNode(label, "id", id);
        if (node == null) {
            throw new NotFoundException(String.format("No %s found with id %s", label.name().toLowerCase(), idStr));
        }
        return node;
    }

    /**
     * Finds the RELATED relationship with the given application-level "id" property.
     *
     * @param tx the transaction within which we are working
     * @param idStr the "id" property value, as a string
     * @return the matching relationship
     * @throws NumberFormatException if idStr is not a valid long
     * @throws NotFoundException if no such relationship exists
     */
    public static Relationship findRelatedOrThrow(Transaction tx, String idStr) {
        long id = Long.parseLong(idStr);
        Relationship rel = tx.findRelationship(ERelations.RELATED, "id", id);
        if (rel == null) {
            throw new NotFoundException("No relation found with id " + idStr);
        }
        return rel;
    }

    /**
     * Resolves a REST path-segment reference to a node of the given managed label, trying
     * the application-assigned numeric "id" first and falling back to a name/sigil match
     * among the given candidates. This is the "dual addressing" lookup used by
     * Witness/Stemma/RelationType/AnnotationLabel endpoints (and any other managed label)
     * so a resource can be addressed by its stable id as well as its mutable name.
     *
     * @param tx the transaction within which we are working
     * @param label the managed label of the node to find
     * @param candidates the pool of nodes in scope -- typically all nodes of this label
     *                   belonging to a particular tradition. Both an id match and a name/sigil
     *                   match must be among these candidates.
     * @param ref the path-segment string: either the numeric id or the name/sigil
     * @param nameProperty the name of the property to match ref against when it isn't numeric
     * @return the matching node
     * @throws NotFoundException if ref is numeric and no candidate has that id (including when
     *         the id belongs to a node outside the candidates, e.g. in another tradition), or if
     *         ref is a name/sigil and no candidate has it
     * @throws AmbiguousReferenceException if ref is a name/sigil matched by 2+ candidates
     */
    public static Node resolveManagedRef(Transaction tx, Label label, List<Node> candidates, String ref, String nameProperty) {
        if (nameIsNumeric(ref)) {
            long id = Long.parseLong(ref);
            Node node = tx.findNode(label, "id", id);
            // The id lookup is database-wide; a node that exists but isn't among the given
            // candidates (e.g. belongs to a different tradition) doesn't exist in this scope.
            if (node == null || !candidates.contains(node)) {
                throw new NotFoundException(String.format("No %s found with id %s", label.name().toLowerCase(), ref));
            }
            return node;
        }
        List<Node> matches = new ArrayList<>();
        for (Node candidate : candidates) {
            if (ref.equals(candidate.getProperty(nameProperty, null))) {
                matches.add(candidate);
            }
        }
        if (matches.isEmpty()) {
            throw new NotFoundException(String.format("No %s found with %s %s", label.name().toLowerCase(), nameProperty, ref));
        }
        if (matches.size() > 1) {
            throw new AmbiguousReferenceException(String.format(
                    "Ambiguous reference: %d %s nodes found with %s %s; use the numeric id instead",
                    matches.size(), label.name().toLowerCase(), nameProperty, ref));
        }
        return matches.getFirst();
    }

    /**
     * Enforces name/sigil uniqueness among the children of a given parent node: throws if
     * a *different* child already has the candidate name, otherwise returns normally having
     * acquired a write lock on the parent node, so that the caller can safely assign the name.
     *
     * @param tx the transaction within which we are working
     * @param parentNode the parent node whose children are checked for a name collision
     * @param childRelType the relationship type connecting parentNode to its children
     * @param childLabel the label the colliding child must carry to be considered
     * @param nameProperty the name of the property to check for a collision
     * @param candidateName the name/sigil being claimed
     * @param excludeSelf a child to exclude from the collision check (e.g. the node being
     *                    renamed, which may already carry candidateName as its current
     *                    name) -- may be null, e.g. on create, when there is no such node yet
     * @throws NameConflictException if a different child of childLabel already has
     *         nameProperty == candidateName
     */
    public static void ensureNameUnique(Transaction tx, Node parentNode, RelationshipType childRelType,
                                         Label childLabel, String nameProperty, String candidateName, Node excludeSelf) {
        tx.acquireWriteLock(parentNode);
        for (Node child : getRelated(parentNode, childRelType)) {
            if (!child.hasLabel(childLabel)) continue;
            if (child.equals(excludeSelf)) continue;
            if (candidateName.equals(child.getProperty(nameProperty, null))) {
                throw new NameConflictException(String.format(
                        "A %s with %s '%s' already exists", childLabel.name().toLowerCase(), nameProperty, candidateName));
            }
        }
    }

    /**
     * Helper to check whether a string looks like a numeric id.
     *
     * @param value the string to check
     * @return true if it can parse as a number
     */
    public static boolean nameIsNumeric(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
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
