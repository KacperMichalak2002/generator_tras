package com.wsyska.kmichalak.routegenerator.osm;

import com.wsyska.kmichalak.routegenerator.graph.CsrGraph;
import com.wsyska.kmichalak.routegenerator.graph.GeoUtils;
import com.wsyska.kmichalak.routegenerator.graph.Graph;
import com.wsyska.kmichalak.routegenerator.routing.Dijkstra;
import com.wsyska.kmichalak.routegenerator.routing.EdgeCost;
import com.wsyska.kmichalak.routegenerator.routing.PathResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


public final class GraphImportDemo {

    private static final Set<String> NO_BIKE_HIGHWAYS =
            Set.of("motorway", "motorway_link", "trunk", "trunk_link", "steps");
    private static final Set<String> NO_ACCESS = Set.of("no", "private");

    private GraphImportDemo() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1 && args.length != 5 && args.length != 6) {
            System.err.println("Użycie: GraphImportDemo <plik.osm.pbf> [latA lonA latB lonB [trasa.geojson]]");
            System.exit(1);
        }
        OsmGraphImporter.Result result = OsmGraphImporter.importFile(Path.of(args[0]));
        printStats(result.stats());
        if (args.length == 1) {
            return;
        }

        CsrGraph graph = result.graph();
        EdgeCost cost = demoBikeCost(graph);
        int from = nearestNode(graph, Double.parseDouble(args[1]), Double.parseDouble(args[2]));
        int to = nearestNode(graph, Double.parseDouble(args[3]), Double.parseDouble(args[4]));

        long start = System.nanoTime();
        Optional<PathResult> path = new Dijkstra(graph).route(from, to, cost);
        long ms = (System.nanoTime() - start) / 1_000_000;

        if (path.isEmpty()) {
            System.out.printf("Brak trasy między węzłami %d i %d (%d ms)%n", from, to, ms);
            return;
        }
        PathResult p = path.get();
        System.out.printf(Locale.ROOT, "Trasa: %.2f km, %d krawędzi, Dijkstra %d ms%n",
                p.lengthM() / 1000, p.edges().length, ms);
        if (args.length == 6) {
            Files.writeString(Path.of(args[5]), toGeoJson(graph, p));
            System.out.println("Zapisano " + args[5] + " (podgląd: geojson.io)");
        }
    }


    static EdgeCost demoBikeCost(Graph graph) {
        int n = graph.attributeCount();
        boolean[] forward = new boolean[n];
        boolean[] backward = new boolean[n];
        for (int a = 0; a < n; a++) {
            Map<String, String> tags = graph.attributeTags(a);
            boolean allowed = bikeAllowed(tags);
            Oneway oneway = Oneway.of(tags, "bicycle");
            forward[a] = allowed && oneway.allows(false);
            backward[a] = allowed && oneway.allows(true);
        }
        return (g, e) -> {
            int a = g.edgeAttributes(e);
            boolean ok = g.isReverse(e) ? backward[a] : forward[a];
            return ok ? g.edgeLength(e) : Double.POSITIVE_INFINITY;
        };
    }

    private static boolean bikeAllowed(Map<String, String> tags) {
        String bicycle = tags.get("bicycle");
        if (bicycle != null) {
            return !"no".equals(bicycle) && !"use_sidepath".equals(bicycle);
        }
        String highway = tags.get("highway");
        if (highway == null || NO_BIKE_HIGHWAYS.contains(highway)) {
            return false;
        }
        String vehicle = tags.get("vehicle");
        String access = vehicle != null ? vehicle : tags.get("access");
        return access == null || !NO_ACCESS.contains(access);
    }

    // Przeszukanie wszystkich węzłów; wystarcza do demo. Właściwe przyciąganie do drogi to FR-16.
    static int nearestNode(Graph graph, double lat, double lon) {
        int best = -1;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int v = 0; v < graph.nodeCount(); v++) {
            if (graph.firstEdge(v) == graph.endEdge(v)) {
                continue;
            }
            double d = GeoUtils.haversineMeters(lat, lon, graph.lat(v), graph.lon(v));
            if (d < bestDist) {
                bestDist = d;
                best = v;
            }
        }
        return best;
    }

    static String toGeoJson(Graph graph, PathResult path) {
        StringBuilder sb = new StringBuilder(
                "{\"type\":\"Feature\",\"properties\":{\"lengthKm\":");
        sb.append(String.format(Locale.ROOT, "%.3f", path.lengthM() / 1000));
        sb.append("},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[");
        int[] edges = path.edges();
        for (int k = 0; k < edges.length; k++) {
            int e = edges[k];
            for (int i = k == 0 ? 0 : 1; i < graph.edgePointCount(e); i++) {
                if (k > 0 || i > 0) {
                    sb.append(',');
                }
                sb.append(String.format(Locale.ROOT, "[%.7f,%.7f]", graph.edgePointLon(e, i), graph.edgePointLat(e, i)));
            }
        }
        return sb.append("]}}").toString();
    }

    private static void printStats(OsmGraphImporter.Stats s) {
        System.out.printf(Locale.ROOT, """
                Drogi w pliku:            %,d
                Drogi w grafie:           %,d
                Węzły w pliku:            %,d (użyte przez drogi: %,d, brakujące odwołania: %,d)
                Węzły grafu:              %,d
                Segmenty / krawędzie:     %,d / %,d
                Punkty pośrednie:         %,d
                Zestawy tagów:            %,d
                Usunięte węzły (wyspy):   %,d (składowych przed: %,d)
                Długość sieci:            %,.1f km
                Czas: drogi %,d ms, węzły %,d ms, budowa %,d ms, razem %,d ms
                Pamięć grafu:             %.1f MB
                """,
                s.waysRead(), s.waysKept(), s.nodesRead(), s.referencedNodes(), s.missingNodeRefs(),
                s.routingNodes(), s.segments(), s.edges(), s.pillarPoints(), s.attributeSets(),
                s.removedNodes(), s.componentsBefore(), s.totalLengthKm(),
                s.waysPassMs(), s.nodesPassMs(), s.buildMs(), s.totalMs(), s.graphBytes() / 1e6);
    }
}
