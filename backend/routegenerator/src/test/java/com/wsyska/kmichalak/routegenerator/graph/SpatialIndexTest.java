package com.wsyska.kmichalak.routegenerator.graph;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpatialIndexTest {

    @Test
    void nearestMatchesBruteForce() {
        SplittableRandom random = new SplittableRandom(7);
        GraphBuilder b = new GraphBuilder();
        for (int i = 0; i < 3000; i++) {
            b.addNode(50.5 + random.nextDouble(0.5), 20.2 + random.nextDouble(0.8));
        }
        CsrGraph g = b.build();
        SpatialIndex index = SpatialIndex.build(g, 300);

        for (int q = 0; q < 500; q++) {
            double lat = 50.45 + random.nextDouble(0.6);
            double lon = 20.15 + random.nextDouble(0.9);
            int expected = bruteForce(g, lat, lon, 5000, v -> true);
            assertEquals(expected, index.nearest(lat, lon, 5000), "zapytanie " + q);
        }
    }

    @Test
    void respectsPredicateAndMaxDistance() {
        GraphBuilder b = new GraphBuilder();
        int near = b.addNode(50.0, 20.0);
        int far = b.addNode(50.01, 20.0); // ok. 1,1 km na północ
        CsrGraph g = b.build();
        SpatialIndex index = SpatialIndex.build(g, 200);

        assertEquals(near, index.nearest(50.0001, 20.0, 2000));
        assertEquals(far, index.nearest(50.0001, 20.0, 2000, v -> v != near));
        assertEquals(-1, index.nearest(50.0001, 20.0, 500, v -> v != near));
        assertEquals(-1, index.nearest(51.0, 20.0, 1000));
    }

    @Test
    void destinationIsAtGivenDistanceAndBearing() {
        double[] p = GeoUtils.destination(50.87, 20.63, 90, 1000);

        assertEquals(1000, GeoUtils.haversineMeters(50.87, 20.63, p[0], p[1]), 0.01);
        assertEquals(50.87, p[0], 1e-4);
        double[] north = GeoUtils.destination(50.87, 20.63, 0, 1000);
        assertEquals(20.63, north[1], 1e-9);
    }

    private static int bruteForce(Graph g, double lat, double lon, double max, java.util.function.IntPredicate ok) {
        int best = -1;
        double bestDist = max;
        for (int v = 0; v < g.nodeCount(); v++) {
            double d = GeoUtils.haversineMeters(lat, lon, g.lat(v), g.lon(v));
            if (d <= bestDist && (best < 0 || d < bestDist) && ok.test(v)) {
                best = v;
                bestDist = d;
            }
        }
        return best;
    }
}
