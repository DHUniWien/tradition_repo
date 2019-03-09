package net.stemmaweb.rest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.qmino.miredot.annotations.ReturnType;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.model.WitnessTextModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;

import net.stemmaweb.services.ReadingService;
import net.stemmaweb.services.WitnessPath;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.Uniqueness;

import static net.stemmaweb.rest.Util.jsonerror;

/**
 * Comprises all the API calls related to a witness.
 * Can be called using http://BASE_URL/witness
 * @author PSE FS 2015 Team2
 */

public class Witness {

    private GraphDatabaseService db;
    private String tradId;
    private String sigil;
    private String sectId;
    private String errorMessage;

    public Witness (String traditionId, String requestedSigil) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        tradId = traditionId;
        // The "sigil" might be a sigil, or it might be a node ID.
        Long nodeId = null;
        try {
            nodeId = Long.valueOf(requestedSigil);
        } catch (NumberFormatException e) {
            sigil = requestedSigil;
        }
        if (nodeId != null) {
            try (Transaction tx = db.beginTx()) {
                // Look this up via Cypher, to avoid triggering an exception
                Result lookup = db.execute(String.format("MATCH (n:WITNESS) WHERE id(n) = %d RETURN n", nodeId));
                Iterator<Node> nc = lookup.columnAs("n");
                nc.forEachRemaining(x -> sigil = x.getProperty("sigil").toString());
                tx.success();
            }
        }
        if (sigil == null) sigil = requestedSigil;
        sectId = null;
    }

    public Witness (String traditionId, String sectionId, String requestedSigil) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        tradId = traditionId;
        sigil = requestedSigil;
        sectId = sectionId;
    }

    // Backwards compatibility for API
    public Response getWitnessAsText() {
        return getWitnessAsTextWithLayer(new ArrayList<>(), "0", "E");
    }

    /**
     * Returns a WitnessModel corresponding to the requested witness.
     * @summary Get witness information
     * @return  A WitnessModel containing information about the witness
     * @statuscode 200 - on success
     * @statuscode 404 - if the tradition, section, or witness text doesn't exist
     * @statuscode 500 - on error, with an error message
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = WitnessModel.class)
    public Response getWitnessInfo() {
        WitnessModel thisWit;
        try (Transaction tx = db.beginTx()) {
            Node witnessNode = db.findNode(Nodes.WITNESS, "sigil", sigil);
            if (witnessNode == null)
                return Response.status(Status.NOT_FOUND).build();
            thisWit = new WitnessModel(witnessNode);
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }
        return Response.ok(thisWit).build();
    }

    /**
     * Deletes the requested witness.
     * @summary Delete a witness
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
        try (Transaction tx = db.beginTx()) {
            Node witnessNode = db.findNode(Nodes.WITNESS, "sigil", sigil);
            if (witnessNode == null) return Response.status(Status.NOT_FOUND).build();
            // Find all references to the witness throughout the tradition, and delete them
            HashSet<Node> orphanReadings = new HashSet<>();
            for (Relationship r : DatabaseService.returnEntireTradition(tradId, db).relationships()) {
                if (r.isType(ERelations.SEQUENCE)) {
                    Node start = r.getStartNode();
                    Node end = r.getEndNode();
                    for (String layer : r.getPropertyKeys()) {
                        ReadingService.removeWitnessLink(start, end, sigil, layer);
                    }
                    // Was this the last outgoing for the start, or the last incoming for the end?
                    if (!start.getRelationships(Direction.OUTGOING, ERelations.SEQUENCE, ERelations.LEMMA_TEXT).iterator().hasNext())
                        orphanReadings.add(start);
                    if (!end.getRelationships(Direction.INCOMING, ERelations.SEQUENCE, ERelations.LEMMA_TEXT).iterator().hasNext())
                        orphanReadings.add(end);
                }
            }
            // Delete any orphan readings
            for (Node orphan : orphanReadings) {
                if (orphan.hasRelationship()) {
                    // Check that no SEQUENCE or LEMMA_TEXT relationships are left
                    for (Relationship r : orphan.getRelationships()) {
                        if (r.isType(ERelations.SEQUENCE) || r.isType(ERelations.LEMMA_TEXT))
                            return Response.serverError()
                                    .entity(String.format("Reading %d (%s) still has sequence links",
                                            orphan.getId(), orphan.getProperty("text"))).build();
                        r.delete();
                    }
                    orphan.delete();
                }
            }
            // Look through any stemmata and turn the witness hypothetical in each of them
            for (Relationship r : witnessNode.getRelationships(ERelations.HAS_WITNESS)) {
                Node owner = r.getStartNode();
                if (owner.hasLabel(Nodes.STEMMA)) {
                    Node newHypothetical = db.createNode(Nodes.WITNESS);
                    DatabaseService.copyProperties(witnessNode, newHypothetical);
                    newHypothetical.setProperty("hypothetical", true);
                    for (Relationship link : witnessNode.getRelationships(ERelations.TRANSMITTED)) {
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
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }
        return Response.ok().build();
    }



    /**
     * finds a witness in the database and returns it as a string; if start and end are
     * specified, a substring of the full witness text between those ranks inclusive is
     * returned. if end-rank is too high or start-rank too low will return up to the end
     * / from the start of the witness. If one or more witness layers are specified, return
     * the text composed of those layers.
     *
     * @summary Get witness text
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
    @ReturnType(clazz = WitnessTextModel.class)
    public Response getWitnessAsTextWithLayer(
            @QueryParam("layer") @DefaultValue("") List<String> layer,
            @QueryParam("start") @DefaultValue("0") String start,
            @QueryParam("end") @DefaultValue("E") String end) {

        long startRank = Long.parseLong(start);
        long endRank;

        ArrayList<Node> iterationList = sectionsRequested();
        if (iterationList == null)
            return Response.status(errorMessage.contains("not found") ? Status.NOT_FOUND : Status.INTERNAL_SERVER_ERROR)
                    .entity(jsonerror(errorMessage)).build();

        // Empty out the layer list if it is the default.
        if (layer.size() == 1 && layer.get(0).equals(""))
            layer.remove(0);

        ArrayList<Node> witnessReadings = new ArrayList<>();
        for (Node currentSection: iterationList) {
            if (iterationList.size() > 1 && (!end.equals("E") || startRank != 0))
                return Response.status(Status.BAD_REQUEST)
                        .entity(jsonerror("Cannot request specific start/end across sections")).build();

            if (end.equals("E")) {
                // Find the rank of the graph's end.
                Node endNode = DatabaseService.getRelated(currentSection, ERelations.HAS_END).get(0);
                try (Transaction tx = db.beginTx()) {
                    endRank = Long.valueOf(endNode.getProperty("rank").toString());
                    tx.success();
                } catch (Exception e) {
                    e.printStackTrace();
                    return Response.serverError().entity(jsonerror(e.getMessage())).build();
                }
            } else
                endRank = Long.parseLong(end);

            if (endRank == startRank) {
                return Response.status(Status.BAD_REQUEST)
                        .entity(jsonerror("end-rank is equal to start-rank"))
                        .build();
            }

            if (endRank < startRank) {
                // Swap them around.
                long tempRank = startRank;
                startRank = endRank;
                endRank = tempRank;
            }

            Node startNode = DatabaseService.getStartNode(String.valueOf(currentSection.getId()), db);
            try (Transaction tx = db.beginTx()) {
                final long sr = startRank;
                final long er = endRank;
                witnessReadings.addAll(traverseReadings(startNode, layer).stream()
                        .filter(x -> Long.valueOf(x.getProperty("rank").toString()) >= sr
                                && Long.valueOf(x.getProperty("rank").toString()) <= er)
                        .collect(Collectors.toList()));
                tx.success();
            } catch (Exception e) {
                if (e.getMessage().equals("CONFLICT"))
                    return Response.status(Status.CONFLICT).entity(jsonerror("Traversal end node not reached")).build();
                e.printStackTrace();
                return Response.serverError().entity(jsonerror(e.getMessage())).build();
            }
        }
        // If the path is size 0 then we didn't even get to the end node; the witness path doesn't exist.
        if (witnessReadings.size() == 0)
            return Response.status(Status.NOT_FOUND)
                    .entity(jsonerror("No witness path found for this sigil")).build();
        // Construct the text from the node reading models
        String witnessText = ReadingService.textOfReadings(
                witnessReadings.stream().map(ReadingModel::new).collect(Collectors.toList()), false);
        WitnessTextModel wtm = new WitnessTextModel(witnessText);
        return Response.ok(wtm).build();

    }

    /**
     * Returns the sequence of readings for a given witness.
     *
     * @summary Get readings
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
        if (witnessClass.size() == 1 && witnessClass.get(0).equals(""))
            witnessClass.remove(0);

        ArrayList<Node> iterationList = sectionsRequested();
        if (iterationList == null)
            return Response.status(errorMessage.contains("not found") ? Status.NOT_FOUND : Status.INTERNAL_SERVER_ERROR)
                    .entity(jsonerror(errorMessage)).build();

        for (Node currentSection: iterationList) {
            try (Transaction tx = db.beginTx()) {
                Node startNode = DatabaseService.getStartNode(String.valueOf(currentSection.getId()), db);
                readingModels.addAll(traverseReadings(startNode, witnessClass).stream().map(ReadingModel::new).collect(Collectors.toList()));
                // Remove the meta node from the list
                if (readingModels.size() > 0 && readingModels.get(readingModels.size() - 1).getText().equals("#END#"))
                    readingModels.remove(readingModels.size() - 1);
                tx.success();
            } catch (Exception e) {
                if (e.getMessage().equals("CONFLICT"))
                    return Response.status(Status.CONFLICT).entity(jsonerror("Traversal end node not reached")).build();
                e.printStackTrace();
                return Response.serverError().entity(jsonerror(e.getMessage())).build();
            }
        }

        // If the path is size 0 then the witness path doesn't exist.
        if (readingModels.size() == 0)
            return Response.status(Status.NOT_FOUND)
                    .entity(jsonerror("No witness path found for this sigil")).build();
        // ...and return.
        return Response.status(Status.OK).entity(readingModels).build();
    }

    // For use within a transaction
    private ArrayList<Node> traverseReadings(Node startNode, List<String> witnessClass) throws Exception {
        Evaluator e;
        if (witnessClass == null)
            e = new WitnessPath(sigil).getEvalForWitness();
        else
            e = new WitnessPath(sigil, witnessClass).getEvalForWitness();

        ArrayList<Node> result = new ArrayList<>();
        db.traversalDescription().depthFirst()
                .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                .evaluator(e)
                .uniqueness(Uniqueness.RELATIONSHIP_PATH)
                .traverse(startNode)
                .nodes()
                .forEach(result::add);
        // If the path is nonzero but the end node wasn't reached, we had a conflict.
        if (result.size() > 0 && !result.get(result.size()-1).hasProperty("is_end"))
            throw new Exception("CONFLICT");
        return result;
    }

    private ArrayList<Node> sectionsRequested() {
        Node traditionNode = DatabaseService.getTraditionNode(tradId, db);
        if (traditionNode == null) {
            errorMessage = "Requested tradition does not exist";
            return null;
        }

        ArrayList<Node> iterationList = new ArrayList<>();
        if (this.sectId == null) {
            iterationList = DatabaseService.getSectionNodes(tradId, db);
        } else {
            if (!DatabaseService.sectionInTradition(tradId, sectId, db)) {
                errorMessage = "Requested section does not exist in this tradition";
                return null;
            }
            try (Transaction tx = db.beginTx()) {
                Node sectionNode = db.getNodeById(Long.valueOf(sectId));
                iterationList.add(sectionNode);
                tx.success();
            } catch (Exception e) {
                e.printStackTrace();
                errorMessage = e.getMessage();
                return null;
            }
        }
        return iterationList;
    }

}
