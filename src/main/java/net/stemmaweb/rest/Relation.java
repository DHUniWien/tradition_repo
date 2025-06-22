package net.stemmaweb.rest;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import net.stemmaweb.model.GraphModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationModel;
import net.stemmaweb.model.RelationTypeModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.ReadingService;

import net.stemmaweb.services.VariantGraphService;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Traverser;
import org.neo4j.graphdb.traversal.Uniqueness;

import static net.stemmaweb.Util.jsonerror;
import static net.stemmaweb.services.RelationService.returnRelationType;
import static net.stemmaweb.services.RelationService.TransitiveRelationTraverser;

/**
 * Comprises all the api calls related to a relation.
 * can be called by using {@code http://BASE_URL/relation}
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
     * @param relationModel - JSON structure of the relation to create
     * @return The relation(s) created, as well as any other readings in the graph that
     * had a relation set between them.
     */
    // TODO make this an idempotent PUT call
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
            summary = "Create relation",
            description = "Creates a new relation between the specified reading nodes.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "JSON structure of the relation to create",
                    required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON)
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
        if (scope.equals(SCOPE_TRADITION) || scope.equals(SCOPE_SECTION) || scope.equals(SCOPE_LOCAL)) {
            GraphModel relationChanges = new GraphModel();

            Response response = this.create_local(relationModel);
            if (Status.CREATED.getStatusCode() != response.getStatus()) {
                return response;
            }
            GraphModel createResult = (GraphModel)response.getEntity();
            relationChanges.addReadings(createResult.getReadings());
            relationChanges.addRelations(createResult.getRelations());
            // Fish out the ID of the relationship that we explicitly created
            Optional<RelationModel> orm = createResult.getRelations().stream()
                    .filter(x -> x.getTarget().equals(relationModel.getTarget())
                            && x.getSource().equals(relationModel.getSource())).findFirst();
            assert(orm.isPresent());
            String thisRelId = orm.get().getId();
            if (!scope.equals(SCOPE_LOCAL)) {
                Boolean use_normal = returnRelationType(tradId, relationModel.getType()).getUse_regular();
                try (Transaction tx = db.beginTx()) {
                    Node readingA = db.getNodeById(Long.parseLong(relationModel.getSource()));
                    Node readingB = db.getNodeById(Long.parseLong(relationModel.getTarget()));
                    Node startingPoint = VariantGraphService.getTraditionNode(tradId, db);
                    if (scope.equals(SCOPE_SECTION))
                        startingPoint = db.getNodeById((Long) readingA.getProperty("section_id"));
                    Relationship thisRelation = db.getRelationshipById(Long.parseLong(thisRelId));

                    // Get all the readings that belong to our tradition or section
                    ResourceIterable<Node> tradReadings = VariantGraphService.returnEntireTradition(startingPoint).nodes();
                    // Pick out the ones that share the readingA text
                    Function<Node, Object> nodefilter = (n) -> use_normal && n.hasProperty("normal_form")
                            ? n.getProperty("normal_form") : (n.hasProperty("text") ? n.getProperty("text"): "");
                    HashSet<Node> ourA = tradReadings.stream()
                            .filter(x -> nodefilter.apply(x).equals(nodefilter.apply(readingA)) && !x.equals(readingA))
                            .collect(Collectors.toCollection(HashSet::new));
                    HashMap<String, HashSet<Long>> ranks = new HashMap<>();
                    for (Node cur_node : ourA) {
                        long node_id = cur_node.getId();
                        String node_rank = cur_node.getProperty("rank").toString();
                        String node_section = cur_node.getProperty("section_id").toString();
                        String key = node_section + "/" + node_rank;
                        HashSet<Long> cur_set = ranks.getOrDefault(node_rank, new HashSet<>());
                        cur_set.add(node_id);
                        ranks.putIfAbsent(key, cur_set);
                    }

                    // Pick out the ones that share the readingB text
                    HashSet<Node> ourB = tradReadings.stream().filter(x -> x.hasProperty("text")
                            && nodefilter.apply(x).equals(nodefilter.apply(readingB)) && !x.equals(readingB))
                            .collect(Collectors.toCollection(HashSet::new));
                    RelationModel userel;
                    for (Node cur_node : ourB) {
                        long node_id = cur_node.getId();
                        long node_rank = (Long) cur_node.getProperty("rank");
                        String node_section = cur_node.getProperty("section_id").toString();
                        String key = node_section + "/" + node_rank;

                        HashSet<Long> cur_set = ranks.get(key);
                        if (cur_set != null) {
                            for (Long id : cur_set) {
                                userel = new RelationModel(thisRelation);
                                userel.setSource(Long.toString(id));
                                userel.setTarget(Long.toString(node_id));
                                response = this.create_local(userel);
                                if (Status.NOT_MODIFIED.getStatusCode() != response.getStatus()) {
                                    if (Status.CREATED.getStatusCode() == response.getStatus()) {
                                        createResult = (GraphModel) response.getEntity();
                                        relationChanges.addReadings(createResult.getReadings());
                                        relationChanges.addRelations(createResult.getRelations());
                                    }  // This is a best-effort operation, so ignore failures
                                }
                            }
                        }
                    }
                    tx.success();
                } catch (Exception e) {
                    e.printStackTrace();
                    return Response.serverError().build();
                }
            }
            return Response.status(Status.CREATED).entity(relationChanges).build();
        }
        return Response.status(Status.BAD_REQUEST).entity("Undefined Scope").build();
    }

    // Create a relation; return the relation created as well as any reading nodes whose
    // properties (e.g. rank) have changed.
    private Response create_local(RelationModel relationModel) {
        GraphModel readingsAndRelationModel;
        try (Transaction tx = db.beginTx()) {
            /*
             * Currently search by id search, because is much faster by measurement. Because
             * the id search is O(n) just go through all ids without care. And the
             *
             */
            Node readingA = db.getNodeById(Long.parseLong(relationModel.getSource()));
            Node readingB = db.getNodeById(Long.parseLong(relationModel.getTarget()));

            Node ourSection = db.getNodeById(Long.parseLong(readingA.getProperty("section_id").toString()));
            Node ourTradition = ourSection.getSingleRelationship(ERelations.PART, Direction.INCOMING).getStartNode();
            if (!ourTradition.getProperty("id").equals(tradId))
                return Response.status(Status.CONFLICT)
                    .entity(jsonerror("The specified readings do not belong to the specified tradition"))
                    .build();


            if (!readingA.getProperty("section_id").equals(readingB.getProperty("section_id")))
                return Response.status(Status.CONFLICT)
                        .entity(jsonerror("Cannot create relation across tradition sections"))
                        .build();

            if (isMetaReading(readingA) || isMetaReading(readingB))
                return Response.status(Status.CONFLICT)
                    .entity(jsonerror("Cannot set relation on a meta reading"))
                    .build();

            // Get, or create implicitly, the relation type node for the given type.
            RelationTypeModel rmodel = returnRelationType(tradId, relationModel.getType());

            // Check that the relation type is compatible with the passed relation model
            if (!relationModel.getScope().equals("local") && !rmodel.getIs_generalizable())
                return Response.status(Status.CONFLICT)
                        .entity(jsonerror("Relation type " + rmodel.getName() + " cannot be made outside a local scope"))
                        .build();

            // Remove any weak relations that might conflict
            // LATER better idea: write a traverser that will disregard weak relations
            Boolean colocation = rmodel.getIs_colocation();
            if (colocation) {
                Iterable<Relationship> relsA = readingA.getRelationships(ERelations.RELATED);
                for (Relationship r : relsA) {
                    RelationTypeModel rm = returnRelationType(tradId, r.getProperty("type").toString());
                    if (rm.getIs_weak())
                        r.delete();
                }
                Iterable<Relationship> relsB = readingB.getRelationships(ERelations.RELATED);
                for (Relationship r : relsB) {
                    RelationTypeModel rm = returnRelationType(tradId, r.getProperty("type").toString());
                    if (rm.getIs_weak())
                        r.delete();
                }
            }

            Boolean isCyclic = ReadingService.wouldGetCyclic(readingA, readingB);
            if (isCyclic && colocation) {
                    return Response
                            .status(Status.CONFLICT)
                            .entity(jsonerror("This relation creation is not allowed, it would result in a cyclic graph."))
                            .build();
            } else if (!isCyclic && !colocation) {
                return Response
                        .status(Status.CONFLICT)
                        .entity(jsonerror("This relation creation is not allowed. The two readings can be co-located."))
                        .build();
            } // TODO add constraints about witness uniqueness or lack thereof

            // Check if relation already exists
            Iterable<Relationship> relationships = readingA.getRelationships(ERelations.RELATED);
            for (Relationship relationship : relationships) {
                if (relationship.getOtherNode(readingA).equals(readingB)) {
                    RelationModel thisRel = new RelationModel(relationship);
                    RelationTypeModel rtm = returnRelationType(tradId, thisRel.getType());
                    if (thisRel.getType().equals(relationModel.getType())) {
                        // TODO allow for update of existing relation
                        tx.success();
                        return Response.status(Status.NOT_MODIFIED).type(MediaType.TEXT_PLAIN_TYPE).build();
                    } else if (!rtm.getIs_weak()) {
                        tx.success();
                        String msg = String.format("Relation of type %s already exists between readings %s and %s",
                                relationModel.getType(), relationModel.getSource(), relationModel.getTarget());
                        return Response.status(Status.CONFLICT).entity(jsonerror(msg)).build();
                    }
                }
            }

            // We are finally ready to write a relation.
            readingsAndRelationModel = createSingleRelation(readingA, readingB, relationModel, rmodel);
            // We can also write any transitive relationships.
            propagateRelation(readingsAndRelationModel, rmodel);
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.status(Response.Status.CREATED).entity(readingsAndRelationModel).build();
    }

    /**
     * Muck with the database to set a relation
     *
     * @param readingA - the source reading
     * @param readingB - the target reading
     * @param relModel - the RelationModel to set
     * @param rtm      - the RelationTypeModel describing what sort of relation this is
     * @return a GraphModel containing the single n4j relationship plus whatever readings were re-ranked
     */
    private GraphModel createSingleRelation(Node readingA, Node readingB,
                                            RelationModel relModel, RelationTypeModel rtm) throws Exception {
        ArrayList<ReadingModel> changedReadings = new ArrayList<>();
        ArrayList<RelationModel> createdRelations = new ArrayList<>();

        Boolean colocation = rtm.getIs_colocation();
        Relationship relationAtoB = readingA.createRelationshipTo(readingB, ERelations.RELATED);

        relationAtoB.setProperty("type", nullToEmptyString(relModel.getType()));
        relationAtoB.setProperty("scope", nullToEmptyString(relModel.getScope()));
        relationAtoB.setProperty("annotation", nullToEmptyString(relModel.getAnnotation()));
        relationAtoB.setProperty("displayform",
                nullToEmptyString(relModel.getDisplayform()));
        relationAtoB.setProperty("a_derivable_from_b", relModel.getA_derivable_from_b());
        relationAtoB.setProperty("b_derivable_from_a", relModel.getB_derivable_from_a());
        relationAtoB.setProperty("alters_meaning", relModel.getAlters_meaning());
        relationAtoB.setProperty("is_significant", relModel.getIs_significant());
        relationAtoB.setProperty("non_independent", relModel.getNon_independent());
        relationAtoB.setProperty("reading_a", readingA.getProperty("text"));
        relationAtoB.setProperty("reading_b", readingB.getProperty("text"));
        if (colocation) relationAtoB.setProperty("colocation", true);

        // Recalculate the ranks, if necessary
        Long rankA = (Long) readingA.getProperty("rank");
        Long rankB = (Long) readingB.getProperty("rank");
        if (!rankA.equals(rankB) && colocation) {
            // Which one is the lower-ranked reading? Promote it, and recalculate from that point
            Long higherRank = rankA < rankB ? rankB : rankA;
            Node lowerRanked = rankA < rankB ? readingA : readingB;
            lowerRanked.setProperty("rank", higherRank);
            changedReadings.add(new ReadingModel(lowerRanked));
            Set<Node> changedRank = ReadingService.recalculateRank(lowerRanked);
            for (Node cr : changedRank)
                if (!cr.equals(lowerRanked))
                    changedReadings.add(new ReadingModel(cr));

        }

        createdRelations.add(new RelationModel(relationAtoB));
        return new GraphModel(changedReadings, createdRelations, new ArrayList<>());
    }

    /**
     * Checks if a reading is a "Meta"-reading
     *
     * @param reading - the reading to check
     * @return true or false
     */
    private boolean isMetaReading(Node reading) {
        return reading != null &&
                ((reading.hasProperty("is_lacuna") && reading.getProperty("is_lacuna").equals(true)) ||
                        (reading.hasProperty("is_start") && reading.getProperty("is_start").equals(true)) ||
                        (reading.hasProperty("is_ph") && reading.getProperty("is_ph").equals(true)) ||
                        (reading.hasProperty("is_end") && reading.getProperty("is_end").equals(true))
                );
    }

    /**
     * Propagates reading relations according to type specification.
     * NOTE - To be used inside a transaction
     *
     * @param newRelationResult - the GraphModel that contains a relation just created
     * @param rtm - the relation type specification
     */
    private void propagateRelation(GraphModel newRelationResult, RelationTypeModel rtm) throws Exception {
        // First see if this relation type should be propagated.
        if (!rtm.getIs_transitive()) return;
        // Now go through all the relations that have been created, and make sure that any
        // transitivity effects have been accounted for.
        for (RelationModel rm : newRelationResult.getRelations()) {
            TransitiveRelationTraverser relTraverser = new TransitiveRelationTraverser(tradId, rtm);
            Node startNode = db.getNodeById(Long.parseLong(rm.getSource()));
            ArrayList<Node> relatedNodes = new ArrayList<>();
            // Get all the readings that are related by this or a more closely-bound type.
            db.traversalDescription().depthFirst()
                    .relationships(ERelations.RELATED)
                    .evaluator(relTraverser)
                    .uniqueness(Uniqueness.NODE_GLOBAL)
                    .traverse(startNode).nodes().forEach(relatedNodes::add);
            // Now go through them and make sure the relations are explicit.
            ArrayList<Node> iterateNodes = new ArrayList<>(relatedNodes);
            while (!iterateNodes.isEmpty()) {
                Node readingA = iterateNodes.remove(0);
                HashSet<Node> alreadyRelated = new HashSet<>();
                readingA.getRelationships(ERelations.RELATED).forEach(x -> alreadyRelated.add(x.getOtherNode(readingA)));
                for (Node readingB : iterateNodes) {
                    if (!alreadyRelated.contains(readingB)) {
                        GraphModel interim = createSingleRelation(readingA, readingB, rm, rtm);
                        newRelationResult.addReadings(interim.getReadings());
                        newRelationResult.addRelations(interim.getRelations());
                    }
                }
            }
            // Now go back through them and make sure that relations to more loosely-bound
            // transitive nodes are marked.
            for (Node sibling : relatedNodes) {
                HashMap<Node, Relationship> connections = new HashMap<>();
                // Get the nodes we are directly related to, and the relations involved, if
                // they meet the criteria
                for (Relationship r : sibling.getRelationships(ERelations.RELATED)) {
                    RelationTypeModel othertm = returnRelationType(tradId, r.getProperty("type").toString());
                    if (othertm.getBindlevel() > rtm.getBindlevel() && othertm.getIs_transitive())
                        connections.put(r.getOtherNode(sibling), r);
                }

                HashSet<Node> cousins = new HashSet<>(relatedNodes);
                for (Node n : connections.keySet()) {
                    cousins.remove(n);
                    RelationModel newmodel = new RelationModel(connections.get(n));
                    RelationTypeModel newtm = returnRelationType(tradId, newmodel.getType());
                    for (Node c : cousins) {
                        ArrayList<Relationship> priorLinks = DatabaseService.getRelationshipTo(n, c, ERelations.RELATED);
                        if (priorLinks.isEmpty()) {
                            // Create a relation based on the looser link
                            GraphModel interim = createSingleRelation(n, c, newmodel, newtm);
                            newRelationResult.addReadings(interim.getReadings());
                            newRelationResult.addRelations(interim.getRelations());
                        }
                    }
                }


            }
        }
    }

    /**
     * Remove the relation specified. There should be only one.
     *
     * @param relationModel - the JSON specification of the relationship(s) to delete
     * @return A list of all relationships that were removed.
     */
    @POST
    @Path("/remove")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
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
                    @ApiResponse(responseCode = "500", description = "Failure, with JSON error message", content = @Content(mediaType = "application/json"))
            }
    )
    public Response deleteByData(RelationModel relationModel) {
        ArrayList<RelationModel> deleted = new ArrayList<>();

        try (Transaction tx = db.beginTx()) {
            Node readingA = db.getNodeById(Long.parseLong(relationModel.getSource()));
            Node readingB = db.getNodeById(Long.parseLong(relationModel.getTarget()));

            switch (relationModel.getScope()) {
                case SCOPE_LOCAL:
                    ArrayList<Relationship> findRel = DatabaseService.getRelationshipTo(readingA, readingB, ERelations.RELATED);
                    if (findRel.isEmpty()) {
                        return Response.status(Status.NOT_FOUND).entity(jsonerror("Relation not found")).build();
                    } else {
                        Relationship theRel = findRel.get(0);
                        RelationModel relInfo = new RelationModel(theRel);
                        theRel.delete();
                        deleted.add(relInfo);
                    }
                    break;

                case SCOPE_SECTION:
                case SCOPE_TRADITION:
                    Traverser toCheck = relationModel.getScope().equals(SCOPE_SECTION)
                            ? VariantGraphService.returnTraditionSection(readingA.getProperty("section_id").toString(), db)
                            : VariantGraphService.returnEntireTradition(tradId, db);

                    for (Relationship rel : toCheck.relationships()) {
                        if (rel.getType().name().equals(ERelations.RELATED.name())) {
                            Node ra = db.getNodeById(Long.parseLong(relationModel.getSource()));
                            Node rb = db.getNodeById(Long.parseLong(relationModel.getTarget()));

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
            tx.success();
        }
        return Response.status(Response.Status.OK).entity(deleted).build();
    }
    
    /**
     * Removes a relation by internal ID.
     *
     * @param relationId - the ID of the relation to delete
     * @return The deleted relation
     */
    @DELETE
    @Path("{relationId}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Operation(
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
                    @ApiResponse(responseCode = "403", description = "Forbidden, if the given ID does not belong to a relation", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "500", description = "Failure, with JSON error message", content = @Content(mediaType = "application/json"))
            }
    )
    public Response deleteById(@PathParam("relationId") String relationId) {
        RelationModel relationModel;

        try (Transaction tx = db.beginTx()) {
            Relationship relationship = db.getRelationshipById(Long.parseLong(relationId));
            if(relationship.getType().name().equals("RELATED")) {
                relationModel = new RelationModel(relationship);
                relationship.delete();
            } else {
                return Response.status(Status.FORBIDDEN).entity(jsonerror("This is not a relation link")).build();
            }
            tx.success();
        } catch (Exception e) {
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(relationModel).build();
    }
    
    private String nullToEmptyString(String str){
        return str == null ? "" : str;
    }
}