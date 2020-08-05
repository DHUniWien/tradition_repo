package net.stemmaweb.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.services.RelationService;
import net.stemmaweb.services.VariantCrawler;
import net.stemmaweb.services.VariantGraphService;
import net.stemmaweb.services.WitnessPath;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.graphdb.traversal.Uniqueness;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.*;
import java.util.stream.Collectors;

@XmlRootElement
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class VariantListModel {

    /**
     * the list of variant locations in this section
     */
    private List<VariantLocationModel> variantlist;
    /**
     * whether we are moving dislocated (e.g. transposed) variants to their matching base
     */
    private boolean dislocationCombined = false;
    /**
     * whether this list has excluded type-1 (singleton) variants
     */
    private boolean filterTypeOne = false;
    /**
     * whether this list suppresses any variants marked with the is_nonsense property
     */
    private boolean nonsenseSuppressed = false;
    /**
     * the witness that was used as the base text. Can also be "lemma" or "majority".
     */
    private String basisText;
    /**
     * the relation name, if any, that the text was normalized on prior to producing the variant list
     */
    private String conflateOnRelation;
    /**
     * the minimum level of relation significance that the variants in this list are linked with
     */
    private RelationModel.Significance significant = RelationModel.Significance.no;
    /**
     * the regular expression, if any, that was used to filter readings in the list
     */
    private String suppressedReadingsRegex;
    /**
     * the types of relations that are dislocations in this tradition. Not publicly accessible.
     */
    private List<String> dislocationTypes;

    public VariantListModel() {
        variantlist = new ArrayList<>();
        suppressedReadingsRegex = "^$";
        conflateOnRelation = "";
    }

    /**
     * Generate a list of variants for the given section.
     *
     * @param sectionNode - the section to generate the list for
     * @param baseWitness - the witness sigil to indicate the base text, if any
     * @param conflate    - the name of a relation that should be the basis for text normalisation, if any
     * @param suppress    - a regular expression of readings that should be excluded from the variant list
     * @param filterNonsense - whether to exclude readings marked as nonsense readings
     * @param filterTypeOne - whether to filter out so-called "type 1" variants
     * @param significant - whether to filter out variants with significance less than indicated. Possible
     *                    values are "no", "maybe" and "yes".
     * @param combine     - whether to move variants marked as dislocations to the variant location of
     *                    their corresponding base readings
     */
    public VariantListModel(Node sectionNode, String baseWitness, String conflate, String suppress, Boolean filterNonsense,
                            Boolean filterTypeOne, String significant, Boolean combine) throws Exception {
        // Initialize our instance properties
        this.variantlist = new ArrayList<>();
        this.conflateOnRelation = conflate;
        // Set the right regex for our 'suppress' value
        if (suppress.equals("punct"))
            this.suppressedReadingsRegex = "^(\\p{IsPunctuation}+)$";
        else if (suppress.equals("NONE") || suppress.equals("none"))
            this.suppressedReadingsRegex = "^$";
        else
            this.suppressedReadingsRegex = suppress;
        this.nonsenseSuppressed = filterNonsense;
        this.filterTypeOne = filterTypeOne;
        this.significant = RelationModel.Significance.valueOf(significant);
        this.dislocationCombined = combine;
        if (conflate == null) conflate = "";
        GraphDatabaseService db = sectionNode.getGraphDatabase();
        try (Transaction tx = db.beginTx()) {
            RelationshipType follow = ERelations.SEQUENCE;
            if (!conflate.equals("")) {
                VariantGraphService.normalizeGraph(sectionNode, conflate);
                follow = ERelations.NSEQUENCE;
            }

            // Figure out which types are dislocation types in this tradition
            this.dislocationTypes = new ArrayList<>();
            for (RelationTypeModel rtm : RelationService.ourRelationTypes(sectionNode)) {
                if (!rtm.getIs_colocation())
                    dislocationTypes.add(rtm.getName());
            }

            // See which list of readings will serve as our base text
            Node startNode = VariantGraphService.getStartNode(String.valueOf(sectionNode.getId()), db);
            TraversalDescription baseWalker = db.traversalDescription().depthFirst();
            List<Relationship> baseText;
            if (baseWitness != null) {
                // We use the requested witness text, which is connected via SEQUENCE or NSEQUENCE
                // links and so unproblematic.
                baseWalker = baseWalker.evaluator(new WitnessPath(baseWitness, follow).getEvalForWitness());
                baseText = baseWalker.traverse(startNode).relationships().stream().collect(Collectors.toList());
                this.basisText = baseWitness;
            } else {
                // We collect the readings, but count their SEQUENCE or NSEQUENCE links in the base text.
                List<Node> baseReadings;
                if (startNode.hasRelationship(ERelations.LEMMA_TEXT, Direction.OUTGOING)) {
                    // We traverse the lemma text
                    baseWalker = baseWalker.relationships(ERelations.LEMMA_TEXT);
                    baseReadings = baseWalker.traverse(startNode).nodes().stream().collect(Collectors.toList());
                    this.basisText = "lemma";
                } else {
                    // We calculate and use the majority text
                    baseReadings = VariantGraphService.calculateMajorityText(sectionNode);
                    this.basisText = "majority";
                }
                baseText = new ArrayList<>();
                Node prior = baseReadings.remove(0);
                for (Node curr : baseReadings) {
                    prior.getRelationships(follow, Direction.OUTGOING).forEach(x -> {
                        if (x.getEndNode().equals(curr)) baseText.add(x);});
                    prior = curr;
                }
            }

            this.findVariants(db, baseText, follow);

            // Filter readings by regex / nonsense flag as needed. Pass the base text in case
            // any before/after reading settings need to be altered.
            List<ReadingModel> baseChain = baseText.stream().map(x -> new ReadingModel(x.getEndNode())).collect(Collectors.toList());
            baseChain.add(0, new ReadingModel(baseText.get(0).getStartNode()));
            this.filterReadings(baseChain);

            // Filter for type1 variants
            if (filterTypeOne)
                this.variantlist = this.variantlist.stream().filter(x -> !isTypeOne(x)).collect(Collectors.toList());

            // Filter for significant variants
            if (!significant.equals("no"))
                this.variantlist = this.variantlist.stream().filter(this::meetsSignificance).collect(Collectors.toList());

            // Combine dislocations if we were asked to
            if (combine) this.combineDisplacements();

            // Clean up if we normalised
            if (!conflate.equals(""))
                VariantGraphService.clearNormalization(sectionNode);

            tx.success();
        }
    }

    private void findVariants (GraphDatabaseService db, List<Relationship> sequence, RelationshipType follow) {
        // Create the evaluator we need
        Evaluator vle = new VariantCrawler(sequence).variantListEvaluator();
        // Set up the traversal for the path segments we want
        try (Transaction tx = db.beginTx()) {
            TraversalDescription traverser = db.traversalDescription().depthFirst()
                    .relationships(follow, Direction.OUTGOING)
                    .uniqueness(Uniqueness.RELATIONSHIP_PATH)
                    .evaluator(vle);
            // Get our base chain of nodes
            List<Node> baseChain = sequence.stream().map(Relationship::getEndNode).collect(Collectors.toList());
            baseChain.add(0, sequence.get(0).getStartNode());
            // We have to run the traverser from each node in the base chain, to get any variants that start there.
            for (Node n : baseChain) {
                for (org.neo4j.graphdb.Path v : traverser.traverse(n)) {
                    VariantModel vm = new VariantModel(v);
                    // Sanity check
                    // if (!baseChain.contains(v.startNode()) || !baseChain.contains(v.endNode()))
                    //     throw new Exception("Variant chain disconnected from base chain");
                    if (!vm.isEmpty()) {
                        VariantLocationModel vloc = this.getVLM(baseChain, v.startNode(), v.endNode());
                        vloc.addVariant(vm);
                    }
                }
            }
            tx.success();
        }

        // Add relation information to each variant location. This will also notice displaced variants.
        for (VariantLocationModel vlm : this.getVariantlist())
            vlm.collectRelationsInLocation(db, this.dislocationTypes);
        // Sort the result by rank index and base text length, and return
        this.getVariantlist().sort(Comparator.comparingInt(x -> x.getBase().size()));
        this.getVariantlist().sort(Comparator.comparingLong(VariantLocationModel::getRankIndex));
    }

    private VariantLocationModel getVLM(List<Node> baseChain,
                                        Node vStart,
                                        Node vEnd) {
        // Retrieve any existing VariantLocationModel, or create a new one
        VariantLocationModel vlm = new VariantLocationModel();
        String key = String.format("%d -- %d", vStart.getId(), vEnd.getId());
        Optional<VariantLocationModel> ovlm = this.getVariantlist().stream()
                .filter(x -> key.equals(x.lookupKey())).findFirst();
        if (ovlm.isPresent()) {
            vlm = ovlm.get();
        }
        // Initialize the VLModel if it is new.
        if (vlm.getRankIndex() == 0) {
            // Turn our sub-chain into reading models
            List<ReadingModel> baseReadings = baseChain
                    .subList(baseChain.indexOf(vStart), baseChain.indexOf(vEnd)+1)
                    .stream().map(ReadingModel::new).collect(Collectors.toList());
            // Set the reading models in place in the VLM
            vlm.setBefore(baseReadings.remove(0));
            vlm.setAfter(baseReadings.remove(baseReadings.size() - 1));
            vlm.setBase(baseReadings);
            // Set the rank index to the rank of the first base reading
            if (baseReadings.size() > 0)
                vlm.setRankIndex(baseReadings.get(0).getRank());
            else
                vlm.setRankIndex(vlm.getBefore().getRank() + 1);
            vlm.setNormalised(vStart.hasRelationship(ERelations.NSEQUENCE, Direction.OUTGOING));
            this.variantlist.add(vlm);
        }
        return vlm;
    }

    /**
     * Checks whether a VariantLocationModel is "type 1", i.e. none of the variants appear in more than
     * a single witness.
     *
     * @param vloc - The VariantLocationModel to check
     * @return true or false
     */
    private static boolean isTypeOne(VariantLocationModel vloc) {
        boolean is_type1 = true;
        for (VariantModel vm : vloc.getVariants()) {
            is_type1 = is_type1 && vm.getWitnessList().size() == 1;
        }
        return is_type1;
    }

    /**
     * Tests whether the variant location in question meets a significance test. The test succeeds
     * if any of the relations between the base and the variant(s) meet the given threshold.
     *
     * @param vloc - The variant location to test
     * @return true or false
     */
    private boolean meetsSignificance (VariantLocationModel vloc) {
        // If there are no relations associated with the variant, it can't be significant.
        boolean meets = false;
        for (RelationModel rm : vloc.getRelations())
            if (this.significant.equals(RelationModel.Significance.maybe))
                meets = meets || !rm.getIs_significant().equals("no");
            else if (this.significant.equals(RelationModel.Significance.yes))
                meets = meets || rm.getIs_significant().equals("yes");
        return meets;
    }


    private void filterReadings(List<ReadingModel> baseText) {
        // For each VLM in our list, filter it
        for (VariantLocationModel vlm : this.getVariantlist())
            vlm.filterReadings(this.suppressedReadingsRegex, this.nonsenseSuppressed, baseText);

        // Then re-add all VLMs, which will control for duplicates
        List<VariantLocationModel> existing = this.getVariantlist().stream().filter(x -> !x.isEmpty())
                .collect(Collectors.toList());
        this.variantlist = new ArrayList<>();
        for (VariantLocationModel vlm : existing) this.addVLM(vlm);

    }

    // Deal with non-colocated variants
    private void combineDisplacements() {
        List<VariantLocationModel> result = new ArrayList<>();
        // TODO do we need this index or will getVLM() do what we need?
        // Make an index of our base reading sequences and the VLMs they appear in
        HashMap<String,VariantLocationModel> lemmaIndex = new HashMap<>();
        List<VariantLocationModel> vlmlist = this.getVariantlist();
        for (VariantLocationModel vlm : vlmlist) {
            String baseKey = vlm.getBase().stream().map(ReadingModel::getId).collect(Collectors.joining("-"));
            lemmaIndex.put(baseKey, vlm);
        }
        // - Get the list of displacements
        vlmlist.stream().filter(VariantLocationModel::hasDisplacement).forEach(vlm -> {
            // - Find the variant location that corresponds to each of the addition + omission
            // Which variant(s) is dislocated?
            List<VariantModel> toDelete = new ArrayList<>();
            vlm.getVariants().stream().filter(VariantModel::getDisplaced).forEach(vm -> {
                // This is a displaced variant that should be moved to its "real" variant location,
                // which we can find by following the respective relation.
                List<String> vmrids = vm.getReadings().stream().map(ReadingModel::getId).collect(Collectors.toList());
                List<RelationModel> vmrels = vlm.getRelations().stream()
                        .filter(x -> vmrids.contains(x.getSource()) || vmrids.contains(x.getTarget()))
                        .collect(Collectors.toList());
                VariantLocationModel displacedBase = findDisplacement(vm, vmrels, lemmaIndex);
                if (displacedBase != null) {
                    // Remove this VariantModel from the current VLM and add it to the one that was found.
                    toDelete.add(vm);
                    displacedBase.addVariant(vm);
                    // Add an anchor for the displaced variant
                    vm.setAnchor( vlm.getBefore().getRank() > displacedBase.getBefore().getRank()
                            ? vlm.getBefore() : vlm.getAfter() );
                }
            });
            vlm.setVariants(vlm.getVariants().stream().filter(x -> !toDelete.contains(x)).collect(Collectors.toList()));

        });
        // - Remove any now-empty variant locations
        this.variantlist = vlmlist.stream().filter(x -> x.getVariants().size() > 0).collect(Collectors.toList());

        // - TODO condense symmetrical transpositions
    }

    // Return the VLM that contains the base text corresponding to the given variant. The base text
    // corresponds if each of its readings, in sequence, has the same type of displacement to each
    // of the variant readings, in sequence.
    private VariantLocationModel findDisplacement(VariantModel vm,
                                                  List<RelationModel> relations,
                                                  HashMap<String,VariantLocationModel> lemmaIndex) {
        List<String> baseIds = new ArrayList<>();
        String relationType = null;
        for (ReadingModel rdgm : vm.getReadings()) {
            // TODO can we just add the dislocation type list to this filter and skip the 'continue' below?
            List<RelationModel> rdgrels = relations.stream()
                    .filter(x -> x.getSource().equals(rdgm.getId()) || x.getTarget().equals(rdgm.getId()))
                    .collect(Collectors.toList());
            boolean alreadyLinked = false;
            for (RelationModel relm : rdgrels) {
                if (!this.dislocationTypes.contains(relm.getType())) continue;
                // If we have already linked this reading to a different base reading via a non-colocation,
                // it is too complicated for our algorithm and we will not try to match it anymore.
                if (alreadyLinked) return null;
                // If we find that the type of displacement has changed, it is too complicated for our
                // algorithm and we will not try to match it anymore.
                if (relationType != null && !relm.getType().equals(relationType)) return null;
                // If we haven't already made a link between this reading and a base elsewhere, and there
                // is a relevant relation, get the other end of it.
                if (relm.getSource().equals(rdgm.getId()))
                    baseIds.add(relm.getTarget());
                else
                    baseIds.add(relm.getSource());
                alreadyLinked = true;
                relationType = relm.getType();
            }
        }
        // Find the relevant variant location for this displaced variant
        String baseKey = String.join("-", baseIds);
        return lemmaIndex.getOrDefault(baseKey, null);
    }

    /**
     * Adds a new variant location model to the list, ensuring no duplication
     * @param newVLM the VLM to add, or to merge with an existing identical one
     */
    private void addVLM(VariantLocationModel newVLM) {
        Optional<VariantLocationModel> existing = this.getVariantlist().stream()
                .filter(x -> newVLM.lookupKey().equals(x.lookupKey())).findFirst();
        boolean merged = false;
        if (existing.isPresent()) {
            VariantLocationModel oldVLM = existing.get();
            if (oldVLM.sameAs(newVLM)) {
                for (VariantModel vm : newVLM.getVariants())
                    oldVLM.addVariant(vm);
                merged = true;
            }
        }
        if (!merged) this.variantlist.add(newVLM);

    }

    /* Access methods */

    public List<VariantLocationModel> getVariantlist() {
        return variantlist;
    }

    public boolean isDislocationCombined() {
        return dislocationCombined;
    }

    public boolean isNonsenseSuppressed() {
        return nonsenseSuppressed;
    }

    public String getSuppressedReadingsRegex() {
        return suppressedReadingsRegex;
    }

    public String getBasisText() {
        return basisText;
    }

    public String getConflateOnRelation() {
        return conflateOnRelation;
    }

    public boolean isFilterTypeOne() {
        return filterTypeOne;
    }

    public RelationModel.Significance getSignificant() {
        return significant;
    }

    public List<String> getDislocationTypes() {
        return dislocationTypes;
    }

}
