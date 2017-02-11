package net.stemmaweb.rest;

import java.util.ArrayList;
import java.util.stream.Collectors;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;

import net.stemmaweb.services.WitnessPath;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.Evaluators;
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

    /**
     * finds a witness in the database and returns it as a string; if start and end are
     * specified, a substring of the full witness text between those ranks inclusive is
     * returned. if end-rank is too high or start-rank too low will return up to the end
     * / from the start of the witness
     *
     * @param start - the starting rank
     * @param end   - the end rank
     * @return a witness as a string
     *
     */
    @GET
    @Path("/text")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response getWitnessAsText(@QueryParam("start") @DefaultValue("0") String start,
                                     @QueryParam("end") @DefaultValue("E") String end) {
        return getWitnessAsTextWithLayer(null, start, end);
    }

    // Backwards compatibility for API
    public Response getWitnessAsText() {
        return getWitnessAsTextWithLayer(null, "0", "E");
    }

    /**
     * Returns the text of a particular witness layer, as above.
     *
     * @param layer - the text layer to return, e.g. "a.c."
     * @param start - the starting rank
     * @param end   - the end rank
     * @return a witness as a string
     */
    @GET
    @Path("/text/{layer}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response getWitnessAsTextWithLayer(
            @PathParam("layer") String layer,
            @QueryParam("start") @DefaultValue("0") String start,
            @QueryParam("end") @DefaultValue("E") String end) {

        String witnessAsText = "";

        long startRank = Long.parseLong(start);
        long endRank;

        Node traditionNode = DatabaseService.getTraditionNode(this.tradId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity("tradition not found").build();

        ArrayList<Node> iterationList = new ArrayList<>();
        // Lets see if a tradition has one or more sections
        ArrayList<Node> sectionNodes = DatabaseService.getRelated(traditionNode, ERelations.PART);
        if (sectionNodes.size() == 0) {
            iterationList.add(traditionNode);
        } else {
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
                    if (sectionNode == null)
                        return Response.status(Status.NOT_FOUND).entity("section not found").build();
                    Relationship rel = sectionNode.getSingleRelationship(ERelations.PART, Direction.INCOMING);
                    if (rel == null || rel.getStartNode().getId() != traditionNode.getId()) {
                        return Response.status(Status.NOT_FOUND).entity("this section is not part of this tradition").build();
                    }
                    iterationList.add(sectionNode);
                }
                tx.success();
            } catch (Exception e) {
                return Response.status(Status.INTERNAL_SERVER_ERROR).build();
            }
        }

        for (Node currentSection: iterationList) {
            if (iterationList.size() > 1) {
                end = "E";
                startRank = 0;
            }
            if (end.equals("E")) {
                // Find the rank of the graph's end.
                Node endNode = DatabaseService.getRelated(currentSection, ERelations.HAS_END).get(0);
                try (Transaction tx = db.beginTx()) {
                    endRank = Long.valueOf(endNode.getProperty("rank").toString());
                    tx.success();
                } catch (Exception e) {
                    e.printStackTrace();
                    return Response.serverError().entity(e.getMessage()).build();
                }
            } else
                endRank = Long.parseLong(end);

            if (endRank == startRank) {
                return Response.status(Status.BAD_REQUEST)
                        .entity("end-rank is equal to start-rank")
                        .build();
            }

            if (endRank < startRank) {
                // Swap them around.
                long tempRank = startRank;
                startRank = endRank;
                endRank = tempRank;
            }

            try (Transaction tx = db.beginTx()) {
                Relationship rel = currentSection.getSingleRelationship(ERelations.COLLATION, Direction.OUTGOING);
                if(rel != null) {
                    Node startNode = rel.getEndNode();
                    Boolean joinPrior;
                    for (Node node : traverseReadings(startNode, layer)) {
                        long nodeRank = Long.parseLong(node.getProperty("rank").toString());
                        joinPrior = booleanValue(node, "join_prior");
                        if (nodeRank >= startRank && nodeRank <= endRank
                                && !booleanValue(node, "is_lacuna")) {
                            if (!joinPrior && !booleanValue(node, "join_next") && !witnessAsText.equals(""))
                                witnessAsText += " ";
                            witnessAsText += node.getProperty("text").toString();
                        }
                    }
                }
                tx.success();
            } catch (Exception exception) {
                return Response.status(Status.INTERNAL_SERVER_ERROR).build();
            }
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
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response getWitnessAsReadings() {
        return getWitnessAsReadings(null);
    }

    @GET
    @Path("/readings/{layer}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response getWitnessAsReadings(@PathParam("layer") String witnessClass) {
        ArrayList<ReadingModel> readingModels = new ArrayList<>();

        Node startNode = DatabaseService.getStartNode(tradId, db);
        if (startNode == null) {
            return Response.status(Status.NOT_FOUND)
                    .entity("Could not find tradition with this id")
                    .build();
        }

        try (Transaction tx = db.beginTx()) {
            readingModels.addAll(traverseReadings(startNode, witnessClass).stream().map(ReadingModel::new).collect(Collectors.toList()));
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

    private ArrayList<Node> traverseReadings(Node startNode, String witnessClass) throws Exception {
        Evaluator e;
        if (witnessClass == null)
            e = new WitnessPath(sigil).getEvalForWitness();
        else
            e = new WitnessPath(sigil, witnessClass).getEvalForWitness();

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
