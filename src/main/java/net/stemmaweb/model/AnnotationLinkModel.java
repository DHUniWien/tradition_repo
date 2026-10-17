package net.stemmaweb.model;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

import net.stemmaweb.services.DatabaseService;

/**
 * A model for an outbound link (relationship) from an annotation to some target node.
 */

@SuppressWarnings("WeakerAccess")
public class AnnotationLinkModel {
    /**
     * The relationship type for the link. Should be specified in an AnnotationLabel definition belonging to this tradition.
     */
    private String type;
    /**
     * The specification of what path should be followed, if this is a reading-path-based annotation.
     */
    private String follow;
    /**
     * The ID of the target node for this annotation link. For a managed target type
     * (READING, SECTION, ANNOTATION, etc.) this is the target's application-assigned numeric
     * id; for a TRADITION it is the tradition's UUID, and for a USER the user's externally
     * supplied id.
     */
    private String target;
    /**
     * The Neo4j label of the target node (e.g. "READING", "SECTION", "ANNOTATION", or
     * an unmanaged type like "WITNESS"). Required because ids are only unique per label,
     * not globally, and a link type can be validly declared for more than one target
     * label in an AnnotationLabel's links schema -- so the label cannot be safely
     * inferred and must be supplied explicitly.
     */
    private String targetLabel;

    public AnnotationLinkModel() {}

    public AnnotationLinkModel(Relationship r) {
        setType(r.getType().name());
        Node end = r.getEndNode();
        String label = DatabaseService.addressableLabelOf(end);
        if (label == null)
            throw new IllegalStateException("Annotation link target has no id: " + end.getLabels());
        setTargetLabel(label);
        setTarget(end.getProperty("id").toString());
        if (r.hasProperty("follow"))
            setFollow(r.getProperty("follow").toString());
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFollow() {
        return follow;
    }

    public void setFollow(String follow) {
        this.follow = follow;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getTargetLabel() {
        return targetLabel;
    }

    public void setTargetLabel(String targetLabel) {
        // LATER handle annotation labels properly instead of requiring ANNOTATION here
        this.targetLabel = targetLabel;
    }
}
