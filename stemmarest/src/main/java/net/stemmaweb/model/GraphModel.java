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
    /**
     * A set of readings that make up a portion of a variant graph.
     */
    private HashSet<ReadingModel> readings;
    /**
     * A set of links that make up a portion of a variant graph. Depending on context, these can be
     * either relationship links or sequence (path) links.
     */
    private HashSet<RelationModel> relations;
    
    public GraphModel(List<ReadingModel> readings, List<RelationModel> relations) {
        super();
        this.readings = new HashSet<>();
        this.relations = new HashSet<>();
        this.readings.addAll(readings);
        this.relations.addAll(relations);
    }

    public GraphModel() {
        super();
        this.readings = new HashSet<>();
        this.relations = new HashSet<>();
    }

    public HashSet<ReadingModel> getReadings() { return readings; }

    public void setReadings(List<ReadingModel> readings) {
        this.readings.clear();
        this.readings.addAll(readings);
    }

    public void addReadings(HashSet<ReadingModel> readings) { this.readings.addAll(readings); }

    public HashSet<RelationModel> getRelations() {
        return relations;
    }

    public void setRelations(List<RelationModel> relations) {
        this.relations.clear();
        this.relations.addAll(relations);
    }

    public void addRelations(HashSet<RelationModel> relations) { this.relations.addAll(relations); }
}
