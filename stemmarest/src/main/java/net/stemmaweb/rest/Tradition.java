package net.stemmaweb.rest;

import com.sun.jersey.multipart.FormDataParam;
import net.stemmaweb.exporter.DotExporter;
import net.stemmaweb.exporter.GraphMLExporter;
import net.stemmaweb.exporter.StemmawebExporter;
import net.stemmaweb.exporter.TabularExporter;
import net.stemmaweb.model.*;
import net.stemmaweb.parser.*;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import org.codehaus.jettison.json.JSONObject;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.*;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.InputStream;
import java.util.*;

import static net.stemmaweb.rest.Util.jsonerror;
//import org.neo4j.helpers.collection.IteratorUtil; // Neo4j 2.x


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
    public Section getSection(@PathParam("sectionId") String sectionId) {
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
                return Response.serverError().entity(jsonerror("Error reading JSON response on creation")).build();
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

    @POST
    @Path("/section")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response addSection(@FormDataParam("name") String sectionName,
                               @FormDataParam("filetype") String filetype,
                               @FormDataParam("file") InputStream uploadedInputStream) {
        // Make a new section node to connect to the tradition in question. But if we are
        // parsing our own GraphML, the section node(s) should be created according to the
        // XML data therein, and not here.
        Node traditionNode = DatabaseService.getTraditionNode(traditionId, db);
        ArrayList<SectionModel> existingSections = produceSectionList(traditionNode);
        Node sectionNode = traditionNode;
        if (!filetype.equals("graphml")) {
            try (Transaction tx = db.beginTx()) {
                sectionNode = db.createNode(Nodes.SECTION);
                sectionNode.setProperty("name", sectionName);
                traditionNode.createRelationshipTo(sectionNode, ERelations.PART);
                tx.success();
            }
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
        if (filetype.equals("cxjson"))
            result = new CollateXJsonParser().parseCollateXJson(uploadedInputStream, sectionNode);
        if (filetype.equals("stemmaweb"))
            // Pass it off to the somewhat legacy GraphML parser
            result = new StemmawebParser().parseGraphML(uploadedInputStream, sectionNode);
        if (filetype.equals("graphml"))
            result = new GraphMLParser().parseGraphML(uploadedInputStream, traditionNode);
        // If we got this far, it was an unrecognized filetype.
        if (result == null)
            result = Response.status(Status.BAD_REQUEST).entity(jsonerror("Unrecognized file type " + filetype)).build();

        if (result.getStatus() > 201) {
            // If the result wasn't a success, delete the section node before returning the result.
            Section restSect = new Section(traditionId, String.valueOf(sectionNode.getId()));
            restSect.deleteSection();
        } else if (!filetype.equals("graphml")){
            // Otherwise, if we haven't already, link this section behind the last of the prior sections.
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
     * Initializes ranks in sections where readings have no rank-property
     *
     * This does not belong to the official API!
     * It is just a hack to initialize sections where their readings have
     * no "rank" defined
     *
     * TODO do we need this or can we use the usual rank calculation?
     */
    @GET
    @Path("/initRanks")
    @Produces(MediaType.APPLICATION_JSON)
    public Response initRanks() {
        Node traditionNode = DatabaseService.getTraditionNode(traditionId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity(jsonerror("tradition not found")).build();

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
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
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
            return Response.status(Status.NOT_FOUND).entity(jsonerror("tradition not found")).build();

        ArrayList<SectionModel> sectionList = produceSectionList(traditionNode);
        if (sectionList == null)
            return Response.serverError().entity(jsonerror("Something went wrong building section list")).build();

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
            return Response.status(Status.NOT_FOUND).entity(jsonerror("tradition not found")).build();

        ArrayList<WitnessModel> witnessList = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            DatabaseService.getRelated(traditionNode, ERelations.HAS_WITNESS)
                    .forEach(r -> witnessList.add(new WitnessModel(r)));
            tx.success();
        } catch (Exception e) {
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
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
     * Gets a list of all relationships of a tradition with the given id.
     *
     * @return Http Response 200 and a list of relationship model in JSON
     */
    @GET
    @Path("/relationships")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response getAllRelationships() {
        ArrayList<RelationshipModel> relList = new ArrayList<>();
        Node traditionNode = DatabaseService.getTraditionNode(traditionId, db);
        if (traditionNode == null)
            return Response.status(Status.NOT_FOUND).entity(jsonerror("tradition not found")).build();
        ArrayList<SectionModel> ourSections = produceSectionList(traditionNode);
        if (ourSections == null)
            return Response.serverError().entity(jsonerror("section lookup failed")).build();
        for (SectionModel s : ourSections) {
            Section sectRest = new Section(traditionId, s.getId());
            ArrayList<RelationshipModel> sectRels = sectRest.sectionRelationships();
            if (sectRels == null)
                return Response.serverError().entity(jsonerror("something went wrong in section relationships")).build();
            relList.addAll(sectRels);
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
                    .entity(jsonerror("There is no tradition with this id")).build();

        ArrayList<SectionModel> allSections = produceSectionList(traditionNode);
        if (allSections == null)
            return Response.serverError()
                    .entity(jsonerror("Tradition has no sections")).build();

        ArrayList<ReadingModel> readingModels = new ArrayList<>();
        for (SectionModel sm : allSections) {
            Section sectRest = new Section(traditionId, sm.getId());
            ArrayList<ReadingModel> sectionReadings = sectRest.sectionReadings();
            if (sectionReadings == null)
                return Response.serverError().entity(jsonerror("section lookup failed")).build();
            readingModels.addAll(sectionReadings);

        }
        return Response.ok(readingModels).build();
    }

    // TODO add method to find identical and mergeable readings across the whole tradition
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
            if (tradition.getDirection() != null )
                traditionNode.setProperty("direction", tradition.getDirection());
            if (tradition.getStemweb_jobid() != null )
                traditionNode.setProperty("stemweb_jobid", tradition.getStemweb_jobid());
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
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
                e.printStackTrace();
                return Response.serverError().build();
            }
        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(jsonerror("A tradition with this id was not found!"))
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
            return Response.status(Status.NOT_FOUND).entity(jsonerror("No such tradition found")).build();

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
            return Response.status(Status.NOT_FOUND).type(MediaType.TEXT_PLAIN).entity("No such tradition found").build();
        GraphMLExporter exporter = new GraphMLExporter();
        return exporter.writeNeo4J(traditionId);
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
            return Response.status(Status.NOT_FOUND).type(MediaType.TEXT_PLAIN).entity("No such tradition found").build();
        StemmawebExporter parser = new StemmawebExporter();
        return parser.writeNeo4J(traditionId);
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
        return parser.writeNeo4J(traditionId, includeRelatedRelationships);
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

}

