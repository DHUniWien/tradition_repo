package net.stemmaweb.rest;

import static net.stemmaweb.Util.jsonerror;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;

import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.NameConflictException;
import net.stemmaweb.services.RelationService;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
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
import net.stemmaweb.model.RelationTypeModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.VariantGraphService;

/**
 * Module to handle the specification and definition of relation types that may exist on
 * this tradition.
 *
 * @author tla
 */

public class RelationType {
    /**
     * The name of a type of reading relation.
     */
    private final GraphDatabaseService db;
    private final String traditionId;
    private final String typeName;

    public RelationType(String tradId, String requestedType) {
        this.db = new GraphDatabaseServiceProvider().getDatabase();
        traditionId = tradId;
        // The typeName might be the relation type's numeric id, or it might be its name.
        typeName = requestedType;
    }

    /**
     * Fetches this tradition's node, or throws NotFoundException if no such tradition exists.
     *
     * @param tx the transaction within which we are working
     * @return the tradition node
     * @throws NotFoundException if no such tradition exists
     */
    private Node requireTraditionNode(Transaction tx) {
        Node tradNode = VariantGraphService.getTraditionNode(tx, traditionId);
        if (tradNode == null)
            throw new NotFoundException(String.format("No tradition found with id %s", traditionId));
        return tradNode;
    }

    /**
     * Resolves this relation type's path-segment reference (numeric id or name) to its node,
     * among the relation types belonging to the given tradition node.
     *
     * @param tx the transaction within which we are working
     * @param traditionNode the already-resolved tradition node
     * @return the matching relation type node
     * @throws NotFoundException if no relation type matches the reference
     * @throws IllegalArgumentException if the reference is a name shared by 2+ relation types
     *         (only reachable for legacy data created before name uniqueness was enforced)
     */
    private Node resolveRelationTypeNode(Transaction tx, Node traditionNode) {
        List<Node> candidates = DatabaseService.getRelated(traditionNode, ERelations.HAS_RELATION_TYPE);
        return DatabaseService.resolveManagedRef(tx, Nodes.RELATION_TYPE, candidates, typeName, "name");
    }

