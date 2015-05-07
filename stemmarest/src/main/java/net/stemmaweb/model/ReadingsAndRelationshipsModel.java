package net.stemmaweb.model;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * 
 * @author PSE FS 2015 Team2
 *
 */
@XmlRootElement
@JsonInclude(Include.NON_NULL)
public class ReadingsAndRelationshipsModel {
    private List<ReadingModel> readings;
    private List<RelationshipModel> relationships;
    
    public ReadingsAndRelationshipsModel(List<ReadingModel> readings, List<RelationshipModel> relationships) {
        super();
        this.readings = readings;
        this.relationships = relationships;
    }

    public ReadingsAndRelationshipsModel() {

    }

    public List<ReadingModel> getReadings() {
        return readings;
    }

    public void setReadings(List<ReadingModel> readings) {
        this.readings = readings;
    }

    public List<RelationshipModel> getRelationships() {
        return relationships;
    }

    public void setRelationships(List<RelationshipModel> relationships) {
        this.relationships = relationships;
    }

}
