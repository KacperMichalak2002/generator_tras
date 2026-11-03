package com.wsyska.kmichalak.routegenerator.routing;

import com.wsyska.kmichalak.routegenerator.graph.CsrGraph;
import com.wsyska.kmichalak.routegenerator.graph.GraphBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Graf syntetyczny (długości w metrach, krawędzie dwukierunkowe):
 *
 * <pre>
 *        100        100
 *   0 ─────── 1 ─────── 2
 *   │                   │
 *   │ 50            50  │
 *   │       100         │
 *   3 ─────── 4 ─────── 5        6 (odizolowany)
 *       300  (3-4)
 * </pre>
 *
 * Krawędzie: 0-1:100, 1-2:100, 0-3:50, 3-4:300, 4-5:100, 2-5:50.
 * Najkrótsza 0→5: 0-1-2-5 = 250 (a nie 0-3-4-5 = 450).
 */
class DijkstraTest {

    private CsrGraph graph;
    private Dijkstra dijkstra;

    @BeforeEach
    void setUp() {
        GraphBuilder b = new GraphBuilder();
        for (int i = 0; i < 7; i++) {
            b.addNode(50.86 + i * 0.001, 20.62);
        }
        b.addBidirectionalEdge(0, 1, 100)
                .addBidirectionalEdge(1, 2, 100)
                .addBidirectionalEdge(0, 3, 50)
                .addBidirectionalEdge(3, 4, 300)
                .addBidirectionalEdge(4, 5, 100)
                .addBidirectionalEdge(2, 5, 50);
        graph = b.build();
        dijkstra = new Dijkstra(graph);
    }

    @Test
    void findsShortestPath() {
        PathResult path = dijkstra.route(0, 5, EdgeCost.DISTANCE).orElseThrow();

        assertArrayEquals(new int[] {0, 1, 2, 5}, path.nodes());
        assertEquals(250, path.lengthM(), 1e-6);
        assertEquals(250, path.cost(), 1e-6);
        assertEquals(3, path.edges().length);
    }

    @Test
    void edgesOfPathConnectConsecutiveNodes() {
        PathResult path = dijkstra.route(3, 2, EdgeCost.DISTANCE).orElseThrow();
        int[] nodes = path.nodes();
        int[] edges = path.edges();
        for (int i = 0; i < edges.length; i++) {
            assertEquals(nodes[i + 1], graph.edgeTarget(edges[i]));
        }
    }

    @Test
    void returnsEmptyWhenTargetUnreachable() {
        assertEquals(Optional.empty(), dijkstra.route(0, 6, EdgeCost.DISTANCE));
    }

    @Test
    void sourceEqualsTargetGivesSingleNodePath() {
        PathResult path = dijkstra.route(4, 4, EdgeCost.DISTANCE).orElseThrow();

        assertArrayEquals(new int[] {4}, path.nodes());
        assertEquals(0, path.edges().length);
        assertEquals(0, path.lengthM());
    }

    @Test
    void forbiddenEdgesAreAvoided() {

        EdgeCost noTwoToFive = (g, e) -> isEdge(2, 5, e) ? Double.POSITIVE_INFINITY : g.edgeLength(e);

        PathResult path = dijkstra.route(0, 5, noTwoToFive).orElseThrow();

        assertArrayEquals(new int[] {0, 3, 4, 5}, path.nodes());
        assertEquals(450, path.lengthM(), 1e-6);
    }

    @Test
    void costCanDifferFromLength() {
        EdgeCost avoidMainRoad = (g, e) -> isEdge(1, 2, e) ? 10 * g.edgeLength(e) : g.edgeLength(e);

        PathResult path = dijkstra.route(0, 5, avoidMainRoad).orElseThrow();

        assertArrayEquals(new int[] {0, 3, 4, 5}, path.nodes());
        assertEquals(450, path.cost(), 1e-6);
        assertEquals(450, path.lengthM(), 1e-6);
    }

    @Test
    void rejectsUnknownNode() {
        assertThrows(IndexOutOfBoundsException.class, () -> dijkstra.route(0, 99, EdgeCost.DISTANCE));
    }

    @Test
    void rejectsNegativeCost() {
        EdgeCost negative = (g, e) -> -1;
        assertThrows(IllegalStateException.class, () -> dijkstra.route(0, 5, negative));
    }

    @Test
    void sameInstanceWorksFromManyThreads() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Callable<PathResult>> tasks = IntStream.range(0, 200)
                    .<Callable<PathResult>>mapToObj(i -> () -> dijkstra.route(0, 5, EdgeCost.DISTANCE).orElseThrow())
                    .toList();
            for (Future<PathResult> f : pool.invokeAll(tasks)) {
                assertArrayEquals(new int[] {0, 1, 2, 5}, f.get().nodes());
            }
        }
    }

    private boolean isEdge(int from, int to, int edge) {
        return edge >= graph.firstEdge(from) && edge < graph.endEdge(from) && graph.edgeTarget(edge) == to;
    }
}
