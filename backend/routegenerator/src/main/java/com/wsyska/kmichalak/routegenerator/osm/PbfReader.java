package com.wsyska.kmichalak.routegenerator.osm;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public final class PbfReader {

    private static final int MAX_HEADER_SIZE = 64 * 1024;
    private static final int MAX_BLOB_SIZE = 32 * 1024 * 1024;
    private static final Set<String> SUPPORTED_FEATURES =
            Set.of("OsmSchema-V0.6", "DenseNodes", "HistoricalInformation");

    private PbfReader() {
    }

    public static void read(Path file, OsmHandler handler) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            read(in, handler);
        }
    }

    public static void read(InputStream input, OsmHandler handler) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(input, 1 << 16));
        Inflater inflater = new Inflater();
        try {
            while (true) {
                int headerSize;
                try {
                    headerSize = in.readInt();
                } catch (EOFException end) {
                    return;
                }
                if (headerSize < 0 || headerSize > MAX_HEADER_SIZE) {
                    throw new OsmFormatException("Nieprawidłowy rozmiar nagłówka bloku: " + headerSize);
                }
                byte[] headerBytes = in.readNBytes(headerSize);
                String type = null;
                int dataSize = -1;
                ProtoReader header = new ProtoReader(headerBytes);
                while (header.next()) {
                    switch (header.field()) {
                        case 1 -> type = header.readString();
                        case 3 -> dataSize = (int) header.readVarint();
                        default -> header.skip();
                    }
                }
                if (dataSize < 0 || dataSize > MAX_BLOB_SIZE) {
                    throw new OsmFormatException("Nieprawidłowy rozmiar bloku: " + dataSize);
                }
                byte[] blob = in.readNBytes(dataSize);
                if (blob.length != dataSize) {
                    throw new OsmFormatException("Plik urwany w środku bloku");
                }
                byte[] data = unpackBlob(blob, inflater);
                if ("OSMHeader".equals(type)) {
                    checkHeader(data);
                } else if ("OSMData".equals(type)) {
                    readPrimitiveBlock(data, handler);
                }
                // nieznane typy bloków specyfikacja każe pomijać
            }
        } finally {
            inflater.end();
        }
    }

    private static byte[] unpackBlob(byte[] blob, Inflater inflater) {
        ProtoReader r = new ProtoReader(blob);
        byte[] raw = null;
        byte[] zlib = null;
        int rawSize = -1;
        while (r.next()) {
            switch (r.field()) {
                case 1 -> raw = r.readBytes();
                case 2 -> rawSize = (int) r.readVarint();
                case 3 -> zlib = r.readBytes();
                case 4, 6, 7 -> throw new OsmFormatException(
                        "Nieobsługiwana kompresja (lzma/lz4/zstd) – użyj pliku z Geofabrik (zlib)");
                default -> r.skip();
            }
        }
        if (raw != null) {
            return raw;
        }
        if (zlib == null || rawSize < 0 || rawSize > MAX_BLOB_SIZE) {
            throw new OsmFormatException("Pusty lub nieprawidłowy blok danych");
        }
        byte[] out = new byte[rawSize];
        inflater.reset();
        inflater.setInput(zlib);
        try {
            int n = 0;
            while (n < rawSize && !inflater.finished()) {
                int got = inflater.inflate(out, n, rawSize - n);
                if (got == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                n += got;
            }
            if (n != rawSize) {
                throw new OsmFormatException("Rozpakowano " + n + " zamiast " + rawSize + " bajtów");
            }
        } catch (DataFormatException e) {
            throw new OsmFormatException("Błąd dekompresji zlib", e);
        }
        return out;
    }

    private static void checkHeader(byte[] data) {
        ProtoReader r = new ProtoReader(data);
        while (r.next()) {
            if (r.field() == 4) {
                String feature = r.readString();
                if (!SUPPORTED_FEATURES.contains(feature)) {
                    throw new OsmFormatException("Nieobsługiwana wymagana funkcja PBF: " + feature);
                }
            } else {
                r.skip();
            }
        }
    }

    private static void readPrimitiveBlock(byte[] data, OsmHandler handler) {
        String[] strings = new String[0];
        long granularity = 100;
        long latOffset = 0;
        long lonOffset = 0;
        ProtoReader r = new ProtoReader(data);
        while (r.next()) {
            switch (r.field()) {
                case 1 -> strings = readStringTable(r.readMessage());
                case 17 -> granularity = r.readVarint();
                case 19 -> latOffset = r.readVarint();
                case 20 -> lonOffset = r.readVarint();
                default -> r.skip();
            }
        }
        Block block = new Block(strings, granularity, latOffset, lonOffset);

        r = new ProtoReader(data);
        while (r.next()) {
            if (r.field() == 2) {
                readGroup(r.readMessage(), block, handler);
            } else {
                r.skip();
            }
        }
    }

    private static String[] readStringTable(ProtoReader r) {
        java.util.ArrayList<String> list = new java.util.ArrayList<>();
        while (r.next()) {
            if (r.field() == 1) {
                list.add(r.readString());
            } else {
                r.skip();
            }
        }
        return list.toArray(String[]::new);
    }

    private static void readGroup(ProtoReader r, Block block, OsmHandler handler) {
        while (r.next()) {
            switch (r.field()) {
                case 1 -> {
                    if (handler.wantsNodes()) {
                        readNode(r.readMessage(), block, handler);
                    } else {
                        r.skip();
                    }
                }
                case 2 -> {
                    if (handler.wantsNodes()) {
                        readDenseNodes(r.readMessage(), block, handler);
                    } else {
                        r.skip();
                    }
                }
                case 3 -> {
                    if (handler.wantsWays()) {
                        readWay(r.readMessage(), block, handler);
                    } else {
                        r.skip();
                    }
                }
                default -> r.skip();
            }
        }
    }

    private static void readNode(ProtoReader r, Block block, OsmHandler handler) {
        long id = 0;
        long lat = 0;
        long lon = 0;
        while (r.next()) {
            switch (r.field()) {
                case 1 -> id = r.readSInt64();
                case 8 -> lat = r.readSInt64();
                case 9 -> lon = r.readSInt64();
                default -> r.skip();
            }
        }
        handler.node(id, block.lat(lat), block.lon(lon));
    }

    private static void readDenseNodes(ProtoReader r, Block block, OsmHandler handler) {
        long[] ids = new long[0];
        long[] lats = new long[0];
        long[] lons = new long[0];
        while (r.next()) {
            switch (r.field()) {
                case 1 -> ids = r.readPackedVarints(true);
                case 8 -> lats = r.readPackedVarints(true);
                case 9 -> lons = r.readPackedVarints(true);
                default -> r.skip();
            }
        }
        if (lats.length != ids.length || lons.length != ids.length) {
            throw new OsmFormatException("Niespójne DenseNodes");
        }
        long id = 0;
        long lat = 0;
        long lon = 0;
        for (int i = 0; i < ids.length; i++) {
            id += ids[i];
            lat += lats[i];
            lon += lons[i];
            handler.node(id, block.lat(lat), block.lon(lon));
        }
    }

    private static void readWay(ProtoReader r, Block block, OsmHandler handler) {
        long id = 0;
        long[] keys = new long[0];
        long[] vals = new long[0];
        long[] refs = new long[0];
        while (r.next()) {
            switch (r.field()) {
                case 1 -> id = r.readVarint();
                case 2 -> keys = r.readPackedVarints(false);
                case 3 -> vals = r.readPackedVarints(false);
                case 8 -> refs = r.readPackedVarints(true);
                default -> r.skip();
            }
        }
        if (keys.length != vals.length) {
            throw new OsmFormatException("Niespójne tagi drogi " + id);
        }
        Map<String, String> tags = HashMap.newHashMap(keys.length);
        for (int i = 0; i < keys.length; i++) {
            tags.put(block.string(keys[i]), block.string(vals[i]));
        }
        for (int i = 1; i < refs.length; i++) {
            refs[i] += refs[i - 1];
        }
        handler.way(id, refs, tags);
    }

    private record Block(String[] strings, long granularity, long latOffset, long lonOffset) {

        double lat(long value) {
            return 1e-9 * (latOffset + granularity * value);
        }

        double lon(long value) {
            return 1e-9 * (lonOffset + granularity * value);
        }

        String string(long index) {
            if (index < 0 || index >= strings.length) {
                throw new OsmFormatException("Indeks poza tablicą napisów: " + index);
            }
            return strings[(int) index];
        }
    }
}
