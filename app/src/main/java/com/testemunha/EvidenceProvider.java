package com.testemunha;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Provedor proprio (sem AndroidX) para compartilhar/abrir arquivos do cofre
 * de forma somente-leitura. So serve arquivos da pasta privada de provas e do cache.
 */
public class EvidenceProvider extends ContentProvider {

    static final String AUTHORITY = "com.testemunha.files";

    static Uri uriFor(File f) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(f.getName()).build();
    }

    static Uri uriForCache(File f) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath("cache").appendPath(f.getName()).build();
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        java.util.List<String> seg = uri.getPathSegments();
        if (seg.isEmpty()) throw new FileNotFoundException();
        File base;
        String name;
        if (seg.size() >= 2 && "cache".equals(seg.get(0))) {
            base = new File(getContext().getCacheDir(), "share");
            name = seg.get(1);
        } else {
            base = Store.evidenceDir(getContext());
            name = seg.get(seg.size() - 1);
        }
        if (name.contains("..") || name.contains("/")) throw new FileNotFoundException();
        File f = new File(base, name);
        if (!f.exists()) throw new FileNotFoundException();
        return f;
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String sel, String[] args, String order) {
        File f;
        try { f = resolve(uri); } catch (FileNotFoundException e) { return null; }
        String[] cols = {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(cols, 1);
        c.addRow(new Object[]{f.getName(), f.length()});
        return c;
    }

    @Override
    public String getType(Uri uri) {
        String n = uri.getLastPathSegment();
        if (n == null) return "application/octet-stream";
        if (n.endsWith(".m4a")) return "audio/mp4";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".jpg")) return "image/jpeg";
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
