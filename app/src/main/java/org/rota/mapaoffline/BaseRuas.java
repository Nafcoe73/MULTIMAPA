package org.rota.mapaoffline;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lê o ruas.bin (todas as ruas com nome dentro do raio, gerado no PC) e diz que ruas
 * estão perto de um ponto. Formato: "RUAS1\n" + int32 LE tamanho + cabeçalho JSON +
 * blocos zlib (nomes em UTF-8 separados por \n; arrays int32 little-endian).
 */
final class BaseRuas {
    String[] nomes;
    int[] px, py, seg, goff, gseg;
    double lat0, lon0, kx, ky, cel, xmin, ymin;
    int nx, ny;

    BaseRuas(File f) throws IOException {
        byte[] d = Ficheiros.lerTudo(f);
        if (d.length < 10 || !new String(d, 0, 6, Ficheiros.LATIN1).equals("RUAS1\n"))
            throw new IOException("ruas.bin inválido");
        int n = ByteBuffer.wrap(d, 6, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        Map<String, Object> cab = Json.objeto(new String(d, 10, n, Ficheiros.UTF8));
        int pos = 10 + n;
        for (Object o : (List<?>) cab.get("blocos")) {
            List<?> b = (List<?>) o;
            String nome = (String) b.get(0);
            int tam = ((Double) b.get(1)).intValue();
            byte[] raw = Ficheiros.inflar(d, pos, tam);
            pos += tam;
            if (nome.equals("nomes")) {
                nomes = new String(raw, Ficheiros.UTF8).split("\n", -1);
            } else {
                int[] a = new int[raw.length / 4];
                ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(a);
                switch (nome) {
                    case "px": px = a; break;
                    case "py": py = a; break;
                    case "seg": seg = a; break;
                    case "goff": goff = a; break;
                    case "gseg": gseg = a; break;
                    default: break;
                }
            }
        }
        lat0 = Json.num(cab, "lat0", 0);
        lon0 = Json.num(cab, "lon0", 0);
        kx = Json.num(cab, "kx", 1);
        ky = Json.num(cab, "ky", 1);
        cel = Json.num(cab, "cel", 200);
        xmin = Json.num(cab, "xmin", 0);
        ymin = Json.num(cab, "ymin", 0);
        nx = (int) Json.num(cab, "nx", 0);
        ny = (int) Json.num(cab, "ny", 0);
        if (nomes == null || px == null || py == null || seg == null || goff == null || gseg == null)
            throw new IOException("ruas.bin incompleto");
    }

    /** {nome: distância mínima em metros} de todas as ruas até maxD metros. */
    Map<String, Double> candidatos(double lat, double lon, double maxD) {
        Map<String, Double> cand = new HashMap<>();
        double x = (lon - lon0) * kx, y = (lat - lat0) * ky;
        int ci = (int) Math.floor((x - xmin) / cel), cj = (int) Math.floor((y - ymin) / cel);
        int alcance = (int) (maxD / cel) + 1;
        for (int j = cj - alcance; j <= cj + alcance; j++) {
            if (j < 0 || j >= ny) continue;
            for (int i = ci - alcance; i <= ci + alcance; i++) {
                if (i < 0 || i >= nx) continue;
                int c = j * nx + i;
                for (int k = goff[c]; k < goff[c + 1]; k++) {
                    int s = gseg[k];
                    double dist = Geo.projSegmento(x, y, px[s], py[s], px[s + 1], py[s + 1])[0];
                    if (dist <= maxD) {
                        String nome = nomes[seg[s]];
                        Double ant = cand.get(nome);
                        if (ant == null || dist < ant) cand.put(nome, dist);
                    }
                }
            }
        }
        return cand;
    }
}
