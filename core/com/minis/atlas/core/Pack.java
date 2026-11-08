package com.minis.atlas.core;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class Pack implements Closeable {

    public static final String[] IMG_EXT = {".png", ".tga", ".jpg", ".jpeg"};

    private final ZipFile zf;
    private final List<String> images = new ArrayList<>();

    public Pack(File f) throws IOException {
        zf = new ZipFile(f);
        List<? extends ZipEntry> es = Collections.list(zf.entries());
        for (ZipEntry e : es) {
            if (e.isDirectory()) continue;
            String n = e.getName();
            if (n.startsWith("__MACOSX")) continue;
            String low = n.toLowerCase();
            for (String ext : IMG_EXT) {
                if (low.endsWith(ext)) {
                    images.add(n);
                    break;
                }
            }
        }
    }

    public List<String> images() {
        return images;
    }

    public Img read(String entry, Converter.Decoder dec) throws Exception {
        ZipEntry e = zf.getEntry(entry);
        if (e == null) throw new IOException("zip 内找不到 " + entry);
        InputStream in = zf.getInputStream(e);
        try {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream((int) Math.max(64, e.getSize()));
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return dec.decode(bo.toByteArray(), entry);
        } finally {
            in.close();
        }
    }

    @Override
    public void close() {
        try {
            zf.close();
        } catch (IOException ignored) {
        }
    }

    public static final class Index {
        public static final class Hit {
            public final String path;
            public final String how;

            Hit(String path, String how) {
                this.path = path;
                this.how = how;
            }
        }

        private final Map<String, Object[]> byName = new LinkedHashMap<>();
        private final Map<String, Object[]> byNorm = new LinkedHashMap<>();
        private final Map<String, String> byBase = new LinkedHashMap<>();

        public void add(String name, String path, int priority) {
            String key = name.toLowerCase();
            Object[] old = byName.get(key);
            if (old == null || priority >= ((Integer) old[1])) byName.put(key, new Object[]{path, priority});
            String n = norm(name);
            old = byNorm.get(n);
            if (old == null || priority >= ((Integer) old[1])) byNorm.put(n, new Object[]{path, priority});
            byBase.putIfAbsent(norm(baseName(path)), path);
        }

        public Hit find(String want, Map<String, String[]> alias) {
            String w = want.toLowerCase();
            Object[] hit = byName.get(w);
            if (hit != null) return new Hit((String) hit[0], "exact");

            Map<String, String[]> table = alias != null ? alias : Tables.BLOCK_ALIAS;
            String[] cands = table.get(w);
            if (cands != null) {
                for (String cand : cands) {
                    String c = cand.toLowerCase();
                    Object[] h = byName.get(c);
                    if (h != null) return new Hit((String) h[0], "alias:" + cand);
                    Object[] h2 = byNorm.get(norm(c));
                    if (h2 != null) return new Hit((String) h2[0], "alias*:" + cand);
                }
            }
            String wn = norm(w);
            Object[] h = byNorm.get(wn);
            if (h != null) return new Hit((String) h[0], "norm");
            for (String suf : new String[]{"_top", "_side", "_all", "_front", "_bottom"}) {
                Object[] h3 = byNorm.get(norm(w + suf));
                if (h3 != null) return new Hit((String) h3[0], "suffix" + suf);
            }
            String best = null;
            double bestR = 0;
            for (Map.Entry<String, Object[]> e : byNorm.entrySet()) {
                double r = SeqRatio.ratio(wn, e.getKey());
                if (r > bestR) {
                    bestR = r;
                    best = (String) e.getValue()[0];
                }
            }
            if (best != null && bestR >= 0.87)
                return new Hit(best, String.format(java.util.Locale.US, "fuzzy:%.2f", bestR));
            return null;
        }
    }

    public Index buildIndex() {
        Index idx = new Index();
        for (String n : images) {
            String low = n.toLowerCase().replace('\\', '/');
            int prio = (low.contains("/blocks/") || low.contains("/items/")) ? 2 : 1;
            idx.add(stripExt(baseName(n)), n, prio);
        }
        return idx;
    }

    public static String baseName(String p) {
        int i = Math.max(p.lastIndexOf('/'), p.lastIndexOf('\\'));
        return i >= 0 ? p.substring(i + 1) : p;
    }

    public static String stripExt(String s) {
        int i = s.lastIndexOf('.');
        return i > 0 ? s.substring(0, i) : s;
    }

    public static String norm(String s) {
        s = s.toLowerCase();
        if (s.startsWith("textures/")) s = s.substring(9);
        else if (s.startsWith("texture/")) s = s.substring(8);
        if (s.startsWith("blocks/")) s = s.substring(7);
        else if (s.startsWith("block/")) s = s.substring(6);
        else if (s.startsWith("items/")) s = s.substring(6);
        else if (s.startsWith("item/")) s = s.substring(5);
        for (String ext : IMG_EXT) {
            if (s.endsWith(ext)) {
                s = s.substring(0, s.length() - ext.length());
                break;
            }
        }
        s = s.replace("minecraft:", "");
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) b.append(c);
        }
        return b.toString();
    }
}
