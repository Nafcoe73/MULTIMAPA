package org.rota.mapaoffline;

import java.util.Map;

/** Texto da caixa de navegação (em HTML simples) e a cor que ela deve ter. */
final class Painel {
    static final int ESPERA = 0, NORMAL = 1, ALERTA = 2, FORA = 3;
    static final double ALERTA_M = 100;   // distância para o aviso laranja

    final String html;
    final int cor;

    private Painel(String html, int cor) {
        this.html = html;
        this.cor = cor;
    }

    /** Nome da rua onde estou. -> {texto, "1" se exato}. 'preferida' ganha nos cruzamentos. */
    static String[] ruaAqui(BaseRuas ruas, double lat, double lon, double precisao, String preferida) {
        if (ruas == null) return new String[]{null, "0"};
        Map<String, Double> cand = ruas.candidatos(lat, lon, 150);
        if (cand.isEmpty()) return new String[]{null, "0"};
        double limite = Math.max(25.0, Math.min(precisao, 50.0));
        if (preferida != null && !preferida.isEmpty()) {
            Double dp = cand.get(preferida);
            if (dp != null && dp <= limite) return new String[]{preferida, "1"};
        }
        String nome = null;
        double d = Double.MAX_VALUE;
        for (Map.Entry<String, Double> e : cand.entrySet()) {
            if (e.getValue() < d) { d = e.getValue(); nome = e.getKey(); }
        }
        if (d <= limite) return new String[]{nome, "1"};
        return new String[]{String.format(Geo.PT, "perto de %s (%.0f m)", nome, d), "0"};
    }

    /**
     * @param eu      {lat, lon, precisão} ou null se ainda não há GPS
     * @param destino {lat, lon}
     */
    static Painel calcular(boolean temMapa, double[] eu, BaseRuas ruas, Navegacao nav,
                           double[] destino, String estadoRuas) {
        if (!temMapa) return new Painel("", ESPERA);
        if (eu == null) {
            String extra = (estadoRuas != null && !estadoRuas.isEmpty())
                    ? "<br><small>" + Geo.html(estadoRuas) + "</small>" : "";
            return new Painel("À espera do GPS…" + extra, ESPERA);
        }
        double lat = eu[0], lon = eu[1], prec = eu[2];

        if (nav == null) {
            String[] r = ruaAqui(ruas, lat, lon, prec, null);
            String h = "<b>Estás em:</b> " + Geo.html(r[0] != null ? r[0] : "rua desconhecida");
            if (ruas == null) h += "<br><small>(sem ruas.bin: só sei os nomes da rota)</small>";
            return new Painel(h, NORMAL);
        }

        double[] e = nav.estado(lat, lon);
        double feito = e[0], fora = e[1];
        int i = (int) e[2];
        double limite = Math.max(50.0, Math.min(prec * 1.5, 120.0));
        boolean foraRota = fora > limite;
        String[] r = ruaAqui(ruas, lat, lon, prec, foraRota ? null : nav.passos.get(i).nome);
        String rua = r[0];
        boolean exata = "1".equals(r[1]);

        if (foraRota) {
            double d = Geo.distancia(lat, lon, destino[0], destino[1]);
            double ru = Geo.rumo(lat, lon, destino[0], destino[1]);
            return new Painel("<b>FORA DA ROTA</b> (a " + Geo.fmtDist(fora) + " do caminho)<br>"
                    + "<b>Estás em:</b> " + Geo.html(rua != null ? rua : "rua desconhecida") + "<br>"
                    + "Destino a " + Geo.fmtDist(d) + " para " + Geo.cardeal(ru), FORA);
        }

        Navegacao.Passo passo = nav.passos.get(i);
        String aqui = (rua != null && exata) ? rua
                : (passo.nome != null && !passo.nome.isEmpty() ? passo.nome : (rua != null ? rua : "—"));
        StringBuilder h = new StringBuilder("<b>Estás em:</b> ").append(Geo.html(aqui));
        int cor = NORMAL;
        if (i + 1 < nav.passos.size()) {
            Navegacao.Passo prox = nav.passos.get(i + 1);
            double falta = Math.max(0, nav.along[i + 1] - feito);
            if (falta <= ALERTA_M) {
                cor = ALERTA;
                h.append("<br><big><b>DAQUI A ").append(Geo.fmtDist(falta).toUpperCase(Geo.PT))
                        .append(": ").append(Geo.html(prox.texto)).append("</b></big>");
            } else {
                h.append("<br><b>A seguir</b> (").append(Geo.fmtDist(falta)).append("): ")
                        .append(Geo.html(prox.texto));
            }
        }
        h.append("<br>Faltam ").append(Geo.fmtDist(Math.max(0, nav.total - feito))).append(" pela rota");
        return new Painel(h.toString(), cor);
    }
}
