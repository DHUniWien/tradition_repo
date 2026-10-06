package net.stemmaweb.rest;

import static net.stemmaweb.Util.jsonerror;
import static net.stemmaweb.services.RelationService.returnRelationType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.stemmaweb.model.RelationTypeModel;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Traverser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import net.stemmaweb.model.GraphModel;
import net.stemmaweb.model.RelationModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.RelationService;
import net.stemmaweb.services.VariantGraphService;

/**
 * Comprises all the api calls related to a relation.
 * can be called by using <a href="http://BASE_URL/relation">...</a>
 * @author PSE FS 2015 Team2
 */

public class Relation {

    private final GraphDatabaseService db;
    private final String tradId;
    private static final String SCOPE_LOCAL = "local";
    private static final String SCOPE_SECTION = "section";
    private static final String SCOPE_TRADITION = "tradition";


    public Relation(String traditionId) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        tradId = traditionId;
    }

    /**
     * Creates a new relation between the specified reading nodes.
     *
     * @title Create relation
     * @param relationModel - JSON structure of the relation to create
     * @return The relation(s) created, as well as any other readings in the graph that
     * had a relation set between them.
     * @statuscode 201 - on success
     * @statuscode 304 - if the specified relation type/scope already exists
     * @statuscode 400 - if the request has an invalid scope
     * @statuscode 409 - if the relationship cannot legally be created
     * @statuscode 500 - on failure, with JSON error message
     */
    // TODO make this an idempotent PUT call
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            tags = {"Relation"},
            summary = "Create relation",
            description = "Creates a new relation between the specified reading nodes.",
            requestBody = @RequestBody(
                    description = "JSON structure of the relation to create",
                    required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = RelationModel.class))
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "On success",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = GraphModel.class))
                    ),
                    @ApiResponse(
                            responseCode = "304",
                            description = "If the specified relation type/scope already exists"
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "If the request has an invalid scope"
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "If the relationship cannot legally be created"
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "On failure, with JSON error message"
                    )
            }
    )
    public Response create(RelationModel relationModel) {
        // Make sure a scope is set
        if (relationModel.getScope() == null) relationModel.setScope(SCOPE_LOCAL);
        String scope = relationModel.getScope();
        if (!scope.equals(SCOPE_TRADITION) && !scope.equals(SCOPE_SECTION) && !scope.equals(SCOPE_LOCAL)) {
            return Response.status(Status.BAD_REQUEST).entity("Undefined Scope").build();
        }

        try (Transaction tx = db.beginTx()) {
            GraphModel relationChanges = new GraphModel();

            // Create the local relation
            GraphModel localResult = RelationService.createLocalRelation(tx, tradId, relationModel);
            if (localResult.getRelations().isEmpty()) {
                return Response.status(Status.NOT_MODIFIED).build();
            }
            relationChanges.addReadings(localResult.getReadings());
            relationChanges.addRelations(localResult.getRelations());

            // If scope is not local, propagate to matching readings
            if (!scope.equals(SCOPE_LOCAL)) {
                // Fish out the ID of the relationship that we explicitly created
                Optional<RelationModel> orm = localResult.getRelations().stream()
                        .filter(x -> x.getTarget().equals(relationModel.getTarget())
                                && x.getSource().equals(relationModel.getSource())).findFirst();
                assert(orm.isPresent());
                String thisRelId = orm.get().getId();

                RelationTypeModel typeDefinition = returnRelationType(tx, tradId, relationModel.getType());
                if (typeDefinition == null)
                    throw new IllegalStateException("Relation type definition for " + relationModel.getType() + " does not exist");
                Boolean use_normal = typeDefinition.getUse_regular();
                Node readingA = DatabaseService.findNodeOrThrow(tx, Nodes.READING, relationModel.getSource());
                Node readingB = DatabaseService.findNodeOrThrow(tx, Nodes.READING, relationModel.getTarget());
                Node startingPoint = VariantGraphService.getTraditionNode(tx, tradId);
                if (scope.equals(SCOPE_SECTION))
                    startingPoint = DatabaseService.findNodeOrThrow(tx, Nodes.SECTION, String.valueOf(readingA.getProperty("section_id")));
                Relationship thisRelation = DatabaseService.findRelatedOrThrow(tx, thisRelId);

                // Get all the readings that belong to our tradition or section
                ArrayList<Node> tradReadings = new ArrayList<>();
                VariantGraphService.returnEntireTradition(tx, startingPoint).nodes().forEach(tradReadings::add);
                // Pick out the ones that share the readingA text
                Function<Node, Object> nodefilter = (n) -> use_normal && n.hasProperty("normal_form")
                        ? n.getProperty("normal_form") : (n.hasProperty("text") ? n.getProperty("text"): "");
                HashSet<Node> ourA = tradReadings.stream()
                        .filter(x -> nodefilter.apply(x).equals(nodefilter.apply(readingA)) && !x.equals(readingA))
                        .collect(Collectors.toCollection(HashSet::new));
                HashMap<String, HashSet<String>> ranks = new HashMap<>();
                for (Node cur_node : ourA) {
                    long node_rank = (Long) cur_node.getProperty("rank");
                    String node_section = cur_node.getProperty("section_id").toString();
                    String key = node_section + "/" + node_rank;
                    HashSet<String> cur_set = ranks.getOrDefault(key, new HashSet<>());
                    cur_set.add(cur_node.getProperty("id").toString());
                    ranks.putIfAbsent(key, cur_set);
                }

                // Pick out the ones that share the readingB text
                HashSet<Node> ourB = tradReadings.stream().filter(x -> x.hasProperty("text")
                        && nodefilter.apply(x).equals(nodefilter.apply(readingB)) && !x.equals(readingB))
                        .collect(Collectors.toCollection(HashSet::new));
                RelationModel userel;
                for (Node cur_node : ourB) {
                    String nodeB_id = cur_node.getProperty("id").toString();
                    long node_rank = (Long) cur_node.getProperty("rank");
                    String node_section = cur_node.getProperty("section_id").toString();
                    String key = node_section + "/" + node_rank;

                    HashSet<String> cur_set = ranks.get(key);
                    if (cur_set != null) {
                        for (String nodeA_id : cur_set) {
                            userel = new RelationModel(thisRelation);
                            userel.setSource(nodeA_id);
                            userel.setTarget(nodeB_id);
                            try {
                                GraphModel createResult = RelationService.createLocalRelation(tx, tradId, userel);
                                relationChanges.addReadings(createResult.getReadings());
                                relationChanges.addRelations(createResult.getRelations());
                            } catch (IllegalStateException e) {
                                // This is a best-effort operation, so ignore failures
                            }
                        }
                    }
                }
            }
            tx.commit();
            return Response.status(Status.CREATED).entity(relationChanges).build();
        } catch (IllegalStateException e) {
            return Response.status(Status.CONFLICT).entity(jsonerror(e.getMessage())).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
    }

    /**
     * Remove the relation specified. There should be only one.
     *
     * @title Delete a relation specifed by JSON data.
     * @param relationModel - the JSON specification of the relationship(s) to delete
     * @return A list of all relationships that were removed.
     * @statuscode 200 - on success
     * @statuscode 400 - if an invalid scope was specified
     * @statuscode 404 - if no matching relationship was found
     * @statuscode 500 - on failure, with JSON error message
     */
    @POST
    @Path("/remove")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            tags = {"Relation"},
            summary = "Delete a relation specified by JSON data",
            description = "Remove the relation specified. There should be only one.",
            requestBody = @RequestBody(
                    description = "The JSON specification of the relationship(s) to delete",
                    required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RelationModel.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "A list of all relationships that were removed", content = @Content(mediaType = "application/json", schema = @Schema(implementation = List.class))),
                    @ApiResponse(responseCode = "400", description = "Bad request, if an invalid scope was specified", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "404", description = "Not found, if no matching relationship was found", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "500", description = "Failure (uncaught exception; response body format not guaranteed)")
            }
    )
    public Response deleteByData(RelationModel relationModel) {
        ArrayList<RelationModel> deleted = new ArrayList<>();

        try (Transaction tx = db.beginTx()) {
            Node readingA = DatabaseService.findNodeOrThrow(tx, Nodes.READING, relationModel.getSource());
            Node readingB = DatabaseService.findNodeOrThrow(tx, Nodes.READING, relationModel.getTarget());

            switch (relationModel.getScope()) {
                case SCOPE_LOCAL:
                    ArrayList<Relationship> findRel = DatabaseService.getRelationshipTo(readingA, readingB, ERelations.RELATED);
                    if (findRel.isEmpty()) {
                        return Response.status(Status.NOT_FOUND).entity(jsonerror("Relation not found")).build();
                    } else {
                        Relationship theRel = findRel.getFirst();
                        RelationModel relInfo = new RelationModel(theRel);
                        theRel.delete();
                        deleted.add(relInfo);
                    }
                    break;

                case SCOPE_SECTION:
                case SCOPE_TRADITION:
                    Traverser toCheck = relationModel.getScope().equals(SCOPE_SECTION)
                            ? VariantGraphService.returnTraditionSection(tx, readingA.getProperty("section_id").toString())
                            : VariantGraphService.returnEntireTradition(tx, tradId);

                    for (Relationship rel : toCheck.relationships()) {
                        if (rel.getType().name().equals(ERelations.RELATED.name())) {
                            Node ra = DatabaseService.findNodeOrThrow(tx, Nodes.READING, relationModel.getSource());
                            Node rb = DatabaseService.findNodeOrThrow(tx, Nodes.READING, relationModel.getTarget());

                            if ((rel.getStartNode().getProperty("text").equals(ra.getProperty("text"))
                                    || rel.getEndNode().getProperty("text").equals(ra.getProperty("text")))
                                    && (rel.getStartNode().getProperty("text").equals(rb.getProperty("text"))
                                    || rel.getEndNode().getProperty("text").equals(rb.getProperty("text")))) {
                                RelationModel relInfo = new RelationModel(rel);
                                rel.delete();
                                deleted.add(relInfo);
                            }
                        }
                    }
                    break;

                default:
                    return Response.status(Status.BAD_REQUEST).entity(jsonerror("Undefined Scope")).build();
            }
            tx.commit();
        }
        return Response.status(Response.Status.OK).entity(deleted).build();
    }
    
    /**
     * Removes a relation by internal ID.
     *
     * @title Delete relation by ID
     * @param relationId - the ID of the relation to delete
     * @return The deleted relation
     * @statuscode 200 - on success
     * @statuscode 500 - on failure (e.g. the given ID does not belong to a RELATED relationship), with JSON error message
     */
    @DELETE
    @Path("{relationId}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            tags = {"Relation"},
            summary = "Delete relation by ID",
            description = "Removes a relation by internal ID.",
            parameters = @Parameter(
                    name = "relationId",
                    description = "The ID of the relation to delete",
                    required = true,
                    schema = @Schema(type = "string")
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "The deleted relation", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RelationModel.class))),
                    @ApiResponse(responseCode = "500", description = "Failure (e.g. the given ID does not belong to a RELATED relationship), with JSON error message", content = @Content(mediaType = "application/json"))
            }
    )
    public Response deleteById(@PathParam("relationId") String relationId) {
        RelationModel relationModel;

        try (Transaction tx = db.beginTx()) {
            Relationship relationship = DatabaseService.findRelatedOrThrow(tx, relationId);
            relationModel = new RelationModel(relationship);
            relationship.delete();
            tx.commit();
        } catch (Exception e) {
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(relationModel).build();
    }
}