package net.stemmaweb.rest;

import com.alexmerz.graphviz.ParseException;
import com.qmino.miredot.annotations.MireDotIgnore;
import com.qmino.miredot.annotations.ReturnType;
import net.stemmaweb.exporter.DotExporter;
import net.stemmaweb.exporter.GraphMLExporter;
import net.stemmaweb.exporter.StemmawebExporter;
import net.stemmaweb.exporter.TabularExporter;
import net.stemmaweb.model.*;
import net.stemmaweb.parser.*;
import net.stemmaweb.services.*;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.json.JSONObject;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.*;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.LocalDateTime.now;
import static net.stemmaweb.Util.*;
//import org.neo4j.helpers.collection.IteratorUtil; // Neo4j 2.x


/**
 * Comprises all the api calls related to a tradition.
 * Can be called using http://BASE_URL/tradition
 *
 * @author PSE FS 2015 Team2
 */

public class Tradition {
    private final GraphDatabaseService db;
    /**
     * This is where the tradition ID should go
     */
    private final String traditionId;

    public Tradition(String requestedId) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        traditionId = requestedId;
    }

    /*
     * Delegated API calls
     */

    /**
     * Delegates to {@link net.stemmaweb.rest.Section Section} module
     * @param sectionId - the ID of the requested tradition section
     */
    @Path("/section/{sectionId}")
    public Section getSection(@PathParam("sectionId") String sectionId) {
        ArrayList<SectionModel> tradSections = produceSectionList(VariantGraphService.getTraditionNode(traditionId, db));
        if (tradSections != null)
            for (SectionModel s : tradSections)
                if (s.getId().equals(sectionId))
                    return new Section(traditionId, sectionId);
        return null;
    }

    /**
     * Delegates to {@link net.stemmaweb.rest.Witness Witness} module
     * @param sigil - the sigil of the requested witness
     */
    @Path("/witness/{sigil}")
    public Witness getWitness(@PathParam("sigil") String sigil) {
        return new Witness(traditionId, sigil);
    }

    /**
     * Delegates to {@link net.stemmaweb.rest.Stemma Stemma} module
     * @param name - the name of the requested stemma
     */
    @Path("/stemma/{name}")
    public Stemma getStemma(@PathParam("name") String name) {
        return new Stemma(traditionId, name);
    }

    /**
     * Delegates to {@link net.stemmaweb.rest.Relation Relation} module
     */
    @Path("/relation")
    public Relation getRelation() {
        return new Relation(traditionId);
    }

    /**
     * Delegates to {@link net.stemmaweb.rest.Reading Reading} module, if the reading belongs to this tradition
     */
    @Path("/reading/{id}")
    public Reading getReading(@PathParam("id") String rid) {
        Reading resource = new Reading(rid);
        if (resource.getTraditionId().equals(traditionId))
            return resource;
        // Otherwise return a Reading resource that will produce a 404
        return new Reading("-1");
    }

    /**
     * Delegates to {@link net.stemmaweb.rest.RelationType RelationType} module
     * @param name - the name of the requested RelationType
     */
    @Path("/relationtype/{name}")
    public RelationType getRelationType(@PathParam("name") String name) { return new RelationType(traditionId, name); }

    /**
     * Delegates to {@link net.stemmaweb.rest.AnnotationLabel AnnotationLabel} module
     * @param name - the name of the requested annotation label
     */
    @Path("/annotationlabel/{name}")
    public AnnotationLabel getAnnotationType(@PathParam("name") String name) { return new AnnotationLabel(traditionId, name); }

    /**
     * Delegates to {@link net.stemmaweb.rest.Annotation Annotation} module
     * @param annoid - the ID of the requested annotation
     */
    @Path("/annotation/{annoid}")
    public Annotation getAnnotationOnTradition(@PathParam("annoid") String annoid) {
        return new Annotation(traditionId, annoid);
    }

    /*
     * Resource creation calls
     */

    /**
     * Create / save a new stemma for this tradition.
     *
     * @title Upload a new stemma
     *
     * @param stemmaSpec - the StemmaModel that describes the new stemma
     * @return The stemma specification in JSON format.
     * @statuscode 201 - on success
     * @statuscode 500 - on error, with an error message
     */
    @POST  // a new stemma
    @Path("/stemma")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("application/json; charset=utf-8")
    @ReturnType("net.stemmaweb.model.StemmaModel")
    public Response newStemma(StemmaModel stemmaSpec) {
        // Make sure the tradition exists
        Node traditionNode = VariantGraphService.getTraditionNode(traditionId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity(jsonerror("tradition not found")).build();

        // Make sure the stemma has a name.
        if (stemmaSpec.getIdentifier() == null || stemmaSpec.getIdentifier().equals("")) {
            // Is there a name in the dot spec?
            if (stemmaSpec.getDot() != null) try {
                stemmaSpec.setIdentifier(DotParser.getDotGraphName(stemmaSpec.getDot()));
            } catch (ParseException e) {
                return Response.status(Status.BAD_REQUEST)
                        .entity(jsonerror("Parse error in dot: " + e.getMessage())).build();
            }
            else stemmaSpec.setIdentifier(String.format("New stemma %s", now()));
        }
        Stemma restStemma = new Stemma(traditionId, stemmaSpec.getIdentifier(), true);
        return restStemma.replaceStemma(stemmaSpec);
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
                throw new Exception(
                        String.format("Section list and section node mismatch: %d nodes, %d sections found",
                                depth, sectionList.size()));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return sectionList;
    }

    /**
     * Create a new section for this tradition. Returns the ID of the new section, in the
     * form {@code {"parentId": <ID>}}.
     *
     * @title Upload section
     *
     * @param sectionName - The name of the section
     * @param filetype - The format of the section data file.
     *                 See the documentation of POST /tradition for possible values.
     * @param uploadedInputStream - The section file data
     * @return The stemma specification in JSON format.
     * @statuscode 201 - on success
     * @statuscode 400 - if the file type is unrecognised
     * @statuscode 500 - on error, with an error message
     */

    @POST
    @Path("/section")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("application/json; charset=utf-8")
    @ReturnType("java.lang.Void")
    public Response addSection(@FormDataParam("name") String sectionName,
                               @FormDataParam("filetype") String filetype,
                               @FormDataParam("file") InputStream uploadedInputStream) {

        // Make a new section node to connect to the tradition in question.
        Node traditionNode = VariantGraphService.getTraditionNode(traditionId, db);
        ArrayList<SectionModel> existingSections = produceSectionList(traditionNode);
        Node sectionNode = createNewSection(traditionNode, sectionName);
        if (sectionNode == null)
            return Response.serverError().entity(jsonerror("Error creating new section node on tradition")).build();

        // Dispatch the data for parsing, with the new section node as the parent node
        Response result = parseDispatcher(sectionNode, filetype, uploadedInputStream, true);

        // Handle the result
        if (result.getStatus() > 201) {
            // If the result wasn't a success, delete the section node before returning the result.
            Section restSect = new Section(traditionId, String.valueOf(sectionNode.getId()));
            restSect.deleteSection();
            return result;
        } else {
            // Otherwise, retrieve the section ID for our own response and link this section
            // behind the last of the prior sections
            JSONObject internResponse = new JSONObject((String) result.getEntity());
            if (existingSections != null && existingSections.size() > 0) {
                SectionModel ls = existingSections.get(existingSections.size() - 1);
                try (Transaction tx = db.beginTx()) {
                    Node lastSection = db.getNodeById(Long.parseLong(ls.getId()));
                    lastSection.createRelationshipTo(sectionNode, ERelations.NEXT);
                    tx.success();
                } catch (Exception e) {
                    e.printStackTrace();
                    return Response.serverError().build();
                }
            }
            return Response.status(Status.CREATED).entity(jsonresp("sectionId", internResponse.getLong("parentId"))).build();
        }
    }


    // utility method for creating a new section on a tradition
    private static Node createNewSection(Node traditionNode, String sectionName) {
        Node sectionNode;
        GraphDatabaseService db = traditionNode.getGraphDatabase();
        try (Transaction tx = db.beginTx()) {
            sectionNode = db.createNode(Nodes.SECTION);
            sectionNode.setProperty("name", sectionName);
            traditionNode.createRelationshipTo(sectionNode, ERelations.PART);
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return sectionNode;
    }

    /**
     * A package-private method to add sections to a given tradition, used by POST /tradition and POST /section
     *
     * @param parentNode - either the tradition node or the section node, depending on addSingleSection
     * @param filetype   - indicates which of the supported filetypes we are parsing
     * @param uploadedInputStream - the data to parse
     * @param addSingleSection - whether we are adding a section to an existing tradition, or uploading
     *                          a new tradition entirely
     * @return a Response indicating the result
     */
    static Response parseDispatcher(Node parentNode, String filetype, InputStream uploadedInputStream, boolean addSingleSection) {
        Response result = null;
        // All parsers except GraphML expect a section node; create it here if we are not adding a
        // section to an existing tradition.
        if (!addSingleSection && !filetype.startsWith("graphml")) {
            Node sectionNode = createNewSection(parentNode, "DEFAULT");
            if (sectionNode == null)
                return Response.serverError()
                        .entity(jsonerror("Error creating new section node on tradition")).build();
            parentNode = sectionNode;
        }
        // Parse the contents of the given file into that section
        if (filetype.equals("csv"))
            // Pass it off to the CSV reader
            result = new TabularParser().parseCSV(uploadedInputStream, parentNode, ',');
        if (filetype.equals("tsv"))
            // Pass it off to the CSV reader with tab separators
            result = new TabularParser().parseCSV(uploadedInputStream, parentNode, '\t');
        if (filetype.startsWith("xls"))
            // Pass it off to the Excel reader
            result = new TabularParser().parseExcel(uploadedInputStream, parentNode, filetype);
        if (filetype.equals("teips"))
            // Pass it off to the TEI parser
            result = new TEIParallelSegParser().parseTEIParallelSeg(uploadedInputStream, parentNode);
        // TODO we need to parse TEI double-endpoint attachment from CTE
        if (filetype.equals("collatex"))
            // Pass it off to the CollateX parser
            result = new CollateXParser().parseCollateX(uploadedInputStream, parentNode);
        if (filetype.equals("cxjson"))
            // Pass it off to the CollateX JSON parser
            result = new CollateXJsonParser().parseCollateXJson(uploadedInputStream, parentNode);
        if (filetype.equals("stemmaweb"))
            // Pass it off to the old Stemmaweb-format parser
            result = new StemmawebParser().parseGraphML(uploadedInputStream, parentNode);
        if (filetype.equals("graphmlsingle"))
            // Pass it off to the legacy single-file GraphML parser
            result = new GraphMLParser().parseGraphMLSingle(uploadedInputStream, parentNode, addSingleSection);
        if (filetype.equals("graphml"))
            // Pass it off to the GraphML ZIP parser
            result = new GraphMLParser().parseGraphMLZip(uploadedInputStream, parentNode, addSingleSection);
        // If we got this far, it was an unrecognized filetype.
        if (result == null)
            result = Response.status(Status.BAD_REQUEST).entity(jsonerror("Unrecognized file type " + filetype)).build();

        return result;
    }

    /**
     * Create a new annotation on this tradition.
     * @param am - an AnnotationModel specifying the annotation to create
     * @return the created AnnotationModel
     * @statuscode 201 - on success
     * @statuscode 403 - if the AnnotationModel is invalid
     * @statuscode 404 - if tradition doesn't exist
     * @statuscode 500 - on error
     */

    @POST
    @Path("/annotation")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("application/json; charset=utf-8")
    @ReturnType(clazz = AnnotationModel.class)
    public Response addAnnotation(AnnotationModel am) {
        Node traditionNode = VariantGraphService.getTraditionNode(traditionId, db);
        Response result;
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity(jsonerror("tradition not found")).build();
        try (Transaction tx = db.beginTx()) {
            Node anno = db.createNode();
            traditionNode.createRelationshipTo(anno, ERelations.HAS_ANNOTATION);
            Annotation annoRest = new Annotation(traditionId, String.valueOf(anno.getId()));
            result = annoRest.updateAnnotation(am);
            if (result.getStatus() != Status.OK.getStatusCode())
                // Abort the operation and return the non-OK result
                return result;
            // Otherwise, commit it
            tx.success();
        } catch (Exception e) {
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.status(Status.CREATED).entity(result.getEntity()).build();
    }

    /**
     * Resets ranks across the whole tradition
     *
     * This does not belong to the official API!
     * It is a secret hack to fix ranks if we find they are broken or missing.
     */
    @GET
    @Path("/initRanks")
    @Produces(MediaType.APPLICATION_JSON)
    @MireDotIgnore
    public Response initRanks() {
        Node traditionNode = VariantGraphService.getTraditionNode(traditionId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity(jsonerror("tradition not found")).build();
        List<SectionModel> smlist = produceSectionList(traditionNode);
        if (smlist == null)
            return Response.ok().build();

        try (Transaction tx = db.beginTx()) {
            for (SectionModel sm : smlist) {
                ReadingService.recalculateRank(VariantGraphService.getStartNode(sm.getId(), db), true);
            }
            tx.success();
        } catch (Exception e) {
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(jsonresp("result", "success")).build();

    }

    /*----------------------------*
     * Collection retrieval calls *
     *----------------------------*/

    /**
     * Gets a list of all sections of a tradition with the given id.
     *
     * @title Get sections
     * @return A list of section metadata
     * @statuscode 200 - on success
     * @statuscode 404 - if no such tradition exists
     * @statuscode 500 - on failure, with an error message
     */
    @GET
    @Path("/sections")
    @Produces("application/json; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.SectionModel>")
    public Response getAllSections() {
        Node traditionNode = VariantGraphService.getTraditionNode(traditionId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity(jsonerror("tradition not found")).build();

        ArrayList<SectionModel> sectionList = produceSectionList(traditionNode);
        if (sectionList == null)
            return Response.serverError().entity(jsonerror("Something went wrong building section list")).build();

        return Response.ok(sectionList).build();
    }

    /**
     * Gets a list of all the witnesses of a tradition with the given id.
     *
     * @title Get witnesses
     * @return A list of witness metadata
     * @statuscode 200 - on success
     * @statuscode 404 - if no such tradition exists
     * @statuscode 500 - on failure, with an error message
     */
    @GET
    @Path("/witnesses")
    @Produces("application/json; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.WitnessModel>")
    public Response getAllWitnesses() {
        Node traditionNode = VariantGraphService.getTraditionNode(traditionId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity(jsonerror("tradition not found")).build();

        ArrayList<WitnessModel> witnessList = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            DatabaseService.getRelated(traditionNode, ERelations.HAS_WITNESS)
                    .forEach(r -> witnessList.add(new WitnessModel(r)));
            tx.success();
        } catch (Exception e) {
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        Collections.sort(witnessList);
        return Response.ok(witnessList).build();
    }

    /**
     * Gets a list of all the stemmata associated with this tradition.
     *
     * @title Get stemmata
     * @return A list of section metadata
     * @statuscode 200 - on success
     * @statuscode 404 - if no such tradition exists
     * @statuscode 500 - on failure, with an error message
     */
    @GET
    @Path("/stemmata")
    @Produces("application/json; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.StemmaModel>")
    public Response getAllStemmata() {
        Node traditionNode = VariantGraphService.getTraditionNode(traditionId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity(jsonerror("No such tradition found")).build();

        // find all stemmata associated with this tradition
        ArrayList<StemmaModel> stemmata = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA)
                    .forEach(x -> stemmata.add(new StemmaModel(x)));
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }

        return Response.ok(stemmata).build();
    }

    /**
     * Gets a list of all relationships defined within the given tradition.
     *
     * @title Get relationships
     * @param includeReadings - Include the ReadingModel information for the source and target
     * @return A list of relationship metadata
     * @statuscode 200 - on success
     * @statuscode 404 - if no such tradition exists
     * @statuscode 500 - on failure, with an error message
     */
    @GET
    @Path("/relations")
    @Produces("application/json; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.RelationModel>")
    public Response getAllRelationships(@DefaultValue("false") @QueryParam("include_readings") String includeReadings) {
        ArrayList<RelationModel> relList = new ArrayList<>();
        Node traditionNode = VariantGraphService.getTraditionNode(traditionId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity(jsonerror("tradition not found")).build();
        ArrayList<SectionModel> ourSections = produceSectionList(traditionNode);
        if (ourSections == null)
            return Response.serverError().entity(jsonerror("section lookup failed")).build();
        for (SectionModel s : ourSections) {
            Section sectRest = new Section(traditionId, s.getId());
            ArrayList<RelationModel> sectRels = sectRest.sectionRelations(includeReadings.equals("true"));
            if (sectRels == null)
                return Response.serverError().entity(jsonerror("something went wrong in section relations")).build();
            relList.addAll(sectRels);
        }

       return Response.ok(relList).build();
    }

    /**
     * Gets a list of all relation types defined within the given tradition.
     *
     * @title Get relationships
     * @return A list of relationship metadata
     * @statuscode 200 - on success
     * @statuscode 404 - if no such tradition exists
     * @statuscode 500 - on failure, with an error message
     */
    @GET
    @Path("/relationtypes")
    @Produces("application/json; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.RelationTypeModel>")
    public Response getAllRelationTypes() {
        Node traditionNode = VariantGraphService.getTraditionNode(traditionId, db);
        List<RelationTypeModel> relTypeList;
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity(jsonerror("tradition not found")).build();
        try {
            relTypeList = RelationService.ourRelationTypes(traditionNode);
        } catch (Exception e) {
            return Response.serverError()
                    .entity(jsonerror("relation types could not be collected: " + e.getMessage()))
                    .build();
        }
        return Response.ok(relTypeList).build();
    }

    /**
     * Gets a list of all readings in the given tradition.
     *
     * @title Get readings
     * @return A list of reading metadata
     * @statuscode 200 - on success
     * @statuscode 404 - if no such tradition exists
     * @statuscode 500 - on failure, with an error message
     */
    @GET
    @Path("/readings")
    @Produces("application/json; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.ReadingModel>")
    public Response getAllReadings() {
        Node traditionNode = VariantGraphService.getTraditionNode(traditionId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND)
                    .entity(jsonerror("There is no tradition with this id")).build();

        ArrayList<SectionModel> allSections = produceSectionList(traditionNode);
        if (allSections == null)
            return Response.serverError()
                    .entity(jsonerror("Tradition has no sections")).build();

        ArrayList<ReadingModel> readingModels = new ArrayList<>();
        for (SectionModel sm : allSections) {
            Section sectRest = new Section(traditionId, sm.getId());
            List<ReadingModel> sectionReadings = sectRest.sectionReadings();
            if (sectionReadings == null)
                return Response.serverError().entity(jsonerror("section lookup failed")).build();
            readingModels.addAll(sectionReadings);

        }
        return Response.ok(readingModels).build();
    }

    /**
     * Return a list of the annotations that have been made on this tradition.
     *
     * @title Get annotations on tradition
     *
     * @param filterLabels Return only annotations with the given label. May be specified multiple times.
     * @return a list of AnnotationModels
     * @statuscode 200 - on success
     * @statuscode 400 - if tradition doesn't exist
     * @statuscode 500 - on error
     */
    @GET
    @Path("/annotations")
    @Produces("application/json; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.AnnotationModel>")
    public Response getAllAnnotations(@QueryParam("label") List<String> filterLabels) {
        Node traditionNode = VariantGraphService.getTraditionNode(traditionId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND)
                    .entity(jsonerror("There is no tradition with this id")).build();

        List<AnnotationModel> result;
        try (Transaction tx = db.beginTx()) {
            ArrayList<AnnotationModel> allAnnotations = new ArrayList<>();
            traditionNode.getRelationships(ERelations.HAS_ANNOTATION, Direction.OUTGOING)
                    .forEach(x -> allAnnotations.add(new AnnotationModel(x.getEndNode())));
            if (filterLabels.size() > 0)
                result = allAnnotations.stream().filter(x -> filterLabels.contains(x.getLabel()))
                        .collect(Collectors.toList());
            else
                result = allAnnotations;
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(e.getMessage()).build();
        }
        return Response.ok(result).build();
    }

    /**
     * Return a list of the annotation labels that have been defined for this tradition.
     *
     * @title Get annotation labels for tradition
     *
     * @return a list of AnnotationLabelModels
     * @statuscode 200 - on success
     * @statuscode 400 - if tradition doesn't exist
     * @statuscode 500 - on error
     */
    @GET
    @Path("/annotationlabels")
    @Produces("application/json; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.AnnotationLabelModel>")
    public Response getDefinedAnnotationLabels() {
        Node traditionNode = VariantGraphService.getTraditionNode(traditionId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND)
                    .entity(jsonerror("There is no tradition with this id")).build();

        List<AnnotationLabelModel> result = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            traditionNode.getRelationships(ERelations.HAS_ANNOTATION_TYPE, Direction.OUTGOING)
                    .forEach(x -> result.add(new AnnotationLabelModel(x.getEndNode())));
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(e.getMessage()).build();
        }
        return Response.ok(result).build();
    }

    /**
     * Deletes any annotations on this tradition that lack referents, unless the annotation is marked as "primary".
     * Returns a list of the deleted annotations.
     *
     * @title Clean up dangling annotations
     * @return a list of AnnotationModels representing deleted annotations
     */
    @POST
    @Path("/pruneAnnotations")
    @Produces("application/json; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.AnnotationModel>")
    public Response pruneAnnotations() {
        Node traditionNode = VariantGraphService.getTraditionNode(traditionId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity(jsonerror("No such tradition found")).build();
        List<AnnotationModel> deleted = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            for (Node a : DatabaseService.getRelated(traditionNode, ERelations.HAS_ANNOTATION)) {
                boolean isPrimary = a.getProperty("primary", false).equals(true);
                if (!a.hasRelationship(Direction.OUTGOING) && !isPrimary) {
                    deleted.add(new AnnotationModel(a));
                    a.getRelationships(Direction.INCOMING).forEach(Relationship::delete);
                    a.delete();
                }
            }
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(deleted).build();
    }



    // TODO add method to find identical and mergeable readings across the whole tradition
    /*
     * Base tradition URL calls
     */

    /**
     * Changes the metadata of the tradition.
     *
     * @title Update tradition information
     *
     * @param tradition A JSON specification of the desired tradition metadata.
     * @return The updated tradition information.
     * @statuscode 200 - on success
     * @statuscode 404 - if the tradition or the requested owner does not exist
     * @statuscode 500 - on error, with an error message
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("application/json; charset=utf-8")
    @ReturnType(clazz = TraditionModel.class)
    public Response changeTraditionMetadata(TraditionModel tradition) {
        TraditionModel updatedTradition;
        try (Transaction tx = db.beginTx()) {
            Node traditionNode = db.findNode(Nodes.TRADITION, "id", traditionId);
            if( traditionNode == null ) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(jsonerror("There is no Tradition with this id"))
                        .build();
            }

            if (tradition.getOwner() != null) {
                Node newUser = db.findNode(Nodes.USER, "id", tradition.getOwner());
                if (newUser == null) {
                    return Response.status(Response.Status.NOT_FOUND)
                            .entity(jsonerror("A user with this id does not exist"))
                            .build();
                }
                Relationship oldOwnership = traditionNode.getSingleRelationship(ERelations.OWNS_TRADITION, Direction.INCOMING);
                if (!oldOwnership.getStartNode().getProperty("id").equals(tradition.getOwner())) {
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
            if (!tradition.getDirection().equals("") )
                traditionNode.setProperty("direction", tradition.getDirection());
            // We need to be able to both set and unset this, but not touch it if it isn't specified.
            // Thus, if the value passed is 0 or negative, we unset it entirely.
            Integer swjid = tradition.getStemweb_jobid();
            if (swjid != null ) {
                if (swjid < 1)
                    traditionNode.removeProperty("stemweb_jobid");
                else
                    traditionNode.setProperty("stemweb_jobid", tradition.getStemweb_jobid());
            }
            // Generate the updated model to return it
            updatedTradition = new TraditionModel(traditionNode);
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(updatedTradition).build();
    }

    /**
     * Removes an entire tradition, including all witnesses, stemmata, sections, readings,
     * and relationships.
     *
     * @title Delete tradition
     *
     * @statuscode 200 - on success
     * @statuscode 404 - if tradition does not exist
     * @statuscode 500 - on error, with an error message
     */
    @DELETE
    @ReturnType("java.lang.Void")
    public Response deleteTraditionById() {
        Node foundTradition = VariantGraphService.getTraditionNode(traditionId, db);
        if (foundTradition != null) {
            try (Transaction tx = db.beginTx()) {
                /*
                 * Find all the nodes and relations to remove
                 */
                Set<Relationship> removableRelations = new HashSet<>();
                Set<Node> removableNodes = new HashSet<>();
                VariantGraphService.returnEntireTradition(foundTradition)
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
                e.printStackTrace();
                return Response.serverError().entity(jsonerror(e.getMessage())).build();
            }
        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(jsonerror("A tradition with this id was not found!"))
                    .build();
        }

        return Response.ok().build();
    }

    /*
     * Tradition export API
     *
     */

    /**
     * Returns the stored information (metadata) of a tradition.
     * @title Get tradition information
     * @return A JSON structure containing the tradition's metadata
     * @statuscode 200 - on success
     * @statuscode 404 - if tradition does not exist
     * @statuscode 500 - on error, with an error message
     */
    @GET
    @Produces("application/json; charset=utf-8")
    @ReturnType(clazz = TraditionModel.class)
    public Response getTraditionInfo() {
        Node traditionNode = VariantGraphService.getTraditionNode(traditionId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity(jsonerror("No such tradition found")).build();

        TraditionModel metadata = new TraditionModel(traditionNode);
        return Response.ok(metadata).build();
    }

    /**
     * Returns a GraphML file that describes the specified tradition and its data.
     * @title Download GraphML
     *
     * @return XML data
     */
    @GET
    @Path("/graphml")
    @Produces("application/zip")
    @ReturnType("java.lang.Void")
    public Response getGraphML() {
        if (VariantGraphService.getTraditionNode(traditionId, db) == null)
            return Response.status(Status.NOT_FOUND).type(MediaType.TEXT_PLAIN).entity("No such tradition found").build();
        GraphMLExporter exporter = new GraphMLExporter();
        return exporter.writeNeo4J(traditionId, null);
    }

    /**
     * Returns a legacy Stemmaweb-compatible GraphML file that describes the specified tradition and its data.
     * @title Download legacy GraphML
     *
     * @return XML data
     */
    @GET
    @Path("/stemmaweb")
    @Produces(MediaType.APPLICATION_XML)
    @ReturnType("java.lang.String")
    public Response getGraphMLStemmaweb() {
        if (VariantGraphService.getTraditionNode(traditionId, db) == null)
            return Response.status(Status.NOT_FOUND).type(MediaType.TEXT_PLAIN).entity("No such tradition found").build();
        StemmawebExporter parser = new StemmawebExporter();
        return parser.writeNeo4J(traditionId);
    }

    /**
     * Returns a GraphViz dot file that describes the specified tradition and its data.
     *
     * @title Download GraphViz
     *
     * @param includeRelatedRelationships - Include RELATED edges in the dot, if true
     * @param showNormalForms - Display normal form of readings alongside "raw" text form, if true
     * @param showRank - Display the rank of readings, if true
     * @param displayAllSigla - Avoid the 'majority' contraction of long witness labels, if true
     * @param normalise - A RelationType name to normalise on, if desired
     * @param excWitnesses - Exclude the given witness from the dot output. Can be specified multiple times
     * @return Plaintext dot format
     */
    @GET
    @Path("/dot")
    @Produces("text/plain; charset=utf-8")
    @ReturnType("java.lang.String")
    public Response getDot(@DefaultValue("false") @QueryParam("include_relations") Boolean includeRelatedRelationships,
                           @DefaultValue("false") @QueryParam("show_normal") Boolean showNormalForms,
                           @DefaultValue("false") @QueryParam("show_rank") Boolean showRank,
                           @DefaultValue("false") @QueryParam("expand_sigla") Boolean displayAllSigla,
                                                  @QueryParam("normalise") String normalise,
                                                  @QueryParam("include_witness") List<String> excWitnesses) {
        if (VariantGraphService.getTraditionNode(traditionId, db) == null)
            return Response.status(Status.NOT_FOUND).entity("No such tradition found").build();

        // Put our options into an object
        DisplayOptionModel dm = new DisplayOptionModel(
                includeRelatedRelationships, showNormalForms, showRank, displayAllSigla, normalise, excWitnesses);
        DotExporter exporter = new DotExporter(db);
        return exporter.writeNeo4J(traditionId, dm);
    }

    /**
     * Returns a JSON file that contains the aligned reading data for the tradition.
     *
     * @title Download JSON alignment
     *
     * @param toConflate    - Zero or more relationship types whose readings should be treated as identical
     * @param sectionList   - Restrict the output to include the given sections. Can be specified multiple times.
     * @param excludeLayers - If "true", exclude witness layers from the output.
     * @return the JSON alignment
     */
    @GET
    @Path("/json")
    @Produces("application/json; charset=utf-8")
    @ReturnType(clazz = AlignmentModel.class)
    public Response getJson(@QueryParam("conflate") String toConflate,
                            @QueryParam("section") List<String> sectionList,
                            @QueryParam("exclude_layers") String excludeLayers) {
        return new TabularExporter(db).exportAsJSON(traditionId, toConflate,
                sectionList, "true".equals(excludeLayers));
    }

    /**
     * Returns a CSV file that contains the aligned reading data for the tradition.
     *
     * @title Download CSV alignment
     *
     * @param toConflate   - Zero or more relationship types whose readings should be treated as identical
     * @param sectionList - Restrict the output to include the given sections. Can be specified multiple times.
     * @param excludeLayers - If "true", exclude witness layers from the output.
     * @return the CSV alignment as plaintext
     */
    @GET
    @Path("/csv")
    @Produces("text/plain; charset=utf-8")
    @ReturnType("java.lang.String")
    public Response getCsv(@QueryParam("conflate") String toConflate,
                           @QueryParam("section") List<String> sectionList,
                           @QueryParam("exclude_layers") String excludeLayers) {
        return new TabularExporter(db).exportAsCSV(traditionId, ',', toConflate,
                sectionList, "true".equals(excludeLayers));
    }

    /**
     * Returns a tab-separated values (TSV) file that contains the aligned reading data for the tradition.
     *
     * @title Download TSV alignment
     *
     * @param toConflate   - Zero or more relationship types whose readings should be treated as identical
     * @param sectionList - Restrict the output to include the given sections. Can be specified multiple times.
     * @param excludeLayers - If "true", exclude witness layers from the output.
     * @return the TSV alignment as plaintext
     */
    @GET
    @Path("/tsv")
    @Produces("text/plain; charset=utf-8")
    @ReturnType("java.lang.String")
    public Response getTsv(@QueryParam("conflate") String toConflate,
                           @QueryParam("section") List<String> sectionList,
                           @QueryParam("exclude_layers") String excludeLayers) {
        return new TabularExporter(db).exportAsCSV(traditionId, '\t', toConflate,
                sectionList, "true".equals(excludeLayers));
    }

    /**
     * Returns a character matrix suitable for use with e.g. Phylip Pars.
     *
     * @title Download character matrix for parsimony analysis
     *
     * @param toConflate   - Zero or more relationship types whose readings should be treated as identical
     * @param sectionList - Restrict the output to include the given sections. Can be specified multiple times.
     * @param excludeLayers - If "true", exclude witness layers from the output.
     * @param maxVars      - Maximum number of variants per location, above which that location will be discarded.
     *                       Default is 8, for compatibility with Phylip Pars.
     * @return the character matrix as plaintext
     */
    @GET
    @Path("/matrix")
    @Produces("text/plain; charset=utf-8")
    @ReturnType("java.lang.String")
    public Response getCharMatrix(@QueryParam("conflate") String toConflate,
                                  @QueryParam("section") List<String> sectionList,
                                  @QueryParam("exclude_layers") String excludeLayers,
                                  @DefaultValue("8") @QueryParam("maxVars") int maxVars) {
        return new TabularExporter(db).exportAsCharMatrix(traditionId, maxVars, toConflate,
                sectionList, "true".equals(excludeLayers));
    }

}

