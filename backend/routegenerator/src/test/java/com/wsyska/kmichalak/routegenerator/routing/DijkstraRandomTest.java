package com.wsyska.kmichalak.routegenerator.routing;

import com.wsyska.kmichalak.routegenerator.graph.CsrGraph;
import com.wsyska.kmichalak.routegenerator.graph.GeoUtils;
import com.wsyska.kmichalak.routegenerator.graph.Graph;
import com.wsyska.kmichalak.routegenerator.graph.GraphBuilder;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Dijkstra na losowych grafach porównana z najprostszą możliwą implementacją wzorcową
// (O(n²), bez kopca i bez wczesnego zakończenia).
class DijkstraRandomTest {

    @Test
    void matchesReferenceOnRandomGraphs() {
        SplittableRandom random = new SplittableRandom(2024);
        for (int round = 0; round < 30; round++) {
            CsrGraph g = randomGraph(random, 40 + random.nextInt(200));
            EdgeCost cost = (graph, e) -> (graph.edgeSegment(e) % 11 == 0)
                    ? Double.POSITIVE_INFINITY
                    : graph.edgeLength(e) * (1 + graph.edgeSegment(e) % 3);
            Dijkstra dijkstra = new Dijkstra(g);
            for (int q = 0; q < 20; q++) {
                int s = random.nextInt(g.nodeCount());
                int t = random.nextInt(g.nodeCount());
                double expected = referenceDistance(g, s, cost)[t];
                Optional<PathResult> path = dijkstra.route(s, t, cost);

                if (expected == Double.POSITIVE_INFINITY) {
                    assertFalse(path.isPresent(), "runda " + round + ": " + s + "→" + t);
                } else {
                    PathResult p = path.orElseThrow();
                    assertEquals(expected, p.cost(), 1e-6);
                    assertValidPath(g, p, s, t, cost);
                }
            }
        }
    }

    @Test
    void boundsKeepSearchInsideCircle() {
        SplittableRandom random = new SplittableRandom(5);
        CsrGraph g = randomGraph(random, 400);
        Dijkstra dijkstra = new Dijkstra(g);
        int s = 0;
        SearchBounds bounds = SearchBounds.circle(g.lat(s), g.lon(s), 5000);
        int found = 0;
        for (int t = 1; t < g.nodeCount(); t++) {
            boolean inside = bounds.contains(g.lat(t), g.lon(t));
            Optional<PathResult> path = dijkstra.route(s, t, EdgeCost.DISTANCE, bounds);
            if (!inside) {
                assertFalse(path.isPresent());
                continue;
            }
            if (path.isPresent()) {
                found++;
                for (int v : path.get().nodes()) {
                    assertTrue(v == s || bounds.contains(g.lat(v), g.lon(v)));
                }
                double unbounded = dijkstra.route(s, t, EdgeCost.DISTANCE).orElseThrow().cost();
                assertTrue(path.get().cost() >= unbounded - 1e-9);
            }
        }
        assertTrue(found > 10, "za mało tras w granicy: " + found);
    }

    @Test
    void boundsCanForceLongerRoute() {
        // 0 —(krótko przez 1, poza granicą)— 2, albo długo przez 3 i 4 w granicy.
        GraphBuilder b = new GraphBuilder();
        int a = b.addNode(50.000, 20.000);
        int far = b.addNode(50.020, 20.005);
        int c = b.addNode(50.000, 20.010);
        int d = b.addNode(49.998, 20.003);
        int e = b.addNode(49.998, 20.007);
        b.addBidirectionalEdge(a, far, 100).addBidirectionalEdge(far, c, 100)
                .addBidirectionalEdge(a, d, 300).addBidirectionalEdge(d, e, 300).addBidirectionalEdge(e, c, 300);
        CsrGraph g = b.build();
        Dijkstra dijkstra = new Dijkstra(g);

        assertEquals(200, dijkstra.route(a, c, EdgeCost.DISTANCE).orElseThrow().cost(), 1e-9);
        SearchBounds bounds = SearchBounds.circle(50.0, 20.005, 1000);
        PathResult bounded = dijkstra.route(a, c, EdgeCost.DISTANCE, bounds).orElseThrow();
        assertEquals(900, bounded.cost(), 1e-9);
        assertEquals(Arrays.toString(new int[] {a, d, e, c}), Arrays.toString(bounded.nodes()));
    }

