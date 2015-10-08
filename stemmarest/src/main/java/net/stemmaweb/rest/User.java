package net.stemmaweb.rest;

import java.util.ArrayList;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.UserModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

/**
 * Comprises all the API calls related to a user.
 * Can be called using http://BASE_URL/user
 * @author PSE FS 2015 Team2
 */

public class User {
    private GraphDatabaseService db;
    private String userId;

    public User (String requestedId) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        userId = requestedId;
    }

    /**
     * Gets a user by the id.
     *
     * @return A JSON UserModel or a JSON error message
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserById() {
        UserModel userModel;
        try (Transaction tx = db.beginTx()) {
            Node foundUser = db.findNode(Nodes.USER, "id", userId);
            if (foundUser != null) {
                userModel = new UserModel(foundUser);
            } else {
                return Response.status(Status.NO_CONTENT).build();
            }
            tx.success();
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(userModel).build();
    }

    /**
     * Removes a user and all its traditions
     *
     * @return OK on success or an ERROR in JSON format
     */
    @DELETE
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
     * Get all Traditions of a user
     *
     * @return A JSON list of TraditionModel objects
     */
    @GET
    @Path("/traditions")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserTraditions() {
        if (!DatabaseService.userExists(userId, db)) {
            return Response.status(Status.NOT_FOUND).build();
        }

        ArrayList<TraditionModel> traditions = new ArrayList<>();
        try {
            Node thisUser = getUserNode();
            DatabaseService.getRelated(thisUser, ERelations.OWNS_TRADITION)
                    .forEach(x -> traditions.add(new TraditionModel(x)));
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
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
