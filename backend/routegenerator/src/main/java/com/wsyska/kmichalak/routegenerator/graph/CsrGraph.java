package com.wsyska.kmichalak.routegenerator.graph;

import java.util.List;
import java.util.Map;

public final class CsrGraph implements Graph {

    private final float[] lat;
    private final float[] lon;

    private final int[] offsets;
    private final int[] targets;
    private final int[] edgeSegDir;

    private final int[] segFrom;
    private final int[] segTo;
    private final float[] segLength;
    private final int[] segAttributes;
    private final long[] segWayId;
    private final int[] segGeomStart;
    private final float[] geomLat;
    private final float[] geomLon;

    private final List<Map<String, String>> attributes;

    CsrGraph(float[] lat, float[] lon, int[] offsets, int[] targets, int[] edgeSegDir,
             int[] segFrom, int[] segTo, float[] segLength, int[] segAttributes, long[] segWayId,
             int[] segGeomStart, float[] geomLat, float[] geomLon,
             List<Map<String, String>> attributes) {
        this.lat = lat;
        this.lon = lon;
        this.offsets = offsets;
        this.targets = targets;
        this.edgeSegDir = edgeSegDir;
        this.segFrom = segFrom;
        this.segTo = segTo;
        this.segLength = segLength;
        this.segAttributes = segAttributes;
        this.segWayId = segWayId;
        this.segGeomStart = segGeomStart;
        this.geomLat = geomLat;
        this.geomLon = geomLon;
        this.attributes = attributes;
    }

    @Override
    public int nodeCount() {
        return lat.length;
    }

    @Override
    public int edgeCount() {
        return targets.length;
    }

    public int segmentCount() {
        return segFrom.length;
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
    public int edgeSource(int edge) {
        int seg = edgeSegDir[edge] >>> 1;
        return isReverse(edge) ? segTo[seg] : segFrom[seg];
    }

    @Override
    public int edgeTarget(int edge) {
        return targets[edge];
    }

    @Override
    public double edgeLength(int edge) {
        return segLength[edgeSegDir[edge] >>> 1];
    }

    @Override
    public int edgeAttributes(int edge) {
        return segAttributes[edgeSegDir[edge] >>> 1];
    }

    @Override
    public int attributeCount() {
        return attributes.size();
    }

    @Override
    public Map<String, String> attributeTags(int attributesId) {
        return attributes.get(attributesId);
    }

    @Override
    public int edgeSegment(int edge) {
        return edgeSegDir[edge] >>> 1;
    }

    @Override
    public boolean isReverse(int edge) {
        return (edgeSegDir[edge] & 1) != 0;
    }

    @Override
    public long edgeWayId(int edge) {
        return segWayId[edgeSegDir[edge] >>> 1];
    }

    @Override
    public int edgePointCount(int edge) {
        int seg = edgeSegDir[edge] >>> 1;
        return segGeomStart[seg + 1] - segGeomStart[seg] + 2;
    }

    @Override
    public double edgePointLat(int edge, int index) {
        int geom = geometryIndex(edge, index);
        return geom >= 0 ? geomLat[geom] : lat[endpoint(edge, index)];
    }

    @Override
    public double edgePointLon(int edge, int index) {
        int geom = geometryIndex(edge, index);
        return geom >= 0 ? geomLon[geom] : lon[endpoint(edge, index)];
    }

    private int geometryIndex(int edge, int index) {
        int count = edgePointCount(edge);
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException("Punkt " + index + " z " + count);
        }
        if (index == 0 || index == count - 1) {
            return -1;
        }
        int seg = edgeSegDir[edge] >>> 1;
        int pillar = isReverse(edge) ? count - 2 - index : index - 1;
        return segGeomStart[seg] + pillar;
    }

    private int endpoint(int edge, int index) {
        return index == 0 ? edgeSource(edge) : edgeTarget(edge);
    }

    public CsrGraph retainNodes(boolean[] keep) {
        if (keep.length != nodeCount()) {
            throw new IllegalArgumentException("Oczekiwano " + nodeCount() + " flag, jest " + keep.length);
        }
        GraphBuilder builder = new GraphBuilder();
        for (int i = 1; i < attributes.size(); i++) {
            builder.attributes(attributes.get(i));
        }
        int[] newId = new int[nodeCount()];
        for (int v = 0; v < newId.length; v++) {
            newId[v] = keep[v] ? builder.addNode(lat[v], lon[v]) : -1;
        }

        byte[] directions = new byte[segmentCount()];
        for (int e = 0; e < edgeCount(); e++) {
            directions[edgeSegDir[e] >>> 1] |= (byte) (isReverse(e) ? 2 : 1);
        }
        for (int s = 0; s < segmentCount(); s++) {
            int from = newId[segFrom[s]];
            int to = newId[segTo[s]];
            if (from < 0 || to < 0 || directions[s] == 0) {
                continue;
            }
            int start = segGeomStart[s];
            builder.addSegmentEdges(from, to, segLength[s], segAttributes[s], segWayId[s],
                    geomLat, geomLon, start, segGeomStart[s + 1] - start,
                    (directions[s] & 1) != 0, (directions[s] & 2) != 0);
        }
        return builder.build();
    }

    public long estimatedBytes() {
        long bytes = 4L * (lat.length + lon.length + offsets.length + targets.length + edgeSegDir.length
                + segFrom.length + segTo.length + segLength.length + segAttributes.length
                + segGeomStart.length + geomLat.length + geomLon.length);
        bytes += 8L * segWayId.length;
        for (Map<String, String> tags : attributes) {
            bytes += 64 + 96L * tags.size();
        }
        return bytes;
    }
}
