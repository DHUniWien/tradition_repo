package net.stemmaweb.rest;

import java.util.*;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.exporter.DotExporter;
import net.stemmaweb.exporter.GraphMLExporter;
import net.stemmaweb.exporter.TabularExporter;
import net.stemmaweb.model.*;
import net.stemmaweb.parser.DotParser;
import net.stemmaweb.services.*;
import net.stemmaweb.services.DatabaseService;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.Uniqueness;


/**
 * Comprises all the api calls related to a tradition.
 * Can be called using http://BASE_URL/tradition
 *
 * @author PSE FS 2015 Team2
 */

public class Tradition {
    private GraphDatabaseService db;
    private String traditionId;

    public Tradition(String requestedId) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        traditionId = requestedId;
    }

    /*********************
     * Delegated API calls
     */

    @Path("/witness/{sigil}")
    public Witness getWitness(@PathParam("sigil") String sigil) {
        return new Witness(traditionId, sigil);
    }

    @Path("/stemma/{name}")
    public Stemma getStemma(@PathParam("name") String name) {
        return new Stemma(traditionId, name);
    }

    @Path("/relation")
    public Relation getRelation() {
        return new Relation(traditionId);
    }

    /*************************
     * Resource creation calls
     */
    @PUT  // a new stemma
    @Path("/stemma")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response newStemma(String dot) {
        DotParser parser = new DotParser(db);
        return parser.importStemmaFromDot(dot, traditionId);
    }

    /*****************************
     * Collection retrieval calls
     */

    /**
     * Gets a list of all sections of a tradition with the given id.
     *
     * @return Http Response 200 and a list of section models in JSON on success
     * or an ERROR in JSON format
     */
    @GET
    @Path("/sections")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllSections() {
        Node traditionNode = DatabaseService.getTraditionNode(traditionId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity("tradition not found").build();

        ArrayList<SectionModel> sectionList = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            /* use this, in case you want an arbitrary output
            DatabaseService.getRelated(traditionNode, ERelations.PART)
                    .forEach(r -> sectionList.add(new SectionModel(r)));
            */
            ArrayList<Node> sectionNodes = DatabaseService.getRelated(traditionNode, ERelations.PART);
            int depth = sectionNodes.size();
            if (depth > 0) {
                for(Node n: sectionNodes) {
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
                                .forEach(r -> sectionList.add(new SectionModel(r)));
                        break;
                    }
                }
            }
            tx.success();
            if (sectionList.size() != depth) {
                return Response.status(Status.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(sectionList).build();
    }

    /**
     * Gets a list of all the witnesses of a tradition with the given id.
     *
     * @return Http Response 200 and a list of witness models in JSON on success
     * or an ERROR in JSON format
     */
    @GET
    @Path("/witnesses")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllWitnesses() {
        Node traditionNode = DatabaseService.getTraditionNode(traditionId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity("tradition not found").build();

        ArrayList<WitnessModel> witnessList = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            DatabaseService.getRelated(traditionNode, ERelations.HAS_WITNESS)
                    .forEach(r -> witnessList.add(new WitnessModel(r)));
            tx.success();
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(witnessList).build();
    }

    /**
     * Gets a list of all Stemmata available, as dot format
     *
     * @return Http Response ok and a collection of StemmaModels that include
     * the dot
     */
    @GET
    @Path("/stemmata")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllStemmata() {
        Node traditionNode = DatabaseService.getTraditionNode(traditionId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity("No such tradition found").build();

        // find all stemmata associated with this tradition
        ArrayList<StemmaModel> stemmata = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA)
                    .forEach(x -> stemmata.add(new StemmaModel(x)));
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return Response.ok(stemmata).build();
    }

    /**
     * Gets a list of all relationships of a tradition with the given id.
     *
     * @return Http Response 200 and a list of relationship model in JSON
     */
    @GET
    @Path("/relationships")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllRelationships() {
        ArrayList<RelationshipModel> relList = new ArrayList<>();

        Node startNode = DatabaseService.getStartNode(traditionId, db);
        try (Transaction tx = db.beginTx()) {
            db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .uniqueness(Uniqueness.NODE_GLOBAL)
                    .traverse(startNode).nodes().forEach(
                    n -> n.getRelationships(ERelations.RELATED, Direction.OUTGOING).forEach(
                            r -> relList.add(new RelationshipModel(r)))
            );

            tx.success();
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(relList).build();
    }

    /**
     * Returns a list of all readings in a tradition
     *
     * @return the list of readings in json format on success or an ERROR in
     * JSON format
     */
    @GET
    @Path("/readings")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllReadings() {
        Node startNode = DatabaseService.getStartNode(traditionId, db);
        if (startNode == null) {
            return Response.status(Status.NOT_FOUND)
                    .entity("There is no tradition with this id").build();
        }

        ArrayList<ReadingModel> readingModels = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .evaluator(Evaluators.all())
                    .uniqueness(Uniqueness.NODE_GLOBAL).traverse(startNode)
                    .nodes().forEach(node -> readingModels.add(new ReadingModel(node)));
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(readingModels).build();
    }

    /**
     * Get all readings which have the same text and the same rank between given
     * ranks
     *
     * @param startRank the rank from where to start the search
     * @param endRank   the end rank of the search range
     * @return a list of lists as a json ok response: each list contain
     * identical readings on success or an ERROR in JSON format
     */
    // TODO refactor all these traversals somewhere!
    @GET
    @Path("/identicalreadings/{startRank}/{endRank}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getIdenticalReadings(@PathParam("startRank") long startRank,
                                         @PathParam("endRank") long endRank) {
        Node startNode = DatabaseService.getStartNode(traditionId, db);
        if (startNode == null) {
            return Response.status(Status.NOT_FOUND)
                    .entity("Could not find tradition with this id").build();
        }

        ArrayList<List<ReadingModel>> identicalReadings;
        try {
            ArrayList<ReadingModel> readingModels =
                    getAllReadingsFromTraditionBetweenRanks(startNode, startRank, endRank);
            identicalReadings = identifyIdenticalReadings(readingModels, startRank, endRank);
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }

        Boolean isEmpty = true;
        for (List<ReadingModel> list : identicalReadings) {
            if (list.size() > 0) {
                isEmpty = false;
                break;
            }
        }
        if (isEmpty)
            return Response.status(Status.NOT_FOUND)
                    .entity("no identical readings were found")
                    .build();

        return Response.ok(identicalReadings).build();
    }

    // Retrieve all readings of a tradition between two ranks as Nodes
    private ArrayList<Node> getReadingsBetweenRanks(long startRank, long endRank, Node startNode) {
        ArrayList<Node> readings = new ArrayList<>();

        Evaluator e = path -> {
            Integer rank = Integer.parseInt(path.endNode().getProperty("rank").toString());
            if (rank > endRank)
                return Evaluation.EXCLUDE_AND_PRUNE;
            if (rank < startRank)
                return Evaluation.EXCLUDE_AND_CONTINUE;
            return Evaluation.INCLUDE_AND_CONTINUE;
        };
        try (Transaction tx = db.beginTx()) {
            db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .evaluator(e).uniqueness(Uniqueness.NODE_GLOBAL)
                    .traverse(startNode).nodes().forEach(readings::add);
            tx.success();
        }
        return readings;
    }

    // Retrieve all readings of a tradition between two ranks as ReadingModels
    private ArrayList<ReadingModel> getAllReadingsFromTraditionBetweenRanks(
            Node startNode, long startRank, long endRank) {
        ArrayList<ReadingModel> readingModels = new ArrayList<>();
        getReadingsBetweenRanks(startRank, endRank, startNode)
                .forEach(x -> readingModels.add(new ReadingModel(x)));
        Collections.sort(readingModels);
        return readingModels;
    }

    // Gets identical readings in a tradition between the given ranks
    private ArrayList<List<ReadingModel>> identifyIdenticalReadings(
            ArrayList<ReadingModel> readingModels, long startRank, long endRank) {
        ArrayList<List<ReadingModel>> identicalReadingsList = new ArrayList<>();

        for (int i = 0; i <= readingModels.size() - 2; i++)
            while (Objects.equals(readingModels.get(i).getRank(), readingModels.get(i + 1)
                    .getRank()) && i + 1 < readingModels.size()) {
                ArrayList<ReadingModel> identicalReadings = new ArrayList<>();

                if (readingModels.get(i).getText()
                        .equals(readingModels.get(i + 1).getText())
                        && readingModels.get(i).getRank() < endRank
                        && readingModels.get(i).getRank() > startRank) {
                    identicalReadings.add(readingModels.get(i));
                    identicalReadings.add(readingModels.get(i + 1));
                }
                identicalReadingsList.add(identicalReadings);
                i++;
            }
        return identicalReadingsList;
    }

    /**
     * Returns a list of a list of readingModels with could be one the same rank
     * without problems
     * TODO use AlignmentTraverse for this...?
     *
     * @param startRank - where to start
     * @param endRank   - where to end
     * @return list of readings that could be at the same rank in JSON format or
     * an ERROR in JSON format
     */
    @GET
    @Path("/mergeablereadings/{startRank}/{endRank}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCouldBeIdenticalReadings(
            @PathParam("startRank") long startRank,
            @PathParam("endRank") long endRank) {
        Node startNode = DatabaseService.getStartNode(traditionId, db);
        if (startNode == null) {
            return Response.status(Status.NOT_FOUND)
                    .entity("There is no tradition with this id").build();
        }

        ArrayList<ArrayList<ReadingModel>> couldBeIdenticalReadings;
        try (Transaction tx = db.beginTx()) {
            ArrayList<Node> questionedReadings = getReadingsBetweenRanks(
                    startRank, endRank, startNode);

            couldBeIdenticalReadings = getCouldBeIdenticalAsList(questionedReadings);
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        if (couldBeIdenticalReadings.size() == 0)
            return Response.status(Status.NOT_FOUND)
                    .entity("There are no mergeable readings")
                    .build();

        return Response.ok(couldBeIdenticalReadings).build();
    }

    /**
     * Makes separate lists for every group of readings with identical text and
     * different ranks and send the list for further test
     *
     * @param questionedReadings -
     * @return list of lists of identical readings
     */
    private ArrayList<ArrayList<ReadingModel>> getCouldBeIdenticalAsList(
            ArrayList<Node> questionedReadings) {

        ArrayList<ArrayList<ReadingModel>> couldBeIdenticalReadings = new ArrayList<>();

        for (Node nodeA : questionedReadings) {
            ArrayList<Node> sameText = new ArrayList<>();
            questionedReadings.stream().filter(nodeB -> !nodeA.equals(nodeB)
                    && nodeA.getProperty("text").toString().equals(nodeB.getProperty("text").toString())
                    && !nodeA.getProperty("rank").toString().equals(nodeB.getProperty("rank").toString())).forEach(nodeB -> {
                sameText.add(nodeB);
                sameText.add(nodeA);
            });
            if (sameText.size() > 0) {
                couldBeIdenticalCheck(sameText, couldBeIdenticalReadings);
            }
        }
        return couldBeIdenticalReadings;
    }

    /**
     * Adds all the words that could be on the same rank to the result list
     *
     * @param sameText                 -
     * @param couldBeIdenticalReadings -
     */
    private void couldBeIdenticalCheck(ArrayList<Node> sameText,
                                       ArrayList<ArrayList<ReadingModel>> couldBeIdenticalReadings) {

        Node biggerRankNode;
        Node smallerRankNode;
        long biggerRank;
        long smallerRank;
        long rank;
        boolean gotOne;

        ArrayList<ReadingModel> couldBeIdentical = new ArrayList<>();

        for (int i = 0; i < sameText.size() - 1; i++) {
            long rankA = (long) sameText.get(i).getProperty("rank");
            long rankB = (long) sameText.get(i + 1).getProperty("rank");

            if (rankA < rankB) {
                biggerRankNode = sameText.get(i + 1);
                smallerRankNode = sameText.get(i);
                smallerRank = rankA;
                biggerRank = rankB;
            } else {
                biggerRankNode = sameText.get(i);
                smallerRankNode = sameText.get(i + 1);
                smallerRank = rankB;
                biggerRank = rankA;
            }

            gotOne = false;
            Iterable<Relationship> rels = smallerRankNode
                    .getRelationships(Direction.OUTGOING, ERelations.SEQUENCE);

            for (Relationship rel : rels) {
                rank = (long) rel.getEndNode().getProperty("rank");
                if (rank <= biggerRank) {
                    gotOne = true;
                    break;
                }
            }

            if (gotOne) {
                gotOne = false;

                Iterable<Relationship> rels2 = biggerRankNode
                        .getRelationships(Direction.INCOMING, ERelations.SEQUENCE);

                for (Relationship rel : rels2) {
                    rank = (long) rel.getStartNode().getProperty("rank");
                    if (rank >= smallerRank) {
                        gotOne = true;
                        break;
                    }
                }
            }
            if (!gotOne) {
                if (!couldBeIdentical
                        .contains(new ReadingModel(smallerRankNode))) {
                    couldBeIdentical.add(new ReadingModel(smallerRankNode));
                }
                if (!couldBeIdentical
                        .contains(new ReadingModel(biggerRankNode))) {
                    couldBeIdentical.add(new ReadingModel(biggerRankNode));
                }
            }
            if (couldBeIdentical.size() > 0) {
                couldBeIdenticalReadings.add(couldBeIdentical);
            }
        }
    }

    /******************************
     * Tradition-specific calls
     */

    /**
     * Changes the metadata of the tradition.
     *
     * @param tradition in JSON Format
     * @return OK and information about the tradition in JSON on success or an
     * ERROR in JSON format
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response changeTraditionMetadata(TraditionModel tradition) {

        try (Transaction tx = db.beginTx()) {
            Node traditionNode = db.findNode(Nodes.TRADITION, "id", traditionId);
            if( traditionNode == null ) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("There is no Tradition with this id")
                        .build();
            }

            if (tradition.getOwnerId() != null) {
                Node newUser = db.findNode(Nodes.USER, "id", tradition.getOwnerId());
                if (newUser == null) {
                    return Response.status(Response.Status.NOT_FOUND)
                            .entity("Error: A user with this id does not exist")
                            .build();
                }
                Relationship oldOwnership = traditionNode.getSingleRelationship(ERelations.OWNS_TRADITION, Direction.INCOMING);
                if (!oldOwnership.getStartNode().getProperty("id").toString().equals(tradition.getOwnerId())) {
                    // Remove the old ownership
                    oldOwnership.delete();

                    // Add the new ownership
                    newUser.createRelationshipTo(traditionNode, ERelations.OWNS_TRADITION);
                }
            }
            // Now set the other properties that were passed
            if (tradition.getName() != null )
                traditionNode.setProperty("name", tradition.getName());
            if (tradition.getIsPublic() != null )
                traditionNode.setProperty("is_public", tradition.getIsPublic());
            if (tradition.getLanguage() != null )
                traditionNode.setProperty("language", tradition.getLanguage());
            if (tradition.getDirection() != null )
                traditionNode.setProperty("direction", tradition.getDirection());
            if (tradition.getStemweb_jobid() != null )
                traditionNode.setProperty("stemweb_jobid", tradition.getStemweb_jobid());
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(tradition).build();
    }

    /**
     * Removes a complete tradition
     *
     * @return http response
     */
    @DELETE
    public Response deleteTraditionById() {
        Node foundTradition = DatabaseService.getTraditionNode(traditionId, db);
        if (foundTradition != null) {
            try (Transaction tx = db.beginTx()) {
                /*
                 * Find all the nodes and relations to remove
                 */
                Set<Relationship> removableRelations = new HashSet<>();
                Set<Node> removableNodes = new HashSet<>();
                db.traversalDescription()
                        .depthFirst()
                        .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                        .relationships(ERelations.COLLATION, Direction.OUTGOING)
                        .relationships(ERelations.LEMMA_TEXT, Direction.OUTGOING)
                        .relationships(ERelations.HAS_END, Direction.OUTGOING)
                        .relationships(ERelations.HAS_WITNESS, Direction.OUTGOING)
                        .relationships(ERelations.HAS_STEMMA, Direction.OUTGOING)
                        .relationships(ERelations.HAS_ARCHETYPE, Direction.OUTGOING)
                        .relationships(ERelations.TRANSMITTED, Direction.OUTGOING)
                        .relationships(ERelations.RELATED, Direction.OUTGOING)
                        .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL)
                        .traverse(foundTradition)
                        .nodes().forEach(x -> {
                    x.getRelationships().forEach(removableRelations::add);
                    removableNodes.add(x);
                });

                /*
                 * Remove the nodes and relations
                 */
                removableRelations.forEach(Relationship::delete);
                removableNodes.forEach(Node::delete);
                tx.success();
            } catch (Exception e) {
                return Response.status(Status.INTERNAL_SERVER_ERROR).build();
            }
        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("A tradition with this id was not found!")
                    .build();
        }

        return Response.status(Response.Status.OK).build();
    }

    /***************************
     * Tradition export API
     *
     */

    /**
     * Returns GraphML file from specified tradition owned by user
     *
     * @return XML data
     */
    @GET
    @Produces(MediaType.APPLICATION_XML)
    public Response getTradition() {
        GraphMLExporter parser = new GraphMLExporter();
        return parser.parseNeo4J(traditionId);
    }

    /**
     * Returns the tradition metadata
     *
     * @return TraditionModel
     */
    @GET
    @Path("/info")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTraditionInfo() {
        Node traditionNode = DatabaseService.getTraditionNode(traditionId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity("No such tradition found").build();

        TraditionModel metadata = new TraditionModel(traditionNode);
        return Response.ok(metadata).build();
    }

    /**
     * Returns DOT file from specified tradition owned by user
     *
     * @return Plaintext dot format
     */
    @GET
    @Path("/dot")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getDot() {
        if (DatabaseService.getTraditionNode(traditionId, db) == null)
            return Response.status(Status.NOT_FOUND).entity("No such tradition found").build();

        DotExporter parser = new DotExporter(db);
        return parser.parseNeo4J(traditionId);
    }

    /**
     * Returns a JSON representation of a tradition.
     *
     * @param toConflate - Zero or more relationship types whose readings should be treated as identical
     * @return the JSON alignment
     */
    @GET
    @Path("/json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getJson(@QueryParam("conflate") List<String> toConflate) {
        return new TabularExporter(db).exportAsJSON(traditionId, toConflate);
    }

    /**
     * Returns a CSV representation of a tradition.
     *
     * @param toConflate - Zero or more relationship types whose readings should be treated as identical
     * @return the CSV alignment as plaintext
     */
    @GET
    @Path("/csv")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getCsv(@QueryParam("conflate") List<String> toConflate) {
        return new TabularExporter(db).exportAsCSV(traditionId, ',', toConflate);
    }

    /**
     * Returns a tab-separated representation of a tradition.
     *
     * @param toConflate - Zero or more relationship types whose readings should be treated as identical
     * @return the TSV alignment as plaintext
     */
    @GET
    @Path("/tsv")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getTsv(@QueryParam("conflate") List<String> toConflate) {
        return new TabularExporter(db).exportAsCSV(traditionId, '\t', toConflate);
    }



    /**
     * Recalculate ranks starting from 'startNode'
     * Someone would typically use it after inserting a RELATION or a new Node into the graph,
     * where the startNode will be one of the RELATION-nodes or the new node itself.
     *
     * @param nodeId Where to start the recalculation
     * @return XML data
     */
    public boolean recalculateRank(Long nodeId) {
        Comparator<Node> rankComparator = (n1, n2) -> {
            int compVal = ((Long) n1.getProperty("rank"))
                    .compareTo((Long) n2.getProperty("rank"));
            if (compVal == 0) {
                compVal = Long.valueOf(n1.getId()).compareTo(n2.getId());
            }
            return compVal;
        };
        SortedSet<Node> nodesToProcess = new TreeSet<>(rankComparator);

        try (Transaction tx = db.beginTx()) {
            Node currentNode = db.getNodeById(nodeId);
            Node iterNode;

            while (currentNode != null) {
                long nominalRank = getRank(nodeId);
                if (!currentNode.getProperty("rank").equals(nominalRank)) {
                    currentNode.setProperty("rank", nominalRank);

                    // Look, if a RELATED node has a higher rank
                    long currentNodeRank = (long)currentNode.getProperty("rank");

                    // UPDATE nodes on RELATED vertices, if necessary
                    Iterable<Relationship> relationships = currentNode.getRelationships(ERelations.RELATED);
                    for (Relationship relationship : relationships) {
                        if (!relationship.getProperty("type").equals("transposition") &&
                                !relationship.getProperty("type").equals("repetition")) {
                            iterNode = relationship.getOtherNode(currentNode);
                            if ((Long) iterNode.getProperty("rank") < currentNodeRank) {
                                iterNode.setProperty("rank", currentNodeRank);
                                nodesToProcess.add(iterNode);
                            }
                        }
                    }

                    // Update nodes on OUTGOING & SEQUENCE vertices, if necessary
                    relationships = currentNode.getRelationships(Direction.OUTGOING, ERelations.SEQUENCE);
                    // OUTGOING includes SEQUENCE (outgoing) and RELATED
                    for (Relationship relationship : relationships) {
                        iterNode = relationship.getEndNode();
                        if (Long.valueOf(iterNode.getProperty("rank").toString()) <= currentNodeRank) {
                            iterNode.setProperty("rank", currentNodeRank + 1L);
                            nodesToProcess.add(iterNode);
                        }
                    }
                }

                if (nodesToProcess.isEmpty()) {
                    currentNode = null;
                } else {
                    currentNode = nodesToProcess.first();
                    nodesToProcess.remove(currentNode);
                }
            }
            tx.success();
        } catch (Exception e) {
            return false; //Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return true; // Response.ok().build();
    }

    private long getRank(Long nodeId) {
        long nodeRank;

        try (Transaction tx = db.beginTx()) {
            Node currentNode = db.getNodeById(nodeId);
            nodeRank = (long) currentNode.getProperty("rank");

            Iterable<Relationship> relationships = currentNode.getRelationships(Direction.BOTH);
            for (Relationship relationship : relationships) {
                if (relationship.getType().name().equals(ERelations.SEQUENCE.name())
                        && relationship.getEndNode().getId() == nodeId) {
                    nodeRank = Math.max(nodeRank, (long) relationship.getStartNode().getProperty("rank") + 1L);
                } else if (relationship.getType().name().equals(ERelations.RELATED.name())) {
                    if (!relationship.getProperty("type").equals("transposition") &&
                            !relationship.getProperty("type").equals("repetition")) {
                        nodeRank = Math.max(nodeRank, (long) relationship.getOtherNode(currentNode).getProperty("rank"));
                    }
                }
            }
            tx.success();
        } catch (Exception e) {
            nodeRank = -1L;
        }
        return nodeRank;
    }
}

