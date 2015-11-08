package net.stemmaweb.services;

import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

/**
 * Generalized path evaluator for traversing a witness.
 */

public class WitnessPath {
    private final String sigil;
    private final String alternative;

    public WitnessPath (String sigil) {
        this.sigil = sigil;
        this.alternative = null;
    }

    public Evaluator getEvalForWitness () {
        return path -> {

            if (path.length() == 0) {
                return Evaluation.EXCLUDE_AND_CONTINUE;
            }

            if (alternative != null && path.lastRelationship().hasProperty(alternative)) {
                // Follow the alternative instead of the main witness path
                String[] arr = (String[]) path.lastRelationship().getProperty(alternative);
                for (String str : arr) {
                    if (str.equals(sigil)) {
                        return Evaluation.INCLUDE_AND_CONTINUE;
                    }
                }
            }

            if (path.lastRelationship().hasProperty("witnesses")) {
                String[] arr = (String[]) path.lastRelationship()
                        .getProperty("witnesses");
                for (String str : arr) {
                    if (str.equals(sigil)) {
                        return Evaluation.INCLUDE_AND_CONTINUE;
                    }
                }
            }
            return Evaluation.EXCLUDE_AND_PRUNE;
        };
    }
}

/* public class WitnessPath implements PathEvaluator {

    private final String sigil;
    private final String alternative;

    public WitnessPath (String sigil) {
        this.sigil = sigil;
        this.alternative = null;
    }

    public WitnessPath (String sigil, String alternative) {
        this.sigil = sigil;
        this.alternative = alternative;
    }

    @Override
    public Evaluation evaluate(Path path, BranchState branchState) {
        return evaluate(path);
    }

    @Override
    public Evaluation evaluate(Path path) {
        if (path.length() == 0) {
            return Evaluation.EXCLUDE_AND_CONTINUE;
        }

        String[] arr;
        if (alternative != null && path.lastRelationship().hasProperty(alternative)) {
            // Follow the alternative instead of the main witness path
            arr = (String[]) path.lastRelationship().getProperty(alternative);
            for (String str : arr) {
                if (str.equals(sigil)) {
                    return Evaluation.INCLUDE_AND_CONTINUE;
                }
            }
        }

        // Fall back to the main witness path
        if (path.lastRelationship().hasProperty("witnesses")) {
            arr = (String[]) path.lastRelationship()
                    .getProperty("witnesses");
            for (String str : arr) {
                if (str.equals(sigil)) {
                    return Evaluation.INCLUDE_AND_CONTINUE;
                }
            }
        }
        return Evaluation.EXCLUDE_AND_PRUNE;
    }
}

/*
    private static Evaluator getEvalForWitness(final String WITNESS_ID) {
        return path -> {

            if (path.length() == 0) {
                return Evaluation.EXCLUDE_AND_CONTINUE;
            }

            boolean includes = false;
            boolean continues = false;

            if (path.lastRelationship().hasProperty("witnesses")) {
                String[] arr = (String[]) path.lastRelationship()
                        .getProperty("witnesses");
                for (String str : arr) {
                    if (str.equals(WITNESS_ID)) {
                        includes = true;
                        continues = true;
                    }
                }
            }
            return Evaluation.of(includes, continues);
        };
    }

 */