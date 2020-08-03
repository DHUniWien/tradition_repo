package net.stemmaweb.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.services.ReadingService;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.*;
import java.util.stream.Collectors;

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

    public VariantModel() {
        readings = new ArrayList<>();
        witnesses = new HashMap<>();
    }

    /**
     * Initialize a variant model from a given Neo4J path, assumed to be a valid variant path.
     * @param p - the Neo4J path to initialize from
     */
    public VariantModel (Path p) {
        // Get the readings
        List<ReadingModel> vReadings = new ArrayList<>();
        p.nodes().forEach(x -> vReadings.add(new ReadingModel(x)));
        // Remove the first and last (common) readings
        vReadings.remove(0);
        vReadings.remove(vReadings.size()-1);
        this.setReadings(vReadings);

        // Set the "normal" flag appropriately
        this.setNormal(p.startNode().hasRelationship(ERelations.NSEQUENCE, Direction.OUTGOING));

        // Get the witnesses that belong to the whole path
        Map<String, Set<String>> vWits = new HashMap<>();
        boolean first = true;
        for (Relationship r: p.relationships()) {
            for (String layer: r.getPropertyKeys()) {
                // If this is the first relationship we look at, take in all witnesses and their layers
                if (first) {
                    vWits.put(layer, new HashSet<>(Arrays.asList((String[]) r.getProperty(layer))));
                    // Otherwise we have to remove witnesses and layers that don't follow this path entirely
                } else {
                    if (vWits.containsKey(layer)) {
                        Set<String> currWits = vWits.get(layer);
                        currWits.retainAll(Arrays.asList((String[]) r.getProperty(layer)));
                        if (currWits.size() == 0)
                            vWits.remove(layer);
                    }
                }
            }
            first = false;
        }
        // Now add whatever witnesses / layers are left; these are the ones that contain
        // this particular sequence of readings.
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
    public boolean sameAs(VariantModel othervm) {
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
    public boolean mergeVariant(VariantModel othervm) {
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

    public void addWitnesses(Map<String, List<String>> witnesses) {
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
