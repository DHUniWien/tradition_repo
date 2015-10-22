package net.stemmaweb.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Witness;
import net.stemmaweb.services.DatabaseService;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

/**
 * JSON-aware data model for exporting an alignment in tabular format.
 *
 * The result will look like this:
 *  $table = { alignment => [ { witness => "SIGIL",
 *                              tokens => [ { t => "TEXT" }, ... ] },
 *                            { witness => "SIG2",
 *                              tokens => [ { t => "TEXT" }, ... ] },
 *                           ... ],
 *             length => TEXTLEN };
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AlignmentModel {

    private long length;
    private ArrayList<HashMap<String, Object>> alignment;

    public AlignmentModel(Node traditionNode) {
        GraphDatabaseService db = traditionNode.getGraphDatabase();

        try (Transaction tx = db.beginTx()) {
            // First get the length, that's the easy part.
            Node endNode = DatabaseService.getRelated(traditionNode, ERelations.HAS_END).get(0);
            length = (long) endNode.getProperty("rank") - 1;

            // Now make the alignment.
            alignment = new ArrayList<>();
            // For each witness, we make a 'tokens' array of the length of the tradition
            // Get the witnesses in the database
            ArrayList<Node> witnesses = DatabaseService.getRelated(traditionNode, ERelations.HAS_WITNESS);
            for (Node w : witnesses) {
                // Get the REST Witness
                String tradId = traditionNode.getProperty("id").toString();
                String sigil = w.getProperty("sigil").toString();
                Witness witness = new Witness(tradId, sigil);

                // Make the object for the JSON witness row
                HashMap<String, Object> witnessRow = new HashMap<>();
                witnessRow.put("witness", sigil);

                // Get the witness readings
                ArrayList<ReadingModel> witReadings = (ArrayList<ReadingModel>) witness.getWitnessAsReadings().getEntity();
                // Make the object for the JSON token array
                ArrayList<HashMap<String, String>> tokens = new ArrayList<>();
                for (ReadingModel r : witReadings) {
                    // Make the reading token
                    HashMap<String, String> readingToken = new HashMap<>();
                    readingToken.put("t", r.getText());
                    // Put it at its proper rank
                    int currRankIndex = r.getRank().intValue() - 1;
                    for (int i = tokens.size(); i < currRankIndex; i++)
                        tokens.add(null);
                    tokens.add(readingToken);
                }
                // Fill in any empty ranks at the end
                for (int i = tokens.size(); i < length; i++)
                    tokens.add(null);
                witnessRow.put("tokens", tokens);
                alignment.add(witnessRow);
            }
            Comparator<HashMap<String, Object>> bySigil = (o1, o2) -> {
                String wit1 = o1.get("witness").toString();
                String wit2 = o2.get("witness").toString();
                return wit1.compareTo(wit2);
            };
            alignment.sort(bySigil);
            tx.success();
        }
    }

    public ArrayList<HashMap<String, Object>> getAlignment () {
        return alignment;
    }

    public long getLength () {
        return length;
    }
}
