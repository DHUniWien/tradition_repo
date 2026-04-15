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

public class AnnotationService {
    public static AnnotationModel addAnnotationToTradition(Transaction tx, Node traditionNode, AnnotationModel spec)
            throws IllegalArgumentException {
        Node newAnno = tx.createNode();
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

        // Remove any old label and set the new label
        annoNode.getLabels().forEach(annoNode::removeLabel);
        annoNode.addLabel(Label.label(alm.getName()));

        // Now check and replace its properties
        annoNode.getPropertyKeys().forEach(annoNode::removeProperty);
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
        if (findExistingLink(annoNode, linkModel) != null)
            return null;

        // See if the proposed link is valid
        Node target = tx.getNodeByElementId(linkModel.getTarget());
        ArrayList<String> allowedLinks = new ArrayList<>();
        for (Label l : target.getLabels()) {
            if (labelModel.getLinks().containsKey(l.name()))
                allowedLinks.addAll(Arrays.asList(labelModel.getLinks().get(l.name()).split(",")));
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
        for (Relationship r : aNode.getRelationships(Direction.OUTGOING)) {
            if (r.getType().name().equals(linkModel.getType())
                    && r.getEndNode().getElementId().equals(linkModel.getTarget())) {
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
                a.getRelationships(Direction.INCOMING).forEach(Relationship::delete);
                a.delete();
            }
        }
        return deleted;
    }
}
