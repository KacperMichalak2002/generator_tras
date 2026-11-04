package com.wsyska.kmichalak.routegenerator.osm;

import java.util.Arrays;


final class LongIntHashMap {

    private static final long EMPTY = Long.MIN_VALUE;

    private long[] keys;
    private int[] values;
    private int mask;
    private int size;

    LongIntHashMap(int expectedSize) {
        int capacity = Integer.highestOneBit(Math.max(16, expectedSize * 2 - 1)) << 1;
        allocate(capacity);
    }

    int get(long key, int missing) {
        checkKey(key);
        for (int i = slot(key); ; i = (i + 1) & mask) {
            long k = keys[i];
            if (k == key) {
                return values[i];
            }
            if (k == EMPTY) {
                return missing;
            }
        }
    }

    void put(long key, int value) {
        checkKey(key);
        if (size * 4 >= keys.length * 3) {
            rehash();
        }
        for (int i = slot(key); ; i = (i + 1) & mask) {
            long k = keys[i];
            if (k == key) {
                values[i] = value;
                return;
            }
            if (k == EMPTY) {
                keys[i] = key;
                values[i] = value;
                size++;
                return;
            }
        }
    }

    int size() {
        return size;
    }

    long estimatedBytes() {
        return 12L * keys.length;
    }

    private int slot(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 32)) & mask;
    }

    private void rehash() {
        long[] oldKeys = keys;
        int[] oldValues = values;
        allocate(oldKeys.length * 2);
        size = 0;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldKeys[i] != EMPTY) {
                put(oldKeys[i], oldValues[i]);
            }
        }
    }

    private void allocate(int capacity) {
        keys = new long[capacity];
        values = new int[capacity];
        Arrays.fill(keys, EMPTY);
        mask = capacity - 1;
    }

    private static void checkKey(long key) {
        if (key == EMPTY) {
            throw new IllegalArgumentException("Niedozwolony klucz: " + key);
        }
    }
}
