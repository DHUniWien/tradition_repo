package net.stemmaweb.rest;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.model.*;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.ReadingService;
import net.stemmaweb.services.RelationshipService;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.NotFoundException;

/**
 * Comprises all Rest API calls related to a reading. Can be called via
 * http://BASE_URL/reading
 * 
 * @author PSE FS 2015 Team2
 */

public class Reading {

    private String errorMessage; // global error message used for sub-method calls

    private GraphDatabaseService db;
    private Long readId;

    public Reading(String requestedId) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        readId = Long.valueOf(requestedId);
    }

    /**
     * Returns a single reading by global neo4j id
     * @return the reading fetched by the id
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response getReading() {
        ReadingModel reading;
        try (Transaction tx = db.beginTx()) {
            reading = new ReadingModel(db.getNodeById(readId));
            tx.success();
        } catch (NotFoundException e) {
            return Response.status(Status.NO_CONTENT).build();
        } catch (Exception e) {
            errorMessage = e.getMessage();
            return errorResponse(Status.INTERNAL_SERVER_ERROR);
        }
        return Response.ok(reading).build();
    }

    /**
     * Changes properties of a reading according to its keys
     *
     * @param changeModels
     *            an array of ReadingChangePropertyModel objects. Will be converted from
     *            a json string. Example: a json string for an array size 1
     *            which should change the value of 'language' to 'german' will
     *            look like
     *            this:[{\"key\":\"language\",\"newProperty\":\"german\"}]
     * @return ok response with a model of the modified reading in json format
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
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
     *
     * @param filterTypes - a list of relationship types to filter by
     * @return a list of ReadingModels in JSON containing all related readings
     *
     */
    @GET
    @Path("related")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
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
     * Gets all readings related to the given reading.
     *
     * @return a list of ReadingModels in JSON containing all related readings
     *
     */
    @DELETE
    @Path("relations")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response deleteAllRelations() {
        ArrayList<RelationshipModel> deleted = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            Node reading = db.getNodeById(readId);
            for (Relationship rel : reading.getRelationships(ERelations.RELATED)) {
                deleted.add(new RelationshipModel(rel));
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
     *
     * @return a list of WitnessModels that are associated with the reading
     *
     */
    @GET
    @Path("witnesses")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response getReadingWitnesses() {
        HashSet<String> normalWitnesses = new HashSet<>();

        // Look at all incoming SEQUENCE relationships to the reading
        try (Transaction tx = db.beginTx()) {
            Node reading = db.getNodeById(readId);
            // First get the "normal" witnesses
            Iterable<Relationship> readingIncoming = reading.getRelationships(Direction.INCOMING, ERelations.SEQUENCE);
            for (Relationship r : readingIncoming)
                if (r.hasProperty("witnesses"))
                    Collections.addAll(normalWitnesses, (String[]) r.getProperty("witnesses"));
            // Now look for the specials, and add them if they are not in the normal witnesses
            for (Relationship r : readingIncoming) {
                for (String prop : r.getPropertyKeys()) {
                    if (prop.equals("witnesses"))
                        continue;
                    String[] specialWits = (String[]) r.getProperty(prop);
                    for (String w : specialWits) {
                        if (normalWitnesses.contains(w))
                            continue;
                        normalWitnesses.add(w + " (" + prop + ")");
                    }
                }
            }
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = e.getMessage();
            return errorResponse(Status.INTERNAL_SERVER_ERROR);
        }

        return Response.ok(normalWitnesses).build();
    }
        /**
         * Duplicates a reading in a specific tradition. Opposite of merge
         *
         * @param duplicateModel
         *            a model in JSON containing the readings to be duplicated and
         *            the witnesses of the old readings which the duplicated new
         *            readings should now belong to
         * @return a GraphModel in JSON containing all the created readings and the
         *         deleted relationships on success or Status.INTERNAL_SERVER_ERROR
         *         with a detailed message else
         */
    @POST
    @Path("duplicate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response duplicateReading(DuplicateModel duplicateModel) {

        ArrayList<ReadingModel> createdReadings = new ArrayList<>();
        ArrayList<RelationshipModel> tempDeleted = new ArrayList<>();

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

        // Now outside our main transaction block, try to put back the deleted relationships.
        ArrayList<RelationshipModel> deletedRelationships = new ArrayList<>();
        Relation relationRest = new Relation(getTraditionId());
        for (RelationshipModel rm : tempDeleted) {
            Response result = relationRest.create(rm);
            if (Status.CREATED.getStatusCode() != result.getStatus())
                deletedRelationships.add(rm);
        }
        GraphModel readingsAndRelationships = new GraphModel(createdReadings, deletedRelationships);
        return Response.ok(readingsAndRelationships).build();
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
        List<String> allWitnesses = allWitnessesOfReading(originalReading);

        if (newWitnesses.isEmpty()) {
            errorMessage = "The witness list has to contain at least one witness";
            return false;
        }

        for (String newWitness : newWitnesses)
            if (!allWitnesses.contains(newWitness)) {
                errorMessage = "The reading has to be in the witnesses to be duplicated";
                return false;
            }

        if (allWitnesses.size() < 2) {
            errorMessage = "The reading has to be in at least two witnesses";
            return false;
        }

        return true;
    }

    /**
     * Gets all witnesses of a reading in all its normal relationships.
     *
     * @param originalReading
     *            the reading to be duplicated
     * @return the list of witnesses of a reading
     */
    private List<String> allWitnessesOfReading(Node originalReading) {
        List<String> allWitnesses = new LinkedList<>();
        String[] currentWitnesses;
        for (Relationship relationship : originalReading
                .getRelationships(ERelations.SEQUENCE)) {
            currentWitnesses = (String[]) relationship.getProperty("witnesses");
            for (String currentWitness : currentWitnesses) {
                if (!allWitnesses.contains(currentWitness)) {
                    allWitnesses.add(currentWitness);
                }
            }
        }
        return allWitnesses;
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
     * @return a list of the deleted relationships
     */
    private ArrayList<RelationshipModel> duplicate(List<String> newWitnesses,
            Node originalReading, Node addedReading) {
        // copy reading properties to newly added reading
        addedReading = ReadingService.copyReadingProperties(originalReading, addedReading);

        // add witnesses to the correct sequence links
        // Incoming
        for (Relationship originalRelationship : originalReading
                .getRelationships(ERelations.SEQUENCE, Direction.INCOMING))
            transferNewWitnessesFromOriginalReadingToAddedReading(
                            newWitnesses, originalRelationship,
                            originalRelationship.getStartNode(), addedReading);
        // Outgoing
        for (Relationship originalRelationship : originalReading
                .getRelationships(ERelations.SEQUENCE, Direction.OUTGOING)) {
            transferNewWitnessesFromOriginalReadingToAddedReading(
                            newWitnesses, originalRelationship, addedReading,
                            originalRelationship.getEndNode());
        }

        // replicated all colocated relationships of the original reading;
        // delete all non-colocated relationships that cross our rank
        ArrayList<RelationshipModel> tempDeleted = new ArrayList<>();
        String sectId = originalReading.getProperty("section_id").toString();
        String tradId = getTraditionId();
        Section sectionRest = new Section(tradId, sectId);
        Long ourRank = (Long) originalReading.getProperty("rank");
        for (RelationshipModel rm : sectionRest.sectionRelationships()) {
            Relationship originalRel = db.getRelationshipById(Long.valueOf(rm.getId()));
            if (rm.implies_colocation() &&
                    (rm.getSource().equals(String.valueOf(originalReading.getId())) ||
                     rm.getTarget().equals(String.valueOf(originalReading.getId())))) {
                Relationship newRel = addedReading.createRelationshipTo(
                        originalRel.getOtherNode(originalReading),
                        ERelations.RELATED);
                for (String key : originalRel.getPropertyKeys()) {
                    newRel.setProperty(key, originalRel.getProperty(key));
                }
            } else if (!rm.implies_colocation()){
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
/*
        // see if any of the deleted non-colocations can be safely put back
        Relation relationRest = new Relation(tradId);
        ArrayList<RelationshipModel> removedRelationships = new ArrayList<>();
        for (RelationshipModel rm : tempDeleted) {
            Response result = relationRest.create(rm);
            if (Status.CREATED.getStatusCode() != result.getStatus())
                removedRelationships.add(rm);
        }
*/

        //return removedRelationships;
        return tempDeleted;
    }

    /**
     * Transfers all the new witnesses from the relationships of the original
     * reading to the relationships of the newly added reading.
     *
     * @param newWitnesses
     *            the witnesses the duplicated reading will belong to
     * @param originalRel -
     * @param originNode -
     * @param targetNode -
     */
    private void transferNewWitnessesFromOriginalReadingToAddedReading(
            List<String> newWitnesses, Relationship originalRel,
            Node originNode, Node targetNode) {
        String[] oldWitnesses = (String[]) originalRel.getProperty("witnesses");
        // if oldWitnesses only contains one witness and this one should be
        // duplicated, create new relationship for addedReading and delete
        // the one from the originalReading
        if (oldWitnesses.length == 1) {
            if (newWitnesses.contains(oldWitnesses[0])) {
                Relationship newRel = originNode.createRelationshipTo(targetNode, ERelations.SEQUENCE);
                newRel.setProperty("witnesses", oldWitnesses);
                originalRel.delete();
            }
            // if oldWitnesses contains more than one witnesses, create new
            // relationship and add those witnesses which should be duplicated
        } else {
            // add only those old witnesses to stayingWitnessess which are
            // not in newWitnesses
            ArrayList<String> remainingWitnesses = new ArrayList<>();
            ArrayList<String> stayingWitnesses = new ArrayList<>();
            for (String oldWitness : oldWitnesses) {
                if (newWitnesses.contains(oldWitness)) {
                    remainingWitnesses.add(oldWitness);
                } else {
                    stayingWitnesses.add(oldWitness);
                }
            }

            // create new relationship for remaining witnesses if there are any
            if (!remainingWitnesses.isEmpty()) {
                Relationship addedRelationship = originNode
                        .createRelationshipTo(targetNode, ERelations.SEQUENCE);
                Collections.sort(remainingWitnesses);
                addedRelationship.setProperty("witnesses", remainingWitnesses
                        .toArray(new String[remainingWitnesses.size()]));

                if (stayingWitnesses.isEmpty()) {
                    originalRel.delete();
                } else {
                    Collections.sort(stayingWitnesses);
                    originalRel.setProperty("witnesses", stayingWitnesses
                            .toArray(new String[stayingWitnesses.size()]));
                }
            }
        }
    }

    /**
     * Merges two readings into one single reading in a specific tradition.
     * Opposite of duplicate
     *
     * @param secondReadId
     *            the id of the second reading to be merged
     * @return Status.ok if the merge was successful.
     *         Status.INTERNAL_SERVER_ERROR with a detailed message if not
     */
    @POST
    @Path("merge/{secondReadId}")
    // @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
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
            Boolean samerank = stayingReading.getProperty("rank").equals(deletingReading.getProperty("rank"));
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
            merge(stayingReading, deletingReading);
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
        if (containClassTwoRelationships(stayingReading, deletingReading)) {
            errorMessage = "Readings to be merged cannot contain cross-location relationships";
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
     * Checks if the two readings have a relationship between them which is of
     * class two (transposition / repetition).
     *
     * @param stayingReading
     *            the reading which stays in the database
     * @param deletingReading
     *            the reading which will be deleted from the database
     * @return true if a relationship between two readings is of class 2
     */
    private boolean containClassTwoRelationships(Node stayingReading, Node deletingReading) {
        for (Relationship stayingRel : stayingReading.getRelationships(ERelations.RELATED)) {
            if (stayingRel.getOtherNode(stayingReading).equals(deletingReading)
                    && (stayingRel.getProperty("type").equals("transposition")
                    || stayingRel.getProperty("type").equals("repetition"))) {
                return true;
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
     */
    private void merge(Node stayingReading, Node deletingReading) throws Exception {
        deleteRelationshipBetweenReadings(stayingReading, deletingReading);
        copyWitnesses(stayingReading, deletingReading, Direction.INCOMING);
        copyWitnesses(stayingReading, deletingReading, Direction.OUTGOING);
        addRelationshipsToStayingReading(stayingReading, deletingReading);
        deletingReading.delete();
    }

    /**
     * Deletes the relationship between the two readings.
     *
     * @param stayingReading
     *            the reading which stays in the database
     * @param deletingReading
     *            the reading which will be deleted from the database
     */
    private void deleteRelationshipBetweenReadings(Node stayingReading, Node deletingReading) {
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
     * Adds relationships from deletedReading to staying reading.
     *
     * @param stayingReading
     *            the reading which stays in the database
     * @param deletingReading
     *            the reading which will be deleted from the database
     */
    private void addRelationshipsToStayingReading(Node stayingReading,
            Node deletingReading) {
        // copy relationships from deletingReading to stayingReading
        for (Relationship oldRel : deletingReading.getRelationships(
                ERelations.RELATED, Direction.OUTGOING)) {
            Relationship newRel = stayingReading.createRelationshipTo(
                    oldRel.getEndNode(), ERelations.RELATED);
            RelationshipService.copyRelationshipProperties(oldRel, newRel);
            oldRel.delete();
        }
        for (Relationship oldRel : deletingReading.getRelationships(
                ERelations.RELATED, Direction.INCOMING)) {
            Relationship newRel = oldRel.getStartNode().createRelationshipTo(
                    stayingReading, ERelations.RELATED);
            RelationshipService.copyRelationshipProperties(oldRel, newRel);
            oldRel.delete();
        }
    }

    /**
     * Splits up a single reading into several ones in a specific tradition.
     * Opposite of compress
     *
     * @param splitIndex
     *            the index of the first letter of the second word: "unto" with
     *            index 2 gets "un" and "to". if the index is zero the reading
     *            is split using the separator
     * @param model
     *            the string which is between the words to be split, if no
     *            separator is specified (empty String) the reading is split
     *            using whitespace as default. If a splitIndex and a separator
     *            were specified the reading is split using the splitIndex and
     *            removing the separator from the beginning of the second word.
     *            Is given as a String to avoid problems with 'unsafe'
     *            characters in the URL
     * @return a GraphModel in JSON containing all the created and modified
     *         readings and the deleted relationships on success or
     *         Status.INTERNAL_SERVER_ERROR with a detailed message else
     */
    @POST
    @Path("split/{splitIndex}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response splitReading(@PathParam("splitIndex") int splitIndex,
                                 ReadingBoundaryModel model) {
        assert (model != null);
        GraphModel readingsAndRelationships;
        Node originalReading;
        try (Transaction tx = db.beginTx()) {
            originalReading = db.getNodeById(readId);
            String originalText = originalReading.getProperty("text").toString();
            if (splitIndex >= originalText.length())
                errorMessage = "The index must be smaller than the text length";

            else if (!originalText.contains(model.getCharacter()))
                errorMessage = "no such separator exists";

            else if (splitIndex != 0 && !model.getCharacter().equals("")) {
                String textToRemove = originalText.substring(splitIndex,
                        splitIndex + model.getCharacter().length());
                if (!textToRemove.equals(model.getCharacter()))
                    errorMessage = "The separator does not appear in the index location in the text";
            }

            else if (originalReading.hasRelationship(ERelations.RELATED))
                errorMessage = "A reading to be split cannot be part of any relationship";

            if (errorMessage != null)
                return errorResponse(Status.INTERNAL_SERVER_ERROR);

            String[] splitWords = splitUpText(splitIndex, model.getCharacter(), originalText);

            if (!hasRankGap(originalReading, splitWords.length)) {
                Long rankGap = (Long) originalReading.getProperty("rank") + splitWords.length;
                String tradId = getTraditionId();
                for (Relationship rel : originalReading.getRelationships(
                        Direction.OUTGOING, ERelations.SEQUENCE)) {
                    Node nextNode = rel.getEndNode();
                    if (nextNode.hasProperty("rank") &&
                            ((long)nextNode.getProperty("rank") <= rankGap)) {
                        nextNode.setProperty("rank", rankGap);
                        new Tradition(tradId).recalculateRank(nextNode.getId());
                    }
                }
            }

            readingsAndRelationships = split(originalReading, splitWords);

            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = e.getMessage();
            return errorResponse(Status.INTERNAL_SERVER_ERROR);
        }
        return Response.ok(readingsAndRelationships).build();
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
     * Checks if there is a rank gap after the reading to be split. The rank gap
     * has to have at least the size of the readings words. E.g. if the reading
     * is "the little mouse" and has rank 5 the next reading has to have at
     * least rank 8.
     *
     * @param originalReading
     *            the reading to be split
     * @param numberOfWords
     *            the number of words the reading to be split contains
     * @return true if there is a rank gap
     */
    private boolean hasRankGap(Node originalReading, int numberOfWords) {
        String rankKey = "rank";
        Long rank = (Long) originalReading.getProperty(rankKey);
        for (Relationship rel : originalReading.getRelationships(
                Direction.OUTGOING, ERelations.SEQUENCE)) {
            Node nextNode = rel.getEndNode();
            if (nextNode.hasProperty(rankKey)) {
                Long nextRank = (Long) nextNode.getProperty(rankKey);
                if (nextRank - rank >= numberOfWords) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Performs all necessary steps in the database to split the reading.
     *
     * @param originalReading
     *            the reading to be split
     * @param splitWords
     *            the words the reading consists of
     * @return a model of the split graph
     */
    private GraphModel split(Node originalReading, String[] splitWords) {
        ArrayList<ReadingModel> createdOrChangedReadings = new ArrayList<>();
        ArrayList<RelationshipModel> deletedRelationships = new ArrayList<>();

        ArrayList<Relationship> originalOutgoingRels = new ArrayList<>();
        for (Relationship oldRel : originalReading
                .getRelationships(ERelations.SEQUENCE, Direction.OUTGOING) ) {
            originalOutgoingRels.add(oldRel);
        }
        ArrayList<String> allWitnesses = new ArrayList<>();
        for (Relationship relationship : originalReading.getRelationships(
                ERelations.SEQUENCE, Direction.INCOMING)) {
            String[] witnesses = (String[]) relationship.getProperty("witnesses");
            Collections.addAll(allWitnesses, witnesses);

        }
        originalReading.setProperty("text", splitWords[0]);

        createdOrChangedReadings.add(new ReadingModel(originalReading));

        Node lastReading = originalReading;

        for (int i = 1; i < splitWords.length; i++) {
            Node newReading = db.createNode();

            newReading = ReadingService.copyReadingProperties(lastReading, newReading);
            newReading.setProperty("text", splitWords[i]);
            Long previousRank = (Long) lastReading.getProperty("rank");
            newReading.setProperty("rank", previousRank + 1);

            Relationship relationship = lastReading.createRelationshipTo(newReading, ERelations.SEQUENCE);
            Collections.sort(allWitnesses);
            relationship.setProperty("witnesses", allWitnesses.toArray(new String[allWitnesses.size()]));

            lastReading = newReading;
            createdOrChangedReadings.add(new ReadingModel(newReading));
        }
        for (Relationship oldRel : originalOutgoingRels) {
            Relationship newRel = lastReading.createRelationshipTo(oldRel.getEndNode(), ERelations.SEQUENCE);
            RelationshipService.copyRelationshipProperties(oldRel, newRel);
            deletedRelationships.add(new RelationshipModel(oldRel));
            oldRel.delete();
        }

        return new GraphModel(createdOrChangedReadings, deletedRelationships);
    }

    /**
     * gets the next readings from a given readings in the same witness
     *
     * @param witnessId
     *            : the id (name) of the witness
     *
     * @return http.ok and a model of the requested reading in json on success
     *         or an ERROR in JSON format
     */
    @GET
    @Path("next/{witnessId}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response getNextReadingInWitness(@PathParam("witnessId") String witnessId) {
        try (Transaction tx = db.beginTx()) {
            Node read = db.getNodeById(readId);
            Node next;
            Iterable<Relationship> incoming = read.getRelationships(ERelations.SEQUENCE, Direction.OUTGOING);
            Collection<Relationship> matching = StreamSupport.stream(incoming.spliterator(), false)
                    .filter(x -> isPathFor(x, witnessId))
                    .collect(Collectors.toList());
            if (matching.size() != 1) {
                errorMessage = matching.isEmpty()
                        ? "There is no next reading!"
                        : "There is more than one next reading!";
                return errorResponse(Status.INTERNAL_SERVER_ERROR);
            }
            next = matching.iterator().next().getEndNode();
            ReadingModel result = new ReadingModel(next);
            if (result.getIs_end()) {
                errorMessage = "this was the last reading of this witness";
                return errorResponse(Status.NOT_FOUND);
            }
            tx.success();
            return Response.ok(result).build();
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = e.getMessage();
            return errorResponse(Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * gets the previous readings from a given readings in the same witness
     *
     * @param witnessId
     *            : the id (name) of the witness
     *
     * @return http.ok and a model of the requested reading in json on success
     *         or an ERROR in JSON format
     */
    @GET
    @Path("prior/{witnessId}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response getPreviousReadingInWitness(@PathParam("witnessId") String witnessId) {
        try (Transaction tx = db.beginTx()) {
            Node read = db.getNodeById(readId);
            Node prior;
            Iterable<Relationship> incoming = read.getRelationships(ERelations.SEQUENCE, Direction.INCOMING);
            Collection<Relationship> matching = StreamSupport.stream(incoming.spliterator(), false)
                    .filter(x -> isPathFor(x, witnessId))
                    .collect(Collectors.toList());
            if (matching.size() != 1) {
                errorMessage = matching.isEmpty()
                        ? "There is no next reading!"
                        : "There is more than one next reading!";
                return errorResponse(Status.INTERNAL_SERVER_ERROR);
            }
            prior = matching.iterator().next().getStartNode();
            ReadingModel result = new ReadingModel(prior);
            if (result.getIs_start()) {
                errorMessage = "this was the first reading of this witness";
                return errorResponse(Status.NOT_FOUND);
            }
            tx.success();
            return Response.ok(result).build();
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = e.getMessage();
            return errorResponse(Status.INTERNAL_SERVER_ERROR);
        }
    }

    // Assumes that we are already in a transaction!
    private Boolean isPathFor(Relationship sequence, String sigil) {
        if (sequence.hasProperty("witnesses")) {
            String[] wits = (String []) sequence.getProperty("witnesses");
            for (String wit : wits) {
                if (wit.equals(sigil))
                    return true;
            }
        }
        return false;
    }

    /**
     * Compress two readings into one. Texts will be concatenated together (with
     * or without a space or extra text. The reading with the lower rank will be
     * given first. Opposite of split
     *
     * @param readId2
     *            the id of the second reading
     * @param boundary
     *            the ReadingBoundaryModel that specifies whether the readings will be
     *            separated with a string, and if so, what string it will be. If
     *            the readings have join_next or join_prior respectively set, this
     *            will be respected in preference to the boundary model.
     *
     * @return status.ok if compress was successful.
     *         Status.INTERNAL_SERVER_ERROR with a detailed message if not
     *         concatenated
     */
    @POST
    @Path("concatenate/{read2Id}")
    @Consumes(MediaType.APPLICATION_JSON)
    // @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response compressReadings(@PathParam("read2Id") long readId2, ReadingBoundaryModel boundary) {

        Node read1, read2;
        errorMessage = "problem with a reading. could not compress";

        try (Transaction tx = db.beginTx()) {
            read1 = db.getNodeById(readId);
            read2 = db.getNodeById(readId2);
            if ((long) read1.getProperty("rank") > (long) read2.getProperty("rank")) {
                tx.success();
                errorMessage = "the first reading has a higher rank then the second reading";
                return errorResponse(Status.INTERNAL_SERVER_ERROR);
            }
            if (canBeCompressed(read1, read2)) {
                compress(read1, read2, boundary);
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
        Boolean joined = (read1.hasProperty("join_next") && (Boolean) read1.getProperty("join_next")) ||
                (read2.hasProperty("join_prior") && (Boolean) read2.getProperty("join_prior"));
        if (boundary.getSeparate() && !joined) {
            newText = String.join(boundary.getCharacter(),
                    read1.getProperty("text").toString(), read2.getProperty("text").toString());
        } else {
            newText = String.join("",
                    read1.getProperty("text").toString(), read2.getProperty("text").toString());
        }

        read1.setProperty("text", newText);

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

    private Response errorResponse (Status status) {
        String errorJson = String.format("{\"error\": \"%s\"}", errorMessage);
        return Response.status(status).entity(errorJson).build();
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