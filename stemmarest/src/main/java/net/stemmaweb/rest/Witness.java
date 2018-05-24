package net.stemmaweb.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.qmino.miredot.annotations.ReturnType;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.WitnessTextModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;

import net.stemmaweb.services.WitnessPath;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.Evaluators;
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

    public Witness (String traditionId, String requestedSigil) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        tradId = traditionId;
        sigil = requestedSigil;
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

        StringBuilder witnessAsText = new StringBuilder();

        long startRank = Long.parseLong(start);
        long endRank;

        Node traditionNode = DatabaseService.getTraditionNode(this.tradId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity(jsonerror("tradition not found")).build();

        ArrayList<Node> iterationList = sectionsRequested(traditionNode);
        if (iterationList.size() == 0)
            return Response.status(Status.NOT_FOUND).entity(jsonerror("Section not found in this tradition")).build();

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
                final Long sr = startRank;
                final Long er = endRank;
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
        // Remove the meta node from the list
        Boolean joinNext = false;
        try (Transaction tx = db.beginTx()) {
            for (Node node : witnessReadings) {
                if (booleanValue(node, "is_end")) continue;
                if (booleanValue(node, "is_lacuna")) continue;
                if (!joinNext && !booleanValue(node, "join_prior")
                        && !witnessAsText.toString().equals("") && !witnessAsText.toString().endsWith(" "))
                    witnessAsText.append(" ");
                joinNext = booleanValue(node, "join_next");
                witnessAsText.append(node.getProperty("text").toString());
            }
            tx.success();
        }
        WitnessTextModel wtm = new WitnessTextModel(witnessAsText.toString().trim());
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

        Node traditionNode = DatabaseService.getTraditionNode(this.tradId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity(jsonerror("tradition not found")).build();

        ArrayList<Node> iterationList = sectionsRequested(traditionNode);
        if (iterationList.size() == 0)
            return Response.status(Status.NOT_FOUND).entity(jsonerror("Section not found in this tradition")).build();

        for (Node currentSection: iterationList) {
            try (Transaction tx = db.beginTx()) {
                Node startNode = DatabaseService.getStartNode(String.valueOf(currentSection.getId()), db);
                readingModels.addAll(traverseReadings(startNode, witnessClass).stream().map(ReadingModel::new).collect(Collectors.toList()));
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
        // Remove the meta node from the list
        if (readingModels.get(readingModels.size() - 1).getText().equals("#END#"))
            readingModels.remove(readingModels.size() - 1);
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

    private ArrayList<Node> sectionsRequested(Node traditionNode) {
        ArrayList<Node> sectionNodes = DatabaseService.getRelated(traditionNode, ERelations.PART);
        ArrayList<Node> iterationList = new ArrayList<>();
        int depth = sectionNodes.size();
        try (Transaction tx = db.beginTx()) {
            if (this.sectId == null) {
                // order the sections by their occurrence in the tradition
                for (Node n : sectionNodes) {
                    if (!n.getRelationships(Direction.INCOMING, ERelations.NEXT)
                            .iterator()
                            .hasNext()) {
                        db.traversalDescription()
                                .depthFirst()
                                .relationships(ERelations.NEXT, Direction.OUTGOING)
                                .evaluator(Evaluators.toDepth(depth))
                                .uniqueness(Uniqueness.NODE_GLOBAL)
                                .traverse(n)
                                .nodes()
                                .forEach(iterationList::add);
                        break;
                    }
                }
            } else {
                Node sectionNode = db.getNodeById(Long.valueOf(sectId));
                if (sectionNode != null) {
                    Relationship rel = sectionNode.getSingleRelationship(ERelations.PART, Direction.INCOMING);
                    if (rel != null && rel.getStartNode().getId() == traditionNode.getId())
                        iterationList.add(sectionNode);
                }
            }
            tx.success();
        }
        return iterationList;
    }

    // NOTE needs to be in transaction
    private Boolean booleanValue(Node n, String p) {
        return n.hasProperty(p) && Boolean.parseBoolean(n.getProperty(p).toString());
    }
}
