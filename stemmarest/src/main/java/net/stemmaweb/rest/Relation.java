package net.stemmaweb.rest;

import java.util.ArrayList;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.model.GraphModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.ReadingService;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Uniqueness;


/**
 * Comprises all the api calls related to a relation.
 * can be called by using http://BASE_URL/relation
 * @author PSE FS 2015 Team2
 */

public class Relation {

    private GraphDatabaseService db;
    private String tradId;

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
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(RelationshipModel relationshipModel) {
        GraphModel readingsAndRelationshipModel;
        ArrayList<ReadingModel> changedReadings = new ArrayList<>();
        ArrayList<RelationshipModel> createdRelationships = new ArrayList<>();

        Relationship relationshipAtoB;

        try (Transaction tx = db.beginTx()) {
            /*
             * Currently search by id search because is much faster by measurement. Because
             * the id search is O(n) just go through all ids without care. And the
             *
             */
            Node readingA = db.getNodeById(Long.parseLong(relationshipModel.getSource()));
            Node readingB = db.getNodeById(Long.parseLong(relationshipModel.getTarget()));

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
        }
        catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return Response.status(Response.Status.CREATED).entity(readingsAndRelationshipModel).build();
    }

    /**
     * Remove all instances of the relationship specified.
     *
     * @param relationshipModel - the JSON specification of the relationship(s) to delete
     * @return HTTP Response 404 when no node was found, 200 When relationships
     *         where removed
     */
    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    public Response delete(RelationshipModel relationshipModel) {
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
                        return Response.status(Status.NOT_FOUND).build();
                    } else {
                        relationshipAtoB.delete();
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
        return Response.status(Response.Status.OK).build();
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

        try (Transaction tx = db.beginTx())
        {
            Relationship relationship = db.getRelationshipById(Long.parseLong(relationshipId));
            if(relationship.getType().name().equals("RELATED")){
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