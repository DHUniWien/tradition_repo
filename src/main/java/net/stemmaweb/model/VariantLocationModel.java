package net.stemmaweb.model;

import java.util.ArrayList;
import java.util.List;

public class VariantLocationModel {

    private Long rankIndex;
    private List<ReadingModel> readings;
    private List<RelationModel> relations;

    public VariantLocationModel() {
        this.rankIndex = 0L;
        this.readings = new ArrayList<>();
        this.relations = new ArrayList<>();
    }

    public Long getRankIndex() {
        return rankIndex;
    }

    public void setRankIndex(Long rankIndex) {
        this.rankIndex = rankIndex;
    }

    public List<ReadingModel> getReadings() {
        return readings;
    }

    public void setReadings(List<ReadingModel> readings) {
        this.readings = readings;
    }

    public void addReading(ReadingModel rm) {
        this.readings.add(rm);
    }

    public List<RelationModel> getRelations() {
        return relations;
    }

    public void setRelations(List<RelationModel> relations) {
        this.relations = relations;
    }

    public void addRelation(RelationModel rm) {
        this.relations.add(rm);
    }

}
