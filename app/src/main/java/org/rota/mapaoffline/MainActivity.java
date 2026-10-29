package org.rota.mapaoffline;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.Html;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mapa Offline — 100% offline: lê mapa.png + rota.txt (+ ruas.bin) e usa só o GPS.
 * Destino = bolinha VERDE; eu = bolinha VERMELHA; caixa de navegação em baixo.
 */
public class MainActivity extends Activity {
    private static final int REQ_LOC = 1, REQ_IMPORTAR = 2;
    private static final int[] CORES = {
            Color.argb(158, 0, 0, 0),        // à espera
            Color.argb(225, 13, 69, 140),    // azul: normal
            Color.argb(240, 245, 133, 0),    // laranja: manobra a < 100 m
            Color.argb(235, 199, 26, 26)};   // vermelho: fora da rota

    private MapaView mapa;
    private TextView info, nav;
    private Button btnSeguir;
    private GradientDrawable fundoNav;
    private final ExecutorService trabalho = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private LocationManager lm;
    private boolean gpsLigado, seguir;
    private String gpsEstado = "GPS a iniciar…";
    private int satUsados = -1, satVistos = -1;

    private File pastaApp;   // pasta da app (…/Android/data/org.rota.mapaoffline/files)
    private File pasta;      // pasta do mapa atual
    private SharedPreferences prefs;
    private Geo.Meta meta;
    private double[] destino;
    private String destNome = "Destino";
    private Navegacao navegacao;
    private BaseRuas ruas;
    private String estadoRuas = "";
    private double[] eu;     // {lat, lon, precisão, velocidade, rumo}

    // ------------------------------------------------------------ interface
    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private GradientDrawable fundo(int cor, float raioDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(cor);
        g.setCornerRadius(dp(raioDp));
        return g;
    }

    private TextView caixa(float sp) {
        TextView t = new TextView(this);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setPadding(dp(10), dp(6), dp(10), dp(6));
        return t;
    }

