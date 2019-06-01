package net.stemmaweb.rest;

import com.qmino.miredot.annotations.ReturnType;
import net.stemmaweb.model.AnnotationLabelModel;
import net.stemmaweb.model.AnnotationLinkModel;
import net.stemmaweb.model.AnnotationModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.Uniqueness;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static net.stemmaweb.rest.Util.jsonerror;

/**
 * Comprises the API calls having to do with modifying an existing annotation. Annotations can be
 * created from the {@link net.stemmaweb.rest.Tradition Tradition} API.
 *
 * @author tla
 */

public class Annotation {
    private GraphDatabaseService db;
    private String tradId;
    private String annoId;

    Annotation(String tradId, String aid) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        this.db = dbServiceProvider.getDatabase();
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
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = AnnotationModel.class)
    public Response getAnnotation() {
        if (annotationNotFound()) return Response.status(Response.Status.NOT_FOUND).build();
        AnnotationModel result;
        try (Transaction tx = db.beginTx()) {
            Node a = db.getNodeById(Long.parseLong(annoId));
            result = new AnnotationModel(a);
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(result).build();
    }

    /**
     * Modify an existing annotation according to the model specified. Note that this method
     * DOES NOT modify annotation links; that should be done with the /link method.
     *
     * @param newAnno - an {@link net.stemmaweb.model.AnnotationModel AnnotationModel} representing how the annotation should look
     * @return the updated AnnotationModel
     * @statuscode 200 - on success
     * @statuscode 403 - if the AnnotationModel is invalid
     * @statuscode 404 - if the annotation doesn't exist, or doesn't belong to this tradition
     * @statuscode 500 - on error
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = AnnotationModel.class)
    public Response updateAnnotation(AnnotationModel newAnno) {
        if (annotationNotFound()) return Response.status(Response.Status.NOT_FOUND).build();
        AnnotationModel result = null;
        Node tradNode = DatabaseService.getTraditionNode(tradId, db);
        try (Transaction tx = db.beginTx()) {
            // Find the relevant annotation label
            Optional<Node> al = DatabaseService.getRelated(tradNode, ERelations.HAS_ANNOTATION_TYPE)
                    .stream().filter(x -> x.getProperty("name").equals(newAnno.getLabel())).findFirst();
            if (!al.isPresent())
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(jsonerror("No annotation label " + newAnno.getLabel() + " defined for this tradition"))
                        .build();
            AnnotationLabelModel alm = new AnnotationLabelModel(al.get());

            // Get our annotation node
            Node aNode = db.getNodeById(Long.valueOf(annoId));
            // Set the label if it has changed, removing any old label if necessary
            if (aNode.getLabels().iterator().hasNext()) {
                Label aLabel = aNode.getLabels().iterator().next();
                if (!aLabel.name().equals(newAnno.getLabel())) {
                    aNode.removeLabel(aLabel);
                    aNode.addLabel(Label.label(newAnno.getLabel()));
                }
            } else {
                aNode.addLabel(Label.label(newAnno.getLabel()));
            }

            // Now check and replace its properties
            aNode.getPropertyKeys().forEach(aNode::removeProperty);
            for (String pkey : newAnno.getProperties().keySet()) {
                if (!alm.getProperties().keySet().contains(pkey))
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(jsonerror("No property " + pkey + " defined for this annotation label"))
                            .build();
                // Okay? Then set the property
                String ptype = alm.getProperties().get(pkey);
                Object pval;
                // Is it a time-based thing?
                if (ptype.contains("Date") || ptype.contains("Time") || ptype.contains("Temporal")) {
                    Method parse = Class.forName("java.time." + ptype).getMethod("parse", CharSequence.class);
                    pval = parse.invoke(null, newAnno.getProperties().get(pkey).toString());
                } else if (ptype.equals("Character")) {
                    String pstr = newAnno.getProperties().get(pkey).toString();
                    if (pstr.length() > 1)
                        throw new Exception("Cannot set multi-character string value as Character");
                    pval = pstr.charAt(0);
                } else {
                    Class<?> pclass = Class.forName("java.lang." + ptype);
                    if (ptype.equals("String") || ptype.equals("Boolean"))
                        pval = pclass.cast(newAnno.getProperties().get(pkey));
                    else
                        pval = pclass.getMethod("valueOf", String.class)
                                .invoke(null, newAnno.getProperties().get(pkey).toString());
                }
                aNode.setProperty(pkey, pval);
            }
            // With that done, set the "primary" property
            aNode.setProperty("__primary", newAnno.getPrimary());

            // If this is a new annotation, set any given links. Otherwise leave it alone.
            if (!aNode.hasRelationship(Direction.OUTGOING)) {
                for (AnnotationLinkModel linkModel : newAnno.getLinks()) {
                    Response linkAdded = addAnnotationLink(linkModel);
                    if (linkAdded.getStatus() != Response.Status.OK.getStatusCode())
                        return linkAdded;
                }
            }
            result = new AnnotationModel(aNode);
            tx.success();
        } catch (ClassNotFoundException e) {
            Response.status(Response.Status.BAD_REQUEST)
                    .entity(jsonerror("Specified property class not found: " + e.getMessage()))
                    .build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(result).build();
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
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.AnnotationModel>")
    public Response deleteAnnotation() {
        if (annotationNotFound()) return Response.status(Response.Status.NOT_FOUND).build();
        List<AnnotationModel> deleted;
        try (Transaction tx = db.beginTx()) {
            Node a = db.getNodeById(Long.parseLong(annoId));
            // Delete all outgoing relationships, which makes this a dangling annotation
            a.getRelationships(Direction.OUTGOING).forEach(Relationship::delete);
            // Make this node no longer a primary, since we are deleting it explicitly
            a.removeProperty("__primary");
            // Delete the annotation and any other non-primary annotations that it leaves dangling
            deleted = deleteIfDangling(a);
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(deleted).build();
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
            a.getRelationships(Direction.INCOMING).forEach(x -> {parents.add(x.getStartNode()); x.delete();});
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
     * @param alm - the AnnotationLinkModel representing the link that should be added
     * @statuscode 200 - on success
     * @statuscode 304 - if the specified link already exists
     * @statuscode 403 - if the AnnotationLinkModel is invalid
     * @statuscode 404 - if the annotation doesn't exist, or doesn't belong to this tradition
     * @statuscode 500 - on error
     */

    @POST
    @Path("/link")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = AnnotationModel.class)
    public Response addAnnotationLink(AnnotationLinkModel alm) {
        if (annotationNotFound()) return Response.status(Response.Status.NOT_FOUND).build();
        AnnotationModel updated;
        try (Transaction tx = db.beginTx()) {
            Node aNode = db.getNodeById(Long.valueOf(annoId));
            // See if the link already exists
            if (findExistingLink(alm) != null) {
                tx.success();
                return Response.notModified().build();
            }

            String aLabel = aNode.getLabels().iterator().next().name();
            AnnotationLabelModel labelModel = new AnnotationLabelModel(tradId, aLabel);
            // See if the proposed link is valid
            Node target = db.getNodeById(alm.getTarget());
            ArrayList<String> allowedLinks = new ArrayList<>();
            for (Label l : target.getLabels()) {
                if (labelModel.getLinks().containsKey(l.name()))
                    allowedLinks.addAll(Arrays.asList(labelModel.getLinks().get(l.name()).split(",")));
            }
            if (!allowedLinks.contains(alm.getType()))
                return Response.status(Response.Status.BAD_REQUEST).entity(
                        jsonerror("Link type " + alm.getType() + " not allowed for node " + alm.getTarget())).build();

            // Set the proposed link
            Relationship link = aNode.createRelationshipTo(target, RelationshipType.withName(alm.getType()));
            if (alm.getFollow() != null)
                link.setProperty("follow", alm.getFollow());
            updated = new AnnotationModel(aNode);
            tx.success();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(jsonerror("Target node " + alm.getTarget() + " not found")).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(updated).build();
    }

    /**
     * Delete an outbound link from this annotation node. Type and target are specified via an
     * {@link net.stemmaweb.model.AnnotationLinkModel AnnotationLinkModel}. Returns the annotation
     * with the link deleted.
     *
     * @param alm - the AnnotationLinkModel representing the link that should be added
     * @statuscode 200 - on success
     * @statuscode 404 - if the annotation doesn't exist, or doesn't belong to this tradition
     * @statuscode 500 - on error
     */

    @DELETE
    @Path("/link")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = AnnotationModel.class)
    public Response deleteAnnotationLink(AnnotationLinkModel alm) {
        if (annotationNotFound()) return Response.status(Response.Status.NOT_FOUND).build();
        AnnotationModel updated;
        try (Transaction tx = db.beginTx()) {
            Relationship r = findExistingLink(alm);
            if (r == null)
                return Response.status(Response.Status.NOT_FOUND).entity(jsonerror("Specified link not found")).build();
            Node aNode = r.getStartNode();
            r.delete();
            updated = new AnnotationModel(aNode);
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        return Response.ok(updated).build();
    }

    @GET
    @Path("/referents")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType("java.util.List<net.stemmaweb.model.AnnotationModel")
    public Response getReferents(@QueryParam("recursive") @DefaultValue("false") String recurse) {
        if (annotationNotFound()) return Response.status(Response.Status.NOT_FOUND).build();
        List<AnnotationModel> result;
        try {
            result = collectReferents(recurse.equals("true")).stream()
                    .map(AnnotationModel::new).collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }

        return Response.ok(result).build();
    }

    List<Node> collectReferents(boolean recurse) {
        List<Node> result;
        try (Transaction tx = db.beginTx()) {
            Node aNode = db.getNodeById(Long.valueOf(annoId));
            if (recurse) {
                result = new ArrayList<>();
                db.traversalDescription().depthFirst()
                        .evaluator(crawlReferents)
                        .uniqueness(Uniqueness.NODE_GLOBAL)
                        .traverse(aNode).nodes().forEach(result::add);
            } else {
                result = StreamSupport.stream(aNode.getRelationships(Direction.INCOMING).spliterator(), false)
                        .filter(x -> !x.getType().equals(ERelations.HAS_ANNOTATION))
                        .map(Relationship::getStartNode).collect(Collectors.toList());
            }
            tx.success();
        }
        return result;
    }

    // Used inside a transaction
    private Relationship findExistingLink(AnnotationLinkModel alm) {
        Node aNode = db.getNodeById(Long.valueOf(annoId));
        for (Relationship r : aNode.getRelationships(Direction.OUTGOING)) {
            if (r.getType().name().equals(alm.getType()) && r.getEndNodeId() == alm.getTarget()) {
                return r;
            }
        }
        return null;
    }

    // Evaluator to walk the annotation structure
    private static Evaluator crawlReferents = path -> {
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
    private boolean annotationNotFound() {
        boolean found;
        try (Transaction tx = db.beginTx()) {
            Node a = db.getNodeById(Long.parseLong(annoId));
            Relationship r = a.getSingleRelationship(ERelations.HAS_ANNOTATION, Direction.INCOMING);
            Node t = r.getStartNode();
            found = t.hasLabel(Nodes.TRADITION) && t.getProperty("id", "NONE").equals(tradId);
            tx.success();
        } catch (NotFoundException e) {
            return true;
        }
        return !found;
    }
}
