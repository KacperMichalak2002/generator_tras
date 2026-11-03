package com.wsyska.kmichalak.routegenerator.osm;

public class OsmFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OsmFormatException(String message) {
        super(message);
    }

    public OsmFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
