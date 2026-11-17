package com.testemunha;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Guarda as provas e os casos numa area privada do app (getFilesDir), fora da Galeria.
 * Nada e enviado para fora: o app nem tem permissao de internet.
 */
final class Store {

    static final int KIND_AUDIO = 0, KIND_VIDEO = 1, KIND_PHOTO = 2;

    static final class Evidence {
        String id;          // nome do arquivo (unico)
        int kind;
        long createdAt;     // epoch ms
        long durationMs;    // audio/video
        long sizeBytes;
        String hash;        // SHA-256 do arquivo
        double lat, lon;    // 0 = sem localizacao
        boolean hasLoc;
        String note;        // descricao escrita pela pessoa
        String caseId;      // "" = sem caso
        int cam = -1;       // 0 = traseira, 1 = frontal, -1 = nao se aplica

        File file(Context c) { return new File(Store.evidenceDir(c), id); }
        String ext() { return kind == KIND_AUDIO ? ".m4a" : kind == KIND_VIDEO ? ".mp4" : ".jpg"; }
    }

    static final class Case {
        String id;
        long createdAt;
        String title;       // ex.: "Assédio no trabalho"
        String location;    // ex.: "Empresa X - setor financeiro"
        String aggressor;   // "Pessoa envolvida"
        String witnesses;
        String description;
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("store", Context.MODE_PRIVATE);
    }

    static File evidenceDir(Context c) {
        File d = new File(c.getFilesDir(), "evidence");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    // ---------------- provas ----------------

    static synchronized List<Evidence> evidences(Context c) {
        List<Evidence> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString("evidence", "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(fromJson(arr.getJSONObject(i)));
        } catch (Exception ignored) {}
        Collections.sort(out, (a, b) -> Long.compare(b.createdAt, a.createdAt));
        return out;
    }

    static synchronized void saveEvidence(Context c, Evidence e) {
        List<Evidence> all = evidences(c);
        boolean found = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(e.id)) { all.set(i, e); found = true; break; }
        }
        if (!found) all.add(e);
        persistEvidence(c, all);
    }

    static synchronized void deleteEvidence(Context c, String id) {
        List<Evidence> all = evidences(c);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(id)) {
                all.get(i).file(c).delete();
                all.remove(i);
                break;
            }
        }
        persistEvidence(c, all);
    }

    static synchronized Evidence evidence(Context c, String id) {
        for (Evidence e : evidences(c)) if (e.id.equals(id)) return e;
        return null;
    }

    static int countForCase(Context c, String caseId) {
        int n = 0;
        for (Evidence e : evidences(c)) if (caseId.equals(e.caseId)) n++;
        return n;
    }

    private static void persistEvidence(Context c, List<Evidence> all) {
        JSONArray arr = new JSONArray();
        for (Evidence e : all) arr.put(toJson(e));
        sp(c).edit().putString("evidence", arr.toString()).apply();
    }

    private static JSONObject toJson(Evidence e) {
        JSONObject o = new JSONObject();
        try {
            o.put("id", e.id); o.put("kind", e.kind); o.put("createdAt", e.createdAt);
            o.put("durationMs", e.durationMs); o.put("sizeBytes", e.sizeBytes);
            o.put("hash", e.hash == null ? "" : e.hash);
            o.put("lat", e.lat); o.put("lon", e.lon); o.put("hasLoc", e.hasLoc);
            o.put("note", e.note == null ? "" : e.note);
            o.put("caseId", e.caseId == null ? "" : e.caseId);
            o.put("cam", e.cam);
        } catch (Exception ignored) {}
        return o;
    }

    private static Evidence fromJson(JSONObject o) {
        Evidence e = new Evidence();
        e.id = o.optString("id"); e.kind = o.optInt("kind"); e.createdAt = o.optLong("createdAt");
        e.durationMs = o.optLong("durationMs"); e.sizeBytes = o.optLong("sizeBytes");
        e.hash = o.optString("hash"); e.lat = o.optDouble("lat", 0); e.lon = o.optDouble("lon", 0);
        e.hasLoc = o.optBoolean("hasLoc"); e.note = o.optString("note"); e.caseId = o.optString("caseId"); e.cam = o.optInt("cam", -1);
        return e;
    }

    // ---------------- casos ----------------

    static synchronized List<Case> cases(Context c) {
        List<Case> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString("cases", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Case cs = new Case();
                cs.id = o.optString("id"); cs.createdAt = o.optLong("createdAt");
                cs.title = o.optString("title"); cs.location = o.optString("location");
                cs.aggressor = o.optString("aggressor"); cs.witnesses = o.optString("witnesses");
                cs.description = o.optString("description");
                out.add(cs);
            }
        } catch (Exception ignored) {}
        Collections.sort(out, (a, b) -> Long.compare(b.createdAt, a.createdAt));
        return out;
    }

    static synchronized void saveCase(Context c, Case cs) {
        List<Case> all = cases(c);
        boolean found = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(cs.id)) { all.set(i, cs); found = true; break; }
        }
        if (!found) all.add(cs);
        JSONArray arr = new JSONArray();
        for (Case x : all) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", x.id); o.put("createdAt", x.createdAt); o.put("title", x.title);
                o.put("location", x.location); o.put("aggressor", x.aggressor);
                o.put("witnesses", x.witnesses); o.put("description", x.description);
            } catch (Exception ignored) {}
            arr.put(o);
        }
        sp(c).edit().putString("cases", arr.toString()).apply();
    }

    static synchronized void deleteCase(Context c, String id) {
        List<Case> all = cases(c);
        all.removeIf(x -> x.id.equals(id));
        JSONArray arr = new JSONArray();
        for (Case x : all) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", x.id); o.put("createdAt", x.createdAt); o.put("title", x.title);
                o.put("location", x.location); o.put("aggressor", x.aggressor);
                o.put("witnesses", x.witnesses); o.put("description", x.description);
            } catch (Exception ignored) {}
            arr.put(o);
        }
        sp(c).edit().putString("cases", arr.toString()).apply();
        // solta as provas do caso apagado
        List<Evidence> ev = evidences(c);
        boolean changed = false;
        for (Evidence e : ev) if (id.equals(e.caseId)) { e.caseId = ""; changed = true; }
        if (changed) persistEvidence(c, ev);
    }

    static Case caseById(Context c, String id) {
        for (Case cs : cases(c)) if (cs.id.equals(id)) return cs;
        return null;
    }

    // ---------------- hash ----------------

    static String sha256(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ---------------- datas ----------------

    static String dateTime(long ms) {
        return new SimpleDateFormat("dd/MM/yyyy  HH:mm:ss", Locale.getDefault()).format(new Date(ms));
    }

    static String dateShort(long ms) {
        return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date(ms));
    }

    static String timeShort(long ms) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ms));
    }

    /** "Foto", "Vídeo (frontal)" etc. */
    static String kindLabel(Evidence e) {
        String k = e.kind == KIND_AUDIO ? "Áudio" : e.kind == KIND_VIDEO ? "Vídeo" : "Foto";
        if (e.kind != KIND_AUDIO && e.cam == 1) k += " (frontal)";
        if (e.kind != KIND_AUDIO && e.cam == 0) k += " (traseira)";
        return k;
    }

    static long totalBytes(Context c) {
        long t = 0;
        for (Evidence e : evidences(c)) t += e.sizeBytes;
        return t;
    }
}
