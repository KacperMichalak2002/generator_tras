package com.wsyska.kmichalak.routegenerator.graph;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GraphBuilderTest {

    @Test
    void buildsCsrWithEdgesGroupedBySource() {
        GraphBuilder b = new GraphBuilder();
        int a = b.addNode(50.0, 20.0);
        int c = b.addNode(50.1, 20.1);
        int d = b.addNode(50.2, 20.2);
        b.addEdge(d, a, 3).addEdge(a, c, 1).addEdge(c, d, 2).addEdge(a, d, 4);

        CsrGraph g = b.build();

        assertEquals(3, g.nodeCount());
        assertEquals(4, g.edgeCount());
        assertEquals(Set.of(c, d), targetsOf(g, a));
        assertEquals(Set.of(d), targetsOf(g, c));
        assertEquals(Set.of(a), targetsOf(g, d));
        assertEquals(50.1, g.lat(c), 1e-5);
        assertEquals(20.2, g.lon(d), 1e-5);
    }

    @Test
    void nodeWithoutEdgesHasEmptyRange() {
        GraphBuilder b = new GraphBuilder();
        b.addNode(0, 0);
        int lonely = b.addNode(1, 1);
        b.addNode(2, 2);
        b.addEdge(0, 2, 5);

        CsrGraph g = b.build();

        assertEquals(g.firstEdge(lonely), g.endEdge(lonely));
    }

    @Test
    void edgeLengthFromCoordinates() {
        GraphBuilder b = new GraphBuilder();
        int x = b.addNode(50.0, 20.0);
        int y = b.addNode(50.0 + 1.0 / 111.195, 20.0); // ok. 1 km na północ
        b.addEdge(x, y);

        CsrGraph g = b.build();

        assertEquals(1000, g.edgeLength(g.firstEdge(x)), 1);
    }

    @Test
    void growsBeyondInitialCapacity() {
        GraphBuilder b = new GraphBuilder();
        for (int i = 0; i < 1000; i++) {
            b.addNode(i * 1e-4, 0);
        }
        for (int i = 0; i < 999; i++) {
            b.addBidirectionalEdge(i, i + 1, 1);
        }

        CsrGraph g = b.build();

        assertEquals(1000, g.nodeCount());
        assertEquals(1998, g.edgeCount());
    }

    @Test
    void rejectsInvalidEdges() {
        GraphBuilder b = new GraphBuilder();
        b.addNode(0, 0);
        assertThrows(IndexOutOfBoundsException.class, () -> b.addEdge(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> b.addEdge(0, 0, -1));
    }

    private static Set<Integer> targetsOf(Graph g, int node) {
        Set<Integer> result = new HashSet<>();
        for (int e = g.firstEdge(node); e < g.endEdge(node); e++) {
            result.add(g.edgeTarget(e));
        }
        return result;
    }
}
