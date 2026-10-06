package net.stemmaweb.rest;

import static net.stemmaweb.Util.jsonerror;

import java.util.ArrayList;
import java.util.List;

import org.neo4j.graphdb.Relationship;

import net.stemmaweb.services.GraphDatabaseServiceProvider;
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
        typeName = requestedType;
    }

    /**
     * Gets the information for the given relation type name.
     *
     * @title Get relation type
     *
     * @return A JSON RelationTypeModel or a JSON error message
     * @statuscode 200 on success
     * @statuscode 500 on failure, with an error report in JSON format
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            summary = "Get relation type",
            description = "Gets the information for the given relation type name.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "A JSON RelationTypeModel", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RelationTypeModel.class))),
                    @ApiResponse(responseCode = "404", description = "Not found, if the relation type does not exist", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "500", description = "Failure, with an error report in JSON format", content = @Content(mediaType = "application/json"))
            }
    )
    public Response getRelationType() {
        RelationTypeModel rtModel = new RelationTypeModel(typeName);
        Response response;
        try (Transaction tx = db.beginTx()){
            Node foundRelType = rtModel.lookup(VariantGraphService.getTraditionNode(tx, traditionId));
            if (foundRelType == null) {
                response = Response.status(Response.Status.NOT_FOUND).build();
            } else {
            	response = Response.ok(new RelationTypeModel(foundRelType)).build();
            }
        } catch (Exception e) {
            response = Response.serverError().entity(jsonerror(e.getMessage())).build();
        }

        return response;
    }

    /**
     * Creates or updates a relation type according to the specification given.
     *
     * @title Create / update relation type specification
     *
     * @param rtModel - a user specification
     * @return A JSON RelationTypeModel or a JSON error message
     * @statuscode 200 on success, if an existing type was updated
     * @statuscode 201 on success, if a new type was created
     * @statuscode 304 if a default type was requested but could not be generated
     * @statuscode 409 if a default type was requested but a type of that name already exists
     * @statuscode 500 on failure, with an error report in JSON format
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            summary = "Create / update relation type specification",
            description = "Creates or updates a relation type according to the specification given.",
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
                    @ApiResponse(responseCode = "409", description = "A default type was requested, but a type of that name already exists", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "500", description = "Failure, with an error report in JSON format", content = @Content(mediaType = "application/json"))
            }
    )
    public Response create(RelationTypeModel rtModel) {
        // Find any existing relation type on this tradition
        try (Transaction tx = db.beginTx()){
            Node traditionNode = VariantGraphService.getTraditionNode(tx, traditionId);
            Node extantRelType = rtModel.lookup(traditionNode);

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

            if (extantRelType != null) {
                extantRelType = rtModel.update(traditionNode, tx);
                if (extantRelType != null) {
                    tx.commit();
                    return Response.ok().entity(rtModel).build();
                }
            } else {
                extantRelType = rtModel.instantiate(traditionNode, tx);
                if (extantRelType != null) {
                    tx.commit();
                    return Response.status(Response.Status.CREATED).entity(rtModel).build();
                }

            }
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
     * @title Delete a relation type
     * @return A JSON RelationTypeModel of the deleted type
     * @statuscode 200 on success
     * @statuscode 404 if the specified type doesn't exist
     * @statuscode 409 if relations of the type still exist in the tradition
     * @statuscode 500 on failure, with an error report in JSON format
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            summary = "Delete a relation type",
            description = "Deletes the named relation type.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "A JSON RelationTypeModel of the deleted type", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RelationTypeModel.class))),
                    @ApiResponse(responseCode = "404", description = "Not found, if the specified type doesn't exist", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "409", description = "Conflict, if relations of the type still exist in the tradition", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "500", description = "Failure, with an error report in JSON format", content = @Content(mediaType = "application/json"))
            }
    )
    public Response delete() {
        RelationTypeModel rtModel = new RelationTypeModel(typeName);
        Node foundRelType;
        try (Transaction tx = db.beginTx()) {
        	Node tradition = VariantGraphService.getTraditionNode(tx, traditionId);
        	try {
        		foundRelType = rtModel.lookup(tradition);
        		if (foundRelType == null) {
        			return Response.status(Response.Status.NOT_FOUND).build();
        		}
        	} catch (Exception e) {
        		return Response.serverError().entity(jsonerror(e.getMessage())).build();
        	}
            // Do we have any relations that use this type?
            List<Relationship> rels = new ArrayList<>();
            VariantGraphService.returnTraditionRelations(tx, tradition).relationships().forEach(rels::add);
            if (rels.stream().anyMatch(x -> x.getProperty("type", "").equals(typeName)))
                return Response.status(Response.Status.CONFLICT)
                        .entity(jsonerror("Relations of this type still exist; please alter them then try again.")).build();

            // Then I guess we can delete it.
            foundRelType.getSingleRelationship(ERelations.HAS_RELATION_TYPE, Direction.INCOMING).delete();
            foundRelType.delete();
            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
		}
        // Return the thing we deleted.
        return Response.ok(rtModel).build();
    }

}
