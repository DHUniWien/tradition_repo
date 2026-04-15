package net.stemmaweb.rest;

import static java.time.LocalDateTime.now;
import static net.stemmaweb.Util.jsonerror;
import static net.stemmaweb.Util.jsonresp;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.stemmaweb.services.*;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.json.JSONObject;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.Uniqueness;

import com.alexmerz.graphviz.ParseException;
import com.qmino.miredot.annotations.MireDotIgnore;
import com.qmino.miredot.annotations.ReturnType;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import net.stemmaweb.exporter.DotExporter;
import net.stemmaweb.exporter.GraphMLExporter;
import net.stemmaweb.exporter.StemmawebExporter;
import net.stemmaweb.exporter.TEIExporter;
import net.stemmaweb.exporter.TabularExporter;
import net.stemmaweb.model.AlignmentModel;
import net.stemmaweb.model.AnnotationLabelModel;
import net.stemmaweb.model.AnnotationModel;
import net.stemmaweb.model.DisplayOptionModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationModel;
import net.stemmaweb.model.RelationTypeModel;
import net.stemmaweb.model.SectionModel;
import net.stemmaweb.model.StemmaModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.parser.CollateXJsonParser;
import net.stemmaweb.parser.CollateXParser;
import net.stemmaweb.parser.DotParser;
import net.stemmaweb.parser.GraphMLParser;
import net.stemmaweb.parser.StemmawebParser;
import net.stemmaweb.parser.TEIParallelSegParser;
import net.stemmaweb.parser.TabularParser;

//import org.neo4j.helpers.collection.IteratorUtil; // Neo4j 2.x


