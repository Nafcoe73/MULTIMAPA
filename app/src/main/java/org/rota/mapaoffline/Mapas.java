package org.rota.mapaoffline;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Vários mapas: cada um vive na sua pasta dentro da pasta da app
 * (…/files/<nome>/mapa.png, rota.txt, ruas.bin). Ficheiros soltos na raiz
 * (método USB antigo) aparecem como o mapa "Principal".
 */
final class Mapas {
    private Mapas() {}

    static final String[] FICHEIROS = {"mapa.png", "rota.txt", "ruas.bin"};
    static final String PRINCIPAL = "";

    static final class Mapa {
        final File pasta;
        final String id;          // "" = raiz; senão o nome da subpasta
        String destino = "", detalhe = "";

        Mapa(File pasta, String id) {
            this.pasta = pasta;
            this.id = id;
        }

        String nome() {
            return id.isEmpty() ? "Principal" : id;
        }
    }

    static List<Mapa> listar(File raiz) {
        List<Mapa> out = new ArrayList<>();
        if (new File(raiz, "mapa.png").isFile()) out.add(new Mapa(raiz, PRINCIPAL));
        File[] subs = raiz.listFiles();
        if (subs != null) {
            Arrays.sort(subs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File d : subs)
                if (d.isDirectory() && new File(d, "mapa.png").isFile()) out.add(new Mapa(d, d.getName()));
        }
        return out;
    }

    /** Lê destino e raio do mapa.png (só os metadados: é rápido). */
    static void preencher(Mapa m) {
        try {
            Map<String, String> t = Ficheiros.textosPng(new File(m.pasta, "mapa.png"));
            String geo = t.get("rota_geo");
            if (geo != null) {
                Map<String, Object> j = Json.objeto(geo);
                m.destino = Json.txt(j, "dest_nome", "");
                double r = Json.num(j, "raio_m", 0);
                StringBuilder d = new StringBuilder();
                if (r > 0) d.append(String.format(Geo.PT, "raio %s km", fmtKm(r / 1000)));
                if (new File(m.pasta, "ruas.bin").isFile()) d.append(d.length() > 0 ? " · " : "").append("com ruas");
                if (t.get("rota_nav") != null) d.append(d.length() > 0 ? " · " : "").append("com avisos");
                m.detalhe = d.toString();
            }
        } catch (Exception e) {
            m.detalhe = "mapa inválido";
        }
    }

    private static String fmtKm(double km) {
        return km == Math.rint(km) ? String.valueOf((long) km) : String.format(Locale.ROOT, "%.1f", km);
    }

    /** Nome de pasta seguro a partir de um nome qualquer. */
    static String nomeSeguro(String s) {
        if (s == null) s = "";
        String n = s.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", "_").trim();
        while (n.startsWith(".")) n = n.substring(1);
        if (n.length() > 60) n = n.substring(0, 60).trim();
        return n.isEmpty() ? "mapa" : n;
    }

    /** Apaga um mapa. Só mexe nos ficheiros de mapa (nunca fora da pasta da app). */
    static boolean apagar(File raiz, Mapa m) {
        boolean ok = true;
        for (String f : FICHEIROS) {
            File x = new File(m.pasta, f);
            if (x.exists()) ok &= x.delete();
        }
        if (!m.id.isEmpty() && m.pasta.getParentFile() != null && m.pasta.getParentFile().equals(raiz)) {
            File[] resto = m.pasta.listFiles();
            if (resto != null) for (File x : resto) if (x.isFile()) ok &= x.delete();
            ok &= m.pasta.delete();
        }
        return ok;
    }
}
