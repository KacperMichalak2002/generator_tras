package com.wsyska.kmichalak.routegenerator.osm;

import com.wsyska.kmichalak.routegenerator.graph.CsrGraph;
import com.wsyska.kmichalak.routegenerator.graph.GeoUtils;
import com.wsyska.kmichalak.routegenerator.routing.Dijkstra;
import com.wsyska.kmichalak.routegenerator.routing.EdgeCost;
import com.wsyska.kmichalak.routegenerator.routing.PathResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * import.osm.pbf (id drogi: węzły OSM, tagi):
 *   10: 1-2-3-4-5        residential            3 to skrzyżowanie z drogą 11
 *   11: 3-6-7            residential
 *   12: 5-8              highway=proposed       odrzucona
 *   13: 8-9-10-8         building=yes           odrzucona
 *   14: 11-12-13-11      pedestrian, area=yes   odrzucona
 *   15: 7-14             primary, oneway=yes, surface=asphalt, name=Testowa
 *   16: 14-15-999-16-17  track                  węzła 999 nie ma w pliku
 *   17: 1-18-19-20-1     residential            pętla podpięta tylko w węźle 1
 *   18: 30-31            service                odcięta wyspa
 *
 * Węzły grafu: 1, 3, 5, 7, 14, 15, 16, 17, 19 (środek pętli), 30, 31.
 */
class OsmGraphImporterTest {

    private static final Map<Long, double[]> OSM_NODES = Map.ofEntries(
            Map.entry(1L, new double[] {50.8700, 20.6200}), Map.entry(2L, new double[] {50.8700, 20.6210}),
            Map.entry(3L, new double[] {50.8700, 20.6220}), Map.entry(4L, new double[] {50.8705, 20.6230}),
            Map.entry(5L, new double[] {50.8710, 20.6240}), Map.entry(6L, new double[] {50.8690, 20.6220}),
            Map.entry(7L, new double[] {50.8680, 20.6220}), Map.entry(14L, new double[] {50.8670, 20.6230}),
            Map.entry(15L, new double[] {50.8660, 20.6240}), Map.entry(16L, new double[] {50.8650, 20.6250}),
            Map.entry(17L, new double[] {50.8640, 20.6260}), Map.entry(18L, new double[] {50.8710, 20.6190}),
            Map.entry(19L, new double[] {50.8720, 20.6200}), Map.entry(20L, new double[] {50.8710, 20.6210}),
            Map.entry(30L, new double[] {50.9000, 20.7000}), Map.entry(31L, new double[] {50.9010, 20.7000}));

    @Test
    void keepsOnlyJunctionsAndWayEndsAsNodes() throws IOException {
        OsmGraphImporter.Result result = importFixture(1);
        CsrGraph g = result.graph();

        assertEquals(11, g.nodeCount());
        assertEquals(9, g.segmentCount());
        assertEquals(18, g.edgeCount());
        assertEquals(3, g.endEdge(node(g, 3)) - g.firstEdge(node(g, 3)));
        assertEquals(-1, findNode(g, 2));
        assertEquals(-1, findNode(g, 4));
        assertEquals(-1, findNode(g, 6));
    }

    @Test
    void edgeLengthAndGeometryFollowOriginalWay() throws IOException {
        CsrGraph g = importFixture(1).graph();
        int forward = edge(g, node(g, 3), node(g, 1));

        assertEquals(length(3, 2, 1), g.edgeLength(forward), 0.01);
        assertEquals(3, g.edgePointCount(forward));
        assertPoint(g, forward, 0, 3);
        assertPoint(g, forward, 1, 2);
        assertPoint(g, forward, 2, 1);

        int backward = edge(g, node(g, 1), node(g, 3));
        assertNotEquals(g.isReverse(forward), g.isReverse(backward));
        assertPoint(g, backward, 0, 1);
        assertPoint(g, backward, 1, 2);
        assertPoint(g, backward, 2, 3);
    }

    @Test
    void filtersOutNonRoutableWays() throws IOException {
        OsmGraphImporter.Result result = importFixture(1);

        assertEquals(9, result.stats().waysRead());
        assertEquals(6, result.stats().waysKept());
        Set<Long> wayIds = new HashSet<>();
        for (int e = 0; e < result.graph().edgeCount(); e++) {
            wayIds.add(result.graph().edgeWayId(e));
        }
        assertEquals(Set.of(10L, 11L, 15L, 16L, 17L, 18L), wayIds);
    }

