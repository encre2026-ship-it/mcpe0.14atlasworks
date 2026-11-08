package com.minis.atlas.core;

public final class Img {
    public final int w, h;
    public final int[] px;

    public Img(int w, int h) {
        this.w = w;
        this.h = h;
        this.px = new int[w * h];
    }

    public Img(int w, int h, int[] px) {
        this.w = w;
        this.h = h;
        this.px = px;
    }

    public static Img of(int w, int h, int[] px) {
        if (px.length != w * h) throw new IllegalArgumentException("size mismatch");
        return new Img(w, h, px);
    }

    public Img copy() {
        return new Img(w, h, px.clone());
    }

    public int[] size() {
        return new int[]{w, h};
    }

    public Img crop(int l, int t, int r, int b) {
        int nw = Math.max(0, r - l), nh = Math.max(0, b - t);
        Img o = new Img(nw, nh);
        for (int y = 0; y < nh; y++) {
            int sy = t + y;
            if (sy < 0 || sy >= h) continue;
            for (int x = 0; x < nw; x++) {
                int sx = l + x;
                if (sx < 0 || sx >= w) continue;
                o.px[y * nw + x] = px[sy * w + sx];
            }
        }
        return o;
    }

    public void paste(Img s, int dx, int dy) {
        for (int y = 0; y < s.h; y++) {
            int ty = dy + y;
            if (ty < 0 || ty >= h) continue;
            int srow = y * s.w, trow = ty * w;
            for (int x = 0; x < s.w; x++) {
                int tx = dx + x;
                if (tx < 0 || tx >= w) continue;
                px[trow + tx] = s.px[srow + x];
            }
        }
    }

    public Img resize(int nw, int nh, boolean nearest) {
        nw = Math.max(1, nw);
        nh = Math.max(1, nh);
        if (nw == w && nh == h) return copy();
        return nearest ? resizeNearest(nw, nh) : resizeBox(nw, nh);
    }

    public Img resizeNearest(int nw, int nh) {
        Img o = new Img(nw, nh);
        for (int y = 0; y < nh; y++) {
            int sy = (int) ((long) y * h / nh);
            if (sy >= h) sy = h - 1;
            int srow = sy * w, grow = y * nw;
            for (int x = 0; x < nw; x++) {
                int sx = (int) ((long) x * w / nw);
                if (sx >= w) sx = w - 1;
                o.px[grow + x] = px[srow + sx];
            }
        }
        return o;
    }

    public Img resizeBox(int nw, int nh) {
        return resizeBox(nw, nh, true);
    }

    public Img resizeBox(int nw, int nh, boolean premultWeighted) {
        nw = Math.max(1, nw);
        nh = Math.max(1, nh);
        Img o = new Img(nw, nh);
        double sx = (double) w / nw, sy = (double) h / nh;
        for (int y = 0; y < nh; y++) {
            double b0 = y * sy, b1 = (y + 1) * sy;
            for (int x = 0; x < nw; x++) {
                double a0 = x * sx, a1 = (x + 1) * sx;
                double sa = 0, sr = 0, sg = 0, sb = 0, sw = 0;
                int y0 = (int) Math.floor(b0), y1 = (int) Math.ceil(b1);
                int x0 = (int) Math.floor(a0), x1 = (int) Math.ceil(a1);
                for (int yy = y0; yy < y1 && yy < h; yy++) {
                    if (yy < 0) continue;
                    double wy = Math.min(b1, yy + 1) - Math.max(b0, yy);
                    if (wy <= 0) continue;
                    int row = yy * w;
                    for (int xx = x0; xx < x1 && xx < w; xx++) {
                        if (xx < 0) continue;
                        double wx = Math.min(a1, xx + 1) - Math.max(a0, xx);
                        if (wx <= 0) continue;
                        double ww = wx * wy;
                        int c = px[row + xx];
                        int al = (c >>> 24) & 0xFF;
                        double k = premultWeighted ? al * ww : ww;
                        sr += ((c >> 16) & 0xFF) * k;
                        sg += ((c >> 8) & 0xFF) * k;
                        sb += (c & 0xFF) * k;
                        sa += al * ww;
                        sw += ww;
                    }
                }
                if (sw <= 0) continue;
                double den = premultWeighted ? sa : sw;
                if (den <= 0) den = sw;
                int ai = clamp((int) Math.round(sa / sw + 1e-9));
                int r = clamp((int) Math.round(sr / den + 1e-9));
                int g = clamp((int) Math.round(sg / den + 1e-9));
                int b = clamp((int) Math.round(sb / den + 1e-9));
                o.px[y * nw + x] = (ai << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return o;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    public static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }
}
