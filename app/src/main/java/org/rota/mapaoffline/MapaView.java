package org.rota.mapaoffline;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.Collections;
import java.util.List;

/** Mostra o mapa.png (Norte para cima) com zoom de dois dedos, o destino e a minha posição. */
public class MapaView extends View {
    private Bitmap mapa;
    private Geo.Meta meta;
    private List<Geo.Ponto> pontos = Collections.emptyList();
    private double[] destino;          // {lat, lon}
    private double[] eu;               // {lat, lon, precisão, velocidade m/s, rumo graus}

    private float s = 1, s0 = 1, tx, ty;
    private float margemTopo, margemFundo = -1;
    private final float dp;

    private final Paint pBitmap = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLetra = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector escala;
    private final GestureDetector gestos;

    public MapaView(Context c) {
        super(c);
        dp = getResources().getDisplayMetrics().density;
        setBackgroundColor(Color.rgb(30, 30, 30));
        pLetra.setTextAlign(Paint.Align.CENTER);
        pLetra.setTypeface(Typeface.DEFAULT_BOLD);
        pLetra.setTextSize(18 * dp);
        escala = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                zoomEm(d.getFocusX(), d.getFocusY(), d.getScaleFactor());
                return true;
            }
        });
        gestos = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                tx -= dx;
                ty -= dy;
                invalidate();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                zoomEm(e.getX(), e.getY(), 2f);
                return true;
            }
        });
    }

    // ------------------------------------------------------------ dados
    void setMapa(Bitmap b, Geo.Meta m, List<Geo.Ponto> pts, double[] dest) {
        mapa = b;
        meta = m;
        pontos = pts != null ? pts : Collections.<Geo.Ponto>emptyList();
        destino = dest;
        verTudo();
    }

    void setEu(double[] posicao) {
        eu = posicao;
        invalidate();
    }

    /** Espaço ocupado pelas caixas de texto (para pôr N e S à vista). */
    void setMargens(float topo, float fundo) {
        margemTopo = topo;
        margemFundo = fundo;
        invalidate();
    }

    boolean temMapa() {
        return mapa != null && meta != null;
    }

    /** Está dentro da área do mapa? */
    boolean dentro(double lat, double lon) {
        if (meta == null) return false;
        double[] q = Geo.paraPx(meta, lat, lon);
        return q[0] >= 0 && q[0] <= meta.imgW && q[1] >= 0 && q[1] <= meta.imgH;
    }

    // ------------------------------------------------------------ vista
    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        verTudo();
    }

    void verTudo() {
        if (meta == null || getWidth() == 0) { invalidate(); return; }
        s0 = (float) Math.min(getWidth() / meta.imgW, getHeight() / meta.imgH);
        s = s0;
        tx = (float) (getWidth() - meta.imgW * s) / 2f;
        ty = (float) (getHeight() - meta.imgH * s) / 2f;
        invalidate();
    }

    void centrar(double lat, double lon) {
        if (meta == null) return;
        if (s < s0 * 2.5f) s = s0 * 3f;
        double[] q = Geo.paraPx(meta, lat, lon);
        tx = (float) (getWidth() / 2.0 - q[0] * s);
        ty = (float) (getHeight() / 2.0 - q[1] * s);
        invalidate();
    }

    private void zoomEm(float fx, float fy, float f) {
        float ns = Math.max(s0, Math.min(s0 * 16f, s * f));
        f = ns / s;
        tx = fx - (fx - tx) * f;
        ty = fy - (fy - ty) * f;
        s = ns;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        escala.onTouchEvent(e);
        if (!escala.isInProgress()) gestos.onTouchEvent(e);
        return true;
    }

    private float sx(double px) {
        return (float) (tx + px * s);
    }

    private float sy(double py) {
        return (float) (ty + py * s);
    }

    private void bola(Canvas c, float x, float y, float r, int cor) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        c.drawCircle(x, y, r + 2.5f * dp, p);
        p.setColor(cor);
        c.drawCircle(x, y, r, p);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (mapa != null && meta != null) {
            c.save();
            c.translate(tx, ty);
            c.scale((float) (s * meta.imgW / mapa.getWidth()), (float) (s * meta.imgH / mapa.getHeight()));
            c.drawBitmap(mapa, 0, 0, pBitmap);
            c.restore();

            // pontos da rota (pequenos, azuis)
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(230, 25, 100, 230));
            for (Geo.Ponto pt : pontos) {
                double[] q = Geo.paraPx(meta, pt.lat, pt.lon);
                c.drawCircle(sx(q[0]), sy(q[1]), 3.5f * dp, p);
            }

            // destino: VERDE
            if (destino != null) {
                double[] q = Geo.paraPx(meta, destino[0], destino[1]);
                bola(c, sx(q[0]), sy(q[1]), 9 * dp, Color.rgb(13, 190, 50));
            }

            // eu: VERMELHO
            if (eu != null) desenharEu(c);
        }
        desenharLetras(c);
    }

    private void desenharEu(Canvas c) {
        double[] q = Geo.paraPx(meta, eu[0], eu[1]);
        float x = sx(q[0]), y = sy(q[1]), r = 9 * dp;
        boolean dentro = q[0] >= 0 && q[0] <= meta.imgW && q[1] >= 0 && q[1] <= meta.imgH;
        if (dentro) {
            float rp = (float) (eu[2] / Geo.metrosPorPx(meta, eu[0]) * s);
            if (rp > r) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb(40, 255, 0, 0));
                c.drawCircle(x, y, rp, p);
            }
            bola(c, x, y, r, Color.rgb(230, 15, 15));
            if (eu[3] > 0.7) {                         // seta da direção do movimento
                double b = Math.toRadians(eu[4]);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3 * dp);
                p.setColor(Color.rgb(230, 15, 15));
                c.drawLine(x, y, x + (float) Math.sin(b) * r * 3, y - (float) Math.cos(b) * r * 3, p);
            }
        } else {
            // fora do mapa: anel vermelho na borda + seta a apontar para onde estou
            float cx = sx(Math.min(Math.max(q[0], 0), meta.imgW));
            float cy = sy(Math.min(Math.max(q[1], 0), meta.imgH));
            double ang = Math.atan2(y - cy, x - cx);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3 * dp);
            p.setColor(Color.rgb(230, 15, 15));
            c.drawCircle(cx, cy, r, p);
            p.setStrokeWidth(4 * dp);
            c.drawLine(cx, cy, cx + (float) Math.cos(ang) * r * 2.5f, cy + (float) Math.sin(ang) * r * 2.5f, p);
        }
    }

    private void desenharLetras(Canvas c) {
        float w = getWidth(), h = getHeight();
        float fundo = margemFundo > 0 ? margemFundo : h;
        float m = 26 * dp, r = 17 * dp;
        float[][] pos = {
                {w / 2, margemTopo + m}, {w / 2, fundo - m},
                {w - m, (margemTopo + fundo) / 2}, {m, (margemTopo + fundo) / 2}};
        String[] letras = {"N", "S", "E", "O"};
        for (int i = 0; i < 4; i++) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(170, 0, 0, 0));
            c.drawCircle(pos[i][0], pos[i][1], r, p);
            pLetra.setColor(i == 0 ? Color.rgb(255, 90, 90) : Color.WHITE);
            Paint.FontMetrics fm = pLetra.getFontMetrics();
            c.drawText(letras[i], pos[i][0], pos[i][1] - (fm.ascent + fm.descent) / 2, pLetra);
        }
    }
}
