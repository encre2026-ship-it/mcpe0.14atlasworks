
import sys
from PIL import Image

a = Image.open(sys.argv[1]).convert("RGBA")
b = Image.open(sys.argv[2]).convert("RGBA")
if a.size != b.size:
    print("尺寸不同", a.size, b.size)
    sys.exit()
pa, pb = a.load(), b.load()
w, h = a.size
n = w * h
diff = 0
maxd = 0
tot = 0
first = []
for y in range(h):
    for x in range(w):
        ca, cb = pa[x, y], pb[x, y]
        if ca != cb:
            diff += 1
            d = max(abs(ca[i] - cb[i]) for i in range(4))
            maxd = max(maxd, d)
            tot += d
            if len(first) < 8:
                first.append((x, y, ca, cb))
print("%s vs %s: %d/%d 像素不同 (%.2f%%), 最大通道差 %d, 平均差 %.2f"
      % (sys.argv[1].split('/')[-2], sys.argv[2].split('/')[-2], diff, n, 100.0 * diff / n, maxd,
         tot / max(1, diff)))
for f in first:
    print("   ", f)
