package net.stemmaweb.model;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

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
     * The ID of the target node for this annotation link.
     */
    private Long target;

    public AnnotationLinkModel(Relationship r) {
        GraphDatabaseService db = r.getGraphDatabase();
        try (Transaction tx = db.beginTx()) {
            setType(r.getType().name());
            setTarget(r.getEndNodeId());
            if (r.hasProperty("follow"))
                setFollow(r.getProperty("follow").toString());
            tx.success();
        }
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

    public Long getTarget() {
        return target;
    }

    public void setTarget(Long target) {
        this.target = target;
    }
}
