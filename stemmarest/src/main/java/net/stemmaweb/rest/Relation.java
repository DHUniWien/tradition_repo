package net.stemmaweb.rest;

import java.util.*;
import java.util.stream.Collectors;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.qmino.miredot.annotations.ReturnType;
import net.stemmaweb.model.GraphModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationTypeModel;
import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.ReadingService;

import net.stemmaweb.services.RelationshipService;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Uniqueness;

import static net.stemmaweb.rest.Util.jsonerror;


/**
 * Comprises all the api calls related to a relation.
 * can be called by using http://BASE_URL/relation
 * @author PSE FS 2015 Team2
 */

public class Relation {

    private GraphDatabaseService db;
    private String tradId;
    private static final String SCOPE_LOCAL = "local";
    private static final String SCOPE_GLOBAL = "document";


    Relation(String traditionId) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        tradId = traditionId;
    }

    /**
     * Creates a new relationship between the specified reading nodes.
     *
     * @summary Create relationship
     * @param relationshipModel - JSON structure of the relationship to create
     * @return The relationship(s) created, as well as any other readings in the graph that
     * had a relationship set between them.
     * @statuscode 201 - on success
     * @statuscode 304 - if the specified relationship type/scope already exists
     * @statuscode 400 - if the request has an invalid scope
     * @statuscode 409 - if the relationship cannot legally be created
     * @statuscode 500 - on failure, with JSON error message
     */
    // TODO make this an idempotent PUT call
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = GraphModel.class)
    public Response create(RelationshipModel relationshipModel) {

        String scope = relationshipModel.getScope();
        if (scope == null) scope=SCOPE_LOCAL;
        if (scope.equals(SCOPE_GLOBAL) || scope.equals(SCOPE_LOCAL)) {
            GraphModel relationChanges = new GraphModel();

            Response response = this.create_local(relationshipModel);
            if (Status.CREATED.getStatusCode() != response.getStatus()) {
                return response;
            }
            GraphModel createResult = (GraphModel)response.getEntity();
            relationChanges.addReadings(createResult.getReadings());
            relationChanges.addRelationships(createResult.getRelationships());
            Long thisRelId = 0L;
            if (relationChanges.getRelationships().stream().findFirst().isPresent()) // this will always be true
                thisRelId = Long.valueOf(relationChanges.getRelationships().stream().findFirst().get().getId());
            if (scope.equals(SCOPE_GLOBAL)) {
                try (Transaction tx = db.beginTx()) {
                    Node readingA = db.getNodeById(Long.parseLong(relationshipModel.getSource()));
                    Node readingB = db.getNodeById(Long.parseLong(relationshipModel.getTarget()));
                    Node thisTradition = DatabaseService.getTraditionNode(tradId, db);
                    Relationship thisRelation = db.getRelationshipById(thisRelId);

                    // Get all the readings that belong to our tradition
                    ResourceIterable<Node> tradReadings = DatabaseService.returnEntireTradition(thisTradition).nodes();
                    // Pick out the ones that share the readingA text
                    List<Node> ourA = tradReadings.stream().filter(x -> x.hasProperty("text")
                                    && x.getProperty("text").equals(readingA.getProperty("text")))
                            .collect(Collectors.toList());
                    HashMap<Long, HashSet<Long>> ranks = new HashMap<>();
                    for (Node cur_node : ourA) {
                        long node_id = cur_node.getId();
                        long node_rank = (Long) cur_node.getProperty("rank");
                        HashSet<Long> cur_set = ranks.getOrDefault(node_rank, new HashSet<>());
                        cur_set.add(node_id);
                        ranks.putIfAbsent(node_rank, cur_set);
                    }

                    // Pick out the ones that share the readingB text
                    List<Node> ourB = tradReadings.stream().filter(x -> x.hasProperty("text")
                            && x.getProperty("text").equals(readingB.getProperty("text")))
                            .collect(Collectors.toList());
                    RelationshipModel relship;
                    for (Node cur_node : ourB) {
                        long node_id = cur_node.getId();
                        long node_rank = (Long) cur_node.getProperty("rank");

                        HashSet cur_set = ranks.get(node_rank);
                        if (cur_set != null) {
                            for (Object id : cur_set) {
                                relship = new RelationshipModel(thisRelation);
                                relship.setSource(Long.toString((Long) id));
                                relship.setTarget(Long.toString(node_id));
                                // relship.setType(relationshipModel.getType());
                                response = this.create_local(relship);
                                if (Status.NOT_MODIFIED.getStatusCode() != response.getStatus()) {
                                    if (Status.CREATED.getStatusCode() != response.getStatus()) {
                                        return response;
                                    } else {
                                        createResult = (GraphModel) response.getEntity();
                                        relationChanges.addReadings(createResult.getReadings());
                                        relationChanges.addRelationships(createResult.getRelationships());
                                    }
                                }
                            }
                        }
                    }
                    tx.success();
                } catch (Exception e) {
                    return Response.serverError().build();
                }
            }
            // List<String> list = new ArrayList<String>();
            // GenericEntity<List<String>> entity = new GenericEntity<List<String>>(list) {};
            GenericEntity<GraphModel> entity = new GenericEntity<GraphModel>(relationChanges) {};
            return Response.status(Status.CREATED).entity(entity).build();
        }
        return Response.status(Status.BAD_REQUEST).entity("Undefined Scope").build();
    }

    // Create a relationship; return the relationship created as well as any reading nodes whose
    // properties (e.g. rank) have changed.
    private Response create_local(RelationshipModel relationshipModel) {
        GraphModel readingsAndRelationshipModel;
        ArrayList<ReadingModel> changedReadings = new ArrayList<>();
        ArrayList<RelationshipModel> createdRelationships = new ArrayList<>();

        Relationship relationshipAtoB = null;

        try (Transaction tx = db.beginTx()) {
            /*
             * Currently search by id search, because is much faster by measurement. Because
             * the id search is O(n) just go through all ids without care. And the
             *
             */
            Node readingA = db.getNodeById(Long.parseLong(relationshipModel.getSource()));
            Node readingB = db.getNodeById(Long.parseLong(relationshipModel.getTarget()));

            if (!readingA.getProperty("section_id").equals(readingB.getProperty("section_id"))) {
                return Response.status(Status.CONFLICT)
                        .entity(jsonerror("Cannot create relationship across tradition sections"))
                        .build();
            }

            if (isMetaReading(readingA) || isMetaReading(readingB)) {
                return Response.status(Status.CONFLICT)
                        .entity(jsonerror("Cannot set relationship on a meta reading"))
                        .build();
            }

            // Get, or create implicitly, the relationship type node for the given type.
            Node traditionNode = DatabaseService.getTraditionNode(tradId, db);
            RelationTypeModel rmodel = RelationshipService.returnRelationType(traditionNode, relationshipModel.getType());

            // Remove any weak relationships that might conflict
            Boolean colocation = rmodel.getIs_colocation();
            if (colocation) {
                Iterable<Relationship> relsA = readingA.getRelationships(ERelations.RELATED);
                for (Relationship r : relsA) {
                    RelationTypeModel rm = RelationshipService.returnRelationType(traditionNode, r.getProperty("name").toString());
                    if (rm.getIs_weak())
                        r.delete();
                }
                Iterable<Relationship> relsB = readingB.getRelationships(ERelations.RELATED);
                for (Relationship r : relsB) {
                    RelationTypeModel rm = RelationshipService.returnRelationType(traditionNode, r.getProperty("name").toString());
                    if (rm.getIs_weak())
                        r.delete();
                }
            }

            Boolean isCyclic = ReadingService.wouldGetCyclic(readingA, readingB);
            if (isCyclic && colocation) {
                    return Response
                            .status(Status.CONFLICT)
                            .entity(jsonerror("This relationship creation is not allowed, it would result in a cyclic graph."))
                            .build();
            } else if (!isCyclic && !colocation) {
                return Response
                        .status(Status.CONFLICT)
                        .entity(jsonerror("This relationship creation is not allowed. The two readings can be co-located."))
                        .build();
            } // TODO add constraints about witness uniqueness or lack thereof

            // Check if relationship already exists
            Iterable<Relationship> relationships = readingA.getRelationships(ERelations.RELATED);
            for (Relationship relationship : relationships) {
                if (relationship.getOtherNode(readingA).equals(readingB)) {
                    RelationshipModel thisRel = new RelationshipModel(relationship);
                    RelationTypeModel rtm = RelationshipService.returnRelationType(traditionNode, thisRel.getType());
                    if (thisRel.getType().equals(relationshipModel.getType())) {
                        // TODO allow for update of existing relationship
                        tx.success();
                        return Response.status(Status.NOT_MODIFIED).type(MediaType.TEXT_PLAIN_TYPE).build();
                    } else if (rtm.getIs_weak()) {
                        // Rewrite the link that's already there
                        relationshipAtoB = relationship;
                    } else {
                        tx.success();
                        String msg = String.format("Relationship of type %s already exists between readings %s and %s",
                                relationshipModel.getType(), relationshipModel.getSource(), relationshipModel.getTarget());
                        return Response.status(Status.CONFLICT).entity(jsonerror(msg)).build();
                    }
                }
            }
            if (relationshipAtoB == null)
                relationshipAtoB = readingA.createRelationshipTo(readingB, ERelations.RELATED);

            relationshipAtoB.setProperty("type", nullToEmptyString(relationshipModel.getType()));
            relationshipAtoB.setProperty("scope", nullToEmptyString(relationshipModel.getScope()));
            relationshipAtoB.setProperty("annotation", nullToEmptyString(relationshipModel.getAnnotation()));
            relationshipAtoB.setProperty("displayform",
                    nullToEmptyString(relationshipModel.getDisplayform()));
            relationshipAtoB.setProperty("a_derivable_from_b", relationshipModel.getA_derivable_from_b());
            relationshipAtoB.setProperty("b_derivable_from_a", relationshipModel.getB_derivable_from_a());
            relationshipAtoB.setProperty("alters_meaning", relationshipModel.getAlters_meaning());
            relationshipAtoB.setProperty("is_significant", relationshipModel.getIs_significant());
            relationshipAtoB.setProperty("non_independent", relationshipModel.getNon_independent());
            relationshipAtoB.setProperty("reading_a", readingA.getProperty("text"));
            relationshipAtoB.setProperty("reading_b", readingB.getProperty("text"));

            // Recalculate the ranks, if necessary
            Long rankA = (Long) readingA.getProperty("rank");
            Long rankB = (Long) readingB.getProperty("rank");
            if (!rankA.equals(rankB) && colocation) {
                // Which one is the lower-ranked reading? Promote it, and recalculate from that point
                Long higherRank = rankA < rankB ? rankB : rankA;
                Node lowerRanked = rankA < rankB ? readingA : readingB;
                lowerRanked.setProperty("rank", higherRank);
                changedReadings.add(new ReadingModel(lowerRanked));
                List<Node> changedRank = ReadingService.recalculateRank(lowerRanked);
                for (Node cr : changedRank) changedReadings.add(new ReadingModel(cr));
            }

            createdRelationships.add(new RelationshipModel(relationshipAtoB));
            readingsAndRelationshipModel = new GraphModel(changedReadings, createdRelationships);

            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.status(Response.Status.CREATED).entity(readingsAndRelationshipModel).build();
    }

    /**
     * Checks, if a reading is a "Meta"-reading
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
     * Remove the relationship specified. There should be only one.
     *
     * @summary Delete relationship
     * @param relationshipModel - the JSON specification of the relationship(s) to delete
     * @return A list of all relationships that were removed.
     * @statuscode 200 - on success
     * @statuscode 400 - if an invalid scope was specified
     * @statuscode 404 - if no matching relationship was found
     * @statuscode 500 - on failure, with JSON error message
     */
    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.RelationshipModel>")
    public Response delete(RelationshipModel relationshipModel) {
        ArrayList<RelationshipModel> deleted = new ArrayList<>();

        switch (relationshipModel.getScope()) {
            case "local":

                try (Transaction tx = db.beginTx()) {

                    Node readingA = db.getNodeById(Long.parseLong(relationshipModel.getSource()));
                    Node readingB = db.getNodeById(Long.parseLong(relationshipModel.getTarget()));

                    Iterable<Relationship> relationships = readingA.getRelationships(ERelations.RELATED);

                    Relationship relationshipAtoB = null;
                    for (Relationship relationship : relationships) {
                        if ((relationship.getStartNode().equals(readingA)
                                || relationship.getEndNode().equals(readingA))
                                && relationship.getStartNode().equals(readingB)
                                || relationship.getEndNode().equals(readingB)) {
                            relationshipAtoB = relationship;
                        }
                    }

                    if (relationshipAtoB == null) {
                        return Response.status(Status.NOT_FOUND).entity(jsonerror("Relationship not found")).build();
                    } else {
                        RelationshipModel relInfo = new RelationshipModel(relationshipAtoB);
                        relationshipAtoB.delete();
                        deleted.add(relInfo);
                    }
                    tx.success();
                } catch (Exception e) {
                    return Response.serverError().entity(jsonerror(e.getMessage())).build();
                }
                break;

            case "document":
                Node startNode = DatabaseService.getStartNode(tradId, db);

                try (Transaction tx = db.beginTx()) {
                    for (Relationship rel : db.traversalDescription().depthFirst()
                            .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                            .relationships(ERelations.RELATED, Direction.BOTH)
                            .uniqueness(Uniqueness.NODE_GLOBAL)
                            .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL)
                            .traverse(startNode)
                            .relationships()) {
                        if (rel.getType().name().equals(ERelations.RELATED.name())) {
                            Node readingA = db.getNodeById(Long.parseLong(relationshipModel.getSource()));
                            Node readingB = db.getNodeById(Long.parseLong(relationshipModel.getTarget()));

                            if ((rel.getStartNode().getProperty("text").equals(readingA.getProperty("text"))
                                    || rel.getEndNode().getProperty("text").equals(readingA.getProperty("text")))
                                    && (rel.getStartNode().getProperty("text").equals(readingB.getProperty("text"))
                                    || rel.getEndNode().getProperty("text").equals(readingB.getProperty("text")))) {
                                RelationshipModel relInfo = new RelationshipModel(rel);
                                rel.delete();
                                deleted.add(relInfo);
                            }
                        }
                    }
                    tx.success();
                } catch (Exception e) {
                    return Response.serverError().entity(jsonerror(e.getMessage())).build();
                }
                break;

            default:
                return Response.status(Status.BAD_REQUEST).entity(jsonerror("Undefined Scope")).build();
        }
        return Response.status(Response.Status.OK).entity(deleted).build();
    }
    
    /**
     * Removes a relationship by internal ID.
     *
     * @summary Delete relationship by ID
     * @param relationshipId - the ID of the relationship to delete
     * @return The deleted relationship
     * @statuscode 200 - on success
     * @statuscode 403 - if the given ID does not belong to a relationship
     * @statuscode 500 - on failure, with JSON error message
     */
    @DELETE
    @Path("{relationshipId}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = RelationshipModel.class)
    public Response deleteById(@PathParam("relationshipId") String relationshipId) {
        RelationshipModel relationshipModel;

        try (Transaction tx = db.beginTx()) {
            Relationship relationship = db.getRelationshipById(Long.parseLong(relationshipId));
            if(relationship.getType().name().equals("RELATED")) {
                relationshipModel = new RelationshipModel(relationship);
                relationship.delete();
            } else {
                return Response.status(Status.FORBIDDEN).entity(jsonerror("This is not a relationship link")).build();
            }
            tx.success();
        } catch (Exception e) {
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(relationshipModel).build();
    }
    
    private String nullToEmptyString(String str){
        return str == null ? "" : str;
    }
}