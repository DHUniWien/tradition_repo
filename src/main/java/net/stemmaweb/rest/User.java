package net.stemmaweb.rest;

import java.util.ArrayList;
import java.util.Map;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.UserModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

import static net.stemmaweb.Util.jsonerror;

/**
 * Comprises all the API calls related to a user.
 * Can be called using http://BASE_URL/user
 * @author PSE FS 2015 Team2
 */

public class User {
    private GraphDatabaseService db;
    /**
     * The ID of a stemmarest user; this is usually either an email address or a Google ID token.
     */
    private String userId;

    public User (String requestedId) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        userId = requestedId;
    }

    /**
     * Gets the information for the given user ID.
     *
     * @title Get user
     *
     * @return A JSON UserModel or a JSON error message
     * @statuscode 200 on success
     * @statuscode 500 on failure, with an error report in JSON format
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            summary = "Get user",
            description = "Gets the information for the given user ID",
            parameters = {
                    @Parameter(
                            name = "userId",
                            description = "The ID of a stemmarest user; usually an email address or Google ID token",
                            required = true,
                            in = ParameterIn.PATH
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Success",
                            content = @Content(schema = @Schema(implementation = UserModel.class))
                    ),
                    @ApiResponse(
                            responseCode = "204",
                            description = "No content (user not found)"
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Failure",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    )
            }
    )
    public Response getUserById() {
        UserModel userModel;
        try (Transaction tx = db.beginTx()) {
            Node foundUser = db.findNode(Nodes.USER, "id", userId);
            if (foundUser != null) {
                userModel = new UserModel(foundUser);
            } else {
                return Response.noContent().build();
            }
            tx.success();
        } catch (Exception e) {
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(userModel).build();
    }

    /**
     * Creates or updates a user according to the specification given.
     *
     * @title Create / update user
     *
     * @param userModel - a user specification
     * @return A JSON UserModel or a JSON error message
     * @statuscode 200 on success, if an existing user was updated
     * @statuscode 201 on success, if a new user was created
     * @statuscode 500 on failure, with an error report in JSON format
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            summary = "Create / update user",
            description = "Creates or updates a user according to the specification given",
            parameters = {
                    @Parameter(
                            name = "userId",
                            description = "The ID of a stemmarest user; usually an email address or Google ID token",
                            required = true,
                            in = ParameterIn.PATH
                    )
            },
            requestBody = @RequestBody(
                    description = "User specification",
                    required = true,
                    content = @Content(schema = @Schema(implementation = UserModel.class))
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Existing user updated",
                            content = @Content(schema = @Schema(implementation = UserModel.class))
                    ),
                    @ApiResponse(
                            responseCode = "201",
                            description = "New user created",
                            content = @Content(schema = @Schema(implementation = UserModel.class))
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Failure",
                            content = @Content(schema = @Schema(implementation = String.class))
                    )
            }
    )
    public Response create(UserModel userModel) {
        // Find any existing user
        Node extantUser;
        try (Transaction tx = db.beginTx()) {
            extantUser = db.findNode(Nodes.USER, "id", userId);
            tx.success();
        }

        Status returnedStatus;
        if (extantUser != null) {
            // Update the user if it exists
            try (Transaction tx = db.beginTx()) {
                if (extantUser.getProperty("passphrase") != userModel.getPassphrase())
                    extantUser.setProperty("passphrase", userModel.getPassphrase());
                if (extantUser.getProperty("role") != userModel.getRole())
                    extantUser.setProperty("role", userModel.getRole());
                if (extantUser.getProperty("email") != userModel.getEmail())
                    extantUser.setProperty("email", userModel.getEmail());
                if (extantUser.getProperty("active") != userModel.getActive())
                    extantUser.setProperty("active", userModel.getActive());
                tx.success();
            } catch (Exception e) {
                return Response.serverError().entity(jsonerror(e.getMessage())).build();
            }
            returnedStatus = Response.Status.OK;
        } else {
            // Create it if it doesn't exist
            try (Transaction tx = db.beginTx()) {
                Node rootNode = db.findNode(Nodes.ROOT, "name", "Root node");

                extantUser = db.createNode(Nodes.USER);
                extantUser.setProperty("id", userId);
                extantUser.setProperty("passphrase", userModel.getPassphrase());
                extantUser.setProperty("role", userModel.getRole());
                extantUser.setProperty("email", userModel.getEmail());
                extantUser.setProperty("active", userModel.getActive());

                rootNode.createRelationshipTo(extantUser, ERelations.SYSTEMUSER);

                tx.success();
            } catch (Exception e) {
                return Response.serverError().entity(jsonerror(e.getMessage())).build();
            }
            returnedStatus = Response.Status.CREATED;
        }
        UserModel returnedModel = new UserModel(extantUser);
        return Response.status(returnedStatus).entity(returnedModel).build();
    }



    /**
     * Removes a user. This may only be used when the user's traditions have already been deleted.
     *
     * @title Delete user
     *
     * @statuscode 200 on success
     * @statuscode 404 if the requested user doesn't exist
     * @statuscode 412 if the user still owns traditions
     * @statuscode 500 on failure, with an error report in JSON format
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            summary = "Delete user",
            description = "Removes a user. Requires user's traditions to be deleted first",
            parameters = {
                    @Parameter(
                            name = "userId",
                            description = "The ID of a stemmarest user; usually an email address or Google ID token",
                            required = true,
                            in = ParameterIn.PATH
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Success",
                            content = @Content(schema = @Schema(implementation = UserModel.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "User not found",
                            content = @Content(schema = @Schema(implementation = String.class))
                    ),
                    @ApiResponse(
                            responseCode = "412",
                            description = "User still owns traditions",
                            content = @Content(schema = @Schema(implementation = String.class))
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Failure",
                            content = @Content(schema = @Schema(implementation = String.class))
                    )
            }
    )
    public Response deleteUser() {
        Node foundUser;
        UserModel removed;
        try (Transaction tx = db.beginTx()) {
            foundUser = db.findNode(Nodes.USER, "id", userId);

            if (foundUser != null) {
                removed = new UserModel(foundUser);
                // See if the user owns any traditions
                ArrayList<Node> userTraditions = DatabaseService.getRelated(foundUser, ERelations.OWNS_TRADITION);
                if (userTraditions.size() > 0)
                    return Response.status(Status.PRECONDITION_FAILED)
                            .entity("User's traditions must be deleted first")
                            .build();

                // Otherwise, do the deed.
                foundUser.getRelationships().forEach(Relationship::delete);
                foundUser.delete();
                tx.success();
            } else {
                return Response.status(Status.NOT_FOUND)
                        .entity("A user with this ID was not found")
                        .build();
            }
        }
        return Response.ok(removed).build();
    }

    /**
     * Get a list of the traditions belong to the user.
     *
     * @title List user traditions
     *
     * @return A JSON list of tradition metadata objects
     */
    @GET
    @Path("/traditions")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            summary = "List user traditions",
            description = "Get a list of the traditions belonging to the user",
            parameters = {
                    @Parameter(
                            name = "userId",
                            description = "The ID of a stemmarest user; usually an email address or Google ID token",
                            required = true,
                            in = ParameterIn.PATH
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Success",
                            content = @Content(schema = @Schema(
                                    implementation = TraditionModel[].class,
                                    description = "List of tradition metadata objects"))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "User not found",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Failure",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    )
            }
    )
    public Response getUserTraditions() {
        if (!DatabaseService.userExists(userId, db)) {
            return Response.status(Status.NOT_FOUND).entity(jsonerror("User does not exist")).build();
        }

        ArrayList<TraditionModel> traditions = new ArrayList<>();
        try {
            Node thisUser = getUserNode();
            DatabaseService.getRelated(thisUser, ERelations.OWNS_TRADITION)
                    .forEach(x -> traditions.add(new TraditionModel(x)));
        } catch (Exception e) {
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(traditions).build();
    }

    private Node getUserNode() {
        Node foundUser;
        try (Transaction tx = db.beginTx()) {
            foundUser = db.findNode(Nodes.USER, "id", userId);
            tx.success();
        }
        return foundUser;
    }
}
