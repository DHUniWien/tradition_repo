package net.stemmaweb.services;

import org.neo4j.graphdb.Node;

import java.util.*;

/**
 * A local implementation of UnionFind, specifically for Neo4J nodes, without having to call out to Cypher.
 * The 'union' method returns a Boolean, for the case when we use this algorithm to do cycle detection.
 */
public class UnionFind {
    private final int[] parent;
    private final int[] size;
    private final Map<Node, Integer> nodeToIndex;
    private final Map<Integer, Node> indexToNode;

    /**
     * Create a UnionFind instance over the given set of Neo4J nodes. This will be static.
     * @param nodes - the set of nodes we are working with. This can't be changed later.
     */
    public UnionFind(Collection<Node> nodes) {
        int nodeCount = nodes.size();
        this.parent = new int[nodeCount];
        this.size = new int[nodeCount];
        this.nodeToIndex = new HashMap<>();
        this.indexToNode = new HashMap<>();

        int index = 0;
        for (Node node : nodes) {
            nodeToIndex.put(node, index);
            indexToNode.put(index, node);
            parent[index] = index;
            size[index] = 1;
            index++;
        }
    }

    public Node find(Node node) {
        if (!nodeToIndex.containsKey(node)) {
            throw new IllegalArgumentException("Node not in initial set");
        }

        int p = nodeToIndex.get(node);
        int root = p;
        while (root != parent[root]) {
            root = parent[root];
        }

        // Path compression
        while (p != root) {
            int next = parent[p];
            parent[p] = root;
            p = next;
        }

        return indexToNode.get(root);
    }

    /**
     * Register a connection between two nodes, collapsing them into the same set.
     *
     * @param p - the source node
     * @param q - the target node
     * @return a boolean indication of whether a new union was made.
     */
    public boolean union(Node p, Node q) {
        Node rootP = find(p);
        Node rootQ = find(q);

        if (rootP.equals(rootQ)) return false;

        int i = nodeToIndex.get(rootP);
        int j = nodeToIndex.get(rootQ);

        if (size[i] < size[j]) {
            parent[i] = j;
            size[j] += size[i];
        } else {
            parent[j] = i;
            size[i] += size[j];
        }
        return true;
    }

    /**
     * Return the (weakly) connected components of the graph as sets of nodes.
     *
     * @return a list of sets of connected nodes
     */
    public List<Set<Node>> connectedSets() {
        Map<Node, Set<Node>> components = new HashMap<>();
        for (Node node : nodeToIndex.keySet()) {
            Node root = find(node);
            components.computeIfAbsent(root, k -> new HashSet<>()).add(node);
        }
        return new ArrayList<>(components.values());
    }

}

