package net.stemmaweb.model;

import java.util.HashSet;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * This model contains a graph or subgraph returned from the Neo4j db
 * @author PSE FS 2015 Team2
 */

@XmlRootElement
@JsonInclude(Include.NON_NULL)
public class GraphModel {
    private HashSet<ReadingModel> readings;
    private HashSet<RelationshipModel> relationships;
    
    public GraphModel(List<ReadingModel> readings, List<RelationshipModel> relationships) {
        super();
        this.readings = new HashSet<>();
        this.relationships = new HashSet<>();
        this.readings.addAll(readings);
        this.relationships.addAll(relationships);
    }

    public GraphModel() {
        super();
        this.readings = new HashSet<>();
        this.relationships = new HashSet<>();
    }

    public HashSet<ReadingModel> getReadings() { return readings; }

    public void setReadings(List<ReadingModel> readings) {
        this.readings.clear();
        this.readings.addAll(readings);
    }

    public void addReadings(HashSet<ReadingModel> readings) { this.readings.addAll(readings); }

    public HashSet<RelationshipModel> getRelationships() {
        return relationships;
    }

    public void setRelationships(List<RelationshipModel> relationships) {
        this.relationships.clear();
        this.relationships.addAll(relationships);
    }

    public void addRelationships(HashSet<RelationshipModel> relationships) { this.relationships.addAll(relationships); }
}
