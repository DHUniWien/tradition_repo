package net.stemmaweb.rest;

import java.util.ArrayList;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.DotToNeo4JParser;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.Neo4JToDotParser;

import org.neo4j.graphdb.*;

/**
 * Comprises all the api calls related to a stemma.
 * Can be called using http://BASE_URL/stemma
 * @author PSE FS 2015 Team2
 */
@Path("/stemma")
public class Stemma implements IResource {

    private GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private GraphDatabaseService db = dbServiceProvider.getDatabase();

    /**
     * Gets a list of all Stemmata available, as dot format
     *
     * @param tradId
     * @return Http Response ok and a list of DOT JSON strings on success or an
     *         ERROR in JSON format
     */
    @GET
    @Path("getallstemmata/fromtradition/{tradId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllStemmata(@PathParam("tradId") String tradId) {
        Node traditionNode = DatabaseService.getTraditionNode(tradId, db);
        // make sure that the node exists
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity("No such tradition found").build();

        // find all stemmata associated with this tradition
        ArrayList<Node> stemmata = DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA);
        Neo4JToDotParser parser = new Neo4JToDotParser(db);
        ArrayList<String> stemmataList = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            for (Node stemma : stemmata) {
                System.out.println(stemma.getProperty("name"));
                Response localResp = parser.parseNeo4JStemma(tradId, stemma.getProperty("name")
                        .toString());
                String dot = (String) localResp.getEntity();
                stemmataList.add(dot);
            }
        }

        return Response.ok(stemmataList).build();
    }

    /**
     * Returns JSON string with a Stemma of a tradition in DOT format
     *
     * @param tradId
     * @param stemmaTitle
     * @return Http Response ok and DOT JSON string on success or an ERROR in
     *         JSON format
     */
    @GET
    @Path("getstemma/fromtradition/{tradId}/withtitle/{stemmaTitle}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStemma(@PathParam("tradId") String tradId,@PathParam("stemmaTitle") String stemmaTitle) {

        Neo4JToDotParser parser = new Neo4JToDotParser(db);
        return parser.parseNeo4JStemma(tradId, stemmaTitle);
    }

    /**
     * Puts the Stemma of a DOT file in the database
     *
     * @param tradId
     * @return Http Response ok and DOT JSON string on success or an ERROR in
     *         JSON format
     */
    @POST
    @Path("newstemma/intradition/{tradId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setStemma(@PathParam("tradId") String tradId, String dot) {
        DotToNeo4JParser parser = new DotToNeo4JParser(db);
        return parser.importStemmaFromDot(dot, tradId, false);
    }

    /**
     * Reorients a stemma tree with a given new root node
     *
     * @param tradId
     * @param stemmaTitle
     * @param nodeId
     * @return Http Response ok and DOT JSON string on success or an ERROR in
     *         JSON format
     */
    @POST
    @Path("reorientstemma/fromtradition/{tradId}/withtitle/{stemmaTitle}/withnewrootnode/{nodeId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response reorientStemma(@PathParam("tradId") String tradId,
                                   @PathParam("stemmaTitle") String stemmaTitle,
                                   @PathParam("nodeId") String nodeId) {

        Response resp;

        try (Transaction tx = db.beginTx())
        {
            // Get the stemma and the witness
            Result foundStemma = db.execute("match (:TRADITION {id:'" + tradId
                    + "'})-[:HAS_STEMMA]->(s:STEMMA {name:'" + stemmaTitle
                    + "'})-[:HAS_WITNESS]->(w:WITNESS {sigil:'" + nodeId + "'}) return s, w");
            if(!foundStemma.hasNext()) {
                return Response.status(Status.NOT_FOUND).build();
            }
            Map<String, Object> queryRow = foundStemma.next();
            Node stemma    = (Node) queryRow.get("s");
            Node archetype = (Node) queryRow.get("w");

            // Delete its current HAS_ARCHETYPE, if any
            Relationship currentArchetype = stemma.getSingleRelationship(ERelations.HAS_ARCHETYPE, Direction.OUTGOING);
            if (currentArchetype != null)
                currentArchetype.delete();

            // Set the new archetype
            stemma.createRelationshipTo(archetype, ERelations.HAS_ARCHETYPE);
            // and make sure the stemma is directed.
            stemma.setProperty("directed", true);

        tx.success();
        }
        resp = getStemma(tradId, stemmaTitle);
        return resp;

    }

}