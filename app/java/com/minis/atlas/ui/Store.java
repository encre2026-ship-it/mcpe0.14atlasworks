package com.minis.atlas.ui;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.minis.atlas.core.Converter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Store implements Converter.Sink, Converter.Readable {

    static final String ROOT_NAME = "图集工坊";

    private final Context ctx;
    private final String base;
    private final boolean media = Build.VERSION.SDK_INT >= 29;
    private final File root;
    private final Map<String, Uri> uris = new LinkedHashMap<>();
    private final Map<String, byte[]> mem = new HashMap<>();
    private final List<Uri> allUris = new ArrayList<>();
    private int bytes;

    Store(Context c, String base) {
        this.ctx = c.getApplicationContext();
        this.base = sanitize(base);
        File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        this.root = new File(new File(dl, ROOT_NAME), this.base);
    }

    static String sanitize(String s) {
        if (s == null) s = "图集";
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if ("\\/:*?\"<>|".indexOf(ch) >= 0) b.append('_');
            else b.append(ch);
        }
        String r = b.toString().trim();
        if (r.isEmpty()) r = "图集";
        return r.length() > 60 ? r.substring(0, 60) : r;
    }

    void clearOld() {
        if (!media) return;
        try {
            ContentResolver cr = ctx.getContentResolver();
            Uri q = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            String sel = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
            String[] args = {"%" + ROOT_NAME + "/" + base + "%"};
            List<Uri> old = new ArrayList<>();
            Cursor c = cr.query(q, new String[]{MediaStore.MediaColumns._ID}, sel, args, null);
            if (c != null) {
                while (c.moveToNext()) {
                    old.add(android.content.ContentUris.withAppendedId(q, c.getLong(0)));
                }
                c.close();
            }
            for (Uri u : old) {
                try { cr.delete(u, null, null); } catch (Exception ignore) { }
            }
        } catch (Exception ignore) { }
    }

    @Override
    public void write(String rel, byte[] data) throws IOException {
        bytes += data.length;
        if (data.length <= (4 << 20)) mem.put(rel, data);
        if (media) writeMedia(rel, data);
        else writeFile(rel, data);
    }

    private void writeFile(String rel, byte[] data) throws IOException {
        File f = new File(root, rel);
        File p = f.getParentFile();
        if (p != null && !p.exists() && !p.mkdirs()) throw new IOException("无法创建目录: " + p);
        FileOutputStream fo = new FileOutputStream(f);
        try { fo.write(data); } finally { fo.close(); }
        uris.put(rel, Uri.fromFile(f));
        allUris.add(uris.get(rel));
    }

    private void writeMedia(String rel, byte[] data) throws IOException {
        String name = rel.substring(rel.lastIndexOf('/') + 1);
        String parent = rel.contains("/") ? rel.substring(0, rel.lastIndexOf('/')) : "";
        String dir = Environment.DIRECTORY_DOWNLOADS + "/" + ROOT_NAME + "/" + base
                + (parent.isEmpty() ? "" : "/" + parent) + "/";
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        cv.put(MediaStore.MediaColumns.MIME_TYPE, mime(name));
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH, dir);
        cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
        ContentResolver cr = ctx.getContentResolver();
        Uri u = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
        if (u == null) throw new IOException("MediaStore 无法创建 " + rel);
        OutputStream os = cr.openOutputStream(u);
        if (os == null) throw new IOException("无法写入 " + rel);
        try { os.write(data); } finally { os.close(); }
        cv.clear();
        cv.put(MediaStore.MediaColumns.IS_PENDING, 0);
        cr.update(u, cv, null, null);
        uris.put(rel, u);
        allUris.add(u);
    }

    static String mime(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".tga")) return "image/x-tga";
        if (n.endsWith(".zip")) return "application/zip";
        if (n.endsWith(".csv")) return "text/csv";
        if (n.endsWith(".json") || n.endsWith(".meta") || n.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

    @Override
    public byte[] read(String rel) {
        byte[] m = mem.get(rel);
        if (m != null) return m;
        Uri u = uris.get(rel);
        if (u == null) return null;
        try {
            java.io.InputStream in = ctx.getContentResolver().openInputStream(u);
            if (in == null) return null;
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int k;
            try {
                while ((k = in.read(buf)) > 0) bo.write(buf, 0, k);
            } finally { in.close(); }
            return bo.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    List<String> names() { return new ArrayList<>(uris.keySet()); }

    Uri uri(String rel) { return uris.get(rel); }
    List<Uri> allUris() { return allUris; }
    boolean isMedia() { return media; }
    String pathHint() { return media ? "Download/" + ROOT_NAME + "/" + base : root.getAbsolutePath(); }
    int bytes() { return bytes; }
    int count() { return allUris.size(); }
}
