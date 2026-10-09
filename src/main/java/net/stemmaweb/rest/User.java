package net.stemmaweb.rest;

import static net.stemmaweb.Util.jsonerror;

import java.util.ArrayList;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.UserModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;

/**
 * Comprises all the API calls related to a user.
 * Can be called using <a href="http://BASE_URL/user">...</a>
 * @author PSE FS 2015 Team2
 */

public class User {
    private final GraphDatabaseService db;
    /**
     * The ID of a stemmarest user; this is usually either an email address or a Google ID token.
     */
    private final String userId;

    public User (String requestedId) {
        db = new GraphDatabaseServiceProvider().getDatabase();
        userId = requestedId;
    }

    /**
     * Gets the information for the given user ID.
     *
     * @return A JSON UserModel or a JSON error message
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            tags = {"User"},
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
                    @ApiResponse(responseCode = "200", description = "Success", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserModel.class))),
                    @ApiResponse(responseCode = "404", description = "User not found", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "500", description = "Failure, with an error report in JSON format", content = @Content(mediaType = "application/json"))
            }
    )
    public Response getUserById() {
        try (Transaction tx = db.beginTx()) {
            Node foundUser = tx.findNode(Nodes.USER, "id", userId);
            if (foundUser != null) {
                return Response.ok(new UserModel(foundUser)).build();
            } else {
                return Response.status(Status.NOT_FOUND).build();
            }
        } catch (Exception e) {
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }

    }

    /**
     * Creates or updates a user according to the specification given.
     *
     * @param userModel - a user specification
     * @return A JSON UserModel or a JSON error message
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            tags = {"User"},
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
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserModel.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Existing user updated", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserModel.class))),
                    @ApiResponse(responseCode = "201", description = "New user created", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserModel.class))),
                    @ApiResponse(responseCode = "500", description = "Failure, with an error report in JSON format", content = @Content(mediaType = "application/json"))
            }
    )
    public Response create(UserModel userModel) {
        // Find any existing user
        Node extantUser;
        try (Transaction tx = db.beginTx()) {
            extantUser = tx.findNode(Nodes.USER, "id", userId);
            if (extantUser != null) {
                // User exists, so update it
                if (extantUser.getProperty("passphrase") != userModel.getPassphrase())
                    extantUser.setProperty("passphrase", userModel.getPassphrase());
                if (extantUser.getProperty("role") != userModel.getRole())
                    extantUser.setProperty("role", userModel.getRole());
                if (extantUser.getProperty("email") != userModel.getEmail())
                    extantUser.setProperty("email", userModel.getEmail());
                if (extantUser.getProperty("active") != userModel.getActive())
                    extantUser.setProperty("active", userModel.getActive());
                UserModel updatedUser = new UserModel(extantUser);
                tx.commit();
                return Response.ok(updatedUser).build();
            } else {
                // User doesn't exist, so create it
                Node rootNode = tx.findNode(Nodes.ROOT, "name", "Root node");
                extantUser = tx.createNode(Nodes.USER);
                extantUser.setProperty("id", userId);
                extantUser.setProperty("passphrase", userModel.getPassphrase());
                extantUser.setProperty("role", userModel.getRole());
                extantUser.setProperty("email", userModel.getEmail());
                extantUser.setProperty("active", userModel.getActive());

                rootNode.createRelationshipTo(extantUser, ERelations.SYSTEMUSER);

                UserModel createdUser = new UserModel(extantUser);
                tx.commit();
                return Response.status(Status.CREATED).entity(createdUser).build();
            }
        } catch (Exception e){
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
    }



    /**
     * Removes a user. This may only be used when the user's traditions have already been deleted.
     *
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            tags = {"User"},
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
                    @ApiResponse(responseCode = "200", description = "Success", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserModel.class))),
                    @ApiResponse(responseCode = "404", description = "User not found", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "412", description = "User still owns traditions", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "500", description = "Failure, with an error report in JSON format", content = @Content(mediaType = "application/json"))
            }
    )
    public Response deleteUser() {
        Node foundUser;
        UserModel removed;
        try (Transaction tx = db.beginTx()) {
            foundUser = tx.findNode(Nodes.USER, "id", userId);

            if (foundUser != null) {
                removed = new UserModel(foundUser);
                // See if the user owns any traditions
                ArrayList<Node> userTraditions = DatabaseService.getRelated(foundUser, ERelations.OWNS_TRADITION);
                if (!userTraditions.isEmpty())
                    return Response.status(Status.PRECONDITION_FAILED)
                            .entity("User's traditions must be deleted first")
                            .build();

                // Otherwise, do the deed.
                DatabaseService.getRelationships(foundUser).forEach(Relationship::delete);
                foundUser.delete();
                tx.commit();
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
     * @return A JSON list of tradition metadata objects
     */
    @GET
    @Path("/traditions")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            tags = {"User"},
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
                    @ApiResponse(responseCode = "200", description = "Success", content = @Content(mediaType = "application/json", schema = @Schema(type = "array", implementation = TraditionModel.class))),
                    @ApiResponse(responseCode = "404", description = "User not found", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "500", description = "Failure, with an error report in JSON format", content = @Content(mediaType = "application/json"))
            }
    )
    public Response getUserTraditions() {
    	try (Transaction tx = db.beginTx()) {
            Node thisUser = tx.findNode(Nodes.USER, "id", userId);
            if (thisUser == null)
                return Response.status(Status.NOT_FOUND).entity(jsonerror("User does not exist")).build();

            ArrayList<TraditionModel> traditions = new ArrayList<>();
            DatabaseService.getRelated(thisUser, ERelations.OWNS_TRADITION)
                    .forEach(x -> traditions.add(new TraditionModel(x)));
            return Response.ok(traditions).build();
        } catch (Exception e) {
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
    }
}
