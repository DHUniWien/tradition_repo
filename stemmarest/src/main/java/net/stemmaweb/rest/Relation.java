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
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.Uniqueness;


/**
 * Comprises all the api calls related to a relation.
 * can be called by using http://BASE_URL/relation
 * @author PSE FS 2015 Team2
 */

@Path("/relation")
public class Relation implements IResource {

    private GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private GraphDatabaseService db = dbServiceProvider.getDatabase();

    private static final String SCOPE_LOCAL = "local";
    private static final String SCOPE_GLOBAL = "document";

    /**
     * Creates a new relationship between the two nodes specified.
     *
     * @param relationshipModel
     * @return Http Response 201 and a model containing the created relationship
     *         and the readings involved in JSON on success or an ERROR in JSON
     *         format
     */
    @POST
    @Path("createrelationship")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(RelationshipModel relationshipModel) {

        String scope = relationshipModel.getScope();
        if (scope == null) scope=SCOPE_LOCAL;
        if (scope.equals(SCOPE_GLOBAL) || scope.equals(SCOPE_LOCAL)) {
            ArrayList<GraphModel> entities = new ArrayList<>();

            Response response = this.create_local(relationshipModel);
            if (Status.CREATED.getStatusCode() != response.getStatus()) {
                return response;
            }
            entities.add((GraphModel)response.getEntity());
            if (scope.equals(SCOPE_GLOBAL)) {
                try (Transaction tx = db.beginTx()) {
                    Node readingA = db.getNodeById(Long.parseLong(relationshipModel.getSource()));
                    Node readingB = db.getNodeById(Long.parseLong(relationshipModel.getTarget()));

                    HashMap<Long, HashSet> ranks = new HashMap<>();
                    Result resultA = db.execute("match (n {text: '" + readingA.getProperty("text") + "'}) return n");
                    Iterator<Node> nodesA = resultA.columnAs("n");
                    while (nodesA.hasNext()) {
                        Node cur_node = nodesA.next();
                        long node_id = cur_node.getId();
                        long node_rank = (Long) cur_node.getProperty("rank");
                        HashSet cur_set = ranks.getOrDefault(node_rank, new HashSet<>());
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
                                if (Status.CREATED.getStatusCode() != response.getStatus()) {
                                    return response;
                                } else {
                                    entities.add((GraphModel)response.getEntity());
                                }
                            }
                        }
                    }
//                    recalculateRanks(readingA);
                    tx.success();
                } catch (Exception e) {
                    return Response.status(Status.INTERNAL_SERVER_ERROR).build();
                }
            }
            // List<String> list = new ArrayList<String>();
            // GenericEntity<List<String>> entity = new GenericEntity<List<String>>(list) {};
            GenericEntity<ArrayList<GraphModel>> entity = new GenericEntity<ArrayList<GraphModel>>(entities) {};
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

            if (false == readingA.getProperty("tradition_id").equals(readingB.getProperty("tradition_id"))) {
                return Response.status(Status.CONFLICT)
                        .entity("Cannot create relationship across traditions")
                        .build();
            }

            if (isMetaReading(readingA) || isMetaReading(readingB)) {
                return Response.status(Status.CONFLICT)
                        .entity("Cannot set relationship on a meta reading")
                        .build();
            }

            if (ReadingService.wouldGetCyclic(db, readingA, readingB)) {
                if (!relationshipModel.getType().equals("transposition") &&
                        !relationshipModel.getType().equals("repetition")) {
                    return Response
                            .status(Status.CONFLICT)
                            .entity("This relationship creation is not allowed. Merging the two related readings would result in a cyclic graph.")
                            .build();
                }
            } else if (relationshipModel.getType().equals("transposition")
                    || relationshipModel.getType().equals("repetition")) {
                return Response
                        .status(Status.CONFLICT)
                        .entity("This relationship creation is not allowed. The two readings can be aligned.")
                        .build();
            } // TODO add constraints about witness uniqueness or lack thereof

            relationshipAtoB = readingA.createRelationshipTo(readingB, ERelations.RELATED);
            relationshipAtoB.setProperty("type", nullToEmptyString(relationshipModel.getType()));
            relationshipAtoB.setProperty("a_derivable_from_b",
                    nullToEmptyString(relationshipModel.getA_derivable_from_b()));
            relationshipAtoB.setProperty("alters_meaning",
                    nullToEmptyString(relationshipModel.getAlters_meaning()));
            relationshipAtoB.setProperty("b_derivable_from_a",
                    nullToEmptyString(relationshipModel.getB_derivable_from_a()));
            relationshipAtoB.setProperty("displayform",
                    nullToEmptyString(relationshipModel.getDisplayform()));
            relationshipAtoB.setProperty("is_significant",
                    nullToEmptyString(relationshipModel.getIs_significant()));
            relationshipAtoB.setProperty("non_independent",
                    nullToEmptyString(relationshipModel.getNon_independent()));
            relationshipAtoB.setProperty("reading_a",
                    nullToEmptyString(relationshipModel.getReading_a()));
            relationshipAtoB.setProperty("reading_b",
                    nullToEmptyString(relationshipModel.getReading_b()));
            relationshipAtoB.setProperty("scope", nullToEmptyString(relationshipModel.getScope()));

