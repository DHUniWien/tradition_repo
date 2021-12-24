package net.stemmaweb.rest;

import com.qmino.miredot.annotations.ReturnType;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.UserModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;

import net.stemmaweb.services.VariantGraphService;
import org.apache.tika.Tika;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.json.JSONException;
import org.json.JSONObject;
import org.neo4j.graphdb.*;

import javax.servlet.ServletContext;
import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.stemmaweb.rest.Util.jsonerror;
import static net.stemmaweb.rest.Util.jsonresp;

/**
 * The root of the REST hierarchy. Deals with system-wide collections of
 * objects.
 *
 * @author tla
 */
@Path("/")
public class Root {
    @Context ServletContext context;
    @Context UriInfo uri;
    private final GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private final GraphDatabaseService db = dbServiceProvider.getDatabase();

    /*
     * Delegated API calls
     */

    private static final String CLICHED_MESSAGE = "Hello World!";

    @GET
    @Produces("text/plain")
    public String getHello() {
        return CLICHED_MESSAGE;
    }

    @GET
    @Path("{path: docs.*}")
    public Response getDocs(@PathParam("path") String path) {
        if (path.equals("docs") || path.equals("docs/")) path = "docs/index.html";
        final String target = String.format("/WEB-INF/%s", path);
        Tika tika = new Tika();
        try {
            String mimeType = tika.detect(new File(context.getRealPath(target)));
            InputStream resource = context.getResourceAsStream(target);
            return Response.status(Response.Status.OK).type(mimeType).entity(resource).build();
        } catch (IOException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    /**
     * @param tradId - the ID of the tradition being queried
     */
    @Path("/tradition/{tradId}")
    public Tradition getTradition(@PathParam("tradId") String tradId) {
        return new Tradition(tradId);
    }
    /**
     * @param userId - The ID of a stemmarest user; this is usually either an email address or a Google ID token.
     */
    @Path("/user/{userId}")
    public User getUser(@PathParam("userId") String userId) {
        return new User(userId);
    }
    /**
     * @param readingId - the ID of the reading being queried
     */
    @Path("/reading/{readingId}")
    public Reading getReading(@PathParam("readingId") String readingId) {
        return new Reading(readingId);
    }

    /*
     * Resource creation calls
     */

    /**
     * Imports a new tradition from file data of various forms, and creates at least one section
     * in doing so. Returns the ID of the given tradition, in the form {@code {"tradId": <ID>}}.
     *
     * @summary Upload new tradition
     *
     * @param name      the name of the tradition. Default is the empty string.
     * @param language  the language of the tradition text (e.g. Latin, Syriac).
     * @param direction the direction in which the text should be read. Possible values
     *                  are {@code LR} (left to right), {@code RL} (right to left), or {@code BI} (bidirectional).
     *                  Default is LR.
     * @param userId    the ID of the user to whom this tradition belongs. Required.
     * @param is_public If true, the tradition will be marked as publicly viewable.
     * @param filetype  the type of file being uploaded. Possible values are {@code collatex},
     *                  {@code cxjson}, {@code csv}, {@code tsv}, {@code xls}, {@code xlsx},
     *                  {@code graphml}, {@code stemmaweb}, or {@code teips}.
     *                  Required if 'file' is present.
     * @param empty     Should be set to some non-null value if the tradition is being created without any data file.
     *                  Required if 'file' is not present.
     * @param uploadedInputStream The file data to upload.
     * @param fileDetail The file data to upload.
     *
     * @statuscode 201 - The tradition was created successfully.
     * @statuscode 400 - No file was specified, and the 'empty' flag was not set.
     * @statuscode 409 - The requested owner does not exist in the database.
     * @statuscode 500 - Something went wrong. An error message will be returned.
     *
     */
    @POST
    @Path("/tradition")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("application/json; charset=utf-8")
    @ReturnType("java.lang.Void")
    public Response importGraphMl(@DefaultValue("") @FormDataParam("name") String name,
                                  @FormDataParam("userId") String userId,
                                  @FormDataParam("public") String is_public,
                                  @FormDataParam("language") String language,
                                  @DefaultValue("LR") @FormDataParam("direction") String direction,
                                  @FormDataParam("empty") String empty,
                                  @FormDataParam("filetype") String filetype,
                                  @FormDataParam("file") InputStream uploadedInputStream,
                                  @FormDataParam("file") FormDataContentDisposition fileDetail) {

        if (!DatabaseService.userExists(userId, db)) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(jsonerror("No user with this id exists"))
                    .build();
        }

        if (fileDetail == null && uploadedInputStream == null && empty == null) {
            // No file to parse
            return Response.status(Response.Status.BAD_REQUEST).entity(jsonerror("No file found")).build();
        }

        String tradId;
        try {
            tradId = this.createTradition(name, direction, language, is_public);
        } catch (Exception e) {
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }

        // Link the given user to the created tradition.
        try {
            this.linkUserToTradition(userId, tradId);
        } catch (Exception e) {
            new Tradition(tradId).deleteTraditionById();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }

        // If we got file contents, we should send them off for parsing.
        if (empty == null) {
            Response dataResult = Tradition.parseDispatcher(VariantGraphService.getTraditionNode(tradId, db),
                    filetype, uploadedInputStream, false);
            if (dataResult.getStatus() != Response.Status.CREATED.getStatusCode()) {
                // If something went wrong, delete the new tradition immediately and return the error.
                new Tradition(tradId).deleteTraditionById();
                return dataResult;
            }
            // If we just parsed GraphML (the only format that can preserve prior tradition IDs),
            // get the actual tradition ID in case it was preserved from a prior export.
            if (filetype.equals("graphml")) {
                try {
                    JSONObject dataValues = new JSONObject(dataResult.getEntity().toString());
                    tradId = dataValues.get("parentId").toString();
                } catch (JSONException e) {
                    e.printStackTrace();
                    return Response.serverError().entity(jsonerror("Bad file parse response")).build();
                }
            }
        }

        // Handle direct non-Jersey calls from our test suite
        if (uri == null)
            return Response.status(Response.Status.CREATED).entity(jsonresp("tradId", tradId)).build();
        else
            return Response.created(uri.getRequestUriBuilder().path(tradId).build())
                .entity(jsonresp("tradId", tradId)).build();
    }

    /*
     * Collection calls
     */

    /**
     * Gets a list of all the complete traditions in the database.
     *
     * @summary List traditions
     *
     * @param publiconly    Returns only the traditions marked as being public.
     *                      Default is false.
     *
     * @return A list, one item per tradition, of tradition metadata.
     * @statuscode 200 on success
     * @statuscode 500 on failure, with an error report in JSON format
     */
    @GET
    @Path("/traditions")
    @Produces("application/json; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.TraditionModel>")
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
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(traditionList).build();
    }

