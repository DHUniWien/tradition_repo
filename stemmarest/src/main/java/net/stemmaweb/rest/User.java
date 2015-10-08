package net.stemmaweb.rest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.UserModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Uniqueness;

/**
 * Comprises all the API calls related to a user.
 * Can be called using http://BASE_URL/user
 * @author PSE FS 2015 Team2
 */

@Path("/user")
public class User {
    private GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private GraphDatabaseService db = dbServiceProvider.getDatabase();

    /**
     * Creates a user based on the parameters submitted in JSON.
     *
     * @param userModel
     *            in JSON Format
     * @return a userModel in JSON on success or an ERROR in JSON format
     */
    @POST
    @Path("createuser")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(UserModel userModel) {

        if (DatabaseService.userExists(userModel.getId(), db)) {

            return Response.status(Response.Status.CONFLICT)
                    .entity("Error: A user with this id already exists at " + db.toString())
                    .build();
        }

        try (Transaction tx = db.beginTx()) {
            Node rootNode = db.findNode(Nodes.ROOT, "name", "Root node");

            Node node = db.createNode(Nodes.USER);
            node.setProperty("id", userModel.getId());
            node.setProperty("role", userModel.getRole());
            node.setProperty("email", userModel.getEmail());
            node.setProperty("active", userModel.getActive());

            rootNode.createRelationshipTo(node, ERelations.SYSTEMUSER);

            tx.success();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.status(Response.Status.CREATED).entity(userModel).build();
    }

    /**
     * Gets a user by the id.
     *
     * @param userId The ID to look up
     * @return UserModel as JSON or an ERROR in JSON format
     */
    @GET
    @Path("getuser/withid/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserById(@PathParam("userId") String userId) {
        UserModel userModel = new UserModel();

        try (Transaction tx = db.beginTx()) {
            Node foundUser = db.findNode(Nodes.USER, "id", userId);
            if (foundUser != null) {
                userModel.setId((String) foundUser.getProperty("id"));
                userModel.setRole((String) foundUser.getProperty("role"));
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
     * Removes a user and all his traditions
     *
     * @param userId The ID of the user to delete
     * @return OK on success or an ERROR in JSON format
     */
    @DELETE
    @Path("deleteuser/withid/{userId}")
    public Response deleteUserById(@PathParam("userId") String userId) {
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
     * @param userId The ID to look up
     * @return OK on success or an ERROR in JSON format
     */
    @GET
    @Path("gettraditions/ofuser/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTraditionsByUserId(@PathParam("userId") String userId) {

        ArrayList<TraditionModel> traditions = new ArrayList<>();

        if (!DatabaseService.userExists(userId, db)) {
            return Response.status(Status.NOT_FOUND).build();
        }

        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (n)<-[:OWNS_TRADITION]-(userId:USER {id:'" + userId
                    + "'}) return n");
            Iterator<Node> tradIterator = result.columnAs("n");
            while (tradIterator.hasNext()) {
                if (tradIterator.hasNext()) {
                    Node tradNode = tradIterator.next();
                    TraditionModel tradition = new TraditionModel();

                    tradition.setId(tradNode.getProperty("id").toString());
                    tradition.setName(tradNode.getProperty("name").toString());
                    traditions.add(tradition);
                }
            }
            tx.success();
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(traditions).build();
    }
}
