package net.stemmaweb.model;

import net.stemmaweb.rest.ERelations;
import org.neo4j.graphdb.*;

import java.util.HashMap;
import java.util.Map;

public class AnnotationLabelModel {
    private String name;
    private Map<String, String> properties;
    private Map<String, String> links;

    // Initialise from node
    public AnnotationLabelModel(Node annNode) {
        GraphDatabaseService db = annNode.getGraphDatabase();
        try (Transaction tx = db.beginTx()) {
            // Look up the name
            this.setName(annNode.getProperty("name").toString());

            // Look up the list of properties
            properties = new HashMap<>();
            Relationship prel = annNode.getSingleRelationship(ERelations.HAS_PROPERTIES, Direction.OUTGOING);
            if (prel != null) {
                Node pnode = prel.getEndNode();
                for (String key : pnode.getPropertyKeys()) {
                    properties.put(key, pnode.getProperty("key").toString());
                }
            }

            // Look up the list of links
            links = new HashMap<>();
            Relationship lrel = annNode.getSingleRelationship(ERelations.HAS_LINKS, Direction.OUTGOING);
            if (lrel != null) {
                Node lnode = lrel.getEndNode();
                for (String key : lnode.getPropertyKeys()) {
                    properties.put(key, lnode.getProperty("key").toString());
                }
            }
            tx.success();
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public Map<String, String> getLinks() {
        return links;
    }

    public void setLinks(Map<String, String> links) {
        this.links = links;
    }
}
