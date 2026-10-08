package net.stemmaweb.services;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;

import net.stemmaweb.model.AnnotationLabelModel;
import net.stemmaweb.model.AnnotationLinkModel;
import net.stemmaweb.model.AnnotationModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;

public class AnnotationService {
    public static AnnotationModel addAnnotationToTradition(Transaction tx, Node traditionNode, AnnotationModel spec)
            throws IllegalArgumentException {
        Node newAnno = tx.createNode();
        newAnno.addLabel(Nodes.ANNOTATION);
        DatabaseService.assignIdIfManaged(tx, newAnno);
        traditionNode.createRelationshipTo(newAnno, ERelations.HAS_ANNOTATION);
        return updateAnnotation(tx, traditionNode, newAnno, spec);
    }

    public static AnnotationModel updateAnnotation(Transaction tx, Node traditionNode, Node annoNode, AnnotationModel spec)
            throws IllegalArgumentException {
        // Find the relevant annotation label
        Optional<Node> al = DatabaseService.getRelated(traditionNode, ERelations.HAS_ANNOTATION_TYPE)
                .stream().filter(x -> x.getProperty("name").equals(spec.getLabel())).findFirst();
        if (al.isEmpty())
            throw new IllegalArgumentException("No annotation label " + spec.getLabel() + " defined for this tradition");
        AnnotationLabelModel alm = new AnnotationLabelModel(al.get());

        // Remove any old dynamic label and set the new one
        for (Label l : annoNode.getLabels()) {
            if (!l.name().equals(Nodes.ANNOTATION.name()))
                annoNode.removeLabel(l);
        }
        annoNode.addLabel(Label.label(alm.getName()));

        // Now check and replace its properties apart from the ID.
        for (String pkey : annoNode.getPropertyKeys()) {
            if (!pkey.equals("id"))
                annoNode.removeProperty(pkey);
        }
        for (String pkey : spec.getProperties().keySet()) {
            // Make sure this property name is defined
            if (!alm.getProperties().containsKey(pkey))
                throw new IllegalArgumentException("No property " + pkey + " defined for this annotation label");
            // Okay? Then set the property
            String ptype = alm.getProperties().get(pkey);
            Object pval;
            try {
                // Is it a time-based thing?
                Method parse = Class.forName("java.time." + ptype).getMethod("parse", CharSequence.class);
                pval = parse.invoke(null, spec.getProperties().get(pkey).toString());
            } catch (Exception e) {
                // It isn't a time-based thing. Probably.
                if (ptype.equals("Character")) {
                    // Make sure that the character is actually a single character.
                    String pstr = spec.getProperties().get(pkey).toString();
                    if (pstr.length() > 1)
                        throw new IllegalArgumentException("Cannot set multi-character string value as Character");
                    pval = pstr.charAt(0);
                } else {
                    if (ptype.equals("String"))
                        pval = spec.getProperties().get(pkey);
                    else {
                        try {
                            Class<?> pclass = Class.forName("java.lang." + ptype);
                            pval = pclass.getMethod("valueOf", String.class)
                                    .invoke(null, spec.getProperties().get(pkey).toString());
                        } catch (Exception f) {
                            throw new IllegalArgumentException("Cannot set property " + pkey + " of type " + ptype
                                    + " with value " + spec.getProperties().get(pkey));
                        }
                    }
                }
            }
            annoNode.setProperty(pkey, pval);
        }
        // With that done, set the "primary" property
        annoNode.setProperty("__primary", spec.getPrimary());

        // If this is a new annotation, set any given links. Otherwise leave it alone.
        if (!annoNode.hasRelationship(Direction.OUTGOING)) {
            for (AnnotationLinkModel linkModel : spec.getLinks()) {
                addAnnotationLink(tx, annoNode, alm, linkModel);
            }
        }
        return new AnnotationModel(annoNode);
    }

