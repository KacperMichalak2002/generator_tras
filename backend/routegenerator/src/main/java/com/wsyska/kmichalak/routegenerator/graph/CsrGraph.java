package com.wsyska.kmichalak.routegenerator.graph;

public final class CsrGraph implements Graph {

    private final float[] lat;
    private final float[] lon;
    private final int[] offsets;
    private final int[] targets;
    private final float[] lengths;

    CsrGraph(float[] lat, float[] lon, int[] offsets, int[] targets, float[] lengths) {
        this.lat = lat;
        this.lon = lon;
        this.offsets = offsets;
        this.targets = targets;
        this.lengths = lengths;
    }

    @Override
    public int nodeCount() {
        return lat.length;
    }

    @Override
    public int edgeCount() {
        return targets.length;
    }

    @Override
    public double lat(int node) {
        return lat[node];
    }

    @Override
    public double lon(int node) {
        return lon[node];
    }

    @Override
    public int firstEdge(int node) {
        return offsets[node];
    }

    @Override
    public int endEdge(int node) {
        return offsets[node + 1];
    }

    @Override
    public int edgeTarget(int edge) {
        return targets[edge];
    }

    @Override
    public double edgeLength(int edge) {
        return lengths[edge];
    }

    public long estimatedBytes() {
        return 4L * (lat.length + lon.length + offsets.length + targets.length + lengths.length);
    }
}
