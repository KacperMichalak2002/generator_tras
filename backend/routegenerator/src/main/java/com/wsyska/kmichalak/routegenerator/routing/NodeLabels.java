package com.wsyska.kmichalak.routegenerator.routing;

import java.util.Arrays;

// Odległość i krawędź poprzednika tylko dla węzłów, które wyszukiwanie odwiedziło.
// Tablica mieszająca (adresowanie otwarte): pamięć rośnie z liczbą odwiedzonych węzłów,
// a nie z rozmiarem grafu, więc zapytanie w Kielcach nie płaci za resztę Polski.
final class NodeLabels {

    private static final int EMPTY = -1;

    private int[] nodes;
    private double[] dist;
    private int[] prevEdge;
    private int mask;
    private int size;

    NodeLabels(int expectedSize) {
        allocate(Integer.highestOneBit(Math.max(16, expectedSize * 2 - 1)) << 1);
    }

    // Pozycja węzła albo -1, gdy nie był jeszcze odwiedzony.
    int find(int node) {
        for (int i = hash(node); ; i = (i + 1) & mask) {
            int n = nodes[i];
            if (n == node) {
                return i;
            }
            if (n == EMPTY) {
                return -1;
            }
        }
    }

    // Pozycja węzła; nowy węzeł dostaje odległość +∞. Uwaga: dodanie może przebudować tablicę,
    // więc pozycje zwrócone wcześniej przestają być ważne.
    int findOrAdd(int node) {
        if (size * 4 >= nodes.length * 3) {
            rehash();
        }
        for (int i = hash(node); ; i = (i + 1) & mask) {
            int n = nodes[i];
            if (n == node) {
                return i;
            }
            if (n == EMPTY) {
                nodes[i] = node;
                dist[i] = Double.POSITIVE_INFINITY;
                prevEdge[i] = -1;
                size++;
                return i;
            }
        }
    }

    double dist(int slot) {
        return dist[slot];
    }

    int prevEdge(int slot) {
        return prevEdge[slot];
    }

    void set(int slot, double distance, int edge) {
        dist[slot] = distance;
        prevEdge[slot] = edge;
    }

    int size() {
        return size;
    }

    private int hash(int node) {
        return (node * 0x9E3779B9) >>> 7 & mask;
    }

    private void rehash() {
        int[] oldNodes = nodes;
        double[] oldDist = dist;
        int[] oldPrev = prevEdge;
        allocate(oldNodes.length * 2);
        for (int i = 0; i < oldNodes.length; i++) {
            int node = oldNodes[i];
            if (node != EMPTY) {
                int j = hash(node);
                while (nodes[j] != EMPTY) {
                    j = (j + 1) & mask;
                }
                nodes[j] = node;
                dist[j] = oldDist[i];
                prevEdge[j] = oldPrev[i];
            }
        }
    }

    private void allocate(int capacity) {
        nodes = new int[capacity];
        dist = new double[capacity];
        prevEdge = new int[capacity];
        Arrays.fill(nodes, EMPTY);
        mask = capacity - 1;
    }
}
