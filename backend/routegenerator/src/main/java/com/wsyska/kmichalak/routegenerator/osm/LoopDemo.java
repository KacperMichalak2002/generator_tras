package com.wsyska.kmichalak.routegenerator.osm;

import com.wsyska.kmichalak.routegenerator.graph.CsrGraph;
import com.wsyska.kmichalak.routegenerator.graph.SpatialIndex;
import com.wsyska.kmichalak.routegenerator.routing.EdgeCost;
import com.wsyska.kmichalak.routegenerator.routing.GeoJson;
import com.wsyska.kmichalak.routegenerator.routing.LoopGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;


public final class LoopDemo {

    private static final String[] COLORS = {"#e41a1c", "#377eb8", "#4daf4a"};

    private LoopDemo() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 4 || args.length > 6) {
            System.err.println("Użycie: LoopDemo <plik.osm.pbf> lat lon km [ziarno] [pętle.geojson]");
            System.exit(1);
        }
        double lat = Double.parseDouble(args[1]);
        double lon = Double.parseDouble(args[2]);
        double km = Double.parseDouble(args[3]);
        long seed = args.length >= 5 ? Long.parseLong(args[4]) : 1;

        OsmGraphImporter.Result imported = OsmGraphImporter.importFile(Path.of(args[0]));
        GraphImportDemo.printStats(imported.stats());
        CsrGraph graph = imported.graph();

        long t0 = System.nanoTime();
        SpatialIndex index = SpatialIndex.build(graph, 250);
        System.out.printf("Indeks przestrzenny: %d ms%n", (System.nanoTime() - t0) / 1_000_000);

        EdgeCost cost = GraphImportDemo.demoBikeCost(graph);
        LoopGenerator generator = new LoopGenerator(graph, index, LoopGenerator.Options.defaults());
        int start = index.nearest(lat, lon, 500, v -> hasUsableEdge(graph, cost, v));
        if (start < 0) {
            System.out.println("Brak drogi w promieniu 500 m od punktu startu");
            return;
        }

        List<String> features = new ArrayList<>();
        long total = System.nanoTime();
        for (int i = 0; i < 3; i++) {
            long t = System.nanoTime();
            Optional<LoopGenerator.LoopResult> loop = generator.generate(start, km * 1000, cost, seed + i);
            long ms = (System.nanoTime() - t) / 1_000_000;
            if (loop.isEmpty()) {
                System.out.printf("Wariant %d: nie udało się wygenerować pętli (%d ms)%n", i + 1, ms);
                continue;
            }
            LoopGenerator.LoopResult r = loop.get();
            System.out.printf(Locale.ROOT,
                    "Wariant %d: %.2f km (odchyłka %+.1f%%), powtórzone odcinki %.1f%%, prób %d, %d ms%n",
                    i + 1, r.lengthM() / 1000, r.deviation() * 100, r.reusedShare() * 100, r.attempts(), ms);
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("variant", i + 1);
            props.put("lengthKm", Math.round(r.lengthM()) / 1000.0);
            props.put("stroke", COLORS[i]);
            props.put("stroke-width", 4);
            features.add(GeoJson.feature(graph, r.path(), props));
        }
        System.out.printf("Razem 3 warianty: %d ms%n", (System.nanoTime() - total) / 1_000_000);

        if (args.length == 6 && !features.isEmpty()) {
            Files.writeString(Path.of(args[5]), GeoJson.featureCollection(features));
            System.out.println("Zapisano " + args[5] + " (podgląd: geojson.io)");
        }
    }

    private static boolean hasUsableEdge(CsrGraph graph, EdgeCost cost, int node) {
        for (int e = graph.firstEdge(node); e < graph.endEdge(node); e++) {
            if (!EdgeCost.isForbidden(cost.cost(graph, e))) {
                return true;
            }
        }
        return false;
    }
}
