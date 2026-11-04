package com.wsyska.kmichalak.routegenerator.osm;

import java.util.Map;
import java.util.Set;


public enum Oneway {
    NO, FORWARD, BACKWARD;

    private static final Set<String> IMPLIED_ONEWAY_HIGHWAYS = Set.of("motorway", "motorway_link");
    private static final Set<String> ONEWAY_JUNCTIONS = Set.of("roundabout", "circular");

    public static Oneway of(Map<String, String> tags) {
        return of(tags, null);
    }

    public static Oneway of(Map<String, String> tags, String vehicle) {
        if (vehicle != null) {
            Oneway specific = parse(tags.get("oneway:" + vehicle));
            if (specific != null) {
                return specific;
            }
            if ("bicycle".equals(vehicle)) {
                String cycleway = tags.get("cycleway");
                if (cycleway != null && cycleway.startsWith("opposite")) {
                    return NO;
                }
            }
        }
        Oneway explicit = parse(tags.get("oneway"));
        if (explicit != null) {
            return explicit;
        }
        if (containsTag(ONEWAY_JUNCTIONS, tags.get("junction"))
                || containsTag(IMPLIED_ONEWAY_HIGHWAYS, tags.get("highway"))) {
            return FORWARD;
        }
        return NO;
    }

    private static boolean containsTag(Set<String> values, String value) {
        return value != null && values.contains(value);
    }

    public boolean allows(boolean reverse) {
        return switch (this) {
            case NO -> true;
            case FORWARD -> !reverse;
            case BACKWARD -> reverse;
        };
    }

    private static Oneway parse(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "yes", "true", "1" -> FORWARD;
            case "-1", "reverse" -> BACKWARD;
            case "no", "false", "0" -> NO;
            default -> null;
        };
    }
}
