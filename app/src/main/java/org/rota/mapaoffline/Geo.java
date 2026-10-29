package org.rota.mapaoffline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Contas de mapa (Web Mercator, igual à app do PC), distâncias e formatação. */
final class Geo {
    private Geo() {}

    static final Locale PT = Locale.forLanguageTag("pt-PT");

    /** Calibração do mapa.png (metadado "rota_geo" gravado pela app do PC). */
    static final class Meta {
        double centerLat, centerLon, zoom, scale, imgW, imgH, destLat, destLon, raioM;
        String destNome;

        static Meta de(java.util.Map<String, Object> m) {
            Meta r = new Meta();
            r.centerLat = Json.num(m, "center_lat", Double.NaN);
            r.centerLon = Json.num(m, "center_lon", Double.NaN);
            r.zoom = Json.num(m, "zoom", Double.NaN);
            r.scale = Json.num(m, "scale", 1);
            r.imgW = Json.num(m, "img_w", Double.NaN);
            r.imgH = Json.num(m, "img_h", Double.NaN);
            r.destLat = Json.num(m, "dest_lat", r.centerLat);
            r.destLon = Json.num(m, "dest_lon", r.centerLon);
            r.raioM = Json.num(m, "raio_m", 0);
            r.destNome = Json.txt(m, "dest_nome", "Destino");
            if (Double.isNaN(r.centerLat) || Double.isNaN(r.zoom) || Double.isNaN(r.imgW))
                throw new IllegalArgumentException("mapa.png sem calibração completa");
            return r;
        }
    }

    /** Ponto com nome (linhas do rota.txt). */
    static final class Ponto {
        final String nome;
        final double lat, lon;

        Ponto(String nome, double lat, double lon) {
            this.nome = nome;
            this.lat = lat;
            this.lon = lon;
        }
    }

    static double[] mundo(double lat, double lon, double zoom) {
        double siny = Math.min(Math.max(Math.sin(Math.toRadians(lat)), -0.9999), 0.9999);
        double n = 256 * Math.pow(2, zoom);
        return new double[]{
                n * (lon + 180.0) / 360.0,
                n * (0.5 - Math.log((1 + siny) / (1 - siny)) / (4 * Math.PI))};
    }

    /** Coordenadas -> pixel na imagem (origem no canto superior esquerdo). */
    static double[] paraPx(Meta m, double lat, double lon) {
        double[] c = mundo(m.centerLat, m.centerLon, m.zoom);
        double[] p = mundo(lat, lon, m.zoom);
        return new double[]{(p[0] - c[0]) * m.scale + m.imgW / 2.0, (p[1] - c[1]) * m.scale + m.imgH / 2.0};
    }

    static double metrosPorPx(Meta m, double lat) {
        return 156543.03392 * Math.cos(Math.toRadians(lat)) / Math.pow(2, m.zoom) / m.scale;
    }

    static double distancia(double lat1, double lon1, double lat2, double lon2) {
        double a1 = Math.toRadians(lat1), a2 = Math.toRadians(lat2);
        double dl = Math.toRadians(lat2 - lat1), dg = Math.toRadians(lon2 - lon1);
        double h = Math.pow(Math.sin(dl / 2), 2) + Math.cos(a1) * Math.cos(a2) * Math.pow(Math.sin(dg / 2), 2);
        return 2 * 6371000 * Math.asin(Math.sqrt(h));
    }

    static double rumo(double lat1, double lon1, double lat2, double lon2) {
        double a1 = Math.toRadians(lat1), a2 = Math.toRadians(lat2), dg = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dg) * Math.cos(a2);
        double x = Math.cos(a1) * Math.sin(a2) - Math.sin(a1) * Math.cos(a2) * Math.cos(dg);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    static String cardeal(double graus) {
        String[] p = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};
        return p[(int) Math.floor((graus + 22.5) / 45) % 8];
    }

    static String fmtDist(double m) {
        return m < 1000 ? String.format(PT, "%.0f m", m) : String.format(PT, "%.2f km", m / 1000);
    }

    static List<double[]> decodePolyline(String s) {
        List<double[]> pts = new ArrayList<>();
        int i = 0;
        long lat = 0, lng = 0;
        while (i < s.length()) {
            long[] v = new long[2];
            for (int k = 0; k < 2; k++) {
                int shift = 0;
                long result = 0;
                while (true) {
                    int b = s.charAt(i++) - 63;
                    result |= (long) (b & 0x1F) << shift;
                    shift += 5;
                    if (b < 0x20) break;
                }
                v[k] = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
            }
            lat += v[0];
            lng += v[1];
            pts.add(new double[]{lat / 1e5, lng / 1e5});
        }
        return pts;
    }

    /** Distância do ponto (px,py) ao segmento e fração t (0..1) da projeção. */
    static double[] projSegmento(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1, l2 = dx * dx + dy * dy;
        double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / l2));
        double qx = x1 + t * dx, qy = y1 + t * dy;
        return new double[]{Math.hypot(px - qx, py - qy), t};
    }

    static String html(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
