package com.wsyska.kmichalak.routegenerator.graph;

import java.util.Arrays;


public final class GraphBuilder {

    private float[] lat = new float[16];
    private float[] lon = new float[16];
    private int nodeCount;

    private int[] from = new int[16];
    private int[] to = new int[16];
    private float[] length = new float[16];
    private int edgeCount;

    public int addNode(double latitude, double longitude) {
        if (nodeCount == lat.length) {
            lat = Arrays.copyOf(lat, nodeCount * 2);
            lon = Arrays.copyOf(lon, nodeCount * 2);
        }
        lat[nodeCount] = (float) latitude;
        lon[nodeCount] = (float) longitude;
        return nodeCount++;
    }

    public GraphBuilder addEdge(int source, int target, double lengthM) {
        checkNode(source);
        checkNode(target);
        if (!(lengthM >= 0)) {
            throw new IllegalArgumentException("Długość krawędzi musi być nieujemna: " + lengthM);
        }
        if (edgeCount == from.length) {
            from = Arrays.copyOf(from, edgeCount * 2);
            to = Arrays.copyOf(to, edgeCount * 2);
            length = Arrays.copyOf(length, edgeCount * 2);
        }
        from[edgeCount] = source;
        to[edgeCount] = target;
        length[edgeCount] = (float) lengthM;
        edgeCount++;
        return this;
    }


    public GraphBuilder addEdge(int source, int target) {
        checkNode(source);
        checkNode(target);
        return addEdge(source, target,
                GeoUtils.haversineMeters(lat[source], lon[source], lat[target], lon[target]));
    }

    public GraphBuilder addBidirectionalEdge(int a, int b, double lengthM) {
        return addEdge(a, b, lengthM).addEdge(b, a, lengthM);
    }

    public CsrGraph build() {
        int[] offsets = new int[nodeCount + 1];
        for (int i = 0; i < edgeCount; i++) {
            offsets[from[i] + 1]++;
        }
        for (int v = 0; v < nodeCount; v++) {
            offsets[v + 1] += offsets[v];
        }

        int[] targets = new int[edgeCount];
        float[] lengths = new float[edgeCount];
        int[] next = Arrays.copyOf(offsets, nodeCount);
        for (int i = 0; i < edgeCount; i++) {
            int pos = next[from[i]]++;
            targets[pos] = to[i];
            lengths[pos] = length[i];
        }

        return new CsrGraph(
                Arrays.copyOf(lat, nodeCount),
                Arrays.copyOf(lon, nodeCount),
                offsets, targets, lengths);
    }

    private void checkNode(int node) {
        if (node < 0 || node >= nodeCount) {
            throw new IndexOutOfBoundsException("Nieznany węzeł: " + node);
        }
    }
}
