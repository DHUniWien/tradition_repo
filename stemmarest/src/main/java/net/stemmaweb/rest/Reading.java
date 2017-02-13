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

    Reading(String requestedId) {
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
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(reading).build();
    }

    /**
     * Changes properties of a reading according to its keys
     *
     * @param changeModels
     *            an array of changeReadingModel objects. Will be converted from
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
        ReadingModel modelToReturn;
        Node reading;
        try (Transaction tx = db.beginTx()) {
            reading = db.getNodeById(readId);
            for (KeyPropertyModel keyPropertyModel : changeModels.getProperties()) {
                if (!reading.hasProperty(keyPropertyModel.getKey()))
                    return Response
                            .status(Status.INTERNAL_SERVER_ERROR)
                            .entity("the reading does not have such property: '"
                                    + keyPropertyModel.getKey()
                                    + "'. no changes to the reading have been done")
                            .build();
            }
            for (KeyPropertyModel keyPropertyModel : changeModels.getProperties()) {
                reading.setProperty(keyPropertyModel.getKey(), keyPropertyModel.getProperty());
            }
            modelToReturn = new ReadingModel(reading);
            tx.success();
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
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
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
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
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
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
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
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
        ArrayList<RelationshipModel> deletedRelationships = null;

        Node originalReading;

        try (Transaction tx = db.beginTx()) {
            List<Long> readings = duplicateModel.getReadings();
            for (Long readId : readings) {
                originalReading = db.getNodeById(readId);
                List<String> newWitnesses = duplicateModel.getWitnesses();

                if (!canBeDuplicated(originalReading, newWitnesses)) {
                    return Response.status(Status.INTERNAL_SERVER_ERROR)
                            .entity(errorMessage).build();
                }

                Node newNode = db.createNode();
                deletedRelationships = duplicate(newWitnesses, originalReading, newNode);
                createdReadings.add(new ReadingModel(newNode));
            }

            tx.success();
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
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
        ArrayList<RelationshipModel> deletedRelationships = new ArrayList<>();
        // copy reading properties to newly added reading
        addedReading = ReadingService.copyReadingProperties(originalReading, addedReading);

        // copy relationships
        for (Relationship originalRel : originalReading.getRelationships(ERelations.RELATED)) {
            Relationship newRel = addedReading.createRelationshipTo(
                    originalRel.getOtherNode(originalReading),
                    ERelations.RELATED);
            for (String key : originalRel.getPropertyKeys()) {
                newRel.setProperty(key, originalRel.getProperty(key));
            }
        }

        // add witnesses to normal relationships
        // Incoming
        for (Relationship originalRelationship : originalReading
                .getRelationships(ERelations.SEQUENCE, Direction.INCOMING))
            deletedRelationships
                    .addAll(transferNewWitnessesFromOriginalReadingToAddedReading(
                            newWitnesses, originalRelationship,
                            originalRelationship.getStartNode(), addedReading));
        // Outgoing
        for (Relationship originalRelationship : originalReading
                .getRelationships(ERelations.SEQUENCE, Direction.OUTGOING)) {
            deletedRelationships
                    .addAll(transferNewWitnessesFromOriginalReadingToAddedReading(
                            newWitnesses, originalRelationship, addedReading,
                            originalRelationship.getEndNode()));
        }

        return deletedRelationships;
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
     * @return a list of the deleted edges
     */
    private ArrayList<RelationshipModel> transferNewWitnessesFromOriginalReadingToAddedReading(
            List<String> newWitnesses, Relationship originalRel,
            Node originNode, Node targetNode) {
        ArrayList<RelationshipModel> deletedRelationships = new ArrayList<>();
        String[] oldWitnesses = (String[]) originalRel.getProperty("witnesses");
        // if oldWitnesses only contains one witness and this one should be
        // duplicated, create new relationship for addedReading and delete
        // the one from the originalReading
        if (oldWitnesses.length == 1) {
            if (newWitnesses.contains(oldWitnesses[0])) {
                Relationship newRel = originNode.createRelationshipTo(targetNode, ERelations.SEQUENCE);
                newRel.setProperty("witnesses", oldWitnesses);
                deletedRelationships.add(new RelationshipModel(originalRel));
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
                    deletedRelationships.add(new RelationshipModel(originalRel));
                    originalRel.delete();
                } else {
                    Collections.sort(stayingWitnesses);
                    originalRel.setProperty("witnesses", stayingWitnesses
                            .toArray(new String[stayingWitnesses.size()]));
                }
            }
        }

        return deletedRelationships;
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
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response mergeReadings(@PathParam("secondReadId") long secondReadId) {

        Node stayingReading;
        Node deletingReading;

        try (Transaction tx = db.beginTx()) {
            stayingReading = db.getNodeById(readId);
            deletingReading = db.getNodeById(secondReadId);

            if (!canBeMerged(stayingReading, deletingReading)) {
                return Response.status(Status.CONFLICT).entity(errorMessage).build();
            }
            merge(stayingReading, deletingReading);

            tx.success();
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
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
            errorMessage = "Readings to be merged cannot contain class 2 relationships " +
                    "(transposition / repetition)";
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
     * Checks if the two readings contain the same text or not.
     *
     * @param stayingReading
     *            the reading which stays in the database
     * @param deletingReading
     *            the reading which will be deleted from the database
     * @return true if they contain the same text
     */
    private boolean doContainSameText(Node stayingReading, Node deletingReading) {
        return stayingReading.getProperty("text").toString()
                .equals(deletingReading.getProperty("text").toString());
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
    private void merge(Node stayingReading, Node deletingReading) {
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
            Direction direction) {
        for (Relationship stayingRel : stayingReading.getRelationships(
                ERelations.SEQUENCE, direction)) {
            for (Relationship deletingRel : deletingReading.getRelationships(
                    ERelations.SEQUENCE, direction)) {
                if (stayingRel.getOtherNode(stayingReading).getId() == (deletingRel
                        .getOtherNode(deletingReading).getId())) {
                    // get Witnesses
                    String[] stayingReadingWitnesses = (String[]) stayingRel.getProperty("witnesses");
                    String[] deletingReadingWitnesses = (String[]) deletingRel.getProperty("witnesses");

                    // combine witness lists into one list
                    int sRWl = stayingReadingWitnesses.length;
                    int dRWl = deletingReadingWitnesses.length;
                    String[] combinedWitnesses = new String[sRWl + dRWl];
                    System.arraycopy(stayingReadingWitnesses, 0, combinedWitnesses, 0, sRWl);
                    System.arraycopy(deletingReadingWitnesses, 0, combinedWitnesses, sRWl, dRWl);
                    Arrays.sort(combinedWitnesses);
                    stayingRel.setProperty("witnesses", combinedWitnesses);
                    deletingRel.delete();
                }
            }
        }
        for (Relationship deletingRel : deletingReading.getRelationships(
                ERelations.SEQUENCE, direction)) {
            Relationship newRel;
            if (direction.equals(Direction.OUTGOING)) {
                newRel = stayingReading
                        .createRelationshipTo(deletingRel.getOtherNode(deletingReading),
                                ERelations.SEQUENCE);
            } else {
                newRel = deletingRel
                        .getOtherNode(deletingReading)
                        .createRelationshipTo(stayingReading, ERelations.SEQUENCE);
            }
            newRel.setProperty("witnesses", deletingRel.getProperty("witnesses"));
            deletingRel.delete();
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
                                 CharacterModel model) {
        assert (model != null);
        GraphModel readingsAndRelationships;
        Node originalReading;
        try (Transaction tx = db.beginTx()) {
            originalReading = db.getNodeById(readId);
            String originalText = originalReading.getProperty("text").toString();
            if (splitIndex >= originalText.length()) {
                return Response.status(Status.INTERNAL_SERVER_ERROR)
                        .entity("The index must be smaller than the text length")
                        .build();
            }

            if (!originalText.contains(model.getCharacter())) {
                return Response.status(Status.INTERNAL_SERVER_ERROR)
                        .entity("no such separator exists")
                        .build();
            }

            if (splitIndex != 0 && !model.getCharacter().equals("")) {
                String textToRemove = originalText.substring(splitIndex,
                        splitIndex + model.getCharacter().length());
                if (!textToRemove.equals(model.getCharacter())) {
                    return Response.status(Status.INTERNAL_SERVER_ERROR)
                            .entity("The separator does not appear in the index location in the text")
                            .build();
                }
            }

            if (originalReading.hasRelationship(ERelations.RELATED)) {
                return Response.status(Status.INTERNAL_SERVER_ERROR)
                        .entity("A reading to be split cannot be part of any relationship")
                        .build();
            }

            String[] splitWords = splitUpText(splitIndex, model.getCharacter(), originalText);

            if (!hasRankGap(originalReading, splitWords.length)) {
                // TODO (sk): ask TLA, if modification of the rank will be ok
                Long rankGap = (Long) originalReading.getProperty("rank") + splitWords.length;
                String tradId = originalReading.getProperty("tradition_id").toString();
                for (Relationship rel : originalReading.getRelationships(
                        Direction.OUTGOING, ERelations.SEQUENCE)) {
                    Node nextNode = rel.getEndNode();
                    if (nextNode.hasProperty("rank") &&
                            ((long)nextNode.getProperty("rank") <= rankGap)) {
                        nextNode.setProperty("rank", rankGap);
                        new Tradition(tradId).recalculateRank(nextNode.getId());
                    }
                }
                /*
                if (!hasRankGap(originalReading, splitWords.length)) {
                    return Response.status(Status.INTERNAL_SERVER_ERROR)
                            .entity("There has to be a rank-gap after a reading to be split")
                            .build();
                }
                */
            }

            readingsAndRelationships = split(originalReading, splitWords);

            tx.success();
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
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
                String message = matching.isEmpty()
                        ? "There is no next reading!"
                        : "There is more than one next reading!";
                return Response.status(Status.INTERNAL_SERVER_ERROR)
                        .entity(message)
                        .build();
            }
            next = matching.iterator().next().getEndNode();
            ReadingModel result = new ReadingModel(next);
            if (result.getIs_end())
                return Response.status(Status.NOT_FOUND)
                        .entity("this was the last reading of this witness").build();
            tx.success();
            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
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
                String message = matching.isEmpty()
                        ? "There is no prior reading!"
                        : "There is more than one prior reading!";
                return Response.status(Status.INTERNAL_SERVER_ERROR)
                        .entity(message)
                        .build();
            }
            prior = matching.iterator().next().getStartNode();
            ReadingModel result = new ReadingModel(prior);
            if (result.getIs_start())
                return Response.status(Status.NOT_FOUND)
                        .entity("this was the first reading of this witness").build();
            tx.success();
            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
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
     * @param con
     *            concatenate must be 0 (for 'no') or 1 (for 'yes'). if
     *            concatenate is set to 1, the compressing will be done with
     *            with_str between the texts of the readings. If it is 0, texts
     *            will be concatenate with a single space.
     * @param character
     *            the string which will come between the texts of the readings
     *            if con is set to 1 could also be an empty string. Is given as
     *            a String to avoid problems with 'unsafe' characters in the URL
     *
     * @return status.ok if compress was successful.
     *         Status.INTERNAL_SERVER_ERROR with a detailed message if not
     *         concatenated
     */
    @POST
    @Path("concatenate/{read2Id}/{con}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response compressReadings(@PathParam("read2Id") long readId2,
                                     @PathParam("con") String con, CharacterModel character) {

        Node read1, read2;
        errorMessage = "problem with a reading. could not compress";
        boolean toConcatenate;
        switch (con) {
            case "0":
                toConcatenate = false;
                break;
            case "1":
                toConcatenate = true;
                break;
            default:
                return Response.status(Status.INTERNAL_SERVER_ERROR)
                        .entity("argument concatenate has an invalid value")
                        .build();
        }

        try (Transaction tx = db.beginTx()) {
            read1 = db.getNodeById(readId);
            read2 = db.getNodeById(readId2);
            if ((long) read1.getProperty("rank") > (long) read2.getProperty("rank")) {
                tx.success();
                return Response.status(Status.INTERNAL_SERVER_ERROR)
                        .entity("the first reading has a higher rank then the second reading")
                        .build();
            }
            if (canBeCompressed(read1, read2)) {
                compress(read1, read2, toConcatenate, character.getCharacter());
                tx.success();
                return Response.ok().build();
            }
            tx.success();
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.status(Status.CONFLICT).entity(errorMessage).build();
    }

    /**
     * compress two readings
     *
     * @param read1
     *            the first reading
     * @param read2
     *            the second reading
     * @param with_str
     *            the string to come between the texts of the readings.
     * @param toConcatenate
     *            boolean: if true - texts of readings will be concatenated with
     *            with_str in between. if false: texts of readings will be
     *            concatenated with one empty space in between.
     */
    private void compress(Node read1, Node read2, boolean toConcatenate, String with_str) {
        String textRead1 = (String) read1.getProperty("text");
        String textRead2 = (String) read2.getProperty("text");
        String insertedText = (toConcatenate) ? with_str : " ";

        read1.setProperty("text", textRead1 + insertedText + textRead2);

        Relationship from1to2 = getRelationshipBetweenReadings(read1, read2);
        from1to2.delete();
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
        for (Relationship tempRel : read2.getRelationships(Direction.OUTGOING)) {
            Node tempNode = tempRel.getOtherNode(read2);
            Relationship rel1 = read1.createRelationshipTo(tempNode, ERelations.SEQUENCE);
            for (String key : tempRel.getPropertyKeys()) {
                rel1.setProperty(key, tempRel.getProperty(key));
            }
            tempRel.delete();
        }

        for (Relationship tempRel : read2.getRelationships(Direction.INCOMING)) {
            Node tempNode = tempRel.getOtherNode(read2);
            Relationship rel1 = tempNode.createRelationshipTo(read1, ERelations.SEQUENCE);
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
        Iterable<Relationship> rel;
        rel = read2.getRelationships(ERelations.SEQUENCE);

        Iterator<Relationship> normalFromRead2 = rel.iterator();
        if (!normalFromRead2.hasNext()) {
            errorMessage = "second readings is not connected. could not compress";
            return false;
        }
        Relationship from1to2 = getRelationshipBetweenReadings(read1, read2);
        if (from1to2 == null) {
            errorMessage = "reading are not neighbors. could not compress";
            return false;
        }

        if (hasNotNormalRelationships(read1) || hasNotNormalRelationships(read2)) {
            errorMessage = "reading has other relations. could not compress";
            return false;
        }

        if (1 != numberOfPredecessors(read2)) {
            errorMessage = "reading has more then one predecessor. could not compress";
            return false;
        }
        return true;
    }

    /**
     * checks if a reading has relationships which are not SEQUENCE
     *
     * @param read
     *            the reading
     * @return true if it has, false otherwise
     */
    private boolean hasNotNormalRelationships(Node read) {
        String type;
        String normal = ERelations.SEQUENCE.toString();

        for (Relationship rel : read.getRelationships()) {
            type = rel.getType().name();

            if (!type.equals(normal)) {
                return true;
            }
        }
        return false;
    }

    /**
     * get the normal relationship between two readings
     *
     * @param read1
     *            the first reading
     * @param read2
     *            the second reading
     * @return the SEQUENCE relationship
     */
    private Relationship getRelationshipBetweenReadings(Node read1, Node read2) {
        Relationship from1to2 = null;
        for (Relationship tempRel : read1.getRelationships()) {
            if (tempRel.getOtherNode(read1).equals(read2)) {
                from1to2 = tempRel;
            }
        }
        return from1to2;
    }

    /**
     * checks if a reading has relationships which are not SEQUENCE
     *
     * @param read
     *            the reading
     * @return true if it has, false otherwise
     */
    private int numberOfPredecessors(Node read) {
        int numberOfPredecessors = 0;
        for (Relationship rel : read.getRelationships(ERelations.SEQUENCE, Direction.INCOMING)) {
            numberOfPredecessors++;
        }
        return numberOfPredecessors;
    }
}