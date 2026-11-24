package com.wsyska.kmichalak.routegenerator.osm;

import java.util.Map;

public interface OsmHandler {

    default void node(long id, double lat, double lon) {
    }

    default void way(long id, long[] nodeRefs, Map<String, String> tags) {
    }

    default boolean wantsWays() {
        return true;
    }

    default boolean wantsNodes() {
        return true;
    }
}
