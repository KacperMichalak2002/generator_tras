package com.wsyska.kmichalak.routegenerator.routing;

import com.wsyska.kmichalak.routegenerator.graph.GeoUtils;

// Obszar, poza który wyszukiwanie nie wychodzi. Pętla o długości L nie oddala się od startu
// o więcej niż L/2, więc reszta mapy jest dla niej zbędna. Granica chroni też przed
// przeszukaniem całego kraju, gdy cel jest nieosiągalny.
public final class SearchBounds {

    private static final double METERS_PER_DEG = Math.toRadians(GeoUtils.EARTH_RADIUS_M);

    private final double centerLat;
    private final double centerLon;
    private final double cosLat;
    private final double radiusSq;

    private SearchBounds(double centerLat, double centerLon, double radiusM) {
        this.centerLat = centerLat;
        this.centerLon = centerLon;
        this.cosLat = Math.cos(Math.toRadians(centerLat));
        this.radiusSq = radiusM * radiusM;
    }

    public static SearchBounds circle(double lat, double lon, double radiusM) {
        if (!(radiusM > 0)) {
            throw new IllegalArgumentException("Promień musi być dodatni: " + radiusM);
        }
        return new SearchBounds(lat, lon, radiusM);
    }

    // Przybliżenie płaskie (bez funkcji trygonometrycznych, bo wywoływane dla każdej krawędzi).
    // Dla promieni do kilkuset km błąd to pojedyncze procenty, więc granicę warto brać z zapasem.
    public boolean contains(double lat, double lon) {
        double dy = (lat - centerLat) * METERS_PER_DEG;
        double dx = (lon - centerLon) * METERS_PER_DEG * cosLat;
        return dx * dx + dy * dy <= radiusSq;
    }
}
