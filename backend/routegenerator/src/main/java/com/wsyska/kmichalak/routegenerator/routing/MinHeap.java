package com.wsyska.kmichalak.routegenerator.routing;

import java.util.Arrays;

// Kopiec binarny (najmniejszy klucz na górze) na dwóch tablicach, bez obiektu na każdy wpis.
// Ten sam węzeł może być w kopcu kilka razy; nieaktualne wpisy pomija Dijkstra.
final class MinHeap {

    private double[] keys;
    private int[] values;
    private int size;

    MinHeap(int capacity) {
        keys = new double[Math.max(16, capacity)];
        values = new int[keys.length];
    }

    boolean isEmpty() {
        return size == 0;
    }

    void push(double key, int value) {
        if (size == keys.length) {
            keys = Arrays.copyOf(keys, size * 2);
            values = Arrays.copyOf(values, size * 2);
        }
        int i = size++;
        while (i > 0) {
            int parent = (i - 1) >>> 1;
            if (keys[parent] <= key) {
                break;
            }
            keys[i] = keys[parent];
            values[i] = values[parent];
            i = parent;
        }
        keys[i] = key;
        values[i] = value;
    }

    double peekKey() {
        return keys[0];
    }

    int pop() {
        int top = values[0];
        size--;
        if (size > 0) {
            double key = keys[size];
            int value = values[size];
            int i = 0;
            while (true) {
                int child = 2 * i + 1;
                if (child >= size) {
                    break;
                }
                if (child + 1 < size && keys[child + 1] < keys[child]) {
                    child++;
                }
                if (key <= keys[child]) {
                    break;
                }
                keys[i] = keys[child];
                values[i] = values[child];
                i = child;
            }
            keys[i] = key;
            values[i] = value;
        }
        return top;
    }
}
