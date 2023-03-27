package net.stemmaweb.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlRootElement;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Path;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.services.ReadingService;

@XmlRootElement
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class VariantModel {
    /**
     * Expresses a variant, which is made up of a sequence of readings and a set of witness sigla.
     */

    private List<ReadingModel> readings;
    private Map<String,List<String>> witnesses;
    private Boolean normal = false;
    private Boolean displaced = false;
    private Boolean isEmpty = true;
    private ReadingModel anchor;

    @SuppressWarnings("unused")     // It's used by response.readEntity(VariantListModel.class)
    VariantModel() {
        readings = new ArrayList<>();
        witnesses = new HashMap<>();
    }

    /**
     * Initialize a variant model from a given Neo4J path, assumed to be a valid variant path.
     * @param p - the Neo4J path to initialize from
     */
    VariantModel (Path p, Map<String,Set<String>> vWits) {
        // Get the readings
        List<ReadingModel> vReadings = new ArrayList<>();
        p.nodes().forEach(x -> vReadings.add(new ReadingModel(x)));
        // Remove the first and last (common) readings
        vReadings.remove(0);
        vReadings.remove(vReadings.size()-1);
        this.setReadings(vReadings);

        // Set the "normal" flag appropriately
        this.setNormal(p.startNode().hasRelationship(Direction.OUTGOING, ERelations.NSEQUENCE));

        // Now add the witnesses / layers that belong to the path, making sure to keep the sigla sorted.
        Map<String, List<String>> endWitnesses = new HashMap<>();
        for (String layer : vWits.keySet()) {
            List<String> sigla = new ArrayList<>(vWits.get(layer));
            Collections.sort(sigla);
            endWitnesses.put(layer, sigla);
        }
        this.setWitnesses(endWitnesses);
        if (endWitnesses.size() > 0)
            this.isEmpty = false;
    }

    /**
     * Indicates whether another VariantModel represents the same variant text, possibly with different witnesses.
     * @param othervm - the other VariantModel to compare against
     * @return the answer
     */
    boolean sameAs(VariantModel othervm) {
        // Are the readings the same?
        String ourText = ReadingService.textOfReadings(this.readings, this.normal, true);
        String theirText = ReadingService.textOfReadings(othervm.readings, othervm.normal, true);
        if (!ourText.equals(theirText)) return false;
        if (!this.getNormal().equals(othervm.getNormal())) return false;
        if (!this.getDisplaced().equals(othervm.getDisplaced())) return false;
        if (this.getAnchor() != null) {
            return othervm.getAnchor() != null && this.getAnchor().getId().equals(othervm.getAnchor().getId());
        } else
            return othervm.getAnchor() == null;
    }

    /**
     * Merges another variant into this one, provided the other variant is identical but for its
     * witnesses, and returns true if the merge happened.
     * @param othervm - the other VariantModel to merge with
     * @return a boolean indicating whether the variants were merged
     */
    boolean mergeVariant(VariantModel othervm) {
        if (this.sameAs(othervm)) {
            // We merge only the witnesses.
            this.addWitnesses(othervm.getWitnesses());
            return true;
        }
        return false;
    }

    /*
     * Accessors
     */

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

    private void addWitnesses(Map<String, List<String>> witnesses) {
        // Add the given witnesses to the existing list.
        for (String layer: witnesses.keySet()) {
            if (this.witnesses.containsKey(layer)) {
                HashSet<String> existing = new HashSet<>();
                existing.addAll(this.witnesses.get(layer));
                existing.addAll(witnesses.get(layer));
                this.witnesses.put(layer, existing.stream().sorted().collect(Collectors.toList()));
            } else {
                this.witnesses.put(layer, witnesses.get(layer));
            }
        }
        // Run through the layers; if any witness appears both in 'witnesses' and in any layer,
        // then remove it from the layer.
        ArrayList<String> deletedLayers = new ArrayList<>();
        for (String layer : witnesses.keySet()) {
            if (layer.equals("witnesses")) continue;
            List<String> mainWits = witnesses.getOrDefault("witnesses", new ArrayList<>());
            List<String> layerWits = witnesses.get(layer);
            List<String> toRemove = new ArrayList<>();
            for (String sigil : layerWits) {
                if (mainWits.contains(sigil))
                    toRemove.add(sigil);
            }
            layerWits.removeAll(toRemove);
            if (layerWits.isEmpty()) deletedLayers.add(layer);
        }
        deletedLayers.forEach(witnesses::remove);
    }

    // This can have more than two answers, so we return a string.
    String containsWitnesses(Map<String,List<String>> witnessList) {
        boolean witnessSeen = false;
        boolean witnessMissed = false;
        for (String layer : witnessList.keySet()) {
            if (this.witnesses.containsKey(layer)) {
                for (String sigil : witnessList.get(layer)) {
                    witnessSeen = this.witnesses.get(layer).contains(sigil);
                }
            } else witnessMissed = true;
        }
        if (witnessSeen && witnessMissed) return "partial";
        if (witnessSeen) return "yes";
        return "no";
    }

    @JsonIgnore
    List<String> getWitnessList() {
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

    @SuppressWarnings("SameParameterValue")
    void setDisplaced(Boolean displaced) {
        this.displaced = displaced;
    }

    public ReadingModel getAnchor() {
        return anchor;
    }

    public void setAnchor(ReadingModel anchor) {
        this.anchor = anchor;
    }

    /**
     * Empty in this context means that the variant has no witnesses.
     * @return boolean
     */
    @JsonIgnore
    public boolean isEmpty() {
        return this.isEmpty;
    }

    @Override
    public String toString() {
        String vText = ReadingService.textOfReadings(this.readings, this.normal, true);
        if (vText.equals("")) vText = "om.";
        return String.format("%s: %s", vText, String.join(" ", this.getWitnessList()));
    }
}
