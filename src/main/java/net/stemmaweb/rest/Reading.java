package net.stemmaweb.rest;

import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import jakarta.ws.rs.*;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import com.qmino.miredot.annotations.ReturnType;
import net.stemmaweb.model.*;
import net.stemmaweb.services.*;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.traversal.Uniqueness;

import static net.stemmaweb.rest.Util.jsonerror;

/**
 * Comprises all Rest API calls related to a reading. Can be called via
 * http://BASE_URL/reading
 * 
 * @author PSE FS 2015 Team2
 */

public class Reading {

    private String errorMessage; // global error message used for sub-method calls

    private final GraphDatabaseService db;
    /**
     * The ID of the reading to query
     */
    private final Long readId;

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
     * @statuscode 204 - if the reading doesn't exist
     * @statuscode 500 - on error, with an error message
    */
    @GET
    @Produces("application/json; charset=utf-8")
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
     * Changes the properties of an existing reading. Properties whose change has
     * potential knock-on effects on other readings, such as "is_lemma", cannot be
     * set using this method.
     *
     * @summary Update an existing reading
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
    @Produces("application/json; charset=utf-8")
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
                    return errorResponse(Status.BAD_REQUEST);
                } else if (currentKey.equals("is_lemma")
                        && !keyPropertyModel.getProperty().equals(reading.getProperty(currentKey))) {
                    errorMessage = "Use /setlemma to change the reading's lemmatisation";
                    return errorResponse(Status.BAD_REQUEST);
                }
                // Check that this field actually exists in our model
                Field ourField = modelToReturn.getClass().getDeclaredField(currentKey);
                // Then set the property.
                // Convert types not native to JSON
                if (ourField.getType().equals(Long.class))
                    reading.setProperty(currentKey, Long.valueOf(keyPropertyModel.getProperty().toString()));
                else
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
        } catch (NotFoundException e) {
            errorMessage = e.getMessage();
            return errorResponse(Status.NOT_FOUND);
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = e.getMessage();
            return errorResponse(Status.INTERNAL_SERVER_ERROR);
        }
        return Response.status(Response.Status.OK).entity(modelToReturn).build();
    }

    /**
     * Deletes a reading. This only makes sense if it is a user-addable reading, i.e. an emendation.
     * If the lemma path goes through the emendation, the lemma path will also be removed.
     *
     * @summary Delete a user-addable reading
     * @return  A GraphModel containing the deleted content (readings and sequences)
     * @statuscode 200 - on success
     * @statuscode 403 - if deletion of a non-user reading is requested
     * @statuscode 404 - if the reading doesn't exist
     * @statuscode 500 - on error
     */
    @DELETE
    @Produces("application/json; charset=utf-8")
    @ReturnType(clazz = GraphModel.class)
    public Response deleteUserReading() {
        GraphModel deletedElements = new GraphModel();
        try (Transaction tx = db.beginTx()) {
            Node reading = db.getNodeById(readId);
            // Can we delete the reading?
            if (!reading.hasLabel(Nodes.EMENDATION)) {
                errorMessage = "Only emendation readings can be deleted";
                return errorResponse(Status.BAD_REQUEST);
            }

            // Get all its relationships for deletion
            boolean onLemmaPath = false;
            List<SequenceModel> deletedSeqs = new ArrayList<>();
            for (Relationship r : reading.getRelationships()) {
                if (r.isType(ERelations.LEMMA_TEXT)) {
                    onLemmaPath = true;
                } else {
                    if (!r.isType(ERelations.HAS_EMENDATION))
                        deletedSeqs.add(new SequenceModel(r));
                    r.delete();
                }
            }
            if (onLemmaPath) {
                // The entire lemma sequence text should be deleted; otherwise it will be broken here.
                db.traversalDescription().depthFirst().relationships(ERelations.LEMMA_TEXT).traverse(reading)
                        .relationships().forEach(x -> {deletedSeqs.add(new SequenceModel(x)); x.delete();});
            }
            deletedElements.setSequences(deletedSeqs);
            deletedElements.setReadings(Collections.singletonList(new ReadingModel(reading)));
            reading.delete();
            tx.success();
        } catch (NotFoundException e) {
            errorMessage = e.getMessage();
            return errorResponse(Status.NOT_FOUND);
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = e.getMessage();
            return errorResponse(Status.INTERNAL_SERVER_ERROR);
        }
        return Response.ok(deletedElements).build();
    }

    /**
     * Toggles whether this reading is a lemma. If so, ensures that no other reading at this
     * rank in this section is a lemma. Returns all readings that were changed.
     *
     * @param value - "true" if the reading should be a lemma
     * @return a list of changed ReadingModels
     * @statuscode 200 - on success
     * @statuscode 500 - on error, with an error message
     */
    @POST
    @Path("setlemma")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces("application/json; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.ReadingModel>")
    public Response setReadingAsLemma(@FormParam("value") @DefaultValue("false") String value) {
        List<ReadingModel> changed = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            Node reading = db.getNodeById(readId);
            if (value.equals("true")) {
                if (!reading.hasProperty("is_lemma") || !reading.getProperty("is_lemma").equals(true)) {
                    Map<String, Object> criteria = new HashMap<>();
                    criteria.put("section_id", reading.getProperty("section_id"));
                    criteria.put("rank", reading.getProperty("rank"));
                    criteria.put("is_lemma", true);
                    db.findNodes(Nodes.READING, criteria).forEachRemaining(x -> {
                        x.removeProperty("is_lemma");
                        changed.add(new ReadingModel(x));
                    });
                    reading.setProperty("is_lemma", true);
                    changed.add(new ReadingModel(reading));
                }
            } else if (reading.hasProperty("is_lemma")){
                reading.removeProperty("is_lemma");
                changed.add(new ReadingModel(reading));
            } // otherwise it's a no-op
            tx.success();
        } catch (NotFoundException e) {
            errorMessage = e.getMessage();
            return errorResponse(Status.NOT_FOUND);
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = e.getMessage();
            return errorResponse(Status.INTERNAL_SERVER_ERROR);
        }
        return Response.ok(changed).build();
    }


    /**
     * Inserts a lacuna in the specified witness(es) after a given reading and before the next
     * reading(s) in the sequence for that witness / those witnesses. Intended to indicate that
     * empty ranks are not a simple omission.
     *
     * @summary Insert a lacuna
     * @param forWitnesses - one or more witnesses that should have the lacuna marked.
     * @return a GraphModel containing the lacuna and its associated SEQUENCE links.
     * @statuscode 200 - on success
     * @statuscode 400 - if a specified witness does not pass through the given reading
     * @statuscode 500 - on error
     */
    @POST
    @Path("/lacunaAfter")
    @Produces("application/json; charset=utf-8")
    @ReturnType("net.stemmaweb.model.GraphModel")
    public Response addLacuna (@QueryParam("witness") List<String> forWitnesses) {
        GraphModel result = new GraphModel();
        try (Transaction tx = db.beginTx()) {
            // Get a reading model so we can easily check the witnesses
            Node us = db.getNodeById(readId);
            ReadingModel thisReading = new ReadingModel(us);
            // Make our lacuna node
            Node lacuna = db.createNode(Nodes.READING);
            lacuna.setProperty("is_lacuna", true);
            lacuna.setProperty("rank", (Long) us.getProperty("rank") + 1);
            lacuna.setProperty("section_id", us.getProperty("section_id"));
            lacuna.setProperty("text", "#LACUNA#");
            HashSet<Relationship> newSeqs = new HashSet<>();
            HashSet<Node> pushedReadings = new HashSet<>();
            Set<Node> changedReadings = new HashSet<>();
            changedReadings.add(lacuna);
            for (String sigil : forWitnesses) {
                // Make sure the witness in question belongs to the given reading
                if (!thisReading.getWitnesses().contains(sigil)) {
                    errorMessage = String.format("The requested witness %s does not belong to this reading", sigil);
                    return errorResponse(Status.BAD_REQUEST);
                }
                // Find the witness's following node
                HashMap<String, String> wit = parseSigil(sigil);
                Node next = this.getNeighbourReadingInSequence(wit.get("sigil"), wit.get("layer"), Direction.OUTGOING);
                if (next == null) {
                    errorMessage = "Witness path " + sigil + " ends after requested reading";
                    return errorResponse(Status.INTERNAL_SERVER_ERROR);
                }
                // Are we going to need to re-rank it?
                if (next.getProperty("rank", 0L).equals((Long) us.getProperty("rank") + 1))
                    pushedReadings.add(next);
                // Thread the lacuna between them
                ReadingService.removeWitnessLink(us, next, wit.get("sigil"), wit.get("layer"), "none");
                newSeqs.add(ReadingService.addWitnessLink(us, lacuna, wit.get("sigil"), wit.get("layer")));
                newSeqs.add(ReadingService.addWitnessLink(lacuna, next, wit.get("sigil"), wit.get("layer")));
            }
            for (Node pushed : pushedReadings) {
                changedReadings.addAll(ReadingService.recalculateRank(pushed));
            }
            result.setReadings(changedReadings.stream().map(ReadingModel::new).collect(Collectors.toList()));
            result.setSequences(newSeqs.stream().map(SequenceModel::new).collect(Collectors.toList()));
            tx.success();
        } catch (NotFoundException e) {
            errorMessage = e.getMessage();
            return errorResponse(Status.NOT_FOUND);
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = e.getMessage();
            return errorResponse(Status.INTERNAL_SERVER_ERROR);
        }
        return Response.ok(result).build();
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
    @Produces("application/json; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.ReadingModel>")
    public Response getRelatedReadings(@QueryParam("types") List<String> filterTypes) {
        try {
            List<Node> relatedReadings = collectRelatedReadings(filterTypes);
            return Response.ok(relatedReadings.stream().map(ReadingModel::new).collect(Collectors.toList())).build();
        } catch (NotFoundException e) {
            errorMessage = e.getMessage();
            return errorResponse(Status.NOT_FOUND);
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = e.getMessage();
            return errorResponse(Status.INTERNAL_SERVER_ERROR);
        }
    }


    /**
     * Propagates this reading's normal form to all other readings related by the given type.
     *
     * @summary Propagate normal form along relations
     * @param onRelationType - the relation type to propagate along
     * @return a list of changed readings
     * @statuscode 200 - on success
     * @statuscode 400 - if the reading has neither normal form nor text
     * @statuscode 500 - on failure
     */
    @POST
    @Path("normaliseRelated/{reltype}")
    @Produces("application/json; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.ReadingModel>")
    public Response normaliseRelated(@PathParam("reltype") String onRelationType) {
        List<ReadingModel> changed = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            List<Node> related = collectRelatedReadings(Collections.singletonList(onRelationType));
            Node us = db.getNodeById(readId);
            String key = us.hasProperty("normal_form") ? "normal_form" : "text";
            Object ourNormalForm = db.getNodeById(readId).getProperty(key);
            // Set the normal form on this reading if it wasn't already there
            us.setProperty("normal_form", ourNormalForm);
            for (Node n : related) {
                if (!n.getProperty("normal_form", "").equals(ourNormalForm)) {
                    n.setProperty("normal_form", ourNormalForm);
                    changed.add(new ReadingModel(n));
                }
            }
            tx.success();
        } catch (NotFoundException e) {
            Status ret = Status.NOT_FOUND;    // Maybe it was the reading that wasn't found
            errorMessage = e.getMessage();
            if (errorMessage.contains("No such property"))  // or maybe it was the property.
                ret = Status.BAD_REQUEST;
            return errorResponse(ret);
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = e.getMessage();
            return errorResponse(Status.INTERNAL_SERVER_ERROR);
        }
        return Response.ok(changed).build();
    }


    private List<Node> collectRelatedReadings(List<String> filterTypes) throws Exception {
        List<Node> allRelated = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            Node reading = db.getNodeById(readId);
            RelationService.RelatedReadingsTraverser rt;
            if (filterTypes == null || filterTypes.size() == 0)
                // Traverse all relations
                rt = new RelationService.RelatedReadingsTraverser(reading);
            else
                // Traverse only the named relations
                rt = new RelationService.RelatedReadingsTraverser(reading, x -> filterTypes.contains(x.getName()));
            db.traversalDescription().depthFirst()
                    .relationships(ERelations.RELATED)
                    .evaluator(rt)
                    .uniqueness(Uniqueness.NODE_GLOBAL)
                    .traverse(reading).nodes().forEach(allRelated::add);
            tx.success();
        }
        return allRelated;
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
    @Produces("application/json; charset=utf-8")
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
        } catch (NotFoundException e) {
            errorMessage = e.getMessage();
            return errorResponse(Status.NOT_FOUND);
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
    @Produces("application/json; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.WitnessModel>")
    public Response getReadingWitnesses() {
        try {
            return Response.ok(collectWitnesses(false)).build();
        } catch (NotFoundException e) {
            errorMessage = e.getMessage();
            return errorResponse(Status.NOT_FOUND);
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = e.getMessage();
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
    @Produces("application/json; charset=utf-8")
    @ReturnType(clazz = GraphModel.class)
    public Response duplicateReading(DuplicateModel duplicateModel) {

        ArrayList<ReadingModel> createdReadings = new ArrayList<>();
        ArrayList<RelationModel> tempDeleted = new ArrayList<>();
        ArrayList<SequenceModel> newSequences = new ArrayList<>();

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
                GraphModel localResult = duplicate(newWitnesses, originalReading, newNode);
                tempDeleted.addAll(localResult.getRelations());
                newSequences.addAll(localResult.getSequences());
                ReadingModel newModel = new ReadingModel(newNode);
                newModel.setOrig_reading(String.valueOf(readId));
                createdReadings.add(newModel);
            }

            // Remove any sequences from the list that were created only temporarily (e.g. if
            // multiple nodes were duplicated serially.)
            ArrayList<SequenceModel> tempSequences = new ArrayList<>();
            for (SequenceModel sm : newSequences) {
                try {
                    db.getRelationshipById(Long.parseLong(sm.getId()));
                } catch (NotFoundException e) {
                    tempSequences.add(sm);
                }
            }
            newSequences.removeAll(tempSequences);
            tx.success();
        } catch (NotFoundException e) {
            errorMessage = e.getMessage();
            return errorResponse(Status.NOT_FOUND);
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

        GraphModel readingsAndRelations = new GraphModel(createdReadings, deletedRelations, newSequences);
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
     * @return a GraphModel containing the new readings, new sequences and deleted relations.
     *         Note that this does NOT return deleted or modified sequences.
     */
    private GraphModel duplicate(List<String> newWitnesses, Node originalReading, Node addedReading) throws Exception {
        // copy reading properties to newly added reading
        ReadingService.copyReadingProperties(originalReading, addedReading);
        Reading rdgRest = new Reading(String.valueOf(originalReading.getId()));

        // add witnesses to the correct sequence links
        HashSet<Relationship> newSequences = new HashSet<>();
        for (String wit : newWitnesses) {
            HashMap<String, String> witness = parseSigil(wit);
            Node prior = rdgRest.getNeighbourReadingInSequence(witness.get("sigil"), witness.get("layer"), Direction.INCOMING);
            Node next = rdgRest.getNeighbourReadingInSequence(witness.get("sigil"), witness.get("layer"), Direction.OUTGOING);
            if (prior == null || next == null) {
                throw new Exception("No prior / next node found for reading " + originalReading.getId() + "!");
            }
            try (Transaction tx = db.beginTx()) {
                // Store the added/changed SEQUENCE links, so that they go into the new GraphModel
                newSequences.add(ReadingService.addWitnessLink(prior, addedReading, witness.get("sigil"), witness.get("layer")));
                newSequences.add(ReadingService.addWitnessLink(addedReading, next, witness.get("sigil"), witness.get("layer")));
                ReadingService.removeWitnessLink(prior, originalReading, witness.get("sigil"), witness.get("layer"), "end");
                ReadingService.removeWitnessLink(originalReading, next, witness.get("sigil"), witness.get("layer"), "start");
                tx.success();
            }
        }
        ArrayList<SequenceModel> sequenceModels = new ArrayList<>();
        newSequences.forEach(x -> sequenceModels.add(new SequenceModel(x)));

        // replicated all colocated relations of the original reading;
        // delete all non-colocated relations that cross our rank
        ArrayList<RelationModel> tempDeleted = new ArrayList<>();
        String sectId = originalReading.getProperty("section_id").toString();
        String tradId = getTraditionId();
        Section sectionRest = new Section(tradId, sectId);
        Long ourRank = (Long) originalReading.getProperty("rank");
        for (RelationModel rm : sectionRest.sectionRelations()) {
            Relationship originalRel = db.getRelationshipById(Long.parseLong(rm.getId()));
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
                ReadingModel relSource = new ReadingModel(db.getNodeById(Long.parseLong(rm.getSource())));
                ReadingModel relTarget = new ReadingModel(db.getNodeById(Long.parseLong(rm.getTarget())));
                if ((relSource.getRank() < ourRank && relTarget.getRank() > ourRank)
                    || (relSource.getRank() > ourRank && relTarget.getRank() < ourRank)) {
                    originalRel.delete();
                    tempDeleted.add(rm);
                }
            }
        }

        return new GraphModel(new ArrayList<>(), tempDeleted, sequenceModels);
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

            // TEMPORARY sanity check: Find all witnesses of the reading to be merged.
            ReadingModel drm = new ReadingModel(deletingReading);

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
            merge(stayingReading, deletingReading);

            // TEMPORARY: Check that all affected witnesses still have paths to the end node
            for (String sig : drm.getWitnesses()) {
                HashMap<String, String> parts = parseSigil(sig);
                Witness w = new Witness(getTraditionId(), stayingReading.getProperty("section_id").toString(), parts.get("sigil"));
                Response r;
                if (parts.get("layer").equals("witnesses"))
                    r = w.getWitnessAsText();
                else {
                    ArrayList<String> layers = new ArrayList<>();
                    layers.add(parts.get("layer"));
                    r = w.getWitnessAsTextWithLayer(layers, "0", "E");
                }
                if (r.getStatus() != Status.OK.getStatusCode()) {
                    throw new Exception ("Merge broke path for witness " + sig);
                }
            }
            // Re-rank nodes if necessary
            if (!samerank) {
                ReadingService.recalculateRank(aPriorNode);
            }

            tx.success();
        } catch (NotFoundException e) {
            errorMessage = e.getMessage();
            return errorResponse(Status.NOT_FOUND);
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
    private boolean canBeMerged(Node stayingReading, Node deletingReading) throws Exception {
        // Ensure that the two readings belong to the same section.
        if (!stayingReading.getProperty("section_id").equals(deletingReading.getProperty("section_id"))) {
            errorMessage = "Readings must be in the same section!";
            return false;
        }
        // Test for non-colo relations.
        if (hasNonColoRelations(stayingReading, deletingReading)) {
            errorMessage = "Readings to be merged cannot contain cross-location relations";
            return false;
        }
        // If the two readings are aligned, there is no need to test for cycles.
        boolean aligned = false;
        RelationService.RelatedReadingsTraverser rt = new RelationService.RelatedReadingsTraverser(
                stayingReading, RelationTypeModel::getIs_colocation);
        for (Node n : db.traversalDescription().depthFirst()
                .relationships(ERelations.RELATED)
                .evaluator(rt)
                .uniqueness(Uniqueness.NODE_GLOBAL)
                .traverse(stayingReading).nodes()) {
            if (n.equals(deletingReading)) {
                aligned = true;
                break;
            }
        }
        // Test for cycles.
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
     */
    private void merge(Node stayingReading, Node deletingReading) {
        // Remove any existing relations between the readings
        deleteRelationBetweenReadings(stayingReading, deletingReading);
        // Transfer the witnesses of the to-be-deleted reading to the staying reading
        for (Relationship r : deletingReading.getRelationships(ERelations.SEQUENCE, Direction.INCOMING)) {
            ReadingService.transferWitnesses(r.getStartNode(), stayingReading, r);
            r.delete();
        }
        for (Relationship r : deletingReading.getRelationships(ERelations.SEQUENCE, Direction.OUTGOING)) {
            ReadingService.transferWitnesses(stayingReading, r.getEndNode(), r);
            r.delete();
        }
        // Transfer any existing reading relations to the node that will remain
        addRelationsToStayingReading(stayingReading, deletingReading);
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
    @Produces("application/json; charset=utf-8")
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
            ReadingService.recalculateRank(originalReading, true);

            tx.success();
        } catch (NotFoundException e) {
            errorMessage = e.getMessage();
            return errorResponse(Status.NOT_FOUND);
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
     * @return a list of the new SEQUENCE relationships created.
     */
    private GraphModel split(Node originalReading, int splitIndex, ReadingBoundaryModel model) {
        ArrayList<ReadingModel> createdOrChangedReadings = new ArrayList<>();
        ArrayList<SequenceModel> createdSequences = new ArrayList<>();

        // Get the sequence relationships that came out of the original reading
        ArrayList<Relationship> originalOutgoingRels = new ArrayList<>();
        originalReading.getRelationships(ERelations.SEQUENCE, Direction.OUTGOING).forEach(originalOutgoingRels::add);

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
            // Set the rank here, even though we re-rank above, so that the ReadingModels we produce are right
            Long previousRank = (Long) lastReading.getProperty("rank");
            newReading.setProperty("rank", previousRank + 1);
            if (!model.getSeparate()) {
                newReading.setProperty("join_prior", true);
                lastReading.setProperty("join_next", true);
            }

            // Copy the witnesses from our outgoing sequence links
            Relationship newSeq = lastReading.createRelationshipTo(newReading, ERelations.SEQUENCE);
            // This will pick up the relationship we just made
            for (Relationship r : originalOutgoingRels)
                ReadingService.transferWitnesses(lastReading, newReading, r);

            // Add the newly created objects to our eventual GraphModel
            createdOrChangedReadings.add(new ReadingModel(newReading));
            createdSequences.add(new SequenceModel(newSeq));

            // Loop
            lastReading = newReading;
        }
        for (Relationship oldRel : originalOutgoingRels) {
            Relationship newRel = lastReading.createRelationshipTo(oldRel.getEndNode(), oldRel.getType());
            RelationService.copyRelationshipProperties(oldRel, newRel);
            createdSequences.add(new SequenceModel(newRel));
            oldRel.delete();
        }

        return new GraphModel(createdOrChangedReadings, new ArrayList<>(), createdSequences);
    }

    /**
     * Gets the reading that follows the requested reading in the given witness.
     *
     * @summary Next reading
     *
     * @param witnessId - the id (sigil) of the witness
     * @param layer - the witness layer to follow
     *
     * @return the following reading
     * @statuscode 200 - on success
     * @statuscode 404 - if there is no subsequent reading
     * @statuscode 500 - on error, with an error message
     */
    @GET
    @Path("next/{witnessId}")
    @Produces("application/json; charset=utf-8")
    @ReturnType(clazz = ReadingModel.class)
    public Response getNextReadingInWitness(@PathParam("witnessId") String witnessId,
                                            @DefaultValue("witnesses") @QueryParam("layer") String layer) {
        Node foundNeighbour = getNeighbourReadingInSequence(witnessId, layer, Direction.OUTGOING);
        if (foundNeighbour != null) {
            ReadingModel result = new ReadingModel(foundNeighbour);
            if (result.getIs_end()) {
                errorMessage = "this was the last reading for this witness";
                return errorResponse(Status.NOT_FOUND);
            }
            return Response.ok(new ReadingModel(foundNeighbour)).build();
        }
        return errorResponse(errorMessage.contains("not found") ? Status.NOT_FOUND : Status.INTERNAL_SERVER_ERROR);
    }

    /**
     * Gets the reading that precedes the requested reading in the given witness.
     *
     * @summary Prior reading
     *
     * @param witnessId - the id (sigil) of the witness
     * @param layer - the witness layer to follow
     *
     * @return the prior reading
     * @statuscode 200 - on success
     * @statuscode 404 - if there is no prior reading
     * @statuscode 500 - on error, with an error message
     */
    @GET
    @Path("prior/{witnessId}")
    @Produces("application/json; charset=utf-8")
    @ReturnType(clazz = ReadingModel.class)
    public Response getPreviousReadingInWitness(@PathParam("witnessId") String witnessId,
                                                @DefaultValue("witnesses") @QueryParam("layer") String layer) {
        Node foundNeighbour = getNeighbourReadingInSequence(witnessId, layer, Direction.INCOMING);
        if (foundNeighbour != null) {
            ReadingModel result = new ReadingModel(foundNeighbour);
            if (result.getIs_start()) {
                errorMessage = "this was the first reading for this witness";
                return errorResponse(Status.NOT_FOUND);
            }
            return Response.ok(new ReadingModel(foundNeighbour)).build();
        }
        return errorResponse(errorMessage.contains("not found") ? Status.NOT_FOUND : Status.INTERNAL_SERVER_ERROR);
    }

    // Gets the neighbour reading in the given direction for the given witness. Returns
    // the relevant ReadingModel, or sets errorMessage and returns null.
    private Node getNeighbourReadingInSequence(String witnessId, String layer, Direction dir) {
        Node neighbour = null;
        try (Transaction tx = db.beginTx()) {
            Node read = db.getNodeById(readId);
            // Sanity check: does the requested witness+layer actually exist in this node in
            // either direction?
            ReadingModel rm = new ReadingModel(read);
            if (!layer.equals("witnesses")) { // if the base witness isn't here we will error below anyway
                String wholesigil = String.format("%s (%s)", witnessId, layer);
                if (!rm.getWitnesses().contains(wholesigil)) {
                    errorMessage = "Requested witness layer " + wholesigil + "does not pass through this node";
                    return null;
                }

            }
            String dirdisplay = dir.equals(Direction.INCOMING) ? "prior" : "next";
            Iterable<Relationship> seqs = read.getRelationships(ERelations.SEQUENCE, dir);
            // Get the list of relations matching the given layer
            Collection<Relationship> matching = StreamSupport.stream(seqs.spliterator(), false)
                    .filter(x -> isPathFor(x, witnessId, layer))
                    .collect(Collectors.toList());
            // If none and we are looking for a layer, re-fetch the list of relations matching the base layer
            if (matching.size() == 0 && !layer.equals("witnesses")) {
                matching = StreamSupport.stream(seqs.spliterator(), false)
                        .filter(x -> isPathFor(x, witnessId, "witnesses"))
                        .collect(Collectors.toList());
            }
            // We should now have exactly one matching sequence.
            if (matching.size() != 1) {
                errorMessage = matching.isEmpty()
                        ? "There is no " + dirdisplay + " reading!"
                        : "There is more than one " + dirdisplay + " reading!";
            } else {
                neighbour = matching.iterator().next().getOtherNode(read);
            }
            tx.success();
        } catch (NotFoundException e) {
            errorMessage = e.getMessage();
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = e.getMessage();
        }
        return neighbour;
    }

    // Assumes that we are already in a transaction!
    // Returns true if the sequence contains the given witness layer.
    private Boolean isPathFor(Relationship sequence, String sigil, String layer) {
        if (sequence.hasProperty(layer)) {
            String[] wits = (String []) sequence.getProperty(layer);
            for (String wit : wits) {
                if (wit.equals(sigil))
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
    // @Produces("application/json; charset=utf-8")
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
        } catch (NotFoundException e) {
            errorMessage = e.getMessage();
            return errorResponse(Status.NOT_FOUND);
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
        // This will store the combined plain text form
        String plaintextform = null;
        // These are the (initial) default values in case display and normal_form aren't set
        String r1plain = read1.getProperty("text", "").toString();
        String r2plain = read2.getProperty("text", "").toString();
        for (String prop : text_properties) {
            if (boundary.getSeparate() && !joined) {
                newText = String.join(boundary.getCharacter(),
                        read1.getProperty(prop, r1plain).toString(),
                        read2.getProperty(prop, r2plain).toString());
            } else {
                newText = String.join("",
                        read1.getProperty(prop, r1plain).toString(),
                        read2.getProperty(prop, r2plain).toString());
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
            tradId = db.getNodeById(Long.parseLong(rdg.getProperty("section_id").toString()))
                    .getSingleRelationship(ERelations.PART, Direction.INCOMING)
                    .getStartNode().getProperty("id").toString();
            tx.success();
        }
        return tradId;
    }
}