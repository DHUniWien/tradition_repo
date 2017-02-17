package net.stemmaweb.rest;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.sun.jersey.multipart.FormDataParam;
import net.stemmaweb.exporter.DotExporter;
import net.stemmaweb.exporter.GraphMLExporter;
import net.stemmaweb.exporter.StemmawebExporter;
import net.stemmaweb.exporter.TabularExporter;
import net.stemmaweb.model.*;
import net.stemmaweb.parser.*;
import net.stemmaweb.services.*;
import net.stemmaweb.services.DatabaseService;

import org.codehaus.jettison.json.JSONObject;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.*;


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

    @Path("/section/{sectionId}")
    public Section getSection(@PathParam("sectionId") String sectionId) throws Exception {
        ArrayList<SectionModel> tradSections = produceSectionList(DatabaseService.getTraditionNode(traditionId, db));
        if (tradSections != null)
            for (SectionModel s : tradSections)
                if (s.getId().equals(sectionId))
                    return new Section(traditionId, sectionId);
        return null;
    }

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
    @POST  // a new stemma
    @Path("/stemma")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response newStemma(String dot) {
        DotParser parser = new DotParser(db);
        Response result = parser.importStemmaFromDot(dot, traditionId);
        if(result.getStatus() == Status.CREATED.getStatusCode()) {
            Stemma restStemma;
            try {
                // Read the stemma name and return the stemma that was created
                JSONObject newStemma = new JSONObject(result.getEntity().toString());
                restStemma = new Stemma(traditionId, newStemma.getString("name"), true);
            } catch (org.codehaus.jettison.json.JSONException e) {
                return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Error reading JSON response on creation").build();
            }
            return restStemma.getStemma();
        } else {
            return result;
        }
    }

    private ArrayList<SectionModel> produceSectionList (Node traditionNode) {
        ArrayList<SectionModel> sectionList = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
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
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return sectionList;
    }

    @POST
    @Path("/section")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response addSection(@FormDataParam("name") String sectionName,
                               @FormDataParam("filetype") String filetype,
                               @FormDataParam("file") InputStream uploadedInputStream) throws IOException {
        // Make a new section node to connect to the tradition in question
        Node sectionNode;
        Node traditionNode = DatabaseService.getTraditionNode(traditionId, db);
        ArrayList<SectionModel> existingSections = produceSectionList(traditionNode);
        try (Transaction tx = db.beginTx()) {
            sectionNode = db.createNode(Nodes.SECTION);
            sectionNode.setProperty("name", sectionName);
            traditionNode.createRelationshipTo(sectionNode, ERelations.PART);
            tx.success();
        }

        // Parse the contents of the given file into that section
        Response result = null;
        if (filetype.equals("csv"))
            // Pass it off to the CSV reader
            result = new TabularParser().parseCSV(uploadedInputStream, sectionNode, ',');
        if (filetype.equals("tsv"))
            // Pass it off to the CSV reader with tab separators
            result = new TabularParser().parseCSV(uploadedInputStream, sectionNode, '\t');
        if (filetype.startsWith("xls"))
            // Pass it off to the Excel reader
            result = new TabularParser().parseExcel(uploadedInputStream, sectionNode, filetype);
        if (filetype.equals("teips"))
            result = new TEIParallelSegParser().parseTEIParallelSeg(uploadedInputStream, sectionNode);
        // TODO we need to parse TEI double-endpoint attachment from CTE
        if (filetype.equals("collatex"))
            // Pass it off to the CollateX parser
            result = new CollateXParser().parseCollateX(uploadedInputStream, sectionNode);
        if (filetype.equals("stemmaweb"))
            // Pass it off to the somewhat legacy GraphML parser
            result = new StemmawebParser().parseGraphML(uploadedInputStream, sectionNode);
        // If we got this far, it was an unrecognized filetype.
        if (result == null)
            result = Response.status(Status.BAD_REQUEST).entity("Unrecognized file type " + filetype).build();

        if (result.getStatus() > 201) {
            // If the result wasn't a success, delete the section node before returning the result.
            Section restSect = new Section(traditionId, String.valueOf(sectionNode.getId()));
            restSect.deleteSection();
        } else {
            // Otherwise, link this section behind the last of the prior sections.
            if (existingSections != null && existingSections.size() > 0) {
                SectionModel ls = existingSections.get(existingSections.size() - 1);
                try (Transaction tx = db.beginTx()) {
                    Node lastSection = db.getNodeById(Long.valueOf(ls.getId()));
                    lastSection.createRelationshipTo(sectionNode, ERelations.NEXT);
                    tx.success();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return result;
    }

    /**
     * Initializes ranks in sessions where readings have no rank-property
     *
     * This does not belong to the official API!
     * It is just a hack to initialize sections where their readings have
     * no "rank" defined
     */
    @GET
    @Path("/initRanks")
    @Produces(MediaType.APPLICATION_JSON)
    public Response initRanks() {
        Node traditionNode = DatabaseService.getTraditionNode(traditionId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity("tradition not found").build();

        ArrayList<SectionModel> updatedSections = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            ArrayList<Node> sectionList = DatabaseService.getRelated(traditionNode, ERelations.PART);
            for(Node section: sectionList) {
                Node startNode = DatabaseService.getRelated(section, ERelations.COLLATION).get(0);
                if (!startNode.hasProperty("rank")) {
                    LinkedList<Node> queue = new LinkedList<>();
                    queue.add(startNode);

                    while (!queue.isEmpty()) {
                        Node curNode = queue.poll();
                        if (!curNode.hasProperty("rank")) {
                            curNode.setProperty("rank", 0L);
                        }
                        Iterator<Relationship> inRels = curNode.getRelationships(Direction.INCOMING, ERelations.SEQUENCE).iterator();
                        if (!inRels.hasNext()) {
                            inRels = curNode.getRelationships(Direction.INCOMING, ERelations.LEMMA_TEXT).iterator();
                        }
                        Long maxRank = -1L;
                        while(inRels.hasNext()) {
                            Relationship curRel = inRels.next();
                            Node inNode = curRel.getStartNode();
                            if (!inNode.hasProperty("rank")) {
                                queue.add(curNode);
                                curNode = null;
                                break;
                            }
                            Long inRank = (Long)inNode.getProperty("rank");
                            if (inRank > maxRank)
                                maxRank = inRank;
                        }
                        if (curNode != null) {
                            maxRank += 1L;
                            if (maxRank > (Long)curNode.getProperty("rank")) {
                                curNode.setProperty("rank", maxRank + 1L);
                            }
                            Iterator<Relationship> outRels = curNode.getRelationships(Direction.OUTGOING, ERelations.SEQUENCE).iterator();
                            if (!inRels.hasNext()) {
                                outRels = curNode.getRelationships(Direction.OUTGOING, ERelations.LEMMA_TEXT).iterator();
                            }
                            while(outRels.hasNext()) {
                                Relationship curRel = outRels.next();
                                Node curOutNode = curRel.getEndNode();
                                if(!queue.contains(curOutNode)) {
                                    queue.add(curOutNode);
                                }
                            }
                        }
                    }
                    updatedSections.add(new SectionModel(section));
                }
            }
            tx.success();
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(updatedSections).build();

    }
    /*
     * Collection retrieval calls
     */

    /*
     * Gets a list of all sections of a tradition with the given id.
     *
     * @return Http Response 200 and a list of section models in JSON on success
     * or an ERROR in JSON format
     */
    @GET
    @Path("/sections")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response getAllSections() {
        Node traditionNode = DatabaseService.getTraditionNode(traditionId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity("tradition not found").build();

        ArrayList<SectionModel> sectionList = produceSectionList(traditionNode);
        if (sectionList == null)
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();

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
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
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
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
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
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
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
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response getAllReadings() {
        Node traditionNode = DatabaseService.getTraditionNode(traditionId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND)
                    .entity("There is no tradition with this id").build();

        ArrayList<SectionModel> allSections = produceSectionList(traditionNode);
        if (allSections == null)
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity("Tradition has no sections").build();

        ArrayList<ReadingModel> readingModels = new ArrayList<>();
        for (SectionModel sm : allSections) {
            Node startNode = DatabaseService.getStartNode(sm.getId(), db);
            if (startNode == null) {
                return Response.status(Status.INTERNAL_SERVER_ERROR)
                        .entity("Section " + sm.getId() + " has no start node").build();
            }

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
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response getIdenticalReadings(@PathParam("startRank") long startRank,
                                         @PathParam("endRank") long endRank) {
        ArrayList<List<ReadingModel>> identicalReadings = collectIdenticalReadings(startRank, endRank);
        if (identicalReadings == null)
            return Response.status(Status.NOT_FOUND).entity("no identical readings were found").build();

        return Response.ok(identicalReadings).build();
    }

    // We want access within net.stemmaweb as well
    public ArrayList<List<ReadingModel>> collectIdenticalReadings(long startRank, long endRank) {
        Node startNode = DatabaseService.getStartNode(traditionId, db);
        if (startNode == null) return null;

        ArrayList<List<ReadingModel>> identicalReadings;
        try {
            ArrayList<ReadingModel> readingModels =
                    getAllReadingsFromSectionBetweenRanks(startNode, startRank, endRank);
            identicalReadings = identifyIdenticalReadings(readingModels, startRank, endRank);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        ArrayList<List<ReadingModel>> result = identicalReadings.stream().filter(x -> x.size() > 0)
                .collect(Collectors.toCollection(ArrayList::new));
        if (result.size() == 0) return null;
        return result;
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
    private ArrayList<ReadingModel> getAllReadingsFromSectionBetweenRanks(
            Node startNode, long startRank, long endRank) {
        ArrayList<ReadingModel> readingModels = new ArrayList<>();
        getReadingsBetweenRanks(startRank, endRank, startNode)
                .forEach(x -> readingModels.add(new ReadingModel(x)));
        readingModels.sort(Comparator.comparing(ReadingModel::getRank));
        return readingModels;
    }

    // Gets identical readings in a list of ReadingModels sorted by rank.
    private ArrayList<List<ReadingModel>> identifyIdenticalReadings(
            ArrayList<ReadingModel> readingModels, long startRank, long endRank) {
        ArrayList<List<ReadingModel>> identicalReadingsList = new ArrayList<>();

        HashMap<String, List<ReadingModel>> rankSet = new HashMap<>();
        for (ReadingModel rm : readingModels) {
            String normReading = Normalizer.normalize(rm.getText(), Normalizer.Form.NFC);
            if (rm.getRank() > endRank)
                break;
            if (rm.getRank() > startRank) {
                for (String k : rankSet.keySet())
                    if (rankSet.get(k).size() > 1)
                        identicalReadingsList.add(rankSet.get(k));
                rankSet.clear();
                rankSet.put(normReading, new ArrayList<>(Collections.singletonList(rm)));
                startRank = rm.getRank();
            }
            else if (rankSet.containsKey(normReading))
                rankSet.get(normReading).add(rm);
            else
                rankSet.put(normReading, new ArrayList<>(Collections.singletonList(rm)));
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
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
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

    /*
     * Tradition-specific calls
     */

    /*
     * Changes the metadata of the tradition.
     *
     * @param tradition in JSON Format
     * @return OK and information about the tradition in JSON on success or an
     * ERROR in JSON format
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response changeTraditionMetadata(TraditionModel tradition) {

        try (Transaction tx = db.beginTx()) {
            Node traditionNode = db.findNode(Nodes.TRADITION, "id", traditionId);
            if( traditionNode == null ) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("There is no Tradition with this id")
                        .build();
            }

            if (tradition.getOwner() != null) {
                Node newUser = db.findNode(Nodes.USER, "id", tradition.getOwner());
                if (newUser == null) {
                    return Response.status(Response.Status.NOT_FOUND)
                            .entity("Error: A user with this id does not exist")
                            .build();
                }
                Relationship oldOwnership = traditionNode.getSingleRelationship(ERelations.OWNS_TRADITION, Direction.INCOMING);
                if (!oldOwnership.getStartNode().getProperty("id").toString().equals(tradition.getOwner())) {
                    // Remove the old ownership
                    oldOwnership.delete();

                    // Add the new ownership
                    newUser.createRelationshipTo(traditionNode, ERelations.OWNS_TRADITION);
                }
            }
            // Now set the other properties that were passed
            if (tradition.getName() != null )
                traditionNode.setProperty("name", tradition.getName());
            if (tradition.getIs_public() != null )
                traditionNode.setProperty("is_public", tradition.getIs_public());
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
                DatabaseService.returnEntireTradition(foundTradition)
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

    /*
     * Tradition export API
     *
     */

    /*
     * Returns the tradition metadata
     *
     * @return TraditionModel
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response getTraditionInfo() {
        Node traditionNode = DatabaseService.getTraditionNode(traditionId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity("No such tradition found").build();

        TraditionModel metadata = new TraditionModel(traditionNode);
        return Response.ok(metadata).build();
    }

    /**
     * Returns GraphML file from specified tradition owned by user
     *
     * @return XML data
     */
    @GET
    @Path("/graphml")
    @Produces(MediaType.APPLICATION_XML)
    public Response getGraphML() {
        if (DatabaseService.getTraditionNode(traditionId, db) == null)
            return Response.status(Status.NOT_FOUND).entity("No such tradition found").build();
        GraphMLExporter parser = new GraphMLExporter();
        return parser.parseNeo4J(traditionId);
    }

    /**
     * Returns GraphML file from specified tradition owned by user
     *
     * @return XML data
     */
    @GET
    @Path("/stemmaweb")
    @Produces(MediaType.APPLICATION_XML)
    public Response getGraphMLStemmaweb() {
        if (DatabaseService.getTraditionNode(traditionId, db) == null)
            return Response.status(Status.NOT_FOUND).entity("No such tradition found").build();
        StemmawebExporter parser = new StemmawebExporter();
        return parser.parseNeo4J(traditionId);
    }

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
        if (DatabaseService.getTraditionNode(traditionId, db) == null)
            return Response.status(Status.NOT_FOUND).entity("No such tradition found").build();

        DotExporter parser = new DotExporter(db);
        return parser.parseNeo4J(traditionId, includeRelatedRelationships);
    }

    /**
     * Returns a JSON representation of a tradition.
     *
     * @param toConflate - Zero or more relationship types whose readings should be treated as identical
     * @return the JSON alignment
     */
    @GET
    @Path("/json")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
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
    @Produces(MediaType.TEXT_PLAIN + "; charset=utf-8")
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
    @Produces(MediaType.TEXT_PLAIN + "; charset=utf-8")
    public Response getTsv(@QueryParam("conflate") List<String> toConflate) {
        return new TabularExporter(db).exportAsCSV(traditionId, '\t', toConflate);
    }


    /**
     * Recalculate ranks starting from 'startNode'
     * Someone would typically use it after inserting a RELATION or a new Node into the graph,
     * where the startNode will be one of the RELATION-nodes or the new node itself.
     *
     * @param nodeId Where to start the recalculation
     * @return boolean (True in case of success, otherwise False)
     */
    public boolean recalculateRank(Long nodeId) {
        Set<Node> nodesWaiting = new HashSet<>();
        Set<Node> nodesToProcess = new HashSet<>();
        Set<Long> idNodesProcessed = new HashSet<>();

        try (Transaction tx = db.beginTx()) {
            Node currentNode = db.getNodeById(nodeId);
            Iterable<Relationship> relationships = currentNode.getRelationships(Direction.INCOMING, ERelations.SEQUENCE, ERelations.LEMMA_TEXT);
            for (Relationship relationship : relationships) {
                idNodesProcessed.add(relationship.getStartNode().getId());
            }
            nodesWaiting.add(currentNode);

            while (!nodesWaiting.isEmpty() || !nodesToProcess.isEmpty()) {
                // handle all nodes that are ready to process first
                for (Node curProcessNode : nodesToProcess) {
                    if (idNodesProcessed.contains(curProcessNode.getId()))
                        continue;

                    Set<Node> curNodes = new HashSet<>();
                    curNodes.add(curProcessNode);
                    curNodes.addAll(getRelatedNodes(curProcessNode));

                    // determine rank
                    Long currentRank = 0L;
                    for (Node curNode : curNodes) {
                        currentRank = Math.max(currentRank, determineNodeRank(curNode));
                    }

                    // update rank and add curNode to "nodesProcessed"
                    for (Node curNode : curNodes) {
                        curNode.setProperty("rank", currentRank);
                        idNodesProcessed.add(curNode.getId());
                    }

                    // put direct successors into set "nodesKnown" for further processing
                    for (Node curNode : curNodes) {
                        relationships = curNode.getRelationships(Direction.OUTGOING, ERelations.SEQUENCE, ERelations.LEMMA_TEXT);
                        for (Relationship relationship : relationships) {
                            Node newNode = relationship.getEndNode();
                            Long newNodeId = newNode.getId();
                            nodesWaiting.add(newNode);
                            idNodesProcessed.remove(newNodeId);
                        }
                    }
                }
                nodesToProcess.clear();

                // all possible nodes are being processed, so look at the set "nodesKnown" for nodes
                // where we know all direct predecessor nodes (i.e. members of "nodesProcessed"
                Set<Node> nodesExamined = new HashSet<>();
                Set<Node> bestNodes = new HashSet<>();
                Long bestNodeRank = Long.MAX_VALUE;
                for (Node curKnownNode: nodesWaiting) {
                    if (nodesExamined.contains(curKnownNode))
                        continue;

                    Set<Node> curNodes = new HashSet<>();
                    curNodes.add(curKnownNode);
                    curNodes.addAll(getRelatedNodes(curKnownNode));

                    boolean predecessorsUnknown = false;
                    Long estimatedRank = 0L;
                    for (Node curNode : curNodes) {
                        estimatedRank = Math.max(estimatedRank, determineNodeRank(curNode));

                        relationships = curNode.getRelationships(Direction.INCOMING, ERelations.SEQUENCE, ERelations.LEMMA_TEXT);
                        for (Relationship relationship : relationships) {
                            if (!idNodesProcessed.contains(relationship.getStartNode().getId())) {
                                predecessorsUnknown = true;
                            }
                        }
                    }
                    if (!predecessorsUnknown) {
                        nodesToProcess.addAll(curNodes);
                    } else if (estimatedRank <= bestNodeRank) {
                        if (estimatedRank < bestNodeRank) {
                            bestNodeRank = estimatedRank;
                            bestNodes.clear();
                        }
                        bestNodes.addAll(curNodes);
                    }
                    nodesExamined.addAll(curNodes);
                }
                if (nodesToProcess.isEmpty()) {
                    // there are no simple dependencies, so take the relationships into account, too
                    nodesToProcess.addAll(bestNodes);
                }
                nodesWaiting.removeAll(nodesToProcess);
            }
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return false; //Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return true;
    }


    private Set<Node> getRelatedNodes(Node startNode) {
        Set<Node> nodeSet = new HashSet<>();
        try (Transaction tx = db.beginTx()) {
            Evaluator e = path -> {
                if (path.lastRelationship() == null)
                    return Evaluation.INCLUDE_AND_CONTINUE; // it's the start node
                String propType = path.lastRelationship().getProperty("type").toString();
                if (propType.equals("transposition") || propType.equals("repetition"))
                    return Evaluation.EXCLUDE_AND_PRUNE;
                return Evaluation.INCLUDE_AND_CONTINUE;
            };
            TraversalDescription relatedTraversal = db.traversalDescription()
                    .depthFirst()
                    .evaluator(e)
                    .relationships(ERelations.RELATED, Direction.BOTH)
                    .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL);
            for ( Node currentNode : relatedTraversal.traverse(startNode).nodes()) {
                nodeSet.add(currentNode);
            }
            tx.success();
        }
        return nodeSet;
    }

    private long determineNodeRank(Node currentNode) {
        long nodeRank = 0;

        try (Transaction tx = db.beginTx()) {
            Iterable<Relationship> relationships = currentNode.getRelationships(Direction.INCOMING, ERelations.SEQUENCE, ERelations.LEMMA_TEXT);
            for (Relationship relationship : relationships) {
                Node prevNode = relationship.getStartNode();
                nodeRank = Math.max(nodeRank, Long.valueOf(prevNode.getProperty("rank").toString()));
            }
            nodeRank += 1L;
            tx.success();
        }
        return nodeRank;
    }
}