    @Test
    void keepsOnlyRoutingTagsAndMarksDirection() throws IOException {
        CsrGraph g = importFixture(1).graph();
        int along = edge(g, node(g, 7), node(g, 14));
        int against = edge(g, node(g, 14), node(g, 7));

        assertEquals(Map.of("highway", "primary", "oneway", "yes", "surface", "asphalt"), g.edgeTags(along));
        assertFalse(g.isReverse(along));
        assertTrue(g.isReverse(against));
        assertTrue(Oneway.of(g.edgeTags(along)).allows(g.isReverse(along)));
        assertFalse(Oneway.of(g.edgeTags(against)).allows(g.isReverse(against)));
    }

    @Test
    void identicalTagSetsShareOneAttributeId() throws IOException {
        CsrGraph g = importFixture(1).graph();

        int way10 = edge(g, node(g, 1), node(g, 3));
        int way11 = edge(g, node(g, 3), node(g, 7));
        int way15 = edge(g, node(g, 7), node(g, 14));
        assertEquals(g.edgeAttributes(way10), g.edgeAttributes(way11));
        assertNotEquals(g.edgeAttributes(way10), g.edgeAttributes(way15));
    }

    @Test
    void splitsWayAtMissingNode() throws IOException {
        OsmGraphImporter.Result result = importFixture(1);
        CsrGraph g = result.graph();

        assertEquals(1, result.stats().missingNodeRefs());
        edge(g, node(g, 14), node(g, 15));
        edge(g, node(g, 16), node(g, 17));
        for (int e = g.firstEdge(node(g, 15)); e < g.endEdge(node(g, 15)); e++) {
            assertEquals(node(g, 14), g.edgeTarget(e));
        }
    }

    @Test
    void splitsClosedLoopSoThereAreNoSelfLoops() throws IOException {
        CsrGraph g = importFixture(1).graph();

        for (int e = 0; e < g.edgeCount(); e++) {
            assertNotEquals(g.edgeSource(e), g.edgeTarget(e));
        }
        // Po podziale są dwie drogi 1→19: przez 18 (zgodnie z drogą) i przez 20 (pod prąd pętli).
        int middle = node(g, 19);
        int viaEighteen = forwardEdge(g, node(g, 1), middle);
        int viaTwenty = forwardEdge(g, middle, node(g, 1));
        assertEquals(length(1, 18, 19), g.edgeLength(viaEighteen), 0.01);
        assertEquals(length(19, 20, 1), g.edgeLength(viaTwenty), 0.01);
        assertPoint(g, viaEighteen, 1, 18);
        assertPoint(g, viaTwenty, 1, 20);
        assertEquals(2, g.endEdge(middle) - g.firstEdge(middle));
        assertEquals(3, g.endEdge(node(g, 1)) - g.firstEdge(node(g, 1)));
    }

    @Test
    void removesSmallIslands() throws IOException {
        OsmGraphImporter.Result result = importFixture(5);
        CsrGraph g = result.graph();

        assertEquals(3, result.stats().componentsBefore());
        assertEquals(4, result.stats().removedNodes());
        assertEquals(7, g.nodeCount());
        assertEquals(-1, findNode(g, 30));
        assertEquals(-1, findNode(g, 16));
        int e = edge(g, node(g, 3), node(g, 1));
        assertPoint(g, e, 1, 2);
        assertEquals(length(3, 2, 1), g.edgeLength(e), 0.01);
    }

    @Test
    void routesOnImportedGraph() throws IOException {
        CsrGraph g = importFixture(1).graph();
        Dijkstra dijkstra = new Dijkstra(g);

        PathResult path = dijkstra.route(node(g, 5), node(g, 15), EdgeCost.DISTANCE).orElseThrow();

        assertArrayEquals(new int[] {node(g, 5), node(g, 3), node(g, 7), node(g, 14), node(g, 15)}, path.nodes());
        assertEquals(length(5, 4, 3) + length(3, 6, 7) + length(7, 14) + length(14, 15), path.lengthM(), 0.05);
    }

