package net.stemmaweb.rest;

import static net.stemmaweb.Util.jsonerror;

import java.util.ArrayList;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

import com.qmino.miredot.annotations.ReturnType;

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
     * @title Get user
     *
     * @return A JSON UserModel or a JSON error message
     * @statuscode 200 on success
     * @statuscode 500 on failure, with an error report in JSON format
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = UserModel.class)
    public Response getUserById() {
        try (Transaction tx = db.beginTx()) {
            Node foundUser = tx.findNode(Nodes.USER, "id", userId);
            if (foundUser != null) {
                return Response.ok(new UserModel(foundUser)).build();
            } else {
                return Response.noContent().build();
            }
        } catch (Exception e) {
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }

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
    @ReturnType(clazz = UserModel.class)
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
                tx.commit();
                return Response.ok(new UserModel(extantUser)).build();
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

                tx.commit();
                return Response.status(Status.CREATED).entity(new UserModel(extantUser)).build();
            }
        } catch (Exception e){
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
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
    @ReturnType(clazz = UserModel.class)
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
     * @title List user traditions
     *
     * @return A JSON list of tradition metadata objects
     */
    @GET
    @Path("/traditions")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.TraditionModel>")
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
