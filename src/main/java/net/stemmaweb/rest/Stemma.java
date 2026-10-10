package net.stemmaweb.rest;

import static net.stemmaweb.Util.jsonerror;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.stemmaweb.parser.StemmarestImportException;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import net.stemmaweb.model.StemmaModel;
import net.stemmaweb.parser.DotParser;
import net.stemmaweb.parser.NewickParser;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.NameConflictException;
import net.stemmaweb.services.VariantGraphService;

/**
 * Comprises all the api calls related to a stemma.
 * Can be called using <a href="http://BASE_URL/stemma">...</a>
 * @author PSE FS 2015 Team2
 */
public class Stemma {

    private final GraphDatabaseService db;
    private final String tradId;
    private final String ref;
    private final Boolean newCreated;

    public Stemma (String traditionId, String requestedName) {
        this(traditionId, requestedName, false);
    }

    public Stemma (String traditionId, String requestedName, Boolean created) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        tradId = traditionId;
        // The ref might be the stemma's numeric id, or it might be its name.
        ref = requestedName;
        newCreated = created;
    }

    /**
     * Resolves this stemma's path-segment reference (numeric id or name) to its node, among
     * the stemmata belonging to the tradition.
     *
     * @param tx the transaction within which we are working
     * @return the matching stemma node
     * @throws NotFoundException if no such tradition exists, or no stemma matches the reference
     * @throws IllegalArgumentException if the reference is a name shared by 2+ stemmata
     *         (only reachable for legacy data created before name uniqueness was enforced)
     */
    private Node resolveStemmaNode(Transaction tx) {
        Node tradNode = VariantGraphService.getTraditionNode(tx, tradId);
        if (tradNode == null)
            throw new NotFoundException(String.format("No tradition found with id %s", tradId));
        List<Node> candidates = DatabaseService.getRelated(tradNode, ERelations.HAS_STEMMA);
        return DatabaseService.resolveManagedRef(tx, Nodes.STEMMA, candidates, ref, "name");
    }

    /**
     * Fetches the information for the specified stemma.
     *
     * @return The stemma information, including its dot specification.
     */
    @GET
    @Produces("application/json; charset=utf-8")
    @Operation(
            tags = {"Stemma"},
            summary = "Get stemma",
            description = "Fetches the information for the specified stemma, including its dot specification.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "on success",
                            content = @Content(schema = @Schema(implementation = StemmaModel.class))
                    ),
                    @ApiResponse(
                            responseCode = "201",
                            description = "Stemma was created",
                            content = @Content(schema = @Schema(implementation = StemmaModel.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "if the stemma reference is a name shared by multiple stemmata (legacy data only); address the stemma by its numeric id instead",
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
        try (Transaction tx = db.beginTx()) {
            Node stemmaNode = resolveStemmaNode(tx);
            StemmaModel result = new StemmaModel(tx, stemmaNode);
            Status returncode = newCreated ? Status.CREATED : Status.OK;
            return Response.status(returncode).entity(result).build();
        } catch (NotFoundException e) {
            return Response.status(Status.NOT_FOUND)
                    .entity(jsonerror(String.format("No stemma %s found for tradition %s", ref, tradId))).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Status.BAD_REQUEST).entity(jsonerror(e.getMessage())).build();
        }
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
            tags = {"Stemma"},
            summary = "Replace or add new stemma",
            description = "Stores a new or updated stemma under the given name.",
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
                            description = "if the stemma reference is a name shared by multiple stemmata (legacy data only); address the stemma by its numeric id instead",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "if the submitted stemma specification fails validation (e.g. a witness not marked as hypothetical or extant, multiple archetype nodes found, or a DOT/Newick parse error)",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "if the tradition, or the existing stemma being replaced, is not found",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "if a stemma by this name already exists, or the requested witness hypothetical/extant status conflicts with an existing witness",
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
        if (stemmaSpec.getDot() == null && stemmaSpec.getNewick() == null) {
            // Metadata-only change - don't recreate the stemma contents
            try (Transaction tx = db.beginTx()) {
                Node stemmaNode = resolveStemmaNode(tx);
                Node tradNode = VariantGraphService.getTraditionNode(tx, tradId);
                String currentName = stemmaNode.getProperty("name").toString();
                String targetName = stemmaSpec.getName() != null ? stemmaSpec.getName() : currentName;
                if (DatabaseService.nameIsNumeric(targetName))
                    return Response.status(Status.BAD_REQUEST)
                            .entity(jsonerror("Stemma name may not be numeric: " + targetName)).build();
                DatabaseService.ensureNameUnique(tx, tradNode, ERelations.HAS_STEMMA, Nodes.STEMMA,
                        "name", targetName, stemmaNode);
                if (!targetName.equals(currentName)) {
                    // The "hypothesis" property on this stemma's TRANSMITTED relationships tags
                    // them as belonging to this stemma by name (see DotExporter); keep them in
                    // sync with the rename, or the stemma's own edges become unreachable.
                    Set<Relationship> transmitted = new HashSet<>();
                    for (Node witness : DatabaseService.getRelated(stemmaNode, ERelations.HAS_WITNESS))
                        transmitted.addAll(DatabaseService.getRelationships(
                                witness, Direction.BOTH, ERelations.TRANSMITTED));
                    for (Relationship r : transmitted)
                        if (currentName.equals(r.getProperty("hypothesis", null)))
                            r.setProperty("hypothesis", targetName);
                }
                stemmaNode.setProperty("name", targetName);
                StemmaModel result = new StemmaModel(tx, stemmaNode);
                tx.commit();
                return Response.ok(result).build();
            } catch (NotFoundException e) {
                return Response.status(Status.NOT_FOUND).build();
            } catch (NameConflictException e) {
                return Response.status(Status.CONFLICT).entity(jsonerror(e.getMessage())).build();
            } catch (IllegalArgumentException e) {
                return Response.status(Status.BAD_REQUEST).entity(jsonerror(e.getMessage())).build();
            }
        }

        // The stemma shape is changing. Wrap this entire thing in a transaction so that we can
        // roll back the deletion if the replacement import fails.
        try (Transaction tx = db.beginTx()) {
            Object preservedId = null;
            if (!this.newCreated) {
                Node existing = resolveStemmaNode(tx);
                // The replacement is a brand-new node, but it is still the same stemma as far
                // as the caller is concerned, so it keeps the existing stemma's id.
                preservedId = existing.getProperty("id", null);
                // In case the stemma spec doesn't have a name, assume it wants to keep the
                // name of the existing stemma being replaced -- not the raw ref, which may be
                // its numeric id rather than its name.
                if (stemmaSpec.getName() == null)
                    stemmaSpec.setName(existing.getProperty("name").toString());
                doStemmaDeletion(tx);
            } else if (stemmaSpec.getName() == null)
                stemmaSpec.setName(this.ref);

            if (stemmaSpec.getNewick() != null) {
                // We are importing a Newick tree; roleplay accordingly.
                NewickParser parser = new NewickParser(tx);
                parser.importStemmaFromNewick(tradId, stemmaSpec);
            } else {
                DotParser parser = new DotParser(tx);
                parser.importStemmaFromDot(tradId, stemmaSpec);
            }

            // Find the stemma we just imported. Both parsers enforce name uniqueness within
            // the tradition, and fill in stemmaSpec's name if it was missing, so the name
            // identifies it. Don't re-resolve this.ref - if it was the replaced stemma's
            // numeric id, it no longer exists until we restore it below.
            Node tradNode = VariantGraphService.getTraditionNode(tx, tradId);
            Node imported = DatabaseService.getRelated(tradNode, ERelations.HAS_STEMMA).stream()
                    .filter(x -> stemmaSpec.getName().equals(x.getProperty("name", null)))
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "Imported stemma " + stemmaSpec.getName() + " not found"));
            // The replaced node has already been deleted within this transaction, so its id is
            // free to reuse without colliding with the STEMMA id uniqueness constraint.
            if (preservedId != null)
                imported.setProperty("id", preservedId);
            StemmaModel result = new StemmaModel(tx, imported);
            tx.commit();
            return Response.status(this.newCreated ? Status.CREATED : Status.OK).entity(result).build();
        } catch (NotFoundException e) {
            return Response.status(Status.NOT_FOUND).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Status.BAD_REQUEST).entity(jsonerror(e.getMessage())).build();
        } catch (StemmarestImportException e) {
            e.printStackTrace();
            return Response.status(e.getStatus()).entity(jsonerror(e.getMessage())).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
    }


    /**
     * Deletes the stemma that is identified by the given name.
     *
     * @return The stemma information, including its dot specification.
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            tags = {"Stemma"},
            summary = "Delete stemma",
            description = "Deletes the stemma that is identified by the given name.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "on success",
                            content = @Content(schema = @Schema(implementation = StemmaModel.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "if the stemma reference is a name shared by multiple stemmata (legacy data only); address the stemma by its numeric id instead",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "if no such stemma exists for this tradition"
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "on failure, with an error message",
                            content = @Content(mediaType = "text/plain")
                    )
            }
    )
    public Response deleteStemma() {
        try (Transaction tx = db.beginTx()) {
            StemmaModel removed = doStemmaDeletion(tx);
            tx.commit();
            return Response.ok(removed).build();
        } catch (NotFoundException e) {
            return Response.status(Status.NOT_FOUND).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Status.BAD_REQUEST).entity(jsonerror(e.getMessage())).build();
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    private StemmaModel doStemmaDeletion(Transaction tx) {
        Node stemmaNode = resolveStemmaNode(tx);
        // The actual name, used to match TRANSMITTED relationships' "hypothesis" property
        // below -- not the raw ref, which may be the stemma's numeric id rather than its name.
        String stemmaName = stemmaNode.getProperty("name").toString();

        StemmaModel removed = new StemmaModel(tx, stemmaNode);
        Set<Relationship> removableRelations = new HashSet<>();
        Set<Node> removableNodes = new HashSet<>();

        // The stemma is removable
        removableNodes.add(stemmaNode);
        removableRelations.add(stemmaNode.getSingleRelationship(ERelations.HAS_STEMMA, Direction.INCOMING));

        // Its HAS_WITNESS relations are removable
        DatabaseService.getRelationships(stemmaNode, Direction.OUTGOING, ERelations.HAS_WITNESS)
                .forEach(x -> {
                    removableRelations.add(x);
                    removableNodes.add(x.getEndNode());
                });
        removableRelations.addAll(DatabaseService.getRelationships(
                stemmaNode, Direction.OUTGOING, ERelations.HAS_ARCHETYPE));

        // Its associated TRANSMISSION relations are removable
        removableNodes
                .forEach(n -> DatabaseService.getRelationships(n, Direction.BOTH, ERelations.TRANSMITTED)
                        .forEach(r -> {
                                    if (r.getProperty("hypothesis").equals(stemmaName))
                                        removableRelations.add(r);
                                }
                        ));

        // Its witnesses are removable if they have no links left
        removableRelations.forEach(Relationship::delete);
        removableNodes.stream().filter(x -> !x.hasRelationship()).forEach(Node::delete);
        return removed;
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
            tags = {"Stemma"},
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
                            description = "if the stemma reference is a name shared by multiple stemmata (legacy data only); address the stemma by its numeric id instead",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "if the witness does not occur in this stemma",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "409",
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

        try (Transaction tx = db.beginTx()) {
            // Get the stemma
            Node stemma;
            try {
                stemma = resolveStemmaNode(tx);
            } catch (NotFoundException e) {
                return Response.status(Status.NOT_FOUND).entity(jsonerror("No such witness found in stemma")).build();
            } catch (IllegalArgumentException e) {
                return Response.status(Status.BAD_REQUEST).entity(jsonerror(e.getMessage())).build();
            }

            // Check if the stemma has contamination. If so it can't be reoriented!
            if (stemma.hasProperty("is_contaminated"))
                return Response.status(Status.CONFLICT)
                        .entity(jsonerror("Contaminated stemma cannot be reoriented")).build();

            // Find the requested archetype witness among the stemma's witnesses
            Node archetype = null;
            for (Node witness : DatabaseService.getRelated(stemma, ERelations.HAS_WITNESS)) {
                if (nodeId.equals(witness.getProperty("sigil", null))) {
                    archetype = witness;
                    break;
                }
            }
            if (archetype == null)
                return Response.status(Status.NOT_FOUND).entity(jsonerror("No such witness found in stemma")).build();

            // Delete its current HAS_ARCHETYPE, if any
            Relationship currentArchetype = stemma.getSingleRelationship(ERelations.HAS_ARCHETYPE, Direction.OUTGOING);
            if (currentArchetype != null)
                currentArchetype.delete();

            // Set the new archetype
            stemma.createRelationshipTo(archetype, ERelations.HAS_ARCHETYPE);
            // and make sure the stemma is directed.
            stemma.setProperty("directed", true);
            StemmaModel result = new StemmaModel(tx, stemma);
            tx.commit();
            return Response.ok(result).build();
        }
    }

}