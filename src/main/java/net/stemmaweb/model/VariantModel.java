package net.stemmaweb.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import net.stemmaweb.services.ReadingService;

import java.util.*;

public class VariantModel {
    /**
     * Expresses a variant, which is made up of a sequence of readings and a set of witness sigla.
     */

    private List<ReadingModel> readings;
    private Map<String,List<String>> witnesses;
    private Boolean normal;
    private Boolean displaced;

    public VariantModel() {
        readings = new ArrayList<>();
        witnesses = new HashMap<>();
    }

    public List<ReadingModel> getReadings() {
        return readings;
    }

    public void setReadings(List<ReadingModel> readings) {
        this.readings = readings;
    }

    public Map<String, List<String>> getWitnesses() {
        return witnesses;
    }

    public void setWitnesses(Map<String, List<String>> witnesses) {
        this.witnesses = witnesses;
    }

    @JsonIgnore
    public List<String> getWitnessList() {
        ArrayList<String> sigList = new ArrayList<>();
        for (String l : this.witnesses.keySet())
            for (String s : this.witnesses.get(l))
                sigList.add(l.equals("witnesses") ? s : String.format("%s (%s)", s, l));
        Collections.sort(sigList);
        return sigList;
    }

    public Boolean getNormal() {
        return normal;
    }

    public void setNormal(Boolean normal) {
        this.normal = normal;
    }

    public Boolean getDisplaced() {
        return displaced;
    }

    public void setDisplaced(Boolean displaced) {
        this.displaced = displaced;
    }

    @Override
    public String toString() {
        String vText = ReadingService.textOfReadings(this.readings, this.normal, true);
        if (vText.equals("")) vText = "om.";
        return String.format("%s: %s", vText, String.join(" ", this.getWitnessList()));
    }
}
