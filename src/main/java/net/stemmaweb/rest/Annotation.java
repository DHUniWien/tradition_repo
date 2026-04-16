package net.stemmaweb.rest;

import static net.stemmaweb.Util.jsonerror;
import static net.stemmaweb.services.AnnotationService.findExistingLink;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import net.stemmaweb.services.GraphDatabaseServiceProvider;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.Uniqueness;

import com.qmino.miredot.annotations.ReturnType;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import net.stemmaweb.model.AnnotationLabelModel;
import net.stemmaweb.model.AnnotationLinkModel;
import net.stemmaweb.model.AnnotationModel;
import net.stemmaweb.services.AnnotationService;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.VariantGraphService;

/**
 * Comprises the API calls having to do with modifying an existing annotation. Annotations can be
 * created from the {@link net.stemmaweb.rest.Tradition Tradition} API.
 *
 * @author tla
 */

public class Annotation {
    private final GraphDatabaseService db;
    private final String tradId;
    private final String annoId;

    Annotation(String tradId, String aid) {
        this.db = new GraphDatabaseServiceProvider().getDatabase();
        this.tradId = tradId;
        this.annoId = aid;
    }

    /**
     * Look up an existing annotation by ID.
     *
     * @return the {@link net.stemmaweb.model.AnnotationModel AnnotationModel} corresponding to the specified ID
     * @statuscode 200 - on success
     * @statuscode 404 - if the annotation doesn't exist, or doesn't belong to this tradition
     * @statuscode 500 - on error
     */
    @GET
    @Produces("application/json; charset=utf-8")
    @ReturnType(clazz = AnnotationModel.class)
    public Response getAnnotation() {
        AnnotationModel result;
        Response response;
        try (Transaction tx = db.beginTx()) {
            if (annotationNotFound(tx)) {
                response = Response.status(Response.Status.NOT_FOUND).build();
            } else {
                Node a = tx.getNodeByElementId(annoId);
                result = new AnnotationModel(a);
                response = Response.ok(result).build();
            }
        }
        return response;
    }

