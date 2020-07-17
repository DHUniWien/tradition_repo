package net.stemmaweb.model;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import net.stemmaweb.services.ReadingService;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@XmlRootElement
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class VariantLocationModel {

    /**
     * the rank where this variant location starts
     */
    private Long rankIndex;
    /**
     * the ReadingModel that is the last common point before the variant starts
     */
    private ReadingModel before;
    /**
     * the ReadingModel that is the first common point after the variant ends
     */
    private ReadingModel after;
    /**
     * the list of readings that constitutes the lemma for this location
     */
    private List<ReadingModel> base;
    /**
     * the list of VariantModels that constitute the variants for this location
     */
    private List<VariantModel> variants;
    /**
     * the list of relations that exist between readings in the base and readings in th variants
     */
    private List<RelationModel> relations;
    /**
     * whether any of the above-mentioned relations is a displacement type, e.g. transposition
     */
    private boolean has_displacement = false;
    /**
     * whether we are working with a normalised text
     */
    private boolean normalised = false;

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

    @JsonGetter("has_displacement")
    public boolean hasDisplacement() {
        return has_displacement;
    }

    @JsonSetter("has_displacement")
    public void setDisplacement(boolean has_displacement) {
        this.has_displacement = has_displacement;
    }

    public ReadingModel getBefore() {
        return before;
    }

    public void setBefore(ReadingModel before) {
        this.before = before;
    }

    public ReadingModel getAfter() {
        return after;
    }

    public void setAfter(ReadingModel after) {
        this.after = after;
    }

    public boolean isNormalised() {
        return normalised;
    }

    public void setNormalised(boolean normalised) {
        this.normalised = normalised;
    }

    @Override
    public String toString() {
        StringBuilder apparatusEntry = new StringBuilder();
        apparatusEntry.append(this.rankIndex);
        apparatusEntry.append(": ");
        // String from = this.isNormalised() ? this.getBefore().getNormal_form() : this.getBefore().getText();
        // String to = this.isNormalised() ? this.getAfter().getNormal_form() : this.getAfter().getText();
        String base = ReadingService.textOfReadings(this.getBase(), this.isNormalised(), false);
        boolean interp = false;
        if (base.equals("")) {
            // Any variants with content are going to be additions. We will represent them as interpolations
            // between the "before" and "after" readings.
            List<ReadingModel> reps = Arrays.asList(this.getBefore(), this.getAfter());
            base = ReadingService.textOfReadings(reps, this.isNormalised(), false);
            interp = true;
        }
        apparatusEntry.append(base);
        apparatusEntry.append("] ");
        for (VariantModel vm : this.getVariants()) {
            String witnessList = String.join(" ", vm.getWitnessList());
            String varText = ReadingService.textOfReadings(vm.getReadings(), this.isNormalised(), false);
            if (interp)
                varText += "(interp.)";
            else if (varText.equals(""))
                varText = "(om.)";
            apparatusEntry.append(String.format("\t%s: %s\n", varText, witnessList));
        }
        return apparatusEntry.toString();
    }
}
