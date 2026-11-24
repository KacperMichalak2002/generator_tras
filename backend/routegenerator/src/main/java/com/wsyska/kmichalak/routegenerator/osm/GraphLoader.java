package com.wsyska.kmichalak.routegenerator.osm;

import com.wsyska.kmichalak.routegenerator.graph.CsrGraph;
import com.wsyska.kmichalak.routegenerator.graph.GraphFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

// Wczytuje graf z pliku .graph obok pliku PBF, a gdy go nie ma albo jest nieaktualny,
// importuje PBF i zapisuje graf na następny raz. Pierwsze uruchomienie trwa tyle co import,
// kolejne tyle co odczyt pliku z dysku.
public final class GraphLoader {

    public record Loaded(CsrGraph graph, boolean fromGraphFile, Path graphFile, long millis,
                         OsmGraphImporter.Stats importStats, String note) {
    }

    private GraphLoader() {
    }

    public static Loaded load(Path pbf) throws IOException {
        return load(pbf, defaultGraphFile(pbf), OsmGraphImporter.Options.defaults());
    }

    // np. data/poland-latest.osm.pbf → data/poland-latest.osm.pbf.graph
    public static Path defaultGraphFile(Path pbf) {
        return pbf.resolveSibling(pbf.getFileName() + ".graph");
    }

    public static Loaded load(Path pbf, Path graphFile, OsmGraphImporter.Options options) throws IOException {
        long start = System.nanoTime();
        boolean pbfExists = Files.exists(pbf);
        boolean graphExists = Files.exists(graphFile);

        // Sam plik grafu bez PBF (np. w kontenerze Dockera) też wystarczy.
        if (!pbfExists) {
            if (!graphExists) {
                throw new IOException("Brak pliku mapy " + pbf + " i pliku grafu " + graphFile);
            }
            CsrGraph graph = GraphFile.read(graphFile);
            return new Loaded(graph, true, graphFile, ms(start), null, "brak pliku PBF, użyto samego pliku grafu");
        }

        GraphFile.Meta expected = new GraphFile.Meta(Files.size(pbf),
                Files.getLastModifiedTime(pbf).toMillis(), settingsHash(options));
        String note;
        if (!graphExists) {
            note = "brak pliku grafu";
        } else {
            try {
                if (GraphFile.readMeta(graphFile).equals(expected)) {
                    CsrGraph graph = GraphFile.read(graphFile);
                    return new Loaded(graph, true, graphFile, ms(start), null, "plik grafu aktualny");
                }
                note = "plik PBF albo ustawienia importu zmieniły się od zapisu grafu";
            } catch (IOException e) {
                note = "nie udało się odczytać pliku grafu (" + e.getMessage() + ")";
            }
        }

        OsmGraphImporter.Result imported = OsmGraphImporter.importGraph(OsmGraphImporter.PbfSource.of(pbf), options);
        try {
            GraphFile.write(imported.graph(), expected, graphFile);
            note += ", zaimportowano i zapisano " + graphFile.getFileName();
        } catch (IOException e) {
            // Graf jest gotowy w pamięci, więc brak zapisu nie przerywa działania aplikacji.
            note += ", zaimportowano, ale zapis grafu się nie udał (" + e.getMessage() + ")";
        }
        return new Loaded(imported.graph(), false, graphFile, ms(start), imported.stats(), note);
    }

    static long settingsHash(OsmGraphImporter.Options options) {
        CRC32 crc = new CRC32();
        String settings = options.filter().fingerprint() + "|" + options.minComponentNodes();
        crc.update(settings.getBytes(StandardCharsets.UTF_8));
        return crc.getValue();
    }

    private static long ms(long fromNanos) {
        return (System.nanoTime() - fromNanos) / 1_000_000;
    }
}
