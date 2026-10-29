package org.rota.mapaoffline;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Traçado completo da rota + passos (metadado "rota_nav" do mapa.png). */
final class Navegacao {
    static final class Passo {
        final String texto, nome;
        final double lat, lon;

        Passo(String texto, String nome, double lat, double lon) {
            this.texto = texto;
            this.nome = nome;
            this.lat = lat;
            this.lon = lon;
        }
    }

    final List<Passo> passos = new ArrayList<>();
    final double[] along;      // distância (m) desde o início da rota até cada passo
    final double total;
    private final double lat0, lon0, kx, ky;
    private final double[] xs, ys, cum;

    /** A partir do JSON {"poly": "...", "passos": [{"t","n","la","lo"}, ...]} */
    static Navegacao deJson(String json) {
        Map<String, Object> m = Json.objeto(json);
        List<Passo> ps = new ArrayList<>();
        Object lista = m.get("passos");
        if (lista instanceof List) {
            for (Object o : (List<?>) lista) {
                @SuppressWarnings("unchecked") Map<String, Object> p = (Map<String, Object>) o;
                ps.add(new Passo(Json.txt(p, "t", ""), Json.txt(p, "n", ""),
                        Json.num(p, "la", 0), Json.num(p, "lo", 0)));
            }
        }
        return new Navegacao(Json.txt(m, "poly", ""), ps);
    }

    Navegacao(String poly, List<Passo> lista) {
        List<double[]> geo = Geo.decodePolyline(poly);
        if (geo.size() < 2) throw new IllegalArgumentException("rota sem traçado");
        lat0 = geo.get(0)[0];
        lon0 = geo.get(0)[1];
        kx = 111320.0 * Math.cos(Math.toRadians(lat0));
        ky = 111320.0;
        int n = geo.size();
        xs = new double[n];
        ys = new double[n];
        cum = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = (geo.get(i)[1] - lon0) * kx;
            ys[i] = (geo.get(i)[0] - lat0) * ky;
            if (i > 0) cum[i] = cum[i - 1] + Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
        }
        total = cum[n - 1];
        along = new double[lista.size()];
        int ini = 0;
        for (int k = 0; k < lista.size(); k++) {          // os passos seguem a ordem da rota
            Passo p = lista.get(k);
            double x = (p.lon - lon0) * kx, y = (p.lat - lat0) * ky;
            double melhor = Double.MAX_VALUE;
            int bi = ini;
            for (int i = ini; i < n; i++) {
                double d = (xs[i] - x) * (xs[i] - x) + (ys[i] - y) * (ys[i] - y);
                if (d < melhor) { melhor = d; bi = i; }
            }
            ini = bi;
            passos.add(p);
            along[k] = cum[bi];
        }
    }

    /** -> {distância já feita na rota, distância ao caminho, índice do passo atual} */
    double[] estado(double lat, double lon) {
        double x = (lon - lon0) * kx, y = (lat - lat0) * ky;
        double melhor = Double.MAX_VALUE, aoLongo = 0;
        for (int i = 0; i < xs.length - 1; i++) {
            double[] r = Geo.projSegmento(x, y, xs[i], ys[i], xs[i + 1], ys[i + 1]);
            if (r[0] < melhor) {
                melhor = r[0];
                aoLongo = cum[i] + r[1] * (cum[i + 1] - cum[i]);
            }
        }
        int atual = 0;
        for (int k = 0; k < along.length; k++) if (along[k] <= aoLongo + 10) atual = k;
        return new double[]{aoLongo, melhor, atual};
    }
}
