package net.stemmaweb.rest;

import java.util.ArrayList;
import java.util.Map;

import javax.ws.rs.*;
import javax.ws.rs.Path;
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
public class Stemma {

    private GraphDatabaseService db;
    private String tradId;
    private String name;

    public Stemma (String traditionId, String requestedName) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        tradId = traditionId;
        name = requestedName;
    }

    /**
     * Returns JSON string with a Stemma of a tradition in DOT format
     *
     * @return Http Response ok and DOT JSON string on success or an ERROR in
     *         JSON format
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStemma() {

        Neo4JToDotParser parser = new Neo4JToDotParser(db);
        return parser.parseNeo4JStemma(tradId, name);
    }

    @DELETE  // TODO implement
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteStemma() {
        return Response.status(Status.SERVICE_UNAVAILABLE).build();
    }
    /**
     * Reorients a stemma tree with a given new root node
     *
     * @param tradId
     * @param name
     * @param nodeId
     * @return Http Response ok and DOT JSON string on success or an ERROR in
     *         JSON format
     */
    @POST
    @Path("reorient/{nodeId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response reorientStemma(@PathParam("tradId") String tradId,
                                   @PathParam("name") String name,
                                   @PathParam("nodeId") String nodeId) {

        Response resp;

        try (Transaction tx = db.beginTx())
        {
            // Get the stemma and the witness
            Result foundStemma = db.execute("match (:TRADITION {id:'" + tradId
                    + "'})-[:HAS_STEMMA]->(s:STEMMA {name:'" + name
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
        return getStemma();

    }

}