package com.minis.atlas.core;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import javax.imageio.ImageIO;

public class Dbg {
    public static void main(String[] a) throws Exception {
        File f = new File(a[0]);
        BufferedImage bi = ImageIO.read(f);
        int w = bi.getWidth(), h = bi.getHeight();
        int[] px = new int[w * h];
        bi.getRGB(0, 0, w, h, px, 0, w);
        System.out.println("size " + w + "x" + h + " type=" + bi.getType());
        for (int x = 16; x < 20; x++) {
            int c = px[x];
            System.out.printf("java src (%d,0) a=%d r=%d g=%d b=%d%n", x, (c >>> 24) & 255,
                    (c >> 16) & 255, (c >> 8) & 255, c & 255);
        }
        Img img = Img.of(w, h, px);
        Img pm = new Img(w, h);
        for (int i = 0; i < w * h; i++) {
            int c = img.px[i];
            int al = (c >>> 24) & 0xFF;
            int r = ((c >> 16) & 0xFF) * al / 255;
            int g = ((c >> 8) & 0xFF) * al / 255;
            int b = (c & 0xFF) * al / 255;
            pm.px[i] = (al << 24) | (r << 16) | (g << 8) | b;
        }
        for (int x = 16; x < 20; x++) {
            int c = pm.px[x];
            System.out.printf("java pm  (%d,0) a=%d r=%d g=%d b=%d%n", x, (c >>> 24) & 255,
                    (c >> 16) & 255, (c >> 8) & 255, c & 255);
        }
        Img q = pm.resizeBox(16, 16);
        for (int x = 8; x < 11; x++) {
            int c = q.px[x];
            System.out.printf("java box (%d,0) a=%d r=%d g=%d b=%d%n", x, (c >>> 24) & 255,
                    (c >> 16) & 255, (c >> 8) & 255, c & 255);
        }
    }
}
