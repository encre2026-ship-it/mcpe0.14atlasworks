
import argparse
import os
import struct
import sys
import zipfile
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(os.path.dirname(HERE), "app", "assets")

CANDIDATES = [
    "assets/images/",
    "assets/",
]

def png_to_rgba(data):

    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("不是 PNG")
    pos, w, h, color, depth, interlace, idat = 8, 0, 0, 0, 0, 0, []
    while pos < len(data):
        (ln,) = struct.unpack(">I", data[pos:pos + 4])
        typ = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + ln]
        pos += 12 + ln
        if typ == b"IHDR":
            w, h, depth, color, _comp, _filt, interlace = struct.unpack(">IIBBBBB", body[:13])
        elif typ == b"IDAT":
            idat.append(body)
        elif typ == b"IEND":
            break
    if depth != 8 or interlace != 0:
        raise ValueError("只支持 8bit 非隔行 PNG（得到 depth=%d interlace=%d）" % (depth, interlace))
    ch = {0: 1, 2: 3, 4: 2, 6: 4}.get(color)
    if ch is None:
        raise ValueError("不支持的 PNG color type %d" % color)
    raw = zlib.decompress(b"".join(idat))
    stride = w * ch
    out = bytearray(h * stride)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        f = raw[p]
        p += 1
        cur = bytearray(raw[p:p + stride])
        p += stride
        if f == 1:
            for i in range(ch, stride):
                cur[i] = (cur[i] + cur[i - ch]) & 0xFF
        elif f == 2:
            for i in range(stride):
                cur[i] = (cur[i] + prev[i]) & 0xFF
        elif f == 3:
            for i in range(stride):
                a = cur[i - ch] if i >= ch else 0
                cur[i] = (cur[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif f == 4:
            for i in range(stride):
                a = cur[i - ch] if i >= ch else 0
                b = prev[i]
                c = prev[i - ch] if i >= ch else 0
                pp = a + b - c
                pa, pb, pc = abs(pp - a), abs(pp - b), abs(pp - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                cur[i] = (cur[i] + pr) & 0xFF
        elif f != 0:
            raise ValueError("未知 PNG 过滤类型 %d" % f)
        out[y * stride:(y + 1) * stride] = cur
        prev = cur

    if ch == 4:
        return w, h, bytes(out)

    rgba = bytearray(w * h * 4)
    for i in range(w * h):
        if ch == 3:
            rgba[i * 4:i * 4 + 3] = out[i * 3:i * 3 + 3]
        elif ch == 1:
            v = out[i]
            rgba[i * 4:i * 4 + 3] = bytes((v, v, v))
        elif ch == 2:
            v = out[i * 2]
            rgba[i * 4:i * 4 + 3] = bytes((v, v, v))
            rgba[i * 4 + 3] = out[i * 2 + 1]
        if ch != 2:
            rgba[i * 4 + 3] = 255
    return w, h, bytes(rgba)

def tga_from_rgba(w, h, rgba, bottom_up=True):

    hdr = bytes((0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0)) + struct.pack("<HH", w, h) + bytes((32, 0x08))
    rows = []
    for y in range(h):
        src = rgba[y * w * 4:(y + 1) * w * 4]
        row = bytearray(w * 4)
        row[0::4], row[1::4], row[2::4], row[3::4] = src[2::4], src[1::4], src[0::4], src[3::4]
        rows.append(bytes(row))
    if bottom_up:
        rows.reverse()
    return hdr + b"".join(rows)

def looks_like_png(d):
    return d[:8] == b"\x89PNG\r\n\x1a\n"

def tga_is_plain(d):

    return len(d) > 18 and d[2] == 2 and d[16] in (24, 32) and d[0] == 0

def pick(z, name):
    for pre in CANDIDATES:
        p = pre + name
        if p in z.namelist():
            return p
    for n in z.namelist():
        if n.endswith("/" + name) or n == name:
            return n
    return None

def extract_tga(z, name, dst):
    src = pick(z, name)
    if src is None:
        raise SystemExit("APK 里找不到 %s" % name)
    data = z.read(src)
    if looks_like_png(data):
        w, h, rgba = png_to_rgba(data)
        open(dst, "wb").write(tga_from_rgba(w, h, rgba))
        print("  %-22s %-34s PNG %dx%d -> TGA32" % (name, src, w, h))
    elif tga_is_plain(data):
        open(dst, "wb").write(data)
        print("  %-22s %-34s TGA 原样拷贝 (%d B)" % (name, src, len(data)))
    else:
        raise SystemExit("%s 是压缩/特殊 TGA，请在游戏里用 0.14.3 原版 APK 再试" % src)

def extract_raw(z, name, dst):
    src = pick(z, name)
    if src is None:
        raise SystemExit("APK 里找不到 %s" % name)
    open(dst, "wb").write(z.read(src))
    print("  %-22s %-34s %d B" % (name, src, os.path.getsize(dst)))

def main():
    ap = argparse.ArgumentParser(description="从 0.14.x APK 提取参考图集到 app/assets/")
    ap.add_argument("apk", help="Minecraft Bedrock 0.14.x 的 APK 路径")
    ap.add_argument("--out", default=ASSETS, help="输出目录（默认 app/assets）")
    a = ap.parse_args()

    if not os.path.isfile(a.apk):
        raise SystemExit("找不到 %s" % a.apk)
    os.makedirs(a.out, exist_ok=True)
    print("从 %s 提取参考图集 -> %s" % (a.apk, a.out))
    with zipfile.ZipFile(a.apk) as z:
        extract_tga(z, "terrain-atlas.tga", os.path.join(a.out, "terrain-atlas.tga"))
        extract_raw(z, "terrain.meta", os.path.join(a.out, "terrain.meta"))
        extract_tga(z, "items-opaque.tga", os.path.join(a.out, "items-opaque.tga"))
        extract_raw(z, "items.meta", os.path.join(a.out, "items.meta"))
    print("完成。现在可以 `sh build.sh`。")

if __name__ == "__main__":
    sys.exit(main())