    @Test
    void bikeCostRespectsOneway() throws IOException {
        CsrGraph g = importFixture(1).graph();
        Dijkstra dijkstra = new Dijkstra(g);
        EdgeCost bike = GraphImportDemo.demoBikeCost(g);

        assertTrue(dijkstra.route(node(g, 7), node(g, 14), bike).isPresent());
        assertEquals(Optional.empty(), dijkstra.route(node(g, 14), node(g, 7), bike));
        assertTrue(dijkstra.route(node(g, 14), node(g, 7), EdgeCost.DISTANCE).isPresent());
    }

    @Test
    void geoJsonContainsWholeGeometry() throws IOException {
        CsrGraph g = importFixture(1).graph();
        PathResult path = new Dijkstra(g).route(node(g, 1), node(g, 7), EdgeCost.DISTANCE).orElseThrow();

        String json = GraphImportDemo.toGeoJson(g, path);

        // 1 -(2)- 3 -(6)- 7: pięć punktów, punkt 3 tylko raz
        assertEquals(5, json.split("\\],\\[").length);
        assertTrue(json.startsWith("{\"type\":\"Feature\""));
    }

    @Test
    void customFilterCanDropTracks() throws IOException {
        Set<String> highways = new HashSet<>(RoadFilter.ROUTABLE_HIGHWAYS);
        highways.remove("track");
        RoadFilter filter = new RoadFilter(highways, RoadFilter.KEPT_TAG_KEYS);

        OsmGraphImporter.Result result = OsmGraphImporter.importGraph(
                OsmGraphImporter.PbfSource.of(fixtureBytes()), new OsmGraphImporter.Options(filter, 1));

        assertEquals(5, result.stats().waysKept());
        assertEquals(0, result.stats().missingNodeRefs());
    }

    private static OsmGraphImporter.Result importFixture(int minComponentNodes) throws IOException {
        return OsmGraphImporter.importGraph(OsmGraphImporter.PbfSource.of(fixtureBytes()),
                OsmGraphImporter.Options.defaults().withMinComponentNodes(minComponentNodes));
    }

    private static byte[] fixtureBytes() throws IOException {
        try (InputStream in = OsmGraphImporterTest.class.getResourceAsStream("/osm/import.osm.pbf")) {
            return in.readAllBytes();
        }
    }

    private static int node(CsrGraph g, long osmId) {
        int v = findNode(g, osmId);
        assertTrue(v >= 0, "Brak w grafie węzła OSM " + osmId);
        return v;
    }

    private static int findNode(CsrGraph g, long osmId) {
        double[] p = OSM_NODES.get(osmId);
        for (int v = 0; v < g.nodeCount(); v++) {
            if (Math.abs(g.lat(v) - p[0]) < 1e-5 && Math.abs(g.lon(v) - p[1]) < 1e-5) {
                return v;
            }
        }
        return -1;
    }

    private static int edge(CsrGraph g, int from, int to) {
        List<Integer> found = new ArrayList<>();
        for (int e = g.firstEdge(from); e < g.endEdge(from); e++) {
            if (g.edgeTarget(e) == to) {
                found.add(e);
            }
        }
        assertEquals(1, found.size(), "Krawędzie " + from + "→" + to);
        return found.get(0);
    }

    private static int forwardEdge(CsrGraph g, int from, int to) {
        List<Integer> found = new ArrayList<>();
        for (int e = g.firstEdge(from); e < g.endEdge(from); e++) {
            if (g.edgeTarget(e) == to && !g.isReverse(e)) {
                found.add(e);
            }
        }
        assertEquals(1, found.size(), "Krawędzie zgodne z drogą " + from + "→" + to);
        return found.get(0);
    }

    private static double length(long... osmIds) {
        double sum = 0;
        for (int i = 1; i < osmIds.length; i++) {
            double[] a = OSM_NODES.get(osmIds[i - 1]);
            double[] b = OSM_NODES.get(osmIds[i]);
            sum += GeoUtils.haversineMeters(a[0], a[1], b[0], b[1]);
        }
        return sum;
    }

    private static void assertPoint(CsrGraph g, int edge, int index, long osmId) {
        double[] p = OSM_NODES.get(osmId);
        assertEquals(p[0], g.edgePointLat(edge, index), 1e-5);
        assertEquals(p[1], g.edgePointLon(edge, index), 1e-5);
    }
}
