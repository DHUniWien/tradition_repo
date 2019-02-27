package net.stemmaweb.rest;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.qmino.miredot.annotations.ReturnType;
import net.stemmaweb.model.*;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.ReadingService;
import net.stemmaweb.services.RelationService;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.NotFoundException;

import static net.stemmaweb.rest.Util.jsonerror;

/**
 * Comprises all Rest API calls related to a reading. Can be called via
 * http://BASE_URL/reading
 *
 * @author PSE FS 2015 Team2
 */

public class Reading {

    private String errorMessage; // global error message used for sub-method calls

    private GraphDatabaseService db;
    /**
     * The ID of the reading to query
     */
    private Long readId;

    public Reading(String requestedId) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        // The requested ID might have an 'n' prepended, if it was taken from the SVG output.
        readId = Long.valueOf(requestedId.replaceAll("n", ""));
    }

    /**
     * Returns the metadata for a single reading.
     *
     * @summary Get a reading
     * @return The reading information as a JSON structure.
     * @statuscode 200 - on success
     * @statuscode 500 - on error, with an error message
    */
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = ReadingModel.class)
    public Response getReading() {
        ReadingModel reading;
        try (Transaction tx = db.beginTx()) {
            reading = new ReadingModel(db.getNodeById(readId));
            tx.success();
        } catch (NotFoundException e) {
            return Response.noContent().build();
        } catch (Exception e) {
            errorMessage = e.getMessage();
            return errorResponse(Status.INTERNAL_SERVER_ERROR);
        }
        return Response.ok(reading).build();
    }

    /**
     * Changes the properties of an existing reading.
     * @summary Update an existing reading
     *
     * @param changeModels
     *            an array of named key/value property pairs. For example, a request to
     *            change the reading's language to German will look like this:
     *            {@code {"properties": [{"key":"language","newProperty":"German"}]}}
     * @return The metadata of the updated reading
     * @statuscode 200 - on success
     * @statuscode 400 - on an invalid property key, or an invalid property value type
     * @statuscode 500 - on error, with an error message
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = ReadingModel.class)
    public Response changeReadingProperties(ReadingChangePropertyModel changeModels) {
        ReadingModel modelToReturn = new ReadingModel();
        Node reading;
        String currentKey = "";
        try (Transaction tx = db.beginTx()) {
            reading = db.getNodeById(readId);
            for (KeyPropertyModel keyPropertyModel : changeModels.getProperties()) {
                currentKey = keyPropertyModel.getKey();
                if (currentKey.equals("id")) {
                    errorMessage = "Reading ID cannot be changed!";
                    return errorResponse(Status.INTERNAL_SERVER_ERROR);
                }
                // Check that this field actually exists in our model
                modelToReturn.getClass().getDeclaredField(currentKey);
                // Then set the property.
                reading.setProperty(currentKey, keyPropertyModel.getProperty());
            }
            modelToReturn = new ReadingModel(reading);
            tx.success();
        } catch (NoSuchFieldException f) {
            errorMessage = "Reading has no such property '" + f.getMessage() + "'";
            return errorResponse(Status.BAD_REQUEST);
        } catch (ClassCastException e) {
            errorMessage = "Property " + currentKey + " of the wrong type: " + e.getMessage();
            return errorResponse(Status.BAD_REQUEST);
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = e.getMessage();
            return errorResponse(Status.INTERNAL_SERVER_ERROR);
        }
        return Response.status(Response.Status.OK).entity(modelToReturn).build();
    }

    /**
     * Gets all readings related to the given reading.
     * @summary Get related readings
     *
     * @param filterTypes - a list of relation types to filter by
     * @return a list of readings related via the given relation types.
     * @statuscode 200 - on success
     * @statuscode 500 - on error, with an error message
     *
     */
    @GET
    @Path("related")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.ReadingModel>")
    public Response getRelatedReadings(@QueryParam("types") List<String> filterTypes) {
        ArrayList<ReadingModel> relatedReadings = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            Node reading = db.getNodeById(readId);
            for (Relationship r : reading.getRelationships(ERelations.RELATED)) {
                if (filterTypes.size() > 0) {
                    String relType = r.getProperty("type").toString();
                    if (!filterTypes.contains(relType))
                        continue;
                }
                relatedReadings.add(new ReadingModel(r.getOtherNode(reading)));
            }
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = e.getMessage();
            return errorResponse(Status.INTERNAL_SERVER_ERROR);
        }
        return Response.ok(relatedReadings).build();
    }

    /**
     * Deletes all relations associated with the given reading.
     * @summary Delete all reading relations
     *
     * @return a list of the relations that were deleted.
     * @statuscode 200 - on success
     * @statuscode 500 - on error, with an error message
     */
    @DELETE
    @Path("relations")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.RelationModel")
    public Response deleteAllRelations() {
        ArrayList<RelationModel> deleted = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            Node reading = db.getNodeById(readId);
            for (Relationship rel : reading.getRelationships(ERelations.RELATED)) {
                deleted.add(new RelationModel(rel));
                rel.delete();
            }
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = e.getMessage();
            return errorResponse(Status.INTERNAL_SERVER_ERROR);
        }
        return Response.ok(deleted).build();
    }


    /**
     * Gets the list of witnesses that carry the given reading.
     * @summary Get reading witnesses
     *
     * @return the metadata of the witnesses to this reading.
     * @statuscode 200 - on success
     * @statuscode 500 - on error, with an error message
     */
    @GET
    @Path("witnesses")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.WitnessModel>")
    public Response getReadingWitnesses() {
        try {
            return Response.ok(collectWitnesses(false)).build();
        } catch (Exception e) {
            return errorResponse(Status.INTERNAL_SERVER_ERROR);
        }
    }

    private HashSet<String> collectWitnesses(Boolean includeAllLayers) {
        HashSet<String> normalWitnesses = new HashSet<>();

        // Look at all incoming SEQUENCE relationships to the reading
        try (Transaction tx = db.beginTx()) {
            Node reading = db.getNodeById(readId);
            // First get the "normal" witnesses
            Iterable<Relationship> readingSeqs = reading.getRelationships(Direction.BOTH, ERelations.SEQUENCE);
            for (Relationship r : readingSeqs)
                if (r.hasProperty("witnesses"))
                    Collections.addAll(normalWitnesses, (String[]) r.getProperty("witnesses"));
            // Now look for the specials, and add them if they are not in the normal witnesses
            for (Relationship r : readingSeqs) {
                for (String prop : r.getPropertyKeys()) {
                    if (prop.equals("witnesses"))
                        continue;
                    String[] specialWits = (String[]) r.getProperty(prop);
                    for (String w : specialWits) {
                        if (normalWitnesses.contains(w) && !includeAllLayers)
                            continue;
                        normalWitnesses.add(w + " (" + prop + ")");
                    }
                }
            }
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = e.getMessage();
            throw(e);
        }
        return normalWitnesses;
    }

    /**
     * Duplicates a reading in a specific tradition; this should be used when a reading has
     * been mis-collated, or when the editor otherwise wishes to assert that seemingly
     * identical readings in different witnesses are distinct.
     *
     * This is the opposite of the {@code merge} call.
     *
     * @summary Duplicate a reading
     *
     * @param duplicateModel
     *            specifies the reading(s) to be duplicated, as well as the witnesses to which
     *            the duplicated new reading(s) should now belong.
     * @return a GraphModel in JSON containing all the created readings and the
     *         deleted relations.
     * @statuscode 200 - on success
     * @statuscode 500 - on error, with an error message
     */
    @POST
    @Path("duplicate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = GraphModel.class)
    public Response duplicateReading(DuplicateModel duplicateModel) {

        ArrayList<ReadingModel> createdReadings = new ArrayList<>();
        ArrayList<RelationModel> tempDeleted = new ArrayList<>();

        Node originalReading;

        try (Transaction tx = db.beginTx()) {
            List<Long> readings = duplicateModel.getReadings().stream().map(Long::valueOf).collect(Collectors.toList());
            for (Long readId : readings) {
                originalReading = db.getNodeById(readId);
                List<String> newWitnesses = duplicateModel.getWitnesses();

                if (!canBeDuplicated(originalReading, newWitnesses)) {
                    return errorResponse(Status.INTERNAL_SERVER_ERROR);
                }

                Node newNode = db.createNode();
                tempDeleted.addAll(duplicate(newWitnesses, originalReading, newNode));
                ReadingModel newModel = new ReadingModel(newNode);
                newModel.setOrig_reading(String.valueOf(readId));
                createdReadings.add(newModel);
            }

            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = e.getMessage();
            return errorResponse(Status.INTERNAL_SERVER_ERROR);
        }

        // Now outside our main transaction block, try to put back the deleted relations.
        ArrayList<RelationModel> deletedRelations = new ArrayList<>();
        Relation relationRest = new Relation(getTraditionId());
        for (RelationModel rm : tempDeleted) {
            Response result = relationRest.create(rm);
            if (Status.CREATED.getStatusCode() != result.getStatus())
                deletedRelations.add(rm);
        }
        GraphModel readingsAndRelations = new GraphModel(createdReadings, deletedRelations);
        return Response.ok(readingsAndRelations).build();
    }

    /**
     * Checks if the reading can be duplicated for the given witness list. Sets
     * the global error message if not.
     *
     * @param originalReading
     *            the reading to be duplicated
     * @param newWitnesses
     *            the witnesses the duplicated reading will belong to
     * @return true if specific reading can be duplicated, false else
     */
    private boolean canBeDuplicated(Node originalReading, List<String> newWitnesses) {
        // Make a new REST object for the given reading, to check its witnesses
        Reading tocheck = new Reading(String.valueOf(originalReading.getId()));
        HashSet<String> allWitnesses = tocheck.collectWitnesses(true);

        if (newWitnesses.isEmpty()) {
            errorMessage = "No witnesses have been assigned to the new reading";
            return false;
        }

        for (String newWitness : newWitnesses)
            if (!allWitnesses.contains(newWitness)) {
                errorMessage = "The reading does not contain the specified witness " + newWitness;
                return false;
            }

        if (allWitnesses.size() < 2) {
            errorMessage = "The reading cannot be split between fewer than two witnesses";
            return false;
        }

        return true;
    }


    /**
     * Performs all necessary steps in the database to duplicate the reading.
     * NOTE: to be used inside a transaction!
     *
     * @param newWitnesses
     *            : the new witnesses to be split from the original path
     * @param originalReading
     *            : the reading to be duplicated
     * @param addedReading
     *            : the newly duplicated reading
     * @return a list of the deleted relations
     */
    private ArrayList<RelationModel> duplicate(List<String> newWitnesses,
                                               Node originalReading, Node addedReading) {
        // copy reading properties to newly added reading
        ReadingService.copyReadingProperties(originalReading, addedReading);
        Reading rdgRest = new Reading(String.valueOf(originalReading.getId()));

        // add witnesses to the correct sequence links
        for (String wit : newWitnesses) {
            HashMap<String, String> witness = parseSigil(wit);
            Node prior = rdgRest.getNeighbourReadingInSequence(wit, Direction.INCOMING);
            Node next = rdgRest.getNeighbourReadingInSequence(wit, Direction.OUTGOING);
            try (Transaction tx = db.beginTx()) {
                ReadingService.addWitnessLink(prior, addedReading, witness.get("sigil"), witness.get("layer"));
                ReadingService.addWitnessLink(addedReading, next, witness.get("sigil"), witness.get("layer"));
                ReadingService.removeWitnessLink(prior, originalReading, witness.get("sigil"), witness.get("layer"));
                ReadingService.removeWitnessLink(originalReading, next, witness.get("sigil"), witness.get("layer"));
                tx.success();
            }
        }

        // replicated all colocated relations of the original reading;
        // delete all non-colocated relations that cross our rank
        ArrayList<RelationModel> tempDeleted = new ArrayList<>();
        String sectId = originalReading.getProperty("section_id").toString();
        String tradId = getTraditionId();
        Section sectionRest = new Section(tradId, sectId);
        Long ourRank = (Long) originalReading.getProperty("rank");
        for (RelationModel rm : sectionRest.sectionRelations()) {
            Relationship originalRel = db.getRelationshipById(Long.valueOf(rm.getId()));
            if (originalRel.hasProperty("colocation") && originalRel.getProperty("colocation").equals(true) &&
                    (rm.getSource().equals(String.valueOf(originalReading.getId())) ||
                     rm.getTarget().equals(String.valueOf(originalReading.getId())))) {
                Relationship newRel = addedReading.createRelationshipTo(
                        originalRel.getOtherNode(originalReading),
                        ERelations.RELATED);
                for (String key : originalRel.getPropertyKeys()) {
                    newRel.setProperty(key, originalRel.getProperty(key));
                }
            } else if (!(originalRel.hasProperty("colocation") &&
                    originalRel.getProperty("colocation").equals(true))){
                // Get the related readings
                ReadingModel relSource = new ReadingModel(db.getNodeById(Long.valueOf(rm.getSource())));
                ReadingModel relTarget = new ReadingModel(db.getNodeById(Long.valueOf(rm.getTarget())));
                if ((relSource.getRank() < ourRank && relTarget.getRank() > ourRank)
                    || (relSource.getRank() > ourRank && relTarget.getRank() < ourRank)) {
                    originalRel.delete();
                    tempDeleted.add(rm);
                }
            }
        }

        return tempDeleted;
    }

    /**
     * Merges two co-located readings into one single reading. This will primarily be used
     * when a collation has missed that a pair of readings is identical.
     *
     * This is the opposite of the {@code duplicate} call.
     *
     * @summary Merge readings
     *
     * @param secondReadId - the id of the second reading to be merged
     * @statuscode 200 - on success
     * @statuscode 409 - if merging the readings would invalidate the graph.
     *                   This usually means that they are not in the same variant location.
     * @statuscode 500 - on error, with an error message
     */
    @POST
    @Path("merge/{secondReadId}")
    @ReturnType("java.lang.Void")
    public Response mergeReadings(@PathParam("secondReadId") long secondReadId) {

        Node stayingReading;
        Node deletingReading;

        try (Transaction tx = db.beginTx()) {
            stayingReading = db.getNodeById(readId);
            deletingReading = db.getNodeById(secondReadId);

            if (!canBeMerged(stayingReading, deletingReading)) {
                return errorResponse(Status.CONFLICT);
            }
            // See if they are on the same rank; if not, we will have to re-rank the graph
            // from the node before the one removed.
            boolean samerank = stayingReading.getProperty("rank").equals(deletingReading.getProperty("rank"));
            Iterable<Relationship> priorRels = deletingReading.getRelationships(
                    Direction.INCOMING, ERelations.LEMMA_TEXT, ERelations.SEQUENCE);
            Node aPriorNode = null;
            if (priorRels.iterator().hasNext())
                aPriorNode = priorRels.iterator().next().getStartNode();
            if (aPriorNode == null) {
                errorMessage = "Node to be merged has no prior node!";
                return errorResponse(Status.INTERNAL_SERVER_ERROR);
            }

            // Do the deed
            merge(stayingReading, deletingReading, true);
            // Re-rank nodes if necessary
            if (!samerank) {
                ReadingService.recalculateRank(aPriorNode);
            }

            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = e.getMessage();
            return errorResponse(Status.INTERNAL_SERVER_ERROR);
        }
        return Response.ok().build();
    }

    /**
     * Checks if the two readings can be merged or not. Sets the global error
     * message if not.
     *
     * @param stayingReading
     *            the reading which stays in the database
     * @param deletingReading
     *            the reading which will be deleted from the database
     * @return true if readings can be merged, false if not
     */
    private boolean canBeMerged(Node stayingReading, Node deletingReading) {
        /*
         if (!doContainSameText(stayingReading, deletingReading)) {
         errorMessage = "Readings to be merged do not contain the same text";
         return false;
         }
         */
        if (hasNonColoRelations(stayingReading, deletingReading)) {
            errorMessage = "Readings to be merged cannot contain cross-location relations";
            return false;
        }
        // If the two readings are aligned, there is no need to test for cycles.
        boolean aligned = false;
        for (Relationship rel : stayingReading.getRelationships(ERelations.RELATED)) {
            if (rel.getEndNode().equals(deletingReading)) {
                aligned = true;
                break;
            }
        }
        if (!aligned) {
            if (ReadingService.wouldGetCyclic(stayingReading, deletingReading)) {
                errorMessage = "Readings to be merged would make the graph cyclic";
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if the two readings have a relation between them which implies non-colocation.
     *
     * @param stayingReading
     *            the reading which stays in the database
     * @param deletingReading
     *            the reading which will be deleted from the database
     * @return true if the readings have a non-colocation relation
     */
    private boolean hasNonColoRelations(Node stayingReading, Node deletingReading) {
        for (Relationship stayingRel : stayingReading.getRelationships(ERelations.RELATED)) {
            if (stayingRel.getOtherNode(stayingReading).equals(deletingReading)) {
                return !(stayingRel.hasProperty("colocation") && stayingRel.getProperty("colocation").equals(true));
            }
        }
        return false;
    }

    /**
     * Performs all necessary steps in the database to merge two readings into
     * one.
     *
     * @param stayingReading
     *            the reading which stays in the database
     * @param deletingReading
     *            the reading which will be deleted from the database
     * @param postponeDeletion
     *            if true, the deletion is postponed
     */
    public void merge(Node stayingReading, Node deletingReading, Boolean postponeDeletion) throws Exception {
        deleteRelationBetweenReadings(stayingReading, deletingReading);
        copyWitnesses(stayingReading, deletingReading, Direction.INCOMING);
        copyWitnesses(stayingReading, deletingReading, Direction.OUTGOING);
        addRelationsToStayingReading(stayingReading, deletingReading);
        if (!postponeDeletion) {
            deletingReading.delete();
        }
    }

    /**
     * Deletes the relationship between the two readings.
     *
     * @param stayingReading
     *            the reading which stays in the database
     * @param deletingReading
     *            the reading which will be deleted from the database
     */
    private void deleteRelationBetweenReadings(Node stayingReading, Node deletingReading) {
        for (Relationship firstRel : stayingReading.getRelationships(ERelations.RELATED)) {
            for (Relationship secondRel : deletingReading.getRelationships(ERelations.RELATED)) {
                if (firstRel.equals(secondRel)) {
                    firstRel.delete();
                }
            }
        }
    }

    /**
     * Copies the witnesses from the reading to be deleted to the staying
     * reading.
     *
     * @param stayingReading
     *            the reading which stays in the database
     * @param deletingReading
     *            the reading which will be deleted from the database
     */
    private void copyWitnesses(Node stayingReading, Node deletingReading,
            Direction direction) throws Exception {
        for (Relationship rel : deletingReading.getRelationships(ERelations.SEQUENCE, direction)) {
            Node targetNode = rel.getOtherNode(deletingReading);
            for (String witClass : rel.getPropertyKeys()) {
                for (String w : (String[]) rel.getProperty(witClass))
                    if (direction.equals(Direction.OUTGOING))
                        ReadingService.addWitnessLink(stayingReading, targetNode, w, witClass);
                    else
                        ReadingService.addWitnessLink(targetNode, stayingReading, w, witClass);
            }
            rel.delete();
        }
        // Consistency check, that no double witness paths were created.
        // witness -> class -> seen?
        HashMap<String, HashMap<String, Boolean>> seenWitness = new HashMap<>();
        for (Relationship rel : stayingReading.getRelationships(ERelations.SEQUENCE, direction)) {
            for (String witClass : rel.getPropertyKeys())
                for (String w : (String[]) rel.getProperty(witClass)) {
                    if (seenWitness.containsKey(w)) {
                        if (seenWitness.get(w).containsKey(witClass))
                            throw new Exception("Double witness specification in reading merge");
                        else
                            seenWitness.get(w).put(witClass, true);
                    } else {
                        HashMap<String, Boolean> classmap = new HashMap<>();
                        classmap.put(witClass, true);
                        seenWitness.put(w, classmap);
                    }
                }
        }
    }

    /**
     * Adds relations from deletedReading to staying reading.
     *
     * @param stayingReading
     *            the reading which stays in the database
     * @param deletingReading
     *            the reading which will be deleted from the database
     */
    private void addRelationsToStayingReading(Node stayingReading,
                                              Node deletingReading) {
        // copy relationships from deletingReading to stayingReading
        for (Relationship oldRel : deletingReading.getRelationships(
                ERelations.RELATED, Direction.OUTGOING)) {
            Relationship newRel = stayingReading.createRelationshipTo(
                    oldRel.getEndNode(), ERelations.RELATED);
            RelationService.copyRelationshipProperties(oldRel, newRel);
            oldRel.delete();
        }
        for (Relationship oldRel : deletingReading.getRelationships(
                ERelations.RELATED, Direction.INCOMING)) {
            Relationship newRel = oldRel.getStartNode().createRelationshipTo(
                    stayingReading, ERelations.RELATED);
            RelationService.copyRelationshipProperties(oldRel, newRel);
            oldRel.delete();
        }
    }

    /**
     * Splits up a single reading into smaller consecutive reading units. Note that this
     * operation should not change the text of any witness!
     *
     * This is the opposite of the {@code compress} call.
     *
     * @summary Split a reading
     *
     * @param splitIndex - the index of the first letter of the second word, indicating where
     *            the reading is to be split. For example, "unto" with index 2 produces "un"
     *            and "to". If the index is zero the reading is split on all occurrences
     *            of the separator.
     * @param model
     *            A set of criteria indicating how the reading is to be split.
     *            If 'separate' is false, then the 'join_next' and 'join_prior' attributes
     *            are set on the respective readings. If splitIndex is zero, then the
     *            'character' must occur somewhere in the reading string, and will be
     *            removed from the reading text.
     * @return a JSON description of all the readings that were created or modified, as well
     *         as the new sequence links necessary to construct the bath.
     * @statuscode 200 - on success
     * @statuscode 500 - on error, with a descriptive error message
     */
    @POST
    @Path("split/{splitIndex}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = GraphModel.class)
    public Response splitReading(@PathParam("splitIndex") int splitIndex,
                                 ReadingBoundaryModel model) {
        assert (model != null);
        GraphModel readingsAndRelations;
        Node originalReading;
        try (Transaction tx = db.beginTx()) {
            originalReading = db.getNodeById(readId);
            String originalText = originalReading.getProperty("text").toString();
            if (splitIndex >= originalText.length())
                errorMessage = "The index must be smaller than the text length";

            else if (model.getIsRegex()) {
                // Test that the regex matches on the original text
                Pattern p = Pattern.compile(model.getCharacter());
                Matcher m = p.matcher(originalText);
                if (!m.find())
                    errorMessage = "The given regular expression does not match the original text";
            }

            else if (!originalText.contains(model.getCharacter()))
                errorMessage = "no such separator exists";

            else if (splitIndex != 0 && !model.getCharacter().equals("")) {
                String textToRemove = originalText.substring(splitIndex,
                        splitIndex + model.getCharacter().length());
                if (!textToRemove.equals(model.getCharacter()))
                    errorMessage = "The separator does not appear in the index location in the text";
            }

            else if (originalReading.hasRelationship(ERelations.RELATED))
                errorMessage = "A reading to be split cannot be part of any relation";

            if (errorMessage != null)
                return errorResponse(Status.INTERNAL_SERVER_ERROR);

            readingsAndRelations = split(originalReading, splitIndex, model);
            // new Tradition(getTraditionId()).recalculateRank(originalReading.getId());
            ReadingService.recalculateRank(originalReading);

            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = e.getMessage();
            return errorResponse(Status.INTERNAL_SERVER_ERROR);
        }
        return Response.ok(readingsAndRelations).build();
    }

    /**
     * Splits up the text of the reading into the words.
     *
     * @param splitIndex
     *            the index of the first letter of the second word
     * @param separator
     *            the string between the words
     * @param originalText
     *            the text to be split up
     * @return an array containing the separated words
     */
    private String[] splitUpText(int splitIndex, String separator, String originalText) {
        String[] splitWords;
        if (splitIndex > 0) {
            splitWords = new String[2];
            splitWords[0] = originalText.substring(0, splitIndex);
            splitWords[1] = originalText.substring(splitIndex);
            // remove separator from second word if there was one specified
             if (separator != null && !separator.equals("")) {
                splitWords[1] = splitWords[1].substring(separator.length());
            }
        } else {
            if (separator.equals("")) {
                separator = "\\s+";
            }
            splitWords = originalText.split(separator);
        }
        return splitWords;
    }

    /**
     * Performs all necessary steps in the database to split the reading.
     *
     * @param originalReading
     *            the reading to be split
     * @param splitIndex
     *            the words the reading consists of
     * @param model
     *            the ReadingBoundaryModel saying how the reading should be split
     * @return a model of the split graph
     */
    private GraphModel split(Node originalReading, int splitIndex, ReadingBoundaryModel model) {
        ArrayList<ReadingModel> createdOrChangedReadings = new ArrayList<>();
        ArrayList<RelationModel> createdRelations = new ArrayList<>();

        // Get the witness sequences that come out of the original reading, as well as
        // the list of witnesses
        ArrayList<Relationship> originalOutgoingRels = new ArrayList<>();
        ArrayList<String> allWitnesses = new ArrayList<>();
        for (Relationship oldRel : originalReading
                .getRelationships(ERelations.SEQUENCE, Direction.OUTGOING) ) {
            originalOutgoingRels.add(oldRel);
            String[] witnesses = (String[]) oldRel.getProperty("witnesses");
            Collections.addAll(allWitnesses, witnesses);
        }

        // Get the sequence of reading text that should be created
        String[] splitWords = splitUpText(splitIndex, model.getCharacter(),
                originalReading.getProperty("text").toString());

        // Change the first reading
        originalReading.setProperty("text", splitWords[0]);
        createdOrChangedReadings.add(new ReadingModel(originalReading));

        // Add the new readings
        Node lastReading = originalReading;

        for (int i = 1; i < splitWords.length; i++) {
            Node newReading = db.createNode();

            ReadingService.copyReadingProperties(lastReading, newReading);
            newReading.setProperty("text", splitWords[i]);
            // Long previousRank = (Long) lastReading.getProperty("rank");
            // newReading.setProperty("rank", previousRank + 1);
            if (!model.getSeparate())
                newReading.setProperty("join_prior", true);

            Relationship relationship = lastReading.createRelationshipTo(newReading, ERelations.SEQUENCE);
            Collections.sort(allWitnesses);
            relationship.setProperty("witnesses", allWitnesses.toArray(new String[0]));
            // TODO wtf, a sequence relationship into a RelationModel?
            createdRelations.add(new RelationModel(relationship));

            lastReading = newReading;
            createdOrChangedReadings.add(new ReadingModel(newReading));
        }
        for (Relationship oldRel : originalOutgoingRels) {
            Relationship newRel = lastReading.createRelationshipTo(oldRel.getEndNode(), oldRel.getType());
            RelationService.copyRelationshipProperties(oldRel, newRel);
            createdRelations.add(new RelationModel(newRel));
            oldRel.delete();
        }

        return new GraphModel(createdOrChangedReadings, createdRelations);
    }

    /**
     * Gets the reading that follows the requested reading in the given witness.
     *
     * @summary Next reading
     *
     * @param witnessId - the id (sigil) of the witness
     *
     * @return the following reading
     * @statuscode 200 - on success
     * @statuscode 404 - if there is no subsequent reading
     * @statuscode 500 - on error, with an error message
     */
    @GET
    @Path("next/{witnessId}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = ReadingModel.class)
    public Response getNextReadingInWitness(@PathParam("witnessId") String witnessId) {
        Node foundNeighbour = getNeighbourReadingInSequence(witnessId, Direction.OUTGOING);
        if (foundNeighbour != null)
            return Response.ok(new ReadingModel(foundNeighbour)).build();
        Status errorStatus = errorMessage.contains("this was the last")
                ? Status.NOT_FOUND : Status.INTERNAL_SERVER_ERROR;
        return errorResponse(errorStatus);
    }

    /**
     * Gets the reading that precedes the requested reading in the given witness.
     *
     * @summary Prior reading
     *
     * @param witnessId - the id (sigil) of the witness
     *
     * @return the prior reading
     * @statuscode 200 - on success
     * @statuscode 404 - if there is no prior reading
     * @statuscode 500 - on error, with an error message
     */
    @GET
    @Path("prior/{witnessId}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = ReadingModel.class)
    public Response getPreviousReadingInWitness(@PathParam("witnessId") String witnessId) {
        Node foundNeighbour = getNeighbourReadingInSequence(witnessId, Direction.INCOMING);
        if (foundNeighbour != null)
            return Response.ok(new ReadingModel(foundNeighbour)).build();
        Status errorStatus = errorMessage.contains("this was the first")
                ? Status.NOT_FOUND : Status.INTERNAL_SERVER_ERROR;
        return errorResponse(errorStatus);
    }

    // Gets the neighbour reading in the given direction for the given witness. Returns
    // the relevant ReadingModel, or sets errorMessage and returns null.
    private Node getNeighbourReadingInSequence(String witnessId, Direction dir) {
        Node neighbour = null;
        try (Transaction tx = db.beginTx()) {
            Node read = db.getNodeById(readId);
            String dirdisplay = dir.equals(Direction.INCOMING) ? "prior" : "next";
            Iterable<Relationship> incoming = read.getRelationships(ERelations.SEQUENCE, dir);
            Collection<Relationship> matching = StreamSupport.stream(incoming.spliterator(), false)
                    .filter(x -> isPathFor(x, witnessId))
                    .collect(Collectors.toList());
            if (matching.size() != 1) {
                errorMessage = matching.isEmpty()
                        ? "There is no " + dirdisplay + " reading!"
                        : "There is more than one " + dirdisplay + " reading!";
            } else {
                neighbour = matching.iterator().next().getOtherNode(read);
                ReadingModel result = new ReadingModel(neighbour);
                if (result.getIs_start() && dir == Direction.INCOMING) {
                    errorMessage = "this was the first reading for this witness";
                    neighbour = null;
                } else if (result.getIs_end() && dir == Direction.OUTGOING) {
                    errorMessage = "this was the last reading for this witness";
                    neighbour = null;
                }
            }
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = e.getMessage();
        }
        return neighbour;
    }

    // Assumes that we are already in a transaction!
    private Boolean isPathFor(Relationship sequence, String sigil) {
        HashMap<String, String> parsed = parseSigil(sigil);
        if (sequence.hasProperty(parsed.get("layer"))) {
            String[] wits = (String []) sequence.getProperty(parsed.get("layer"));
            for (String wit : wits) {
                if (wit.equals(parsed.get("sigil")))
                    return true;
            }
        }
        return false;
    }

    // Small utility function for parsing witness sigla
    private static HashMap<String, String> parseSigil (String sigil) {
        HashMap<String, String> result = new HashMap<>();
        String layer = "witnesses";
        String layerpattern = "^(.*)\\s+\\((.*)\\)";
        if (sigil.matches(layerpattern)) {
            layer = sigil.replaceAll(layerpattern, "$2");
            sigil = sigil.replaceAll(layerpattern, "$1");
        }
        result.put("layer", layer);
        result.put("sigil", sigil);
        return result;
    }
    /**
     * Collapse two consecutive readings into one. Texts will be concatenated together
     * (with or without a space or extra text). This call may only be used on consecutive
     * readings with no divergent witness paths between them, and no relations marked
     * on either individual reading. The reading with the lower rank (i.e., that which
     * comes first in the text) must be given first in the URL.
     *
     * This is the opposite of the {@code split} call.
     *
     * @summary Concatenate readings
     *
     * @param readId2 - the id of the second reading
     * @param boundary
     *            The specification of whether the reading text will be separated with a string,
     *            and if so, what string it will be. If the readings have {@code join_next} or
     *            {@code join_prior} set, this will be respected in preference to the boundary specification.
     *
     * @statuscode 200 - on success
     * @statuscode 409 - if the readings cannot legally be concatenated
     * @statuscode 500 - on error, with an error message
     */
    @POST
    @Path("concatenate/{read2Id}")
    @Consumes(MediaType.APPLICATION_JSON)
    // @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType("java.lang.Void")
    public Response compressReadings(@PathParam("read2Id") long readId2, ReadingBoundaryModel boundary) {

        Node read1, read2;
        errorMessage = "problem with a reading. could not compress";

        try (Transaction tx = db.beginTx()) {
            read1 = db.getNodeById(readId);
            read2 = db.getNodeById(readId2);
            if ((long) read1.getProperty("rank") > (long) read2.getProperty("rank")) {
                tx.success();
                errorMessage = "the first reading has a higher rank then the second reading";
                return errorResponse(Status.CONFLICT);
            }
            if (canBeCompressed(read1, read2)) {
                compress(read1, read2, boundary);
                ReadingService.recalculateRank(read1);
                tx.success();
                return Response.ok().build();
            }
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = e.getMessage();
            return errorResponse(Status.INTERNAL_SERVER_ERROR);
        }
        return errorResponse(Status.CONFLICT);
    }

    /**
     * compress two readings
     *
     * @param read1
     *            the first reading
     * @param read2
     *            the second reading
     * @param boundary
     *            the BoundaryModel that determines, in compbination with reading flags
     *            join_next and join_prior, how the reading text will be constructed.
     */
    private void compress(Node read1, Node read2, ReadingBoundaryModel boundary) {
        String newText;
        boolean joined = (read1.hasProperty("join_next") && (Boolean) read1.getProperty("join_next")) ||
                (read2.hasProperty("join_prior") && (Boolean) read2.getProperty("join_prior"));
        // We need to join the text, the display form, and the normal form
        String[] text_properties = {"text", "display", "normal_form"};
        String plaintextform = null;
        for (String prop : text_properties) {
            if (boundary.getSeparate() && !joined) {
                newText = String.join(boundary.getCharacter(),
                        read1.getProperty(prop, read1.getProperty("text", "")).toString(),
                        read2.getProperty(prop, read2.getProperty("text", "")).toString());
            } else {
                newText = String.join("",
                        read1.getProperty(prop, read1.getProperty("text", "")).toString(),
                        read2.getProperty(prop, read1.getProperty("text", "")).toString());
            }
            if (prop.equals("text"))
                plaintextform = newText;
            if (!newText.equals("") && (!newText.equals(plaintextform) || read1.hasProperty(prop)))
                read1.setProperty(prop, newText);
        }

        for (Relationship r : getSequenceBetweenReadings(read1, read2) ) {
            r.delete();
        }
        copyRelationships(read1, read2);
        read2.delete();
    }

    /**
     * copy all SEQUENCE relationship from one node to another IMPORTANT: when
     * called needs to be inside a try-catch
     *
     * @param read1
     *            the node which receives the relationships
     * @param read2
     *            the node from which relationships are copied
     */
    private void copyRelationships(Node read1, Node read2) {
        for (Relationship tempRel : read2.getRelationships(Direction.OUTGOING, ERelations.SEQUENCE, ERelations.LEMMA_TEXT)) {
            Node tempNode = tempRel.getOtherNode(read2);
            Relationship rel1 = read1.createRelationshipTo(tempNode, tempRel.getType());
            for (String key : tempRel.getPropertyKeys()) {
                rel1.setProperty(key, tempRel.getProperty(key));
            }
            tempRel.delete();
        }
    }

    /**
     * checks if two readings could be compressed. Sets the global error message
     * if not.
     *
     * @param read1
     *            the first reading
     * @param read2
     *            the second reading
     * @return true if ok to compress, false otherwise
     */
    private boolean canBeCompressed(Node read1, Node read2) {
        // The readings need to be contiguous...
        List<Relationship> from1to2 = getSequenceBetweenReadings(read1, read2);
        if (from1to2.isEmpty()) {
            errorMessage = "reading are not contiguous. could not compress";
            return false;
        }
        // ...they need to not have any non-sequence relationships...
        if (hasNonSequenceRelationships(read1) || hasNonSequenceRelationships(read2)) {
            errorMessage = "reading has other relations. could not compress";
            return false;
        }
        // ...and they need to have an effective degree of 1 (that is, the graph needs to
        // be non-forking at this point, though there might be both a SEQUENCE and a
        // LEMMA_TEXT relationship.)
        if (1 != getEffectiveDegree(read2, Direction.INCOMING) || 1 != getEffectiveDegree(read1, Direction.OUTGOING)) {
            errorMessage = "graph forks between these readings. could not compress";
            return false;
        }
        return true;
    }

    private int getEffectiveDegree(Node reading, Direction direction) {
        HashSet<Node> connected = new HashSet<>();
        for (Relationship rel : reading.getRelationships(direction, ERelations.SEQUENCE, ERelations.LEMMA_TEXT)) {
            connected.add(rel.getOtherNode(reading));
        }
        return connected.size();
    }

    /**
     * checks if a reading has relationships which are not SEQUENCE, e.g. RELATED or
     * other user-defined relationships
     *
     * @param read
     *            the reading
     * @return true if it has, false otherwise
     */
    private boolean hasNonSequenceRelationships(Node read) {
        for (Relationship rel : read.getRelationships()) {
            String type = rel.getType().name();

            if (!type.equals(ERelations.SEQUENCE.toString()) && !type.equals(ERelations.LEMMA_TEXT.toString())) {
                return true;
            }
        }
        return false;
    }

    // Class-level utility function to encapsulate the instance-wide error message
    private Response errorResponse (Status status) {
        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE).entity(jsonerror(errorMessage)).build();
    }

    /**
     * get the sequence/lemma relationship(s) between two readings
     *
     * @param read1
     *            the first reading
     * @param read2
     *            the second reading
     * @return the SEQUENCE relationship
     */
    private List<Relationship> getSequenceBetweenReadings(Node read1, Node read2) {
        ArrayList<Relationship> foundRels = new ArrayList<>();
        for (Relationship tempRel : read1.getRelationships(ERelations.SEQUENCE, ERelations.LEMMA_TEXT)) {
            if (tempRel.getOtherNode(read1).equals(read2)) {
                foundRels.add(tempRel);
            }
        }
        return foundRels;
    }

    private String getTraditionId () {
        String tradId;
        try (Transaction tx = db.beginTx()) {
            Node rdg = db.getNodeById(readId);
            tradId = db.getNodeById(Long.valueOf(rdg.getProperty("section_id").toString()))
                    .getSingleRelationship(ERelations.PART, Direction.INCOMING)
                    .getStartNode().getProperty("id").toString();
            tx.success();
        }
        return tradId;
    }
}
