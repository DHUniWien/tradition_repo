package net.stemmaweb.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.WitnessPath;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

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

    // Get an alignment table with no conflation based on relationship
    public AlignmentModel(Node traditionNode) {
        this(traditionNode, new ArrayList<>());
    }

    // Get an alignment table where some of the related readings are conflated.
    public AlignmentModel(Node traditionNode, List<String> conflateRelationships) {
        GraphDatabaseService db = traditionNode.getGraphDatabase();

        try (Transaction tx = db.beginTx()) {
            // First get the length, that's the easy part.
            String tradId = traditionNode.getProperty("id").toString();
            Node startNode = DatabaseService.getStartNode(tradId, db);
            Node endNode = DatabaseService.getEndNode(tradId, db);
            length = (long) endNode.getProperty("rank") - 1;

            // Make a reference list of readings, and their conflation partners.
            // We arbitrarily use the first reading we come to as the reference
            // for all the readings that are equivalent to it.
            HashMap<Node, Node> conflatedReadings = new HashMap<>();
            if (conflateRelationships.size() > 0) {
                PathExpander relationConflater = new PathExpander() {
                    @Override
                    public Iterable<Relationship> expand(Path path, BranchState branchState) {
                        ArrayList<Relationship> relevantRelations = new ArrayList<>();
                        for (Relationship rel : path.endNode().getRelationships(ERelations.RELATED))
                            if (conflateRelationships.contains(rel.getProperty("type").toString()))
                                relevantRelations.add(rel);
                        return relevantRelations;
                    }

                    @Override
                    public PathExpander reverse() {
                        return null;
                    }
                };
                db.traversalDescription().depthFirst()
                        .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                        .evaluator(Evaluators.all())
                        .uniqueness(Uniqueness.NODE_GLOBAL).traverse(startNode)
                        .nodes().forEach(x -> {
                    if (!conflatedReadings.containsKey(x)) {
                        conflatedReadings.put(x, x);
                        // Traverse the readings for the given relationships.
                        ResourceIterable<Node> equivalent = db.traversalDescription().depthFirst()
                                .expand(relationConflater)
                                .evaluator(Evaluators.all())
                                .uniqueness(Uniqueness.NODE_GLOBAL).traverse(x)
                                .nodes();
                                equivalent.forEach(y -> conflatedReadings.put(y, x));
                    }
                });
            }

            // Now make the alignment.
            alignment = new ArrayList<>();
            // For each witness, we make a 'tokens' array of the length of the tradition
            // Get the witnesses in the database
            ArrayList<Node> witnesses = DatabaseService.getRelated(traditionNode, ERelations.HAS_WITNESS);
            for (Node w : witnesses) {
                // Make the object for the JSON witness row
                String sigil = w.getProperty("sigil").toString();
                HashMap<String, Object> witnessRow = new HashMap<>();
                witnessRow.put("witness", sigil);

                // Make the object for the JSON token array
                // Get the witness readings
                // Extract this from the graph DB, not from the REST API.
                // Then it will be easier to use conflatedRelations.
                // But then it will be extra-clear that some refactoring is needed.
                ArrayList<HashMap<String, String>> tokens = new ArrayList<>();
                Evaluator e = new WitnessPath(sigil).getEvalForWitness();
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
                    HashMap<String, String> readingToken = new HashMap<>();
                    readingToken.put("t", r.getProperty("text").toString());

                    // Put it at its proper rank
                    long currRankIndex = (long) r.getProperty("rank") - 1;
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
