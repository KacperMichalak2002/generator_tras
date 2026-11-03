package com.wsyska.kmichalak.routegenerator.graph;


public interface Graph {

    int nodeCount();

    int edgeCount();

    double lat(int node);

    double lon(int node);

    int firstEdge(int node);

    int endEdge(int node);

    int edgeTarget(int edge);

    double edgeLength(int edge);
}
