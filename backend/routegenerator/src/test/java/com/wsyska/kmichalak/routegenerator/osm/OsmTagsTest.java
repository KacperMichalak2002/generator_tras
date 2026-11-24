package com.wsyska.kmichalak.routegenerator.osm;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsmTagsTest {

    @Test
    void onewayExplicitValues() {
        assertEquals(Oneway.FORWARD, Oneway.of(Map.of("highway", "residential", "oneway", "yes")));
        assertEquals(Oneway.BACKWARD, Oneway.of(Map.of("highway", "residential", "oneway", "-1")));
        assertEquals(Oneway.NO, Oneway.of(Map.of("highway", "residential", "oneway", "no")));
        assertEquals(Oneway.NO, Oneway.of(Map.of("highway", "residential")));
    }

    @Test
    void onewayImpliedByRoundaboutAndMotorway() {
        assertEquals(Oneway.FORWARD, Oneway.of(Map.of("highway", "primary", "junction", "roundabout")));
        assertEquals(Oneway.FORWARD, Oneway.of(Map.of("highway", "motorway")));
        assertEquals(Oneway.NO, Oneway.of(Map.of("highway", "motorway", "oneway", "no")));
    }

    @Test
    void bicycleExceptions() {
        Map<String, String> contraflow = Map.of("highway", "residential", "oneway", "yes", "oneway:bicycle", "no");
        assertEquals(Oneway.FORWARD, Oneway.of(contraflow));
        assertEquals(Oneway.NO, Oneway.of(contraflow, "bicycle"));
        assertEquals(Oneway.NO, Oneway.of(
                Map.of("highway", "residential", "oneway", "yes", "cycleway", "opposite_lane"), "bicycle"));
        assertEquals(Oneway.FORWARD, Oneway.of(
                Map.of("highway", "residential", "oneway", "yes", "cycleway", "opposite_lane"), "motorcycle"));
    }

    @Test
    void allowsDependsOnEdgeDirection() {
        assertTrue(Oneway.FORWARD.allows(false));
        assertFalse(Oneway.FORWARD.allows(true));
        assertFalse(Oneway.BACKWARD.allows(false));
        assertTrue(Oneway.BACKWARD.allows(true));
        assertTrue(Oneway.NO.allows(true));
    }

    @Test
    void roadFilterAcceptsOnlyRoutableHighways() {
        RoadFilter filter = RoadFilter.defaults();

        assertTrue(filter.accepts(Map.of("highway", "track")));
        assertTrue(filter.accepts(Map.of("highway", "path")));
        assertTrue(filter.accepts(Map.of("highway", "motorway")));
        assertFalse(filter.accepts(Map.of("highway", "proposed")));
        assertFalse(filter.accepts(Map.of("highway", "construction")));
        assertFalse(filter.accepts(Map.of("highway", "pedestrian", "area", "yes")));
        assertFalse(filter.accepts(Map.of("building", "yes")));
    }

    @Test
    void roadFilterKeepsOnlyRoutingTags() {
        Map<String, String> tags = Map.of("highway", "track", "surface", "gravel", "name", "Leśna",
                "source", "survey", "bicycle", "yes");

        assertEquals(Map.of("highway", "track", "surface", "gravel", "bicycle", "yes"),
                RoadFilter.defaults().relevantTags(tags));
    }

    @Test
    void longIntHashMapGrowsAndFindsAllKeys() {
        LongIntHashMap map = new LongIntHashMap(4);
        for (int i = 0; i < 100_000; i++) {
            map.put(12_000_000_000L + i * 7919L, i);
        }

        assertEquals(100_000, map.size());
        for (int i = 0; i < 100_000; i++) {
            assertEquals(i, map.get(12_000_000_000L + i * 7919L, -1));
        }
        assertEquals(-1, map.get(5, -1));
        map.put(12_000_000_000L, 42);
        assertEquals(42, map.get(12_000_000_000L, -1));
        assertEquals(100_000, map.size());
    }
}
