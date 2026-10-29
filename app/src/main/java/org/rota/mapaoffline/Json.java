package org.rota.mapaoffline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Leitor de JSON minúsculo (sem bibliotecas): objetos -> Map, listas -> List, números -> Double. */
final class Json {
    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    static Object ler(String texto) {
        Json j = new Json(texto);
        j.espacos();
        Object v = j.valor();
        j.espacos();
        if (j.i != j.s.length()) throw new IllegalArgumentException("JSON: lixo no fim");
        return v;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> objeto(String texto) {
        Object v = ler(texto);
        if (!(v instanceof Map)) throw new IllegalArgumentException("JSON: esperava um objeto");
        return (Map<String, Object>) v;
    }

    static double num(Map<String, Object> m, String k, double defeito) {
        Object v = m.get(k);
        return v instanceof Double ? (Double) v : defeito;
    }

    static String txt(Map<String, Object> m, String k, String defeito) {
        Object v = m.get(k);
        return v instanceof String ? (String) v : defeito;
    }

    private void espacos() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private char atual() {
        if (i >= s.length()) throw new IllegalArgumentException("JSON: fim inesperado");
        return s.charAt(i);
    }

    private Object valor() {
        char c = atual();
        switch (c) {
            case '{': return obj();
            case '[': return lista();
            case '"': return texto();
            case 't': palavra("true"); return Boolean.TRUE;
            case 'f': palavra("false"); return Boolean.FALSE;
            case 'n': palavra("null"); return null;
            default: return numero();
        }
    }

    private void palavra(String p) {
        if (!s.startsWith(p, i)) throw new IllegalArgumentException("JSON: esperava " + p);
        i += p.length();
    }

    private Map<String, Object> obj() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        espacos();
        if (atual() == '}') { i++; return m; }
        while (true) {
            espacos();
            String k = texto();
            espacos();
            if (atual() != ':') throw new IllegalArgumentException("JSON: esperava ':'");
            i++;
            espacos();
            m.put(k, valor());
            espacos();
            char c = atual();
            i++;
            if (c == '}') return m;
            if (c != ',') throw new IllegalArgumentException("JSON: esperava ',' ou '}'");
        }
    }

    private List<Object> lista() {
        List<Object> l = new ArrayList<>();
        i++;
        espacos();
        if (atual() == ']') { i++; return l; }
        while (true) {
            espacos();
            l.add(valor());
            espacos();
            char c = atual();
            i++;
            if (c == ']') return l;
            if (c != ',') throw new IllegalArgumentException("JSON: esperava ',' ou ']'");
        }
    }

    private String texto() {
        if (atual() != '"') throw new IllegalArgumentException("JSON: esperava texto");
        i++;
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = atual();
            i++;
            if (c == '"') return b.toString();
            if (c != '\\') { b.append(c); continue; }
            char e = atual();
            i++;
            switch (e) {
                case 'n': b.append('\n'); break;
                case 't': b.append('\t'); break;
                case 'r': b.append('\r'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'u':
                    b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                    break;
                default: b.append(e);   // \" \\ \/
            }
        }
    }

    private Double numero() {
        int ini = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (ini == i) throw new IllegalArgumentException("JSON: carácter inesperado '" + atual() + "'");
        return Double.parseDouble(s.substring(ini, i));
    }
}