    /**
     * Gets the information for the given relation type name.
     *
     * @return A JSON RelationTypeModel or a JSON error message
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            tags = {"Relation Type"},
            summary = "Get relation type",
            description = "Gets the information for the given relation type name.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "A JSON RelationTypeModel", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RelationTypeModel.class))),
                    @ApiResponse(responseCode = "400", description = "if the relation type reference is a name shared by multiple types (legacy data only)", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
                    @ApiResponse(responseCode = "404", description = "Not found, if the relation type does not exist", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "500", description = "Failure, with an error report in JSON format", content = @Content(mediaType = "application/json"))
            }
    )
    public Response getRelationType() {
        try (Transaction tx = db.beginTx()){
            Node traditionNode = requireTraditionNode(tx);
            Node foundRelType = resolveRelationTypeNode(tx, traditionNode);
            return Response.ok(new RelationTypeModel(foundRelType)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(jsonerror(e.getMessage())).build();
        } catch (Exception e) {
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
    }

    /**
     * Creates or updates a relation type according to the specification given.
     *
     * @param rtModel - a user specification
     * @return A JSON RelationTypeModel or a JSON error message
     *             shared by multiple types (legacy data only)
     *             the new name conflicts with another existing relation type, or a rename was
     *             requested for a type that is still in use by relations
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            tags = {"Relation Type"},
            summary = "Create / update relation type specification",
            description = "Creates or updates a relation type according to the specification given. If the "
                    + "specification has no name, a new type takes the name in the URL and an existing type keeps "
                    + "its current name. A type that is in use by any relation cannot be renamed.",
            requestBody = @RequestBody(
                    description = "A user specification",
                    required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RelationTypeModel.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "A JSON RelationTypeModel (existing type was updated)", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RelationTypeModel.class))),
                    @ApiResponse(responseCode = "201", description = "A JSON RelationTypeModel (new type was created)", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RelationTypeModel.class))),
                    @ApiResponse(responseCode = "304", description = "A default type was requested, but could not be generated"),
                    @ApiResponse(responseCode = "400", description = "Bad request, if the specification is invalid, with an error report in JSON format", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "400", description = "if the relation type reference is a name shared by multiple types (legacy data only)", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
                    @ApiResponse(responseCode = "404", description = "Not found, if no such tradition exists", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "409", description = "A default type was requested, but a type of that name already exists; the new name conflicts with another existing relation type; or a rename was requested for a type that is still in use by relations", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "500", description = "Failure, with an error report in JSON format", content = @Content(mediaType = "application/json"))
            }
    )
    public Response create(RelationTypeModel rtModel) {
        // Find any existing relation type on this tradition under the given name in the URL
        try (Transaction tx = db.beginTx()){
            Node traditionNode = requireTraditionNode(tx);
            Node extantRelType;
            try {
                extantRelType = resolveRelationTypeNode(tx, traditionNode);
            } catch (NotFoundException e) {
                extantRelType = null;
            }

            // Were we asked for the secret Stemmaweb defaults?
            if (rtModel.getDefaultsettings() != null) {
                // This won't work if we also have an extant type of this name.
                if (extantRelType != null)
                    return Response.status(Response.Status.CONFLICT)
                            .entity(jsonerror("Cannot instantiate a default for a type that already exists")).build();
                RelationTypeModel defaultType = RelationService.makeDefaultType(tx, traditionNode, typeName);
                if (defaultType == null)
                    return Response.notModified().build();

                tx.commit();
                return Response.status(Response.Status.CREATED).entity(defaultType).build();
            }

            boolean isNew = extantRelType == null;
            if (isNew) {
                // A body with no name creates the type under the name in the URL.
                if (rtModel.getName() == null)
                    rtModel.setName(typeName);
            } else {
                String currentName = extantRelType.getProperty("name").toString();
                // A body with no name leaves the current name alone.
                if (rtModel.getName() == null || rtModel.getName().isEmpty())
                    rtModel.setName(currentName);
                // Relations refer to their type by name, so a type that is in use can't be
                // renamed out from under them.
                if (!rtModel.getName().equals(currentName) && isInUse(tx, traditionNode, currentName))
                    return Response.status(Response.Status.CONFLICT).entity(jsonerror(
                            "Cannot rename a relation type that is still in use; reassign its relations first.")).build();
            }
            Node resultNode = isNew
                    ? rtModel.instantiate(traditionNode, tx)
                    : rtModel.update(traditionNode, extantRelType, tx);
            if (resultNode != null) {
                RelationTypeModel result = new RelationTypeModel(resultNode);
                tx.commit();
                return Response.status(isNew ? Response.Status.CREATED : Response.Status.OK)
                        .entity(result).build();
            }
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(jsonerror(e.getMessage())).build();
        } catch (NameConflictException e) {
            return Response.status(Response.Status.CONFLICT).entity(jsonerror(e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(jsonerror(e.getMessage())).build();
        } catch (Exception e) {
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        // If we got here,
        return Response.serverError().entity(jsonerror("Could neither instantiate nor update relation type")).build();
    }

    /**
     * Deletes the named relation type.
     *
     * @return A JSON RelationTypeModel of the deleted type
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            tags = {"Relation Type"},
            summary = "Delete a relation type",
            description = "Deletes the named relation type.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "A JSON RelationTypeModel of the deleted type", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RelationTypeModel.class))),
                    @ApiResponse(responseCode = "400", description = "if the relation type reference is a name shared by multiple types (legacy data only)", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
                    @ApiResponse(responseCode = "404", description = "Not found, if the specified type doesn't exist", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "409", description = "Conflict, if relations of the type still exist in the tradition", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "500", description = "Failure, with an error report in JSON format", content = @Content(mediaType = "application/json"))
            }
    )
    public Response delete() {
        try (Transaction tx = db.beginTx()) {
            Node tradition = requireTraditionNode(tx);
            Node foundRelType = resolveRelationTypeNode(tx, tradition);
            RelationTypeModel rtModel = new RelationTypeModel(foundRelType);
            String actualName = rtModel.getName();

            // Do we have any relations that use this type?
            if (isInUse(tx, tradition, actualName))
                return Response.status(Response.Status.CONFLICT)
                        .entity(jsonerror("Relations of this type still exist; please alter them then try again.")).build();

            // Then I guess we can delete it.
            foundRelType.getSingleRelationship(ERelations.HAS_RELATION_TYPE, Direction.INCOMING).delete();
            foundRelType.delete();
            tx.commit();
            // Return the thing we deleted.
            return Response.ok(rtModel).build();
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
     * Checks whether any RELATED relationship in the tradition uses the relation type of the
     * given name. Relations record their type by name (the "type" property), so this must be
     * the type's actual current name, not the raw URL reference (which may be its numeric id).
     *
     * @param tx the transaction within which we are working
     * @param traditionNode the tradition whose relations to check
     * @param name the relation type's current name
     * @return true if at least one relation of this type exists
     */
    private static boolean isInUse(Transaction tx, Node traditionNode, String name) {
        List<Relationship> rels = new ArrayList<>();
        VariantGraphService.returnTraditionRelations(tx, traditionNode).relationships().forEach(rels::add);
        return rels.stream().anyMatch(x -> x.getProperty("type", "").equals(name));
    }

}
