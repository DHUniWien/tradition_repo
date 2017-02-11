package net.stemmaweb.rest;

import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Uniqueness;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by tla on 11/02/2017.
 */
public class Section {
    private GraphDatabaseService db;
    private String tradId;
    private String sectId;

    public Section (String traditionId, String sectionId) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        tradId = traditionId;
        sectId = sectionId;
    }


    @DELETE
    public Response deleteSection() {
        String result = "No section with the given ID found";
        try (Transaction tx = db.beginTx()) {
            // Find the section by ID and check that it belongs to this tradition.
            Node foundSection = db.getNodeById(Long.valueOf(sectId));
            if (foundSection != null) {
                Node traditionNode = foundSection.getSingleRelationship(ERelations.PART, Direction.INCOMING)
                        .getStartNode();
                if (traditionNode != null &&
                        traditionNode.getProperty("id").toString().equals(tradId)) {
                    // Find the section either side of this one and connect them if necessary.
                    Node priorSection = null;
                    Node nextSection = null;
                    if (foundSection.hasRelationship(Direction.INCOMING, ERelations.NEXT)) {
                        Relationship incomingRel = foundSection.getSingleRelationship(ERelations.NEXT, Direction.INCOMING);
                        priorSection = incomingRel.getStartNode();
                        incomingRel.delete();
                    }
                    if (foundSection.hasRelationship(Direction.OUTGOING, ERelations.NEXT)) {
                        Relationship outgoingRel = foundSection.getSingleRelationship(ERelations.NEXT, Direction.OUTGOING);
                        nextSection = outgoingRel.getEndNode();
                        outgoingRel.delete();
                    }
                    if (priorSection != null && nextSection != null) {
                        priorSection.createRelationshipTo(nextSection, ERelations.NEXT);
                    }
                    // Remove everything to do with this section.
                    Set<Relationship> removableRelations = new HashSet<>();
                    Set<Node> removableNodes = new HashSet<>();
                    db.traversalDescription()
                            .depthFirst()
                            .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                            .relationships(ERelations.COLLATION, Direction.OUTGOING)
                            .relationships(ERelations.LEMMA_TEXT, Direction.OUTGOING)
                            .relationships(ERelations.HAS_END, Direction.OUTGOING)
                            .relationships(ERelations.RELATED, Direction.OUTGOING)
                            .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL)
                            .traverse(foundSection)
                            .nodes().forEach(x -> {
                        x.getRelationships().forEach(removableRelations::add);
                        removableNodes.add(x);
                    });

                /*
                 * Remove the nodes and relations
                 */
                    removableRelations.forEach(Relationship::delete);
                    removableNodes.forEach(Node::delete);
                    result = "OK";
                }
            }
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        if (result.equals("OK"))
            return Response.ok().build();
        else
            return Response.status(Response.Status.NOT_FOUND).entity(result).build();
    }

    @GET
    @Path("/witnesses")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response getAllWitnessInSection() {
        Node traditionNode = DatabaseService.getTraditionNode(tradId, db);
        if (traditionNode == null)
            return Response.status(Response.Status.NOT_FOUND).entity("tradition not found").build();

        ArrayList<WitnessModel> witnessList = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            Node sectionNode = db.findNode(Nodes.SECTION, "id", sectId);
            if (sectionNode == null)
                return Response.status(Response.Status.NOT_FOUND).entity("section not found").build();
            Relationship rel = sectionNode.getSingleRelationship(ERelations.PART, Direction.INCOMING);
            if (rel == null || rel.getStartNode().getId() != traditionNode.getId()) {
                return Response.status(Response.Status.NOT_FOUND).entity("this section is not part of this tradition").build();
            }

            for (Node m : DatabaseService.getRelated(sectionNode, ERelations.COLLATION)) {
                for (Relationship relationship : m.getRelationships(ERelations.SEQUENCE)) {
                    ArrayList<Node> traditionWitnesses = DatabaseService.getRelated(traditionNode, ERelations.HAS_WITNESS);
                    for (String sigil : (String[]) relationship.getProperty("witnesses")) {
                        for (Node curWitness : traditionWitnesses) {
                            if (sigil.equals(curWitness.getProperty("sigil"))) {
                                witnessList.add(new WitnessModel(curWitness));
                                traditionWitnesses.remove(curWitness);
                                break;
                            }
                        }
                    }
                }
            }
            tx.success();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(witnessList).build();
    }

    // POST section/ID/merge/ID

    // PUT section/ID/orderAfter/ID

    // POST section/ID/splitAtRank/RK
}
