package net.stemmaweb.rest;

import java.util.ArrayList;

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

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Evaluation;
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

        String witnessAsText = "";
        final String WITNESS_ID = sigil;
        Node startNode = DatabaseService.getStartNode(tradId, db);
        Evaluator e = Witness.getEvalForWitness(WITNESS_ID);

        try (Transaction tx = db.beginTx()) {
            for (Node node : db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .evaluator(e).uniqueness(Uniqueness.RELATIONSHIP_PATH)
                    .traverse(startNode).nodes()) {
                if (!node.getProperty("text").equals("#END#")) {
                    witnessAsText += node.getProperty("text") + " ";
                }
            }
            tx.success();
        } catch (Exception exception) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        if (witnessAsText.equals("")) {
            return Response.status(Status.NOT_FOUND)
                    .entity("no witness with this id was found")
                    .build();
        }
        return Response.status(Response.Status.OK)
                .entity("{\"text\":\"" + witnessAsText.trim() + "\"}")
                .build();
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
        final String WITNESS_ID = sigil;
        long startRank = Long.parseLong(start);
        long endRank = Long.parseLong(end);

        if (endRank == startRank) {
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity("end-rank is equal to start-rank")
                    .build();
        }

        if (endRank < startRank) {
            //TODO: want work!  swapRanks(startRank, endRank);
            long tempRank = startRank;
            startRank = endRank;
            endRank = tempRank;
        }

        Evaluator e = Witness.getEvalForWitness(WITNESS_ID);
        Node startNode = DatabaseService.getStartNode(tradId, db);

        try (Transaction tx = db.beginTx()) {
            for (Node node : db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .evaluator(e)
                    .uniqueness(Uniqueness.RELATIONSHIP_PATH)
                    .traverse(startNode)
                    .nodes()) {
                long nodeRank = Long.parseLong( node.getProperty("rank").toString());
                if (nodeRank >= startRank && nodeRank <= endRank &&
                        !node.getProperty("text").equals("#END#")) {
                    witnessAsText += node.getProperty("text") + " ";
                }
            }
            tx.success();
        } catch (Exception exception) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }

        if (witnessAsText.equals("")) {
            return Response.status(Status.NOT_FOUND)
                    .entity("no witness with this id was found")
                    .build();
        }
        return Response.status(Response.Status.OK)
                .entity("{\"text\":\"" + witnessAsText.trim() + "\"}")
                .build();

    }
/*
    private void swapRanks(long startRank, long endRank) {
        long tempRank = endRank;
        endRank = startRank;
        startRank = tempRank;
    }
*/

    /**
     * finds a witness in the database and returns it as a list of readings
     *
     * @return a witness as a list of models of readings in json format
     */
    @GET
    @Path("/readings")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getWitnessAsReadings() {
        final String WITNESS_ID = sigil;

        ArrayList<ReadingModel> readingModels = new ArrayList<>();

        Node startNode = DatabaseService.getStartNode(tradId, db);
        if (startNode == null) {
            return Response.status(Status.NOT_FOUND)
                    .entity("Could not find tradition with this id")
                    .build();
        }

        Evaluator e = Witness.getEvalForWitness(WITNESS_ID);

        try (Transaction tx = db.beginTx()) {

            for (Node startNodes : db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .evaluator(e)
                    .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL)
                    .traverse(startNode)
                    .nodes()) {
                readingModels.add(new ReadingModel(startNodes));
            }
            tx.success();
        } catch (Exception exception) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }

        if (readingModels.size() == 0) {
            return Response.status(Status.NOT_FOUND)
                    .entity("no witness with this id was found")
                    .build();
        }
        if (readingModels.get(readingModels.size() - 1).getText().equals("#END#")) {
            readingModels.remove(readingModels.size() - 1);
        }
        return Response.status(Status.OK).entity(readingModels).build();
    }

    public static Evaluator getEvalForWitness(final String WITNESS_ID) {
        return path -> {

            if (path.length() == 0) {
                return Evaluation.EXCLUDE_AND_CONTINUE;
            }

            boolean includes = false;
            boolean continues = false;

            if (path.lastRelationship().hasProperty("witnesses")) {
                String[] arr = (String[]) path.lastRelationship()
                        .getProperty("witnesses");
                for (String str : arr) {
                    if (str.equals(WITNESS_ID)) {
                        includes = true;
                        continues = true;
                    }
                }
            }
            return Evaluation.of(includes, continues);
        };
    }
}
