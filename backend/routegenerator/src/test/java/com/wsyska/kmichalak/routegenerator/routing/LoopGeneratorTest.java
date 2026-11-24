package com.wsyska.kmichalak.routegenerator.routing;

import com.wsyska.kmichalak.routegenerator.graph.CsrGraph;
import com.wsyska.kmichalak.routegenerator.graph.GraphBuilder;
import com.wsyska.kmichalak.routegenerator.graph.SpatialIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Siatka 60 x 60 skrzyżowań co ok. 150 m (ok. 9 x 9 km) z kilkoma losowo usuniętymi odcinkami.
class LoopGeneratorTest {

    private static final int N = 60;
    private static final double STEP_LAT = 0.00135;
    private static final double STEP_LON = 0.00213;

    private CsrGraph graph;
    private LoopGenerator generator;
    private int center;

    @BeforeEach
    void setUp() {
        SplittableRandom random = new SplittableRandom(3);
        GraphBuilder b = new GraphBuilder();
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                b.addNode(50.80 + r * STEP_LAT + random.nextDouble(-2e-4, 2e-4),
                        20.55 + c * STEP_LON + random.nextDouble(-3e-4, 3e-4));
            }
        }
        b.addNode(51.5, 21.5); // odizolowany węzeł, ostatni
        CsrGraph nodesOnly = b.build();
        int attr = b.attributes(Map.of("highway", "residential"));
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                int v = r * N + c;
                if (c + 1 < N && random.nextDouble() > 0.1) {
                    addRoad(b, nodesOnly, v, v + 1, attr);
                }
                if (r + 1 < N && random.nextDouble() > 0.1) {
                    addRoad(b, nodesOnly, v, v + N, attr);
                }
            }
        }
        graph = b.build();
        generator = new LoopGenerator(graph, SpatialIndex.build(graph, 200), LoopGenerator.Options.defaults());
        center = (N / 2) * N + N / 2;
    }

    @Test
    void loopStartsAndEndsAtStartAndIsConnected() {
        LoopGenerator.LoopResult loop = generator.generate(center, 6000, EdgeCost.DISTANCE, 1).orElseThrow();
        int[] nodes = loop.path().nodes();
        int[] edges = loop.path().edges();

        assertEquals(center, nodes[0]);
        assertEquals(center, nodes[nodes.length - 1]);
        for (int i = 0; i < edges.length; i++) {
            assertEquals(nodes[i], graph.edgeSource(edges[i]));
            assertEquals(nodes[i + 1], graph.edgeTarget(edges[i]));
        }
        assertTrue(loop.waypoints().length >= 1);
    }

    // FR-02: rower od 5 km. Krótsze pętle na tej siatce bywają mniej dokładne (do poprawy przy korekcie długości).
    @Test
    void lengthIsCloseToTarget() {
        for (double km : new double[] {5, 8, 12}) {
            for (long seed = 1; seed <= 10; seed++) {
                LoopGenerator.LoopResult loop = generator.generate(center, km * 1000, EdgeCost.DISTANCE, seed)
                        .orElseThrow();
                assertTrue(Math.abs(loop.deviation()) <= 0.10,
                        km + " km, ziarno " + seed + ": odchyłka " + loop.deviation());
            }
        }
    }

    @Test
    void sameSeedGivesSameLoop() {
        int[] first = generator.generate(center, 8000, EdgeCost.DISTANCE, 42).orElseThrow().path().edges();
        int[] second = generator.generate(center, 8000, EdgeCost.DISTANCE, 42).orElseThrow().path().edges();

        assertArrayEquals(first, second);
    }

    @Test
    void differentSeedsGiveDifferentLoops() {
        int[] a = generator.generate(center, 8000, EdgeCost.DISTANCE, 1).orElseThrow().path().edges();
        int[] b = generator.generate(center, 8000, EdgeCost.DISTANCE, 2).orElseThrow().path().edges();

        assertFalse(java.util.Arrays.equals(a, b));
    }

    @Test
    void avoidsGoingBackTheSameWay() {
        double total = 0;
        for (long seed = 1; seed <= 10; seed++) {
            total += generator.generate(center, 8000, EdgeCost.DISTANCE, seed).orElseThrow().reusedShare();
        }
        assertTrue(total / 10 < 0.15, "średni udział powtórzonych odcinków: " + total / 10);
    }

    @Test
    void neverUsesForbiddenEdges() {
        // Zakaz wjazdu na wszystko na północ od środka siatki.
        double limit = graph.lat(center) + 0.0001;
        EdgeCost southOnly = (g, e) ->
                g.lat(g.edgeSource(e)) > limit || g.lat(g.edgeTarget(e)) > limit
                        ? Double.POSITIVE_INFINITY : g.edgeLength(e);

        LoopGenerator.LoopResult loop = generator.generate(center, 6000, southOnly, 5).orElseThrow();

        for (int e : loop.path().edges()) {
            assertFalse(EdgeCost.isForbidden(southOnly.cost(graph, e)));
        }
        assertEquals(center, loop.path().nodes()[loop.path().nodes().length - 1]);
    }

    @Test
    void isolatedStartGivesNoLoop() {
        int isolated = graph.nodeCount() - 1;

        assertEquals(Optional.empty(), generator.generate(isolated, 5000, EdgeCost.DISTANCE, 1));
    }

    private static void addRoad(GraphBuilder b, CsrGraph nodes, int u, int v, int attr) {
        double len = com.wsyska.kmichalak.routegenerator.graph.GeoUtils.haversineMeters(
                nodes.lat(u), nodes.lon(u), nodes.lat(v), nodes.lon(v));
        b.addWaySegment(u, v, len, attr, -1, new float[0], new float[0], 0);
    }
}
