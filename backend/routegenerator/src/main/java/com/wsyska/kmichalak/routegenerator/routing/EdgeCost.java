package com.wsyska.kmichalak.routegenerator.routing;

import com.wsyska.kmichalak.routegenerator.graph.Graph;


@FunctionalInterface
public interface EdgeCost {

    double cost(Graph graph, int edge);

    EdgeCost DISTANCE = Graph::edgeLength;

    static boolean isForbidden(double cost) {
        return cost == Double.POSITIVE_INFINITY;
    }
}
