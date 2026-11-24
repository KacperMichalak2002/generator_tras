package com.wsyska.kmichalak.routegenerator.graph;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Zapis gotowego grafu do pliku binarnego i odczyt. Graf to kilkanaście płaskich tablic, więc
// odczyt to praktycznie kopiowanie bajtów z dysku do tablic, bez żadnych obliczeń. Dla Polski
// to sekundy zamiast minuty importu PBF.
//
// Format (little-endian):
//   magic, wersja formatu, Meta (opis pliku źródłowego i ustawień importu),
//   tablice grafu (każda poprzedzona długością), na końcu zestawy tagów.
public final class GraphFile {

    // Opis tego, z czego graf powstał. Gdy plik PBF albo ustawienia importu się zmienią,
    // graf w pliku jest nieaktualny i trzeba go zbudować od nowa.
    public record Meta(long sourceSize, long sourceModifiedMs, long settingsHash) {
    }

    public static final class FormatException extends IOException {
        private static final long serialVersionUID = 1L;

        public FormatException(String message) {
            super(message);
        }
    }

    private static final long MAGIC = 0x3148504152474752L; // w pliku bajty układają się w napis "RGGRAPH1"
    private static final int VERSION = 1;
    private static final int BUFFER_BYTES = 8 * 1024 * 1024;

    private GraphFile() {
    }

