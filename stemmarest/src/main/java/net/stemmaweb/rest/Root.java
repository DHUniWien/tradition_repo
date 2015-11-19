package net.stemmaweb.rest;

import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.UserModel;
import net.stemmaweb.parser.CollateXParser;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.parser.GraphMLParser;
import net.stemmaweb.parser.TabularParser;
import org.neo4j.graphdb.*;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The root of the REST hierarchy. Deals with system-wide collections of
 * objects.
 *
 * @author tla
 */
@Path("/")
public class Root {
    private GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private GraphDatabaseService db = dbServiceProvider.getDatabase();

    /**
     * Delegated API calls
     */

    @Path("/tradition/{tradId}")
    public Tradition getTradition(@PathParam("tradId") String tradId) {
        return new Tradition(tradId);
    }
    @Path("/user/{userId}")
    public User getUser(@PathParam("userId") String userId) {
        return new User(userId);
    }
    @Path("/reading/{readingId}")
    public Reading getReading(@PathParam("readingId") String readingId) {
        return new Reading(readingId);
    }

    /**
     * Resource creation calls
     */

    /**
     * Imports a tradition by given GraphML file and meta data
     *
     * @return Http Response with the id of the imported tradition on success or
     *         an ERROR in JSON format
     * @throws XMLStreamException
     */
    @PUT
    @Path("/tradition")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response importGraphMl(@FormDataParam("name") String name,
                                  @FormDataParam("filetype") String filetype,
                                  @FormDataParam("language") String language,
                                  @DefaultValue("LR") @FormDataParam("direction") String direction,
                                  @FormDataParam("public") String is_public,
                                  @FormDataParam("userId") String userId,
                                  @FormDataParam("file") InputStream uploadedInputStream,
                                  @FormDataParam("file") FormDataContentDisposition fileDetail) throws IOException,
            XMLStreamException {

        if (!DatabaseService.userExists(userId, db)) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("Error: No user with this id exists")
                    .build();
        }

        String tradId;
        try {
            tradId = this.createTradition(name, direction, language, is_public);
            this.linkUserToTradition(userId, tradId);
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(String.format("{\"error\":\"%s\"}", e.getMessage()))
                    .build();
        }

        if (fileDetail == null && uploadedInputStream == null) {
            // No file to parse
            String response = String.format("{\"tradId\":\"%s\"}", tradId);
            return Response.status(Response.Status.CREATED).entity(response).build();
        }
        if (filetype.equals("csv"))
            // Pass it off to the CSV reader
            return new TabularParser().parseCSV(uploadedInputStream, tradId, ',');
        if (filetype.equals("tsv"))
            // Pass it off to the CSV reader with tab separators
            return new TabularParser().parseCSV(uploadedInputStream, tradId, '\t');
        if (filetype.startsWith("xls"))
            // Pass it off to the Excel reader
            return new TabularParser().parseExcel(uploadedInputStream, tradId, filetype);
        // TODO we need to parse TEI parallel seg, CTE, and CollateX XML
        if (filetype.equals("collatex"))
            return new CollateXParser().parseCollateX(uploadedInputStream, tradId);
        // Otherwise assume GraphML, for backwards compatibility. Text direction will be taken
        // from the GraphML file.
        if (filetype.equals("graphml"))
            return new GraphMLParser().parseGraphML(uploadedInputStream, userId, tradId);

        // If we got this far, it was an unrecognized filetype.
        return Response.status(Response.Status.BAD_REQUEST).entity("Unrecognized file type " + filetype).build();
    }

    /**
     * Creates a user based on the parameters submitted in JSON.
     *
     * @param userModel -  in JSON format
     * @return A JSON UserModel or a JSON error message
     */
    @PUT
    @Path("/user")
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
     * Collection calls
     */

    /**
     * Gets a list of all the complete traditions in the database.
     *
     * @return Http Response 200 and a list of tradition models in JSON on
     *         success or Http Response 500
     */
    @GET
    @Path("/traditions")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllTraditions(@DefaultValue("false") @QueryParam("public") Boolean publiconly) {
        List<TraditionModel> traditionList = new ArrayList<>();

        try (Transaction tx = db.beginTx()) {
            ResourceIterator<Node> nodeList;
            if (publiconly)
                nodeList = db.findNodes(Nodes.TRADITION, "is_public", true);
            else
                nodeList = db.findNodes(Nodes.TRADITION);
            nodeList.forEachRemaining(t -> traditionList.add(new TraditionModel(t)));
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(traditionList).build();
    }

    @GET
    @Path("/users")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllUsers() {
        List<UserModel> userList = new ArrayList<>();

        try (Transaction tx = db.beginTx()) {

            db.findNodes(Nodes.USER)
                    .forEachRemaining(t -> userList.add(new UserModel(t)));
            tx.success();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(userList).build();
    }

    public String createTradition(String name, String direction, String language, String isPublic) throws Exception {
        String tradId = UUID.randomUUID().toString();
        try (Transaction tx = db.beginTx()) {
            // Make the tradition node
            /*
            traditionNode = db.findNode(Nodes.TRADITION, "name", name);
            if (traditionNode != null) {
                tx.failure();
                throw new Exception("A tradition named '" + name + "' already exists!");
            }
            */
            Node traditionNode = db.createNode(Nodes.TRADITION);
            traditionNode.setProperty("id", tradId);
            traditionNode.setProperty("name", name);
            traditionNode.setProperty("direction", direction);
            if (language != null) {
                traditionNode.setProperty("language", language);
            }
            if (isPublic != null) {
                traditionNode.setProperty("isPublic", isPublic);
            }
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Could not create a new tradition!");
        }
        return tradId;
    }

    private boolean createUser(String userId, String isAdmin) throws Exception {
        try (Transaction tx = db.beginTx()) {
            Node rootNode = db.findNode(Nodes.ROOT, "name", "Root node");
            if (rootNode == null) {
                DatabaseService.createRootNode(db);
                rootNode = db.findNode(Nodes.ROOT, "name", "Root node");
            }
            Node userNode = db.findNode(Nodes.USER, "id", userId);
            if (userNode != null) {
                tx.failure();
                throw new Exception("A user with ID " + userId + " already exists!");
            }
            Node node = db.createNode(Nodes.USER);
            node.setProperty("id", userId);
            node.setProperty("isAdmin", isAdmin);

            rootNode.createRelationshipTo(node, ERelations.SYSTEMUSER);
            tx.success();
        }
        return true;
    }

    private void linkUserToTradition(String userId, String tradId) throws Exception {
        try (Transaction tx = db.beginTx()) {
            Node userNode = db.findNode(Nodes.USER, "id", userId);
            if (userNode == null) {
                tx.failure();
                throw new Exception("There is no user with ID " + userId + "!");
            }
            Node traditionNode = db.findNode(Nodes.TRADITION, "id", tradId);
            if (traditionNode == null) {
                tx.failure();
                throw new Exception("There is no tradition with ID " + tradId + "!");
            }
            userNode.createRelationshipTo(traditionNode, ERelations.OWNS_TRADITION);
            tx.success();
        }
    }
}
