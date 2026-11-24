package com.wsyska.kmichalak.routegenerator.routing;

import java.util.Arrays;

// Zbiór liczb int (np. id segmentów trasy). BitSet o rozmiarze całego grafu kosztowałby
// kilka MB na każdą próbę pętli przy mapie Polski, a trasa ma tylko kilka tysięcy segmentów.
final class IntHashSet {

    private static final int EMPTY = -1;

    private int[] values;
    private int mask;
    private int size;

    IntHashSet(int expectedSize) {
        allocate(Integer.highestOneBit(Math.max(16, expectedSize * 2 - 1)) << 1);
    }

    boolean contains(int value) {
        for (int i = hash(value); ; i = (i + 1) & mask) {
            int v = values[i];
            if (v == value) {
                return true;
            }
            if (v == EMPTY) {
                return false;
            }
        }
    }

    // Zwraca true, gdy wartości wcześniej nie było.
    boolean add(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Tylko wartości nieujemne: " + value);
        }
        if (size * 4 >= values.length * 3) {
            int[] old = values;
            allocate(old.length * 2);
            size = 0;
            for (int v : old) {
                if (v != EMPTY) {
                    add(v);
                }
            }
        }
        for (int i = hash(value); ; i = (i + 1) & mask) {
            int v = values[i];
            if (v == value) {
                return false;
            }
            if (v == EMPTY) {
                values[i] = value;
                size++;
                return true;
            }
        }
    }

    int size() {
        return size;
    }

    private int hash(int value) {
        return (value * 0x9E3779B9) >>> 7 & mask;
    }

    private void allocate(int capacity) {
        values = new int[capacity];
        Arrays.fill(values, EMPTY);
        mask = capacity - 1;
    }
}
