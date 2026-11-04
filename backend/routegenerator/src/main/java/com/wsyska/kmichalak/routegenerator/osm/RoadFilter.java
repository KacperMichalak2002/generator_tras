package com.wsyska.kmichalak.routegenerator.osm;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public final class RoadFilter {

    public static final Set<String> ROUTABLE_HIGHWAYS = Set.of(
            "motorway", "motorway_link", "trunk", "trunk_link",
            "primary", "primary_link", "secondary", "secondary_link",
            "tertiary", "tertiary_link", "unclassified", "residential",
            "living_street", "service", "road", "track",
            "path", "cycleway", "footway", "bridleway", "pedestrian", "steps");

    public static final Set<String> KEPT_TAG_KEYS = Set.of(
            "highway", "junction", "oneway", "oneway:bicycle",
            "access", "vehicle", "bicycle", "motor_vehicle", "motorcycle", "motorcar",
            "surface", "tracktype", "smoothness", "mtb:scale",
            "maxspeed", "service", "cycleway", "ford", "motorroad");

    private final Set<String> highways;
    private final Set<String> keptKeys;

    public RoadFilter(Set<String> highways, Set<String> keptKeys) {
        this.highways = Set.copyOf(highways);
        this.keptKeys = Set.copyOf(keptKeys);
    }

    public static RoadFilter defaults() {
        return new RoadFilter(ROUTABLE_HIGHWAYS, KEPT_TAG_KEYS);
    }

    public boolean accepts(Map<String, String> tags) {
        String highway = tags.get("highway");
        return highway != null && highways.contains(highway) && !"yes".equals(tags.get("area"));
    }

    public Map<String, String> relevantTags(Map<String, String> tags) {
        Map<String, String> result = HashMap.newHashMap(keptKeys.size());
        for (Map.Entry<String, String> tag : tags.entrySet()) {
            if (keptKeys.contains(tag.getKey())) {
                result.put(tag.getKey(), tag.getValue());
            }
        }
        return result;
    }
}
