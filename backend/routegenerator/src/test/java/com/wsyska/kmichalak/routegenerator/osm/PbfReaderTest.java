package com.wsyska.kmichalak.routegenerator.osm;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PbfReaderTest {

    @Test
    void readsNodesWithCoordinates() throws IOException {
        List<double[]> nodes = new ArrayList<>();
        read(new OsmHandler() {
            @Override
            public void node(long id, double lat, double lon) {
                nodes.add(new double[] {id, lat, lon});
            }
        });

        assertEquals(5, nodes.size());
        assertEquals(1, nodes.get(0)[0]);
        assertEquals(50.8661, nodes.get(0)[1], 1e-7);
        assertEquals(20.6286, nodes.get(0)[2], 1e-7);
        assertEquals(5, nodes.get(4)[0]);
        assertEquals(50.87, nodes.get(4)[1], 1e-7);
    }

    @Test
    void readsWaysWithRefsAndTags() throws IOException {
        List<Long> ids = new ArrayList<>();
        List<long[]> refs = new ArrayList<>();
        List<Map<String, String>> tags = new ArrayList<>();
        read(new OsmHandler() {
            @Override
            public void way(long id, long[] nodeRefs, Map<String, String> t) {
                ids.add(id);
                refs.add(nodeRefs);
                tags.add(t);
            }
        });

        assertEquals(List.of(100L, 101L, 102L), ids);
        assertArrayEquals(new long[] {1, 2, 3}, refs.get(0));
        assertArrayEquals(new long[] {4, 5, 4}, refs.get(2));
        assertEquals(Map.of("highway", "residential", "name", "Testowa"), tags.get(0));
        assertEquals("gravel", tags.get(1).get("surface"));
    }

    @Test
    void statsCountHighways() throws IOException {
        PbfStats stats = new PbfStats();
        read(stats);

        assertEquals(5, stats.nodes());
        assertEquals(3, stats.ways());
        assertEquals(2, stats.highways());
        assertEquals(5, stats.highwayNodeRefs());
    }

    @Test
    void canSkipNodes() throws IOException {
        int[] counts = new int[2];
        read(new OsmHandler() {
            @Override
            public void node(long id, double lat, double lon) {
                counts[0]++;
            }

            @Override
            public void way(long id, long[] nodeRefs, Map<String, String> tags) {
                counts[1]++;
            }

            @Override
            public boolean wantsNodes() {
                return false;
            }
        });

        assertEquals(0, counts[0]);
        assertEquals(3, counts[1]);
    }

    @Test
    void rejectsGarbage() {
        byte[] garbage = {0x7f, 0x00, 0x00, 0x00, 1, 2, 3};
        assertThrows(OsmFormatException.class,
                () -> PbfReader.read(new ByteArrayInputStream(garbage), new OsmHandler() { }));
    }

    @Test
    void emptyInputIsEmptyFile() throws IOException {
        PbfStats stats = new PbfStats();
        PbfReader.read(new ByteArrayInputStream(new byte[0]), stats);
        assertEquals(0, stats.nodes());
    }

    private static void read(OsmHandler handler) throws IOException {
        try (InputStream in = PbfReaderTest.class.getResourceAsStream("/osm/mini.osm.pbf")) {
            PbfReader.read(in, handler);
        }
    }
}
