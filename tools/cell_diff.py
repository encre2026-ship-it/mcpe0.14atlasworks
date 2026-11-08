
import sys
from PIL import Image

a = Image.open(sys.argv[1]).convert("RGBA")
b = Image.open(sys.argv[2]).convert("RGBA")
cell = int(sys.argv[3]) if len(sys.argv) > 3 else 16
pa, pb = a.load(), b.load()
w, h = a.size
bad = 0
worst = []
for cy in range(0, h, cell):
    for cx in range(0, w, cell):
        mx = 0
        tot = 0
        for y in range(cy, min(cy + cell, h)):
            for x in range(cx, min(cx + cell, w)):
                d = max(abs(pa[x, y][i] - pb[x, y][i]) for i in range(4))
                mx = max(mx, d)
                tot += 1 if d else 0
        if mx > 16:
            bad += 1
            worst.append((mx, cx, cy))
worst.sort(reverse=True)
print("格子差异>16 的格子: %d / %d" % (bad, (w // cell) * (h // cell)))
for m in worst[:10]:
    print("   max=%3d at (%d,%d)" % m)
