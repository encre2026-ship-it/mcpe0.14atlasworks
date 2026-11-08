package com.minis.atlas.core;

import java.nio.file.Files;
import java.nio.file.Paths;

public class PngDbg {
    public static void main(String[] a) throws Exception {
        for (String f : a) {
            byte[] d = Files.readAllBytes(Paths.get(f));
            Img im;
            try {
                im = Png.decode(d);
            } catch (Exception e) {
                System.out.println("ERR " + f + " " + e.getMessage());
                continue;
            }
            long s = 0;
            for (int v : im.px) s = (s * 31 + v) & 0xFFFFFFFFL;
            System.out.println("OK " + im.w + "x" + im.h + " sum=" + s + "  " + f);
        }
    }
}
