package net.stemmaweb.rest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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
import javax.xml.stream.XMLStreamException;

import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.services.Neo4JToDotParser;
import net.stemmaweb.services.Neo4JToGraphMLParser;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Uniqueness;

import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;


/**
 * Comprises all the api calls related to a tradition.
 * Can be called using http://BASE_URL/tradition
 * @author PSE FS 2015 Team2
 */

@Path("/tradition")
public class Tradition implements IResource {
    private GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private GraphDatabaseService db = dbServiceProvider.getDatabase();

    /**
     * Changes the metadata of the tradition.
     *
     * @param tradition
     *            in JSON Format
     * @return OK and information about the tradition in JSON on success or an
     *         ERROR in JSON format
     */
    @POST
    @Path("changemetadata/fromtradition/{tradId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response changeTraditionMetadata(TraditionModel tradition,
            @PathParam("tradId") String witnessId) {

        if (!DatabaseService.checkIfUserExists(tradition.getOwnerId(), db)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Error: A user with this id does not exist")
                    .build();
        }

        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (witnessId:TRADITION {id:'" + witnessId
                    + "'}) return witnessId");
            Iterator<Node> nodes = result.columnAs("witnessId");

            if (nodes.hasNext()) {
                // Remove the old ownership
                String removeRelationQuery = "MATCH (tradition:TRADITION {id: '" + witnessId + "'}) "
                        + "MATCH tradition<-[r:OWNS_TRADITION]-(:USER) DELETE r";
                result = db.execute(removeRelationQuery);
                System.out.println(result.toString());

                // Add the new ownership
                String createNewRelationQuery = "MATCH(user:USER {id:'" + tradition.getOwnerId()
                        + "'}) " + "MATCH(tradition: TRADITION {id:'" + witnessId + "'}) "
                        + "SET tradition.name = '" + tradition.getName() + "' "
                        + "SET tradition.public = '" + tradition.getIsPublic() + "' "
                        + "CREATE (tradition)<-[r:OWNS_TRADITION]-(user) RETURN r, tradition";
                result = db.execute(createNewRelationQuery);
                System.out.println(result.toString());

            } else {
                // Tradition not found
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Tradition not found")
                        .build();
            }

            tx.success();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(tradition).build();
    }

    /**
     * Gets a list of all the complete traditions in the database.
     *
     * @return Http Response 200 and a list of tradition models in JSON on
     *         success or Http Response 500
     */
    @GET
    @Path("getalltraditions")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllTraditions() {
        List<TraditionModel> traditionList = new ArrayList<>();

        try (Transaction tx = db.beginTx()) {

            Result result = db.execute("match (u:USER)-[:OWNS_TRADITION]->(n:TRADITION) return n");
            Iterator<Node> traditions = result.columnAs("n");
            while(traditions.hasNext())
            {
                Node trad = traditions.next();
                TraditionModel tradModel = new TraditionModel();
                if(trad.hasProperty("id"))
                    tradModel.setId(trad.getProperty("id").toString());
                if(trad.hasProperty("name"))
                    tradModel.setName(trad.getProperty("name").toString());
                traditionList.add(tradModel);
            }
            tx.success();
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(traditionList).build();
    }

    /**
     * Gets a list of all the witnesses of a tradition with the given id.
     *
     * @param tradId
     * @return Http Response 200 and a list of witness models in JSON on success
     *         or an ERROR in JSON format
     */
    @GET
    @Path("getallwitnesses/fromtradition/{tradId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllWitnesses(@PathParam("tradId") String tradId) {

        ArrayList<WitnessModel> witnessList = new ArrayList<>();

        try (Transaction tx = db.beginTx()) {
            Node traditionNode;
            Iterable<Relationship> relationships;
            Node startNode = DatabaseService.getStartNode(tradId, db);

            try {
                if (startNode == null) {
                    return Response.status(Status.NOT_FOUND)
                            .entity("no tradition with this id was found")
                            .build();
                }

                traditionNode = getTraditionNode(tradId, db);

                if (traditionNode == null) {
                    return Response.status(Status.NOT_FOUND).entity("tradition not found").build();
                }

                relationships = startNode.getRelationships(Direction.OUTGOING);

                if (relationships == null) {
                    return Response.status(Status.NOT_FOUND).entity("start node not found").build();
                }

                for (Relationship rel : relationships) {
                    for (String id : ((String[]) rel.getProperty("witnesses"))) {
                        WitnessModel witM = new WitnessModel();
                        witM.setId(id);

                        witnessList.add(witM);
                    }
                }

                tx.success();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(witnessList).build();
    }

    /**
     * Gets a list of all relationships of a tradition with the given id.
     *
     * @param tradId
     * @return Http Response 200 and a list of relationship model in JSON
     */
    @GET
    @Path("getallrelationships/fromtradition/{tradId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllRelationships(@PathParam("tradId") String tradId) {

        ArrayList<RelationshipModel> relList = new ArrayList<>();

        try (Transaction tx = db.beginTx()) {

            Node startNode = DatabaseService.getStartNode(tradId, db);

            for (Node node : db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .uniqueness(Uniqueness.NODE_GLOBAL)
                    .traverse(startNode).nodes()) {

                Iterable<Relationship> rels = node.getRelationships(ERelations.RELATED,
                        Direction.OUTGOING);
                for(Relationship rel : rels)
                {
                    RelationshipModel relMod = new RelationshipModel(rel);
                    relList.add(relMod);
                }
            }
            tx.success();
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(relList).build();
    }

    /**
     * Helper method for getting the tradition node with a given tradition id
     *
     * @param tradId
     * @param engine
     * @return the root tradition node
     */
    private Node getTraditionNode(String tradId, GraphDatabaseService engine) {
        Result result = engine.execute("match (n:TRADITION {id: '" + tradId + "'}) return n");
        Iterator<Node> nodes = result.columnAs("n");

        if (nodes.hasNext()) {
            return nodes.next();
        }
        return null;
    }

    /**
     * Returns GraphML file from specified tradition owned by user
     *
     * @param tradId
     * @return XML data
     */
    @GET
    @Path("gettradition/withid/{tradId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTradition(@PathParam("tradId") String tradId) {
        Neo4JToGraphMLParser parser = new Neo4JToGraphMLParser();
        return parser.parseNeo4J(tradId);
    }

    /**
     * Removes a complete tradition
     *
     * @param tradId
     * @return http response
     */
    @DELETE
    @Path("deletetradition/withid/{tradId}")
    public Response deleteTraditionById(@PathParam("tradId") String tradId) {

        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (tradId:TRADITION {id:'" + tradId
                    + "'}) return tradId");
            Iterator<Node> nodes = result.columnAs("tradId");

            if (nodes.hasNext()) {
                Node node = nodes.next();

                /*
                 * Find all the nodes and relations to remove
                 */
                Set<Relationship> removableRelations = new HashSet<>();
                Set<Node> removableNodes = new HashSet<>();
                for (Node currentNode : db.traversalDescription()
                        .depthFirst()
                        .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                        .relationships(ERelations.STEMMA, Direction.OUTGOING)
                        .relationships(ERelations.RELATED, Direction.OUTGOING)
                        .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL)
                        .traverse(node)
                        .nodes())
                {
                    for(Relationship currentRelationship : currentNode.getRelationships()){
                        removableRelations.add(currentRelationship);
                    }
                    removableNodes.add(currentNode);
                }

                /*
                 * Remove the nodes and relations
                 */
                for(Relationship removableRel:removableRelations){
                    removableRel.delete();
                }
                for(Node remNode:removableNodes){
                    remNode.delete();
                }
            } else {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("A tradition with this id was not found!")
                        .build();
            }

            tx.success();
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.status(Response.Status.OK).build();
    }

    /**
     * Imports a tradition by given GraphML file and meta data
     *
     * @return Http Response with the id of the imported tradition on success or
     *         an ERROR in JSON format
     * @throws XMLStreamException
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/newtraditionwithgraphml")
    public Response importGraphMl(@FormDataParam("name") String name,
                                  @FormDataParam("language") String language,
                                  @FormDataParam("public") String is_public,
                                  @FormDataParam("userId") String userId,
                                  @FormDataParam("file") InputStream uploadedInputStream,
                                  @FormDataParam("file") FormDataContentDisposition fileDetail) throws IOException,
            XMLStreamException {



        if (!DatabaseService.checkIfUserExists(userId, db)) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("Error: No user with this id exists")
                    .build();
        }

        GraphMLToNeo4JParser parser = new GraphMLToNeo4JParser();
        Response resp = parser.parseGraphML(uploadedInputStream, userId, name);
        // nodes are unique

        return resp;
    }

    /**
     * Returns DOT file from specified tradition owned by user
     *
     * @param tradId
     * @return XML data
     */
    @GET
    @Path("getdot/fromtradition/{tradId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDot(@PathParam("tradId") String tradId) {

        String filename = "upload/output.dot";

        File file = new File(filename);
        file.delete();

        if(getTraditionNode(tradId, db) == null)
            return Response.status(Status.NOT_FOUND).entity("No such tradition found").build();

        Neo4JToDotParser parser = new Neo4JToDotParser(db);
        parser.parseNeo4J(tradId);

        String everything = "";
        try(BufferedReader br = new BufferedReader(new FileReader(filename))) {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append(System.lineSeparator());
                line = br.readLine();
            }
            everything = sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Response.ok(everything).build();
    }
}