    public static AnnotationLinkModel addAnnotationLink(Transaction tx, Node annoNode, AnnotationLabelModel labelModel,
                                                        AnnotationLinkModel linkModel) {
        String targetLabelName = linkModel.getTargetLabel();
        if (targetLabelName == null || targetLabelName.isBlank())
            throw new IllegalArgumentException("Annotation link requires a targetLabel");

        if (findExistingLink(annoNode, linkModel) != null)
            return null;

        // Resolve the target node. For a managed label (READING, SECTION, ANNOTATION),
        // look it up by its application-assigned id, exactly as any other managed-entity
        // lookup. Otherwise, fall back to the permanent elementId exception for unmanaged
        // target types, but verify the resolved node actually carries the claimed label --
        // the client is now asserting the label, rather than it being read off the node.
        Label targetLabel = Label.label(targetLabelName);
        Node target;
        if (DatabaseService.isManagedLabel(targetLabel)) {
            target = DatabaseService.findNodeOrThrow(tx, targetLabel, linkModel.getTarget());
        } else {
            target = tx.getNodeByElementId(linkModel.getTarget());
            if (!target.hasLabel(targetLabel))
                throw new IllegalArgumentException("Target node " + linkModel.getTarget()
                        + " does not carry label " + targetLabelName);
        }

        // See if the proposed link is valid, checking allowed link types against exactly
        // the label the client specified -- not every label the target node happens to
        // carry, since the same link type can be validly declared for more than one
        // target label in the links schema (e.g. {"READING": "BEGINS,ENDS", "SECTION":
        // "BEGINS,ENDS"}), making a union-based check ambiguous.
        ArrayList<String> allowedLinks = new ArrayList<>();
        if (labelModel.getLinks().containsKey(targetLabelName))
            allowedLinks.addAll(Arrays.asList(labelModel.getLinks().get(targetLabelName).split(",")));
        else if (targetLabelName.equals(Nodes.ANNOTATION.name())) {
            // Backward compatibility: a schema authored/reimported from before the
            // ANNOTATION marker label existed keys its links map by the target's dynamic
            // per-tradition type label (e.g. "PERSONREF") instead of "ANNOTATION". This is
            // a validation-only fallback -- target resolution above always goes through
            // the managed "ANNOTATION" label and id, regardless of which key matches here.
            String dynamicLabel = null;
            for (Label l : target.getLabels()) {
                if (!l.name().equals(Nodes.ANNOTATION.name())) {
                    dynamicLabel = l.name();
                    break;
                }
            }
            if (dynamicLabel != null && labelModel.getLinks().containsKey(dynamicLabel))
                allowedLinks.addAll(Arrays.asList(labelModel.getLinks().get(dynamicLabel).split(",")));
        }
        if (!allowedLinks.contains(linkModel.getType()))
            throw new IllegalArgumentException("Link type " + linkModel.getType() + " not allowed for node " + linkModel.getTarget());

        // Set the proposed link
        Relationship link = annoNode.createRelationshipTo(target, RelationshipType.withName(linkModel.getType()));
        if (linkModel.getFollow() != null)
            link.setProperty("follow", linkModel.getFollow());
        return new AnnotationLinkModel(link);
    }

    public static String findExistingLink(Node aNode, AnnotationLinkModel linkModel) {
        boolean managed = linkModel.getTargetLabel() != null
                && DatabaseService.isManagedLabel(Label.label(linkModel.getTargetLabel()));
        for (Relationship r : DatabaseService.getRelationships(aNode, Direction.OUTGOING)) {
            Node tNode = r.getEndNode();
            boolean targetMatches = managed
                    ? tNode.getProperty("id", "").toString().equals(linkModel.getTarget())
                      && tNode.hasLabel(Label.label(linkModel.getTargetLabel()))
                    : tNode.getElementId().equals(linkModel.getTarget());
            if (r.getType().name().equals(linkModel.getType()) && targetMatches) {
                return r.getElementId();
            }
        }
        return null;
    }

    public static List<AnnotationModel> pruneAnnotations(Node traditionNode) {
        List<AnnotationModel> deleted = new ArrayList<>();
        for (Node a : DatabaseService.getRelated(traditionNode, ERelations.HAS_ANNOTATION)) {
            boolean isPrimary = a.getProperty("primary", false).equals(true);
            if (!a.hasRelationship(Direction.OUTGOING) && !isPrimary) {
                deleted.add(new AnnotationModel(a));
                DatabaseService.getRelationships(a, Direction.INCOMING).forEach(Relationship::delete);
                a.delete();
            }
        }
        return deleted;
    }
}
