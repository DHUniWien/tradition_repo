package net.stemmaweb.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.VariantGraphService;
import net.stemmaweb.services.WitnessPath;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.*;

import javax.xml.bind.annotation.XmlRootElement;
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

@XmlRootElement
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AlignmentModel {

    private long length;
    private ArrayList<WitnessTokensModel> alignment;

    // Make an empty alignment table
    public AlignmentModel() {}

    // Get an alignment table
    public AlignmentModel(Node sectionNode) {
        GraphDatabaseService db = sectionNode.getGraphDatabase();

        try (Transaction tx = db.beginTx()) {
            String sectId = String.valueOf(sectionNode.getId());
            Node traditionNode = VariantGraphService.getTraditionNode(sectionNode);
            Node startNode = VariantGraphService.getStartNode(sectId, db);
            Node endNode = VariantGraphService.getEndNode(sectId, db);

            // First get the length, that's the easy part.
            length = (long) endNode.getProperty("rank") - 1;

            // See if we are computing a normalized table
            RelationshipType seqType = ERelations.SEQUENCE;
            if (startNode.hasRelationship(ERelations.NSEQUENCE, Direction.OUTGOING))
                seqType = ERelations.NSEQUENCE;

            // Get the traverser for the tradition readings
            Traverser traversedTradition = db.traversalDescription().depthFirst()
                    .relationships(seqType, Direction.OUTGOING)
                    .evaluator(Evaluators.all())
                    .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL).traverse(startNode);

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
                    witnessRow.setWitness(sigil);
                    if (!layer.equals("base")) {
                        witnessRow.setLayer(layer);
                    }

                    // Make the object for the JSON token array
                    ArrayList<ReadingModel> tokens = new ArrayList<>();

                    // Get the witness readings for the given layer
                    ArrayList<String> alternatives = new ArrayList<>();
                    if (!layer.equals("base")) alternatives.add(layer);
                    Evaluator e = new WitnessPath(sigil, alternatives, seqType).getEvalForWitness();
                    ReadingModel filler;
                    for (Node r : db.traversalDescription().depthFirst()
                            .relationships(seqType, Direction.OUTGOING)
                            .evaluator(e)
                            .uniqueness(Uniqueness.NODE_PATH)
                            .traverse(startNode)
                            .nodes()) {
                        if (r.hasProperty("is_end"))
                            continue;

                        // Make the reading token
                        ReadingModel readingToken = new ReadingModel(r);
                        // Check whether it was a lacuna
                        if (readingToken.getIs_lacuna())
                            filler = readingToken;
                        else filler = null;

                        // Put it at its proper rank, filling null bzw. lacuna tokens into the gap
                        long currRankIndex = (long) r.getProperty("rank") - 1;
                        for (int i = tokens.size(); i < currRankIndex; i++)
                            tokens.add(filler);
                        tokens.add(readingToken);
                    }
                    // Skip this witness if it is empty
                    if (tokens.size() == 0) continue;

                    // Fill in any empty ranks at the end
                    for (int i = tokens.size(); i < length; i++)
                        tokens.add(null);

                    // Store the witness row and add it to the alignment
                    witnessRow.setTokens(tokens);
                    alignment.add(witnessRow);
                }
            }
            Comparator<WitnessTokensModel> bySigil = Comparator.comparing(WitnessTokensModel::constructSigil);
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
