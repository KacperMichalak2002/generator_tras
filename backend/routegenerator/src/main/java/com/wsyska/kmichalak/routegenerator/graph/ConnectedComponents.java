package com.wsyska.kmichalak.routegenerator.graph;

import java.util.Arrays;

public final class ConnectedComponents {

    private final int[] componentOf;
    private final int[] sizes;

    private ConnectedComponents(int[] componentOf, int[] sizes) {
        this.componentOf = componentOf;
        this.sizes = sizes;
    }

    public static ConnectedComponents of(Graph graph) {
        int n = graph.nodeCount();
        int[] parent = new int[n];
        int[] rank = new int[n];
        for (int v = 0; v < n; v++) {
            parent[v] = v;
        }
        for (int v = 0; v < n; v++) {
            for (int e = graph.firstEdge(v); e < graph.endEdge(v); e++) {
                union(parent, rank, v, graph.edgeTarget(e));
            }
        }

        int[] componentOf = new int[n];
        int[] rootToComponent = new int[n];
        Arrays.fill(rootToComponent, -1);
        int count = 0;
        for (int v = 0; v < n; v++) {
            int root = find(parent, v);
            if (rootToComponent[root] < 0) {
                rootToComponent[root] = count++;
            }
            componentOf[v] = rootToComponent[root];
        }
        int[] sizes = new int[count];
        for (int v = 0; v < n; v++) {
            sizes[componentOf[v]]++;
        }
        return new ConnectedComponents(componentOf, sizes);
    }

    public int count() {
        return sizes.length;
    }

    public int componentOf(int node) {
        return componentOf[node];
    }

    public int size(int component) {
        return sizes[component];
    }

    public int largestSize() {
        int max = 0;
        for (int size : sizes) {
            max = Math.max(max, size);
        }
        return max;
    }

    public boolean[] nodesInComponentsOfAtLeast(int minNodes) {
        boolean[] keep = new boolean[componentOf.length];
        for (int v = 0; v < keep.length; v++) {
            keep[v] = sizes[componentOf[v]] >= minNodes;
        }
        return keep;
    }

    private static int find(int[] parent, int v) {
        while (parent[v] != v) {
            parent[v] = parent[parent[v]];
            v = parent[v];
        }
        return v;
    }

    private static void union(int[] parent, int[] rank, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra == rb) {
            return;
        }
        if (rank[ra] < rank[rb]) {
            int t = ra;
            ra = rb;
            rb = t;
        }
        parent[rb] = ra;
        if (rank[ra] == rank[rb]) {
            rank[ra]++;
        }
    }
}
