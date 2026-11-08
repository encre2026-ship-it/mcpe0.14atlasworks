package com.minis.atlas.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class Png {

    private Png() {}

    private static final int[] A7X0 = {0, 4, 0, 2, 0, 1, 0};
    private static final int[] A7Y0 = {0, 0, 4, 0, 2, 0, 1};
    private static final int[] A7DX = {8, 8, 4, 4, 2, 2, 1};
    private static final int[] A7DY = {8, 8, 8, 4, 4, 2, 2};

    public static boolean looksLikePng(byte[] d) {
        return d != null && d.length > 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G';
    }

    public static Img decode(byte[] d) throws IOException {
        if (!looksLikePng(d)) throw new IOException("不是 PNG");
        int p = 8;
        int w = 0, h = 0, depth = 8, color = 6, interlace = 0;
        byte[] plte = null, trns = null;
        ByteArrayOutputStream idat = new ByteArrayOutputStream();
        while (p + 8 <= d.length) {
            int len = be32(d, p);
            String type = new String(d, p + 4, 4, StandardCharsets.US_ASCII);
            int off = p + 8;
            if (off + len > d.length) break;
            if (type.equals("IHDR")) {
                w = be32(d, off);
                h = be32(d, off + 4);
                depth = d[off + 8] & 0xFF;
                color = d[off + 9] & 0xFF;
                interlace = d[off + 12] & 0xFF;
            } else if (type.equals("PLTE")) {
                plte = slice(d, off, len);
            } else if (type.equals("tRNS")) {
                trns = slice(d, off, len);
            } else if (type.equals("IDAT")) {
                idat.write(d, off, len);
            } else if (type.equals("IEND")) {
                break;
            }
            p = off + len + 4;
        }
        if (w <= 0 || h <= 0) throw new IOException("PNG 尺寸非法");
        int ch = channels(color);
        boolean packed = depth < 8;
        int bitsPerPixel = ch * depth;

        double total = 0;
        if (interlace == 0) {
            total = (double) h * (1 + Math.ceil((double) w * bitsPerPixel / 8));
        } else {
            for (int i = 0; i < 7; i++) {
                int pw = (w - A7X0[i] + A7DX[i] - 1) / A7DX[i];
                int ph = (h - A7Y0[i] + A7DY[i] - 1) / A7DY[i];
                if (pw > 0 && ph > 0) total += (double) ph * (1 + Math.ceil((double) pw * bitsPerPixel / 8));
            }
        }
        byte[] raw = inflate(idat.toByteArray(), (int) total + 1);
        int[] px = new int[w * h];
        int off = 0;
        if (interlace == 0) {
            off = decodePass(raw, off, w, h, 0, 0, 1, 1, w, color, depth, plte, trns, px);
        } else {
            for (int i = 0; i < 7 && off < raw.length; i++) {
                int pw = (w - A7X0[i] + A7DX[i] - 1) / A7DX[i];
                int ph = (h - A7Y0[i] + A7DY[i] - 1) / A7DY[i];
                if (pw <= 0 || ph <= 0) continue;
                off = decodePass(raw, off, pw, ph, A7X0[i], A7Y0[i], A7DX[i], A7DY[i], w, color, depth, plte, trns, px);
            }
        }
        return Img.of(w, h, px);
    }

    private static int channels(int color) throws IOException {
        switch (color) {
            case 0: return 1;
            case 2: return 3;
            case 3: return 1;
            case 4: return 2;
            case 6: return 4;
            default: throw new IOException("不支持的颜色类型 " + color);
        }
    }

    private static int decodePass(byte[] raw, int off, int pw, int ph, int x0, int y0, int dx, int dy,
                                  int fullW, int color, int depth, byte[] plte, byte[] trns, int[] px) throws IOException {
        int ch = channels(color);
        int bitsPerPixel = ch * depth;
        int stride = (pw * bitsPerPixel + 7) / 8;
        int bpp = Math.max(1, bitsPerPixel / 8);
        byte[] cur = new byte[stride];
        byte[] prev = new byte[stride];
        for (int y = 0; y < ph; y++) {
            if (off + 1 + stride > raw.length) break;
            int filter = raw[off] & 0xFF;
            System.arraycopy(raw, off + 1, cur, 0, stride);
            off += 1 + stride;
            unfilter(filter, cur, prev, bpp);
            int py = y0 + y * dy;
            for (int x = 0; x < pw; x++) {
                px[py * fullW + x0 + x * dx] = sample(cur, x, pw, color, depth, plte, trns);
            }
            byte[] t = prev;
            prev = cur;
            cur = t;
        }
        return off;
    }

    private static int sample(byte[] row, int x, int w, int color, int depth, byte[] plte, byte[] trns) {
        int r, g, b, a = 255;
        if (depth == 8) {
            if (color == 0) {
                r = g = b = row[x] & 0xFF;
            } else if (color == 2) {
                int i = x * 3;
                r = row[i] & 0xFF;
                g = row[i + 1] & 0xFF;
                b = row[i + 2] & 0xFF;
            } else if (color == 3) {
                int idx = row[x] & 0xFF;
                r = pal(plte, idx, 0);
                g = pal(plte, idx, 1);
                b = pal(plte, idx, 2);
                if (trns != null && idx < trns.length) a = trns[idx] & 0xFF;
            } else if (color == 4) {
                r = g = b = row[x * 2] & 0xFF;
                a = row[x * 2 + 1] & 0xFF;
            } else {
                int i = x * 4;
                r = row[i] & 0xFF;
                g = row[i + 1] & 0xFF;
                b = row[i + 2] & 0xFF;
                a = row[i + 3] & 0xFF;
            }
        } else if (depth == 16) {
            if (color == 0) {
                r = g = b = row[x * 2] & 0xFF;
            } else if (color == 2) {
                int i = x * 6;
                r = row[i] & 0xFF;
                g = row[i + 2] & 0xFF;
                b = row[i + 4] & 0xFF;
            } else if (color == 4) {
                r = g = b = row[x * 4] & 0xFF;
                a = row[x * 4 + 2] & 0xFF;
            } else {
                int i = x * 8;
                r = row[i] & 0xFF;
                g = row[i + 2] & 0xFF;
                b = row[i + 4] & 0xFF;
                a = row[i + 6] & 0xFF;
            }
        } else {

            int perByte = 8 / depth;
            int byteIdx = x / perByte;
            int shift = 8 - depth * (x % perByte + 1);
            int mask = (1 << depth) - 1;
            int v = ((row[byteIdx] & 0xFF) >> shift) & mask;
            if (color == 3) {
                r = pal(plte, v, 0);
                g = pal(plte, v, 1);
                b = pal(plte, v, 2);
                if (trns != null && v < trns.length) a = trns[v] & 0xFF;
            } else {

                r = g = b = (int) Math.round(v * 255.0 / mask);
            }
        }
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int pal(byte[] plte, int idx, int comp) {
        if (plte == null) return 0;
        int i = idx * 3 + comp;
        return i < plte.length ? (plte[i] & 0xFF) : 0;
    }

    private static void unfilter(int f, byte[] cur, byte[] prev, int bpp) {
        int n = cur.length;
        switch (f) {
            case 1:
                for (int i = bpp; i < n; i++) cur[i] = (byte) (cur[i] + cur[i - bpp]);
                break;
            case 2:
                for (int i = 0; i < n; i++) cur[i] = (byte) (cur[i] + prev[i]);
                break;
            case 3:
                for (int i = 0; i < n; i++) {
                    int l = i >= bpp ? (cur[i - bpp] & 0xFF) : 0;
                    cur[i] = (byte) (cur[i] + ((l + (prev[i] & 0xFF)) >> 1));
                }
                break;
            case 4:
                for (int i = 0; i < n; i++) {
                    int a = i >= bpp ? (cur[i - bpp] & 0xFF) : 0;
                    int b = prev[i] & 0xFF;
                    int c = i >= bpp ? (prev[i - bpp] & 0xFF) : 0;
                    cur[i] = (byte) (cur[i] + paeth(a, b, c));
                }
                break;
            default:
                break;
        }
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
        if (pa <= pb && pa <= pc) return a;
        if (pb <= pc) return b;
        return c;
    }

    private static byte[] inflate(byte[] in, int hint) throws IOException {
        Inflater inf = new Inflater();
        try {
            inf.setInput(in);
            ByteArrayOutputStream bo = new ByteArrayOutputStream(Math.max(64, Math.min(hint, 1 << 26)));
            byte[] buf = new byte[65536];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                bo.write(buf, 0, n);
            }
            return bo.toByteArray();
        } catch (DataFormatException e) {
            throw new IOException("PNG 数据损坏: " + e.getMessage());
        } finally {
            inf.end();
        }
    }

    public static byte[] encode(Img img) {
        int w = img.w, h = img.h;
        int stride = w * 4;
        byte[] raw = new byte[(stride + 1) * h];
        for (int y = 0; y < h; y++) {
            int o = y * (stride + 1);
            raw[o] = 0;
            for (int x = 0; x < w; x++) {
                int c = img.px[y * w + x];
                int i = o + 1 + x * 4;
                raw[i] = (byte) ((c >>> 16) & 0xFF);
                raw[i + 1] = (byte) ((c >>> 8) & 0xFF);
                raw[i + 2] = (byte) (c & 0xFF);
                raw[i + 3] = (byte) ((c >>> 24) & 0xFF);
            }
        }
        Deflater def = new Deflater(6);
        def.setInput(raw);
        def.finish();
        ByteArrayOutputStream z = new ByteArrayOutputStream(raw.length / 3 + 64);
        byte[] buf = new byte[65536];
        while (!def.finished()) z.write(buf, 0, def.deflate(buf));
        def.end();

        ByteArrayOutputStream out = new ByteArrayOutputStream(z.size() + 128);
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, 0, 8);
        byte[] ihdr = new byte[13];
        put32(ihdr, 0, w);
        put32(ihdr, 4, h);
        ihdr[8] = 8;
        ihdr[9] = 6;
        chunk(out, "IHDR", ihdr);
        chunk(out, "IDAT", z.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] head = new byte[8];
        put32(head, 0, data.length);
        for (int i = 0; i < 4; i++) head[4 + i] = (byte) type.charAt(i);
        out.write(head, 0, 8);
        out.write(data, 0, data.length);
        CRC32 crc = new CRC32();
        crc.update(head, 4, 4);
        crc.update(data, 0, data.length);
        byte[] tail = new byte[4];
        put32(tail, 0, (int) crc.getValue());
        out.write(tail, 0, 4);
    }

    private static void put32(byte[] b, int o, int v) {
        b[o] = (byte) (v >>> 24);
        b[o + 1] = (byte) (v >>> 16);
        b[o + 2] = (byte) (v >>> 8);
        b[o + 3] = (byte) v;
    }

    private static int be32(byte[] b, int o) {
        return ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }

    private static byte[] slice(byte[] d, int off, int len) {
        byte[] r = new byte[len];
        System.arraycopy(d, off, r, 0, Math.min(len, d.length - off));
        return r;
    }
}