    @Test
    void searchBoundsAgreeWithHaversine() {
        SearchBounds bounds = SearchBounds.circle(50.87, 20.63, 50_000);
        for (int bearing = 0; bearing < 360; bearing += 15) {
            double[] inside = GeoUtils.destination(50.87, 20.63, bearing, 49_000);
            double[] outside = GeoUtils.destination(50.87, 20.63, bearing, 51_000);
            assertTrue(bounds.contains(inside[0], inside[1]), "kierunek " + bearing);
            assertFalse(bounds.contains(outside[0], outside[1]), "kierunek " + bearing);
        }
    }

    @Test
    void helperStructuresGrowCorrectly() {
        NodeLabels labels = new NodeLabels(2);
        for (int i = 0; i < 50_000; i++) {
            labels.set(labels.findOrAdd(i * 37), i, i);
        }
        assertEquals(50_000, labels.size());
        for (int i = 0; i < 50_000; i++) {
            int slot = labels.find(i * 37);
            assertEquals(i, labels.dist(slot), 0);
            assertEquals(i, labels.prevEdge(slot));
        }
        assertEquals(-1, labels.find(1));

        MinHeap heap = new MinHeap(2);
        SplittableRandom random = new SplittableRandom(1);
        double[] keys = new double[10_000];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = random.nextDouble();
            heap.push(keys[i], i);
        }
        Arrays.sort(keys);
        for (double key : keys) {
            assertEquals(key, heap.peekKey(), 0);
            heap.pop();
        }
        assertTrue(heap.isEmpty());

        IntHashSet set = new IntHashSet(2);
        assertTrue(set.add(5));
        assertFalse(set.add(5));
        for (int i = 0; i < 10_000; i++) {
            set.add(i * 13);
        }
        assertTrue(set.contains(13 * 9_999));
        assertFalse(set.contains(14));
        assertEquals(10_000 + 1, set.size());
    }

    private static CsrGraph randomGraph(SplittableRandom random, int n) {
        GraphBuilder b = new GraphBuilder();
        for (int i = 0; i < n; i++) {
            b.addNode(50.0 + random.nextDouble(0.08), 20.0 + random.nextDouble(0.12));
        }
        CsrGraph nodes = b.build();
        int edges = n * 2;
        for (int i = 0; i < edges; i++) {
            int u = random.nextInt(n);
            int v = random.nextInt(n);
            double len = GeoUtils.haversineMeters(nodes.lat(u), nodes.lon(u), nodes.lat(v), nodes.lon(v));
            if (random.nextDouble() < 0.7) {
                b.addWaySegment(u, v, len, 0, -1, new float[0], new float[0], 0);
            } else {
                b.addEdge(u, v, len);
            }
        }
        return b.build();
    }

    private static double[] referenceDistance(Graph g, int source, EdgeCost cost) {
        int n = g.nodeCount();
        double[] dist = new double[n];
        boolean[] done = new boolean[n];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        dist[source] = 0;
        for (int round = 0; round < n; round++) {
            int v = -1;
            for (int i = 0; i < n; i++) {
                if (!done[i] && (v < 0 || dist[i] < dist[v])) {
                    v = i;
                }
            }
            if (v < 0 || dist[v] == Double.POSITIVE_INFINITY) {
                break;
            }
            done[v] = true;
            for (int e = g.firstEdge(v); e < g.endEdge(v); e++) {
                double c = cost.cost(g, e);
                if (c != Double.POSITIVE_INFINITY && dist[v] + c < dist[g.edgeTarget(e)]) {
                    dist[g.edgeTarget(e)] = dist[v] + c;
                }
            }
        }
        return dist;
    }

    private static void assertValidPath(Graph g, PathResult p, int s, int t, EdgeCost cost) {
        int[] nodes = p.nodes();
        int[] edges = p.edges();
        assertEquals(s, nodes[0]);
        assertEquals(t, nodes[nodes.length - 1]);
        double sum = 0;
        double length = 0;
        for (int i = 0; i < edges.length; i++) {
            assertEquals(nodes[i], g.edgeSource(edges[i]));
            assertEquals(nodes[i + 1], g.edgeTarget(edges[i]));
            sum += cost.cost(g, edges[i]);
            length += g.edgeLength(edges[i]);
        }
        assertEquals(p.cost(), sum, 1e-6);
        assertEquals(p.lengthM(), length, 1e-6);
    }
}
