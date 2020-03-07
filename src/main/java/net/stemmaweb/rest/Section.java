package net.stemmaweb.rest;

import com.qmino.miredot.annotations.MireDotIgnore;
import com.qmino.miredot.annotations.ReturnType;
import net.stemmaweb.exporter.DotExporter;
import net.stemmaweb.exporter.GraphMLExporter;
import net.stemmaweb.exporter.TabularExporter;
import net.stemmaweb.model.*;
import net.stemmaweb.services.*;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.graphdb.traversal.Uniqueness;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static net.stemmaweb.rest.Util.jsonerror;
import static net.stemmaweb.rest.Util.jsonresp;
import static net.stemmaweb.services.ReadingService.*;
import static net.stemmaweb.services.RelationService.returnRelationType;

/**
 * Comprises all the API calls related to a tradition section.
 *
 * @author tla
 */
public class Section {
    private GraphDatabaseService db;
    private String tradId;
    private String sectId;

    public Section(String traditionId, String sectionId) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        tradId = traditionId;
        sectId = sectionId;
    }

    /*
     * Delegation
     */

    /**
     * @param sigil - The sigil of the requested witness
     * @return the Witness REST module initialised for that sigil
     */
    @Path("/witness/{sigil}")
    public Witness getWitnessFromSection(@PathParam("sigil") String sigil) {
        return new Witness(tradId, sectId, sigil);
    }


    // Base paths

    /**
     * Get the metadata for a section.
     *
     * @summary Get section
     * @return  a SectionModel for the requested section
     * @statuscode 200 - on success
     * @statuscode 404 - if no such tradition or section exists
     * @statuscode 500 - on failure, with an error message
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = SectionModel.class)
    public Response getSectionInfo() {
        SectionModel result;
        if (!sectionInTradition())
            return Response.status(Response.Status.NOT_FOUND).entity(jsonerror("Tradition and/or section not found")).build();
        try (Transaction tx = db.beginTx()) {
            result = new SectionModel(db.getNodeById(Long.valueOf(sectId)));
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok().entity(result).build();
    }

    /**
     * Update the metadata for a section.
     *
     * @summary Update section
     * @param newInfo - A JSON specification of the section update
     * @return  a SectionModel for the updated section
     * @statuscode 200 - on success
     * @statuscode 404 - if no such tradition or section exists
     * @statuscode 500 - on failure, with an error message
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = SectionModel.class)
    public Response updateSectionInfo(SectionModel newInfo) {
        if (!sectionInTradition())
            return Response.status(Response.Status.NOT_FOUND).entity(jsonerror("Tradition and/or section not found")).build();
        try (Transaction tx = db.beginTx()) {
            Node thisSection = db.getNodeById(Long.valueOf(sectId));
            if (newInfo.getName() != null)
                thisSection.setProperty("name", newInfo.getName());
            if (newInfo.getLanguage() != null)
                thisSection.setProperty("language", newInfo.getLanguage());
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return getSectionInfo();
    }

    /**
     * Delete the specified section, and update the tradition's sequence of sections to
     * account for any resulting gap. Returns a JSON response on error with key 'error'.
     *
     * @summary Delete section
     * @statuscode 200 - on success
     * @statuscode 404 - if no such tradition or section exists
     * @statuscode 500 - on failure, with an error message
     */
    @DELETE
    @ReturnType("java.lang.Void")
    public Response deleteSection() {
        if (!sectionInTradition())
            return Response.status(Response.Status.NOT_FOUND).type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(jsonerror("Tradition and/or section not found")).build();
        try (Transaction tx = db.beginTx()) {
            Node foundSection = db.getNodeById(Long.valueOf(sectId));
            if (foundSection != null) {
                // Find the section either side of this one and connect them if necessary.
                removeFromSequence(foundSection);
                // Collect all nodes and relationships that belong to this section.
                Set<Relationship> removableRelations = new HashSet<>();
                Set<Node> removableNodes = new HashSet<>();
                VariantGraphService.returnTraditionSection(foundSection).nodes()
                        .forEach(x -> {
                            removableNodes.add(x);
                            x.getRelationships(Direction.BOTH).forEach(removableRelations::add);
                        });

                // Remove said nodes and relationships.
                removableRelations.forEach(Relationship::delete);
                removableNodes.forEach(Node::delete);
                // Clean up any annotations that need it.
                Tradition tService = new Tradition(tradId);
                Response pruned = tService.pruneAnnotations();
                if (pruned.getStatus() > 299) {
                    return pruned;
                }
            }
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(jsonerror(e.getMessage())).build();
        }

        return Response.ok().build();
    }

    /*
     * Collections
     */

    /**
     * Gets a list of all the witnesses of the section with the given id.
     *
     * @summary Get witnesses
     * @return A list of witness metadata
     * @statuscode 200 - on success
     * @statuscode 404 - if no such tradition or section exists
     * @statuscode 500 - on failure, with an error message
     */
    @GET
    @Path("/witnesses")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.WitnessModel>")
    public Response getAllWitnessInSection() {
        if (!sectionInTradition())
            return Response.status(Response.Status.NOT_FOUND).entity(jsonerror("Tradition and/or section not found")).build();

        ArrayList<Node> sectionWitnessNodes = collectSectionWitnesses();
        if (sectionWitnessNodes == null)
            return Response.serverError()
                    .entity(jsonerror("No witnesses found in section")).build();

        try (Transaction tx = db.beginTx()) {
            ArrayList<WitnessModel> sectionWits = new ArrayList<>();
            sectionWitnessNodes.forEach(x -> sectionWits.add(new WitnessModel(x)));
            tx.success();
            Collections.sort(sectionWits);
            return Response.ok().entity(sectionWits).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError()
                    .entity(jsonerror("Section witnesses could not be queried")).build();
        }
    }

    // Also used by the GraphML exporter
    public ArrayList<Node> collectSectionWitnesses() {
        HashSet<Node> witnessList = new HashSet<>();
        Node traditionNode = VariantGraphService.getTraditionNode(tradId, db);
        Node sectionStart = VariantGraphService.getStartNode(sectId, db);
        ArrayList<Node> traditionWitnesses = DatabaseService.getRelated(traditionNode, ERelations.HAS_WITNESS);
        try (Transaction tx = db.beginTx()) {
            for (Relationship relationship : sectionStart.getRelationships(ERelations.SEQUENCE)) {
                for (String witClass : relationship.getPropertyKeys()) {
                    for (String sigil : (String[]) relationship.getProperty(witClass)) {
                        for (Node curWitness : traditionWitnesses) {
                            if (sigil.equals(curWitness.getProperty("sigil"))) {
                                witnessList.add(curWitness);
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
            return null;
        }
        return new ArrayList<>(witnessList);
    }

    /**
     * Gets a list of all readings in the given tradition section.
     *
     * @summary Get readings
     * @return A list of reading metadata
     * @statuscode 200 - on success
     * @statuscode 404 - if no such tradition or section exists
     * @statuscode 500 - on failure, with an error message
     */
    @GET
    @Path("/readings")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.ReadingModel>")
    public Response getAllReadings() {
        if (!sectionInTradition())
            return Response.status(Response.Status.NOT_FOUND).entity(jsonerror("Tradition and/or section not found")).build();

        List<ReadingModel> readingModels = sectionReadings();
        if (readingModels == null)
            return Response.serverError().entity(jsonerror("No readings found in section")).build();
        return Response.ok(readingModels).build();
    }

    List<ReadingModel> sectionReadings() {
        ArrayList<ReadingModel> readingModels = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            Node startNode = VariantGraphService.getStartNode(sectId, db);
            if (startNode == null) throw new Exception("Section " + sectId + " has no start node");
            db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .relationships(ERelations.EMENDED, Direction.OUTGOING)
                    .evaluator(Evaluators.all())
                    .uniqueness(Uniqueness.NODE_GLOBAL).traverse(startNode)
                    .nodes().forEach(node -> readingModels.add(new ReadingModel(node)));
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return readingModels;
    }

    /**
     * Gets a list of all relations defined within the given section.
     *
     * @summary Get relations
     * @param includeReadings - Include the ReadingModel information for the source and target
     * @return A list of relation metadata
     * @statuscode 200 - on success
     * @statuscode 404 - if no such tradition exists
     * @statuscode 500 - on failure, with an error message
     */
    @GET
    @Path("/relations")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.RelationModel>")
    public Response getAllRelationships(@DefaultValue("false") @QueryParam("include_readings") String includeReadings) {
        ArrayList<RelationModel> relList = sectionRelations(includeReadings.equals("true"));

        if (relList == null) {
            return Response.serverError().entity(jsonerror("No relations found in section")).build();
        }
        return Response.ok(relList).build();
    }

    ArrayList<RelationModel> sectionRelations() {
        return sectionRelations(false);
    }

    ArrayList<RelationModel> sectionRelations(Boolean includeReadings) {
        ArrayList<RelationModel> relList = new ArrayList<>();

        Node startNode = VariantGraphService.getStartNode(sectId, db);
        try (Transaction tx = db.beginTx()) {
            db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .uniqueness(Uniqueness.NODE_GLOBAL)
                    .traverse(startNode).nodes().forEach(
                    n -> n.getRelationships(ERelations.RELATED, Direction.OUTGOING).forEach(
                            r -> relList.add(new RelationModel(r, includeReadings)))
            );

            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return relList;
    }


    /**
     * Gets a list of all clusters of readings that are related via colocation links.
     *
     * @summary Get colocated clusters of readings
     * @return a list of clusters
     * @statuscode 200 - on success
     * @statuscode 500 - on error
     */
    @GET
    @Path("/colocated")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType("java.util.List<java.util.List<net.stemmaweb.model.ReadingModel>>")
    public Response getColocatedClusters() {
        List<Set<Node>> clusterList;
        try {
            clusterList = RelationService.getClusters(tradId, sectId, db, true);
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
        List<List<ReadingModel>> result = new ArrayList<>();
        for (Set<Node> cluster : clusterList) {
            List<ReadingModel> clusterModel = new ArrayList<>();
            for (Node n : cluster)
                clusterModel.add(new ReadingModel(n));
            result.add(clusterModel);
        }
        return Response.ok(result).build();
    }
    /**
     * Gets the lemma text for the section, if there is any. Returns the text in a JSON object
     * with key 'text'.
     *
     * @summary Get lemma text
     * @param followFinal - Whether or not to follow the 'lemma_text' path
     * @param startRank - Return a substring of the lemma text starting at the given rank
     * @param endRank - Return a substring of the lemma text ending at the given rank
     * @param startRdg - Return a substring of the lemma text starting with the given reading. Overrides startRank.
     * @param endRdg - Return a substring of the lemma text ending at the given reading. Overrides endRank.
     * @return a TextSequenceModel containing the requested lemma text
     * @statuscode 200 - on success
     * @statuscode 404 - if no such tradition exists
     * @statuscode 500 - on failure, with an error message
     */
    @GET
    @Path("/lemmatext")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = TextSequenceModel.class)
    public Response getLemmaText(@QueryParam("final")     @DefaultValue("false") String followFinal,
                                 @QueryParam("startRank") @DefaultValue("1") String startRank,
                                 @QueryParam("endRank")   @DefaultValue("E") String endRank,
                                 @QueryParam("startRdg") String startRdg,
                                 @QueryParam("endRdg")   String endRdg) {
        if (!sectionInTradition())
            return Response.status(Response.Status.NOT_FOUND).entity("Tradition and/or section not found").build();
        List<ReadingModel> sectionLemmata;
        try {
            if (startRdg != null) startRank = rankForReading(startRdg);
            if (endRdg != null) endRank = rankForReading(endRdg);
            sectionLemmata = collectLemmaReadings(followFinal.equals("true"), startRank, endRank);
            // Add on the end node, so we know whether a lacuna marker is needed.
            sectionLemmata.add(new ReadingModel(VariantGraphService.getEndNode(sectId, db)));
        } catch (Exception e) {
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        TextSequenceModel lm = new TextSequenceModel(
                ReadingService.textOfReadings(sectionLemmata, true, followFinal.equals("false")));
        return Response.ok(lm).build();
    }

    /**
     * Gets the list of lemma readings for the section, if there are any. Requesting the "final"
     * lemma sequence will return what was set by .../setlemma; otherwise all readings marked
     * as lemmata will be returned, in order of rank, whether or not they are yet on a lemma
     * path.
     *
     * @summary Get sequence of lemma readings
     * @param followFinal - Whether or not to follow the 'lemma_text' path
     * @param startRank - Return a substring of the lemma text starting at the given rank
     * @param endRank - Return a substring of the lemma text ending at the given rank
     * @param startRdg - Return a substring of the lemma text starting with the given reading. Overrides startRank.
     * @param endRdg - Return a substring of the lemma text ending at the given reading. Overrides endRank.
     * @return A JSON list of lemma text ReadingModels
     * @statuscode 200 - on success
     * @statuscode 404 - if no such tradition exists
     * @statuscode 500 - on failure, with an error message
     */
    @GET
    @Path("/lemmareadings")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.ReadingModel>")
    public Response getLemmaReadings(@QueryParam("final") @DefaultValue("false") String followFinal,
                                     @QueryParam("startRank") @DefaultValue("1") String startRank,
                                     @QueryParam("endRank")   @DefaultValue("E") String endRank,
                                     @QueryParam("startRdg") String startRdg,
                                     @QueryParam("endRdg")   String endRdg) {
        if (!sectionInTradition())
            return Response.status(Response.Status.NOT_FOUND).entity("Tradition and/or section not found").build();
        List<ReadingModel> sectionLemmata;
        try {
            if (startRdg != null) startRank = rankForReading(startRdg);
            if (endRdg != null) endRank = rankForReading(endRdg);
            sectionLemmata = collectLemmaReadings(followFinal.equals("true"), startRank, endRank);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(sectionLemmata).build();
    }

    private List<ReadingModel> collectLemmaReadings(Boolean followFinal, String startFrom, String endAt) {
        List<ReadingModel> result;
        Node sectionStart = VariantGraphService.getStartNode(sectId, db);
        Node sectionEnd = VariantGraphService.getEndNode(sectId, db);
        try (Transaction tx = db.beginTx()) {
            long startRank = Long.valueOf(startFrom);
            long endRank = endAt.equals("E")
                    ? Long.valueOf(sectionEnd.getProperty("rank").toString()) - 1
                    : Long.valueOf(endAt);
            if (followFinal) {
                ResourceIterable<Node> sectionLemmata = db.traversalDescription().depthFirst()
                        .relationships(ERelations.LEMMA_TEXT, Direction.OUTGOING)
                        .evaluator(Evaluators.all())
                        .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL).traverse(sectionStart)
                        .nodes();
                // Limit to the requested rank range
                result = sectionLemmata.stream().map(ReadingModel::new)
                        .filter(x -> x.getRank() >= startRank && x.getRank() <= endRank)
                        .collect(Collectors.toList());
            } else {
                result = db.traversalDescription().depthFirst()
                        .expand(new AlignmentTraverse())
                        .uniqueness(Uniqueness.NODE_GLOBAL)
                        .traverse(sectionStart).nodes().stream()
                        .filter(x -> x.hasLabel(Nodes.READING)
                                && x.hasProperty("is_lemma")
                                && x.getProperty("is_lemma").equals(true)
                                && (Long) x.getProperty("rank") >= startRank
                                && (Long) x.getProperty("rank") <= endRank)
                        .map(ReadingModel::new).sorted().collect(Collectors.toList());
            }
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            throw(e);
        }
        return result;
    }

    private String rankForReading(String rdgId) {
        String answer;
        try (Transaction tx = db.beginTx()) {
            Node rdgNode = db.getNodeById(Long.valueOf(rdgId));
            answer = rdgNode.getProperty("rank").toString();
            tx.success();
        }
        return answer;
    }

    /**
     * Return a list of annotations that refer to a node belonging to this section. The 'label'
     * query parameter can be specified one or more times to restrict the output to the selected
     * annotation types. If the 'recursive' query parameter has a value of 'true', then the
     * results will include the ancestors of the (selected) section annotations.
     *
     * @param filterLabels - one or more annotation labels to restrict the query to
     * @param recurse - return the ancestors of the selected annotations as well
     * @return A list of AnnotationModels representing the requested annotations on the section
     * @statuscode 200 - on success
     * @statuscode 404 - if no such tradition exists
     * @statuscode 500 - on failure, with an error message
     */
    @GET
    @Path("/annotations")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.AnnotationModel>")
    public Response getAnnotationsOnSection(@QueryParam("label") List<String> filterLabels,
                                            @QueryParam("recursive") @DefaultValue("false") String recurse) {
        if (!sectionInTradition())
            return Response.status(Response.Status.NOT_FOUND).entity("Tradition and/or section not found").build();
        List<AnnotationModel> result = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            // We want to find all annotation nodes that are linked both to the tradition node
            // and to some node in this section.
            HashSet<Node> foundAnns = new HashSet<>();
            for (Node n : VariantGraphService.returnTraditionSection(sectId, db).nodes()) {
                StreamSupport.stream(n.getRelationships(Direction.INCOMING).spliterator(), false)
                        .filter(x -> x.getStartNode().hasRelationship(ERelations.HAS_ANNOTATION, Direction.INCOMING))
                        .map(Relationship::getStartNode).forEach(foundAnns::add);
            }
            // Filter the annotations if we have been asked to
            if (filterLabels.size() > 0) {
                for (Node a : new ArrayList<>(foundAnns)) {
                    boolean foundLabel = false;
                    for (Label l : a.getLabels()) {
                        foundLabel = filterLabels.contains(l.name()) || foundLabel;
                    }
                    if (!foundLabel) foundAnns.remove(a);
                }
            }

            // If we've been asked for referents too, add them to the model
            if (recurse.equals("true")) {
                for (Node n : new ArrayList<>(foundAnns)) {
                    Annotation aService = new Annotation(tradId, String.valueOf(n.getId()));
                    foundAnns.addAll(aService.collectReferents(true));
                }
            }
            foundAnns.forEach(x -> result.add(new AnnotationModel(x)));
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }


        return Response.ok(result).build();
    }

    /**
     * Return a list of variant groupings suitable for a critical apparatus. The base text to use
     * is determined as follows:
     *  - If the 'base' parameter is given, that witness text will be the base.
     *  - If not, and '/setlemma' has been called on the section, that lemma text will be the base.
     *  - Otherwise, the majority text will be calculated and used as the base.
     *
     * @param significant - Restrict the variant groups to the given significance level or above
     * @param exclude_type1 - If true, exclude type 1 (i.e. singleton) variants from the groupings
     * @param combine - If true, attempt to combine non-colocated variants (e.g. transpositions) into
     *                the VariantLocationModel of the corresponding base
     * @param baseWitness  - Use the path of the given witness as the base path.
     * @param conflate - The name of a relation type that should be used for normalization
     *
     * @return A list of VariantLocationModels
     */

    @GET
    @Path("/variants")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.VariantLocationModel>")
    public Response getVariantGroups(@DefaultValue("no") @QueryParam("significant") String significant,
                                     @DefaultValue("no") @QueryParam("exclude_type1") String exclude_type1,
                                     @DefaultValue("no") @QueryParam("combine_dislocations") String combine,
                                                         @QueryParam("base_witness") String baseWitness,
                                                         @QueryParam("normalize") String conflate) {
        if (!sectionInTradition())
            return Response.status(Response.Status.NOT_FOUND).entity("Tradition and/or section not found").build();

        List<VariantLocationModel> vlocs;
        try (Transaction tx = db.beginTx()) {
            Node sectionNode = db.getNodeById(Long.valueOf(sectId));
            // Normalize the graph if we have been asked to
            if (conflate != null)
                VariantGraphService.normalizeGraph(sectionNode, conflate);

            // See which list of readings will serve as our base text
            Node startNode = VariantGraphService.getStartNode(sectId, db);
            TraversalDescription baseWalker = db.traversalDescription().depthFirst();
            if (baseWitness != null)
                // We use the requested witness text.
                baseWalker = baseWalker.evaluator(new WitnessPath(baseWitness).getEvalForWitness());
            else if (startNode.hasRelationship(ERelations.LEMMA_TEXT, Direction.OUTGOING))
                // We use the lemma text.
                baseWalker = baseWalker.relationships(ERelations.LEMMA_TEXT);
            else {
                // We calculate and use the majority text.
                VariantGraphService.calculateMajorityText(sectionNode);
                baseWalker = baseWalker.relationships(ERelations.MAJORITY);
            }
            List<Node> baseText = baseWalker.traverse(startNode).nodes().stream().collect(Collectors.toList());

            // Walk the base text looking for diversions
            vlocs = findVariants(baseText, conflate != null ? ERelations.NSEQUENCE : ERelations.SEQUENCE, combine.equals("true"));

            // Filter for type1 variants
            if (exclude_type1.equals("true"))
                vlocs = vlocs.stream().filter(x -> !isTypeOne(x)).collect(Collectors.toList());

            // Filter for significant variants
            if (!significant.equals("no"))
                vlocs = vlocs.stream().filter(x -> meetsSignificance(x, significant)).collect(Collectors.toList());

            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(e.getMessage()).build();
        }
        // Sort the list by rank index
        vlocs.sort(Comparator.comparingLong(VariantLocationModel::getRankIndex));

        return Response.ok(vlocs).build();
    }

    /**
     * Checks whether a VariantLocationModel is "type 1", i.e. none of the variants appear in more than
     * a single witness.
     *
     * @param vloc - The VariantLocationModel to check
     * @return true or false
     */
    private static boolean isTypeOne(VariantLocationModel vloc) {
        boolean is_type1 = true;
        for (VariantModel vm : vloc.getVariants()) {
            is_type1 = is_type1 && vm.getWitnessList().size() == 1;
        }
        return is_type1;
    }

    /**
     * Tests whether the variant location in question meets a significance test. The test succeeds
     * if any of the relations between the base and the variant(s) meet the given threshold.
     *
     * @param vloc - The variant location to test
     * @param significance - The minimum significance to pass the threshold
     * @return true or false
     */
    private static boolean meetsSignificance (VariantLocationModel vloc, String significance) {
        // If there are no relations associated with the variant, it can't be significant.
        boolean meets = false;
        for (RelationModel rm : vloc.getRelations())
            if (significance.equals("maybe"))
                meets = meets || !rm.getIs_significant().equals("no");
            else if (significance.equals("yes"))
                meets = meets || rm.getIs_significant().equals("yes");
        return meets;
    }

    /**
     * The core logic for variant detection.
     *
     * @param sequence - The base text sequence off of which the variants are based.
     * @param follow - The type of sequence relationship we are following, i.e. whether we are in normalized mode.
     * @param combine - Whether to combine dislocations into the associated variants
     * @return A list of VariantLocationModels for the whole sequence.
     */
    private List<VariantLocationModel> findVariants(List<Node> sequence, RelationshipType follow, boolean combine) {
        Node curr = sequence.get(0); // this should be a START node
        Node end = sequence.get(sequence.size() - 1); // this should be an END node
        // We might come up with an arbitrary number of overlapping variant locations, depending
        // on how the graph branches and rejoins between the two common points. These need to be
        // indexed both by start and end.
        Map<Node, Map<Node,VariantLocationModel>> vlocs = new HashMap<>();
        int currIndex = 0;
        while(!curr.equals(end)) {
            // Skip ahead until that curr is the last common node before a branch.
            if (curr.getDegree(follow, Direction.OUTGOING) == 1) {
                Node next = curr.getSingleRelationship(follow, Direction.OUTGOING).getEndNode();
                // next should be the next node in the base sequence.
                if (is_common(next, follow, combine)) {
                    curr = next;
                    currIndex++;
                    continue;
                }
            }
            // We have a fork ahead of us. Find where the fork ends and get the chain of
            // base readings.
            Node variantEnd;
            int vIndex = currIndex;
            do {
                variantEnd = sequence.get(++vIndex);
            } while (!is_common(variantEnd, follow, combine) && !variantEnd.equals(end));
            List<Node> baseChain = sequence.subList(currIndex, vIndex+1);

            // Now find all paths between these two nodes.
            for (org.neo4j.graphdb.Path variantPath : db.traversalDescription()
                    .breadthFirst()
                    .relationships(follow, Direction.OUTGOING)
                    .uniqueness(Uniqueness.RELATIONSHIP_PATH)
                    .evaluator(Evaluators.includeWhereEndNodeIs(variantEnd))
                    .traverse(curr)) {
                // Only pay attention if we reached the end
                // if (!variantPath.endNode().equals(variantEnd)) continue;
                // Skip it if it is the base path.
                ArrayList<Node> variantChain = new ArrayList<>();
                variantPath.nodes().forEach(variantChain::add);
                if (variantChain.equals(baseChain))
                    continue;

                // This isn't the base path, so see where this path rejoins the base path.
                // If it rejoins and branches again, we have to treat each branch as a separate variant.
                // The intersections array will hold a list of indices to variantChain whose readings also
                // exist in baseChain.
                ArrayList<Integer> intersections = new ArrayList<>();
                for (int i = 1; i < variantChain.size(); i++) {
                    if (baseChain.contains(variantChain.get(i)))
                        intersections.add(i);
                }
                ArrayList<Relationship> variantLinks = new ArrayList<>();
                variantPath.relationships().forEach(variantLinks::add);
                int departure = 0;
                // Get the chain of relationships between the point of departure (i.e. the last intersection)
                // and the next intersection, and make a VariantModel out of that sub-path.
                for (int i : intersections) {
                    // If the next intersection is only one step along from the point of departure, then
                    // either we have an omission at this location, or this location is simply following
                    // the base chain. We need to see which case we have.
                    boolean skip = false;
                    if (i - departure == 1) {
                        int bIdx = baseChain.indexOf(variantChain.get(departure));
                        skip = baseChain.get(bIdx+1).equals(variantChain.get(i));
                    }
                    // It seems we have a real variant. Proceed.
                    if (!skip) {
                        VariantLocationModel vlm = getVLM(vlocs, baseChain, variantChain.get(departure), variantChain.get(i));
                        // Add this variant to that VLModel
                        HashSet<String> pathSigla = new HashSet<>();
                        // Our list of sigla for this variant path is the intersection of all sigla that
                        // occur along it.
                        for (Relationship r : variantLinks.subList(departure, i)) {
                            if (pathSigla.size() > 0)
                                pathSigla.retainAll(collectSigla(r));
                            else
                                pathSigla.addAll(collectSigla(r));
                        }
                        // If we are out of sigla, then this path doesn't comprise a variant.
                        if (pathSigla.isEmpty()) continue;
                        Map<String, List<String>> vWits = new HashMap<>();
                        for (String sig : pathSigla) {
                            String[] split = sig.split("\\|");
                            String witlayer = split[1];
                            if (vWits.containsKey(witlayer))
                                vWits.get(witlayer).add(split[0]);
                            else {
                                List<String> wl = new ArrayList<>();
                                wl.add(split[0]);
                                vWits.put(witlayer, wl);
                            }
                        }
                        // Now that we have the map of witnesses, make the VariantModel and add it.
                        VariantModel vm = new VariantModel();
                        List<Node> variantReadings = variantChain.subList(departure+1, i);
                        vm.setReadings(variantReadings.stream().map(ReadingModel::new).collect(Collectors.toList()));
                        vm.setWitnesses(vWits);
                        vm.setNormal(follow.equals(ERelations.NSEQUENCE));
                        // Do any of these variant readings have a non-colocated relation that points somewhere else?
                        /* List<String> ourBase = vlm.getBase().stream().map(ReadingModel::getId).collect(Collectors.toList());
                        for (Node vr : variantReadings) {
                            for (Relationship vrel : vr.getRelationships(ERelations.RELATED)) {
                                RelationTypeModel vreltype = returnRelationType(tradId, vrel.getProperty("type").toString());
                                if (!vreltype.getIs_colocation() &&
                                        !ourBase.contains(String.valueOf(vrel.getOtherNode(vr).getId())))
                                    vm.setDisplaced(true);
                            }
                        } */
                        vlm.addVariant(vm);
                        // vlm.setDisplacement(vlm.hasDisplacement() || vm.getDisplaced());
                    }
                    // The intersection we dealt with is now our new point of departure.
                    departure = i;
                }
            }
            // If we are in "combine dislocations" mode, there might not have been any variant paths;
            // we have to check for

            // Move on.
            curr = variantEnd;
            currIndex = vIndex;
        }
        // Collect the VariantLocationModels we made
        List<VariantLocationModel> result = new ArrayList<>();
        for (Map<Node,VariantLocationModel> hm : vlocs.values()) {
            result.addAll(hm.values());
        }
        // Add relation information to each of them. This will also notice displaced variants.
        for (VariantLocationModel vlm : result)
            collectRelationsInLocation(vlm);
        // Sort the result by rank index and return
        result.sort(Comparator.comparingLong(VariantLocationModel::getRankIndex));
        return result;
    }

    /**
     * Utility method to check whether a node is common in the given context.
     *
     * @param n - The node to check
     * @param follow - The RelationshipType we are following, i.e. whether we are in normalized mode
     * @return true or false
     */
    private boolean is_common(Node n, RelationshipType follow, boolean combine) {
        if (n.getProperty("is_start", false).equals(true)) return true;
        if (n.getProperty("is_end", false).equals(true)) return true;
        String propName = follow.equals(ERelations.NSEQUENCE) ? "ncommon" : "is_common";
        boolean propIsCommon = n.getProperty(propName, false).equals(true);
        if (combine && propIsCommon) {
            // Check for dislocations
            for (Relationship r : n.getRelationships(ERelations.RELATED)) {
                RelationTypeModel rtm = returnRelationType(tradId, r.getProperty("type").toString());
                propIsCommon = propIsCommon && rtm.getIs_colocation();
            }
        }
        return propIsCommon;
    }

    /**
     * Returns (initializing, if necessary) the VariantLocationModel for the given start and end
     * readings, using the given base.
     *
     * @param vlocs - The existing map of start node -> end node -> initialized model
     * @param baseChain - The relevant chain of base readings
     * @param vStart - The "before" node for the required VLM
     * @param vEnd - The "after" node for the required VLM
     * @return The VLM that was requested
     */
    private static VariantLocationModel getVLM(Map<Node, Map<Node,VariantLocationModel>> vlocs,
                                               List<Node> baseChain,
                                               Node vStart,
                                               Node vEnd) {
        // Retrieve any existing VariantLocationModel, or create a new one
        VariantLocationModel vlm = new VariantLocationModel();
        if (vlocs.containsKey(vStart)) {
            if (vlocs.get(vStart).containsKey(vEnd))
                vlm = vlocs.get(vStart).get(vEnd);
            else
                vlocs.get(vStart).put(vEnd, vlm);
        } else {
            HashMap<Node,VariantLocationModel> hm = new HashMap<>();
            hm.put(vEnd, vlm);
            vlocs.put(vStart, hm);
        }
        // Initialize the VLModel if it is new.
        if (vlm.getRankIndex() == 0) {
            List<ReadingModel> baseReadings = baseChain
                    .subList(baseChain.indexOf(vStart), baseChain.indexOf(vEnd)+1)
                    .stream().map(ReadingModel::new).collect(Collectors.toList());
            vlm.setBefore(baseReadings.remove(0));
            vlm.setAfter(baseReadings.remove(baseReadings.size() - 1));
            vlm.setBase(baseReadings);
            if (baseReadings.size() > 0)
                vlm.setRankIndex(baseReadings.get(0).getRank());
            else
                vlm.setRankIndex(vlm.getBefore().getRank() + 1);
            vlm.setNormalised(vStart.hasRelationship(ERelations.NSEQUENCE, Direction.OUTGOING));
        }
        return vlm;
    }

    /**
     * Collects and returns the sigla along a given sequence link.
     *
     * @param r the SEQUENCE or NSEQUENCE relationship to collect from
     * @return a list of sigla in "sigil|layer" format
     */
    private static List<String> collectSigla (Relationship r) {
        List<String> collected = new ArrayList<>();
        for (String layer : r.getPropertyKeys())
            for (String sig : (String[]) r.getProperty(layer))
                collected.add(String.format("%s|%s", sig, layer));
        return collected;
    }

    /**
     * Looks for all RELATED links between nodes involved in a VariantLocationModel, and adds the
     * corresponding RelationModels to the VariantLocationModel in question.
     *
     * @param vlm - the VariantLocationModel to analyze
     */
    private void collectRelationsInLocation(VariantLocationModel vlm) {
        Set<Relationship> relations = new HashSet<>();
        // Gather all the nodes we need
        Set<Node> clusterNodes = new HashSet<>();
        try (Transaction tx = db.beginTx()) {
            for (ReadingModel rm : vlm.getBase())
                clusterNodes.add(db.getNodeById(Long.valueOf(rm.getId())));
            HashMap<Node, VariantModel> vModelForReading = new HashMap<>();
            for (VariantModel vm : vlm.getVariants())
                for (ReadingModel rm : vm.getReadings()) {
                    Node vrdg = db.getNodeById(Long.valueOf(rm.getId()));
                    clusterNodes.add(vrdg);
                    // As long as we're here, make a map of variant node -> VariantModel
                    vModelForReading.put(vrdg, vm);
                }
            for (Node n : clusterNodes) {
                for (Relationship rel : n.getRelationships(ERelations.RELATED, Direction.OUTGOING))
                    // Add any relation we find that links to another node in this variant location
                    if (clusterNodes.contains(rel.getEndNode()))
                        relations.add(rel);
                    // Add the relation anyway, if it signifies a displaced variant reading.
                    // Also mark the variant and its location as being displaced / having a displacement.
                    else if (vModelForReading.containsKey(n)){
                        RelationTypeModel rtm = returnRelationType(tradId, rel.getProperty("type").toString());
                        if (!rtm.getIs_colocation()) {
                            relations.add(rel);
                            vModelForReading.get(n).setDisplaced(true);
                            vlm.setDisplacement(true);
                        }
                    }
            }
            List<RelationModel> rml = relations.stream().map(RelationModel::new).collect(Collectors.toList());
            vlm.setRelations(rml);
            tx.success();
        }
    }

    // Deal with non-colocated variants
    private static void combineDisplacements(List<VariantLocationModel> vlmlist) {
        // - Get the list of transpositions
        for (VariantLocationModel vlm : vlmlist.stream()
                .filter(VariantLocationModel::hasDisplacement).collect(Collectors.toList())) {
            // - Find the variant location that corresponds to each of the addition + omission
            // Which variant(s) is dislocated?
            for (VariantModel vm : vlm.getVariants().stream()
                    .filter(VariantModel::getDisplaced).collect(Collectors.toList())) {

            }
            // - Alter the addition bzw. omission to be a displaced variant
            // - Remove any now-empty variant locations
            // - If it is a symmetrical transposition, make a new variant location to indicate this!

        }
    }


    /*
     * Manipulation
     */

    /**
     * Move this section to a new place in the section sequence. Upon error, returns a JSON response
     * with key 'error'.
     *
     * @summary Reorder section
     * @param priorSectID - the ID of the section that should precede this one; "none" if this section should be first.
     * @statuscode 200 - on success
     * @statuscode 400 - if the priorSectId doesn't belong to the given tradition
     * @statuscode 404 - if no such tradition or section exists
     * @statuscode 500 - on failure, with an error message
     */
    @PUT
    @Path("/orderAfter/{priorSectID}")
    @Produces(MediaType.TEXT_PLAIN)
    @ReturnType("java.lang.Void")
    public Response reorderSectionAfter(@PathParam("priorSectID") String priorSectID) {
        try (Transaction tx = db.beginTx()) {
            if (!sectionInTradition())
                return Response.status(Response.Status.NOT_FOUND).entity("Tradition and/or section not found").build();
            if (!priorSectID.equals("none") && !VariantGraphService.sectionInTradition(tradId, priorSectID, db))
                return Response.status(Response.Status.NOT_FOUND).entity("Requested prior section not found").build();
            if (priorSectID.equals(sectId))
                return Response.status(Response.Status.BAD_REQUEST).entity("Cannot reorder a section after itself").build();

            Node thisSection = db.getNodeById(Long.valueOf(sectId));

            // Check that the requested prior section also exists and is part of the tradition
            Node priorSection = null;   // the requested prior section
            Node latterSection = null;  // the section after the requested prior
            if (priorSectID.equals("none")) {
                // There is no prior section, and the first section will become the latter one. Find it.
                ArrayList<Node> sectionNodes = VariantGraphService.getSectionNodes(tradId, db);
                if (sectionNodes == null)
                    return Response.serverError().entity("Tradition has no sections").build();
                for (Node s : sectionNodes) {
                    if (!s.hasRelationship(ERelations.NEXT, Direction.INCOMING)) {
                        latterSection = s;
                        break;
                    }
                }
                if (latterSection == null)
                    return Response.serverError().entity("Could not find tradition's first section").build();

                // If we request the first section to go first, it should be a no-op.
                else if (latterSection.equals(thisSection))
                    return Response.ok().build();
            } else {
                priorSection = db.getNodeById(Long.valueOf(priorSectID));
                if (priorSection == null) {
                    return Response.status(Response.Status.NOT_FOUND).entity("Section " + priorSectID + "not found").build();
                }
                Node pnTradition = VariantGraphService.getTraditionNode(priorSection);
                if (!pnTradition.getProperty("id").equals(tradId))
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity("Section " + priorSectID + " doesn't belong to this tradition").build();

                if (priorSection.hasRelationship(Direction.OUTGOING, ERelations.NEXT)) {
                    Relationship oldSeq = priorSection.getSingleRelationship(ERelations.NEXT, Direction.OUTGOING);
                    latterSection = oldSeq.getEndNode();
                    oldSeq.delete();
                }
            }

            // Remove our node from its existing sequence
            removeFromSequence(thisSection);

            // Link it up to the prior if it exists
            if (priorSection != null) priorSection.createRelationshipTo(thisSection, ERelations.NEXT);
            // ...and to the old "next" if it exists
            if (latterSection != null) thisSection.createRelationshipTo(latterSection, ERelations.NEXT);
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }
        return Response.ok().build();
    }

    /**
     * Split a section into two at the given graph rank, and adjust the tradition's section order accordingly.
     * Returns a JSON response of the form {@code {"sectionId": <ID>}}, containing the ID of the new section.
     * Upon error, returns an error message with key 'error'.
     *
     * @summary Reorder section
     * @param rankstr - the rank at which the section should be split
     * @return  JSON response with key 'sectionId' or key 'error'
     * @statuscode 200 - on success
     * @statuscode 400 - if the section doesn't contain the specified rank
     * @statuscode 404 - if no such tradition or section exists
     * @statuscode 500 - on failure, with an error message
     */
    @POST
    @Path("/splitAtRank/{rankstr}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    // @ReturnType("java.lang.String")
    public Response splitAtRank (@PathParam("rankstr") String rankstr) {
        if (!sectionInTradition())
            return Response.status(Response.Status.NOT_FOUND).entity(jsonerror("Tradition and/or section not found")).build();

        Long rank = Long.valueOf(rankstr);
        // Get the reading(s) at the given rank, and at the prior rank
        Node startNode = VariantGraphService.getStartNode(sectId, db);
        Node sectionEnd = VariantGraphService.getEndNode(sectId, db);
        Long newSectionId;

        try (Transaction tx = db.beginTx()) {
            Node thisSection = db.getNodeById(Long.valueOf(sectId));

            // Make sure we aren't just trying to split off the end node
            if (rank.equals(sectionEnd.getProperty("rank")))
                return Response.status(Response.Status.BAD_REQUEST).entity(jsonerror("Cannot split section at its end rank")).build();

            // Make a list of relationships that cross our requested rank
            // Keep track of the witnesses that had lacunae at this point; they will need lacunae
            // at the start of the new section too.
            boolean lacunoseWitsPresent = false;
            HashSet<Relationship> linksToSplit = new HashSet<>();
            for (Relationship r : sequencesCrossingRank(rank, false)) {
                Node thisStart = r.getStartNode();
                Long endRank = (Long) r.getEndNode().getProperty("rank");
                linksToSplit.add(r);
                if (thisStart.hasProperty("is_lacuna") && thisStart.getProperty("is_lacuna").equals(true)
                        && endRank > rank) lacunoseWitsPresent = true;
            }

            // Make sure we have readings at the requested rank in this section
            if (linksToSplit.size() == 0)
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(jsonerror("Rank not found within section")).build();

            // Make a new section node and insert it into the sequence
            Node newSection = db.createNode(Nodes.SECTION);
            VariantGraphService.getTraditionNode(thisSection).createRelationshipTo(newSection, ERelations.PART);
            newSection.setProperty("name", thisSection.getProperty("name") + " split");
            newSectionId = newSection.getId();
            Section newSectionRest = new Section(tradId, String.valueOf(newSection.getId()));
            Response reorder = newSectionRest.reorderSectionAfter(sectId);
            if (reorder.getStatus() != Response.Status.OK.getStatusCode())
                return reorder;

            // Attach the old END node to the new section
            sectionEnd.getSingleRelationship(ERelations.HAS_END, Direction.INCOMING).delete();
            newSection.createRelationshipTo(sectionEnd, ERelations.HAS_END);

            // Close off the prior rank with a new END node, and the requested rank with a new
            // START node
            Node newEnd = db.createNode(Nodes.READING);
            newEnd.setProperty("is_end", true);
            newEnd.setProperty("text", "#END#");
            newEnd.setProperty("rank", rank);
            newEnd.setProperty("section_id", Long.valueOf(sectId));
            thisSection.createRelationshipTo(newEnd, ERelations.HAS_END);

            Node newStart = db.createNode(Nodes.READING);
            newStart.setProperty("is_start", true);
            newStart.setProperty("text", "#START#");
            newStart.setProperty("rank", 0L);
            newStart.setProperty("section_id", newSection.getId());
            newSection.createRelationshipTo(newStart, ERelations.COLLATION);

            Node newLacuna = null;
            if (lacunoseWitsPresent) {
                newLacuna = db.createNode(Nodes.READING);
                newLacuna.setProperty("is_lacuna", true);
                newLacuna.setProperty("text", "#LACUNA#");
                newLacuna.setProperty("rank", 1L);
                newLacuna.setProperty("section_id", Long.valueOf(sectId));
            }

            // Reattach the readings to their respective new end/start nodes
            for (Relationship crossed : linksToSplit) {
                Node lastInOld = crossed.getStartNode();
                Node firstInNew = crossed.getEndNode();
                if (lastInOld.hasProperty("is_lacuna") && lastInOld.getProperty("is_lacuna").equals(true)
                        && (Long) firstInNew.getProperty("rank") > rank) {
                    ReadingService.transferWitnesses(newStart, newLacuna, crossed);
                    ReadingService.transferWitnesses(newLacuna, firstInNew, crossed);
                } else if (!firstInNew.equals(sectionEnd))
                    ReadingService.transferWitnesses(newStart, firstInNew, crossed);
                if (!lastInOld.equals(startNode))
                    ReadingService.transferWitnesses(lastInOld, newEnd, crossed);

            }
            linksToSplit.forEach(Relationship::delete);

            // Collect all readings from the second section and alter their section metadata
            final Long newId = newSection.getId();
            db.traversalDescription().depthFirst().expand(new AlignmentTraverse(newStart))
                    .uniqueness(Uniqueness.NODE_GLOBAL).traverse(newStart).nodes()
                    .stream().forEach(x -> {
                        if (x.hasLabel(Nodes.EMENDATION)) {
                            x.getSingleRelationship(ERelations.HAS_EMENDATION, Direction.INCOMING).delete();
                            newSection.createRelationshipTo(x, ERelations.HAS_EMENDATION);
                        }
                        if (x.hasLabel(Nodes.READING)) {
                            x.setProperty("section_id", newId);
                            if (!x.equals(newStart))
                                x.setProperty("rank", Long.valueOf(x.getProperty("rank").toString()) - rank + 1);
                        }
                    }
            );

            // Check for lacunae - if the last reading in the old section is a lacuna, the first reading
            // in the new section should also be one


            // Re-initialize the ranks on the new section
            // recalculateRank(newStart);

            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok().entity(jsonresp("sectionId", newSectionId)).build();
    }


    @SuppressWarnings("SameParameterValue")
    private List<Relationship> sequencesCrossingRank(Long rank, Boolean leftfencepost) {
        Node startNode = VariantGraphService.getStartNode(sectId, db);
        return db.traversalDescription().depthFirst()
                .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                .evaluator(Evaluators.all())
                .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL).traverse(startNode)
                .relationships().stream().filter(x -> crossesRank(x, rank, leftfencepost))
                .collect(Collectors.toList());
    }

    private static boolean crossesRank (Relationship r, Long rank, boolean leftfencepost) {
        Long startRank = (Long) r.getStartNode().getProperty("rank");
        Long endRank = (Long) r.getEndNode().getProperty("rank");
        boolean startsBefore = startRank < rank || (leftfencepost && startRank.equals(rank));
        boolean endsAfter = endRank > rank || (!leftfencepost && endRank.equals(rank));
        return startsBefore && endsAfter;
    }

    /**
     * Merge two sections into one, and adjust the tradition's section order accordingly. The
     * specified sections must be contiguous, and will be merged according to their existing order.
     *
     * @summary Merge sections
     * @param otherId - the rank at which the section should be split
     * @statuscode 200 - on success
     * @statuscode 400 - if the sections are not contiguous
     * @statuscode 404 - if no such tradition or section exists
     * @statuscode 500 - on failure, with an error message
     */
    @POST
    @Path("/merge/{otherId}")
    @Produces(MediaType.TEXT_PLAIN)
    @ReturnType("java.lang.Void")
    public Response mergeSections (@PathParam("otherId") String otherId) {
        if (!sectionInTradition())
            return Response.status(Response.Status.NOT_FOUND).entity("Tradition and/or section not found").build();
        if (!VariantGraphService.sectionInTradition(tradId, otherId, db))
            return Response.status(Response.Status.NOT_FOUND).entity("Requested other section not found").build();

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
            Node oldEnd = VariantGraphService.getEndNode(String.valueOf(firstSection.getId()), db);
            Node oldStart = VariantGraphService.getStartNode(String.valueOf(secondSection.getId()), db);
            Node trueStart = VariantGraphService.getStartNode(String.valueOf(firstSection.getId()), db);
            Node trueEnd = VariantGraphService.getEndNode(String.valueOf(secondSection.getId()), db);

            // Collect all readings from the second section and alter their section metadata
            final Long keptId = firstSection.getId();
            db.traversalDescription().depthFirst().expand(new AlignmentTraverse(oldStart))
                    .uniqueness(Uniqueness.NODE_GLOBAL).traverse(oldStart).nodes()
                    .stream().filter(x -> x.hasLabel(Nodes.READING)).forEach(x -> x.setProperty("section_id", keptId));

            for (Relationship r : secondSection.getRelationships(ERelations.HAS_EMENDATION)) {
                Node e = r.getEndNode();
                r.delete();
                firstSection.createRelationshipTo(e, ERelations.HAS_EMENDATION);
            }

            // Collect the last readings from the first section, for later rank recalculation
            List<Node> firstSectionEnd = new ArrayList<>();
            oldEnd.getRelationships(Direction.INCOMING, ERelations.LEMMA_TEXT, ERelations.SEQUENCE)
                    .forEach(x -> firstSectionEnd.add(x.getStartNode()));

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
            link.setProperty("witnesses", oldWitnesses.toArray(new String[0]));

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

            // TODO Look for any lacuna nodes in a row that can be merged

            // Move the second end node to the first section
            trueEnd.getSingleRelationship(ERelations.HAS_END, Direction.INCOMING).delete();
            firstSection.createRelationshipTo(trueEnd, ERelations.HAS_END);

            // Adjust the section ordering and delete the second section
            removeFromSequence(secondSection);
            secondSection.getSingleRelationship(ERelations.PART, Direction.INCOMING).delete();
            secondSection.delete();

            // Re-initialize the ranks starting from the final readings of the first section.
            for (Node n : firstSectionEnd)
                recalculateRank(n);

            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(e.getMessage()).build();
        }
        return Response.ok().build();
    }

    /**
     * Resets ranks within the given section
     *
     * This does not belong to the official API!
     * It is a secret hack to fix ranks if we find they are broken or missing.
     */
    @GET
    @Path("/initRanks")
    @Produces(MediaType.APPLICATION_JSON)
    @MireDotIgnore
    public Response initRanks() {
        if (!sectionInTradition())
            return Response.status(Response.Status.NOT_FOUND).entity("Tradition and/or section not found").build();
        try (Transaction tx = db.beginTx()) {
            ReadingService.recalculateRank(VariantGraphService.getStartNode(sectId, db), true);
            tx.success();
        } catch (Exception e) {
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(jsonresp("result", "success")).build();

    }


    /*
     * Analysis
     */

    /**
     * Returns a list of pairs of readings that could potentially be identical - that is, they
     * have the same text and same joining properties, and are co-located. This is used to
     * identify possible inconsistencies in the collation. The pairs are ordered so that the
     * reading with more witnesses is listed first.
     *
     * @summary List mergeable readings
     * @param startRank - where to start
     * @param endRank   - where to end
     * @param threshold - the number of ranks to look ahead/behind
     * @param limitText      - limit search to readings with the given text
     * @return a list of lists of readings that may be merged.
     * @statuscode 200 - on success
     * @statuscode 404 - if no such tradition or section exists
     * @statuscode 500 - on failure, with an error message
     */
    @GET
    @Path("/mergeablereadings/{startRank}/{endRank}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType("java.util.List<java.util.List<net.stemmaweb.model.ReadingModel>>")
    public Response getCouldBeIdenticalReadings(
            @PathParam("startRank") long startRank,
            @PathParam("endRank") long endRank,
            @DefaultValue("10") @QueryParam("threshold") long threshold,
            @DefaultValue("") @QueryParam("text") String limitText) {
        Node startNode = VariantGraphService.getStartNode(sectId, db);
        if (startNode == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(jsonerror("Tradition and/or section not found")).build();
        }

        List<List<ReadingModel>> couldBeIdenticalReadings;
        try (Transaction tx = db.beginTx()) {
            List<Node> questionedReadings = getReadingsBetweenRanks(
                    startRank, endRank, startNode, limitText);

            couldBeIdenticalReadings = getCouldBeIdenticalAsList(questionedReadings, threshold);
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(couldBeIdenticalReadings).build();
    }

    /**
     * Makes separate lists for every group of readings with identical text and
     * different ranks and send the list for further test
     *
     * @param questionedReadings -
     * @return list of lists of identical readings
     */
    private List<List<ReadingModel>> getCouldBeIdenticalAsList (
            List<Node> questionedReadings, long threshold) throws Exception {

        List<List<ReadingModel>> couldBeIdenticalReadings = new ArrayList<>();
        HashSet<Long> processed = new HashSet<>();

        for (Node nodeA : questionedReadings) {
            if (processed.contains(nodeA.getId()))
                continue;
            Long aRank = Long.valueOf(nodeA.getProperty("rank").toString());
            List<Node> sameText = questionedReadings.stream().filter(x -> !x.equals(nodeA)
                && x.getProperty("text").equals(nodeA.getProperty("text"))
                && Math.abs(Long.valueOf(x.getProperty("rank").toString()) - aRank) < threshold)
                    .collect(Collectors.toList());
            for (Node n : sameText) {
                if (processed.contains(n.getId()))
                    continue;
                if (!wouldGetCyclic(nodeA, n)) {
                    // Get the reading models
                    ReadingModel rma = new ReadingModel(nodeA);
                    ReadingModel rmn = new ReadingModel(n);
                    // Order them by descending number of witnesses
                    ArrayList<ReadingModel> pair = new ArrayList<>(Arrays.asList(rma, rmn));
                    pair.sort((a, b) -> b.getWitnesses().size() - a.getWitnesses().size());
                    couldBeIdenticalReadings.add(pair);
                }
            }
            processed.add(nodeA.getId());
        }
        return couldBeIdenticalReadings.stream()
                .sorted(Comparator.comparingLong(this::rankDifference))
                .collect(Collectors.toList());
    }

    // Return the difference in ranks between the given pair of readings.
    private long rankDifference(List<ReadingModel> pair) {
        return Math.abs(pair.get(1).getRank() - pair.get(0).getRank());
    }

    // Retrieve all readings of a tradition between two ranks as Nodes
    private List<Node> getReadingsBetweenRanks(long startRank, long endRank, Node startNode, String limitText) throws Exception {
        List<Node> readings;
        PathExpander e = new AlignmentTraverse(startNode);
        try (Transaction tx = db.beginTx()) {
            Stream<Node> readingStream = db.traversalDescription().depthFirst()
                    .expand(e).uniqueness(Uniqueness.NODE_GLOBAL)
                    .traverse(startNode).nodes().stream()
                    .filter(x -> startRank <= Long.valueOf(x.getProperty("rank").toString()) &&
                            endRank >= Long.valueOf(x.getProperty("rank").toString()));
            if (!limitText.equals(""))
                readingStream = readingStream.filter(x -> x.getProperty("text").toString().equals(limitText));
            readings = readingStream.collect(Collectors.toList());
            tx.success();
        }
        return readings;
    }


    /**
     * Get all readings which have the same text and the same rank, between the given ranks.
     * This is a constrained version of {@code mergeablereadings}.
     *
     * @summary Find identical readings
     * @param startRank the rank from where to start the search
     * @param endRank   the rank at which to end the search
     * @return a list of lists of identical readings
     * @statuscode 200 - on success
     * @statuscode 404 - if no such tradition or section exists
     */
    // TODO refactor all these traversals somewhere!
    @GET
    @Path("/identicalreadings/{startRank}/{endRank}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType("java.util.List<java.util.List<net.stemmaweb.model.ReadingModel>>")
    public Response getIdenticalReadings(@PathParam("startRank") long startRank,
                                         @PathParam("endRank") long endRank) {
        ArrayList<List<ReadingModel>> identicalReadings = collectIdenticalReadings(startRank, endRank);
        if (identicalReadings == null)
            return Response.status(Response.Status.NOT_FOUND).entity(jsonerror("no identical readings were found")).build();

        return Response.ok(identicalReadings).build();
    }

    // We want access within net.stemmaweb.parser as well
    public ArrayList<List<ReadingModel>> collectIdenticalReadings(long startRank, long endRank) {
        Node startNode = VariantGraphService.getStartNode(sectId, db);
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

    // Retrieve all readings of a tradition between two ranks as ReadingModels
    private ArrayList<ReadingModel> getAllReadingsFromSectionBetweenRanks(
            Node startNode, long startRank, long endRank) throws Exception {
        ArrayList<ReadingModel> readingModels = new ArrayList<>();
        getReadingsBetweenRanks(startRank, endRank, startNode, "")
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
     * Chain through the readings marked as lemmata and construct the LEMMA_TEXT link. Returns a
     * short
     *
     * @summary Set the lemma text
     * @return  JSON value with key 'result' (== 'success') or 'error'
     * @statuscode 200 - on success
     * @statuscode 404 - if no such tradition or section exists
     * @statuscode 409 - on detection of conflicting lemma readings
     * @statuscode 500 - on failure, with an error message
     */
    @POST
    @Path("/setlemma")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    // @ReturnType("java.lang.String")
    public Response setLemmaText() {
        if (!sectionInTradition())
            return Response.status(Response.Status.NOT_FOUND).entity(jsonerror("Tradition and/or section not found")).build();
        try (Transaction tx = db.beginTx()) {
            Node startNode = VariantGraphService.getStartNode(sectId, db);
            Node endNode = VariantGraphService.getEndNode(sectId, db);
            // Delete any existing lemma text links
            ResourceIterable<Relationship> lemmaLinks = db.traversalDescription().depthFirst()
                    .relationships(ERelations.LEMMA_TEXT, Direction.OUTGOING)
                    .evaluator(Evaluators.all())
                    .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL).traverse(startNode)
                    .relationships();
            lemmaLinks.forEach(Relationship::delete);
            // Go through the section readings collecting and ordering lemmata

            List<ReadingModel> sectionLemmata = sectionReadings().stream().filter(ReadingModel::getIs_lemma)
                    .sorted(ReadingModel::compareTo)
                    .collect(Collectors.toList());

            // Recreate links, checking for branching
            Node priorLemma = startNode;
            for (ReadingModel tl : sectionLemmata) {
                Node thisLemma = db.getNodeById(Long.valueOf(tl.getId()));
                ReadingModel pl = new ReadingModel(priorLemma);
                // Check that we don't have same-rank readings
                if (priorLemma.getProperty("rank").equals(thisLemma.getProperty("rank")))
                    return Response.status(Response.Status.CONFLICT)
                            .entity(jsonerror(String.format(
                                    "Cannot have two lemma readings (%s and %s) in the same place",
                                    pl.getNormal_form(), tl.getNormal_form())))
                            .build();
                // Create the new relationship
                priorLemma.createRelationshipTo(thisLemma, ERelations.LEMMA_TEXT);
                priorLemma = thisLemma;
            }
            // Connect the last lemma text to the end node
            priorLemma.createRelationshipTo(endNode, ERelations.LEMMA_TEXT);

            // Sanity check against branching
            priorLemma = startNode;
            while (!priorLemma.equals(endNode)) {
                // This will throw an exception if there is more than a single relationship
                Relationship r = priorLemma.getSingleRelationship(ERelations.LEMMA_TEXT, Direction.OUTGOING);
                if (r == null)
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity(jsonerror(String.format(
                                    "Lemma chain ends prematurely at %s / %s",
                                    priorLemma.getProperty("normal_form"),
                                    priorLemma.getProperty("rank"))))
                            .build();
                priorLemma = r.getEndNode();
            }
            tx.success();
        } catch (Exception e) {
            if (!e.getMessage().contains("More than one"))
                e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(jsonresp("result", "success")).build();
    }

    /**
     * Return a list of emendations on this section.
     *
     * @return a GraphModel containing the emendations that have been made on this section
     * @statuscode 200 - on success
     * @statuscode 404 - if specified section or specified tradition doesn't exist
     * @statuscode 500 - on error
     */
    @GET
    @Path("/emendations")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = GraphModel.class)
    public Response getEmendations() {
        if (!sectionInTradition())
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(jsonerror("Tradition and/or section not found")).build();
        GraphModel result = new GraphModel();
        try (Transaction tx = db.beginTx()) {
            // Crawl the section looking for all emendations
            Node sectionNode = db.getNodeById(Long.valueOf(sectId));
            List<Node> emended = new ArrayList<>();
            for (Relationship r : sectionNode.getRelationships(ERelations.HAS_EMENDATION, Direction.OUTGOING))
                emended.add(r.getEndNode());
            result.setReadings(emended.stream().map(ReadingModel::new).collect(Collectors.toList()));
            List<SequenceModel> emendSeqs = new ArrayList<>();
            for (Node n : emended) {
                for (Relationship r : n.getRelationships(ERelations.EMENDED)) {
                    emendSeqs.add(new SequenceModel(r));
                }
            }
            result.setSequences(emendSeqs);
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(result).build();
    }

    /**
     * Propose an emendation (that is, an edit not supported by any witness) to the text.
     * An emendation is a special type of reading, which requires an authority (i.e. the
     * identity of the proposer) to be named.
     *
     * @param proposal - A ProposedEmendationModel with the information
     * @return a GraphModel containing the new reading and its links to the rest of the text
     * @statuscode 200 - on success
     * @statuscode 400 - on bad request
     * @statuscode 404 - if the tradition and/or section doesn't exist
     * @statuscode 500 - on error
     */
    @POST
    @Path("/emend")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = GraphModel.class)
    public Response emendText(ProposedEmendationModel proposal) {
        if (!sectionInTradition())
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(jsonerror("Tradition and/or section not found")).build();
        GraphModel result = new GraphModel();
        try (Transaction tx = db.beginTx()) {
            // Find all the last readings at or prior to the fromRank. Omit any lacunae that
            // span the range - we don't emend a lacunose text.
            Set<Node> atOrPrior = sequencesCrossingRank(proposal.getFromRank(), false)
                    .stream().filter(x -> !(x.getStartNode().hasProperty("is_lacuna")
                                && (Long) x.getEndNode().getProperty("rank", 0) >= proposal.getToRank()))
                    .map(Relationship::getStartNode).collect(Collectors.toSet());
            // Find all the first readings at or after the toRank. Again omit the lacunae.
            Set<Node> atOrAfter = sequencesCrossingRank(proposal.getToRank(), false)
                    .stream().filter(x -> !(x.getStartNode().hasProperty("is_lacuna")
                                && (Long) x.getStartNode().getProperty("rank", 0) < proposal.getFromRank()))
                            .map(Relationship::getEndNode).collect(Collectors.toSet());
            // Make sure we actually have readings on either side
            if (atOrPrior.isEmpty() || atOrAfter.isEmpty())
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(jsonerror("Invalid rank range specified for emendation")).build();
            // Make the emendation node
            Node emendation = db.createNode(Nodes.READING, Nodes.EMENDATION);
            emendation.setProperty("text", proposal.getText());
            emendation.setProperty("authority", proposal.getAuthority());
            emendation.setProperty("rank", proposal.getFromRank());
            emendation.setProperty("section_id", Long.valueOf(sectId));
            ReadingModel emrm = new ReadingModel(emendation);
            result.setReadings(Collections.singletonList(emrm));
            // Connect it in the graph
            Node sectionNode = db.getNodeById(Long.valueOf(sectId));
            sectionNode.createRelationshipTo(emendation, ERelations.HAS_EMENDATION);
            List<SequenceModel> newLinks = new ArrayList<>();
            for (Node n : atOrPrior) newLinks.add(new SequenceModel(n.createRelationshipTo(emendation, ERelations.EMENDED)));
            for (Node n : atOrAfter) newLinks.add(new SequenceModel(emendation.createRelationshipTo(n, ERelations.EMENDED)));
            result.setSequences(newLinks);
            // If it is a zero-width emendation, re-rank the graph
            ReadingService.recalculateRank(emendation);
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(result).build();
    }


    /*
     * Export
     */

    /**
     * Returns a JSON GraphModel (readings, relations, sequences incl. lemma & emendation) for the section.
     *
     * @summary Download JSON description of graph nodes & edges
     * @return GraphModel of the section subgraph, excluding annotations
     * @statuscode 200 - on success
     * @statuscode 404 - if no such tradition or section exists
     * @statuscode 500 - on failure, with an error message
     */
    @GET
    @Path("/graph")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = GraphModel.class)
    public Response getGraphModel() {
        // TODO does this check make sense, or does the not-found happen already in Tradition.java?
        if (VariantGraphService.getTraditionNode(tradId, db) == null)
            return Response.status(Response.Status.NOT_FOUND).type(MediaType.TEXT_PLAIN_TYPE)
                    .entity("No such tradition found").build();

        GraphModel thisSection = new GraphModel();
        try (Transaction tx = db.beginTx()) {
            // Add the readings
            thisSection.addReadings(StreamSupport.stream(VariantGraphService.returnTraditionSection(sectId, db)
                    .nodes().spliterator(), false).filter(x -> x.hasLabel(Nodes.READING))
                    .map(ReadingModel::new).collect(Collectors.toSet()));
            // Add the relations
            thisSection.addRelations(StreamSupport.stream(VariantGraphService.returnTraditionSection(sectId, db)
                    .relationships().spliterator(), false).filter(x -> x.isType(ERelations.RELATED))
                    .map(RelationModel::new).collect(Collectors.toSet()));
            // Add the sequences
            thisSection.addSequences(StreamSupport.stream(VariantGraphService.returnTraditionSection(sectId, db)
                    .relationships().spliterator(), false)
                    .filter(x -> x.isType(ERelations.SEQUENCE) || x.isType(ERelations.LEMMA_TEXT) || x.isType(ERelations.EMENDED))
                    .map(SequenceModel::new).collect(Collectors.toSet()));

            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(thisSection).build();
    }

    // Export the dot / SVG for a particular section
    /**
     * Returns a GraphML file that describes the specified section and its data, including annotations.
     *
     * @summary Download GraphML XML description of section
     * @param includeWitnesses - Whether or not to include witness information in the XML
     * @return GraphML description of the section subgraph
     * @statuscode 200 - on success
     * @statuscode 404 - if no such tradition or section exists
     * @statuscode 500 - on failure, with an error message
     */
    @GET
    @Path("/graphml")
    @Produces(MediaType.APPLICATION_XML + "; charset=utf-8")
    @ReturnType("java.lang.Void")
    public Response getGraphML(@DefaultValue("false") @QueryParam("include_witnesses") Boolean includeWitnesses) {
        if (VariantGraphService.getTraditionNode(tradId, db) == null)
            return Response.status(Response.Status.NOT_FOUND).type(MediaType.TEXT_PLAIN_TYPE)
                    .entity("No such tradition found").build();

        GraphMLExporter exporter = new GraphMLExporter();
        return exporter.writeNeo4J(tradId, sectId, includeWitnesses);
    }

    // Export the dot / SVG for a particular section
    /**
     * Returns a GraphViz dot file that describes the specified section and its data.
     *
     * @summary Download GraphViz
     * @param includeRelatedRelationships - Include RELATED edges in the dot, if true
     * @param showNormalForms - Display normal form of readings alongside "raw" text form, if true
     * @param showRank - Display the rank of readings, if true
     * @param displayAllSigla - Avoid the 'majority' contraction of long witness labels, if true
     * @param normalise - A RelationType name to normalise on, if desired
     * @param excWitnesses - Exclude the given witness from the dot output. Can be specified multiple times
     * @return Plaintext dot format
     * @statuscode 200 - on success
     * @statuscode 404 - if no such tradition or section exists
     * @statuscode 500 - on failure, with an error message
     */
    @GET
    @Path("/dot")
    @Produces(MediaType.TEXT_PLAIN + "; charset=utf-8")
    @ReturnType(clazz = String.class)
    public Response getDot(@DefaultValue("false") @QueryParam("include_relations") Boolean includeRelatedRelationships,
                           @DefaultValue("false") @QueryParam("show_normal") Boolean showNormalForms,
                           @DefaultValue("false") @QueryParam("show_rank") Boolean showRank,
                           @DefaultValue("false") @QueryParam("expand_sigla") Boolean displayAllSigla,
                                                  @QueryParam("normalise") String normalise,
                                                  @QueryParam("exclude_witness") List<String> excWitnesses) {
        if (VariantGraphService.getTraditionNode(tradId, db) == null)
            return Response.status(Response.Status.NOT_FOUND).entity("No such tradition found").build();

        // Put our options into an object
        DisplayOptionModel dm = new DisplayOptionModel(
                includeRelatedRelationships, showNormalForms, showRank, displayAllSigla, normalise, excWitnesses);
        // Make the dot.
        DotExporter exporter = new DotExporter(db);
        return exporter.writeNeo4J(tradId, sectId, dm);
    }

    /**
     * Returns an alignment table for the section in JSON format.
     *
     * @summary Download JSON alignment
     *
     * @param toConflate   - Zero or more relationship types whose readings should be treated as identical
     * @return the JSON alignment
     */
    @GET
    @Path("/json")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = AlignmentModel.class)
    public Response getJson(@QueryParam("conflate") String toConflate) {
        List<String> thisSection = new ArrayList<>(Collections.singletonList(sectId));
        return new TabularExporter(db).exportAsJSON(tradId, toConflate, thisSection);
    }

    /**
     * Returns a CSV file that contains the aligned reading data for the tradition.
     *
     * @summary Download CSV alignment
     *
     * @param toConflate   - Zero or more relationship types whose readings should be treated as identical
     * @return the CSV alignment as plaintext
     */
    @GET
    @Path("/csv")
    @Produces(MediaType.TEXT_PLAIN + "; charset=utf-8")
    @ReturnType("java.lang.Void")
    public Response getCsv(@QueryParam("conflate") String toConflate) {
        List<String> thisSection = new ArrayList<>(Collections.singletonList(sectId));
        return new TabularExporter(db).exportAsCSV(tradId, ',', toConflate, thisSection);
    }

    /**
     * Returns a tab-separated values (TSV) file that contains the aligned reading data for the tradition.
     *
     * @summary Download TSV alignment
     *
     * @param toConflate   - Zero or more relationship types whose readings should be treated as identical
     * @return the TSV alignment as plaintext
     */
    @GET
    @Path("/tsv")
    @Produces(MediaType.TEXT_PLAIN + "; charset=utf-8")
    @ReturnType(clazz = String.class)
    public Response getTsv(@QueryParam("conflate") String toConflate) {
        List<String> thisSection = new ArrayList<>(Collections.singletonList(sectId));
        return new TabularExporter(db).exportAsCSV(tradId, '\t', toConflate, thisSection);
    }

    /**
     * Returns a character matrix suitable for use with e.g. Phylip Pars.
     *
     * @summary Download character matrix for parsimony analysis
     *
     * @param toConflate   - Zero or more relationship types whose readings should be treated as identical
     * @param maxVars      - Maximum number of variants per location, above which that location will be discarded.
     *                       Default is 8, for compatibility with Phylip Pars.
     * @return the character matrix as plaintext
     */
    @GET
    @Path("/matrix")
    @Produces(MediaType.TEXT_PLAIN + "; charset=utf-8")
    @ReturnType(clazz = String.class)
    public Response getCharMatrix(@QueryParam("conflate") String toConflate,
                                  @DefaultValue("8") @QueryParam("maxVars") int maxVars) {
        List<String> thisSection = new ArrayList<>(Collections.singletonList(sectId));
        return new TabularExporter(db).exportAsCharMatrix(tradId, maxVars, toConflate, thisSection);
    }

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

    private Boolean sectionInTradition() {
        return VariantGraphService.sectionInTradition(tradId, sectId, db);
    }

}
