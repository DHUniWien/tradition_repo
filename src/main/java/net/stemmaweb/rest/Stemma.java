package net.stemmaweb.rest;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import net.stemmaweb.model.StemmaModel;
import net.stemmaweb.parser.DotParser;
import net.stemmaweb.parser.NewickParser;
import net.stemmaweb.services.GraphDatabaseServiceProvider;

import org.neo4j.graphdb.*;

import static net.stemmaweb.Util.jsonerror;

/**
 * Comprises all the api calls related to a stemma.
 * Can be called using {@code http://BASE_URL/stemma}
 * @author PSE FS 2015 Team2
 */
public class Stemma {

    private final GraphDatabaseService db;
    private final String tradId;
    private final String name;
    private final Boolean newCreated;

    public Stemma (String traditionId, String requestedName) {
        this(traditionId, requestedName, false);
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
     * @return The stemma information, including its dot specification.
     */
    @GET
    @Produces("application/json; charset=utf-8")
    @Operation(
            summary = "Get stemma",
            description = "Fetches the information for the specified stemma, including its dot specification.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "On success",
                            content = @Content(schema = @Schema(implementation = StemmaModel.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "If no such tradition exists",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "On failure, with an error message",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    )
            }
    )
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
     * @param stemmaSpec - A StemmaModel containing the new or replacement stemma.
     * @return The stemma information, including its dot specification.
     */
    @PUT  // a replacement stemma
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("application/json; charset=utf-8")
    @Operation(
            summary = "Replace or add new stemma",
            description = "Stores a new or updated stemma under the given name.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "On success, if stemma is updated",
                            content = @Content(schema = @Schema(implementation = StemmaModel.class))
                    ),
                    @ApiResponse(
                            responseCode = "201",
                            description = "On success, if stemma is new",
                            content = @Content(schema = @Schema(implementation = StemmaModel.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "If the stemma name in the URL doesn't match the name in the JSON information",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "If no such tradition exists",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "On failure, with an error message",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    )
            }
    )
    public Response replaceStemma(StemmaModel stemmaSpec) {
        // In case the stemma spec doesn't have a name, assume it wants the name in the URL just called
        if (stemmaSpec.getIdentifier() == null)
            stemmaSpec.setIdentifier(this.name);
        // Wrap this entire thing in a transaction so that we can roll back
        // the deletion if the replacement import fails.
        try (Transaction tx = db.beginTx()) {
            if (!this.newCreated) {
                Response deletionResult = deleteStemma();
                if (deletionResult.getStatus() != 200)
                    return deletionResult;
            }

            Response replaceResult;
            if (stemmaSpec.getNewick() != null) {
                // We are importing a Newick tree; roleplay accordingly.
                NewickParser parser = new NewickParser(db);
                replaceResult = parser.importStemmaFromNewick(tradId, stemmaSpec);
            } else {
                DotParser parser = new DotParser(db);
                replaceResult = parser.importStemmaFromDot(tradId, stemmaSpec);
            }
            if (replaceResult.getStatus() != 201)
                return replaceResult;

            // OK, we can commit it.
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        // Return the stemma that has been PUT under this name.
        return this.getStemma();
    }


    /**
     * Deletes the stemma that is identified by the given name.
     *
     * @return The stemma information, including its dot specification.
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            summary = "Delete stemma",
            description = "Deletes the stemma that is identified by the given name.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "On success, if stemma is updated",
                            content = @Content(schema = @Schema(implementation = StemmaModel.class))
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "On failure, with an error message",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    )
            }
    )
    public Response deleteStemma() {
        Node stemmaNode = getStemmaNode();
        if (stemmaNode == null)
            return Response.status(Status.NOT_FOUND).build();
        StemmaModel removed = new StemmaModel(stemmaNode);
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
            return Response.ok(removed).build();
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
     */
    @POST
    @Path("reorient/{nodeId}")
    @Produces("application/json; charset=utf-8")
    @Operation(
            summary = "Reorient stemma",
            description = "Reorients a stemma tree so that the given witness node is the root (archetype). This operation can only be performed on a stemma without contamination links.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "On success, if stemma is updated",
                            content = @Content(schema = @Schema(implementation = StemmaModel.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "If the witness does not occur in this stemma",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "412",
                            description = "If the stemma is contaminated",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "On failure, with an error message",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    )
            }
    )
    public Response reorientStemma(@PathParam("nodeId") String nodeId) {

        try (Transaction tx = db.beginTx())
        {
            // Get the stemma and the witness
            Node stemma;
            Node archetype;
            try (Result foundStemma = db.execute("match (:TRADITION {id:'" + tradId
                    + "'})-[:HAS_STEMMA]->(s:STEMMA {name:'" + name
                    + "'})-[:HAS_WITNESS]->(w:WITNESS {sigil:'" + nodeId + "'}) return s, w")) {
                if (!foundStemma.hasNext())
                    return Response.status(Status.NOT_FOUND).entity(jsonerror("No such witness found in stemma")).build();

                // Fish the stemma and requested archetype out of the query
                Map<String, Object> queryRow = foundStemma.next();
                stemma = (Node) queryRow.get("s");
                archetype = (Node) queryRow.get("w");
            }

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
            try (Result query = db.execute("match (:TRADITION {id:'" + tradId
                    + "'})-[:HAS_STEMMA]->(s:STEMMA {name:'" + name + "'}) return s")) {
                ResourceIterator<Node> foundStemma = query.columnAs("s");
                tx.success();
                if (!foundStemma.hasNext())
                    return null;
                return foundStemma.next();
            }
        }
    }

}