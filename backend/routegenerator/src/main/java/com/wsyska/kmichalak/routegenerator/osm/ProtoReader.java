package com.wsyska.kmichalak.routegenerator.osm;

import java.nio.charset.StandardCharsets;


final class ProtoReader {

    static final int VARINT = 0;
    static final int FIXED64 = 1;
    static final int LENGTH_DELIMITED = 2;
    static final int FIXED32 = 5;

    private final byte[] buf;
    private int pos;
    private final int limit;

    private int tag;

    ProtoReader(byte[] buf) {
        this(buf, 0, buf.length);
    }

    ProtoReader(byte[] buf, int offset, int length) {
        this.buf = buf;
        this.pos = offset;
        this.limit = offset + length;
    }

    boolean next() {
        if (pos >= limit) {
            return false;
        }
        tag = (int) readVarint();
        return true;
    }

    int field() {
        return tag >>> 3;
    }

    int wireType() {
        return tag & 7;
    }

    long readVarint() {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            byte b = buf[pos++];
            result |= (long) (b & 0x7F) << shift;
            if (b >= 0) {
                return result;
            }
        }
        throw new OsmFormatException("Zbyt długi varint");
    }

    long readSInt64() {
        long n = readVarint();
        return (n >>> 1) ^ -(n & 1);
    }

    String readString() {
        int len = readLength();
        String s = new String(buf, pos, len, StandardCharsets.UTF_8);
        pos += len;
        return s;
    }

    byte[] readBytes() {
        int len = readLength();
        byte[] out = new byte[len];
        System.arraycopy(buf, pos, out, 0, len);
        pos += len;
        return out;
    }

    ProtoReader readMessage() {
        int len = readLength();
        ProtoReader nested = new ProtoReader(buf, pos, len);
        pos += len;
        return nested;
    }

    long[] readPackedVarints(boolean zigZag) {
        if (wireType() == VARINT) {
            return new long[] {zigZag ? readSInt64() : readVarint()};
        }
        ProtoReader packed = readMessage();
        long[] values = new long[packed.countVarints()];
        for (int i = 0; i < values.length; i++) {
            values[i] = zigZag ? packed.readSInt64() : packed.readVarint();
        }
        return values;
    }

    void skip() {
        switch (wireType()) {
            case VARINT -> readVarint();
            case FIXED64 -> pos += 8;
            case LENGTH_DELIMITED -> {
                int len = readLength();
                pos += len;
            }
            case FIXED32 -> pos += 4;
            default -> throw new OsmFormatException("Nieobsługiwany wire type: " + wireType());
        }
    }

    private int countVarints() {
        int count = 0;
        for (int i = pos; i < limit; i++) {
            if (buf[i] >= 0) {
                count++;
            }
        }
        return count;
    }

    private int readLength() {
        long len = readVarint();
        if (len < 0 || pos + len > limit) {
            throw new OsmFormatException("Nieprawidłowa długość pola: " + len);
        }
        return (int) len;
    }
}
