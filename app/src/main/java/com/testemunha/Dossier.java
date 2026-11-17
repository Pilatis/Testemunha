package com.testemunha;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Locale;

/** Gera um PDF com a linha do tempo do caso e a lista de provas (com data, hora, local e hash). */
final class Dossier {
    private Dossier() {}

    private static final int W = 595, H = 842;     // A4 em pontos (72 dpi)
    private static final int M = 48;               // margem
    private static final int LINE = 16;

    static File build(Context c, Store.Case cs) throws Exception {
        List<Store.Evidence> all = Store.evidences(c);
        List<Store.Evidence> items = new java.util.ArrayList<>();
        for (Store.Evidence e : all) if (cs.id.equals(e.caseId)) items.add(e);

        PdfDocument doc = new PdfDocument();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        State st = new State();
        st.page = newPage(doc, st);

        // cabecalho
        p.setColor(Color.parseColor("#B8860B"));
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        p.setTextSize(10);
        st.canvas.drawText("DOSSIÊ DE PROVAS - TESTEMUNHA", M, st.y, p);
        st.y += LINE + 6;

        p.setColor(Color.BLACK);
        p.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD));
        p.setTextSize(20);
        st.canvas.drawText(clip(cs.title, 40), M, st.y, p);
        st.y += LINE + 10;

        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(11);
        p.setColor(Color.parseColor("#444444"));
        line(st, p, "Gerado em: " + Store.dateTime(System.currentTimeMillis()));
        if (nonEmpty(cs.location)) line(st, p, "Local: " + cs.location);
        if (nonEmpty(cs.aggressor)) line(st, p, "Pessoa(s) envolvida(s): " + cs.aggressor);
        if (nonEmpty(cs.witnesses)) line(st, p, "Testemunhas: " + cs.witnesses);
        line(st, p, "Total de provas: " + items.size());
        st.y += 8;

        if (nonEmpty(cs.description)) {
            sectionTitle(st, p, "Relato");
            paragraph(st, p, cs.description);
            st.y += 6;
        }

        sectionTitle(st, p, "Provas (ordem cronológica)");
        // ordena por data crescente
        items.sort((a, b) -> Long.compare(a.createdAt, b.createdAt));
        int n = 1;
        for (Store.Evidence e : items) {
            ensureSpace(doc, st, 140);
            drawEvidence(c, doc, st, p, n++, e);
            st.y += 8;
        }

        // rodape de integridade
        ensureSpace(doc, st, 80);
        sectionTitle(st, p, "Sobre a integridade");
        paragraph(st, p, "Cada prova tem um código SHA-256. Esse código muda se o arquivo for alterado, "
                + "então serve para demonstrar que os arquivos entregues são os originais. Os arquivos de mídia "
                + "acompanham este dossiê.");

        doc.finishPage(st.page);
        File out = new File(shareDir(c), "Dossie_" + safe(cs.title) + ".pdf");
        try (FileOutputStream fos = new FileOutputStream(out)) { doc.writeTo(fos); }
        doc.close();
        return out;
    }

    private static void drawEvidence(Context c, PdfDocument doc, State st, Paint p, int n, Store.Evidence e) {
        p.setColor(Color.parseColor("#111111"));
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(12);
        String kind = Store.kindLabel(e);
        line(st, p, n + ". " + kind + " - " + Store.dateTime(e.createdAt));

        p.setTypeface(Typeface.MONOSPACE);
        p.setTextSize(9);
        p.setColor(Color.parseColor("#555555"));
        line(st, p, "Arquivo: " + e.id + "   (" + Ui.size(e.sizeBytes) + ")");
        if (e.durationMs > 0) line(st, p, "Duração: " + Ui.clock(e.durationMs / 1000));
        if (e.hasLoc) line(st, p, String.format(Locale.US, "Local: %.5f, %.5f", e.lat, e.lon));
        line(st, p, "SHA-256: " + e.hash);

        if (nonEmpty(e.note)) {
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(10);
            p.setColor(Color.parseColor("#333333"));
            paragraph(st, p, "Descrição: " + e.note);
        }
        // miniatura da foto
        if (e.kind == Store.KIND_PHOTO) {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 4;
                Bitmap bm = BitmapFactory.decodeFile(e.file(c).getAbsolutePath(), o);
                if (bm != null) {
                    int tw = 150;
                    int th = (int) (bm.getHeight() * (tw / (float) bm.getWidth()));
                    ensureSpace(doc, st, th + 10);
                    st.canvas.drawBitmap(Bitmap.createScaledBitmap(bm, tw, th, true), M, st.y, null);
                    st.y += th + 6;
                    bm.recycle();
                }
            } catch (Throwable ignored) {}
        }
    }

    // ---------------- helpers de layout ----------------

    private static class State {
        PdfDocument.Page page;
        Canvas canvas;
        int y;
        int pageNo = 0;
    }

    private static PdfDocument.Page newPage(PdfDocument doc, State st) {
        st.pageNo++;
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(W, H, st.pageNo).create();
        PdfDocument.Page page = doc.startPage(info);
        st.canvas = page.getCanvas();
        st.y = M + 10;
        return page;
    }

    private static void ensureSpace(PdfDocument doc, State st, int needed) {
        if (st.y + needed > H - M) {
            doc.finishPage(st.page);
            st.page = newPage(doc, st);
        }
    }

    private static void line(State st, Paint p, String s) {
        st.canvas.drawText(clip(s, lineChars(p)), M, st.y, p);
        st.y += LINE;
    }

    private static void sectionTitle(State st, Paint p, String s) {
        st.y += 6;
        p.setColor(Color.parseColor("#B8860B"));
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        p.setTextSize(10);
        st.canvas.drawText(s.toUpperCase(Locale.ROOT), M, st.y, p);
        st.y += LINE + 2;
        st.canvas.drawLine(M, st.y - LINE + 4, W - M, st.y - LINE + 4, p);
    }

    private static void paragraph(State st, Paint p, String s) {
        p.setTypeface(Typeface.DEFAULT);
        if (p.getTextSize() < 9) p.setTextSize(10);
        int max = lineChars(p);
        for (String wline : wrap(s, max)) {
            st.canvas.drawText(wline, M, st.y, p);
            st.y += LINE;
        }
    }

    private static int lineChars(Paint p) {
        float cw = p.measureText("M");
        return Math.max(20, (int) ((W - 2 * M) / Math.max(1f, cw)));
    }

    private static java.util.List<String> wrap(String s, int max) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (s == null) return out;
        for (String para : s.split("\n")) {
            String cur = "";
            for (String word : para.split(" ")) {
                if ((cur + " " + word).trim().length() > max) {
                    if (!cur.isEmpty()) out.add(cur);
                    cur = word;
                } else {
                    cur = cur.isEmpty() ? word : cur + " " + word;
                }
            }
            out.add(cur);
        }
        return out;
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static boolean nonEmpty(String s) { return s != null && !s.trim().isEmpty(); }

    private static String safe(String s) {
        if (s == null || s.trim().isEmpty()) return "caso";
        return s.replaceAll("[^a-zA-Z0-9]+", "_");
    }

    static File shareDir(Context c) {
        File d = new File(c.getCacheDir(), "share");
        if (!d.exists()) d.mkdirs();
        return d;
    }
}
