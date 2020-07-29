package net.stemmaweb.services;

import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

public class VariantCrawler {
    private final Set<String> lemmaLinks;
    private final Set<Long> lemmaNodes;

    public VariantCrawler(List<Relationship> lp) {
        this.lemmaLinks = lp.stream().map(Relationship::toString).collect(Collectors.toSet());
        this.lemmaNodes = lp.stream().map(x -> x.getEndNode().getId()).collect(Collectors.toSet());
    }

    public Evaluator variantListEvaluator() {
        return path -> {
            // We don't want to return zero-length paths
            if (path.length() == 0)
                return Evaluation.EXCLUDE_AND_CONTINUE;
            // Are we on the lemma path? If so, truncate before.
            if (this.lemmaLinks.contains(path.lastRelationship().toString()))
                return Evaluation.EXCLUDE_AND_PRUNE;
            // Have we hit a lemma node again? If so, truncate after.
            if (this.lemmaNodes.contains(path.endNode().getId()))
                return Evaluation.INCLUDE_AND_PRUNE;
            // Otherwise keep going, but don't return the path yet.
            return Evaluation.EXCLUDE_AND_CONTINUE;
        };
    }
}
