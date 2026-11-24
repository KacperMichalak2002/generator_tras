package com.wsyska.kmichalak.routegenerator.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GraphBuilder {

    private static final float[] NO_POINTS = new float[0];

    private float[] lat = new float[16];
    private float[] lon = new float[16];
    private int nodeCount;

    private int[] segFrom = new int[16];
    private int[] segTo = new int[16];
    private float[] segLength = new float[16];
    private int[] segAttributes = new int[16];
    private long[] segWayId = new long[16];
    private int[] segGeomStart = new int[17];
    private int segmentCount;

    private float[] geomLat = new float[16];
    private float[] geomLon = new float[16];
    private int geomCount;

    private int[] edgeFrom = new int[16];
    private int[] edgeTo = new int[16];
    private int[] edgeSegDir = new int[16];
    private int edgeCount;

    private final List<Map<String, String>> attributes = new ArrayList<>();
    private final Map<Map<String, String>, Integer> attributeIds = new HashMap<>();

    public GraphBuilder() {
        attributes(Map.of());
    }

    public int addNode(double latitude, double longitude) {
        if (nodeCount == lat.length) {
            lat = Arrays.copyOf(lat, nodeCount * 2);
            lon = Arrays.copyOf(lon, nodeCount * 2);
        }
        lat[nodeCount] = (float) latitude;
        lon[nodeCount] = (float) longitude;
        return nodeCount++;
    }

    public int nodeCount() {
        return nodeCount;
    }

    public int attributes(Map<String, String> tags) {
        Integer id = attributeIds.get(tags);
        if (id != null) {
            return id;
        }
        Map<String, String> copy = Map.copyOf(tags);
        attributes.add(copy);
        attributeIds.put(copy, attributes.size() - 1);
        return attributes.size() - 1;
    }

    public GraphBuilder addEdge(int source, int target, double lengthM) {
        addSegmentEdges(source, target, lengthM, 0, -1, NO_POINTS, NO_POINTS, 0, 0, true, false);
        return this;
    }

    public GraphBuilder addEdge(int source, int target) {
        checkNode(source);
        checkNode(target);
        return addEdge(source, target,
                GeoUtils.haversineMeters(lat[source], lon[source], lat[target], lon[target]));
    }

    public GraphBuilder addBidirectionalEdge(int a, int b, double lengthM) {
        return addWaySegment(a, b, lengthM, 0, -1, NO_POINTS, NO_POINTS, 0);
    }

    public GraphBuilder addWaySegment(int from, int to, double lengthM, int attributesId, long wayId,
                                      float[] pillarLat, float[] pillarLon, int pillarCount) {
        return addWaySegment(from, to, lengthM, attributesId, wayId, pillarLat, pillarLon, 0, pillarCount);
    }

    public GraphBuilder addWaySegment(int from, int to, double lengthM, int attributesId, long wayId,
                                      float[] pillarLat, float[] pillarLon, int pillarOffset, int pillarCount) {
        addSegmentEdges(from, to, lengthM, attributesId, wayId, pillarLat, pillarLon, pillarOffset, pillarCount,
                true, true);
        return this;
    }

    void addSegmentEdges(int from, int to, double lengthM, int attributesId, long wayId,
                         float[] pillarLat, float[] pillarLon, int pillarOffset, int pillarCount,
                         boolean forward, boolean reverse) {
        int seg = addSegment(from, to, lengthM, attributesId, wayId, pillarLat, pillarLon, pillarOffset, pillarCount);
        if (forward) {
            addEdgeRecord(from, to, seg, false);
        }
        if (reverse) {
            addEdgeRecord(to, from, seg, true);
        }
    }

    public CsrGraph build() {
        int[] offsets = new int[nodeCount + 1];
        for (int i = 0; i < edgeCount; i++) {
            offsets[edgeFrom[i] + 1]++;
        }
        for (int v = 0; v < nodeCount; v++) {
            offsets[v + 1] += offsets[v];
        }

        int[] targets = new int[edgeCount];
        int[] segDir = new int[edgeCount];
        int[] next = Arrays.copyOf(offsets, nodeCount);
        for (int i = 0; i < edgeCount; i++) {
            int pos = next[edgeFrom[i]]++;
            targets[pos] = edgeTo[i];
            segDir[pos] = edgeSegDir[i];
        }

        return new CsrGraph(
                Arrays.copyOf(lat, nodeCount), Arrays.copyOf(lon, nodeCount),
                offsets, targets, segDir,
                Arrays.copyOf(segFrom, segmentCount), Arrays.copyOf(segTo, segmentCount),
                Arrays.copyOf(segLength, segmentCount), Arrays.copyOf(segAttributes, segmentCount),
                Arrays.copyOf(segWayId, segmentCount), Arrays.copyOf(segGeomStart, segmentCount + 1),
                Arrays.copyOf(geomLat, geomCount), Arrays.copyOf(geomLon, geomCount),
                List.copyOf(attributes));
    }

    private int addSegment(int from, int to, double lengthM, int attributesId, long wayId,
                           float[] pillarLat, float[] pillarLon, int pillarOffset, int pillarCount) {
        checkNode(from);
        checkNode(to);
        if (!(lengthM >= 0)) {
            throw new IllegalArgumentException("Długość krawędzi musi być nieujemna: " + lengthM);
        }
        if (attributesId < 0 || attributesId >= attributes.size()) {
            throw new IllegalArgumentException("Nieznany zestaw tagów: " + attributesId);
        }
        if (segmentCount == segFrom.length) {
            int cap = segmentCount * 2;
            segFrom = Arrays.copyOf(segFrom, cap);
            segTo = Arrays.copyOf(segTo, cap);
            segLength = Arrays.copyOf(segLength, cap);
            segAttributes = Arrays.copyOf(segAttributes, cap);
            segWayId = Arrays.copyOf(segWayId, cap);
            segGeomStart = Arrays.copyOf(segGeomStart, cap + 1);
        }
        if (geomCount + pillarCount > geomLat.length) {
            int cap = Math.max(geomLat.length * 2, geomCount + pillarCount);
            geomLat = Arrays.copyOf(geomLat, cap);
            geomLon = Arrays.copyOf(geomLon, cap);
        }
        System.arraycopy(pillarLat, pillarOffset, geomLat, geomCount, pillarCount);
        System.arraycopy(pillarLon, pillarOffset, geomLon, geomCount, pillarCount);
        geomCount += pillarCount;

        int seg = segmentCount++;
        segFrom[seg] = from;
        segTo[seg] = to;
        segLength[seg] = (float) lengthM;
        segAttributes[seg] = attributesId;
        segWayId[seg] = wayId;
        segGeomStart[seg + 1] = geomCount;
        return seg;
    }

    private void addEdgeRecord(int from, int to, int seg, boolean reverse) {
        if (edgeCount == edgeFrom.length) {
            int cap = edgeCount * 2;
            edgeFrom = Arrays.copyOf(edgeFrom, cap);
            edgeTo = Arrays.copyOf(edgeTo, cap);
            edgeSegDir = Arrays.copyOf(edgeSegDir, cap);
        }
        edgeFrom[edgeCount] = from;
        edgeTo[edgeCount] = to;
        edgeSegDir[edgeCount] = seg << 1 | (reverse ? 1 : 0);
        edgeCount++;
    }

    private void checkNode(int node) {
        if (node < 0 || node >= nodeCount) {
            throw new IndexOutOfBoundsException("Nieznany węzeł: " + node);
        }
    }
}
