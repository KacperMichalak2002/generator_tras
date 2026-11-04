package com.wsyska.kmichalak.routegenerator.graph;

import java.util.Arrays;
import java.util.function.IntPredicate;

public final class SpatialIndex {

    private final Graph graph;
    private final double minLat;
    private final double minLon;
    private final double cellLat;
    private final double cellLon;
    private final double minCellM;
    private final int rows;
    private final int cols;
    private final int[] cellStart;
    private final int[] cellNodes;

    private SpatialIndex(Graph graph, double minLat, double minLon, double cellLat, double cellLon,
                         double minCellM, int rows, int cols, int[] cellStart, int[] cellNodes) {
        this.graph = graph;
        this.minLat = minLat;
        this.minLon = minLon;
        this.cellLat = cellLat;
        this.cellLon = cellLon;
        this.minCellM = minCellM;
        this.rows = rows;
        this.cols = cols;
        this.cellStart = cellStart;
        this.cellNodes = cellNodes;
    }

    public static SpatialIndex build(Graph graph, double cellSizeM) {
        int n = graph.nodeCount();
        double minLat = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;
        for (int v = 0; v < n; v++) {
            minLat = Math.min(minLat, graph.lat(v));
            maxLat = Math.max(maxLat, graph.lat(v));
            minLon = Math.min(minLon, graph.lon(v));
            maxLon = Math.max(maxLon, graph.lon(v));
        }
        if (n == 0) {
            minLat = maxLat = minLon = maxLon = 0;
        }
        double metersPerDegLat = Math.toRadians(GeoUtils.EARTH_RADIUS_M);
        double maxAbsLat = Math.max(Math.abs(minLat), Math.abs(maxLat));
        double cellLat = cellSizeM / metersPerDegLat;
        double cellLon = cellSizeM / (metersPerDegLat * Math.cos(Math.toRadians((minLat + maxLat) / 2)));
        double minCellM = Math.min(cellSizeM,
                cellLon * metersPerDegLat * Math.cos(Math.toRadians(Math.min(89, maxAbsLat))));
        int rows = (int) ((maxLat - minLat) / cellLat) + 1;
        int cols = (int) ((maxLon - minLon) / cellLon) + 1;

        int[] cellStart = new int[rows * cols + 1];
        int[] cellOf = new int[n];
        for (int v = 0; v < n; v++) {
            int r = (int) ((graph.lat(v) - minLat) / cellLat);
            int c = (int) ((graph.lon(v) - minLon) / cellLon);
            cellOf[v] = r * cols + c;
            cellStart[cellOf[v] + 1]++;
        }
        for (int i = 0; i < rows * cols; i++) {
            cellStart[i + 1] += cellStart[i];
        }
        int[] cellNodes = new int[n];
        int[] next = Arrays.copyOf(cellStart, rows * cols);
        for (int v = 0; v < n; v++) {
            cellNodes[next[cellOf[v]]++] = v;
        }
        return new SpatialIndex(graph, minLat, minLon, cellLat, cellLon, minCellM, rows, cols, cellStart, cellNodes);
    }

    public int nearest(double lat, double lon, double maxDistanceM) {
        return nearest(lat, lon, maxDistanceM, v -> true);
    }

    public int nearest(double lat, double lon, double maxDistanceM, IntPredicate accept) {
        int r0 = (int) Math.floor((lat - minLat) / cellLat);
        int c0 = (int) Math.floor((lon - minLon) / cellLon);
        int best = -1;
        double bestDist = maxDistanceM;
        int maxRing = (int) Math.ceil(maxDistanceM / minCellM) + 1;
        for (int ring = 0; ring <= maxRing; ring++) {
            if (best >= 0 && bestDist <= (ring - 1) * minCellM) {
                break;
            }
            for (int r = r0 - ring; r <= r0 + ring; r++) {
                if (r < 0 || r >= rows) {
                    continue;
                }
                boolean edgeRow = r == r0 - ring || r == r0 + ring;
                int step = edgeRow ? 1 : Math.max(1, 2 * ring);
                for (int c = c0 - ring; c <= c0 + ring; c += step) {
                    if (c < 0 || c >= cols) {
                        continue;
                    }
                    int cell = r * cols + c;
                    for (int i = cellStart[cell]; i < cellStart[cell + 1]; i++) {
                        int v = cellNodes[i];
                        double d = GeoUtils.haversineMeters(lat, lon, graph.lat(v), graph.lon(v));
                        if (d <= bestDist && (best < 0 || d < bestDist) && accept.test(v)) {
                            best = v;
                            bestDist = d;
                        }
                    }
                }
            }
        }
        return best;
    }
}
