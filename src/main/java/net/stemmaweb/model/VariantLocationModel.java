package net.stemmaweb.model;

import java.util.ArrayList;
import java.util.List;

public class VariantLocationModel {

    private Long rankIndex;
    private List<ReadingModel> base;
    private List<VariantModel> variants;
    private List<RelationModel> relations;
    private boolean has_displacement;

    public VariantLocationModel() {
        this.rankIndex = 0L;
        this.base = new ArrayList<>();
        this.variants = new ArrayList<>();
        this.relations = new ArrayList<>();
    }

    public Long getRankIndex() {
        return rankIndex;
    }

    public void setRankIndex(Long rankIndex) {
        this.rankIndex = rankIndex;
    }

    public List<ReadingModel> getBase() {
        return base;
    }

    public void setBase(List<ReadingModel> readings) {
        this.base = readings;
    }

    public List<RelationModel> getRelations() {
        return relations;
    }

    public List<VariantModel> getVariants() {
        return variants;
    }

    public void setVariants(List<VariantModel> variants) {
        this.variants = variants;
    }

    public void addVariant(VariantModel variant) {
        this.variants.add(variant);
    }

    public void setRelations(List<RelationModel> relations) {
        this.relations = relations;
    }

    public void addRelation(RelationModel rm) {
        this.relations.add(rm);
    }

    public boolean hasDisplacement() {
        return has_displacement;
    }

    public void setDisplacement(boolean has_displacement) {
        this.has_displacement = has_displacement;
    }
}
