package net.stemmaweb.rest;

import static net.stemmaweb.Util.jsonerror;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.neo4j.graphdb.*;

import com.qmino.miredot.annotations.ReturnType;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.TextSequenceModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
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
    private String sigil;
    private String sectId;

    public Witness (String traditionId, String requestedSigil) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        tradId = traditionId;
        // The "sigil" might be a sigil, or it might be a node ID.
        // TODO Check when we ever call a witness by node ID??
        try {
            String found = getWitnessById(requestedSigil);
            if (found != null)
                sigil = found;
        } catch (NumberFormatException e) {
            sigil = requestedSigil;
        }
        if (sigil == null) sigil = requestedSigil;
        sectId = null;
    }

    public Witness (String traditionId, String sectionId, String requestedSigil) {
        this(traditionId, requestedSigil);
        sectId = sectionId;
    }

    private String getWitnessById(String nodeId) {
        String foundSigil = null;
        try (Transaction tx = db.beginTx()) {
        	Node tradNode = VariantGraphService.getTraditionNode(tx, tradId);
            Node found = null;
            for (Relationship r : DatabaseService.getRelationships(tradNode, Direction.OUTGOING, ERelations.HAS_WITNESS)) {
                if (r.getEndNode().getElementId().equals(nodeId))
                    found = r.getEndNode();
            }
            if (found != null)
                foundSigil = found.getProperty("sigil").toString();
        }
        return foundSigil;
    }

    private Node getWitnessBySigil(Transaction tx) {
        Node tradNode = VariantGraphService.getTraditionNode(tx, tradId);
        for (Relationship r : DatabaseService.getRelationships(tradNode, Direction.OUTGOING, ERelations.HAS_WITNESS)) {
            Node wit = r.getEndNode();
            if (wit.hasProperty("sigil") && wit.getProperty("sigil").equals(sigil)) {
                return wit;
            }
        }
        return null;
    }

    // Backwards compatibility for API
    public Response getWitnessAsText() {
        return getWitnessAsTextWithLayer(new ArrayList<>(), "0", "E");
    }

    /**
     * Returns a WitnessModel corresponding to the requested witness.
     * @title Get witness information
     * @return  A WitnessModel containing information about the witness
     * @statuscode 200 - on success
     * @statuscode 404 - if the tradition, section, or witness text doesn't exist
     * @statuscode 500 - on error, with an error message
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = WitnessModel.class)
    public Response getWitnessInfo() {
        try (Transaction tx = db.beginTx()) {
            Node witnessNode = getWitnessBySigil(tx);
            if (witnessNode == null) return Response.status(Status.NOT_FOUND).build();
            WitnessModel thisWit = new WitnessModel(witnessNode);
            return Response.ok(thisWit).build();

        }
    }

    /**
     * Deletes the requested witness.
     *
     * @title Delete a witness
     * @statuscode 200 - on success
     * @statuscode 404 - if the tradition, section, or witness text doesn't exist
     * @statuscode 500 - on error, with an error message
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = WitnessModel.class)
    public Response deleteWitness() {
        if (sectId != null)
            return Response.status(Status.BAD_REQUEST).entity("Cannot delete a witness from a single section").build();
        WitnessModel removed;
        try (Transaction tx = db.beginTx()) {
            // Find the node in question
            Node witnessNode = getWitnessBySigil(tx);
            if (witnessNode == null) return Response.status(Status.NOT_FOUND).build();
            // Find all references to the witness throughout the tradition, and delete them
            removed = new WitnessModel(witnessNode);
            HashSet<Node> orphanReadings = new HashSet<>();
            for (Relationship r : VariantGraphService.returnEntireTradition(tx, tradId).relationships()) {
                if (r.isType(ERelations.SEQUENCE)) {
                    Node start = r.getStartNode();
                    Node end = r.getEndNode();
                    for (String layer : r.getPropertyKeys()) {
                        ReadingService.removeWitnessLink(start, end, sigil, layer, "none");
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
            // Look through any stemmata and turn the witness hypothetical in each of them
            for (Relationship r : DatabaseService.getRelationships(witnessNode, ERelations.HAS_WITNESS)) {
                Node owner = r.getStartNode();
                if (owner.hasLabel(Nodes.STEMMA)) {
                    Node newHypothetical = tx.createNode(Nodes.WITNESS);
                    DatabaseService.copyProperties(witnessNode, newHypothetical);
                    newHypothetical.setProperty("hypothetical", true);
                    for (Relationship link : DatabaseService.getRelationships(witnessNode, ERelations.TRANSMITTED)) {
                        Relationship copy;
                        if (link.getStartNode().equals(witnessNode))
                            copy = newHypothetical.createRelationshipTo(link.getEndNode(), ERelations.TRANSMITTED);
                        else
                            copy = link.getStartNode().createRelationshipTo(newHypothetical, ERelations.TRANSMITTED);
                        DatabaseService.copyProperties(link, copy);
                        link.delete();
                    }
                    owner.createRelationshipTo(newHypothetical, ERelations.HAS_WITNESS);
                } // otherwise it is the link to the TRADITION node.
                r.delete();
            }
            // Delete the node
            witnessNode.delete();
            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }
        return Response.ok(removed).build();
    }



    /**
     * finds a witness in the database and returns it as a string; if start and end are
     * specified, a substring of the full witness text between those ranks inclusive is
     * returned. if end-rank is too high or start-rank too low will return up to the end
     * / from the start of the witness. If one or more witness layers are specified, return
     * the text composed of those layers.
     *
     * @title Get witness text
     * @param layer - the text layer(s) to return, e.g. "a.c." or "s.l.". These layers must not conflict with each other!
     * @param start - the starting rank
     * @param end   - the end rank
     * @return The witness text as a string.
     * @statuscode 200 - on success
     * @statuscode 400 - if a start or end rank is specified on the tradition-wide call, or if the start rank and end rank match
     * @statuscode 404 - if the tradition, section, or witness text doesn't exist
     * @statuscode 409 - if a section's end node cannot be reached while assembling the witness text
     * @statuscode 500 - on error, with an error message
     */
    @GET
    @Path("/text")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = TextSequenceModel.class)
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
            String witnessText = VariantGraphService.getWitnessText(tx, tradId, sectId, sigil, layer, startRank, endRank);
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
     * @title Get readings
     * @param witnessClass - the text layer to return, e.g. "a.c."
     * @return The witness text as a list of readings.
     * @statuscode 200 - on success
     * @statuscode 404 - if the tradition, section, or witness text doesn't exist
     * @statuscode 409 - if a section's end node cannot be reached while assembling the witness text
     * @statuscode 500 - on error, with an error message
     */
    @GET
    @Path("/readings")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.ReadingModel>")
    public Response getWitnessAsReadings(@QueryParam("layer") @DefaultValue("") List<String> witnessClass) {
        ArrayList<ReadingModel> readingModels = new ArrayList<>();
        if (witnessClass.size() == 1 && witnessClass.getFirst().isEmpty())
            witnessClass.removeFirst();

        try (Transaction tx = db.beginTx()) {
            ArrayList<Node> iterationList = VariantGraphService.sectionsRequested(tx, tradId, sectId);

            for (Node currentSection : iterationList) {
                Node startNode = VariantGraphService.getStartNode(tx, currentSection.getElementId());
                readingModels.addAll(VariantGraphService.traverseReadingsOfWitness(tx, startNode, sigil, witnessClass)
                        .stream().map(ReadingModel::new).toList());
                // Remove the meta node from the list
                if (!readingModels.isEmpty() && readingModels.getLast().getIs_end())
                    readingModels.removeLast();
            }
        } catch (org.neo4j.graphdb.NotFoundException e) {
            return Response.status(Status.NOT_FOUND).entity(jsonerror(e.getMessage())).build();
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
