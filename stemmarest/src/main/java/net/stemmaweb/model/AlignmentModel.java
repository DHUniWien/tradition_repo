package net.stemmaweb.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.RelationService;
import net.stemmaweb.services.WitnessPath;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.*;

import java.util.*;

/**
 * JSON-aware data model for exporting an alignment in tabular format. Uses ReadingModel to
 * represent the reading tokens.
 *
 * The result will look like this:
 *  $table = { alignment: [ { witness: "SIGIL",
 *                            tokens: [ { id: 123, text: "TEXT", normal_form: "NORMAL, ... }, ... ] },
 *                          { witness: "SIG2",
 *                            tokens: [ { id: 456, text: "TEXT", normal_form: "NORMAL, ... }, ... ] },
 *                           ... ],
 *             length => TEXTLEN };
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AlignmentModel {

    // A small class for each witness "column" in the alignment table
    public class WitnessTokensModel {
        String witness;
        String base;
        ArrayList<ReadingModel> tokens;

        public void setWitness(String witness) {this.witness = witness;}
        public String getWitness() {return this.witness;}
        public void setBase(String base) {this.base = base;}
        public String getBase() {return this.base;}
        public Boolean hasBase() {return this.base != null;}
        public void setTokens (ArrayList<ReadingModel> tokens) {this.tokens = tokens;}
        public ArrayList<ReadingModel> getTokens() {return this.tokens;}
    }

    private long length;
    private ArrayList<WitnessTokensModel> alignment;

    // Make an empty alignment table
    public AlignmentModel() {}

    // Get an alignment table where some of the related readings are conflated.
    public AlignmentModel(Node sectionNode, List<String> conflateRelations) {
        GraphDatabaseService db = sectionNode.getGraphDatabase();

        try (Transaction tx = db.beginTx()) {
            String sectId = String.valueOf(sectionNode.getId());
            Node traditionNode = DatabaseService.getTraditionNode(sectionNode, db);
            String tradId = traditionNode.getProperty("id").toString();
            Node startNode = DatabaseService.getStartNode(sectId, db);
            Node endNode = DatabaseService.getEndNode(sectId, db);

            // First get the length, that's the easy part.
            length = (long) endNode.getProperty("rank") - 1;

            // Get the traverser for the tradition readings
            Traverser traversedTradition = db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .evaluator(Evaluators.all())
                    .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL).traverse(startNode);

            // Make a reference list of readings, and their conflation partners.
            // We arbitrarily use the first reading we come to as the reference
            // for all the readings that are equivalent to it.
            HashMap<Node, Node> conflatedReadings = new HashMap<>();
            for (String conflate : conflateRelations) {
                RelationTypeModel crtm = RelationService.returnRelationType(tradId, conflate);
                RelationService.RelationTraverse reltraverser = new RelationService.RelationTraverse(tradId, crtm);
                traversedTradition.nodes().forEach(x -> {  // For each reading node in the section...
                    // If it isn't represented by anyone else yet, it's represented by itself
                    if (!conflatedReadings.containsKey(x)) conflatedReadings.put(x, x);
                    // Get the representative
                    Node referenceReading = conflatedReadings.get(x);
                    // Traverse the readings for the given relation and its subsidiaries, and
                    // note them all as being represented by referenceReading.
                    ResourceIterable<Node> equivalent = db.traversalDescription().depthFirst()
                            .relationships(ERelations.RELATED)
                            .evaluator(reltraverser)
                            .uniqueness(Uniqueness.NODE_GLOBAL).traverse(x)
                            .nodes();
                            equivalent.forEach(y -> conflatedReadings.put(y, referenceReading));

                });
            }

            // Now make the alignment.
            alignment = new ArrayList<>();
            // For each witness, we make a 'tokens' array of the length of the tradition
            // Get the witnesses in the database
            ArrayList<Node> witnesses = DatabaseService.getRelated(traditionNode, ERelations.HAS_WITNESS);
            for (Node w : witnesses) {
                String sigil = w.getProperty("sigil").toString();
                // Find out which witness layers we need to deal with
                HashSet<String> layers = new HashSet<>();
                layers.add("base");
                for (Relationship seq : traversedTradition.relationships()) {
                    for (String layer : seq.getPropertyKeys()) {
                        if (!layer.equals("witnesses")) {
                            ArrayList<String> layerwits = new ArrayList<>(Arrays.asList((String[]) seq.getProperty(layer)));
                            if (layerwits.contains(sigil)) layers.add(layer);
                        }
                    }
                }

                // Now for each layer iteration, produce a set of tokens.
                for (String layer : layers) {
                    WitnessTokensModel witnessRow = new WitnessTokensModel();
                    if (layer.equals("base")) witnessRow.setWitness(sigil);
                    else {
                        witnessRow.setWitness(String.format("%s (%s)", sigil, layer));
                        witnessRow.setBase(sigil);
                    }

                    // Make the object for the JSON token array
                    ArrayList<ReadingModel> tokens = new ArrayList<>();

                    // Get the witness readings for the given layer
                    ArrayList<String> alternatives = new ArrayList<>();
                    if (!layer.equals("base")) alternatives.add(layer);
                    Evaluator e = new WitnessPath(sigil, alternatives).getEvalForWitness();
                    for (Node r : db.traversalDescription().depthFirst()
                            .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                            .evaluator(e)
                            .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL)
                            .traverse(startNode)
                            .nodes()) {
                        if (r.hasProperty("is_end"))
                            continue;
                        // Get the reading we should use
                        if (conflatedReadings.containsKey(r))
                            r = conflatedReadings.get(r);

                        // Make the reading token
                        ReadingModel readingToken = new ReadingModel(r);

                        // Put it at its proper rank, filling null tokens into the gap
                        long currRankIndex = (long) r.getProperty("rank") - 1;
                        for (int i = tokens.size(); i < currRankIndex; i++)
                            tokens.add(null);
                        tokens.add(readingToken);
                    }
                    // Skip this witness if it is the base and only layer, and empty
                    if (tokens.size() == 0 && layer.equals("base") && layers.size() == 1) continue;

                    // Fill in any empty ranks at the end
                    for (int i = tokens.size(); i < length; i++)
                        tokens.add(null);

                    // Store the witness row and add it to the alignment
                    witnessRow.setTokens(tokens);
                    alignment.add(witnessRow);
                }
            }
            Comparator<WitnessTokensModel> bySigil = Comparator.comparing(WitnessTokensModel::getWitness);
            alignment.sort(bySigil);
            tx.success();
        }
    }

    public ArrayList<WitnessTokensModel> getAlignment () {
        return alignment;
    }

    public void addWitness (WitnessTokensModel wtm) {
        if (alignment == null) alignment = new ArrayList<>();
        alignment.add(wtm);
    }

    public long getLength () {
        return length;
    }
    public void setLength (long length) {this.length = length;}
}
