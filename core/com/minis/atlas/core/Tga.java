package com.minis.atlas.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class Tga {

    private Tga() {}

    public static boolean looksLikeTga(byte[] d) {
        if (d == null || d.length < 18) return false;
        int type = d[2] & 0xFF;
        return type == 2 || type == 3 || type == 10 || type == 11;
    }

    public static byte[] encode32(Img img) {
        int w = img.w, h = img.h;
        ByteArrayOutputStream bo = new ByteArrayOutputStream(18 + w * h * 4);
        bo.write(0);
        bo.write(0);
        bo.write(2);
        for (int i = 0; i < 5; i++) bo.write(0);
        bo.write(0);
        bo.write(0);
        bo.write(0);
        bo.write(0);
        le16(bo, w);
        le16(bo, h);
        bo.write(32);
        bo.write(0x28);
        byte[] out = new byte[18 + w * h * 4];
        System.arraycopy(bo.toByteArray(), 0, out, 0, 18);
        int p = 18;
        for (int i = 0; i < w * h; i++) {
            int c = img.px[i];
            int a = (c >>> 24) & 0xFF;
            if (a == 0) {
                p += 4;
                continue;
            }
            out[p] = (byte) (c & 0xFF);
            out[p + 1] = (byte) ((c >>> 8) & 0xFF);
            out[p + 2] = (byte) ((c >>> 16) & 0xFF);
            out[p + 3] = (byte) a;
            p += 4;
        }
        return out;
    }

    private static void le16(ByteArrayOutputStream bo, int v) {
        bo.write(v & 0xFF);
        bo.write((v >>> 8) & 0xFF);
    }

    public static Img decode(byte[] d) throws IOException {
        if (d == null || d.length < 18) throw new IOException("tga 太短");
        int idLen = d[0] & 0xFF;
        int cmapType = d[1] & 0xFF;
        int type = d[2] & 0xFF;
        int cmapFirst = u16(d, 3), cmapLen = u16(d, 5), cmapDepth = d[7] & 0xFF;
        int w = u16(d, 12), h = u16(d, 14);
        int bpp = d[16] & 0xFF;
        int desc = d[17] & 0xFF;
        if (w <= 0 || h <= 0) throw new IOException("tga 尺寸非法 " + w + "x" + h);
        int p = 18 + idLen;
        if (cmapType == 1 && cmapDepth > 0) {
            p += ((cmapDepth + 7) / 8) * cmapLen;
        }
        boolean rle = type == 10 || type == 11;
        boolean gray = type == 3 || type == 11;
        if (type != 2 && type != 3 && type != 10 && type != 11)
            throw new IOException("不支持的 tga 类型 " + type);
        int bytes = (bpp + 7) / 8;
        if (bytes < 1) throw new IOException("tga bpp 非法 " + bpp);
        int[] px = new int[w * h];
        int total = w * h, idx = 0, n = 0;
        int[] raw = new int[total];
        while (n < total) {
            int count = 0, value = 0;
            if (rle) {
                if (p >= d.length) break;
                int hd = d[p++] & 0xFF;
                count = (hd & 0x7F) + 1;
                if ((hd & 0x80) != 0) {
                    value = p < d.length ? readPx(d, p, bytes, gray) : 0;
                    p += bytes;
                    for (int i = 0; i < count && n < total; i++) raw[n++] = value;
                } else {
                    for (int i = 0; i < count && n < total; i++) {
                        raw[n++] = p < d.length ? readPx(d, p, bytes, gray) : 0;
                        p += bytes;
                    }
                }
            } else {
                while (n < total) {
                    if (p + bytes > d.length) { raw[n++] = 0; p += bytes; continue; }
                    raw[n++] = readPx(d, p, bytes, gray);
                    p += bytes;
                }
            }
            if (count <= 0) break;
        }
        boolean topDown = (desc & 0x20) != 0;
        boolean rightLeft = (desc & 0x10) != 0;
        for (int y = 0; y < h; y++) {
            int sy = topDown ? y : (h - 1 - y);
            for (int x = 0; x < w; x++) {
                int sx = rightLeft ? (w - 1 - x) : x;
                px[sy * w + sx] = raw[y * w + x];
            }
        }
        return Img.of(w, h, px);
    }

    private static int readPx(byte[] d, int p, int bytes, boolean gray) {
        if (gray) {
            int v = bytes >= 2 ? (d[p] & 0xFF) : (d[p] & 0xFF);
            int a = 255;
            if (bytes >= 2) a = d[p + 1] & 0xFF;
            return (a << 24) | (v << 16) | (v << 8) | v;
        }
        int b = d[p] & 0xFF, g = d[p + 1] & 0xFF, r = d[p + 2] & 0xFF;
        int a = bytes >= 4 ? (d[p + 3] & 0xFF) : 255;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int u16(byte[] d, int i) {
        return (d[i] & 0xFF) | ((d[i + 1] & 0xFF) << 8);
    }
}
