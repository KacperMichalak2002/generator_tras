package com.wsyska.kmichalak.routegenerator.graph;

import java.util.Map;

public interface Graph {

    int nodeCount();

    int edgeCount();

    double lat(int node);

    double lon(int node);

    int firstEdge(int node);

    int endEdge(int node);

    int edgeSource(int edge);

    int edgeTarget(int edge);

    double edgeLength(int edge);

    int edgeAttributes(int edge);

    int attributeCount();

    Map<String, String> attributeTags(int attributesId);

    boolean isReverse(int edge);

    long edgeWayId(int edge);

    int edgePointCount(int edge);

    double edgePointLat(int edge, int index);

    double edgePointLon(int edge, int index);

    default Map<String, String> edgeTags(int edge) {
        return attributeTags(edgeAttributes(edge));
    }

    default String edgeTag(int edge, String key) {
        return edgeTags(edge).get(key);
    }
}
