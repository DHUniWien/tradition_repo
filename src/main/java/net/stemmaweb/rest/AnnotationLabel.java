package net.stemmaweb.rest;

import com.qmino.miredot.annotations.ReturnType;
import net.stemmaweb.model.AnnotationLabelModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.VariantGraphService;
import org.neo4j.graphdb.*;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static net.stemmaweb.Util.jsonerror;

/**
 * Comprises the API calls having to do with specifying the annotation types that are allowed on
 * the given tradition. See {@link net.stemmaweb.model.AnnotationLabelModel AnnotationLabelModel} for
 * more information on how these types are specified.
 *
 * @author tla
 */

public class AnnotationLabel {
    private final GraphDatabaseService db;
    private final String tradId;
    private final String name;

    AnnotationLabel(String tradId, String name) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        this.tradId = tradId;
        this.name = name;
    }

    /**
     * Gets the information for the given annotation type name.
     *
     * @title Get annotation label spec
     * @return A JSON AnnotationLabelModel or a JSON error message
     * @statuscode 200 on success
     * @statuscode 400 if there is an error in the annotation type specification
     * @statuscode 500 on failure, with an error report in JSON format
     */
    @GET
    @Produces("application/json; charset=utf-8")
    @ReturnType(clazz = AnnotationLabelModel.class)
    public Response getAnnotationLabel() {
        Node ourNode = lookupAnnotationLabel();
        if (ourNode == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(new AnnotationLabelModel(ourNode)).build();
    }

    /**
     * Creates or updates an annotation type specification
     *
     * @title Put annotation label spec
     * @param alm - The AnnotationLabelModel specification to use
     * @return The AnnotationLabelModel specification created / updated
     * @statuscode 200 on update of existing label
     * @statuscode 201 on creation of new label
     * @statuscode 400 if there is an error in the annotation type specification
     * @statuscode 409 if the requested name is already in use
     * @statuscode 500 on failure, with an error report in JSON format
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("application/json; charset=utf-8")
    @ReturnType(clazz = AnnotationLabelModel.class)
    public Response createOrUpdateAnnotationLabel(AnnotationLabelModel alm) {
        Node ourNode = lookupAnnotationLabel();
        Node tradNode = VariantGraphService.getTraditionNode(tradId, db);
        boolean isNew = false;
        try (Transaction tx = db.beginTx()) {
            // Get the existing list of annotation labels associated with this tradition
            List<String> reservedWords = Arrays.asList("USER", "ROOT", "__SYSTEM__");
            List<String> existingLabels = getValidTargetsForTradition(reservedWords);

            if (ourNode == null) {
                isNew = true;
                // Sanity check - the name in the request needs to match the name in the URL.
                if (!alm.getName().equals(name))
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(jsonerror("Name mismatch in annotation label creation request")).build();
                // The label can't already exist in the NODES enum
                if (existingLabels.contains(alm.getName()) || reservedWords.contains(alm.getName()))
                    return Response.status(Response.Status.CONFLICT)
                            .entity(jsonerror("Requested label " + alm.getName() + " already in use")).build();
                // Create the label and its properties and links
                ourNode = tx.createNode(Nodes.ANNOTATIONLABEL);
                tradNode.createRelationshipTo(ourNode, ERelations.HAS_ANNOTATION_TYPE);
                ourNode.setProperty("name", alm.getName());
                existingLabels.add(alm.getName());
            } else {
                // We are updating an existing label, so we should delete its existing properties and links.
                // First check to make sure that, if we have changed the name, there is not already
                // another annotation label with this name
                if (!alm.getName().equals(name) && (existingLabels.contains(alm.getName()) || reservedWords.contains(alm.getName())))
                    return Response.status(Response.Status.CONFLICT).entity(jsonerror(
                            "Requested label name " + alm.getName() + " already in use")).build();

                // LATER Sanity check that the properties / links being deleted (and not restored) aren't in use
                Relationship p = ourNode.getSingleRelationship(ERelations.HAS_PROPERTIES, Direction.OUTGOING);
                if (p != null) {
                    p.getEndNode().delete();
                    p.delete();
                }
                Relationship l = ourNode.getSingleRelationship(ERelations.HAS_LINKS, Direction.OUTGOING);
                if (l != null) {
                    l.getEndNode().delete();
                    l.delete();
                }

            }
            // Now reset the properties and links according to the model given.
            // Do we have any new properties?
            if (!alm.getProperties().isEmpty()) {
                Node pnode = tx.createNode(Nodes.PROPERTIES);
                ourNode.createRelationshipTo(pnode, ERelations.HAS_PROPERTIES);
                ArrayList<String> allowedValues = new ArrayList<>(Arrays.asList("Boolean", "Long", "Double",
                        "Character", "String", "LocalDate", "OffsetTime", "LocalTime", "ZonedDateTime",
                        "LocalDateTime", "Duration", "Period"));
                for (String key : alm.getProperties().keySet()) {
                    // Reject any property names with a reserved prefix
                    if (key.startsWith("__"))
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity(jsonerror("Property names with prefix __ are reserved to the system")).build();
                    // Validate the value - it needs to be a data type allowed by Neo4J.
                    String val = alm.getProperties().get(key);
                    if (allowedValues.contains(val) ||
                            allowedValues.contains(val.replace("[]", "")))
                        pnode.setProperty(key, val);
                    else
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity(jsonerror("Data type " + val + " not allowed as a Neo4J property")).build();
                }
            }
            // Do we have any links?
            if (!alm.getLinks().isEmpty()) {
                Node lnode = tx.createNode(Nodes.LINKS);
                ourNode.createRelationshipTo(lnode, ERelations.HAS_LINKS);
                for (String key : alm.getLinks().keySet()) {
                    // Validate the value - the node label that is specified as the target for this link
                    // has to exist, either as another annotation label or as a primary node.
                    if (existingLabels.contains(key)) lnode.setProperty(key, alm.getLinks().get(key));
                    else return Response.status(Response.Status.BAD_REQUEST).entity(jsonerror(
                            "Linked node label " + key + " not found in this tradition")).build();
                }
            }
            tx.close();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.status(isNew ? Response.Status.CREATED : Response.Status.OK)
                .entity(new AnnotationLabelModel(ourNode)).build();
    }

    /**
     * Deletes the specified annotation label specification from the tradition. Returns an error
     * if there are any annotations still using this type.
     *
     * @title Delete annotation label
     *
     * @statuscode 200 on success
     * @statuscode 409 if the annotation label is still in use
     * @statuscode 500 on failure, with an error report in JSON format
     * @return a Response indicating the outcome of the request
     */
    @DELETE
    @ReturnType("java.lang.Void")
    public Response deleteAnnotationLabel() {
        Node ourNode = lookupAnnotationLabel();
        if (ourNode == null) return Response.status(Response.Status.NOT_FOUND).build();
        AnnotationLabelModel ourModel = new AnnotationLabelModel(ourNode);
        Node tradNode = VariantGraphService.getTraditionNode(tradId, db);
        try (Transaction tx = db.beginTx()) {
            // Check for annotations on this tradition using this label, before we delete it
            for (Node annoNode : DatabaseService.getRelated(tradNode, ERelations.HAS_ANNOTATION))
                if (annoNode.hasLabel(Label.label(ourModel.getName())))
                    return Response.status(Response.Status.CONFLICT).entity(jsonerror(
                            "Label " + ourModel.getName() + " still in use on annotation " + annoNode.getElementId()))
                            .build();

            // Delete the label's properties and links
            for (Relationship r : ourNode.getRelationships(Direction.OUTGOING)) {
                r.getEndNode().delete();
                r.delete();
            }
            // Delete any reference to the label in any other label's linkset
            for (Node n : getExistingLabelsForTradition()) {
                if (n.equals(ourNode)) continue;
                Relationship l = n.getSingleRelationship(ERelations.HAS_LINKS, Direction.OUTGOING);
                if (l != null) {
                    for (String lname : l.getEndNode().getPropertyKeys()) {
                        if (l.getEndNode().getProperty(lname).toString().equals(name))
                            l.getEndNode().removeProperty(lname);
                    }
                }
            }
            // Finally, delete the label
            ourNode.getSingleRelationship(ERelations.HAS_ANNOTATION_TYPE, Direction.INCOMING).delete();
            ourNode.delete();
            tx.close();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok().build();
    }

    private Node lookupAnnotationLabel() {
        Node ourNode = null;
        try (Transaction tx = db.beginTx()) {
            Node tradNode = VariantGraphService.getTraditionNode(tradId, db);
            Optional<Node> foundNode = DatabaseService.getRelated(tradNode, ERelations.HAS_ANNOTATION_TYPE)
                    .stream().filter(x -> x.getProperty("name", "").equals(name)).findFirst();
            if (foundNode.isPresent()) ourNode = foundNode.get();
            tx.close();
        }
        return ourNode;
    }

    private List<Node> getExistingLabelsForTradition() {
        Node tradNode = VariantGraphService.getTraditionNode(tradId, db);
        List<Node> answer;
        try (Transaction tx = db.beginTx()) {
            answer = DatabaseService.getRelated(tradNode, ERelations.HAS_ANNOTATION_TYPE);
            tx.close();
            return answer;
        }
    }

    private List<String> getValidTargetsForTradition(List<String> reservedWords) {
        List<String> answer;
        // Get the existing labels
        try (Transaction tx = db.beginTx()) {
            answer = getExistingLabelsForTradition().stream()
                    .map(x -> x.getProperty("name").toString()).collect(Collectors.toList());
            tx.close();
        }
        // Get the primary objects that can also be annotated
        for (Nodes x : Nodes.values()) {
            if (!reservedWords.contains(x.name()))
                answer.add(x.name());
        }
        return answer;
    }
}
