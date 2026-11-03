package com.wsyska.kmichalak.routegenerator.routing;

import java.util.Arrays;

public record PathResult(int[] nodes, int[] edges, double cost, double lengthM) {

    public PathResult {
        nodes = nodes.clone();
        edges = edges.clone();
    }

    @Override
    public int[] nodes() {
        return nodes.clone();
    }

    @Override
    public int[] edges() {
        return edges.clone();
    }

    public int source() {
        return nodes[0];
    }

    public int target() {
        return nodes[nodes.length - 1];
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PathResult other
                && Arrays.equals(nodes, other.nodes)
                && Arrays.equals(edges, other.edges)
                && Double.compare(cost, other.cost) == 0
                && Double.compare(lengthM, other.lengthM) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(nodes) + Double.hashCode(cost);
    }

    @Override
    public String toString() {
        return "PathResult[nodes=" + Arrays.toString(nodes)
                + ", cost=" + cost + ", lengthM=" + lengthM + "]";
    }
}
