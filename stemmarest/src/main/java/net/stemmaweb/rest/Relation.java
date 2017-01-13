package net.stemmaweb.rest;

import java.util.*;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.model.GraphModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.ReadingService;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Uniqueness;


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


    public Relation (String traditionId) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        tradId = traditionId;
    }

    /**
     * Creates a new relationship between the two nodes specified.
     *
     * @param relationshipModel - JSON structure of the relationship to create
     * @return Http Response 201 and a model containing the created relationship
     *         and the readings involved in JSON on success or an ERROR in JSON
     *         format
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
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
            if (scope.equals(SCOPE_GLOBAL)) {
                try (Transaction tx = db.beginTx()) {
                    Node readingA = db.getNodeById(Long.parseLong(relationshipModel.getSource()));
                    Node readingB = db.getNodeById(Long.parseLong(relationshipModel.getTarget()));

                    HashMap<Long, HashSet<Long>> ranks = new HashMap<>();
                    Result resultA = db.execute("match (n {text: '" + readingA.getProperty("text") + "'}) return n");
                    Iterator<Node> nodesA = resultA.columnAs("n");
                    while (nodesA.hasNext()) {
                        Node cur_node = nodesA.next();
                        long node_id = cur_node.getId();
                        long node_rank = (Long) cur_node.getProperty("rank");
                        HashSet<Long> cur_set = ranks.getOrDefault(node_rank, new HashSet<>());
                        cur_set.add(node_id);
                        ranks.putIfAbsent(node_rank, cur_set);
                    }

                    Result resultB = db.execute("match (n {text: '" + readingB.getProperty("text") + "'}) return n");
                    Iterator<Node> nodesB = resultB.columnAs("n");
                    RelationshipModel relship;
                    while (nodesB.hasNext()) {
                        Node cur_node = nodesB.next();
                        long node_id = cur_node.getId();
                        long node_rank = (Long) cur_node.getProperty("rank");

                        HashSet cur_set = ranks.get(node_rank);
                        if (cur_set != null) {
                            for (Object id : cur_set) {
                                relship = new RelationshipModel();
                                relship.setSource(Long.toString((Long) id));
                                relship.setTarget(Long.toString(node_id));
                                relship.setType(relationshipModel.getType());
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
                    return Response.status(Status.INTERNAL_SERVER_ERROR).build();
                }
            }
            // List<String> list = new ArrayList<String>();
            // GenericEntity<List<String>> entity = new GenericEntity<List<String>>(list) {};
            GenericEntity<GraphModel> entity = new GenericEntity<GraphModel>(relationChanges) {};
            return Response.status(Status.CREATED).entity(entity).build();
        }
        return Response.status(Status.BAD_REQUEST).entity("Undefined Scope").build();
    }

    private Response create_local(RelationshipModel relationshipModel) {
        GraphModel readingsAndRelationshipModel;
        ArrayList<ReadingModel> changedReadings = new ArrayList<>();
        ArrayList<RelationshipModel> createdRelationships = new ArrayList<>();

        Relationship relationshipAtoB;

        try (Transaction tx = db.beginTx()) {
            /*
             * Currently search by id search, because is much faster by measurement. Because
             * the id search is O(n) just go through all ids without care. And the
             *
             */
            Node readingA = db.getNodeById(Long.parseLong(relationshipModel.getSource()));
            Node readingB = db.getNodeById(Long.parseLong(relationshipModel.getTarget()));

            if (!readingA.getProperty("tradition_id").equals(readingB.getProperty("tradition_id"))) {
                return Response.status(Status.CONFLICT)
                        .entity("Cannot create relationship across traditions")
                        .build();
            }

            if (isMetaReading(readingA) || isMetaReading(readingB)) {
                return Response.status(Status.CONFLICT)
                        .entity("Cannot set relationship on a meta reading")
                        .build();
            }

            Boolean isCyclic = ReadingService.wouldGetCyclic(db, readingA, readingB);
            Boolean isLocationVariant = !relationshipModel.getType().equals("transposition") &&
                    !relationshipModel.getType().equals("repetition");
            if (isCyclic && isLocationVariant) {
                    return Response
                            .status(Status.CONFLICT)
                            .entity("This relationship creation is not allowed, it would result in a cyclic graph.")
                            .build();
            } else if (!isCyclic && !isLocationVariant) {
                return Response
                        .status(Status.CONFLICT)
                        .entity("This relationship creation is not allowed. The two readings can be co-located.")
                        .build();
            } // TODO add constraints about witness uniqueness or lack thereof

            // Check if relationship already exists
            found_existing_relationship: {
                Iterable<Relationship> relationships = readingA.getRelationships(ERelations.RELATED);
                for (Relationship relationship : relationships) {
                    if (relationship.getOtherNode(readingA).equals(readingB)) {
                        if (relationship.getProperty("type").equals(relationshipModel.getType())) {
                            tx.success();
                            return Response.status(Status.NOT_MODIFIED).build();
                        }
                        // TODO SK->TLA ask about additional rules!
                        String oldRelType = (String) relationship.getProperty("type");
                        if (oldRelType.equals("collated")) {
                            // We use the existing relation, instead of delete it and create a new one
                            relationshipAtoB = relationship;
                            break found_existing_relationship;
                        }
                    }
                }
                relationshipAtoB = readingA.createRelationshipTo(readingB, ERelations.RELATED);
            }

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

            changedReadings.add(new ReadingModel(readingA));
            changedReadings.add(new ReadingModel(readingB));
            createdRelationships.add(new RelationshipModel(relationshipAtoB));
            readingsAndRelationshipModel = new GraphModel(changedReadings, createdRelationships);

            // Recalculate the ranks, if necessary
            Long rankA = (Long) readingA.getProperty("rank");
            Long rankB = (Long) readingB.getProperty("rank");
            if (!rankA.equals(rankB)  && isLocationVariant) {
                // Which one is the lower-ranked reading?
                Long nodeId = rankA < rankB ? readingA.getId() : readingB.getId();
                new Tradition(tradId).recalculateRank(nodeId);
            }

            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
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
                ((reading.hasProperty("is_lacuna") && reading.getProperty("is_lacuna").equals("1")) ||
                        (reading.hasProperty("is_start") && reading.getProperty("is_start").equals("1")) ||
                        (reading.hasProperty("is_ph") && reading.getProperty("is_ph").equals("1")) ||
                        (reading.hasProperty("is_end") && reading.getProperty("is_end").equals("1"))
                );
    }

    /**
     * Checks if a relationship between the two nodes specified would produce a
     * cross-relationship. A cross relationship is a relationship that
     * crosses another one created before which is not allowed.
     * Remove all instances of the relationship specified.
     *
     * @param relationshipModel - the JSON specification of the relationship(s) to delete
     * @return HTTP Response 404 when no node was found, 200 and list of relationship edges
     *    removed on success
     */
    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")

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
                        return Response.status(Status.NOT_FOUND).entity(0L).build();
                    } else {
                        RelationshipModel relInfo = new RelationshipModel(relationshipAtoB);
                        relationshipAtoB.delete();
                        deleted.add(relInfo);
                    }
                    tx.success();
                } catch (Exception e) {
                    return Response.status(Status.INTERNAL_SERVER_ERROR).build();
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
                    return Response.status(Status.INTERNAL_SERVER_ERROR).build();
                }
                break;

            default:
                return Response.status(Status.BAD_REQUEST).entity("Undefined Scope").build();
        }
        return Response.status(Response.Status.OK).entity(deleted).build();
    }
    
    /**
     * Removes a relationship by ID
     *
     * @param relationshipId - the ID of the relationship to delete
     * @return HTTP Response 404 when no Relationship was found with id, 200 and
     *         a model of the relationship in JSON when the Relationship was
     *         removed
     */
    @DELETE
    @Path("{relationshipId}")
    public Response deleteById(@PathParam("relationshipId") String relationshipId) {
        RelationshipModel relationshipModel;

        try (Transaction tx = db.beginTx()) {
            Relationship relationship = db.getRelationshipById(Long.parseLong(relationshipId));
            if(relationship.getType().name().equals("RELATED")) {
                relationshipModel = new RelationshipModel(relationship);
                relationship.delete();
            } else {
                return Response.status(Status.FORBIDDEN).build();
            }
            tx.success();
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(relationshipModel).build();
    }
    
    private String nullToEmptyString(String str){
        return str == null ? "" : str;
    }
}