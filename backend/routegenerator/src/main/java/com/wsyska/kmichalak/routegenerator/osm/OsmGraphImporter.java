package com.wsyska.kmichalak.routegenerator.osm;

import com.wsyska.kmichalak.routegenerator.graph.ConnectedComponents;
import com.wsyska.kmichalak.routegenerator.graph.CsrGraph;
import com.wsyska.kmichalak.routegenerator.graph.GeoUtils;
import com.wsyska.kmichalak.routegenerator.graph.GraphBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;


public final class OsmGraphImporter {

    @FunctionalInterface
    public interface PbfSource {
        InputStream open() throws IOException;

        static PbfSource of(Path file) {
            return () -> Files.newInputStream(file);
        }

        static PbfSource of(byte[] data) {
            return () -> new ByteArrayInputStream(data);
        }
    }

    public record Options(RoadFilter filter, int minComponentNodes) {

        public static Options defaults() {
            return new Options(RoadFilter.defaults(), 50);
        }

        public Options withMinComponentNodes(int minNodes) {
            return new Options(filter, minNodes);
        }
    }

    public record Stats(long waysRead, long waysKept, long nodesRead, int referencedNodes,
                        long missingNodeRefs, int componentsBefore, int removedNodes,
                        int routingNodes, int segments, int edges, int pillarPoints, int attributeSets,
                        double totalLengthKm, long waysPassMs, long nodesPassMs, long buildMs,
                        long graphBytes) {

        public long totalMs() {
            return waysPassMs + nodesPassMs + buildMs;
        }
    }

    public record Result(CsrGraph graph, Stats stats) {
    }

    private static final int MISSING = Integer.MIN_VALUE;

    private final RoadFilter filter;
    private final int minComponentNodes;
    private final GraphBuilder builder = new GraphBuilder();

    private final LongIntHashMap nodeIndex = new LongIntHashMap(1 << 16);
    private byte[] refCount = new byte[1 << 16];
    private int denseCount;

    private int[] wayRefs = new int[1 << 16];
    private int refTotal;
    private int[] wayStart = new int[1 << 12];
    private int[] wayAttributes = new int[1 << 12];
    private long[] wayIds = new long[1 << 12];
    private int wayCount;

    private int[] latE7;
    private int[] lonE7;
    private int[] graphNode;

    private float[] pillarLat = new float[64];
    private float[] pillarLon = new float[64];
    private int[] pillarIdx = new int[64];
    private double[] pillarDistance = new double[64];

    private long waysRead;
    private long nodesRead;
    private long missingNodeRefs;

    private OsmGraphImporter(Options options) {
        this.filter = options.filter();
        this.minComponentNodes = options.minComponentNodes();
    }

    public static Result importFile(Path file) throws IOException {
        return importGraph(PbfSource.of(file), Options.defaults());
    }

    public static Result importGraph(PbfSource source, Options options) throws IOException {
        return new OsmGraphImporter(options).run(source);
    }

    private Result run(PbfSource source) throws IOException {
        long t0 = System.nanoTime();
        try (InputStream in = source.open()) {
            PbfReader.read(in, new WaysPass());
        }
        long t1 = System.nanoTime();

        latE7 = new int[denseCount];
        lonE7 = new int[denseCount];
        Arrays.fill(latE7, MISSING);
        try (InputStream in = source.open()) {
            PbfReader.read(in, new NodesPass());
        }
        long t2 = System.nanoTime();

        graphNode = new int[denseCount];
        Arrays.fill(graphNode, -1);
        for (int w = 0; w < wayCount; w++) {
            buildWay(w);
        }
        CsrGraph graph = builder.build();

        int componentsBefore = 0;
        int removedNodes = 0;
        if (minComponentNodes > 1 && graph.nodeCount() > 0) {
            ConnectedComponents components = ConnectedComponents.of(graph);
            componentsBefore = components.count();
            boolean[] keep = components.nodesInComponentsOfAtLeast(minComponentNodes);
            for (boolean k : keep) {
                if (!k) {
                    removedNodes++;
                }
            }
            if (removedNodes > 0) {
                graph = graph.retainNodes(keep);
            }
        }
        long t3 = System.nanoTime();

        double meters = 0;
        int pillarPoints = 0;
        for (int e = 0; e < graph.edgeCount(); e++) {
            if (!graph.isReverse(e)) {
                meters += graph.edgeLength(e);
                pillarPoints += graph.edgePointCount(e) - 2;
            }
        }

        Stats stats = new Stats(waysRead, wayCount, nodesRead, denseCount, missingNodeRefs,
                componentsBefore, removedNodes, graph.nodeCount(), graph.segmentCount(), graph.edgeCount(),
                pillarPoints, graph.attributeCount(), meters / 1000,
                ms(t0, t1), ms(t1, t2), ms(t2, t3), graph.estimatedBytes());
        return new Result(graph, stats);
    }

    private final class WaysPass implements OsmHandler {

        @Override
        public boolean wantsNodes() {
            return false;
        }

