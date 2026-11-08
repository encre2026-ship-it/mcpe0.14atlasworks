package com.minis.atlas.core;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

public class Main {

    static final class DirSink implements Converter.Readable {
        final File root;
        final Map<String, byte[]> mem = new LinkedHashMap<>();

        DirSink(File root) {
            this.root = root;
            root.mkdirs();
        }

        @Override
        public void write(String rel, byte[] data) throws Exception {
            mem.put(rel, data);
            File f = new File(root, rel);
            f.getParentFile().mkdirs();
            FileOutputStream fo = new FileOutputStream(f);
            try {
                fo.write(data);
            } finally {
                fo.close();
            }
        }

        @Override
        public byte[] read(String rel) {
            return mem.get(rel);
        }
    }

    static final Converter.Decoder DEC = (data, name) -> {
        if (name.toLowerCase().endsWith(".tga") || Tga.looksLikeTga(data)) return Tga.decode(data);
        if (!"imageio".equals(System.getProperty("codec")) && Png.looksLikePng(data)) return Png.decode(data);
        BufferedImage bi = ImageIO.read(new ByteArrayInputStream(data));
        if (bi == null) throw new IOException("无法解码 " + name);
        int w = bi.getWidth(), h = bi.getHeight();
        int[] px = new int[w * h];
        bi.getRGB(0, 0, w, h, px, 0, w);
        return Img.of(w, h, px);
    };

    static final Converter.Encoder ENC = Png::encode;

    public static void main(String[] args) throws Exception {
        String ref = args[0], packPath = args[1], out = args[2];
        int scale = Integer.parseInt(args[3]);
        String multi = args[4];
        int desat = Integer.parseInt(args[5]);
        boolean loose = args.length > 6 && args[6].equals("1");
        boolean emitMeta = args.length > 7 && args[7].equals("1");
        boolean emitMip = args.length > 8 && args[8].equals("1");

        Img bt = Tga.decode(Files.readAllBytes(new File(ref, "terrain-atlas.tga").toPath()));
        Img bi = Tga.decode(Files.readAllBytes(new File(ref, "items-opaque.tga").toPath()));
        List<Converter.MetaEntry> tm = Converter.parseMeta(
                new String(Files.readAllBytes(new File(ref, "terrain.meta").toPath()), "UTF-8"));
        List<Converter.MetaEntry> im = Converter.parseMeta(
                new String(Files.readAllBytes(new File(ref, "items.meta").toPath()), "UTF-8"));
        System.out.println("meta: terrain=" + tm.size() + " items=" + im.size());

        Converter.Opt o = new Converter.Opt();
        o.scale = scale;
        o.multi = multi;
        o.desat = desat;
        o.loose = loose;
        o.emitMeta = emitMeta;
        o.emitMip = emitMip;

        DirSink sink = new DirSink(new File(out));
        Pack pack = new Pack(new File(packPath));
        long t0 = System.currentTimeMillis();
        Converter.Result r = Converter.run(o, pack, new File(packPath).getName(), bt, tm, bi, im,
                DEC, ENC, sink, new Converter.Logger() {
                    @Override
                    public void log(String line) {
                        System.out.println(line);
                    }

                    @Override
                    public boolean cancelled() {
                        return false;
                    }
                }, (pct, stage) -> {
                });
        pack.close();
        System.out.println("耗时 " + (System.currentTimeMillis() - t0) + " ms");
        System.out.println("输出: " + r.files.size() + " 个文件");
    }
}