            changedReadings.add(new ReadingModel(readingA));
            changedReadings.add(new ReadingModel(readingB));
            createdRelationships.add(new RelationshipModel(relationshipAtoB));
            readingsAndRelationshipModel = new GraphModel(changedReadings, createdRelationships);

            tx.success();
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.status(Response.Status.CREATED).entity(readingsAndRelationshipModel).build();
    }

    /*
     * Recalculate ranks starting from 'startNode'
     * You would typically use it after inserting a RELATION or a new Node into the graph,
     * where the startNode will be one of the RELATION-nodes or the new node itself.
     * In addition, you can also set the Rank for the given node.
     */
    private void recalculateRanks(Node startNode, int startRank) {
        Comparator<Node> rankComparator = (n1, n2) -> {
            int compVal = Long.valueOf((Long) n1.getProperty("rank"))
                    .compareTo(Long.valueOf((Long) n2.getProperty("rank")));
            if (compVal == 0) {
                compVal = Long.valueOf(n1.getId()).compareTo(Long.valueOf(n2.getId()));
            }
            return compVal;
        };

        SortedSet<Node> nodesToProcess = new TreeSet<>(rankComparator);
        ArrayList<Node> nodesToUpdate = new ArrayList<>();

        long startNodeRank = startRank;

        Iterable<Relationship> relationships = startNode.getRelationships(Direction.INCOMING, ERelations.SEQUENCE);
        for (Relationship relationship : relationships) {
            startNodeRank = Math.max(startNodeRank, (long)relationship.getStartNode().getProperty("rank") + 1L);
        }
        if ((long)startNode.getProperty("rank") < startNodeRank) {
            startNode.setProperty("rank", startNode);
        }

        Node currentNode = startNode;
        Node iterNode;
        long currentNodeRank = (long)currentNode.getProperty("rank");

        while (currentNode != null) {
            // Look, if a RELATED node has a higher rank
            long relatedNodeRank = 0L;
            relationships = currentNode.getRelationships(ERelations.RELATED);
            if (relationships.iterator().hasNext() == true) {
                for (Relationship relationship : relationships) {
                    Node otherNode = relationship.getOtherNode(currentNode);
                    relatedNodeRank = Math.max(relatedNodeRank, (long) otherNode.getProperty("rank"));
                }

                if (currentNodeRank != relatedNodeRank) {
                    // We have to update the current Node
                    currentNode.setProperty("rank", Math.max(relatedNodeRank, (long) currentNode.getProperty("rank")));
                    currentNodeRank = (long) currentNode.getProperty("rank");

                    // UPDATE nodes on RELATED vertices, if necessary
                    relationships = currentNode.getRelationships(ERelations.RELATED);
                    for (Relationship relationship : relationships) {
                        if (relationship.getStartNode().equals(currentNode)) {
                            iterNode = relationship.getEndNode();
                        } else {
                            iterNode = relationship.getStartNode();
                        }
                        if ((long) iterNode.getProperty("rank") < currentNodeRank) {
                            iterNode.setProperty("rank", currentNodeRank);
                            nodesToProcess.add(iterNode);
                        }
                    }
                }
            }

            // Update nodes on OUTGOING & SEQUENCE vertices, if necessary
            relationships = currentNode.getRelationships(Direction.OUTGOING, ERelations.SEQUENCE);
            // OUTGOING includes SEQUENCE (outgoing) and RELATED
            for (Relationship relationship : relationships) {
                iterNode = relationship.getEndNode();
                if ((long) iterNode.getProperty("rank") <= currentNodeRank) {
                    iterNode.setProperty("rank", currentNodeRank + 1L);
                    nodesToProcess.add(iterNode);
                }
            }

            nodesToUpdate.add(currentNode);
            if (nodesToProcess.isEmpty()) {
                currentNode = null;
            } else {
                currentNode = nodesToProcess.first();
                nodesToProcess.remove(currentNode);
            }
        }
    }

    /*
            SortedSet<Node> processing_nodes = new TreeSet<>((n1, n2) -> (int) n1.getProperty("rank") - (int) n2.getProperty("rank"));
     */

    private void recalculateRanks(Node startNode) {
        recalculateRanks(startNode, -1);
    }

    /**
     * Checks, if a reading is a "Meta"-reading
     *
     * @param reading
     * @return
     */
    private boolean isMetaReading(Node reading) {
        if (reading != null &&
                ((reading.hasProperty("is_lacuna") && reading.getProperty("is_lacuna").equals("1")) ||
                        (reading.hasProperty("is_start") && reading.getProperty("is_start").equals("1")) ||
                        (reading.hasProperty("is_ph") && reading.getProperty("is_ph").equals("1")) ||
                        (reading.hasProperty("is_end") && reading.getProperty("is_end").equals("1"))
                )) {
            return true;
        }
        return false;
    }

    /**
     * Gets all the next nodes with the given constraints.
     *
     * @param reading
     * @param direction
     * @param depth
     * @return
     */
    private ResourceIterable<Node> getNextNodes(Node reading, Direction direction, int depth) {
        return db.traversalDescription().breadthFirst().relationships(ERelations.SEQUENCE, direction)
                .evaluator(Evaluators.excludeStartPosition()).evaluator(Evaluators.toDepth(depth))
                .uniqueness(Uniqueness.NODE_GLOBAL).traverse(reading).nodes();
    }

    /**
     * Get a list of all relationships from a given tradition.
     *
     * @param tradId
     * @return a list of the relationships in JSON on success or an ERROR in
     *         JSON format
     */
    @GET
    @Path("getrelationship/fromtradition/{tradId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRelationship(@PathParam("tradId") String tradId,
                                    @QueryParam("node1")String node1,
                                    @QueryParam("node2") String node2) {
        RelationshipType relType = null;

        try (Transaction tx = db.beginTx()) {
            Node relStartNode = db.getNodeById(Long.parseLong(node1));
            Node relEndNode = db.getNodeById(Long.parseLong(node2));
            for (Relationship rel: relStartNode.getRelationships()) {
                if(relStartNode.equals(rel.getStartNode()) && relEndNode.equals(rel.getEndNode())) {
                    relType = rel.getType();
                }
            }
            tx.success();
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        if (relType.equals(null))
            return Response.status(Status.NOT_FOUND).entity("no relationships were found").build();
        GenericEntity<RelationshipType> reship = new GenericEntity<RelationshipType>(relType) {};
        return Response.ok(relType.name()).build();
    }


    /**
     * Get a list of all relationships from a given tradition.
     *
     * @param tradId
     * @return a list of the relationships in JSON on success or an ERROR in
     *         JSON format
     */
    @GET
    @Path("getallrelationships/fromtradition/{tradId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllRelationships(@PathParam("tradId") String tradId) {
        ArrayList<RelationshipModel> relationships = new ArrayList<>();

        try (Transaction tx = db.beginTx()) {
            Node startNode = DatabaseService.getStartNode(tradId, db);
            for (Relationship rel: db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .relationships(ERelations.RELATED, Direction.BOTH)
                    .uniqueness(Uniqueness.NODE_GLOBAL)
                    .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL)
                    .traverse(startNode)
                    .relationships()) {
                if(rel.getType().name().equals(ERelations.RELATED.name())){
                    RelationshipModel tempRel = new RelationshipModel(rel);
                    relationships.add(tempRel);
                }
            }
            tx.success();
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        if (relationships.size() ==0)
            return Response.status(Status.NOT_FOUND).entity("no relationships were found").build();

        return Response.ok(relationships).build();
    }


    /**
     * Remove all relationships, as it is done in
     * https://github.com/tla/stemmaweb
     * /blob/master/lib/stemmaweb/Controller/Relation.pm line 271) in
     * Relationships of type RELATED between the two nodes.
     *
     * @param relationshipModel
     * @param tradId
     * @return HTTP Response 404 when no node was found, 200 When relationships
     *         where removed
     */
    @DELETE
    @Path("deleterelationship/fromtradition/{tradId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(RelationshipModel relationshipModel, @PathParam("tradId") String tradId) {
        long deleted_relations = 0L; // Number of deleted relationships

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
                        relationshipAtoB.delete();
                        deleted_relations += 1L;
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
                                rel.delete();
                                deleted_relations += 1L;
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
        return Response.status(Response.Status.OK).entity(deleted_relations).build();
    }
    
    /**
     * Removes a relationship by ID
     *
     * @param relationshipId
     * @return HTTP Response 404 when no Relationship was found with id, 200 and
     *         a model of the relationship in JSON when the Relationship was
     *         removed
     */
    @DELETE
    @Path("deleterelationshipbyid/withrelationship/{relationshipId}")
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