    /**
     * Gets a list of all the users in the database.
     *
     * @summary List users
     *
     * @return A list, one item per user, of user metadata.
     * @statuscode 200 on success
     * @statuscode 500 on failure, with an error report in JSON format
     */
    @GET
    @Path("/users")
    @Produces("application/json; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.UserModel>")
    public Response getAllUsers() {
        List<UserModel> userList = new ArrayList<>();

        try (Transaction tx = db.beginTx()) {

            db.findNodes(Nodes.USER)
                    .forEachRemaining(t -> userList.add(new UserModel(t)));
            tx.success();
        } catch (Exception e) {
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(userList).build();
    }

    private String createTradition(String name, String direction, String language, String isPublic) {
        String tradId = UUID.randomUUID().toString();
        try (Transaction tx = db.beginTx()) {
            // Make the tradition node
            Node traditionNode = db.createNode(Nodes.TRADITION);
            traditionNode.setProperty("id", tradId);
            // This has a default value
            traditionNode.setProperty("direction", direction);
            // The rest of them don't have defaults
            if (name != null)
                traditionNode.setProperty("name", name);
            if (language != null)
                traditionNode.setProperty("language", language);
            if (isPublic != null)
                traditionNode.setProperty("is_public", isPublic.equals("true"));
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return tradId;
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
