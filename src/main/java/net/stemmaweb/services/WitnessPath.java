package net.stemmaweb.services;

import net.stemmaweb.rest.ERelations;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

import java.util.ArrayList;
import java.util.List;

/**
 * Generalized path evaluator for traversing a witness.
 */

public class WitnessPath {
    private final String sigil;
    private final List<String> alternative;
    private final RelationshipType seqType;

    public WitnessPath (String sigil, List<String> alternative, RelationshipType seqType) {
        this.sigil = sigil;
        this.alternative = alternative;
        this.seqType = seqType;
    }

    public WitnessPath (String sigil, RelationshipType seqType) {
        this.sigil = sigil;
        this.alternative = new ArrayList<>();
        this.seqType = seqType;
    }

    public WitnessPath (String sigil, List<String> alternative) {
        this.sigil = sigil;
        this.alternative = alternative;
        this.seqType = ERelations.SEQUENCE;
    }
    public WitnessPath (String sigil) {
        this.sigil = sigil;
        this.alternative = new ArrayList<>();
        this.seqType = ERelations.SEQUENCE;
    }

    public Evaluator getEvalForWitness () {
        return path -> {

            if (path.length() == 0) {
                return Evaluation.EXCLUDE_AND_CONTINUE;
            }
            // Find all relevant alternative paths out from last node; there should be zero or one.
            Relationship correct = null;
            for (String layer : alternative) {
                Node priorNode = path.lastRelationship().getStartNode();
                for (Relationship r : priorNode.getRelationships(Direction.OUTGOING, seqType))
                    if (r.hasProperty(layer) && witnessIn(r.getProperty(layer)))
                        if (correct != null) // There is more than one relevant path; cut the tree off.
                            return Evaluation.EXCLUDE_AND_PRUNE;
                        else
                            correct = r;
            }
            // There is one relevant path; return depending on whether that path is us.
            if (correct != null)
                return correct.equals(path.lastRelationship())
                        ? Evaluation.INCLUDE_AND_CONTINUE : Evaluation.EXCLUDE_AND_PRUNE;

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
