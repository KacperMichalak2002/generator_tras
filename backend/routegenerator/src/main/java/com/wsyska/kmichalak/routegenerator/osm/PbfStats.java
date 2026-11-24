package com.wsyska.kmichalak.routegenerator.osm;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;


public final class PbfStats implements OsmHandler {

    private long nodes;
    private long ways;
    private long highways;
    private long highwayNodeRefs;

    public static PbfStats of(Path file) throws IOException {
        PbfStats stats = new PbfStats();
        PbfReader.read(file, stats);
        return stats;
    }

    @Override
    public void node(long id, double lat, double lon) {
        nodes++;
    }

    @Override
    public void way(long id, long[] nodeRefs, Map<String, String> tags) {
        ways++;
        if (tags.containsKey("highway")) {
            highways++;
            highwayNodeRefs += nodeRefs.length;
        }
    }

    public long nodes() {
        return nodes;
    }

    public long ways() {
        return ways;
    }

    public long highways() {
        return highways;
    }

    public long highwayNodeRefs() {
        return highwayNodeRefs;
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Użycie: PbfStats <plik.osm.pbf>");
            System.exit(1);
        }
        long start = System.nanoTime();
        PbfStats s = of(Path.of(args[0]));
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("Węzły: %,d%nDrogi: %,d%nDrogi z tagiem highway: %,d (odwołań do węzłów: %,d)%nCzas: %,d ms%n",
                s.nodes, s.ways, s.highways, s.highwayNodeRefs, ms);
    }
}
