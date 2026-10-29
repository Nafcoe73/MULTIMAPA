package org.rota.mapaoffline;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.InflaterInputStream;

/** Leitura dos ficheiros gerados pela app do PC (sem bibliotecas). */
final class Ficheiros {
    private Ficheiros() {}

    static final Charset LATIN1 = Charset.forName("ISO-8859-1");
    static final Charset UTF8 = Charset.forName("UTF-8");

    static byte[] lerTudo(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    static byte[] lerTudo(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return lerTudo(in);
        }
    }

    static byte[] inflar(byte[] d, int off, int len) throws IOException {
        try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(d, off, len))) {
            return lerTudo(in);
        }
    }

    private static int zero(byte[] d, int desde) {
        for (int k = desde; k < d.length; k++) if (d[k] == 0) return k;
        return -1;
    }

    /** Metadados de texto de um PNG (tEXt / zTXt / iTXt), sem ler os dados da imagem. */
    static Map<String, String> textosPng(File f) throws IOException {
        Map<String, String> out = new HashMap<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)))) {
            byte[] sig = new byte[8];
            in.readFully(sig);
            if ((sig[0] & 0xFF) != 0x89 || sig[1] != 'P' || sig[2] != 'N' || sig[3] != 'G') return out;
            while (true) {
                int len;
                try {
                    len = in.readInt();
                } catch (EOFException e) {
                    break;
                }
                byte[] t = new byte[4];
                in.readFully(t);
                String tipo = new String(t, LATIN1);
                if (tipo.equals("IDAT")) {                 // dados da imagem: saltar
                    long falta = (len & 0xFFFFFFFFL) + 4;
                    while (falta > 0) {
                        long s = in.skip(falta);
                        if (s <= 0) { in.readByte(); s = 1; }
                        falta -= s;
                    }
                    continue;
                }
                byte[] d = new byte[len];
                in.readFully(d);
                in.readInt();                              // CRC
                if (tipo.equals("tEXt")) {
                    int z = zero(d, 0);
                    if (z > 0) out.put(new String(d, 0, z, LATIN1), new String(d, z + 1, d.length - z - 1, LATIN1));
                } else if (tipo.equals("zTXt")) {
                    int z = zero(d, 0);
                    if (z > 0) out.put(new String(d, 0, z, LATIN1),
                            new String(inflar(d, z + 2, d.length - z - 2), LATIN1));
                } else if (tipo.equals("iTXt")) {
                    int z = zero(d, 0);
                    if (z <= 0) continue;
                    String chave = new String(d, 0, z, LATIN1);
                    boolean comprimido = d[z + 1] != 0;
                    int lingua = zero(d, z + 3);            // fim da língua
                    int trad = zero(d, lingua + 1);         // fim da palavra traduzida
                    int ini = trad + 1;
                    byte[] txt = comprimido ? inflar(d, ini, d.length - ini) : null;
                    out.put(chave, comprimido ? new String(txt, UTF8) : new String(d, ini, d.length - ini, UTF8));
                } else if (tipo.equals("IEND")) {
                    break;
                }
            }
        }
        return out;
    }

    /** Linhas 'local | lat , lon' do rota.txt. */
    static List<Geo.Ponto> lerRota(File f) throws IOException {
        List<Geo.Ponto> pts = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), UTF8))) {
            String linha;
            while ((linha = r.readLine()) != null) {
                int barra = linha.lastIndexOf('|');
                if (barra < 0) continue;
                String[] c = linha.substring(barra + 1).split(",");
                if (c.length != 2) continue;
                try {
                    pts.add(new Geo.Ponto(linha.substring(0, barra).trim(),
                            Double.parseDouble(c[0].trim()), Double.parseDouble(c[1].trim())));
                } catch (NumberFormatException e) {
                    // cabeçalho "local | coordenadas"
                }
            }
        }
        return pts;
    }
}
