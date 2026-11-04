package com.wsyska.kmichalak.routegenerator.routing;

import com.wsyska.kmichalak.routegenerator.graph.GeoUtils;
import com.wsyska.kmichalak.routegenerator.graph.Graph;
import com.wsyska.kmichalak.routegenerator.graph.SpatialIndex;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Optional;
import java.util.SplittableRandom;

public final class LoopGenerator {

    public record Options(int waypoints, double detourFactor, int maxAttempts, double tolerance,
                          double reusePenalty, double snapRadiusM) {

        public static Options defaults() {
            return new Options(3, 1.3, 5, 0.05, 3.0, 1500);
        }
    }

    public record LoopResult(PathResult path, int[] waypoints, double targetM, int attempts,
                             double reusedLengthM) {

        public LoopResult {
            waypoints = waypoints.clone();
        }

        @Override
        public int[] waypoints() {
            return waypoints.clone();
        }

        public double lengthM() {
            return path.lengthM();
        }

        // Względna odchyłka od zadanej długości, np. 0.04 = trasa o 4% za długa.
        public double deviation() {
            return (path.lengthM() - targetM) / targetM;
        }

        // Część trasy, która biegnie odcinkami już wcześniej przejechanymi.
        public double reusedShare() {
            return path.lengthM() == 0 ? 0 : reusedLengthM / path.lengthM();
        }
    }

    private final Graph graph;
    private final SpatialIndex index;
    private final Options options;
    private final Dijkstra dijkstra;

    public LoopGenerator(Graph graph, SpatialIndex index, Options options) {
        this.graph = graph;
        this.index = index;
        this.options = options;
        this.dijkstra = new Dijkstra(graph);
    }

    public Optional<LoopResult> generate(int start, double distanceM, EdgeCost cost, long seed) {
        if (!(distanceM > 0)) {
            throw new IllegalArgumentException("Dystans musi być dodatni: " + distanceM);
        }
        SplittableRandom random = new SplittableRandom(seed);
        double bearing = random.nextDouble(360);
        boolean clockwise = random.nextBoolean();
        double scale = 1.0;

        LoopResult best = null;
        int[] previousWaypoints = null;
        for (int attempt = 1; attempt <= options.maxAttempts(); attempt++) {
            Optional<LoopResult> loop = tryLoop(start, distanceM, cost, bearing, clockwise, scale, attempt);
            if (loop.isEmpty()) {
                bearing = random.nextDouble(360);
                continue;
            }
            LoopResult result = loop.get();
            if (best == null || Math.abs(result.deviation()) < Math.abs(best.deviation())) {
                best = result;
            }
            if (Math.abs(result.deviation()) <= options.tolerance()) {
                break;
            }
            scale = clamp(scale * distanceM / result.lengthM(), 0.25, 4.0);
            int[] waypoints = result.waypoints();
            if (waypoints.length < options.waypoints() || Arrays.equals(waypoints, previousWaypoints)) {
                bearing = random.nextDouble(360);
            }
            previousWaypoints = waypoints;
        }
        return Optional.ofNullable(best);
    }

    private Optional<LoopResult> tryLoop(int start, double distanceM, EdgeCost cost,
                                         double bearing, boolean clockwise, double scale, int attempt) {
        int k = options.waypoints();
        double radius = distanceM * scale / (2 * Math.PI * options.detourFactor());
        double snapRadius = Math.max(200, Math.min(options.snapRadiusM(), radius * 0.5));
        double[] center = GeoUtils.destination(graph.lat(start), graph.lon(start), bearing, radius);
        double startAngle = bearing + 180;

        int[] waypoints = new int[k];
        int count = 0;
        int previous = start;
        for (int i = 1; i <= k; i++) {
            double angle = startAngle + (clockwise ? 1 : -1) * i * 360.0 / (k + 1);
            double[] p = GeoUtils.destination(center[0], center[1], angle, radius);
            int node = index.nearest(p[0], p[1], snapRadius, v -> hasUsableEdge(v, cost));
            if (node >= 0 && node != previous && node != start) {
                waypoints[count++] = node;
                previous = node;
            }
        }
        if (count == 0) {
            return Optional.empty();
        }
        waypoints = Arrays.copyOf(waypoints, count);

        BitSet usedSegments = new BitSet(graph.edgeCount());
        EdgeCost penalized = (g, e) -> {
            double c = cost.cost(g, e);
            return usedSegments.get(g.edgeSegment(e)) ? c * options.reusePenalty() : c;
        };

        int[] edges = new int[64];
        int edgeCount = 0;
        int current = start;
        for (int i = 0; i <= count; i++) {
            int target = i < count ? waypoints[i] : start;
            Optional<PathResult> leg = dijkstra.route(current, target, penalized);
            if (leg.isEmpty()) {
                return Optional.empty();
            }
            for (int e : leg.get().edges()) {
                usedSegments.set(graph.edgeSegment(e));
                if (edgeCount == edges.length) {
                    edges = Arrays.copyOf(edges, edgeCount * 2);
                }
                edges[edgeCount++] = e;
            }
            current = target;
        }
        return Optional.of(toResult(start, Arrays.copyOf(edges, edgeCount), waypoints, distanceM, cost, attempt));
    }

    private LoopResult toResult(int start, int[] edges, int[] waypoints, double targetM, EdgeCost cost, int attempts) {
        int[] nodes = new int[edges.length + 1];
        nodes[0] = start;
        double length = 0;
        double totalCost = 0;
        double reused = 0;
        BitSet seen = new BitSet(graph.edgeCount());
        for (int i = 0; i < edges.length; i++) {
            int e = edges[i];
            nodes[i + 1] = graph.edgeTarget(e);
            length += graph.edgeLength(e);
            totalCost += cost.cost(graph, e);
            int segment = graph.edgeSegment(e);
            if (seen.get(segment)) {
                reused += graph.edgeLength(e);
            }
            seen.set(segment);
        }
        return new LoopResult(new PathResult(nodes, edges, totalCost, length), waypoints, targetM, attempts, reused);
    }

    private boolean hasUsableEdge(int node, EdgeCost cost) {
        for (int e = graph.firstEdge(node); e < graph.endEdge(node); e++) {
            if (!EdgeCost.isForbidden(cost.cost(graph, e))) {
                return true;
            }
        }
        return false;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
