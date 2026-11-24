package com.wsyska.kmichalak.routegenerator.routing;

import com.wsyska.kmichalak.routegenerator.graph.Graph;

import java.util.Optional;


public final class Dijkstra implements Router {

    private final Graph graph;

    public Dijkstra(Graph graph) {
        this.graph = graph;
    }

    @Override
    public Optional<PathResult> route(int source, int target, EdgeCost cost, SearchBounds bounds) {
        checkNode(source);
        checkNode(target);
        if (source != target && bounds != null && !bounds.contains(graph.lat(target), graph.lon(target))) {
            return Optional.empty();
        }

        NodeLabels labels = new NodeLabels(1024);
        MinHeap heap = new MinHeap(1024);
        labels.set(labels.findOrAdd(source), 0, -1);
        heap.push(0, source);

        while (!heap.isEmpty()) {
            double d = heap.peekKey();
            int v = heap.pop();
            if (d > labels.dist(labels.find(v))) {
                continue;
            }
            if (v == target) {
                return Optional.of(buildPath(source, target, d, labels));
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
                if (bounds != null && !bounds.contains(graph.lat(w), graph.lon(w))) {
                    continue;
                }
                double candidate = d + c;
                int slot = labels.findOrAdd(w);
                if (candidate < labels.dist(slot)) {
                    labels.set(slot, candidate, e);
                    heap.push(candidate, w);
                }
            }
        }
        return Optional.empty();
    }

    private PathResult buildPath(int source, int target, double cost, NodeLabels labels) {
        int hops = 0;
        for (int v = target; v != source; v = graph.edgeSource(labels.prevEdge(labels.find(v)))) {
            hops++;
        }
        int[] nodes = new int[hops + 1];
        int[] edges = new int[hops];
        double length = 0;
        int v = target;
        for (int i = hops; i > 0; i--) {
            int e = labels.prevEdge(labels.find(v));
            nodes[i] = v;
            edges[i - 1] = e;
            length += graph.edgeLength(e);
            v = graph.edgeSource(e);
        }
        nodes[0] = source;
        return new PathResult(nodes, edges, cost, length);
    }

    private void checkNode(int node) {
        if (node < 0 || node >= graph.nodeCount()) {
            throw new IndexOutOfBoundsException("Nieznany węzeł: " + node);
        }
    }
}
