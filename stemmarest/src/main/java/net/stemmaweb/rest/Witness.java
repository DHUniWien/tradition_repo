package net.stemmaweb.rest;

import java.util.ArrayList;
import java.util.stream.Collectors;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;

import net.stemmaweb.services.WitnessPath;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.Uniqueness;

/**
 * Comprises all the API calls related to a witness.
 * Can be called using http://BASE_URL/witness
 * @author PSE FS 2015 Team2
 */

public class Witness {

    private GraphDatabaseService db;
    private String tradId;
    private String sigil;

    public Witness (String traditionId, String requestedSigil) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        tradId = traditionId;
        sigil = requestedSigil;
    }

    /**
     * finds a witness in the database and returns it as a string
     *
     * @return a witness as a string
     */
    @GET
    @Path("/text")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getWitnessAsText() {
        Node tradNode = DatabaseService.getTraditionNode(tradId, db);
        Node endNode = DatabaseService.getRelated(tradNode, ERelations.HAS_END).get(0);
        String endRank;
        try (Transaction tx = db.beginTx()) {
            endRank = endNode.getProperty("rank").toString();
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(e.getMessage()).build();
        }
        return getWitnessAsTextBetweenRanks("0", endRank);
    }

    /**
     * find a requested witness in the data base and return it as a string
     * according to define start and end readings (including the readings in
     * those ranks). if end-rank is too high or start-rank too low will return
     * till the end/from the start of the witness
     *
     * @param start - the starting rank
     * @param end   - the end rank
     * @return a witness as a string
     */
    @GET
    @Path("/text/{start}/{end}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getWitnessAsTextBetweenRanks(
            @PathParam("start") String start,
            @PathParam("end") String end) {

        String witnessAsText = "";
        long startRank = Long.parseLong(start);
        long endRank = Long.parseLong(end);

        if (endRank == startRank) {
            return Response.status(Status.BAD_REQUEST)
                    .entity("end-rank is equal to start-rank")
                    .build();
        }

        if (endRank < startRank) {
            long tempRank = startRank;
            startRank = endRank;
            endRank = tempRank;
        }

        Node startNode = DatabaseService.getStartNode(tradId, db);
        try (Transaction tx = db.beginTx()) {
            Boolean joinPrior = false;
            for (Node node : traverseReadings(startNode)) {
                long nodeRank = Long.parseLong( node.getProperty("rank").toString());
                if (nodeRank >= startRank && nodeRank <= endRank
                        && !booleanValue(node, "is_lacuna")) {
                    if (!joinPrior && !booleanValue(node, "join_next") && !witnessAsText.equals(""))
                        witnessAsText += " ";
                    witnessAsText += node.getProperty("text").toString();
                    joinPrior = booleanValue(node, "join_prior");
                }
            }
            tx.success();
        } catch (Exception exception) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }

        if (witnessAsText.equals("")) {
            return Response.status(Status.NOT_FOUND)
                    .entity("Could not find single witness with this sigil")
                    .build();
        }
        return Response.status(Response.Status.OK)
                .entity("{\"text\":\"" + witnessAsText.trim() + "\"}")
                .build();

    }

    /**
     * finds a witness in the database and returns it as a list of readings
     *
     * @return a witness as a list of models of readings in json format
     */
    @GET
    @Path("/readings")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getWitnessAsReadings() {
        ArrayList<ReadingModel> readingModels = new ArrayList<>();

        Node startNode = DatabaseService.getStartNode(tradId, db);
        if (startNode == null) {
            return Response.status(Status.NOT_FOUND)
                    .entity("Could not find tradition with this id")
                    .build();
        }

        try (Transaction tx = db.beginTx()) {
            readingModels.addAll(traverseReadings(startNode).stream().map(ReadingModel::new).collect(Collectors.toList()));
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(e.getMessage()).build();
        }

        if (readingModels.size() == 0) {
            return Response.status(Status.NOT_FOUND)
                    .entity("Could not find single witness with this sigil")
                    .build();
        }
        if (readingModels.get(readingModels.size() - 1).getText().equals("#END#")) {
            readingModels.remove(readingModels.size() - 1);
        }
        return Response.status(Status.OK).entity(readingModels).build();
    }

    private ArrayList<Node> traverseReadings(Node startNode) throws Exception {
        Evaluator e = new WitnessPath(sigil).getEvalForWitness();
        ArrayList<Node> result = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .evaluator(e)
                    .uniqueness(Uniqueness.RELATIONSHIP_PATH)
                    .traverse(startNode)
                    .nodes()
            .forEach(x -> {
                if (!booleanValue(x, "is_end"))
                    result.add(x);
            });
            tx.success();
        }
        return result;
    }

    // NOTE needs to be in transaction
    private Boolean booleanValue(Node n, String p) {
        return n.hasProperty(p) && Boolean.parseBoolean(n.getProperty(p).toString());
    }
}
