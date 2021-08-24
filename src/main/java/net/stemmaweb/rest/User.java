package net.stemmaweb.rest;

import java.util.ArrayList;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import com.qmino.miredot.annotations.ReturnType;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.UserModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

import static net.stemmaweb.rest.Util.jsonerror;

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
     * @summary Get user
     *
     * @return A JSON UserModel or a JSON error message
     * @statuscode 200 on success
     * @statuscode 500 on failure, with an error report in JSON format
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType("net.stemmaweb.model.UserModel")
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
     * @summary Create / update user
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
    @ReturnType(clazz = UserModel.class)
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
     * @summary Delete user
     *
     * @statuscode 200 on success
     * @statuscode 404 if the requested user doesn't exist
     * @statuscode 412 if the user still owns traditions
     * @statuscode 500 on failure, with an error report in JSON format
     */
    @DELETE
    @ReturnType("java.lang.Void")
    public Response deleteUser() {
        Node foundUser;
        try (Transaction tx = db.beginTx()) {
            foundUser = db.findNode(Nodes.USER, "id", userId);

            if (foundUser != null) {
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
        return Response.status(Response.Status.OK).build();
    }

    /**
     * Get a list of the traditions belong to the user.
     *
     * @summary List user traditions
     *
     * @return A JSON list of tradition metadata objects
     */
    @GET
    @Path("/traditions")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.TraditionModel>")
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
