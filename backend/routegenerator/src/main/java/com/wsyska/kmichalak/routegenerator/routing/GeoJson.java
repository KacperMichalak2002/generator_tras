package com.wsyska.kmichalak.routegenerator.routing;

import com.wsyska.kmichalak.routegenerator.graph.Graph;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GeoJson {

    private GeoJson() {
    }

    public static String feature(Graph graph, PathResult path, Map<String, ?> properties) {
        StringBuilder sb = new StringBuilder("{\"type\":\"Feature\",\"properties\":{");
        boolean first = true;
        for (Map.Entry<String, ?> p : properties.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(p.getKey())).append("\":");
            Object value = p.getValue();
            if (value instanceof Number number) {
                sb.append(String.format(Locale.ROOT, "%s", number));
            } else {
                sb.append('"').append(escape(String.valueOf(value))).append('"');
            }
        }
        sb.append("},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[");
        int[] edges = path.edges();
        for (int k = 0; k < edges.length; k++) {
            int e = edges[k];
            // Pierwszy punkt kolejnej krawędzi to ostatni punkt poprzedniej, więc go pomijamy.
            for (int i = k == 0 ? 0 : 1; i < graph.edgePointCount(e); i++) {
                if (k > 0 || i > 0) {
                    sb.append(',');
                }
                sb.append(String.format(Locale.ROOT, "[%.7f,%.7f]", graph.edgePointLon(e, i), graph.edgePointLat(e, i)));
            }
        }
        return sb.append("]}}").toString();
    }

    public static String featureCollection(List<String> features) {
        return "{\"type\":\"FeatureCollection\",\"features\":[" + String.join(",", features) + "]}";
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
