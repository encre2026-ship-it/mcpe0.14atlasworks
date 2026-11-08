package com.minis.atlas.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Converter {

    public static final int TILE = 16;

    public interface Logger {
        void log(String line);

        boolean cancelled();
    }

    public interface Decoder {
        Img decode(byte[] data, String name) throws Exception;
    }

    public interface Encoder {
        byte[] encodePng(Img img) throws Exception;
    }

    public interface Sink {
        void write(String relPath, byte[] data) throws Exception;
    }

    public interface Progress {
        void onProgress(int percent, String stage);
    }

    public static final class Opt {
        public int scale = 1;
        public String filter = "nearest";
        public boolean loose = false;
        public boolean emitMeta = false;
        public boolean emitMip = false;
        public boolean emitAssets = true;
        public boolean audit = false;
        public String multi = "skip";
        public int desat = 0;
        public boolean doTerrain = true;
        public boolean doItems = true;
    }

    public static final class MetaEntry {
        public String name = "";
        public final List<double[]> uvs = new ArrayList<>();
    }

    public static final class Result {
        public int terrainMatched, terrainTotal, terrainSkippedMulti;
        public final List<String> missed = new ArrayList<>();
        public final List<String> skippedMulti = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();
        public int itemsFilled, itemsTiles, itemsKept;
        public final List<String> tableMiss = new ArrayList<>();
        public final List<String[]> rows = new ArrayList<>();
        public final List<String> files = new ArrayList<>();
        public int terrainW, terrainH, itemsW, itemsH;
        public boolean cancelled;
    }

    private static final int META_W = 512, META_H = 512;

    private Converter() {}

    @SuppressWarnings("unchecked")
    public static List<MetaEntry> parseMeta(String text) {
        List<MetaEntry> out = new ArrayList<>();
        Object root = Json.parse(text);
        if (!(root instanceof List)) return out;
        for (Object o : (List<Object>) root) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) o;
            MetaEntry e = new MetaEntry();
            Object nm = m.get("name");
            e.name = nm instanceof String ? (String) nm : "";
            Object uvs = m.get("uvs");
            if (uvs instanceof List) {
                for (Object u : (List<Object>) uvs) {
                    if (!(u instanceof List)) continue;
                    List<Object> a = (List<Object>) u;
                    if (a.size() < 4) continue;
                    double[] v = new double[a.size()];
                    for (int i = 0; i < a.size(); i++) v[i] = Json.d(a.get(i));
                    e.uvs.add(v);
                }
            }
            out.add(e);
        }
        return out;
    }

    private static int pyRound(double v) {
        return (int) Math.rint(v);
    }

    static Img prepareTile(Img img, int size, String filt) {
        Img src;
        if (img.w == img.h) {
            src = img;
        } else {
            int s = Math.min(img.w, img.h);
            int l = (img.w - s) / 2, t = (img.h - s) / 2;
            src = img.crop(l, t, l + s, t + s);
        }
        if (src.w == size) return src;
        boolean down = src.w > size;
        boolean nearest = !down && !"box".equals(filt);
        return src.resize(size, size, nearest);
    }

    static Img prepareItem(Img img, int size) {
        Img im = img;
        if (im.w != size || im.h != size) {
            int w = im.w, h = im.h;
            Img pm = new Img(w, h);
            for (int i = 0; i < w * h; i++) {
                int c = im.px[i];
                int a = (c >>> 24) & 0xFF;
                int r = ((c >> 16) & 0xFF) * a / 255;
                int g = ((c >> 8) & 0xFF) * a / 255;
                int b = (c & 0xFF) * a / 255;
                pm.px[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
            boolean down = w > size;
            pm = pm.resize(size, size, !down);
            im = new Img(size, size);
            for (int i = 0; i < size * size; i++) {
                int c = pm.px[i];
                int a = (c >>> 24) & 0xFF;
                if (a == 0) {
                    im.px[i] = 0;
                    continue;
                }
                int r = Math.min(255, ((c >> 16) & 0xFF) * 255 / a);
                int g = Math.min(255, ((c >> 8) & 0xFF) * 255 / a);
                int b = Math.min(255, (c & 0xFF) * 255 / a);
                im.px[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        Img out = new Img(im.w, im.h);
        for (int i = 0; i < im.px.length; i++) {
            int c = im.px[i];
            int a = (c >>> 24) & 0xFF;
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            if (a <= 8) out.px[i] = 0;
            else if (a < 128 && r < 32 && g < 32 && b < 32) out.px[i] = 0;
            else out.px[i] = c;
        }
        return out;
    }

    static Img desaturate(Img img, int percent) {
        if (percent <= 0) return img;
        double p = Math.min(100, Math.max(0, percent)) / 100.0;
        Img out = new Img(img.w, img.h);
        for (int i = 0; i < img.px.length; i++) {
            int c = img.px[i];
            int a = (c >>> 24) & 0xFF;
            if (a == 0) {
                out.px[i] = 0;
                continue;
            }
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            int gray = (int) (0.2126 * r + 0.7152 * g + 0.0722 * b);
            int nr = (int) (r + (gray - r) * p);
            int ng = (int) (g + (gray - g) * p);
            int nb = (int) (b + (gray - b) * p);
            out.px[i] = (a << 24) | (clamp(nr) << 16) | (clamp(ng) << 8) | clamp(nb);
        }
        return out;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    static Img tintKeep(Img tile, Img baseRef, Img baseThis) {
        Img out = tile.copy();
        int n = out.w;
        for (int y = 0; y < n && y < out.h; y++) {
            for (int x = 0; x < n; x++) {
                int i = y * out.w + x;
                int c = out.px[i];
                int na = (c >>> 24) & 0xFF;
                int rr = (baseRef.px[y * baseRef.w + x] >> 16) & 0xFF;
                int rg = (baseRef.px[y * baseRef.w + x] >> 8) & 0xFF;
                int rb = baseRef.px[y * baseRef.w + x] & 0xFF;
                int rA = (baseRef.px[y * baseRef.w + x] >>> 24) & 0xFF;
                int tc = baseThis.px[y * baseThis.w + x];
                int tr = (tc >> 16) & 0xFF, tg = (tc >> 8) & 0xFF, tb = tc & 0xFF;
                int ta = (tc >>> 24) & 0xFF;
                if (rA < 8 || ta < 8) {
                    out.px[i] = (Math.max(na, ta) << 24) | (tr << 16) | (tg << 8) | tb;
                    continue;
                }
                double k = ((tr + tg + tb) + 1.0) / ((rr + rg + rb) + 1.0);
                k = Math.min(Math.max(k, 0.25), 4.0);
                int nr = (int) (((c >> 16) & 0xFF) * k);
                int ng = (int) (((c >> 8) & 0xFF) * k);
                int nb = (int) ((c & 0xFF) * k);
                out.px[i] = (na << 24) | (clamp(nr) << 16) | (clamp(ng) << 8) | clamp(nb);
            }
        }
        return out;
    }

    static int pickCellSize(Img src, int baseCell, int scale, boolean uniform) {

        return Math.max(1, baseCell) * Math.max(1, scale);
    }

    public static Result run(Opt o, Pack pack, String packLabel,
                             Img baseTerrain, List<MetaEntry> tMeta,
                             Img baseItems, List<MetaEntry> iMeta,
                             Decoder dec, Encoder enc, Sink sink,
                             Logger log, Progress prog) throws Exception {
        Result r = new Result();
        int scale = o.scale;
        log.log("图集源  : 内置 0.14.3 参考图集");
        log.log("材质包  : " + packLabel);
        log.log(String.format(Locale.US, "倍率    : %dx   multi: %s   desat: %d", scale, o.multi, o.desat));
        log.log("");

        Pack.Index idx = pack.buildIndex();
        log.log("材质包索引: " + pack.images().size() + " 张图");

        int pct = 5;
        if (o.doTerrain && baseTerrain != null && tMeta != null) {
            prog.onProgress(pct, "方块图集");
            buildTerrain(o, baseTerrain, tMeta, idx, pack, dec, enc, sink, log, r, prog);
            pct = 60;
        }
        if (o.doItems && baseItems != null && iMeta != null) {
            prog.onProgress(pct, "物品图集");
            buildItems(o, baseItems, iMeta, idx, pack, dec, enc, sink, log, r, prog);
        }
        if (log.cancelled()) {
            r.cancelled = true;
            return r;
        }

        prog.onProgress(85, "meta / mip");
        List<String> metaFiles = new ArrayList<>();
        if (o.emitMeta) {
            String[][] kinds = {{"terrain", "terrain-atlas.tga"}, {"items", "items-opaque.tga"}};
            for (String[] k : kinds) {
                List<MetaEntry> src = "terrain".equals(k[0]) ? tMeta : iMeta;
                int aw = "terrain".equals(k[0]) ? r.terrainW : r.itemsW;
                int ah = "terrain".equals(k[0]) ? r.terrainH : r.itemsH;
                if (src == null || aw <= 0) continue;
                String body = scaleMeta(src, o.scale, aw, ah, k[0]);
                String fn = k[0] + ".meta";
                sink.write(fn, body.getBytes("UTF-8"));
                r.files.add(fn);
                metaFiles.add(fn);
                log.log("放大 meta: " + fn + String.format(Locale.US,
                        " (%dx%d 图集, 声明空间按原条目 ×%d)", aw, ah, o.scale));
            }
        }

        List<String> mips = new ArrayList<>();
        if (o.emitMip) {
            byte[] tga = readBack(sink, "terrain-atlas.tga");
            if (tga != null) {
                try {
                    Img t = Tga.decode(tga);
                    for (int lvl = 0; lvl < 4; lvl++) {
                        int mw = t.w >> (lvl + 1), mh = t.h >> (lvl + 1);
                        if (mw < 16 || mh < 8) break;
                        Img m = t.resize(mw, mh, false);
                        String fn = "terrain-atlas_mip" + lvl + ".tga";
                        sink.write(fn, Tga.encode32(m));
                        r.files.add(fn);
                        mips.add(fn);
                    }
                } catch (Exception e) {
                    log.log("mip 生成失败: " + e.getMessage());
                }
            }
            log.log(mips.isEmpty() ? "mip 生成: 无(缺 terrain-atlas.tga 或尺寸太小)"
                    : "mip 生成: " + join(mips, ", "));
        }

        if (o.emitAssets) {
            String[] copies = concat(new String[]{"terrain-atlas.tga", "items-opaque.tga"},
                    metaFiles.toArray(new String[0]), mips.toArray(new String[0]));
            List<String> got = new ArrayList<>();
            for (String f : copies) {
                byte[] d = readBack(sink, f);
                if (d == null) continue;
                sink.write("assets/images/" + f, d);
                got.add(f);
            }
            sink.write("assets/README-安装.txt", ("把 assets/images/ 里的文件成套替换进 APK:\n"
                    + "  图集: terrain-atlas.tga / items-opaque.tga\n"
                    + "  meta: terrain.meta / items.meta (若 emit-meta)\n"
                    + "  mip : terrain-atlas_mip0~3.tga (若 emit-mip)\n"
                    + "0.14 图集尺寸锁死: terrain 512x256 / items 1024x1024。\n").getBytes("UTF-8"));
            log.log("assets 目录: assets/images -> " + join(got, ", "));
        }

        writeCsv(o, sink, r);
        prog.onProgress(100, "完成");
        return r;
    }

    public interface Readable extends Sink {
        byte[] read(String relPath);
    }

    private static byte[] readBack(Sink sink, String rel) {
        if (sink instanceof Readable) return ((Readable) sink).read(rel);
        return null;
    }

    private static void writeCsv(Opt o, Sink sink, Result r) throws Exception {
        StringBuilder b = new StringBuilder("kind,name,slots,source,how\n");
        for (String[] row : r.rows) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) b.append(',');
                b.append(row[i].replace(',', ' '));
            }
            b.append('\n');
        }
        sink.write("分配表.csv", b.toString().getBytes("UTF-8"));
        r.files.add("分配表.csv");
    }

    private static String join(List<String> l, String sep) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) b.append(sep);
            b.append(l.get(i));
        }
        return b.toString();
    }

    private static String[] concat(String[] a, String[] b, String[] c) {
        List<String> l = new ArrayList<>();
        Collections.addAll(l, a);
        Collections.addAll(l, b);
        Collections.addAll(l, c);
        return l.toArray(new String[0]);
    }

    private static void buildTerrain(Opt o, Img base, List<MetaEntry> meta, Pack.Index idx,
                                     Pack pack, Decoder dec, Encoder enc, Sink sink,
                                     Logger log, Result r, Progress prog) throws Exception {
        int bw = base.w, bh = base.h;
        int tw = TILE * o.scale;
        Img canvas = base.resize(bw * o.scale, bh * o.scale, true);
        int matched = 0, total = 0;
        int guard = 0;

        for (MetaEntry item : meta) {
            if (log.cancelled()) {
                r.cancelled = true;
                return;
            }
            String name = item.name;
            if (item.uvs.isEmpty()) continue;
            List<int[]> uvs = new ArrayList<>();
            for (double[] u : item.uvs) {
                double sw = (u.length > 4 && u[4] != 0) ? u[4] : META_W;
                double sh = (u.length > 5 && u[5] != 0) ? u[5] : META_H;
                uvs.add(new int[]{pyRound(u[0] * bw / sw), pyRound(u[1] * bh / sh),
                        pyRound(u[2] * bw / sw), pyRound(u[3] * bh / sh)});
            }
            if (uvs.size() > 1 && "skip".equals(o.multi)) {
                if (idx.find(name, Tables.BLOCK_ALIAS) != null) {
                    r.skippedMulti.add(name + "(" + uvs.size() + "格)");
                    r.terrainSkippedMulti++;
                } else {
                    r.missed.add(name);
                }
                total++;
                continue;
            }
            Pack.Index.Hit hit = idx.find(name, Tables.BLOCK_ALIAS);
            if (hit == null) {
                r.missed.add(name);
                total++;
                continue;
            }
            Img tile;
            try {
                tile = prepareTile(pack.read(hit.path, dec), tw, o.filter);
            } catch (Exception e) {
                r.notes.add(name + ": " + e.getMessage());
                r.missed.add(name);
                total++;
                continue;
            }
            total++;
            matched++;
            if (uvs.size() == 1) {
                int[] uv = uvs.get(0);
                canvas.paste(tile, uv[0] * o.scale, uv[1] * o.scale);
                r.rows.add(new String[]{"terrain", name, "1", "-", hit.how});
                if (o.loose) {
                    sink.write("loose/blocks/" + name + ".png", enc.encodePng(tile));
                    r.files.add("loose/blocks/" + name + ".png");
                }
            } else if ("same".equals(o.multi)) {
                Img base0 = base.crop(uvs.get(0)[0], uvs.get(0)[1], uvs.get(0)[2], uvs.get(0)[3])
                        .resize(tw, tw, true);
                for (int[] uv : uvs) {
                    Img bt = base.crop(uv[0], uv[1], uv[2], uv[3]).resize(tw, tw, true);
                    canvas.paste(tintKeep(tile, base0, bt), uv[0] * o.scale, uv[1] * o.scale);
                }
                r.rows.add(new String[]{"terrain", name, String.valueOf(uvs.size()), "-", hit.how + "/same"});
            } else {
                List<String> files = new ArrayList<>();
                List<String> cands = new ArrayList<>();
                cands.add(name);
                String[] al = Tables.BLOCK_ALIAS.get(name.toLowerCase());
                if (al != null) Collections.addAll(cands, al);
                for (String c : cands) {
                    Pack.Index.Hit h2 = idx.find(c, Tables.BLOCK_ALIAS);
                    if (h2 != null && !files.contains(h2.path)) files.add(h2.path);
                }
                for (int i = 0; i < uvs.size(); i++) {
                    String ref = files.get(Math.min(i, files.size() - 1));
                    try {
                        Img t = prepareTile(pack.read(ref, dec), tw, o.filter);
                        int[] uv = uvs.get(i);
                        canvas.paste(t, uv[0] * o.scale, uv[1] * o.scale);
                    } catch (Exception ignored) {
                    }
                }
                r.rows.add(new String[]{"terrain", name, String.valueOf(uvs.size()), "-",
                        "variants(" + files.size() + "图/" + uvs.size() + "格)"});
            }
            if (++guard % 40 == 0) prog.onProgress(5 + (int) (50.0 * guard / Math.max(1, meta.size())), "方块图集");
        }

        if (o.desat > 0) canvas = desaturate(canvas, o.desat);
        byte[] tga = Tga.encode32(canvas);
        sink.write("terrain-atlas.tga", tga);
        sink.write("terrain-atlas.png", enc.encodePng(canvas));
        r.files.add("terrain-atlas.tga");
        r.files.add("terrain-atlas.png");
        r.terrainMatched = matched;
        r.terrainTotal = total;
        r.terrainW = canvas.w;
        r.terrainH = canvas.h;

        log.log(String.format(Locale.US, "[terrain] %dx%d  %d/%d 对上 (%.0f%%)",
                canvas.w, canvas.h, matched, total, 100.0 * matched / Math.max(total, 1)));
        if (!r.skippedMulti.isEmpty()) {
            log.log("  多格跳过: " + join(head(r.skippedMulti, 10), ", ")
                    + (r.skippedMulti.size() > 10 ? " ..." : ""));
        }
        if (!r.missed.isEmpty()) {
            log.log("  没对上: " + join(head(r.missed, 14), ", ") + (r.missed.size() > 14 ? " ..." : ""));
        }
    }

    private static List<String> head(List<String> l, int n) {
        return l.size() <= n ? l : l.subList(0, n);
    }

    private static void buildItems(Opt o, Img base, List<MetaEntry> meta, Pack.Index idx,
                                   Pack pack, Decoder dec, Encoder enc, Sink sink,
                                   Logger log, Result r, Progress prog) throws Exception {
        int bw = base.w, bh = base.h;
        Img canvas = base.resize(bw * o.scale, bh * o.scale, true);
        int filled = 0, tiles = 0, kept = 0;
        int guard = 0;

        for (MetaEntry item : meta) {
            if (log.cancelled()) {
                r.cancelled = true;
                return;
            }
            String name = item.name;
            if (item.uvs.isEmpty()) continue;
            List<int[]> uvs = new ArrayList<>();
            for (double[] u : item.uvs) {
                double sw = (u.length > 4 && u[4] != 0) ? u[4] : META_W;
                double sh = (u.length > 5 && u[5] != 0) ? u[5] : META_H;
                uvs.add(new int[]{pyRound(u[0] * bw / sw), pyRound(u[1] * bh / sh),
                        pyRound(u[2] * bw / sw), pyRound(u[3] * bh / sh)});
            }
            int n = uvs.size();
            List<Img> imgs = null;
            List<String> hows = null;

            String[][] table = Tables.VARIANTS.get(name);
            if (table != null && table.length == n) {
                List<Img> out = new ArrayList<>();
                List<String> hs = new ArrayList<>();
                boolean ok = true;
                for (String[] entry : table) {
                    String v = entry[0];
                    String[] tpls = new String[entry.length - 1];
                    System.arraycopy(entry, 1, tpls, 0, tpls.length);
                    List<String> pats = new ArrayList<>();
                    String[] als = Tables.V_ALIAS.get(v);
                    if (als == null) als = new String[]{v};
                    for (String al : als)
                        for (String t : tpls) pats.add(t.replace("{v}", al).replace("{c}", al));
                    Img hitImg = null;
                    for (String p : pats) {
                        Pack.Index.Hit h = idx.find(p, Tables.ITEM_ALIAS);
                        if (h != null) {
                            hitImg = pack.read(h.path, dec);
                            break;
                        }
                    }
                    if (hitImg == null) {
                        ok = false;
                        r.tableMiss.add(name + "[" + out.size() + "]=" + v);
                        break;
                    }
                    out.add(hitImg);
                    hs.add("table:" + v);
                }
                if (ok) {
                    imgs = out;
                    hows = hs;
                }
            }

            if (imgs == null && n == 1) {
                Pack.Index.Hit h = idx.find(name, Tables.ITEM_ALIAS);
                if (h != null) {
                    imgs = new ArrayList<>();
                    imgs.add(pack.read(h.path, dec));
                    hows = new ArrayList<>();
                    hows.add(h.how);
                }
            }

            if (imgs == null && n > 1) {
                List<String> cands = new ArrayList<>();
                List<String> candNames = new ArrayList<>();
                candNames.add(name);
                String[] al = Tables.ITEM_ALIAS.get(name.toLowerCase());
                if (al != null) Collections.addAll(candNames, al);
                for (String c : candNames) {
                    Pack.Index.Hit h = idx.find(c, Tables.ITEM_ALIAS);
                    if (h != null && !cands.contains(h.path)) cands.add(h.path);
                }
                if (cands.size() >= n) {
                    imgs = new ArrayList<>();
                    hows = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        imgs.add(pack.read(cands.get(Math.min(i, cands.size() - 1)), dec));
                        hows.add("family");
                    }
                }
            }

            if (imgs == null) {
                kept++;
                r.rows.add(new String[]{"items", name, String.valueOf(n), "-", "保留原版"});
                totalTick(prog, meta, ++guard);
                continue;
            }

            Img base0 = base.crop(uvs.get(0)[0], uvs.get(0)[1], uvs.get(0)[2], uvs.get(0)[3]);
            Set<Img> uniq = Collections.newSetFromMap(new IdentityHashMap<Img, Boolean>());
            uniq.addAll(imgs);

            for (int i = 0; i < n; i++) {
                int[] uv = uvs.get(i);
                Img src = imgs.get(i);
                int baseCell = Math.max(1, uv[2] - uv[0]);
                int cell = pickCellSize(src, baseCell, o.scale, false);
                Img tile = prepareItem(src, cell);
                if (uniq.size() == 1 && n > 1 && Tables.TINT_KEEP.contains(name)) {
                    Img bt = base.crop(uv[0], uv[1], uv[2], uv[3]);
                    tile = tintKeep(tile, base0.resize(bt.w, bt.h, true), bt);
                }
                canvas.paste(tile, uv[0] * o.scale, uv[1] * o.scale);
                r.rows.add(new String[]{"items", name, String.valueOf(n), "-", hows.get(i)});
            }
            filled++;
            tiles += n;

            if (o.loose) {
                if (n == 1) {
                    int baseCell = Math.max(1, uvs.get(0)[2] - uvs.get(0)[0]);
                    int cell = pickCellSize(imgs.get(0), baseCell, o.scale, true);
                    Img t = prepareItem(imgs.get(0), cell);
                    sink.write("loose/items/" + name + ".png", enc.encodePng(t));
                    r.files.add("loose/items/" + name + ".png");
                } else {
                    for (int i = 0; i < n; i++) {
                        int baseCell = Math.max(1, uvs.get(i)[2] - uvs.get(i)[0]);
                        int cell = pickCellSize(imgs.get(i), baseCell, o.scale, true);
                        Img t = prepareItem(imgs.get(i), cell);
                        String rel = String.format(Locale.US, "loose/items/_multi/%s__%02d.png", name, i);
                        sink.write(rel, enc.encodePng(t));
                        r.files.add(rel);
                    }
                }
            }
            totalTick(prog, meta, ++guard);
        }

        if (o.desat > 0) canvas = desaturate(canvas, o.desat);
        sink.write("items-opaque.tga", Tga.encode32(canvas));
        sink.write("items-opaque.png", enc.encodePng(canvas));
        r.files.add("items-opaque.tga");
        r.files.add("items-opaque.png");
        r.itemsFilled = filled;
        r.itemsTiles = tiles;
        r.itemsKept = kept;
        r.itemsW = canvas.w;
        r.itemsH = canvas.h;

        log.log(String.format(Locale.US, "[items] %dx%d  替换 %d 名 / %d 格  保留原版 %d 名",
                canvas.w, canvas.h, filled, tiles, kept));
        if (!r.tableMiss.isEmpty()) {
            log.log("  顺序表缺图: " + join(head(r.tableMiss, 8), ", "));
        }
    }

    private static void totalTick(Progress prog, List<MetaEntry> meta, int guard) {
        if (guard % 40 == 0)
            prog.onProgress(60 + (int) (25.0 * guard / Math.max(1, meta.size())), "物品图集");
    }

    static String scaleMeta(List<MetaEntry> meta, int mul, int aw, int ah, String kind) {
        StringBuilder b = new StringBuilder();
        b.append('[');
        boolean firstItem = true;
        for (MetaEntry e : meta) {
            StringBuilder uv = new StringBuilder();
            boolean firstUv = true;
            for (double[] u : e.uvs) {
                if (u.length < 4) continue;
                double sw = (u.length > 4 && u[4] != 0) ? u[4] : aw;
                double sh = (u.length > 5 && u[5] != 0) ? u[5] : ah;
                int nsw = pyRound(sw * mul), nsh = pyRound(sh * mul);
                int x1 = pyRound(u[0] * aw / sw);
                int y1 = pyRound(u[1] * ah / sh);
                int x2 = pyRound(u[2] * aw / sw);
                int y2 = pyRound(u[3] * ah / sh);
                x1 = Math.max(0, Math.min(x1, aw));
                y1 = Math.max(0, Math.min(y1, ah));
                x2 = Math.max(0, Math.min(x2, aw));
                y2 = Math.max(0, Math.min(y2, ah));
                if (x2 <= x1 || y2 <= y1) continue;
                if (!firstUv) uv.append(',');
                firstUv = false;
                uv.append('[').append(x1).append(',').append(y1).append(',')
                        .append(x2).append(',').append(y2).append(',')
                        .append(nsw).append(',').append(nsh).append(']');
            }
            if (firstUv) continue;
            if (!firstItem) b.append(',');
            firstItem = false;
            b.append("{\"uvs\":[").append(uv).append("],\"name\":\"")
                    .append(Json.esc(e.name)).append("\"}");
        }
        b.append(']');
        return b.toString();
    }
}
