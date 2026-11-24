package com.wsyska.kmichalak.routegenerator.routing;

import java.util.Optional;

public interface Router {

    Optional<PathResult> route(int source, int target, EdgeCost cost, SearchBounds bounds);

    default Optional<PathResult> route(int source, int target, EdgeCost cost) {
        return route(source, target, cost, null);
    }
}
