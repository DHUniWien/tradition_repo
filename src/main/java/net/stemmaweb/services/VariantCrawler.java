package net.stemmaweb.services;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.BranchState;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class VariantCrawler {
    private final Set<String> lemmaLinks;
    private final Set<String> lemmaNodes;
    private final RelationshipType followType;
    private final Set<String> excludeWitnesses;
    // Hash by path string rather than path itself, in case the objects aren't equal
    private final Map<String, Map<String,Set<String>>> pathWitnesses;

    public VariantCrawler(List<Relationship> lp, RelationshipType rt, List<String> excludeWitnesses) {
        this.lemmaLinks = lp.stream().map(Relationship::toString).collect(Collectors.toSet());
        this.lemmaNodes = lp.stream().map(x -> x.getEndNode().getElementId()).collect(Collectors.toSet());
        this.excludeWitnesses = new HashSet<>(excludeWitnesses);
        this.followType = rt;
        this.pathWitnesses = new HashMap<>();
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
            if (this.lemmaNodes.contains(path.endNode().getElementId()))
                return Evaluation.INCLUDE_AND_PRUNE;
            // Otherwise keep going, but don't return the path yet.
            return Evaluation.EXCLUDE_AND_CONTINUE;
        };
    }

    private static String pathKey(Iterable<Relationship> pathRels, Relationship extraRel) {
        String result = StreamSupport.stream(pathRels.spliterator(), false).map(Relationship::toString)
                .collect(Collectors.joining(":"));
        if (extraRel != null)
            result = String.format("%s:%s", result, extraRel);
        return result;
    }

    public PathExpander variantListExpander () {
        return new PathExpander() {
            @Override
            public ResourceIterable<Relationship> expand(Path path, BranchState branchState) {
                // If the path is zero-length, try all continuing paths and record their witnesses
                if (path.length() == 0) {
                	ResourceIterable<Relationship> result = path.endNode().getRelationships(Direction.OUTGOING, followType);
                    for (Relationship r: result) {
                        Map<String,Set<String>> pathWits = new HashMap<>();
                        for (String layer : r.getPropertyKeys()) {
                            List<String> followWits = Arrays.stream((String[]) r.getProperty(layer)).filter(
                                    x -> !excludeWitnesses.contains(x)).collect(Collectors.toList());
                            if (!followWits.isEmpty())
                                pathWits.put(layer, new HashSet<>(followWits));
                        }
                        pathWitnesses.put(r.toString(), pathWits);
                    }
                    return result;
                }

                // If not, we have some work to do
                String key = pathKey(path.relationships(), null);
                Map<String,Set<String>> witsSoFar = pathWitnesses.getOrDefault(key, null);
                if (witsSoFar == null || witsSoFar.isEmpty()) {
                    // We have no "through" witnesses for this path, so don't go any farther.
                    return new ArrayList<>();
                }
                // Now for each witness sigil in witsSoFar, find the relationship that continues it.
                Map<String, Relationship> continuations = new HashMap<>();
                Set<String> baseWits = witsSoFar.getOrDefault("witnesses", new HashSet<>());
                for (Relationship r : path.endNode().getRelationships(Direction.OUTGOING, followType)) {
                    // Do the base layer first.
                    Set<String> relBaseWits = new HashSet<>(Arrays.asList(
                            (String[]) r.getProperty("witnesses", new String[0])));
                    for (String sig : baseWits.stream().filter(relBaseWits::contains).collect(Collectors.toList())) {
                        continuations.put(sig, r);
                    }
                    // Now check whether this relationship continues any non-base witness paths.
                    for (String layer : witsSoFar.keySet()) {
                        if (layer.equals("witnesses")) continue;
                        if (r.hasProperty(layer)) {
                            // Get any layer witnesses that are directly continued
                            Set<String> relLayerWits = new HashSet<>(Arrays.asList((String[]) r.getProperty(layer)));
                            for (String sig : witsSoFar.get(layer).stream().filter(relLayerWits::contains)
                                    .collect(Collectors.toList())) {
                                continuations.put(String.format("%s|%s", sig, layer), r);
                            }
                        }
                        // Get any layer witnesses that have reverted to the base witness, assuming a direct layer
                        // continuation has not been found
                        for (String sig : witsSoFar.get(layer).stream().filter(relBaseWits::contains)
                                .collect(Collectors.toList())) {
                            String contKey = String.format("%s|%s", sig, layer);
                            if (!continuations.containsKey(contKey)) continuations.put(contKey, r);
                        }
                    }

                    // Now get any witnesses that have diverged into some layer from a base witness
                    for (String layer : r.getPropertyKeys()) {
                        if (layer.equals("witnesses")) continue;
                        Set<String> relLayerWits = new HashSet<>(Arrays.asList((String[]) r.getProperty(layer)));
                        for (String sig : relLayerWits.stream().filter(baseWits::contains).collect(Collectors.toList())) {
                            String contKey = String.format("%s|%s", sig, layer);
                            if (!continuations.containsKey(contKey)) continuations.put(contKey, r);
                        }
                    }
                }
                // We now have all our path continuations; note them in the state variable and return the
                // relevant relationships.
                for (String contKey : continuations.keySet()) {
                    String[] parts = contKey.split("\\|"); // witness, layer
                    String sigil = parts[0];
                    String layer = parts.length == 1 ? "witnesses" : parts[1];
                    String pKey = pathKey(path.relationships(), continuations.get(contKey));
                    Map<String,Set<String>> m;
                    if (pathWitnesses.containsKey(pKey)) {
                        m = pathWitnesses.get(pKey);
                    } else {
                        m = new HashMap<>();
                        pathWitnesses.put(pKey, m);
                    }
                    if (m.containsKey(layer)) {
                        m.get(layer).add(sigil);
                    } else {
                        Set<String> w = new HashSet<>();
                        w.add(sigil);
                        m.put(layer, w);
                    }
                }
                return continuations.values();
            }

            @Override
            public PathExpander reverse() {
                return null;
            }
        };
    }

    public Map<String, Set<String>> getWitnessesForPath (Path p) {
        String pKey = pathKey(p.relationships(), null);
        return pathWitnesses.get(pKey);
    }
}
