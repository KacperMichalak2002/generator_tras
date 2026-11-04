package com.wsyska.kmichalak.routegenerator.graph;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphStructureTest {

    @Test
    void waySegmentHasTwoEdgesSharingTagsAndGeometry() {
        GraphBuilder b = new GraphBuilder();
        int a = b.addNode(50.0, 20.0);
        int c = b.addNode(50.0, 20.003);
        int attr = b.attributes(Map.of("highway", "track"));
        b.addWaySegment(a, c, 250, attr, 77L,
                new float[] {50.001f, 50.002f}, new float[] {20.001f, 20.002f}, 2);

        CsrGraph g = b.build();

        assertEquals(1, g.segmentCount());
        assertEquals(2, g.edgeCount());
        int forward = g.firstEdge(a);
        int backward = g.firstEdge(c);
        assertFalse(g.isReverse(forward));
        assertTrue(g.isReverse(backward));
        assertEquals(Map.of("highway", "track"), g.edgeTags(backward));
        assertEquals(77L, g.edgeWayId(backward));
        assertEquals(a, g.edgeSource(forward));
        assertEquals(c, g.edgeSource(backward));
        assertEquals(4, g.edgePointCount(backward));
        assertEquals(50.0, g.edgePointLat(forward, 0), 1e-5);
        assertEquals(50.001, g.edgePointLat(forward, 1), 1e-5);
        assertEquals(50.002, g.edgePointLat(backward, 1), 1e-5);
        assertEquals(20.0, g.edgePointLon(backward, 3), 1e-5);
    }

    @Test
    void manualEdgesHaveEmptyTagsAndNoWay() {
        GraphBuilder b = new GraphBuilder();
        b.addNode(0, 0);
        b.addNode(0, 1);
        b.addEdge(0, 1, 5);

        CsrGraph g = b.build();

        assertEquals(Map.of(), g.edgeTags(0));
        assertEquals(-1L, g.edgeWayId(0));
        assertEquals(2, g.edgePointCount(0));
        assertEquals(1, g.attributeCount());
    }

    @Test
    void attributesAreDeduplicated() {
        GraphBuilder b = new GraphBuilder();
        int first = b.attributes(Map.of("highway", "residential", "surface", "asphalt"));
        int second = b.attributes(Map.of("surface", "asphalt", "highway", "residential"));
        int other = b.attributes(Map.of("highway", "track"));

        assertEquals(first, second);
        assertTrue(other != first);
        assertEquals(0, b.attributes(Map.of()));
    }

    @Test
    void componentsAndRetainNodes() {
        GraphBuilder b = new GraphBuilder();
        for (int i = 0; i < 6; i++) {
            b.addNode(50 + i * 0.01, 20);
        }
        int attr = b.attributes(Map.of("highway", "service"));
        b.addWaySegment(0, 1, 10, attr, 1, new float[] {50.005f}, new float[] {20f}, 1);
        b.addWaySegment(1, 2, 20, attr, 2, new float[0], new float[0], 0);
        b.addEdge(2, 0, 30);
        b.addWaySegment(4, 5, 40, attr, 3, new float[0], new float[0], 0);
        CsrGraph g = b.build();

        ConnectedComponents cc = ConnectedComponents.of(g);
        assertEquals(3, cc.count());
        assertEquals(3, cc.largestSize());
        assertEquals(cc.componentOf(0), cc.componentOf(2));

        CsrGraph big = g.retainNodes(cc.nodesInComponentsOfAtLeast(3));

        assertEquals(3, big.nodeCount());
        assertEquals(5, big.edgeCount());
        assertEquals(3, big.segmentCount());
        int e01 = big.firstEdge(0);
        assertEquals(1, big.edgeTarget(e01));
        assertEquals(3, big.edgePointCount(e01));
        assertEquals(50.005, big.edgePointLat(e01, 1), 1e-5);
        assertEquals(Map.of("highway", "service"), big.edgeTags(e01));
        assertArrayEquals(new int[] {0, 1}, outTargets(big, 2));
        assertArrayEquals(new int[] {1}, outTargets(big, 0));
    }

    private static int[] outTargets(Graph g, int v) {
        int[] out = new int[g.endEdge(v) - g.firstEdge(v)];
        for (int e = g.firstEdge(v); e < g.endEdge(v); e++) {
            out[e - g.firstEdge(v)] = g.edgeTarget(e);
        }
        Arrays.sort(out);
        return out;
    }
}
