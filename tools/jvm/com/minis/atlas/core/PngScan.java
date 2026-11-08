package com.minis.atlas.core;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PngScan {
    public static void main(String[] a) throws Exception {
        File root = new File(a[0]);
        List<String> files = new ArrayList<>();
        collect(root, files);
        Collections.sort(files);
        for (String f : files) {
            String rel = f.substring(root.getPath().length() + 1);
            try {
                Img im = Png.decode(Files.readAllBytes(new File(f).toPath()));
                long s = 0;
                for (int v : im.px) s = (s * 31 + v) & 0xFFFFFFFFL;
                System.out.println(rel + "\t" + im.w + "\t" + im.h + "\t" + s);
            } catch (Exception e) {
                System.out.println(rel + "\tERR\t" + e.getMessage());
            }
        }
    }

    static void collect(File d, List<String> out) {
        File[] fs = d.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) collect(f, out);
            else if (f.getName().toLowerCase().endsWith(".png")) out.add(f.getPath());
        }
    }
}
