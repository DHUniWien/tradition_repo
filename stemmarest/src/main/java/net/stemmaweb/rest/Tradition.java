package net.stemmaweb.rest;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.stream.XMLStreamException;

import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.services.*;
import net.stemmaweb.services.DatabaseService;

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
public class Tradition {
    private GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private GraphDatabaseService db = dbServiceProvider.getDatabase();

    /**
     * Delegated API calls
     */

    @Path("/{tradId}/witness/{sigil}")
    public Witness getWitness(@PathParam("tradId") String tradId, @PathParam("sigil") String sigil) {
        return new Witness(tradId, sigil);
    }
    @Path("/{tradId}/stemma/{name}")
    public Stemma getStemma(@PathParam("tradId") String tradId, @PathParam("name") String name) {
        return new Stemma(tradId, name);
    }
    @Path("/{tradId}/relation")
    public Relation getRelation(@PathParam("tradId") String tradId) {
        return new Relation(tradId);
    }

    /**
     * Resource creation calls
     */
    @PUT  // a new stemma
    @Path("{tradId}/stemma")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response newStemma(@PathParam("tradId") String tradId, String dot) {
        DotToNeo4JParser parser = new DotToNeo4JParser(db);
        return parser.importStemmaFromDot(dot, tradId, false);
    }
    @POST  // a replacement stemma TODO test
    @Path("{tradId}/stemma")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response replaceStemma(@PathParam("tradId") String tradId, String dot) {
        DotToNeo4JParser parser = new DotToNeo4JParser(db);
        return parser.importStemmaFromDot(dot, tradId, true);
    }

    /**
     * Collection retrieval calls
     */

    /**
     * Gets a list of all the witnesses of a tradition with the given id.
     *
     * @param tradId ID of the tradition to look up
     * @return Http Response 200 and a list of witness models in JSON on success
     *         or an ERROR in JSON format
     */
    @GET
    @Path("{tradId}/witnesses")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllWitnesses(@PathParam("tradId") String tradId) {
        Node traditionNode = getTraditionNode(tradId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity("tradition not found").build();

        ArrayList<WitnessModel> witnessList = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            DatabaseService.getRelated(traditionNode, ERelations.HAS_WITNESS).forEach(r -> witnessList.add(new WitnessModel(r)));
            tx.success();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(witnessList).build();
    }

    /**
     * Gets a list of all Stemmata available, as dot format
     *
     * @param tradId - the tradition whose stemmata to retrieve
     * @return Http Response ok and a list of DOT JSON strings on success or an
     *         ERROR in JSON format
     */
    @GET
    @Path("{tradId}/stemmata")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllStemmata(@PathParam("tradId") String tradId) {
        Node traditionNode = DatabaseService.getTraditionNode(tradId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity("No such tradition found").build();

        // find all stemmata associated with this tradition
        ArrayList<String> stemmata = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA)
                    .forEach(x -> stemmata.add(x.getProperty("name").toString()));
            tx.success();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        Neo4JToDotParser parser = new Neo4JToDotParser(db);
        ArrayList<String> stemmataList = new ArrayList<>();
        stemmata.forEach( stemma -> {
                        Response localResp = parser.parseNeo4JStemma(tradId, stemma);
                        stemmataList.add((String) localResp.getEntity());
                    });

        return Response.ok(stemmataList).build();
    }

    /**
     * Gets a list of all relationships of a tradition with the given id.
     *
     * @param tradId ID of the tradition to look up
     * @return Http Response 200 and a list of relationship model in JSON
     */
    @GET
    @Path("{tradId}/relationships")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllRelationships(@PathParam("tradId") String tradId) {
        ArrayList<RelationshipModel> relList = new ArrayList<>();

        Node startNode = DatabaseService.getStartNode(tradId, db);
        try (Transaction tx = db.beginTx()) {
            db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .uniqueness(Uniqueness.NODE_GLOBAL)
                    .traverse(startNode).nodes().forEach(
                    n -> n.getRelationships(ERelations.RELATED, Direction.OUTGOING).forEach(
                            r -> relList.add(new RelationshipModel(r)))
            );

            tx.success();
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(relList).build();
    }


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

        if (!DatabaseService.userExists(tradition.getOwnerId(), db)) {
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
     * Helper method for getting the tradition node with a given tradition id
     *
     * @param tradId ID of the tradition to look up
     * @param engine the graph database to query
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
     * @param tradId  ID of the tradition to look up
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
     * @param tradId ID of the tradition to delete
     * @return http response
     */
    @DELETE
    @Path("deletetradition/withid/{tradId}")
    public Response deleteTraditionById(@PathParam("tradId") String tradId) {
        Node foundTradition = DatabaseService.getTraditionNode(tradId, db);
        if (foundTradition != null) {
            try (Transaction tx = db.beginTx()) {
                /*
                 * Find all the nodes and relations to remove
                 */
                Set<Relationship> removableRelations = new HashSet<>();
                Set<Node> removableNodes = new HashSet<>();
                db.traversalDescription()
                        .depthFirst()
                        .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                        .relationships(ERelations.COLLATION, Direction.OUTGOING)
                        .relationships(ERelations.LEMMA_TEXT, Direction.OUTGOING)
                        .relationships(ERelations.HAS_END, Direction.OUTGOING)
                        .relationships(ERelations.HAS_WITNESS, Direction.OUTGOING)
                        .relationships(ERelations.HAS_STEMMA, Direction.OUTGOING)
                        .relationships(ERelations.HAS_ARCHETYPE, Direction.OUTGOING)
                        .relationships(ERelations.TRANSMITTED, Direction.OUTGOING)
                        .relationships(ERelations.RELATED, Direction.OUTGOING)
                        .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL)
                        .traverse(foundTradition)
                        .nodes().forEach(x -> {
                    x.getRelationships().forEach(removableRelations::add);
                    removableNodes.add(x);
                });

                /*
                 * Remove the nodes and relations
                 */
                removableRelations.forEach(Relationship::delete);
                removableNodes.forEach(Node::delete);
                tx.success();
            } catch (Exception e) {
                return Response.status(Status.INTERNAL_SERVER_ERROR).build();
            }
        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("A tradition with this id was not found!")
                    .build();
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



        if (!DatabaseService.userExists(userId, db)) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("Error: No user with this id exists")
                    .build();
        }

        return new GraphMLToNeo4JParser().parseGraphML(uploadedInputStream, userId, name);
    }

    /**
     * Returns DOT file from specified tradition owned by user
     *
     * @param tradId ID of the tradition to export
     * @return XML data
     */
    @GET
    @Path("getdot/fromtradition/{tradId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDot(@PathParam("tradId") String tradId) {
        if(getTraditionNode(tradId, db) == null)
            return Response.status(Status.NOT_FOUND).entity("No such tradition found").build();

        Neo4JToDotParser parser = new Neo4JToDotParser(db);
        return parser.parseNeo4J(tradId);
    }
}
