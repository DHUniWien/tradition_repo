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
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

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
                                  @FormDataParam("direction") String direction,
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

        if (filetype.equals("csv"))
            // Pass it off to the CSV reader
            return new TabularParser().parseCSV(uploadedInputStream, userId, name, ',');
        if (filetype.equals("tsv"))
            // Pass it off to the CSV reader with tab separators
            return new TabularParser().parseCSV(uploadedInputStream, userId, name, '\t');
        if (filetype.startsWith("xls"))
            // Pass it off to the Excel reader
            return new TabularParser().parseExcel(uploadedInputStream, userId, name, filetype);
        // TODO we need to parse TEI parallel seg, CTE, and CollateX XML
        if (filetype.equals("collatex"))
            return new CollateXParser().parseCollateX(uploadedInputStream, userId, name, direction);
        // Otherwise assume GraphML, for backwards compatibility.
        if (filetype.equals("graphml"))
            return new GraphMLParser().parseGraphML(uploadedInputStream, userId, name);

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

}
