package net.stemmaweb.services;

import net.stemmaweb.rest.ERelations;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

/**
 * Generalized path evaluator for traversing a witness.
 */

public class WitnessPath {
    private final String sigil;
    private final String alternative;

    public WitnessPath (String sigil, String alternative) {
        this.sigil = sigil;
        this.alternative = alternative;
    }
    public WitnessPath (String sigil) {
        this.sigil = sigil;
        this.alternative = null;
    }

    public Evaluator getEvalForWitness () {
        return path -> {

            if (path.length() == 0) {
                return Evaluation.EXCLUDE_AND_CONTINUE;
            }

            if (alternative != null) {
                // Follow the alternative path if it includes the witness
                if (path.lastRelationship().hasProperty(alternative)
                        && witnessIn(path.lastRelationship().getProperty(alternative))) {
                    return Evaluation.INCLUDE_AND_CONTINUE;

                // Don't follow the main path if the alternative path exists
                } else if (path.lastRelationship().hasProperty("witnesses")) {
                    Node priorNode = path.lastRelationship().getStartNode();
                    for (Relationship r : priorNode.getRelationships(Direction.OUTGOING, ERelations.SEQUENCE)) {
                        if (r.hasProperty(alternative) && witnessIn(r.getProperty(alternative)))
                            return Evaluation.EXCLUDE_AND_PRUNE;
                    }
                }
            }

            // Follow the main path in the absence of an alternative
            if (path.lastRelationship().hasProperty("witnesses")
                    && witnessIn(path.lastRelationship().getProperty("witnesses")))
                return Evaluation.INCLUDE_AND_CONTINUE;

            return Evaluation.EXCLUDE_AND_PRUNE;
        };
    }

    private Boolean witnessIn (Object property) {
        String[] arr = (String []) property;
        for (String str : arr) {
            if (str.equals(sigil)) {
                return true;
            }
        }
        return false;
    }
}
