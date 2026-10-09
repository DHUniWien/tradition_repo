package net.stemmaweb.rest;

import static net.stemmaweb.Util.jsonerror;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import net.stemmaweb.model.AnnotationLabelModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.VariantGraphService;

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
    private final String ref;

    AnnotationLabel(String tradId, String requestedName) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        this.tradId = tradId;
        // The ref might be the annotation label's numeric id, or it might be its name.
        this.ref = requestedName;
    }

    /**
     * Gets the information for the given annotation type name.
     *
     * @title Get annotation label spec
     * @return A JSON AnnotationLabelModel or a JSON error message
     * @statuscode 200 on success
     * @statuscode 404 if the annotation label doesn't exist
     * @statuscode 500 on failure, with an error report in JSON format
     */
    @GET
    @Produces("application/json; charset=utf-8")
    @Operation(
            tags = {"Annotation Label"},
            summary = "Get annotation label spec",
            description = "Retrieves the specification for the given annotation type name.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Success", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AnnotationLabelModel.class))),
                    @ApiResponse(responseCode = "400", description = "if the annotation label reference is a name shared by multiple labels (legacy data only)", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "404", description = "Annotation label not found", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "500", description = "Failure, with an error report in JSON format", content = @Content(mediaType = "application/json"))
            }
    )
    public Response getAnnotationLabel() {
    	Response response;
        try (Transaction tx = db.beginTx()) {
        	Node ourNode = lookupAnnotationLabel(tx);
        	if (ourNode == null) {
        		response = Response.status(Response.Status.NOT_FOUND).build();
        	} else {
        	    response = Response.ok(new AnnotationLabelModel(ourNode)).build();
        	}
        } catch (NotFoundException e) {
        	response = Response.status(Response.Status.NOT_FOUND).build();
        } catch (IllegalArgumentException e) {
        	response = Response.status(Response.Status.BAD_REQUEST).entity(jsonerror(e.getMessage())).build();
        } catch (Exception e) {
        	e.printStackTrace();
        	response = Response.serverError().entity(jsonerror(e.getMessage())).build();
        }

        return response;
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
     * @statuscode 409 if the requested name is already in use, or if a rename was requested for
     *             a label that is still in use by an annotation or another label's links
     * @statuscode 500 on failure, with an error report in JSON format
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("application/json; charset=utf-8")
    @Operation(
            tags = {"Annotation Label"},
            summary = "Put annotation label spec",
            description = "Creates or updates an annotation type specification.",
            requestBody = @RequestBody(
                    description = "The AnnotationLabelModel specification to use",
                    required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AnnotationLabelModel.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Updated existing label", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AnnotationLabelModel.class))),
                    @ApiResponse(responseCode = "201", description = "Created new label", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AnnotationLabelModel.class))),
                    @ApiResponse(responseCode = "400", description = "Error in the annotation type specification", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "400", description = "if the annotation label reference is a name shared by multiple labels (legacy data only)", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "409", description = "Requested name is already in use, or a rename was requested for a label that is still in use by an annotation or another label's links", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "500", description = "Failure, with an error report in JSON format", content = @Content(mediaType = "application/json"))
            }
    )
    public Response createOrUpdateAnnotationLabel(AnnotationLabelModel alm) {
        boolean isNew = false;
        try (Transaction tx = db.beginTx()) {
        	Node ourNode = lookupAnnotationLabel(tx);
        	Node tradNode = VariantGraphService.getTraditionNode(tx, tradId);
            // Get the existing list of annotation labels associated with this tradition
            List<String> reservedWords = Arrays.asList("USER", "ROOT", "__SYSTEM__");
            List<String> existingLabels = getValidTargetsForTradition(tx, reservedWords);
            boolean specHasProperties = alm.getProperties() != null && !alm.getProperties().isEmpty();
            boolean specHasLinks = alm.getLinks() != null && !alm.getLinks().isEmpty();

            if (ourNode == null) {
                isNew = true;
                // Sanity check - the name in the request needs to match the name in the URL.
                if (!alm.getName().equals(ref))
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(jsonerror("Name mismatch in annotation label creation request")).build();
                // Reject names that look like object IDs
                if (DatabaseService.nameIsNumeric(alm.getName()))
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(jsonerror("Annotation label name may not be numeric: " + alm.getName())).build();
                // The label can't already exist in the NODES enum
                if (existingLabels.contains(alm.getName()) || reservedWords.contains(alm.getName()))
                    return Response.status(Response.Status.CONFLICT)
                            .entity(jsonerror("Requested label " + alm.getName() + " already in use")).build();
                // We need to be specifying at least one link.
                if (!specHasLinks)
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(jsonerror("Annotation label must have at least one link specified")).build();
                // Create the label; we will add properties and links below.
                ourNode = DatabaseService.createNode(tx, Nodes.ANNOTATIONLABEL);
                tradNode.createRelationshipTo(ourNode, ERelations.HAS_ANNOTATION_TYPE);
                ourNode.setProperty("name", alm.getName());
                existingLabels.add(alm.getName());
            } else {
                // We are updating an existing label, so we should delete its existing properties and links.
                // First check to make sure that, if we have changed the name, there is not already
                // another annotation label with this name
                String currentName = ourNode.getProperty("name").toString();
                if (DatabaseService.nameIsNumeric(alm.getName()))
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(jsonerror("Annotation label name may not be numeric: " + alm.getName())).build();
                // Now deal with the name field being empty or null - in this case, pretend no name change was requested
                if (alm.getName() == null || alm.getName().isEmpty())
                    alm.setName(currentName);
                if (!alm.getName().equals(currentName)) {
                    if (existingLabels.contains(alm.getName()) || reservedWords.contains(alm.getName()))
                        return Response.status(Response.Status.CONFLICT).entity(jsonerror(
                                "Requested label name " + alm.getName() + " already in use")).build();
                    // Annotations carry their label's name as a node label, and other labels'
                    // link schemata refer to it by name, so a label in use can't be renamed.
                    // LATER consider allowing rename-only requests
                    String usage = findLabelUsage(tradNode, ourNode, currentName);
                    if (usage != null)
                        return Response.status(Response.Status.CONFLICT).entity(jsonerror(
                                "Cannot rename an annotation label that is still in use: " + usage)).build();
                }

                // Apply the (possible) rename.
                ourNode.setProperty("name", alm.getName());

                // Delete the property and/or link definitions if we are resetting them
                // LATER Sanity check that the properties / links being deleted (and not restored) aren't in use
                if (specHasProperties) {
                    Relationship p = ourNode.getSingleRelationship(ERelations.HAS_PROPERTIES, Direction.OUTGOING);
                    if (p != null) {
                        p.getEndNode().delete();
                        p.delete();
                    }
                }
                if (specHasLinks) {
                    Relationship l = ourNode.getSingleRelationship(ERelations.HAS_LINKS, Direction.OUTGOING);
                    if (l != null) {
                        l.getEndNode().delete();
                        l.delete();
                    }
                }

            }
            // Now reset the properties and links according to the model given.
            // Do we have any new properties?
            if (specHasProperties) {
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
                    // Reject the "id" property name specifically -- it is the system-assigned
                    // entity id (see the entity ID system), and a client-declared property of
                    // the same name would collide with it on every annotation of this type.
                    if (key.equals("id"))
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity(jsonerror("Property name \"id\" is reserved to the system")).build();
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
            if (specHasLinks) {
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
            AnnotationLabelModel returnedModel = new AnnotationLabelModel(ourNode);
            tx.commit();
            return Response.status(isNew ? Response.Status.CREATED : Response.Status.OK)
            		.entity(returnedModel).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(jsonerror(e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(jsonerror(e.getMessage())).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
    }

    /**
     * Deletes the specified annotation label specification from the tradition. Returns an error
     * if there are any annotations still using this type.
     *
     * @title Delete annotation label
     *
     * @statuscode 200 on success
     * @statuscode 404 if the annotation label doesn't exist
     * @statuscode 409 if the annotation label is still in use
     * @statuscode 500 on failure, with an error report in JSON format
     * @return the label model that was deleted
     */
    @DELETE
    @Operation(
            tags = {"Annotation Label"},
            summary = "Delete annotation label",
            description = "Deletes the specified annotation label specification from the tradition. Returns an error if the label is still in use.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Success", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AnnotationLabelModel.class))),
                    @ApiResponse(responseCode = "400", description = "if the annotation label reference is a name shared by multiple labels (legacy data only)", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "404", description = "Annotation label not found", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "409", description = "Annotation label is still in use", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "500", description = "Failure, with an error report in JSON format", content = @Content(mediaType = "application/json"))
            }
    )
    public Response deleteAnnotationLabel() {
    	try (Transaction tx = db.beginTx()) {
    		Node ourNode = lookupAnnotationLabel(tx);
    		if (ourNode == null) return Response.status(Response.Status.NOT_FOUND).build();
    		AnnotationLabelModel ourModel = new AnnotationLabelModel(ourNode);
    		Node tradNode = VariantGraphService.getTraditionNode(tx, tradId);
            // Check for annotations on this tradition using this label, before we delete it
            Node annoNode = findAnnotationUsing(tradNode, ourModel.getName());
            if (annoNode != null)
                return Response.status(Response.Status.CONFLICT).entity(jsonerror(
                        "Label " + ourModel.getName() + " still in use on annotation " + annoNode.getElementId()))
                        .build();

            // Delete the label's properties and links
            for (Relationship r : DatabaseService.getRelationships(ourNode, Direction.OUTGOING)) {
                r.getEndNode().delete();
                r.delete();
            }
            // Delete any reference to the label in any other label's linkset -- compare against
            // the resolved label's actual name, not the raw URL reference (which may be a numeric
            // id), since link targets are always stored by name.
            for (Node n : getExistingLabelsForTradition(tx)) {
                if (n.equals(ourNode)) continue;
                Relationship l = n.getSingleRelationship(ERelations.HAS_LINKS, Direction.OUTGOING);
                if (l != null) {
                    // Links are stored keyed by the target label's name, with the link
                    // type(s) as the value -- so it is the key we need to match.
                    Node linkNode = l.getEndNode();
                    if (linkNode.hasProperty(ourModel.getName()))
                        linkNode.removeProperty(ourModel.getName());
                }
            }
            // Finally, delete the label
            ourNode.getSingleRelationship(ERelations.HAS_ANNOTATION_TYPE, Direction.INCOMING).delete();
            ourNode.delete();
            tx.commit();
            return Response.ok(ourModel).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(jsonerror(e.getMessage())).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
    }

    /**
     * Resolves this annotation label's path-segment reference (numeric id or name) to its node,
     * among the annotation labels belonging to the tradition.
     *
     * @param tx the transaction within which we are working
     * @return the matching annotation label node, or null if no label matches the reference
     * @throws NotFoundException if no such tradition exists
     * @throws IllegalArgumentException if the reference is a name shared by 2+ labels
     *         (only reachable for legacy data created before name uniqueness was enforced)
     */
    private Node lookupAnnotationLabel(Transaction tx) {
        Node tradNode = VariantGraphService.getTraditionNode(tx, tradId);
        if (tradNode == null)
            throw new NotFoundException(String.format("No tradition found with id %s", tradId));
        List<Node> candidates = DatabaseService.getRelated(tradNode, ERelations.HAS_ANNOTATION_TYPE);
        try {
            return DatabaseService.resolveManagedRef(tx, Nodes.ANNOTATIONLABEL, candidates, ref, "name");
        } catch (NotFoundException e) {
            return null;
        }
    }

    /**
     * Finds an annotation on the tradition that uses the annotation label of the given name
     * (annotations carry their label's name as a Neo4j node label).
     *
     * @param tradNode the tradition node
     * @param labelName the annotation label's current name
     * @return the first annotation found using the label, or null if there is none
     */
    private static Node findAnnotationUsing(Node tradNode, String labelName) {
        Label asLabel = Label.label(labelName);
        for (Node annoNode : DatabaseService.getRelated(tradNode, ERelations.HAS_ANNOTATION))
            if (annoNode.hasLabel(asLabel))
                return annoNode;
        return null;
    }

    /**
     * Reports whether the annotation label of the given name is in use, either by an
     * annotation on the tradition, or as a link target in another annotation label's link
     * schema (stored keyed by the target label's name).
     *
     * @param tradNode the tradition node
     * @param labelNode the annotation label node itself, whose own links are not counted
     * @param labelName the annotation label's current name
     * @return a description of the first use found, or null if the label is unused
     */
    private static String findLabelUsage(Node tradNode, Node labelNode, String labelName) {
        Node annoNode = findAnnotationUsing(tradNode, labelName);
        if (annoNode != null)
            return "used by annotation " + annoNode.getProperty("id", annoNode.getElementId());
        for (Node other : DatabaseService.getRelated(tradNode, ERelations.HAS_ANNOTATION_TYPE)) {
            if (other.equals(labelNode)) continue;
            Relationship l = other.getSingleRelationship(ERelations.HAS_LINKS, Direction.OUTGOING);
            if (l != null && l.getEndNode().hasProperty(labelName))
                return "link target of annotation label " + other.getProperty("name");
        }
        return null;
    }

    private List<Node> getExistingLabelsForTradition(Transaction tx) {
        Node tradNode = VariantGraphService.getTraditionNode(tx, tradId);
        List<Node> answer;
        answer = DatabaseService.getRelated(tradNode, ERelations.HAS_ANNOTATION_TYPE);
        return answer;
    }

    private List<String> getValidTargetsForTradition(Transaction tx, List<String> reservedWords) {
        List<String> answer;
        // Get the existing labels
        answer = getExistingLabelsForTradition(tx).stream()
                .map(x -> x.getProperty("name").toString()).collect(Collectors.toList());
        // Get the primary objects that can also be annotated
        for (Nodes x : Nodes.values()) {
            if (!reservedWords.contains(x.name()))
                answer.add(x.name());
        }
        return answer;
    }
}
