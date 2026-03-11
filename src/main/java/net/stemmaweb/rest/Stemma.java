package net.stemmaweb.rest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.alexmerz.graphviz.ParseException;
import net.stemmaweb.model.StemmaModel;
import net.stemmaweb.parser.DotParser;
import net.stemmaweb.parser.NewickParser;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;

import org.json.JSONObject;
import org.neo4j.graphdb.*;

import static net.stemmaweb.Util.jsonerror;

/**
 * Comprises all the api calls related to a stemma.
 * Can be called using <a href="http://BASE_URL/stemma">...</a>
 * @author PSE FS 2015 Team2
 */
public class Stemma {

    private final GraphDatabaseService db;
    private final String tradId;
    /**
     * The reference passed in the URL: either a numeric stemmaid or a legacy name string.
     */
    private final String stemmaRef;
    private final Boolean newCreated;

    /**
     * Resolved node ID and name, populated by getStemmaNode().
     */
    private Long resolvedStemmaid = null;
    private String resolvedName = null;

    public Stemma (String traditionId, String stemmaRef) {
        this(traditionId, stemmaRef, false);
    }

    public Stemma (String traditionId, String stemmaRef, Boolean created) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        tradId = traditionId;
        this.stemmaRef = stemmaRef;
        newCreated = created;
    }

    /**
     * Thrown when a name-based lookup matches multiple stemmata in the same tradition.
     */
    static class AmbiguousStemmaNameException extends RuntimeException {
        AmbiguousStemmaNameException(String message) { super(message); }
    }

    /**
     * Fetches the information for the specified stemma.
     *
     * @title Get stemma
     * @return The stemma information, including its dot specification.
     * @statuscode 200 - on success
     * @statuscode 400 - if the stemma reference is a name shared by multiple stemmata
     * @statuscode 404 - if no such stemma exists for this tradition
     * @statuscode 500 - on failure, with an error message
     */
    @GET
    @Produces("application/json; charset=utf-8")
    @Operation(
            summary = "Get stemma",
            description = "Fetches the information for the specified stemma, including its dot specification.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "on success",
                            content = @Content(schema = @Schema(implementation = StemmaModel.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "if the stemma reference is a name shared by multiple stemmata",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "if no such stemma exists for this tradition",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "on failure, with an error message",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    )
            }
    )
    public Response getStemma() {
        Node stemmaNode;
        try {
            stemmaNode = getStemmaNode();
        } catch (AmbiguousStemmaNameException e) {
            return Response.status(Status.BAD_REQUEST).entity(jsonerror(e.getMessage())).build();
        }
        if (stemmaNode == null) {
            return Response.status(Status.NOT_FOUND)
                    .entity(jsonerror(String.format("No stemma '%s' found for tradition %s", stemmaRef, tradId))).build();
        }
        StemmaModel result = new StemmaModel(stemmaNode);
        Status returncode = newCreated ? Status.CREATED : Status.OK;
        return Response.status(returncode).entity(result).build();
    }

    /**
     * Stores a new or updated stemma under the given ID (either name or number). The name of the
     * resulting stemma will be taken from, in order:
     * - a name given in the stemmaSpec
     * - the name given in the URL, if any
     * - the name specified in the dot spec, if any
     * - the existing name of the stemma in question.
     *
     * @title Replace or add new stemma
     * @param stemmaSpec - A StemmaModel containing the new or replacement stemma.
     * @return The stemma information, including its dot specification.
     * @statuscode 200 - on success, if stemma is updated
     * @statuscode 201 - on success, if stemma is new
     * @statuscode 400 - if the stemma reference is a name shared by multiple stemmata
     * @statuscode 404 - if no such tradition exists
     * @statuscode 500 - on failure, with an error message
     */
    @PUT  // a replacement stemma
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("application/json; charset=utf-8")
    @Operation(
            summary = "Replace or add new stemma",
            description = "Stores a new or updated stemma under the given ID (either name or number).",
            requestBody = @RequestBody(
                    description = "A StemmaModel containing the new or replacement stemma",
                    required = true,
                    content = @Content(schema = @Schema(implementation = StemmaModel.class))
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "on success, if stemma is updated",
                            content = @Content(schema = @Schema(implementation = StemmaModel.class))
                    ),
                    @ApiResponse(
                            responseCode = "201",
                            description = "on success, if stemma is new",
                            content = @Content(schema = @Schema(implementation = StemmaModel.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "if the stemma reference is a name shared by multiple stemmata",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "if no such tradition exists",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "on failure, with an error message",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    )
            }
    )
    public Response replaceStemma(StemmaModel stemmaSpec) {
        // Resolve the existing stemma node if we have it.
        Node existingNode;
        try {
            existingNode = getStemmaNode();
        } catch (AmbiguousStemmaNameException e) {
            return Response.status(Status.BAD_REQUEST).entity(jsonerror(e.getMessage())).build();
        }

        // Resolve the stemma name to use, in priority order:
        // (1) explicit name in the model, (2) URL name reference if it was a string,
        // (3) name extracted from the DOT spec, (4) existing stemma name.
        if (stemmaSpec.getName() == null) {
            boolean stemmaRefIsName;
            try { Long.parseLong(stemmaRef); stemmaRefIsName = false; }
            catch (NumberFormatException e) { stemmaRefIsName = true; }

            if (stemmaRefIsName && resolvedName != null) {
                stemmaSpec.setName(resolvedName);
            } else if (stemmaSpec.getDot() != null) {
                try {
                    String dotName = DotParser.getDotGraphName(stemmaSpec.getDot());
                    if (dotName != null) stemmaSpec.setName(dotName);
                } catch (ParseException ignored) { /* will fail properly in the parser */ }
            }
            // Final fallback: Newick by numeric ID with no name → use existing name
            if (stemmaSpec.getName() == null && resolvedName != null)
                stemmaSpec.setName(resolvedName);
        }

        String newStemmaRef;
        try (Transaction tx = db.beginTx()) {
            if (this.newCreated) {
                // If we are asking explicitly to create a new stemma node, then don't touch the existing one.
                existingNode = null;
            } else {
                // Delete the old stemma contents, but keep the node for its ID.
                Response deletionResult = deleteStemmaContents(existingNode, false);
                if (deletionResult.getStatus() != 200)
                    return deletionResult;
            }

            Response replaceResult;
            if (stemmaSpec.getNewick() != null) {
                NewickParser parser = new NewickParser(db);
                replaceResult = parser.importStemmaFromNewick(tradId, stemmaSpec, existingNode);
            } else {
                DotParser parser = new DotParser(db);
                replaceResult = parser.importStemmaFromDot(tradId, stemmaSpec, existingNode);
            }
            if (replaceResult.getStatus() != 201)
                return replaceResult;

            // Parse the stemmaid of the newly created stemma from the parse response.
            JSONObject responseJson = new JSONObject(replaceResult.getEntity().toString());
            newStemmaRef = String.valueOf(responseJson.getLong("stemmaid"));
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        // Return the newly created stemma by its stemmaid, preserving the creation status code.
        return new Stemma(tradId, newStemmaRef, this.newCreated).getStemma();
    }


    /**
     * Deletes the stemma that is identified by the given identifier or name.
     *
     * @title Delete stemma
     * @return The stemma information, including its dot specification.
     * @statuscode 200 - on success
     * @statuscode 400 - if the stemma reference is a name shared by multiple stemmata
     * @statuscode 404 - if no such stemma exists for this tradition
     * @statuscode 500 - on failure, with an error message
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            summary = "Delete stemma",
            description = "Deletes the stemma that is identified by the given identifier or name.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "on success",
                            content = @Content(schema = @Schema(implementation = StemmaModel.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "if the stemma reference is a name shared by multiple stemmata",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "if no such stemma exists for this tradition"
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "on failure, with an error message",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    )
            }
    )
    public Response deleteStemma() {
        Node stemmaNode;
        try {
            stemmaNode = getStemmaNode();
        } catch (AmbiguousStemmaNameException e) {
            return Response.status(Status.BAD_REQUEST).entity(jsonerror(e.getMessage())).build();
        }
        if (stemmaNode == null)
            return Response.status(Status.NOT_FOUND).build();
        return deleteStemmaContents(stemmaNode, true);
    }

    /**
     * Deletes the contents of a stemma, and deletes the stemma node itself if requested.
     *
     * @param stemmaNode - the Neo4J node representing the stemma
     * @param deleteStemmaItself - whether to delete the stemma node itself
     * @return the Response object that can be passed back to the client
     */
    private Response deleteStemmaContents(Node stemmaNode, Boolean deleteStemmaItself) {
        StemmaModel removed = new StemmaModel(stemmaNode);
        String hypothesisValue = String.valueOf(stemmaNode.getId());
        try (Transaction tx = db.beginTx()) {
            Set<Relationship> removableRelations = new HashSet<>();
            Set<Node> removableNodes = new HashSet<>();

            // The stemma is removable if we were asked to do it
            if (deleteStemmaItself) {
                removableNodes.add(stemmaNode);
                removableRelations.add(stemmaNode.getSingleRelationship(ERelations.HAS_STEMMA, Direction.INCOMING));
            }

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
                                        if (r.getProperty("hypothesis").equals(hypothesisValue))
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
     * @statuscode 200 - on success, if stemma is updated
     * @statuscode 400 - if the stemma reference is a name shared by multiple stemmata
     * @statuscode 404 - if the witness does not occur in this stemma
     * @statuscode 412 - if the stemma is contaminated
     * @statuscode 500 - on failure, with an error message
     */
    @POST
    @Path("reorient/{nodeId}")
    @Produces("application/json; charset=utf-8")
    @Operation(
            summary = "Reorient stemma",
            description = "Reorients a stemma tree so that the given witness node is the root (archetype). This operation can only be performed on a stemma without contamination links.",
            parameters = {
                    @Parameter(name = "nodeId", description = "The ID of the witness node to use as the new root/archetype.", required = true, in = ParameterIn.PATH, schema = @Schema(type = "string"))
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "on success",
                            content = @Content(schema = @Schema(implementation = StemmaModel.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "if the stemma reference is a name shared by multiple stemmata",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "if the witness does not occur in this stemma",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "412",
                            description = "if the stemma is contaminated",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "on failure, with an error message",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    )
            }
    )
    public Response reorientStemma(@PathParam("nodeId") String nodeId) {
        Node stemmaNode;
        try {
            stemmaNode = getStemmaNode();
        } catch (AmbiguousStemmaNameException e) {
            return Response.status(Status.BAD_REQUEST).entity(jsonerror(e.getMessage())).build();
        }
        if (stemmaNode == null)
            return Response.status(Status.NOT_FOUND).entity(jsonerror("No such stemma found")).build();

        try (Transaction tx = db.beginTx())
        {
            // Find the requested witness among this stemma's witnesses
            Node archetype = null;
            for (Relationship hw : stemmaNode.getRelationships(ERelations.HAS_WITNESS, Direction.OUTGOING)) {
                Node witness = hw.getEndNode();
                if (witness.getProperty("sigil").equals(nodeId)) {
                    archetype = witness;
                    break;
                }
            }
            if (archetype == null)
                return Response.status(Status.NOT_FOUND).entity(jsonerror("No such witness found in stemma")).build();

            // Check if the stemma has contamination. If so it can't be reoriented!
            if (stemmaNode.hasProperty("is_contaminated"))
                return Response.status(Status.PRECONDITION_FAILED)
                        .entity(jsonerror("Contaminated stemma cannot be reoriented")).build();

            // Delete its current HAS_ARCHETYPE, if any
            Relationship currentArchetype = stemmaNode.getSingleRelationship(ERelations.HAS_ARCHETYPE, Direction.OUTGOING);
            if (currentArchetype != null)
                currentArchetype.delete();

            // Set the new archetype
            stemmaNode.createRelationshipTo(archetype, ERelations.HAS_ARCHETYPE);
            // and make sure the stemma is directed.
            stemmaNode.setProperty("directed", true);

            tx.success();
        }
        return getStemma();
    }

    /**
     * Resolves {@code stemmaRef} to a Neo4j Node.
     *
     * <p>If {@code stemmaRef} is numeric it is treated as a stemmaid (Neo4j node ID) and looked up
     * directly. Otherwise it is treated as a stemma name; if the name matches exactly one stemma in
     * this tradition the TRANSMITTED relationship {@code hypothesis} values are migrated from the
     * legacy name-based format to the stemmaid-based format (if not already done), and the node is
     * returned. If the name matches more than one stemma an {@link AmbiguousStemmaNameException} is
     * thrown so that callers can return an appropriate 400 response.
     *
     * @return the STEMMA node, or {@code null} if not found
     * @throws AmbiguousStemmaNameException if a name lookup matches multiple stemmata
     */
    private Node getStemmaNode() throws AmbiguousStemmaNameException {
        // Return cached result if already resolved.
        if (resolvedStemmaid != null) {
            try (Transaction tx = db.beginTx()) {
                Node node = db.getNodeById(resolvedStemmaid);
                tx.success();
                return node;
            }
        }

        // Try stemmaid-based lookup first (numeric stemmaRef).
        try {
            long stemmaid = Long.parseLong(stemmaRef);
            try (Transaction tx = db.beginTx()) {
                Node traditionNode = db.findNode(Nodes.TRADITION, "id", tradId);
                if (traditionNode == null) { tx.success(); return null; }
                for (Node s : DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA)) {
                    if (s.getId() == stemmaid) {
                        resolvedStemmaid = stemmaid;
                        resolvedName = s.getProperty("name").toString();
                        tx.success();
                        return s;
                    }
                }
                tx.success();
                return null;
            }
        } catch (NumberFormatException e) {
            // stemmaRef is not numeric — fall through to name-based lookup.
        }

        // Name-based lookup (backward compatibility).
        ArrayList<Node> matching = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            Node traditionNode = db.findNode(Nodes.TRADITION, "id", tradId);
            if (traditionNode != null) {
                for (Node s : DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA)) {
                    if (s.getProperty("name").equals(stemmaRef))
                        matching.add(s);
                }
            }
            tx.success();
        }

        if (matching.isEmpty()) return null;
        if (matching.size() > 1)
            throw new AmbiguousStemmaNameException(
                    String.format("Multiple stemmata named '%s' exist in tradition %s; use the stemmaid to disambiguate.", stemmaRef, tradId));

        Node node = matching.get(0);
        resolvedStemmaid = node.getId();
        resolvedName = stemmaRef;

        // Migrate legacy name-based hypothesis values to stemmaid-based values.
        String stemmaidStr = String.valueOf(resolvedStemmaid);
        try (Transaction tx = db.beginTx()) {
            for (Relationship hw : node.getRelationships(ERelations.HAS_WITNESS, Direction.OUTGOING)) {
                for (Relationship transmitted : hw.getEndNode().getRelationships(ERelations.TRANSMITTED, Direction.BOTH)) {
                    if (transmitted.getProperty("hypothesis").equals(stemmaRef))
                        transmitted.setProperty("hypothesis", stemmaidStr);
                }
            }
            tx.success();
        }

        return node;
    }

}