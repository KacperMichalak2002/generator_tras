package com.wsyska.kmichalak.routegenerator.routing;

import com.wsyska.kmichalak.routegenerator.graph.Graph;

import java.util.Arrays;
import java.util.Optional;
import java.util.PriorityQueue;


public final class Dijkstra implements Router {

    private final Graph graph;

    public Dijkstra(Graph graph) {
        this.graph = graph;
    }

    @Override
    public Optional<PathResult> route(int source, int target, EdgeCost cost) {
        checkNode(source);
        checkNode(target);

        int n = graph.nodeCount();
        double[] dist = new double[n];
        int[] prevEdge = new int[n];
        int[] prevNode = new int[n];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        Arrays.fill(prevEdge, -1);

        PriorityQueue<Entry> queue = new PriorityQueue<>();
        dist[source] = 0;
        queue.add(new Entry(source, 0));

        while (!queue.isEmpty()) {
            Entry current = queue.poll();
            int v = current.node();
            if (current.dist() > dist[v]) {
                continue;
            }
            if (v == target) {
                return Optional.of(buildPath(source, target, dist[target], prevEdge, prevNode));
            }
            for (int e = graph.firstEdge(v); e < graph.endEdge(v); e++) {
                double c = cost.cost(graph, e);
                if (EdgeCost.isForbidden(c)) {
                    continue;
                }
                if (c < 0 || Double.isNaN(c)) {
                    throw new IllegalStateException("Ujemny lub nieokreślony koszt krawędzi " + e + ": " + c);
                }
                int w = graph.edgeTarget(e);
                double candidate = dist[v] + c;
                if (candidate < dist[w]) {
                    dist[w] = candidate;
                    prevEdge[w] = e;
                    prevNode[w] = v;
                    queue.add(new Entry(w, candidate));
                }
            }
        }
        return Optional.empty();
    }

    private PathResult buildPath(int source, int target, double cost, int[] prevEdge, int[] prevNode) {
        int hops = 0;
        for (int v = target; v != source; v = prevNode[v]) {
            hops++;
        }
        int[] nodes = new int[hops + 1];
        int[] edges = new int[hops];
        double length = 0;
        int v = target;
        for (int i = hops; i > 0; i--) {
            nodes[i] = v;
            edges[i - 1] = prevEdge[v];
            length += graph.edgeLength(prevEdge[v]);
            v = prevNode[v];
        }
        nodes[0] = source;
        return new PathResult(nodes, edges, cost, length);
    }

    private void checkNode(int node) {
        if (node < 0 || node >= graph.nodeCount()) {
            throw new IndexOutOfBoundsException("Nieznany węzeł: " + node);
        }
    }

    private record Entry(int node, double dist) implements Comparable<Entry> {
        @Override
        public int compareTo(Entry o) {
            return Double.compare(dist, o.dist);
        }
    }
}
