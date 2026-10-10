package net.stemmaweb.rest;

import static net.stemmaweb.Util.jsonerror;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.neo4j.graphdb.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.TextSequenceModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.parser.Util;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.NameConflictException;
import net.stemmaweb.services.ReadingService;
import net.stemmaweb.services.VariantGraphService;

/**
 * Comprises all the API calls related to a witness.
 * Can be called using <a href="http://BASE_URL/witness">...</a>
 * @author PSE FS 2015 Team2
 */

public class Witness {

    private final GraphDatabaseService db;
    private final String tradId;
    private String ref;
    private String sectId;

    public Witness (String traditionId, String requestedSigil) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        tradId = traditionId;
        // The ref might be the witness's numeric id, or it might be its sigil.
        ref = requestedSigil;
        sectId = null;
    }

    public Witness (String traditionId, String sectionId, String requestedSigil) {
        this(traditionId, requestedSigil);
        sectId = sectionId;
    }

    /**
     * Resolves this witness's path-segment reference (numeric id or sigil) to its node,
     * among the witnesses belonging to the tradition.
     *
     * @param tx the transaction within which we are working
     * @return the matching witness node
     * @throws NotFoundException if no such tradition exists, or no witness matches the reference
     * @throws IllegalArgumentException if the reference is a sigil shared by 2+ witnesses
     *         (should be unreachable!)
     */
    private Node resolveWitnessNode(Transaction tx) {
        Node tradNode = VariantGraphService.getTraditionNode(tx, tradId);
        if (tradNode == null)
            throw new NotFoundException(String.format("No tradition found with id %s", tradId));
        List<Node> candidates = DatabaseService.getRelated(tradNode, ERelations.HAS_WITNESS);
        return DatabaseService.resolveManagedRef(tx, Nodes.WITNESS, candidates, ref, "sigil");
    }

    // Backwards compatibility for API
    public Response getWitnessAsText() {
        return getWitnessAsTextWithLayer(new ArrayList<>(), "0", "E");
    }

    /**
     * Returns a WitnessModel corresponding to the requested witness.
     * @return  A WitnessModel containing information about the witness
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            tags = {"Witness"},
            summary = "Get witness information",
            description = "Returns a WitnessModel corresponding to the requested witness.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Successfully retrieved witness information",
                            content = @Content(schema = @Schema(implementation = WitnessModel.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "if the witness reference is a sigil shared by multiple witnesses (legacy data only)",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Witness not found"
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Server error",
                            content = @Content(schema = @Schema(implementation = String.class))
                    )
            }
    )
    public Response getWitnessInfo() {
        try (Transaction tx = db.beginTx()) {
            Node witnessNode = resolveWitnessNode(tx);
            WitnessModel thisWit = new WitnessModel(witnessNode);
            return Response.ok(thisWit).build();
        } catch (NotFoundException e) {
            return Response.status(Status.NOT_FOUND).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Status.BAD_REQUEST).entity(jsonerror(e.getMessage())).build();
        }
    }

    /**
     * Deletes the requested witness.
     *
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            tags = {"Witness"},
            summary = "Delete a witness",
            description = "Deletes the requested witness from the entire tradition.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Successfully deleted witness",
                            content = @Content(schema = @Schema(implementation = WitnessModel.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Cannot delete witness from a single section",
                            content = @Content(schema = @Schema(implementation = String.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Witness not found"
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Server error during deletion"
                    )
            }
    )
    public Response deleteWitness() {
        if (sectId != null)
            return Response.status(Status.BAD_REQUEST).entity("Cannot delete a witness from a single section").build();
        WitnessModel removed;
        try (Transaction tx = db.beginTx()) {
            // Find the node in question
            Node witnessNode = resolveWitnessNode(tx);
            String actualSigil = witnessNode.getProperty("sigil").toString();
            // Find all references to the witness throughout the tradition, and delete them
            removed = new WitnessModel(witnessNode);
            HashSet<Node> orphanReadings = new HashSet<>();
            for (Relationship r : VariantGraphService.returnEntireTradition(tx, tradId).relationships()) {
                if (r.isType(ERelations.SEQUENCE)) {
                    Node start = r.getStartNode();
                    Node end = r.getEndNode();
                    for (String layer : r.getPropertyKeys()) {
                        ReadingService.removeWitnessLink(start, end, actualSigil, layer, "none");
                    }
                    // Was this the last outgoing for the start, or the last incoming for the end?
                    try (ResourceIterator<Relationship> i = start.getRelationships(Direction.OUTGOING, ERelations.SEQUENCE, ERelations.LEMMA_TEXT).iterator()) {
                        if (!i.hasNext())
                            orphanReadings.add(start);
                    }
                    try (ResourceIterator<Relationship> i = end.getRelationships(Direction.INCOMING, ERelations.SEQUENCE, ERelations.LEMMA_TEXT).iterator()) {
                        if (!i.hasNext())
                            orphanReadings.add(end);
                    }
                }
            }
            // Delete any orphan readings
            for (Node orphan : orphanReadings) {
                if (orphan.hasRelationship()) {
                    // Check that no SEQUENCE or LEMMA_TEXT relationships are left
                    for (Relationship r : DatabaseService.getRelationships(orphan)) {
                        if (r.isType(ERelations.SEQUENCE) || r.isType(ERelations.LEMMA_TEXT))
                            return Response.serverError()
                                    .entity(String.format("Reading %s (%s) still has sequence links",
                                            orphan.getElementId(), orphan.getProperty("text"))).build();
                        r.delete();
                    }
                    orphan.delete();
                }
            }
            // Strip the witness' old ID to avoid constraint violation errors
            witnessNode.removeProperty("id");
            // Look through any stemmata and either delete the witness (if it is a leaf node) or
            // turn it hypothetical (if it isn't).
            for (Relationship r : DatabaseService.getRelationships(witnessNode, ERelations.HAS_WITNESS)) {
                Node owner = r.getStartNode();
                if (owner.hasLabel(Nodes.STEMMA)) {
                    if (owner.hasRelationship(ERelations.HAS_ARCHETYPE) && witnessIsLeaf(witnessNode))
                        continue;
                    // If we got here, the witness needs to be substituted with a hypothetical one.
                    Node newHypothetical = tx.createNode(Nodes.WITNESS);
                    DatabaseService.assignIdIfManaged(tx, newHypothetical);
                    newHypothetical.setProperty("hypothetical", true);
                    // Use the witness's sigil but don't copy any other properties.
                    newHypothetical.setProperty("sigil", witnessNode.getProperty("sigil"));
                    // Copy over the TRANSMITTED links that belong to this stemma.
                    for (Relationship link : DatabaseService.getRelationships(witnessNode, ERelations.TRANSMITTED)) {
                        if (!link.getProperty("hypothesis", "").equals(owner.getProperty("name")))
                            continue;
                        Relationship copy;
                        if (link.getStartNode().equals(witnessNode))
                            copy = newHypothetical.createRelationshipTo(link.getEndNode(), ERelations.TRANSMITTED);
                        else
                            copy = link.getStartNode().createRelationshipTo(newHypothetical, ERelations.TRANSMITTED);
                        copy.setProperty("hypothesis", owner.getProperty("name"));
                        link.delete();
                    }
                    owner.createRelationshipTo(newHypothetical, ERelations.HAS_WITNESS);
                } // otherwise it is the link to the TRADITION node.
                r.delete();
            }
            // Delete all remaining relationships and the node itself.
            witnessNode.getRelationships().forEach(Relationship::delete);
            witnessNode.delete();
            tx.commit();
        } catch (NotFoundException e) {
            return Response.status(Status.NOT_FOUND).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }
        return Response.ok(removed).build();
    }

    // The witness is a leaf if it has no outgoing TRANSMITTED relationships.
    private static boolean witnessIsLeaf(Node witnessNode) {
        return witnessNode.getRelationships(Direction.OUTGOING).stream()
                .noneMatch(r -> r.getType().equals(ERelations.TRANSMITTED));
    }

    /**
     * Creates a new extant witness, or renames an existing one, under the given sigil. Only
     * valid tradition-wide: a witness's identity doesn't make sense scoped to a single section.
     * If the resource reference in the URL doesn't resolve to an existing witness, a new extant
     * witness is created with the URL reference as its sigil (the request body's sigil, if
     * given, must match it); otherwise, the resolved witness is renamed to the sigil given in
     * the request body, and the rename is carried through to the witness's text.
     *
     * @param wm - A WitnessModel containing the desired sigil
     * @return The resulting WitnessModel.
     *                    new witness's body sigil doesn't match the URL reference, or if the
     *                    witness reference is a sigil shared by multiple witnesses (legacy data only)
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            tags = {"Witness"},
            summary = "Create or rename a witness",
            description = "Creates a new extant witness, with the URL reference as its sigil, if that reference "
                    + "doesn't resolve to an existing witness (a sigil in the request body must then match the URL "
                    + "reference); otherwise renames the resolved witness to the sigil given in the request body, "
                    + "carrying the rename through to the witness's text. "
                    + "Only valid tradition-wide, not within a single section.",
            requestBody = @RequestBody(
                    description = "A WitnessModel containing the desired sigil",
                    required = true,
                    content = @Content(schema = @Schema(implementation = WitnessModel.class))
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Successfully renamed the existing witness",
                            content = @Content(schema = @Schema(implementation = WitnessModel.class))
                    ),
                    @ApiResponse(
                            responseCode = "201",
                            description = "Successfully created a new witness",
                            content = @Content(schema = @Schema(implementation = WitnessModel.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "if called within a single section, if the new sigil is not a valid name, "
                                    + "if a new witness's body sigil doesn't match the URL reference, "
                                    + "or if the witness reference is a sigil shared by multiple witnesses (legacy data only)",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Tradition not found"
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Another witness already has the requested sigil",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Server error during creation or rename"
                    )
            }
    )
    public Response putWitness(WitnessModel wm) {
        if (sectId != null)
            return Response.status(Status.BAD_REQUEST)
                    .entity(jsonerror("Cannot create or rename a witness within a single section")).build();
        try (Transaction tx = db.beginTx()) {
            Node tradNode = VariantGraphService.getTraditionNode(tx, tradId);
            if (tradNode == null)
                return Response.status(Status.NOT_FOUND)
                        .entity(jsonerror(String.format("No tradition found with id %s", tradId))).build();
            Node witnessNode;
            boolean isNew;
            try {
                witnessNode = resolveWitnessNode(tx);
                isNew = false;
            } catch (NotFoundException e) {
                witnessNode = null;
                isNew = true;
            }
            String targetSigil;
            if (isNew) {
                // A new witness takes its sigil from the URL reference. A differing sigil in the
                // body is most likely a rename aimed at a mistyped reference; refuse it rather
                // than silently creating a witness the caller didn't ask for.
                if (wm.getSigil() != null && !wm.getSigil().equals(ref))
                    return Response.status(Status.BAD_REQUEST)
                            .entity(jsonerror("sigil in request body does not match the URL reference")).build();
                targetSigil = ref;
            } else {
                targetSigil = wm.getSigil();
            }
            Util.validateSigil(targetSigil);
            DatabaseService.ensureNameUnique(tx, tradNode, ERelations.HAS_WITNESS, Nodes.WITNESS,
                    "sigil", targetSigil, witnessNode);
            if (isNew) {
                witnessNode = Util.createWitness(tx, targetSigil, false);
                tradNode.createRelationshipTo(witnessNode, ERelations.HAS_WITNESS);
            } else {
                String oldSigil = witnessNode.getProperty("sigil").toString();
                if (!oldSigil.equals(targetSigil)) {
                    // Witness membership of the text is recorded as sigil strings on every
                    // SEQUENCE (and normalized NSEQUENCE) link the witness passes through, so
                    // the rename has to be carried through to all of them.
                    List<Relationship> links = new ArrayList<>();
                    VariantGraphService.returnEntireTradition(tx, tradNode).relationships().forEach(r -> {
                        if (r.isType(ERelations.SEQUENCE) || r.isType(ERelations.NSEQUENCE))
                            links.add(r);
                    });
                    for (Relationship link : links)
                        ReadingService.renameWitnessOnLink(link, oldSigil, targetSigil);
                }
                witnessNode.setProperty("sigil", targetSigil);
                witnessNode.setProperty("quotesigil", !Util.isDotId(targetSigil));
            }
            WitnessModel result = new WitnessModel(witnessNode);
            tx.commit();
            return Response.status(isNew ? Status.CREATED : Status.OK).entity(result).build();
        } catch (NameConflictException e) {
            return Response.status(Status.CONFLICT).entity(jsonerror(e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Status.BAD_REQUEST).entity(jsonerror(e.getMessage())).build();
        } catch (NotFoundException e) {
            return Response.status(Status.NOT_FOUND).entity(jsonerror(e.getMessage())).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
    }

    /**
     * finds a witness in the database and returns it as a string; if start and end are
     * specified, a substring of the full witness text between those ranks inclusive is
     * returned. if end-rank is too high or start-rank too low will return up to the end
     * / from the start of the witness. If one or more witness layers are specified, return
     * the text composed of those layers.
     *
     * @param layer - the text layer(s) to return, e.g. "a.c." or "s.l.". These layers must not conflict with each other!
     * @param start - the starting rank
     * @param end   - the end rank
     * @return The witness text as a string.
     */
    @GET
    @Path("/text")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            tags = {"Witness"},
            summary = "Get witness text",
            description = "Finds a witness and returns it as text string. Optionally filters by rank range and text layers.",
            parameters = {
                    @Parameter(
                            name = "layer",
                            description = "Text layer(s) to return (e.g. 'a.c.' or 's.l.')",
                            example = "a.c.",
                            allowEmptyValue = true
                    ),
                    @Parameter(
                            name = "start",
                            description = "Starting rank (inclusive)",
                            example = "0"
                    ),
                    @Parameter(
                            name = "end",
                            description = "End rank (inclusive) or 'E' for section end",
                            example = "E"
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Successfully retrieved witness text",
                            content = @Content(schema = @Schema(implementation = TextSequenceModel.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid rank parameters",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "if the witness reference is a sigil shared by multiple witnesses (legacy data only)",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Tradition, section, or witness not found",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "End node unreachable during text assembly",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Server error during text retrieval",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    )
            }
    )
    public Response getWitnessAsTextWithLayer(
            @QueryParam("layer") @DefaultValue("") List<String> layer,
            @QueryParam("start") @DefaultValue("0") String start,
            @QueryParam("end") @DefaultValue("E") String end) {

        long startRank = Long.parseLong(start);
        long endRank = end.equals("E") ? Long.MAX_VALUE : Long.parseLong(end);

        // Empty out the layer list if it is the default.
        if (layer.size() == 1 && layer.getFirst().isEmpty())
            layer.removeFirst();

        try (Transaction tx = db.beginTx()) {
            Node witnessNode = resolveWitnessNode(tx);
            String actualSigil = witnessNode.getProperty("sigil").toString();
            String witnessText = VariantGraphService.getWitnessText(tx, tradId, sectId, actualSigil, layer, startRank, endRank);
            TextSequenceModel wtm = new TextSequenceModel(witnessText);
            return Response.ok(wtm).build();
        } catch (org.neo4j.graphdb.NotFoundException e) {
            return Response.status(Status.NOT_FOUND).entity(jsonerror(e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Status.BAD_REQUEST).entity(jsonerror(e.getMessage())).build();
        } catch (IllegalStateException e) {
            if (e.getMessage().equals("CONFLICT"))
                return Response.status(Status.CONFLICT).entity(jsonerror("Traversal end node not reached")).build();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
    }

    /**
     * Returns the sequence of readings for a given witness.
     *
     * @param witnessClass - the text layer to return, e.g. "a.c."
     * @return The witness text as a list of readings.
     */
    @GET
    @Path("/readings")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            tags = {"Witness"},
            summary = "Get readings",
            description = "Returns the sequence of readings for a given witness.",
            parameters = {
                    @Parameter(
                            name = "layer",
                            description = "Text layer to return (e.g. 'a.c.')",
                            example = "a.c.",
                            allowEmptyValue = true
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Successfully retrieved readings",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReadingModel.class)))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "if the witness reference is a sigil shared by multiple witnesses (legacy data only)",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Tradition, section, or witness not found",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "End node unreachable during reading assembly",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Server error during reading retrieval",
                            content = @Content(schema = @Schema(implementation = Map.class))
                    )
            }
    )
    public Response getWitnessAsReadings(@QueryParam("layer") @DefaultValue("") List<String> witnessClass) {
        ArrayList<ReadingModel> readingModels = new ArrayList<>();
        if (witnessClass.size() == 1 && witnessClass.getFirst().isEmpty())
            witnessClass.removeFirst();

        try (Transaction tx = db.beginTx()) {
            Node witnessNode = resolveWitnessNode(tx);
            String actualSigil = witnessNode.getProperty("sigil").toString();
            ArrayList<Node> iterationList = VariantGraphService.sectionsRequested(tx, tradId, sectId);

            for (Node currentSection : iterationList) {
                Node startNode = VariantGraphService.getStartNode(tx, currentSection.getProperty("id").toString());
                readingModels.addAll(VariantGraphService.traverseReadingsOfWitness(tx, startNode, actualSigil, witnessClass)
                        .stream().map(ReadingModel::new).toList());
                // Remove the meta node from the list
                if (!readingModels.isEmpty() && readingModels.getLast().getIs_end())
                    readingModels.removeLast();
            }
        } catch (org.neo4j.graphdb.NotFoundException e) {
            return Response.status(Status.NOT_FOUND).entity(jsonerror(e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Status.BAD_REQUEST).entity(jsonerror(e.getMessage())).build();
        } catch (IllegalStateException e) {
            if (e.getMessage().equals("CONFLICT"))
                return Response.status(Status.CONFLICT).entity(jsonerror("Traversal end node not reached")).build();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }

        // If the path is size 0 then the witness path doesn't exist.
        if (readingModels.isEmpty())
            return Response.status(Status.NOT_FOUND)
                    .entity(jsonerror("No witness path found for this sigil")).build();
        // ...and return.
        return Response.status(Status.OK).entity(readingModels).build();
    }

}