    /**
     * Modify an existing annotation according to the model specified. Note that this method
     * DOES NOT modify annotation links; that should be done with the /link method.
     *
     * @param spec - an {@link net.stemmaweb.model.AnnotationModel AnnotationModel} representing how the annotation should look
     * @return the updated AnnotationModel
     * @statuscode 200 - on success
     * @statuscode 403 - if the AnnotationModel is invalid
     * @statuscode 404 - if the annotation doesn't exist, or doesn't belong to this tradition
     * @statuscode 500 - on error
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("application/json; charset=utf-8")
    @ReturnType(clazz = AnnotationModel.class)
    public Response updateAnnotation(AnnotationModel spec) {
        try (Transaction tx = db.beginTx()) {
            if (annotationNotFound(tx))
                return Response.status(Response.Status.NOT_FOUND).build();
            Node tradNode = VariantGraphService.getTraditionNode(tx, tradId);
            Node annoNode = tx.getNodeByElementId(annoId);
            AnnotationModel result = AnnotationService.updateAnnotation(tx, tradNode, annoNode, spec);
            tx.commit();
            return Response.ok(result).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(jsonerror(e.getMessage())).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
    }

    /**
     * Delete an annotation specified by ID. This method will also locate and delete other annotations
     * that are effectively orphaned (i.e. have no outbound links) by this deletion.
     *
     * @return A list of annotations that were deleted
     * @statuscode 200 - on success
     * @statuscode 404 - if the annotation doesn't exist, or doesn't belong to this tradition
     * @statuscode 500 - on error
     */
    @DELETE
    @Produces("application/json; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.AnnotationModel>")
    public Response deleteAnnotation() {
        List<AnnotationModel> deleted;
        Response response;
        try (Transaction tx = db.beginTx()) {
        	if (annotationNotFound(tx)) {
        		response = Response.status(Response.Status.NOT_FOUND).build();
        	} else {
        		Node a = tx.getNodeByElementId(annoId);
        		// Delete all outgoing relationships, which makes this a dangling annotation
        		DatabaseService.getRelationships(a, Direction.OUTGOING).forEach(Relationship::delete);
        		// Make this node no longer a primary, since we are deleting it explicitly
        		a.removeProperty("__primary");
        		// Delete the annotation and any other non-primary annotations that it leaves dangling
        		deleted = deleteIfDangling(a);
        		response = Response.ok(deleted).build();
        	}
            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
            response = Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return response;
    }

    // Used inside a transaction
    private List<AnnotationModel> deleteIfDangling(Node a) {
        List<AnnotationModel> result = new ArrayList<>();
        // Don't delete the TRADITION node
        if (a.hasLabel(Nodes.TRADITION)) return result;

        // Delete the node if it has no remaining outgoing relations
        if (!a.hasRelationship(Direction.OUTGOING) && a.getProperty("__primary", false).equals(false)) {
            result.add(new AnnotationModel(a));
            ArrayList<Node> parents = new ArrayList<>();
            DatabaseService.getRelationships(a, Direction.INCOMING).forEach(x -> {parents.add(x.getStartNode()); x.delete();});
            for (Node p : parents)
                result.addAll(deleteIfDangling(p));
            a.delete();
        }
        return result;
    }

    /**
     * Add an outbound link from this annotation node. Type and target are specified via an
     * {@link net.stemmaweb.model.AnnotationLinkModel AnnotationLinkModel}. Returns the annotation
     * including the new link.
     *
     * @param linkModel - the AnnotationLinkModel representing the link that should be added
     * @statuscode 200 - on success
     * @statuscode 304 - if the specified link already exists
     * @statuscode 403 - if the AnnotationLinkModel is invalid
     * @statuscode 404 - if the annotation doesn't exist, or doesn't belong to this tradition
     * @statuscode 500 - on error
     * @return an AnnotationModel for the annotation with its new link
     */

    @POST
    @Path("/link")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("application/json; charset=utf-8")
    @ReturnType(clazz = AnnotationModel.class)
    public Response addAnnotationLink(AnnotationLinkModel linkModel) {
        try (Transaction tx = db.beginTx()) {
            if (annotationNotFound(tx))
                return Response.status(Response.Status.NOT_FOUND).build();
            Node aNode = tx.getNodeByElementId(annoId);
            AnnotationLabelModel labelModel = new AnnotationLabelModel(tradId, aNode.getLabels().iterator().next().name(), tx);
            AnnotationLinkModel result = AnnotationService.addAnnotationLink(tx, aNode, labelModel, linkModel);
            if (result == null)
                return Response.notModified().build();
            AnnotationModel updated = new AnnotationModel(aNode);
            tx.commit();
            return Response.ok(updated).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(jsonerror("Target node " + linkModel.getTarget() + " not found")).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(jsonerror(e.getMessage())).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
    }

    /**
     * Delete an outbound link from this annotation node. Type and target are specified via an
     * {@link net.stemmaweb.model.AnnotationLinkModel AnnotationLinkModel}. Returns the annotation
     * with the link deleted.
     *
     * @title Delete an outbound link on this annotation
     * @param linkModel - the AnnotationLinkModel representing the link that should be deleted
     * @statuscode 200 - on success
     * @statuscode 404 - if the annotation doesn't exist, or doesn't belong to this tradition
     * @statuscode 500 - on error
     * @return an AnnotationModel for the annotation whose link was deleted
     */

    @DELETE
    @Path("/link")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("application/json; charset=utf-8")
    @ReturnType(clazz = AnnotationModel.class)
    public Response deleteAnnotationLink(AnnotationLinkModel linkModel) {
        try (Transaction tx = db.beginTx()) {
            if (annotationNotFound(tx))
                return Response.status(Response.Status.NOT_FOUND).build();
            Node annoNode = tx.getNodeByElementId(annoId);
            String linkId = findExistingLink(annoNode, linkModel);
            if (linkId == null)
                return Response.status(Response.Status.NOT_FOUND).entity(jsonerror("Specified link not found")).build();
            Relationship r = tx.getRelationshipByElementId(linkId);
            r.delete();
            AnnotationModel updated = new AnnotationModel(annoNode);
            tx.commit();
            return Response.ok(updated).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
    }

    /**
     * Return a list of annotations that point to this one. If the 'recursive' parameter is
     * set to 'true', then the call will return all ancestor annotations; otherwise it will
     * be limited to direct parents.
     *
     * @title Return annotation's referents (parents)
     * @param recurse - Include all ancestors in response
     * @return a list of parent / ancestor AnnotationModels
     * @statuscode 200 - on success
     * @statuscode 404 - if the annotation doesn't exist, or doesn't belong to this tradition
     * @statuscode 500 - on error
     */

    @GET
    @Path("/referents")
    @Produces("application/json; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.AnnotationModel")
    public Response getReferents(@QueryParam("recursive") @DefaultValue("false") String recurse) {
        try (Transaction tx = db.beginTx()) {
            if (annotationNotFound(tx))
                return Response.status(Response.Status.NOT_FOUND).build();
            List<AnnotationModel> result = collectReferents(tx, recurse.equals("true"))
                    .stream().map(AnnotationModel::new).collect(Collectors.toList());
            return Response.ok(result).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
    }

    List<Node> collectReferents(Transaction tx, boolean recurse) {
        Node aNode = tx.getNodeByElementId(annoId);
        if (recurse) {
            List<Node> result = new ArrayList<>();
            tx.traversalDescription().depthFirst()
                    .evaluator(crawlReferents)
                    .uniqueness(Uniqueness.NODE_GLOBAL)
                    .traverse(aNode).nodes().forEach(result::add);
            return result;
        } else {
            return DatabaseService.getRelationships(aNode, Direction.INCOMING).stream()
                    .filter(x -> !x.getType().equals(ERelations.HAS_ANNOTATION))
                    .map(Relationship::getStartNode).collect(Collectors.toList());
        }
    }

    // Evaluator to walk the annotation structure
    private static final Evaluator crawlReferents = path -> {
        if (path.length() == 0)
            return Evaluation.EXCLUDE_AND_CONTINUE;
        // Incoming direction only
        if (path.endNode().equals(path.lastRelationship().getEndNode()))
            return Evaluation.EXCLUDE_AND_PRUNE;
        // Stop when we get to the top of the annotation tree
        if (path.lastRelationship().getType().toString().equals(ERelations.HAS_ANNOTATION.toString()))
            return Evaluation.EXCLUDE_AND_PRUNE;
        return Evaluation.INCLUDE_AND_CONTINUE;
    };

    // Check here whether we need to return a 404
    private boolean annotationNotFound(Transaction tx) {
        boolean found;
        Node a = tx.getNodeByElementId(annoId);
        Relationship r = a.getSingleRelationship(ERelations.HAS_ANNOTATION, Direction.INCOMING);
        Node t = r.getStartNode();
        found = t.hasLabel(Nodes.TRADITION) && t.getProperty("id", "NONE").equals(tradId);

        return !found;
    }
}