/**
 * Comprises all the api calls related to a tradition.
 * Can be called using {@code http://BASE_URL/tradition}
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

    // LATER move all "belonging" checks to here maybe?
    /**
     * Delegates to {@link net.stemmaweb.rest.Section Section} module
     * @param sectionId - the ID of the requested tradition section
     */
    @Path("/section/{sectionId}")
    public Section getSection(@PathParam("sectionId") String sectionId) {
    	return new Section(traditionId, sectionId);
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
        boolean readingInTradition = false;
        try (Transaction tx = db.beginTx()) {
            if (ReadingService.getTraditionId(tx, rid).equals(traditionId))
                readingInTradition = true;
        }
        if (readingInTradition) return new Reading(rid, traditionId);
        // Otherwise return a Reading resource that will produce a 404
        return new Reading("-1");
    }

    /**
     * Delegates to {@link net.stemmaweb.rest.RelationType RelationType} module
     * @param name - the name of the requested RelationType
     */
    @Path("/relationtype/{name}")
    public RelationType getRelationType(@PathParam("name") String name) {
        return new RelationType(traditionId, name);
    }

    /**
     * Delegates to {@link net.stemmaweb.rest.AnnotationLabel AnnotationLabel} module
     * @param name - the name of the requested annotation label
     */
    @Path("/annotationlabel/{name}")
    public AnnotationLabel getAnnotationType(@PathParam("name") String name) {
        return new AnnotationLabel(traditionId, name);
    }

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
    	Node traditionNode;
    	try (Transaction tx = db.beginTx()) {
    		traditionNode = VariantGraphService.getTraditionNode(tx, traditionId);
            tx.commit();
        }

        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity(jsonerror("tradition not found")).build();

        // Make sure the stemma has a name.
        if (stemmaSpec.getIdentifier() == null || stemmaSpec.getIdentifier().isEmpty()) {
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

    private ArrayList<SectionModel> produceSectionList (Node traditionNode, Transaction tx) throws Exception {
        ArrayList<SectionModel> sectionList = new ArrayList<>();
    	traditionNode = tx.getNodeByElementId(traditionNode.getElementId());
        ArrayList<Node> sectionNodes = DatabaseService.getRelated(traditionNode, ERelations.PART);
        int depth = sectionNodes.size();
        if (depth > 0) {
            for(Node n: sectionNodes) {
                if (n.getRelationships(Direction.INCOMING, ERelations.NEXT).stream().findAny().isEmpty()) {
                    tx.traversalDescription()
                            .depthFirst()
                            .relationships(ERelations.NEXT, Direction.OUTGOING)
                            .evaluator(Evaluators.toDepth(depth))
                            .uniqueness(Uniqueness.NODE_GLOBAL)
                            .traverse(n)
                            .nodes()
                            .forEach(r -> sectionList.add(new SectionModel(tx, r)));
                    break;
                }
            }
        }
        if (sectionList.size() != depth) {
            throw new Exception(
                    String.format("Section list and section node mismatch: %d nodes, %d sections found",
                            depth, sectionList.size()));
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

        // Dispatch the data for parsing. This will create one or more new section nodes.
        // A successful response entity returned here looks like {"parentId": 123456} where the parentId
        // is the ID of the first new section created.
        try (Transaction tx = db.beginTx()) {
        	// Get the existing section list
        	Node traditionNode = VariantGraphService.getTraditionNode(tx, traditionId);
        	ArrayList<SectionModel> existingSections = produceSectionList(traditionNode, tx);
        	
        	Response result = this.parseDispatcher(sectionName, filetype, uploadedInputStream, true, tx);
        	
        	// Handle the result
        	if (result.getStatus() == Status.CREATED.getStatusCode()) {
        		// If we created a section, retrieve the section ID for our own response and link this section
        		// behind the last of the prior sections
        		JSONObject internResponse = new JSONObject((String) result.getEntity());
        		String newSectionId = internResponse.getString("parentId");
        		if (!existingSections.isEmpty()) {
        			SectionModel ls = existingSections.getLast();
        			Node lastSection = tx.getNodeByElementId(ls.getId());
        			Node thisSection = tx.getNodeByElementId(newSectionId);
        			lastSection.createRelationshipTo(thisSection, ERelations.NEXT);
        		}
        		tx.commit();
        		return Response.status(Status.CREATED).entity(jsonresp("sectionId", "" + internResponse.get("parentId"))).build();
        	} else {
        		return result;
        	}
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(jsonerror(e.getMessage())).build();
        } catch(Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(jsonerror("Tradition could not be imported!"))
                    .build();
        }
    }


    // utility method for creating a new section on a tradition
    private static Node createNewSection(String traditionNodeId, String sectionName, Transaction tx) {
    	Node sectionNode = tx.createNode(Nodes.SECTION);
        Node traditionNode = tx.getNodeByElementId(traditionNodeId);
        sectionNode.setProperty("name", sectionName);
        traditionNode.createRelationshipTo(sectionNode, ERelations.PART);
        return sectionNode;
    }

    /**
     * A package-private method to add sections to a given tradition, used by POST /tradition and POST /section
     *
     * @param sectionName - the name to be given to the new section created. Will be overridden for GraphML parsing
     * @param filetype    - indicates which of the supported filetypes we are parsing
     * @param uploadedInputStream - the data to parse
     * @param addToExisting - whether we are adding a section to an existing tradition, or uploading
     *                          a new tradition entirely
     * @return a Response indicating the result
     */
    protected Response parseDispatcher(String sectionName, String filetype, InputStream uploadedInputStream,
                             boolean addToExisting, Transaction tx) {
        Response result = null;
        Node traditionNode = VariantGraphService.getTraditionNode(tx, traditionId);
        Node sectionNode = null;
        // If we are adding a section to an existing tradition, or we are parsing anything except
        // GraphML, we have to start by creating the section node
        if (!filetype.startsWith("graphml") || addToExisting)
            sectionNode = createNewSection(traditionNode.getElementId(), sectionName, tx);

        // Parse the contents of the given file into that section
        if (filetype.equals("csv"))
            // Pass it off to the CSV reader
            result = new TabularParser(tx).parseCSV(uploadedInputStream, sectionNode, ',');
        if (filetype.equals("ssv"))
            // Pass it off to the CSV reader
            result = new TabularParser(tx).parseCSV(uploadedInputStream, sectionNode, ';');
        if (filetype.equals("tsv"))
            // Pass it off to the CSV reader with tab separators
            result = new TabularParser(tx).parseCSV(uploadedInputStream, sectionNode, '\t');
        if (filetype.startsWith("xls"))
            // Pass it off to the Excel reader
            result = new TabularParser(tx).parseExcel(uploadedInputStream, sectionNode, filetype);
        if (filetype.equals("teips"))
            // Pass it off to the TEI parser
            result = new TEIParallelSegParser(tx).parseTEIParallelSeg(uploadedInputStream, sectionNode);
        // TODO we need to parse TEI double-endpoint attachment from CTE
        if (filetype.equals("collatex"))
            // Pass it off to the CollateX parser
            result = new CollateXParser(tx).parseCollateX(uploadedInputStream, sectionNode);
        if (filetype.equals("cxjson"))
            // Pass it off to the CollateX JSON parser
            result = new CollateXJsonParser(tx).parseCollateXJson(uploadedInputStream, sectionNode);
        if (filetype.equals("stemmaweb"))
            // Pass it off to the old Stemmaweb-format parser
            result = new StemmawebParser(tx).parseGraphML(uploadedInputStream, sectionNode);
        if (filetype.equals("graphmlsingle"))
            // Pass it off to the legacy single-file GraphML parser
            result = new GraphMLParser(tx).parseGraphMLSingle(uploadedInputStream,
                    addToExisting ? sectionNode : traditionNode,
                    addToExisting);
        if (filetype.equals("graphml"))
            // Pass it off to the GraphML ZIP parser
            result = new GraphMLParser(tx).parseGraphMLZip(uploadedInputStream,
                    addToExisting ? sectionNode : traditionNode,
                    addToExisting);
        // If we got this far, it was an unrecognized filetype.
        if (result == null)
            result = Response.status(Status.BAD_REQUEST).entity(jsonerror("Unrecognized file type " + filetype)).build();
        if (sectionNode != null && !addToExisting) {
            // We created a section with the name DEFAULT at the beginning. If that is still the name,
            // change it to the tradition name
        	String currentName = sectionNode.getProperty("name", "DEFAULT").toString();
        	String tradName = traditionNode.getProperty("name", "DEFAULT").toString();
        	if (currentName.equals("DEFAULT")) {
        		sectionNode.setProperty("name", tradName);
        	}
        }

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
    	try (Transaction tx = db.beginTx()) {
    		Node traditionNode = VariantGraphService.getTraditionNode(tx, traditionId);
            if (traditionNode == null)
                return Response.status(Status.NOT_FOUND).entity(jsonerror("tradition not found")).build();
            traditionNode = VariantGraphService.getTraditionNode(tx, traditionId);
            Node anno = tx.createNode();
            traditionNode.createRelationshipTo(anno, ERelations.HAS_ANNOTATION);
            AnnotationModel result = AnnotationService.updateAnnotation(tx, traditionNode, anno, am);
            tx.commit();
            return Response.status(Status.CREATED).entity(result).build();
    	} catch (Exception e) {
    		return Response.serverError().entity(jsonerror(e.getMessage())).build();
    	}
    }

    /**
     * Resets ranks across the whole tradition
     * This does not belong to the official API!
     * It is a secret hack to fix ranks if we find they are broken or missing.
     */
    @GET
    @Path("/initRanks")
    @Produces(MediaType.APPLICATION_JSON)
    @MireDotIgnore
    public Response initRanks() {
        try (Transaction tx = db.beginTx()) {
            Node traditionNode = VariantGraphService.getTraditionNode(tx, traditionId);
            if (traditionNode == null)
                return Response.status(Status.NOT_FOUND).entity(jsonerror("tradition not found")).build();
            List<SectionModel> smlist = produceSectionList(traditionNode, tx);
            if (smlist.isEmpty())
                return Response.ok().build();
            for (SectionModel sm : smlist) {
                ReadingService.recalculateRank(tx, VariantGraphService.getStartNode(tx, sm.getId()), true);
            }
            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
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
        try (Transaction tx = db.beginTx()) {
            Node traditionNode = VariantGraphService.getTraditionNode(tx, traditionId);
            if (traditionNode == null)
                return Response.status(Status.NOT_FOUND).entity(jsonerror("tradition not found")).build();

            ArrayList<SectionModel> sectionList = produceSectionList(traditionNode, tx);
            return Response.ok(sectionList).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
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
    	try (Transaction tx = db.beginTx()) {
    		Node traditionNode = VariantGraphService.getTraditionNode(tx, traditionId);
            if (traditionNode == null)
                return Response.status(Status.NOT_FOUND).entity(jsonerror("tradition not found")).build();

            ArrayList<WitnessModel> witnessList = new ArrayList<>();
            DatabaseService.getRelated(traditionNode, ERelations.HAS_WITNESS)
                    .forEach(r -> witnessList.add(new WitnessModel(r)));
            Collections.sort(witnessList);
            return Response.ok(witnessList).build();
        } catch (Exception e) {
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
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
    	try (Transaction tx = db.beginTx()) {
    		Node traditionNode = VariantGraphService.getTraditionNode(tx, traditionId);
            if (traditionNode == null)
                return Response.status(Status.NOT_FOUND).entity(jsonerror("No such tradition found")).build();

            // find all stemmata associated with this tradition
            ArrayList<StemmaModel> stemmata = new ArrayList<>();
            DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA)
                    .forEach(x -> stemmata.add(new StemmaModel(tx, x)));
            return Response.ok(stemmata).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
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
        try (Transaction tx = db.beginTx()) {
            Node traditionNode = VariantGraphService.getTraditionNode(tx, traditionId);
            if (traditionNode == null)
                return Response.status(Status.NOT_FOUND).entity(jsonerror("tradition not found")).build();
            ArrayList<SectionModel> ourSections = produceSectionList(traditionNode, tx);
            for (SectionModel s : ourSections) {
                ArrayList<RelationModel> sectRels = VariantGraphService.sectionRelations(tx, s.getId(), includeReadings.equals("true"));
                relList.addAll(sectRels);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
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
    	try (Transaction tx = db.beginTx()) {
    		Node traditionNode = VariantGraphService.getTraditionNode(tx, traditionId);
           if (traditionNode == null)
               return Response.status(Status.NOT_FOUND).entity(jsonerror("tradition not found")).build();

            List<RelationTypeModel> relTypeList = RelationService.ourRelationTypes(tx, traditionNode);
            return Response.ok(relTypeList).build();
        } catch (Exception e) {
            return Response.serverError()
                    .entity(jsonerror("relation types could not be collected: " + e.getMessage()))
                    .build();
        }
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
        try (Transaction tx = db.beginTx()) {
            Node traditionNode = VariantGraphService.getTraditionNode(tx, traditionId);
            if (traditionNode == null)
                return Response.status(Status.NOT_FOUND)
                        .entity(jsonerror("There is no tradition with this id")).build();

            ArrayList<SectionModel> allSections = produceSectionList(traditionNode, tx);
            if (allSections.isEmpty())
                return Response.serverError()
                        .entity(jsonerror("Tradition has no sections")).build();

            ArrayList<ReadingModel> readingModels = new ArrayList<>();
            for (SectionModel sm : allSections) {
                List<ReadingModel> sectionReadings = VariantGraphService.sectionReadings(tx, sm.getId());
                if (sectionReadings.isEmpty())
                    return Response.serverError().entity(jsonerror("Section has no readings")).build();

                readingModels.addAll(sectionReadings);
            }
            return Response.ok(readingModels).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
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
    	try (Transaction tx = db.beginTx()) {
    		Node traditionNode = VariantGraphService.getTraditionNode(tx, traditionId);
            if (traditionNode == null)
                return Response.status(Status.NOT_FOUND)
                        .entity(jsonerror("There is no tradition with this id")).build();

            List<AnnotationModel> result;
            ArrayList<AnnotationModel> allAnnotations = new ArrayList<>();
            traditionNode.getRelationships(Direction.OUTGOING, ERelations.HAS_ANNOTATION)
                    .forEach(x -> allAnnotations.add(new AnnotationModel(x.getEndNode())));
            if (!filterLabels.isEmpty())
                result = allAnnotations.stream().filter(x -> filterLabels.contains(x.getLabel()))
                        .collect(Collectors.toList());
            else
                result = allAnnotations;
            return Response.ok(result).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(e.getMessage()).build();
        }

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
    	try (Transaction tx = db.beginTx()) {
    		Node traditionNode = VariantGraphService.getTraditionNode(tx, traditionId);
            if (traditionNode == null)
                return Response.status(Status.NOT_FOUND)
                        .entity(jsonerror("There is no tradition with this id")).build();

            List<AnnotationLabelModel> result = new ArrayList<>();
            traditionNode.getRelationships(Direction.OUTGOING, ERelations.HAS_ANNOTATION_TYPE)
                    .forEach(x -> result.add(new AnnotationLabelModel(x.getEndNode())));
            return Response.ok(result).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(e.getMessage()).build();
        }

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
    	try (Transaction tx = db.beginTx()) {
    		Node traditionNode = VariantGraphService.getTraditionNode(tx, traditionId);
            if (traditionNode == null)
                return Response.status(Status.NOT_FOUND).entity(jsonerror("No such tradition found")).build();
            List<AnnotationModel> deleted = AnnotationService.pruneAnnotations(traditionNode);
            tx.commit();
            return Response.ok(deleted).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }

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
            Node traditionNode = tx.findNode(Nodes.TRADITION, "id", traditionId);
            if( traditionNode == null ) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(jsonerror("There is no Tradition with this id"))
                        .build();
            }

            if (tradition.getOwner() != null) {
                Node newUser = tx.findNode(Nodes.USER, "id", tradition.getOwner());
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
            if (!tradition.getDirection().isEmpty() )
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
            tx.commit();
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
        try (Transaction tx = db.beginTx()) {
        	Node foundTradition = VariantGraphService.getTraditionNode(tx, traditionId);
        	if (foundTradition != null) {
                /*
                 * Find all the nodes and relations to remove
                 */
                Set<Relationship> removableRelations = new HashSet<>();
                Set<Node> removableNodes = new HashSet<>();
                VariantGraphService.returnEntireTradition(tx, foundTradition)
                        .nodes().forEach(x -> {
                    x.getRelationships().forEach(removableRelations::add);
                    removableNodes.add(x);
                });

                /*
                 * Remove the nodes and relations
                 */
                removableRelations.forEach(Relationship::delete);
                removableNodes.forEach(Node::delete);
                tx.commit();
            } else {
                return Response.status(Response.Status.NOT_FOUND)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(jsonerror("A tradition with this id was not found!"))
                        .build();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
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
    	try (Transaction tx = db.beginTx()) {
    		Node traditionNode = VariantGraphService.getTraditionNode(tx, traditionId);
            if (traditionNode == null)
                return Response.status(Status.NOT_FOUND).entity(jsonerror("No such tradition found")).build();

            TraditionModel metadata = new TraditionModel(traditionNode);
            return Response.ok(metadata).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
    }

    /**
     * Returns a TEI double-endpoint-attachment file representing the section text.
     *
     * @title Download character matrix for parsimony analysis
     * @param significant   - Zero or more relationship types whose readings should be treated as identical
     * @param excludeType1  - If "true", exclude type-1 (singleton) variants
     * @param excludeNonsense - If "true", suppress any variants marked with the is_nonsense property
     * @param combine - If "true", move dislocated (e.g. transposed) variants to their matching base
     * @param suppressMatching - A regular expression; all variants matching this will be suppressed in
     *                         the apparatus. Default is to suppress all punctuation.
     * @param baseWitness - A witness sigil, or the string "majority" or "lemma", to indicate what text to
     *                    use as the base text in the apparatus.
     * @param conflate - A relation type to normalize on
     * @param excWitnesses - A witness to exclude from the apparatus. Can be specified multiple times.
     * @return the character matrix as plaintext
     */
    @GET
    @Produces("application/xml; charset=utf-8")
    @Path("/tei")
    public Response getTei(@DefaultValue("no") @QueryParam("significant") String significant,
                           @DefaultValue("no") @QueryParam("exclude_type1") String excludeType1,
                           @DefaultValue("no") @QueryParam("exclude_nonsense") String excludeNonsense,
                           @DefaultValue("no") @QueryParam("combine_dislocations") String combine,
                           @DefaultValue("punct") @QueryParam("suppress_matching") String suppressMatching,
                           @QueryParam("base_witness") String baseWitness,
                           @QueryParam("normalize") String conflate,
                           @QueryParam("exclude_witness") List<String> excWitnesses) {
    	try (Transaction tx = db.beginTx()) {
    		Node traditionNode = VariantGraphService.getTraditionNode(tx, traditionId);
            if (traditionNode == null)
                return Response.status(Status.NOT_FOUND).entity(jsonerror("No such tradition found")).build();

            TEIExporter exp = new TEIExporter();
            return exp.writeTEI(traditionId, null, null, baseWitness, excWitnesses, conflate,
                    suppressMatching, Boolean.getBoolean(excludeNonsense), Boolean.getBoolean(excludeType1),
                    significant, Boolean.getBoolean(combine), tx);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }
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
    	try (Transaction tx = db.beginTx()) {
            Node traditionNode = VariantGraphService.getTraditionNode(tx, traditionId);

            if (traditionNode == null)
                return Response.status(Status.NOT_FOUND).type(MediaType.TEXT_PLAIN).entity("No such tradition found").build();

            GraphMLExporter exporter = new GraphMLExporter(tx);
            return exporter.writeNeo4J(traditionId, null);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }
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
    	try (Transaction tx = db.beginTx()) {
    		Node traditionNode = VariantGraphService.getTraditionNode(tx, traditionId);
            if (traditionNode == null) {
                return Response.status(Status.NOT_FOUND).type(MediaType.TEXT_PLAIN).entity("No such tradition found").build();
            }
            StemmawebExporter parser = new StemmawebExporter();
            return parser.writeNeo4J(traditionId);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }

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
    	try (Transaction tx = db.beginTx()) {
    		Node traditionNode = VariantGraphService.getTraditionNode(tx, traditionId);
            if (traditionNode == null)
                return Response.status(Status.NOT_FOUND).entity("No such tradition found").build();
            // Put our options into an object
            DisplayOptionModel dm = new DisplayOptionModel(
                    includeRelatedRelationships, showNormalForms, showRank, displayAllSigla, normalise, excWitnesses);
            DotExporter exporter = new DotExporter(tx);
            return exporter.writeNeo4J(traditionId, dm);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }


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

        try (Transaction tx = db.beginTx()) {
        	return new TabularExporter(tx).exportAsJSON(traditionId, toConflate,
        			sectionList, "true".equals(excludeLayers));
        } catch (Exception e) {
        	e.printStackTrace();
        	return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
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
        try (Transaction tx = db.beginTx()) {
        	return new TabularExporter(tx).exportAsCSV(traditionId, ',', toConflate,
        			sectionList, "true".equals(excludeLayers));
        } catch (Exception e) {
        	e.printStackTrace();
        	return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
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
        try (Transaction tx = db.beginTx()) {
        	return new TabularExporter(tx).exportAsCSV(traditionId, '\t', toConflate,
        			sectionList, "true".equals(excludeLayers));
        } catch (Exception e) {
        	e.printStackTrace();
        	return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
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
        try (Transaction tx = db.beginTx()) {
            return new TabularExporter(tx).exportAsCharMatrix(traditionId, maxVars, toConflate,
                    sectionList, "true".equals(excludeLayers));
        } catch (Exception e) {
        	e.printStackTrace();
        	return  Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
    }

}