        @Override
        public void way(long id, long[] nodeRefs, Map<String, String> tags) {
            waysRead++;
            if (nodeRefs.length < 2 || !filter.accepts(tags)) {
                return;
            }
            int attributes = builder.attributes(filter.relevantTags(tags));
            if (refTotal + nodeRefs.length > wayRefs.length) {
                wayRefs = Arrays.copyOf(wayRefs, Math.max(wayRefs.length * 2, refTotal + nodeRefs.length));
            }
            for (long ref : nodeRefs) {
                int idx = nodeIndex.get(ref, -1);
                if (idx < 0) {
                    idx = denseCount++;
                    nodeIndex.put(ref, idx);
                    if (idx == refCount.length) {
                        refCount = Arrays.copyOf(refCount, idx * 2);
                    }
                }
                if (refCount[idx] < 2) {
                    refCount[idx]++;
                }
                wayRefs[refTotal++] = idx;
            }
            if (wayCount + 1 >= wayStart.length) {
                int cap = wayStart.length * 2;
                wayStart = Arrays.copyOf(wayStart, cap);
                wayAttributes = Arrays.copyOf(wayAttributes, cap);
                wayIds = Arrays.copyOf(wayIds, cap);
            }
            wayAttributes[wayCount] = attributes;
            wayIds[wayCount] = id;
            wayCount++;
            wayStart[wayCount] = refTotal;
        }
    }

    private final class NodesPass implements OsmHandler {

        @Override
        public boolean wantsWays() {
            return false;
        }

        @Override
        public void node(long id, double lat, double lon) {
            nodesRead++;
            int idx = nodeIndex.get(id, -1);
            if (idx >= 0) {
                latE7[idx] = (int) Math.round(lat * 1e7);
                lonE7[idx] = (int) Math.round(lon * 1e7);
            }
        }
    }

    private void buildWay(int w) {
        int end = wayStart[w + 1];
        int k = wayStart[w];
        while (k < end) {
            while (k < end && latE7[wayRefs[k]] == MISSING) {
                missingNodeRefs++;
                k++;
            }
            int runStart = k;
            while (k < end && latE7[wayRefs[k]] != MISSING) {
                k++;
            }
            if (k - runStart >= 2) {
                buildRun(w, runStart, k);
            }
        }
    }

    private void buildRun(int w, int start, int end) {
        int prev = wayRefs[start];
        int from = graphNodeOf(prev);
        double length = 0;
        int pillars = 0;

        for (int k = start + 1; k < end; k++) {
            int cur = wayRefs[k];
            if (cur == prev) {
                continue;
            }
            length += distance(prev, cur);
            prev = cur;
            boolean split = k == end - 1 || refCount[cur] >= 2;
            if (!split) {
                ensurePillarCapacity(pillars + 1);
                pillarIdx[pillars] = cur;
                pillarLat[pillars] = (float) (latE7[cur] / 1e7);
                pillarLon[pillars] = (float) (lonE7[cur] / 1e7);
                pillarDistance[pillars] = length;
                pillars++;
                continue;
            }

            int to = graphNodeOf(cur);
            if (to != from) {
                addSegment(w, from, to, length, 0, pillars);
            } else if (pillars > 0) {
                // Pętla wracająca do tego samego węzła: dzielimy ją w połowie,
                // żeby nie powstała krawędź z węzła do niego samego.
                int mid = pillars / 2;
                int midNode = graphNodeOf(pillarIdx[mid]);
                addSegment(w, from, midNode, pillarDistance[mid], 0, mid);
                addSegment(w, midNode, to, length - pillarDistance[mid], mid + 1, pillars - mid - 1);
            }
            from = to;
            length = 0;
            pillars = 0;
        }
    }

    private void addSegment(int w, int from, int to, double length, int pillarOffset, int pillarCount) {
        builder.addWaySegment(from, to, length, wayAttributes[w], wayIds[w],
                pillarLat, pillarLon, pillarOffset, pillarCount);
    }

    private int graphNodeOf(int idx) {
        int node = graphNode[idx];
        if (node < 0) {
            node = builder.addNode(latE7[idx] / 1e7, lonE7[idx] / 1e7);
            graphNode[idx] = node;
        }
        return node;
    }

    private double distance(int a, int b) {
        return GeoUtils.haversineMeters(latE7[a] / 1e7, lonE7[a] / 1e7, latE7[b] / 1e7, lonE7[b] / 1e7);
    }

    private void ensurePillarCapacity(int capacity) {
        if (capacity > pillarIdx.length) {
            int cap = Math.max(capacity, pillarIdx.length * 2);
            pillarIdx = Arrays.copyOf(pillarIdx, cap);
            pillarLat = Arrays.copyOf(pillarLat, cap);
            pillarLon = Arrays.copyOf(pillarLon, cap);
            pillarDistance = Arrays.copyOf(pillarDistance, cap);
        }
    }

    private static long ms(long fromNanos, long toNanos) {
        return (toNanos - fromNanos) / 1_000_000;
    }
}
