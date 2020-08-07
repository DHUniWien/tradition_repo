package net.stemmaweb.model;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.services.ReadingService;
import org.neo4j.graphdb.*;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    /**
     * whether this is (or has become) an empty variant location
     */
    private boolean isEmpty = true;

    VariantLocationModel() {
        this.rankIndex = 0L;
        this.base = new ArrayList<>();
        this.variants = new ArrayList<>();
        this.relations = new ArrayList<>();
    }

    /**
     * Looks for all RELATED links between nodes involved in a VariantLocationModel, and adds the
     * corresponding RelationModels to the VariantLocationModel in question.
     *
     * @param db - the GraphDatabaseService we are using
     * @param dislocationTypes - the list of relation types that are non-colocated in this tradition
     */
    void collectRelationsInLocation(GraphDatabaseService db, List<String> dislocationTypes) {
        Set<Relationship> relations = new HashSet<>();
        // Gather all the nodes we need
        Set<Node> clusterNodes = new HashSet<>();
        try (Transaction tx = db.beginTx()) {
            for (ReadingModel rm : this.getBase())
                clusterNodes.add(db.getNodeById(Long.parseLong(rm.getId())));
            HashMap<Node, VariantModel> vModelForReading = new HashMap<>();
            for (VariantModel vm : this.getVariants())
                for (ReadingModel rm : vm.getReadings()) {
                    Node vrdg = db.getNodeById(Long.parseLong(rm.getId()));
                    clusterNodes.add(vrdg);
                    // As long as we're here, make a map of variant node -> VariantModel
                    vModelForReading.put(vrdg, vm);
                }
            for (Node n : clusterNodes) {
                for (Relationship rel : n.getRelationships(ERelations.RELATED, Direction.OUTGOING))
                    // Add any relation we find that links to another node in this variant location
                    if (clusterNodes.contains(rel.getEndNode()))
                        relations.add(rel);
                        // Add the relation anyway, if it signifies a displaced variant reading.
                        // Also mark the variant as being displaced.
                    else if (vModelForReading.containsKey(n)){
                        if (dislocationTypes.contains(rel.getProperty("type").toString())) {
                            relations.add(rel);
                            vModelForReading.get(n).setDisplaced(true);
                            // this.setDisplacement(true);
                        }
                    }
            }
            List<RelationModel> rml = relations.stream().map(RelationModel::new).collect(Collectors.toList());
            this.setRelations(rml);
            this.isEmpty = false;
            tx.success();
        }
    }

    /**
     * Filter the readings in the variant location on the given criteria.
     * @param filterRegex - The regular expression string to filter out
     * @param filterNonsense - Whether we should filter out readings marked is_nonsense
     */
    void filterReadings (String filterRegex, boolean filterNonsense, List<ReadingModel> baseText) {
        Pattern p = Pattern.compile(filterRegex);
        // If all base readings match the pattern, empty out the base text.
        // If only some base readings match the pattern, keep any "filtered" readings
        // that are not the first or the last reading, so that the base text matches
        // the lemma text for apparatus legibility purposes.
        List<ReadingModel> filteredBase = new ArrayList<>();
        List<ReadingModel> heldOver = new ArrayList<>();
        for (ReadingModel rm : this.getBase()) {
            if (!shouldBeFiltered(rm, p, filterNonsense)) {
                // Do we have any intermediate readings being held?
                filteredBase.addAll(heldOver);
                heldOver.clear();
                filteredBase.add(rm);
            } else if (!filteredBase.isEmpty()) {
                // If we have started a base chain, hold this reading in case we have to include it
                // in the middle of the base sequence.
                heldOver.add(rm);
            }
        }
        if (filteredBase.size() != this.getBase().size()) {
            this.setBase(filteredBase);
            // Reset the rank index just in case
            this.setRankIndex(filteredBase.size() > 0 ? filteredBase.get(0).getRank() : this.getBefore().getRank() + 1);
        }

        // Do we have to change the before/after settings?
        if (shouldBeFiltered(this.getBefore(), p, filterNonsense)) {
            Optional<ReadingModel> orm = baseText.stream().filter(x -> x.getId().equals(this.getBefore().getId())).findFirst();
            if (orm.isPresent()) { // always true
                for (int i = baseText.indexOf(orm.get()); i > 0; i--) {
                    ReadingModel prior = baseText.get(i-1);
                    if (!shouldBeFiltered(prior, p, filterNonsense)) {
                        this.setBefore(prior);
                        break;
                    }
                }
            }
        }

        if (shouldBeFiltered(this.getAfter(), p, filterNonsense)) {
            Optional<ReadingModel> orm = baseText.stream().filter(x -> x.getId().equals(this.getAfter().getId())).findFirst();
            if (orm.isPresent()) { // always true
                for (int i = baseText.indexOf(orm.get()); i < baseText.size() - 1; i++) {
                    ReadingModel next = baseText.get(i + 1);
                    if (!shouldBeFiltered(next, p, filterNonsense)) {
                        this.setAfter(next);
                        break;
                    }
                }
            }
        }

        // For each variant, remove all readings that match the pattern
        for (VariantModel vm : this.getVariants()) {
            List<ReadingModel> filteredVariants = new ArrayList<>();
            for (ReadingModel rm : vm.getReadings()) {
                if (!shouldBeFiltered(rm, p, filterNonsense)) filteredVariants.add(rm);
            }
            if (!filteredVariants.containsAll(vm.getReadings()))
                vm.setReadings(filteredVariants);
        }
        // Empty out and re-add all variants, which will merge any that are now identical
        List<VariantModel> existing = this.getVariants();
        this.variants = new ArrayList<>();
        existing.forEach(this::addVariant);

        // Now treat the special case where we have an emptied-out base and some emptied-out variants
        if (this.getBase().size() == 0)
            this.setVariants(this.getVariants().stream().filter(x -> x.getReadings().size() > 0).collect(Collectors.toList()));

        // If the location has no variants left, mark it as empty
        if (this.getVariants().size() == 0)
            this.isEmpty = true;
    }

    private boolean shouldBeFiltered (ReadingModel rm, Pattern p, boolean filterNonsense) {
        String toTest = this.isNormalised() ? rm.normalized() : rm.getText();
        Matcher m = p.matcher(toTest);
        return m.matches() || (filterNonsense && rm.getIs_nonsense());
    }

    /**
     * Return true if the other location model has the same chain of base readings, the same before and after point,
     * and the same value for normalisation
     * @param otherVLM the VLM to compare to
     */
    boolean sameAs(VariantLocationModel otherVLM) {
        String ourText = ReadingService.textOfReadings(this.getBase(), this.isNormalised(), true);
        String theirText = ReadingService.textOfReadings(otherVLM.getBase(), this.isNormalised(), true);
        if (!ourText.equals(theirText)) return false;
        if (!this.getBefore().getId().equals(otherVLM.getBefore().getId())) return false;
        if (!this.getAfter().getId().equals(otherVLM.getAfter().getId())) return false;
        return this.isNormalised() == otherVLM.isNormalised();
    }

    String lookupKey() {
        return String.format("%s -- %s", this.getBefore().getId(), this.getAfter().getId());
    }

    /*
     * Accessor methods
     */

    public Long getRankIndex() {
        return rankIndex;
    }

    void setRankIndex(Long rankIndex) {
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

    void addVariant(VariantModel variant) {
        // Is there already a variant with this identical set of properties, apart from the witnesses?
        // If so, merge it
        boolean merged = false;
        VariantModel toRemove = null;
        for (VariantModel vm : this.getVariants()) {
            // Is it the same as another variant?
            if (vm.sameAs(variant))
                merged = vm.mergeVariant(variant);
            else {
                // Are the witnesses of the new variant included in an existing variant?
                String overlappingWits = vm.containsWitnesses(variant.getWitnesses());
                if (vm.getReadings().isEmpty() && overlappingWits.equals("yes")) {
                    // This is only okay in the cases where the new variant is a transposition and we are an
                    // omission of those witnesses, or vice versa. In that case, remove the omission variant.
                    toRemove = vm;
                } else if (!overlappingWits.equals("no")) {
                    throw new RuntimeException(String.format(
                            "Tried to add variant %s, overlapping with %s, to this location", variant, vm));
                }
            }
        }
        if (!merged) this.variants.add(variant);
        if (toRemove != null) this.variants.remove(toRemove);

        // Any time we add a variant we should update the list of relations pertaining to this VLM
        // Keep the variant list sorted
        this.variants.sort(Comparator.comparingInt(x -> x.getReadings().size()));
    }

    public void setRelations(List<RelationModel> relations) {
        this.relations = relations;
    }

    @JsonGetter("has_displacement")
    public boolean hasDisplacement() {
        return has_displacement;
    }

    @SuppressWarnings("SameParameterValue")
    @JsonSetter("has_displacement")
    void setDisplacement(boolean has_displacement) {
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

    /**
     * Empty in this contexts means that the location has no VariantModels.
     * @return boolean
     */
    @JsonIgnore
    public boolean isEmpty() {
        return this.isEmpty;
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
                varText += " (interp.)";
            else if (varText.equals(""))
                varText = "(om.)";
            else if (vm.getDisplaced() && this.hasDisplacement()) {
                ReadingModel anchor = vm.getAnchor();
                if (varText.equals(base))
                    varText = "transp. ";
                else
                    varText += " transp. ";
                if (anchor != null && anchor.getRank() > vm.getReadings().get(0).getRank())
                    varText += "prae ";
                else
                    varText += "post ";
                if (anchor == null)
                    varText += "(NULL)";
                else
                    varText += ReadingService.textOfReadings(Collections.singletonList(anchor),
                            this.isNormalised(), false);
            }
            apparatusEntry.append(String.format("\t%s: %s; ", varText, witnessList));
        }
        return apparatusEntry.toString();
    }
}
