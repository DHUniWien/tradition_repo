package net.stemmaweb.rest;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.qmino.miredot.annotations.ReturnType;
import net.stemmaweb.model.StemmaModel;
import net.stemmaweb.parser.DotParser;
import net.stemmaweb.services.GraphDatabaseServiceProvider;

import org.json.JSONObject;
import org.neo4j.graphdb.*;

import static net.stemmaweb.rest.Util.jsonerror;

/**
 * Comprises all the api calls related to a stemma.
 * Can be called using http://BASE_URL/stemma
 * @author PSE FS 2015 Team2
 */
public class Stemma {

    private GraphDatabaseService db;
    private String tradId;
    private String name;
    private Boolean newCreated;

    public Stemma (String traditionId, String requestedName) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        tradId = traditionId;
        name = requestedName;
        newCreated = false;
    }

    public Stemma (String traditionId, String requestedName, Boolean created) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        tradId = traditionId;
        name = requestedName;
        newCreated = created;
    }

    /**
     * Fetches the information for the specified stemma.
     *
     * @summary Get stemma
     * @return The stemma information, including its dot specification.
     * @statuscode 200 - on success
     * @statuscode 404 - if no such tradition exists
     * @statuscode 500 - on failure, with an error message
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = StemmaModel.class)
    public Response getStemma() {
        Node stemmaNode = getStemmaNode();
        if (stemmaNode == null) {
            return Response.status(Status.NOT_FOUND)
                    .entity(jsonerror(String.format("No stemma %s found for tradition %s", name, tradId))).build();
        }
        StemmaModel result = new StemmaModel(stemmaNode);
        Status returncode = newCreated ? Status.CREATED : Status.OK;
        return Response.status(returncode).entity(result).build();
    }

    /**
     * Stores a new or updated stemma under the given name.
     *
     * @summary Replace or add new stemma
     * @param dot - The string specification of the new or replacement stemma topology.
     * @return The stemma information, including its dot specification.
     * @statuscode 200 - on success, if stemma is updated
     * @statuscode 201 - on success, if stemma is new
     * @statuscode 400 - if the stemma name in the URL doesn't match the name in the JSON information
     * @statuscode 404 - if no such tradition exists
     * @statuscode 500 - on failure, with an error message
     */
    @PUT  // a replacement stemma
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = StemmaModel.class)
    public Response replaceStemma(String dot) {
        DotParser parser = new DotParser(db);
        // Wrap this entire thing in a transaction so that we can roll back
        // the deletion if the replacement import fails.
        try (Transaction tx = db.beginTx()) {
            String originalName = name;
            Response deletionResult = deleteStemma();
            if (deletionResult.getStatus() != 200)
                return deletionResult;

            Response replaceResult = parser.importStemmaFromDot(dot, tradId);
            if (replaceResult.getStatus() != 201)
                return replaceResult;

            // Check that the names matched.
            JSONObject content = new JSONObject(replaceResult.getEntity().toString());
            if (!content.get("name").equals(originalName)) {
                return Response.status(Status.BAD_REQUEST)
                        .entity(jsonerror("Name mismatch between original and replacement stemma")).build();
            }

            // OK, we can commit it.
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return this.getStemma();
    }


    /**
     * Deletes the stemma that is identified by the given name.
     *
     * @summary Delete stemma
     * @return The stemma information, including its dot specification.
     * @statuscode 200 - on success, if stemma is updated
     * @statuscode 500 - on failure, with an error message
     */
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    @ReturnType("java.lang.Void")
    public Response deleteStemma() {
        Node stemmaNode = getStemmaNode();
        assert stemmaNode != null;
        try (Transaction tx = db.beginTx()) {
            Set<Relationship> removableRelations = new HashSet<>();
            Set<Node> removableNodes = new HashSet<>();

            // The stemma is removable
            removableNodes.add(stemmaNode);
            removableRelations.add(stemmaNode.getSingleRelationship(ERelations.HAS_STEMMA, Direction.INCOMING));

            // Its HAS_WITNESS relations are removable
            stemmaNode.getRelationships(Direction.OUTGOING, ERelations.HAS_WITNESS)
                .forEach(x -> {
                    removableRelations.add(x);
                    removableNodes.add(x.getEndNode());
                });
            stemmaNode.getRelationships(Direction.OUTGOING, ERelations.HAS_ARCHETYPE)
                    .forEach(removableRelations::add);

            // Its associated TRANSMISSION relations are removable
            removableNodes
                    .forEach(n -> n.getRelationships(ERelations.TRANSMITTED, Direction.BOTH)
                            .forEach(r -> {
                                        if (r.getProperty("hypothesis").equals(name))
                                            removableRelations.add(r);
                                    }
                            ));

            // Its witnesses are removable if they have no links left
            removableRelations.forEach(Relationship::delete);
            removableNodes.stream().filter(x -> !x.hasRelationship()).forEach(Node::delete);
            tx.success();
            return Response.ok().build();
        } catch (Exception e ){
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    /**
     * Reorients a stemma tree so that the given witness node is the root (archetype). This operation
     * can only be performed on a stemma without contamination links.
     *
     * @param nodeId - archetype node
     * @return The updated stemma model
     * @statuscode 200 - on success, if stemma is updated
     * @statuscode 404 - if the witness does not occur in this stemma
     * @statuscode 412 - if the stemma is contaminated
     * @statuscode 500 - on failure, with an error message
     */
    @POST
    @Path("reorient/{nodeId}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response reorientStemma(@PathParam("nodeId") String nodeId) {

        try (Transaction tx = db.beginTx())
        {
            // Get the stemma and the witness
            Result foundStemma = db.execute("match (:TRADITION {id:'" + tradId
                    + "'})-[:HAS_STEMMA]->(s:STEMMA {name:'" + name
                    + "'})-[:HAS_WITNESS]->(w:WITNESS {sigil:'" + nodeId + "'}) return s, w");
            if(!foundStemma.hasNext())
                return Response.status(Status.NOT_FOUND).entity(jsonerror("No such witness found in stemma")).build();

            // Fish the stemma and requested archetype out of the query
            Map<String, Object> queryRow = foundStemma.next();
            Node stemma    = (Node) queryRow.get("s");
            Node archetype = (Node) queryRow.get("w");

            // Check if the stemma has contamination. If so it can't be reoriented!
            if (stemma.hasProperty("is_contaminated"))
                return Response.status(Status.PRECONDITION_FAILED)
                        .entity(jsonerror("Contaminated stemma cannot be reoriented")).build();

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

    private Node getStemmaNode () {
        try (Transaction tx = db.beginTx()) {
            Result query = db.execute("match (:TRADITION {id:'" + tradId
                    + "'})-[:HAS_STEMMA]->(s:STEMMA {name:'" + name + "'}) return s");
            ResourceIterator<Node> foundStemma = query.columnAs("s");
            tx.success();
            if (!foundStemma.hasNext())
                return null;
            return foundStemma.next();
        }
    }

}