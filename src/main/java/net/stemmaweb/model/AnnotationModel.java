package net.stemmaweb.model;

import org.neo4j.graphdb.*;

import net.stemmaweb.services.GraphDatabaseServiceProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This is the data model for an annotation. It needs:
 * - The annotation label
 * - Any properties that the annotation node should have
 * - A list of links (see AnnotationLinkModel) that connects this annotation with the rest

 */

public class AnnotationModel {
    /**
     * The ID of the annotation
     */
    private String id;
    /**
     * The annotation's label - there should be only one
     */
    private String label;
    /**
     * Whether this annotation is a primary object, and should be kept even without referents
     */
    private Boolean isPrimary;
    /**
     * A map of property keys to values
     */
    private Map<String, Object> properties;
    /**
     * A list of outbound links carried by this annotation
     */
    private List<AnnotationLinkModel> links;

    public AnnotationModel() {
        this.isPrimary = false;
        this.properties = new HashMap<>();
        this.links = new ArrayList<>();
    }

    public AnnotationModel(Node annNode) {
//        GraphDatabaseService db = annNode.getGraphDatabase();
        GraphDatabaseService db = new GraphDatabaseServiceProvider().getDatabase();
        try (Transaction tx = db.beginTx()) {
            this.setId(annNode.getElementId());
            // We assume there is only one label
            this.setLabel(annNode.getLabels().iterator().next().name());
            this.setPrimary(annNode.getProperty("__primary", false).equals(true));
            Map<String,Object> props = annNode.getAllProperties();
            props.remove("__primary");
            this.setProperties(props);
            this.links = new ArrayList<>();
            for (Relationship r : annNode.getRelationships(Direction.OUTGOING))
                this.addLink(new AnnotationLinkModel(r));
            tx.close();
        }
    }

    public String getId() {
        return id;
    }

    private void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Boolean getPrimary() {
        return isPrimary;
    }

    public void setPrimary(Boolean primary) {
        isPrimary = primary;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public void addProperty(String key, Object val) {
        this.properties.put(key, val);
    }

    public List<AnnotationLinkModel> getLinks() {
        return links;
    }

    public void setLinks(List<AnnotationLinkModel> links) {
        this.links = links;
    }

    public void addLink(AnnotationLinkModel l) {
        this.links.add(l);
    }
}