    private Button botao(String texto, View.OnClickListener acao) {
        Button b = new Button(this);
        b.setText(texto);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setTextColor(Color.WHITE);
        b.setPadding(dp(2), 0, dp(2), 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setBackground(fundo(Color.argb(225, 32, 32, 32), 6));
        b.setOnClickListener(acao);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        b.setLayoutParams(lp);
        return b;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout raiz = new FrameLayout(this);
        raiz.setFitsSystemWindows(true);
        raiz.setBackgroundColor(Color.rgb(30, 30, 30));

        mapa = new MapaView(this);
        raiz.addView(mapa, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        info = caixa(13.5f);
        info.setGravity(Gravity.CENTER);
        info.setBackground(fundo(CORES[0], 8));
        FrameLayout.LayoutParams lpi = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        lpi.setMargins(dp(8), dp(6), dp(8), 0);
        raiz.addView(info, lpi);

        LinearLayout baixo = new LinearLayout(this);
        baixo.setOrientation(LinearLayout.VERTICAL);
        nav = caixa(16);
        nav.setMinHeight(dp(84));
        nav.setGravity(Gravity.CENTER_VERTICAL);
        fundoNav = fundo(CORES[0], 10);
        nav.setBackground(fundoNav);
        LinearLayout.LayoutParams lpn = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lpn.setMargins(dp(6), 0, dp(6), dp(6));
        baixo.addView(nav, lpn);

        LinearLayout botoes = new LinearLayout(this);
        botoes.setOrientation(LinearLayout.HORIZONTAL);
        botoes.addView(botao("Onde\nestou", v -> centrarEmMim()));
        botoes.addView(botao("Destino", v -> { if (destino != null) mapa.centrar(destino[0], destino[1]); }));
        btnSeguir = botao("Seguir:\nnão", v -> alternarSeguir());
        botoes.addView(btnSeguir);
        botoes.addView(botao("Mapa\ntodo", v -> mapa.verTudo()));
        botoes.addView(botao("Mapas", v -> mostrarMapas()));
        LinearLayout.LayoutParams lpb = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lpb.setMargins(dp(4), 0, dp(4), dp(6));
        baixo.addView(botoes, lpb);
        raiz.addView(baixo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        View.OnLayoutChangeListener margens = (v, l, t, r, bt, ol, ot, or, ob) ->
                mapa.setMargens(info.getBottom() - mapa.getTop(), baixo.getTop() - mapa.getTop());
        info.addOnLayoutChangeListener(margens);
        baixo.addOnLayoutChangeListener(margens);

        setContentView(raiz);

        File ext = getExternalFilesDir(null);   // cria a pasta se não existir
        pastaApp = ext != null ? ext : getFilesDir();
        prefs = getSharedPreferences("mapa_offline", MODE_PRIVATE);
        pasta = escolherMapaInicial();
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        carregar();
        pedirGps();
    }

    @Override
    protected void onDestroy() {
        pararGps();
        trabalho.shutdownNow();
        super.onDestroy();
    }

    private void toast(String t) {
        Toast.makeText(this, t, Toast.LENGTH_LONG).show();
    }

    private void centrarEmMim() {
        if (eu != null) mapa.centrar(eu[0], eu[1]);
        else toast("Ainda sem posição GPS.");
    }

    private void alternarSeguir() {
        seguir = !seguir;
        btnSeguir.setText(seguir ? "Seguir:\nsim" : "Seguir:\nnão");
        if (seguir) centrarEmMim();
    }

    // ------------------------------------------------------------ ficheiros
    private static final class Carregado {
        Bitmap bitmap;
        Geo.Meta meta;
        List<Geo.Ponto> pontos = new ArrayList<>();
        double[] destino;
        String destNome = "Destino";
        Navegacao nav;
        String erro;
    }

    private void carregar() {
        info.setText("A carregar o mapa…");
        trabalho.execute(() -> {
            Carregado c = new Carregado();
            try {
                File png = new File(pasta, "mapa.png");
                if (!png.isFile()) {
                    c.erro = "sem mapa";
                } else {
                    Map<String, String> textos = Ficheiros.textosPng(png);
                    String geo = textos.get("rota_geo");
                    if (geo == null) throw new IllegalArgumentException(
                            "Este mapa.png não tem calibração (foi editado ou não veio da app do PC).");
                    c.meta = Geo.Meta.de(Json.objeto(geo));
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    c.bitmap = BitmapFactory.decodeFile(png.getAbsolutePath(), o);
                    if (c.bitmap == null) throw new IllegalArgumentException("Não consegui abrir o mapa.png.");
                    if (textos.get("rota_nav") != null) {
                        try {
                            c.nav = Navegacao.deJson(textos.get("rota_nav"));
                        } catch (RuntimeException e) {
                            c.nav = null;    // mapa antigo sem traçado: só sem avisos de manobra
                        }
                    }
                    File txt = new File(pasta, "rota.txt");
                    if (txt.isFile()) c.pontos = Ficheiros.lerRota(txt);
                    Geo.Ponto dest = null;
                    for (Geo.Ponto p : c.pontos)
                        if (p.nome.toUpperCase(Locale.ROOT).startsWith("[DESTINO]")) dest = p;
                    if (dest == null && !c.pontos.isEmpty()) dest = c.pontos.get(c.pontos.size() - 1);
                    if (dest != null) {
                        c.destino = new double[]{dest.lat, dest.lon};
                        c.destNome = dest.nome.replace("[DESTINO]", "").trim();
                    } else {
                        c.destino = new double[]{c.meta.destLat, c.meta.destLon};
                        c.destNome = c.meta.destNome;
                    }
                }
            } catch (Exception | OutOfMemoryError e) {
                c.erro = e.getMessage() != null ? e.getMessage() : e.toString();
            }
            ui.post(() -> aplicar(c));
        });
    }

    private void aplicar(Carregado c) {
        ruas = null;
        estadoRuas = "";
        if (c.erro != null || c.meta == null) {
            meta = null;
            navegacao = null;
            destino = null;
            mapa.setMapa(null, null, null, null);
            String porque = "sem mapa".equals(c.erro) ? "<b>Ainda não há mapa.</b>" : "<b>Erro:</b> " + Geo.html(c.erro);
            info.setText(Html.fromHtml(porque + "<br>Carrega em <b>Mapas → Importar</b> e escolhe a pasta onde estão "
                    + "mapa.png, rota.txt e ruas.bin (ex.: Download/rota),<br>ou copia-os por USB para uma "
                    + "subpasta de:<br><small>" + Geo.html(pastaApp.getAbsolutePath()) + "</small>",
                    Html.FROM_HTML_MODE_LEGACY));
            atualizarNav();
            return;
        }
        meta = c.meta;
        navegacao = c.nav;
        destino = c.destino;
        destNome = c.destNome;
        mapa.setMapa(c.bitmap, c.meta, c.pontos, c.destino);
        atualizarTextos();
        final File f = new File(pasta, "ruas.bin");
        if (f.isFile()) {
            estadoRuas = "A carregar as ruas do raio…";
            trabalho.execute(() -> {
                BaseRuas base = null;
                String msg;
                try {
                    base = new BaseRuas(f);
                    msg = base.nomes.length + " ruas do raio carregadas";
                } catch (Exception | OutOfMemoryError e) {
                    msg = "Erro a ler ruas.bin: " + e.getMessage();
                }
                final BaseRuas fb = base;
                final String fm = msg;
                ui.post(() -> {
                    ruas = fb;
                    estadoRuas = fm;
                    atualizarTextos();
                });
            });
        }
    }

    // ------------------------------------------------------------ vários mapas
    private File escolherMapaInicial() {
        String id = prefs.getString("mapa_atual", null);
        List<Mapas.Mapa> lista = Mapas.listar(pastaApp);
        for (Mapas.Mapa m : lista) if (m.id.equals(id)) return m.pasta;
        return lista.isEmpty() ? pastaApp : lista.get(0).pasta;
    }

    private String idAtual() {
        return pasta.equals(pastaApp) ? Mapas.PRINCIPAL : pasta.getName();
    }

    private void abrirMapa(Mapas.Mapa m) {
        pasta = m.pasta;
        prefs.edit().putString("mapa_atual", m.id).apply();
        seguir = false;
        btnSeguir.setText("Seguir:\nnão");
        carregar();
    }

    private void mostrarMapas() {
        trabalho.execute(() -> {
            final List<Mapas.Mapa> lista = Mapas.listar(pastaApp);
            for (Mapas.Mapa m : lista) Mapas.preencher(m);
            ui.post(() -> dialogoMapas(lista));
        });
    }

    private void dialogoMapas(final List<Mapas.Mapa> lista) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(lista.isEmpty() ? "Ainda não há mapas" : "Escolhe o mapa (" + lista.size() + ")");
        if (lista.isEmpty()) {
            b.setMessage("Importa uma pasta com mapa.png, rota.txt e ruas.bin.\n\nSe escolheres uma pasta com "
                    + "várias subpastas (uma por rota), importo todas de uma vez.");
        } else {
            String atual = idAtual();
            CharSequence[] itens = new CharSequence[lista.size()];
            for (int i = 0; i < lista.size(); i++) {
                Mapas.Mapa m = lista.get(i);
                StringBuilder h = new StringBuilder();
                h.append(m.id.equals(atual) ? "<b>✔ " : "<b>").append(Geo.html(m.nome())).append("</b>");
                if (!m.destino.isEmpty() && !m.destino.equals(m.nome()))
                    h.append("<br><small>→ ").append(Geo.html(m.destino)).append("</small>");
                if (!m.detalhe.isEmpty()) h.append("<br><small>").append(Geo.html(m.detalhe)).append("</small>");
                itens[i] = Html.fromHtml(h.toString(), Html.FROM_HTML_MODE_LEGACY);
            }
            b.setItems(itens, (d, i) -> abrirMapa(lista.get(i)));
            b.setNeutralButton("Apagar…", (d, w) -> dialogoApagar(lista));
        }
        b.setPositiveButton("Importar…", (d, w) -> importar());
        b.setNegativeButton("Fechar", null);
        b.show();
    }

    private void dialogoApagar(final List<Mapas.Mapa> lista) {
        CharSequence[] nomes = new CharSequence[lista.size()];
        for (int i = 0; i < lista.size(); i++) nomes[i] = lista.get(i).nome();
        new AlertDialog.Builder(this)
                .setTitle("Apagar que mapa?")
                .setItems(nomes, (d, i) -> {
                    final Mapas.Mapa m = lista.get(i);
                    new AlertDialog.Builder(this)
                            .setMessage("Apagar o mapa «" + m.nome() + "» deste telemóvel?")
                            .setPositiveButton("Apagar", (d2, w) -> {
                                boolean eraAtual = m.pasta.equals(pasta);
                                toast(Mapas.apagar(pastaApp, m) ? "Apagado: " + m.nome() : "Não consegui apagar tudo.");
                                if (eraAtual) {
                                    pasta = escolherMapaInicial();
                                    carregar();
                                }
                            })
                            .setNegativeButton("Cancelar", null)
                            .show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ------------------------------------------------------------ importar (sem permissões: o utilizador escolhe a pasta)
    private void importar() {
        try {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_IMPORTAR);
            toast("Escolhe a pasta do mapa (ou uma pasta com várias subpastas de mapas)");
        } catch (ActivityNotFoundException e) {
            toast("Este telemóvel não deixa escolher pastas. Copia os ficheiros por USB.");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_IMPORTAR || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri arvore = data.getData();
        info.setText("A importar…");
        trabalho.execute(() -> {
            final List<String> importados = new ArrayList<>();
            final String msg = importarArvore(arvore, importados);
            ui.post(() -> {
                toast(msg);
                if (!importados.isEmpty()) {
                    // abre o último importado
                    String id = importados.get(importados.size() - 1);
                    for (Mapas.Mapa m : Mapas.listar(pastaApp)) if (m.id.equals(id)) { abrirMapa(m); return; }
                }
                carregar();
            });
        });
    }

    /** Uma entrada de pasta escolhida pelo utilizador (Storage Access Framework). */
    private static final class Doc {
        final String id, nome, tipo;

        Doc(String id, String nome, String tipo) {
            this.id = id;
            this.nome = nome;
            this.tipo = tipo;
        }

        boolean pasta() {
            return DocumentsContract.Document.MIME_TYPE_DIR.equals(tipo);
        }
    }

    private List<Doc> filhos(Uri arvore, String docId) {
        List<Doc> out = new ArrayList<>();
        Uri u = DocumentsContract.buildChildDocumentsUriUsingTree(arvore, docId);
        try (Cursor c = getContentResolver().query(u, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            while (c != null && c.moveToNext()) out.add(new Doc(c.getString(0), c.getString(1), c.getString(2)));
        }
        return out;
    }

    private String nomeDoc(Uri arvore, String docId) {
        try (Cursor c = getContentResolver().query(DocumentsContract.buildDocumentUriUsingTree(arvore, docId),
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (RuntimeException ignored) {
        }
        return "mapa";
    }

    private static boolean temMapa(List<Doc> docs) {
        for (Doc d : docs) if (!d.pasta() && "mapa.png".equalsIgnoreCase(d.nome)) return true;
        return false;
    }

    /** Importa a pasta escolhida: ou é um mapa, ou contém subpastas que são mapas. */
    private String importarArvore(Uri arvore, List<String> importados) {
        try {
            String raizId = DocumentsContract.getTreeDocumentId(arvore);
            List<Doc> docs = filhos(arvore, raizId);
            if (temMapa(docs)) {
                String id = copiarMapa(arvore, docs, Mapas.nomeSeguro(nomeDoc(arvore, raizId)));
                importados.add(id);
            } else {
                for (Doc d : docs) {
                    if (!d.pasta()) continue;
                    List<Doc> sub = filhos(arvore, d.id);
                    if (temMapa(sub)) importados.add(copiarMapa(arvore, sub, Mapas.nomeSeguro(d.nome)));
                }
            }
        } catch (Exception e) {
            return "Erro a importar: " + e.getMessage();
        }
        if (importados.isEmpty())
            return "Não encontrei nenhum mapa.png nessa pasta (nem nas subpastas).";
        return importados.size() == 1 ? "Importado: " + importados.get(0)
                : "Importados " + importados.size() + " mapas: " + importados;
    }

    private String copiarMapa(Uri arvore, List<Doc> docs, String id) throws java.io.IOException {
        File destino = new File(pastaApp, id);
        if (!destino.isDirectory() && !destino.mkdirs()) throw new java.io.IOException("não consegui criar " + id);
        List<String> feitos = new ArrayList<>();
        for (Doc d : docs) {
            if (d.pasta()) continue;
            for (String alvo : Mapas.FICHEIROS) {
                if (!alvo.equalsIgnoreCase(d.nome)) continue;
                File tmp = new File(destino, alvo + ".tmp");
                try (InputStream in = getContentResolver().openInputStream(
                        DocumentsContract.buildDocumentUriUsingTree(arvore, d.id));
                     OutputStream out = new FileOutputStream(tmp)) {
                    if (in == null) continue;
                    byte[] buf = new byte[1 << 16];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                File fin = new File(destino, alvo);
                if (fin.exists() && !fin.delete()) continue;
                if (tmp.renameTo(fin)) feitos.add(alvo);
            }
        }
        for (String f : Mapas.FICHEIROS)   // não deixar ficheiros de uma versão antiga deste mapa
            if (!feitos.contains(f)) new File(destino, f).delete();
        return id;
    }

    // ------------------------------------------------------------ GPS
    private void pedirGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            ligarGps();
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req != REQ_LOC) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            ligarGps();
        } else {
            gpsEstado = "Sem permissão de localização: dá-a nas definições da app.";
            atualizarTextos();
        }
    }

    private final LocationListener ouvinte = new LocationListener() {
        @Override
        public void onLocationChanged(Location l) {
            aoMudarPosicao(l);
        }

        // Em Android 10 e anteriores estes métodos são obrigatórios
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
            gpsEstado = "GPS ligado, à procura de sinal…";
            atualizarTextos();
        }

        @Override
        public void onProviderDisabled(String provider) {
            gpsEstado = "Liga a Localização (GPS) nas definições!";
            atualizarTextos();
        }
    };

    private final GnssStatus.Callback satelites = new GnssStatus.Callback() {
        @Override
        public void onSatelliteStatusChanged(GnssStatus st) {
            int usados = 0;
            for (int i = 0; i < st.getSatelliteCount(); i++) if (st.usedInFix(i)) usados++;
            satUsados = usados;
            satVistos = st.getSatelliteCount();
            if (eu == null) atualizarTextos();
        }
    };

    @SuppressWarnings({"MissingPermission", "deprecation"})
    private void ligarGps() {
        if (gpsLigado || lm == null) return;
        try {
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                gpsEstado = "Liga a Localização (GPS) nas definições!";
            } else {
                gpsEstado = "À procura de satélites… (fica ao ar livre)";
            }
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, ouvinte, Looper.getMainLooper());
            lm.registerGnssStatusCallback(satelites, ui);
            gpsLigado = true;
            Location ultima = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (ultima != null && System.currentTimeMillis() - ultima.getTime() < 120000) aoMudarPosicao(ultima);
        } catch (SecurityException | IllegalArgumentException e) {
            gpsEstado = "Erro GPS: " + e.getMessage();
        }
        atualizarTextos();
    }

    @SuppressWarnings("deprecation")
    private void pararGps() {
        if (!gpsLigado || lm == null) return;
        try {
            lm.removeUpdates(ouvinte);
            lm.unregisterGnssStatusCallback(satelites);
        } catch (RuntimeException ignored) {
        }
        gpsLigado = false;
    }

    private void aoMudarPosicao(Location l) {
        eu = new double[]{l.getLatitude(), l.getLongitude(),
                l.hasAccuracy() ? l.getAccuracy() : 0, l.hasSpeed() ? l.getSpeed() : 0,
                l.hasBearing() ? l.getBearing() : 0};
        gpsEstado = "GPS ok";
        mapa.setEu(eu);
        if (seguir) mapa.centrar(eu[0], eu[1]);
        atualizarTextos();
    }

    // ------------------------------------------------------------ textos
    private void atualizarTextos() {
        if (meta != null) {
            StringBuilder h = new StringBuilder();
            if (!idAtual().isEmpty()) h.append("<small>Mapa: ").append(Geo.html(idAtual())).append("</small><br>");
            h.append("<font color='#55ee66'>●</font> <b>").append(Geo.html(destNome.length() > 60
                    ? destNome.substring(0, 60) + "…" : destNome)).append("</b>");
            if (eu != null && destino != null) {
                double d = Geo.distancia(eu[0], eu[1], destino[0], destino[1]);
                double r = Geo.rumo(eu[0], eu[1], destino[0], destino[1]);
                h.append("<br><font color='#ff5555'>●</font> Destino a <b>").append(Geo.fmtDist(d))
                        .append("</b> em linha reta, para <b>").append(Geo.cardeal(r)).append("</b> (")
                        .append(String.format(Locale.ROOT, "%.0f°", r)).append(")");
                h.append("<br>").append(String.format(Locale.ROOT, "%.6f, %.6f", eu[0], eu[1]))
                        .append(String.format(Geo.PT, " · ±%.0f m · %.0f km/h", eu[2], eu[3] * 3.6));
                if (!mapa.dentro(eu[0], eu[1]))
                    h.append("<br><font color='#ffaa00'>Estás fora do mapa (seta vermelha na borda).</font>");
            } else {
                h.append("<br>").append(Geo.html(gpsEstado));
                if (satVistos >= 0)
                    h.append(String.format(Geo.PT, "<br><small>satélites: %d visíveis, %d em uso</small>",
                            satVistos, satUsados));
            }
            info.setText(Html.fromHtml(h.toString(), Html.FROM_HTML_MODE_LEGACY));
        }
        atualizarNav();
    }

    private void atualizarNav() {
        Painel pn = Painel.calcular(meta != null, eu == null ? null : new double[]{eu[0], eu[1], eu[2]},
                ruas, navegacao, destino, estadoRuas);
        nav.setVisibility(meta == null ? View.GONE : View.VISIBLE);
        nav.setText(Html.fromHtml(pn.html, Html.FROM_HTML_MODE_LEGACY));
        fundoNav.setColor(CORES[pn.cor]);
    }
}
