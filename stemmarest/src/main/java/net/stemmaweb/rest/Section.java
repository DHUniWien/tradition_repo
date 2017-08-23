package net.stemmaweb.rest;

import net.stemmaweb.exporter.DotExporter;
import net.stemmaweb.model.SectionModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.Uniqueness;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;

import static net.stemmaweb.services.ReadingService.addWitnessLink;
import static net.stemmaweb.services.ReadingService.removePlaceholder;

/*
 * Created by tla on 11/02/2017.
 */
public class Section {
    private GraphDatabaseService db;
    private String tradId;
    private String sectId;

    Section(String traditionId, String sectionId) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        tradId = traditionId;
        sectId = sectionId;
    }

    // Delegation
    @Path("/witness/{sigil}")
    public Witness getWitnessFromSection(@PathParam("sigil") String sigil) {
        return new Witness(tradId, sectId, sigil);
    }


    // Base paths
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response getSectionInfo() {
        SectionModel result;
        try (Transaction tx = db.beginTx()) {
            result = new SectionModel(db.getNodeById(Long.valueOf(sectId)));
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok().entity(result).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response updateSectionInfo(SectionModel newInfo) {
        try (Transaction tx = db.beginTx()) {
            Node thisSection = db.getNodeById(Long.valueOf(sectId));
            if (newInfo.getName() != null)
                thisSection.setProperty("name", newInfo.getName());
            if (newInfo.getLanguage() != null)
                thisSection.setProperty("language", newInfo.getLanguage());
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return getSectionInfo();
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
                    removeFromSequence(foundSection);
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
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(witnessList).build();
    }

    // PUT section/ID/orderAfter/ID
    @PUT
    @Path("/orderAfter/{priorSectID}")
    public Response reorderSectionAfter(@PathParam("priorSectID") String priorSectID) {
        try (Transaction tx = db.beginTx()) {
            Node thisSection = db.getNodeById(Long.valueOf(sectId));

            // Check that the requested section exists and is part of the tradition
            Node priorSection = db.getNodeById(Long.valueOf(priorSectID));
            if (priorSection == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            Node pnTradition = DatabaseService.getTraditionNode(priorSection, db);
            if (!pnTradition.getProperty("id").toString().equals(tradId))
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Section " + priorSectID + " doesn't belong to this tradition").build();

            // Check for and remove the old "next" link from the given prior
            Node latterSection = null;
            if (priorSection.hasRelationship(Direction.OUTGOING, ERelations.NEXT)) {
                Relationship oldSeq = priorSection.getSingleRelationship(ERelations.NEXT, Direction.OUTGOING);
                latterSection = oldSeq.getEndNode();
                oldSeq.delete();
            }

            // Remove our node from its existing sequence
            removeFromSequence(thisSection);

            // Link it up to the prior
            priorSection.createRelationshipTo(thisSection, ERelations.NEXT);
            // ...and to the old "next" if it exists
            if (latterSection != null) {
                thisSection.createRelationshipTo(latterSection, ERelations.NEXT);
            }

            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok().build();
    }

    @POST
    @Path("/splitAtRank/{rankstr}")
    public Response splitAtRank (@PathParam("rankstr") String rankstr) {
        Long rank = Long.valueOf(rankstr);
        // Get the reading(s) at the given rank, and at the prior rank
        Node startNode = DatabaseService.getStartNode(sectId, db);
        Long newSectionId;

        try (Transaction tx = db.beginTx()) {
            Node thisSection = db.getNodeById(Long.valueOf(sectId));

            // Make a list of readings that belong to the requested rank as well
            // as the prior rank
            ArrayList<Node> thisRank = new ArrayList<>();
            ArrayList<Node> priorRank = new ArrayList<>();
            ResourceIterable<Node> sectionReadings = db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .evaluator(Evaluators.all())
                    .uniqueness(Uniqueness.NODE_GLOBAL).traverse(startNode)
                    .nodes();
            for (Node n : sectionReadings) {
                Long nrank = (Long) n.getProperty("rank");
                if (rank.equals(nrank)) {
                    thisRank.add(n);
                } else if (rank.equals(nrank + 1)) {
                    priorRank.add(n);
                }
            }

            // Make sure we have readings at the requested rank in this section
            if (thisRank.size() == 0)
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Rank not found within section").build();

            // Make a new section node and insert it into the sequence
            Node newSection = db.createNode(Nodes.SECTION);
            DatabaseService.getTraditionNode(thisSection, db).createRelationshipTo(newSection, ERelations.PART);
            newSection.setProperty("name", thisSection.getProperty("name") + " split");
            newSectionId = newSection.getId();
            Section newSectionRest = new Section(tradId, String.valueOf(newSection.getId()));
            Response reorder = newSectionRest.reorderSectionAfter(sectId);
            if (reorder.getStatus() != Response.Status.OK.getStatusCode())
                return reorder;

            // Attach the old END node to the new section
            Node sectionEnd = DatabaseService.getEndNode(sectId, db);
            sectionEnd.getSingleRelationship(ERelations.HAS_END, Direction.INCOMING).delete();
            newSection.createRelationshipTo(sectionEnd, ERelations.HAS_END);

            // Close off the prior rank with a new END node, and the requested rank with a new
            // START node
            Node newEnd = db.createNode(Nodes.READING);
            newEnd.setProperty("is_end", true);
            newEnd.setProperty("rank", sectionEnd.getProperty("rank"));
            thisSection.createRelationshipTo(newEnd, ERelations.HAS_END);
            Node newStart = db.createNode(Nodes.READING);
            newStart.setProperty("is_start", true);
            newStart.setProperty("rank", 0L);
            newSection.createRelationshipTo(newStart, ERelations.COLLATION);
            for (Node reading : priorRank)
                for (Relationship rel : reading.getRelationships(Direction.OUTGOING))
                    if (rel.isType(ERelations.SEQUENCE) || rel.isType(ERelations.LEMMA_TEXT)) {
                        Relationship outRel = reading.createRelationshipTo(newEnd, rel.getType());
                        Relationship inRel = newStart.createRelationshipTo(rel.getEndNode(), rel.getType());
                        rel.getPropertyKeys().forEach(x -> outRel.setProperty(x, rel.getProperty(x)));
                        rel.getPropertyKeys().forEach(x -> inRel.setProperty(x, rel.getProperty(x)));
                        rel.delete();
                    }
            for (Node reading : thisRank) {
                for (Relationship rel : reading.getRelationships(Direction.INCOMING)) {
                    if (rel.getStartNode().equals(newStart))
                        continue;
                    if (rel.isType(ERelations.SEQUENCE) || rel.isType(ERelations.LEMMA_TEXT)) {
                        Relationship inRel = rel.getStartNode().createRelationshipTo(newEnd, rel.getType());
                        Relationship outRel = newStart.createRelationshipTo(reading, rel.getType());
                        rel.getPropertyKeys().forEach(x -> inRel.setProperty(x, rel.getProperty(x)));
                        rel.getPropertyKeys().forEach(x -> outRel.setProperty(x, rel.getProperty(x)));
                        rel.delete();
                    }
                }
            }

            // TODO Remove any superfluous witnesses in each split section
            Node sectionStart = DatabaseService.getStartNode(sectId, db);
            for (Relationship r : sectionStart.getRelationships(ERelations.SEQUENCE, Direction.OUTGOING))
                if (r.getEndNode().hasProperty("is_end"))
                    r.delete();
            for (Relationship r : newStart.getRelationships(ERelations.SEQUENCE, Direction.OUTGOING))
                if (r.getEndNode().hasProperty("is_end"))
                    r.delete();


            // Re-initialize the ranks on the new section
            Tradition t = new Tradition(tradId);
            if (!t.recalculateRank(newStart.getId())) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("Rank recalculation of new section failed!").build();
            }

            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok().entity(String.format("{sectionId: %d}", newSectionId)).build();
    }

    @POST
    @Path("/merge/{otherId}")
    public Response mergeSections (@PathParam("otherId") String otherId) {
        try (Transaction tx = db.beginTx()) {
            // Get this node, and see which direction we're merging
            Node thisSection = db.getNodeById(Long.valueOf(sectId));
            Node firstSection = null;
            Node secondSection = null;
            for (Relationship r : thisSection.getRelationships(ERelations.NEXT)) {
                if (otherId.equals(String.valueOf(r.getEndNode().getId())))
                    secondSection = r.getEndNode();
                else if (otherId.equals(String.valueOf(r.getStartNode().getId())))
                    firstSection = r.getStartNode();
            }
            if (firstSection == null) {
                if (secondSection == null)
                    return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Requested sections are not neighbours").build();
                else
                    firstSection = thisSection;
            } else
                secondSection = thisSection;

            // Move relationships from the old start & end nodes
            Node oldEnd = DatabaseService.getEndNode(String.valueOf(firstSection.getId()), db);
            Node oldStart = DatabaseService.getStartNode(String.valueOf(secondSection.getId()), db);
            Node trueStart = DatabaseService.getStartNode(String.valueOf(firstSection.getId()), db);
            Node trueEnd = DatabaseService.getEndNode(String.valueOf(secondSection.getId()), db);

            // First we turn oldEnd and oldStart into placeholder readings, linked to each other
            oldStart.getSingleRelationship(ERelations.COLLATION, Direction.INCOMING).delete();
            oldEnd.getSingleRelationship(ERelations.HAS_END, Direction.INCOMING).delete();
            oldEnd.setProperty("is_placeholder", true);
            oldStart.setProperty("is_placeholder", true);
            HashSet<String> oldWitnesses = new HashSet<>();
            HashSet<String> newWitnesses = new HashSet<>();
            for (Relationship r : oldEnd.getRelationships(ERelations.SEQUENCE))
                for (String key : r.getPropertyKeys())
                    oldWitnesses.addAll(Arrays.asList((String[]) r.getProperty(key)));
            for (Relationship r : oldStart.getRelationships(ERelations.SEQUENCE))
                for (String key : r.getPropertyKeys())
                    newWitnesses.addAll(Arrays.asList((String[]) r.getProperty(key)));
            newWitnesses.stream().filter(x -> !oldWitnesses.contains(x))
                    .forEach(x -> addWitnessLink(trueStart, oldEnd, x, "witnesses"));
            oldWitnesses.stream().filter(x -> !newWitnesses.contains(x))
                    .forEach(x -> addWitnessLink(oldStart, trueEnd, x, "witnesses"));
            oldWitnesses.addAll(newWitnesses);
            Relationship link = oldEnd.createRelationshipTo(oldStart, ERelations.SEQUENCE);
            link.setProperty("witnesses", oldWitnesses.toArray(new String[oldWitnesses.size()]));

            // Reconfigure the lemma text link, if there is one
            Relationship plr = oldEnd.getSingleRelationship(ERelations.LEMMA_TEXT, Direction.INCOMING);
            Relationship nlr = oldStart.getSingleRelationship(ERelations.LEMMA_TEXT, Direction.OUTGOING);
            if (plr != null && nlr != null)
                plr.getStartNode().createRelationshipTo(nlr.getEndNode(), ERelations.LEMMA_TEXT);
            if (plr != null) plr.delete();
            if (nlr != null) nlr.delete();


            // Remove each placeholder in turn
            removePlaceholder(oldEnd);
            removePlaceholder(oldStart);

            // Move the second end node to the first section
            trueEnd.getSingleRelationship(ERelations.HAS_END, Direction.INCOMING).delete();
            firstSection.createRelationshipTo(trueEnd, ERelations.HAS_END);

            // Adjust the section ordering and delete the second section
            removeFromSequence(secondSection);
            secondSection.getSingleRelationship(ERelations.PART, Direction.INCOMING).delete();
            secondSection.delete();

            // Re-initialize the ranks on the new section
            Tradition t = new Tradition(tradId);
            if (!t.recalculateRank(trueStart.getId())) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("Rank recalculation of new section failed!").build();
            }

            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok().build();
    }

    // Export the dot / SVG for a particular section
    /**
     * Returns DOT file from specified tradition owned by user
     *
     * @param includeRelatedRelationships - Whether or not to include RELATED edges in the dot
     * @return Plaintext dot format
     */
    @GET
    @Path("/dot")
    @Produces(MediaType.TEXT_PLAIN + "; charset=utf-8")
    public Response getDot(@DefaultValue("false") @QueryParam("include_relations") Boolean includeRelatedRelationships) {
        if (DatabaseService.getTraditionNode(tradId, db) == null)
            return Response.status(Response.Status.NOT_FOUND).entity("No such tradition found").build();

        DotExporter parser = new DotExporter(db);
        return parser.parseNeo4J(tradId, sectId, includeRelatedRelationships);
    }

    // Export a list of variants for a section

    // For use in a transaction!
    private void removeFromSequence (Node thisSection) {
        Node priorSection = null;
        Node nextSection = null;
        if (thisSection.hasRelationship(Direction.INCOMING, ERelations.NEXT)) {
            Relationship incomingRel = thisSection.getSingleRelationship(ERelations.NEXT, Direction.INCOMING);
            priorSection = incomingRel.getStartNode();
            incomingRel.delete();
        }
        if (thisSection.hasRelationship(Direction.OUTGOING, ERelations.NEXT)) {
            Relationship outgoingRel = thisSection.getSingleRelationship(ERelations.NEXT, Direction.OUTGOING);
            nextSection = outgoingRel.getEndNode();
            outgoingRel.delete();
        }
        if (priorSection != null && nextSection != null) {
            priorSection.createRelationshipTo(nextSection, ERelations.NEXT);
        }
    }
}