    // Zapis do pliku tymczasowego i podmiana na końcu, żeby przerwany zapis
    // nie zostawił połowy pliku, który następnym razem zostałby wczytany.
    public static void write(CsrGraph g, Meta meta, Path file) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
        try {
            try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buf = ByteBuffer.allocateDirect(BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
                buf.putLong(MAGIC).putInt(VERSION);
                buf.putLong(meta.sourceSize()).putLong(meta.sourceModifiedMs()).putLong(meta.settingsHash());
                flush(ch, buf);
                writeFloats(ch, buf, g.lat);
                writeFloats(ch, buf, g.lon);
                writeInts(ch, buf, g.offsets);
                writeInts(ch, buf, g.targets);
                writeInts(ch, buf, g.edgeSegDir);
                writeInts(ch, buf, g.segFrom);
                writeInts(ch, buf, g.segTo);
                writeFloats(ch, buf, g.segLength);
                writeInts(ch, buf, g.segAttributes);
                writeLongs(ch, buf, g.segWayId);
                writeInts(ch, buf, g.segGeomStart);
                writeFloats(ch, buf, g.geomLat);
                writeFloats(ch, buf, g.geomLon);
                byte[] tags = encodeAttributes(g.attributes);
                ensure(ch, buf, 4);
                buf.putInt(tags.length);
                flush(ch, buf);
                ch.write(ByteBuffer.wrap(tags));
                ch.force(false);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public static Meta readMeta(Path file) throws IOException {
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
            return readHeader(ch, buf);
        }
    }

    public static CsrGraph read(Path file) throws IOException {
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocateDirect(BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
            readHeader(ch, buf);
            long fileSize = ch.size();
            float[] lat = readFloats(ch, buf, fileSize);
            float[] lon = readFloats(ch, buf, fileSize);
            int[] offsets = readInts(ch, buf, fileSize);
            int[] targets = readInts(ch, buf, fileSize);
            int[] edgeSegDir = readInts(ch, buf, fileSize);
            int[] segFrom = readInts(ch, buf, fileSize);
            int[] segTo = readInts(ch, buf, fileSize);
            float[] segLength = readFloats(ch, buf, fileSize);
            int[] segAttributes = readInts(ch, buf, fileSize);
            long[] segWayId = readLongs(ch, buf, fileSize);
            int[] segGeomStart = readInts(ch, buf, fileSize);
            float[] geomLat = readFloats(ch, buf, fileSize);
            float[] geomLon = readFloats(ch, buf, fileSize);
            int tagBytes = readLength(ch, buf, fileSize, 1);
            ByteBuffer tags = ByteBuffer.allocate(tagBytes);
            readFully(ch, tags);
            if (ch.position() != fileSize) {
                throw new FormatException("Nadmiarowe dane na końcu pliku grafu");
            }
            List<Map<String, String>> attributes = decodeAttributes(tags.array());

            int n = lat.length;
            int edges = targets.length;
            int segments = segFrom.length;
            if (lon.length != n || offsets.length != n + 1 || edgeSegDir.length != edges
                    || segTo.length != segments || segLength.length != segments || segAttributes.length != segments
                    || segWayId.length != segments || segGeomStart.length != segments + 1
                    || geomLon.length != geomLat.length || offsets[n] != edges) {
                throw new FormatException("Niespójne rozmiary tablic w pliku grafu");
            }
            return new CsrGraph(lat, lon, offsets, targets, edgeSegDir, segFrom, segTo, segLength,
                    segAttributes, segWayId, segGeomStart, geomLat, geomLon, attributes);
        }
    }

    private static Meta readHeader(FileChannel ch, ByteBuffer buf) throws IOException {
        buf.clear().limit(36);
        readFully(ch, buf);
        buf.flip();
        if (buf.getLong() != MAGIC) {
            throw new FormatException("To nie jest plik grafu");
        }
        int version = buf.getInt();
        if (version != VERSION) {
            throw new FormatException("Wersja formatu " + version + ", oczekiwano " + VERSION);
        }
        return new Meta(buf.getLong(), buf.getLong(), buf.getLong());
    }

    // --- zapis tablic kawałkami przez bufor ---

    private static void writeInts(FileChannel ch, ByteBuffer buf, int[] a) throws IOException {
        ensure(ch, buf, 4);
        buf.putInt(a.length);
        for (int off = 0; off < a.length; ) {
            int n = Math.min(a.length - off, buf.remaining() / 4);
            if (n == 0) {
                flush(ch, buf);
                continue;
            }
            buf.asIntBuffer().put(a, off, n);
            buf.position(buf.position() + n * 4);
            off += n;
        }
    }

    private static void writeFloats(FileChannel ch, ByteBuffer buf, float[] a) throws IOException {
        ensure(ch, buf, 4);
        buf.putInt(a.length);
        for (int off = 0; off < a.length; ) {
            int n = Math.min(a.length - off, buf.remaining() / 4);
            if (n == 0) {
                flush(ch, buf);
                continue;
            }
            buf.asFloatBuffer().put(a, off, n);
            buf.position(buf.position() + n * 4);
            off += n;
        }
    }

    private static void writeLongs(FileChannel ch, ByteBuffer buf, long[] a) throws IOException {
        ensure(ch, buf, 4);
        buf.putInt(a.length);
        for (int off = 0; off < a.length; ) {
            int n = Math.min(a.length - off, buf.remaining() / 8);
            if (n == 0) {
                flush(ch, buf);
                continue;
            }
            buf.asLongBuffer().put(a, off, n);
            buf.position(buf.position() + n * 8);
            off += n;
        }
    }

    private static void ensure(FileChannel ch, ByteBuffer buf, int bytes) throws IOException {
        if (buf.remaining() < bytes) {
            flush(ch, buf);
        }
    }

    private static void flush(FileChannel ch, ByteBuffer buf) throws IOException {
        buf.flip();
        while (buf.hasRemaining()) {
            ch.write(buf);
        }
        buf.clear();
    }

    // --- odczyt tablic ---

    private static int[] readInts(FileChannel ch, ByteBuffer buf, long fileSize) throws IOException {
        int[] a = new int[readLength(ch, buf, fileSize, 4)];
        for (int off = 0; off < a.length; ) {
            int n = Math.min(a.length - off, buf.capacity() / 4);
            fill(ch, buf, n * 4);
            buf.asIntBuffer().get(a, off, n);
            off += n;
        }
        return a;
    }

    private static float[] readFloats(FileChannel ch, ByteBuffer buf, long fileSize) throws IOException {
        float[] a = new float[readLength(ch, buf, fileSize, 4)];
        for (int off = 0; off < a.length; ) {
            int n = Math.min(a.length - off, buf.capacity() / 4);
            fill(ch, buf, n * 4);
            buf.asFloatBuffer().get(a, off, n);
            off += n;
        }
        return a;
    }

    private static long[] readLongs(FileChannel ch, ByteBuffer buf, long fileSize) throws IOException {
        long[] a = new long[readLength(ch, buf, fileSize, 8)];
        for (int off = 0; off < a.length; ) {
            int n = Math.min(a.length - off, buf.capacity() / 8);
            fill(ch, buf, n * 8);
            buf.asLongBuffer().get(a, off, n);
            off += n;
        }
        return a;
    }

    // Długość tablicy sprawdzana z rozmiarem pliku, żeby uszkodzony plik nie kazał
    // przydzielić np. 8 GB pamięci.
    private static int readLength(FileChannel ch, ByteBuffer buf, long fileSize, int elementBytes) throws IOException {
        fill(ch, buf, 4);
        int length = buf.getInt();
        if (length < 0 || (long) length * elementBytes > fileSize - ch.position()) {
            throw new FormatException("Nieprawidłowa długość tablicy: " + length);
        }
        return length;
    }

    private static void fill(FileChannel ch, ByteBuffer buf, int bytes) throws IOException {
        buf.clear().limit(bytes);
        readFully(ch, buf);
        buf.flip();
    }

    private static void readFully(FileChannel ch, ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            if (ch.read(buf) < 0) {
                throw new EOFException("Plik grafu jest urwany");
            }
        }
    }

    // --- zestawy tagów: kilkadziesiąt tysięcy małych map, zwykły DataOutputStream wystarcza ---

    private static byte[] encodeAttributes(List<Map<String, String>> attributes) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(attributes.size());
            for (Map<String, String> tags : attributes) {
                out.writeInt(tags.size());
                for (Map.Entry<String, String> tag : tags.entrySet()) {
                    out.writeUTF(tag.getKey());
                    out.writeUTF(tag.getValue());
                }
            }
        }
        return bytes.toByteArray();
    }

    private static List<Map<String, String>> decodeAttributes(byte[] data) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int count = in.readInt();
            if (count < 1 || count > data.length) {
                throw new FormatException("Nieprawidłowa liczba zestawów tagów: " + count);
            }
            List<Map<String, String>> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int size = in.readInt();
                if (size < 0 || size > data.length) {
                    throw new FormatException("Nieprawidłowy rozmiar zestawu tagów: " + size);
                }
                Map<String, String> tags = HashMap.newHashMap(size);
                for (int j = 0; j < size; j++) {
                    tags.put(in.readUTF(), in.readUTF());
                }
                result.add(Map.copyOf(tags));
            }
            return List.copyOf(result);
        }
    }
}
