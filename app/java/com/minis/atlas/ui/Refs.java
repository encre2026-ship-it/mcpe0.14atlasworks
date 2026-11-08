package com.minis.atlas.ui;

import android.content.Context;

import com.minis.atlas.core.Converter;
import com.minis.atlas.core.Img;
import com.minis.atlas.core.Tga;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

final class Refs {
    Img terrain, items;
    List<Converter.MetaEntry> tMeta, iMeta;

    private static Refs cached;

    static synchronized Refs load(Context c) throws IOException {
        if (cached != null) return cached;
        Refs r = new Refs();
        r.terrain = Tga.decode(read(c, "terrain-atlas.tga"));
        r.items = Tga.decode(read(c, "items-opaque.tga"));
        r.tMeta = Converter.parseMeta(new String(read(c, "terrain.meta"), "UTF-8"));
        r.iMeta = Converter.parseMeta(new String(read(c, "items.meta"), "UTF-8"));
        cached = r;
        return r;
    }

    static byte[] read(Context c, String name) throws IOException {
        InputStream in = c.getAssets().open(name);
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream(1 << 16);
            byte[] buf = new byte[8192];
            int k;
            while ((k = in.read(buf)) > 0) bo.write(buf, 0, k);
            return bo.toByteArray();
        } finally {
            in.close();
        }
    }
}
