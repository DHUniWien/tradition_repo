package net.stemmaweb.services;

import org.neo4j.graphdb.Node;

import java.util.*;

public class UnionFind {
    private Map<Node, Node> parent = new HashMap<>();
    public UnionFind(Collection<Node> nodes) {
        for (Node node : nodes) parent.put(node, node);
    }
    public Node find(Node node) {
        // Here lies the recursion
        if (parent.get(node) != node)
            parent.put(node, find(parent.get(node)));
        return parent.get(node);
    }
    public boolean union(Node a, Node b) {
        // Returns a boolean value so that we can use this for cycle detection as well
        // as grouping.
        Node rootA = find(a);
        Node rootB = find(b);
        if (rootA == rootB) return false; // Cycle detected
        parent.put(rootA, rootB);
        return true;
    }
    public Map<Node, Long> returnSets() {
        // Converts the arbitrary parent node to an index number, to mimic the Neo4J UnionFindProc
        Map<Node, Long> sets = new HashMap<>();
        Map<Node, Long> result = new HashMap<>();
        long i = 0L;
        for (Node node : parent.keySet()) {
            Node p = parent.get(node);
            if (!sets.containsKey(p))
                sets.put(p, i++);
            result.put(p, sets.get(p));
        }
        return result;
    }
}
