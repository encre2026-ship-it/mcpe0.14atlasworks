
import ast, sys, io

src = open(sys.argv[1], encoding="utf-8").read()
ns = {"__name__": "atlas_mod"}
exec(compile(src, sys.argv[1], "exec"), ns)

want = ["BLOCK_ALIAS", "ITEM_ALIAS", "VARIANTS", "V_ALIAS", "TINT_KEEP",
        "KEEP_ORIGINAL", "COLORS16", "TOOL5", "ARMOR5"]
missing = [k for k in want if k not in ns]
if missing:
    sys.exit("缺少: %s" % missing)
got = {k: ns[k] for k in want}

out = io.StringIO()
w = out.write

def jstr(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"') + '"'

w("package com.minis.atlas.core;\n\n")
w("import java.util.LinkedHashMap;\nimport java.util.Map;\n\n")
w("/** 由 tools/gen_tables.py 从 atlas.py 自动生成，勿手改。 */\n")
w("final class Tables {\n")
w("    private Tables() {}\n\n")

def map_of_strings(name, d):
    w("    static final Map<String, String[]> %s = new LinkedHashMap<>();\n" % name)
    w("    static {\n")
    for k, v in d.items():
        arr = ", ".join(jstr(x) for x in v)
        w("        %s.put(%s, new String[]{%s});\n" % (name, jstr(k), arr))
    w("    }\n\n")

map_of_strings("BLOCK_ALIAS", got["BLOCK_ALIAS"])
map_of_strings("ITEM_ALIAS", got["ITEM_ALIAS"])
map_of_strings("V_ALIAS", got["V_ALIAS"])

w("    static final Map<String, String[][]> VARIANTS = new LinkedHashMap<>();\n")
w("    static {\n")
for k, arr in got["VARIANTS"].items():
    inner = ", ".join("new String[]{%s, %s}" % (jstr(v), ", ".join(jstr(x) for x in tpl))
                      for v, tpl in arr)
    w("        VARIANTS.put(%s, new String[][]{%s});\n" % (jstr(k), inner))
w("    }\n\n")

def set_of(name, coll):
    w("    static final java.util.Set<String> %s = new java.util.LinkedHashSet<>();\n" % name)
    w("    static {\n")
    for x in coll:
        w("        %s.add(%s);\n" % (name, jstr(x)))
    w("    }\n\n")

set_of("TINT_KEEP", got["TINT_KEEP"])
set_of("KEEP_ORIGINAL", got["KEEP_ORIGINAL"])

w("    static final String[] COLORS16 = {%s};\n" % ", ".join(jstr(x) for x in got["COLORS16"]))
w("    static final String[] TOOL5 = {%s};\n" % ", ".join(jstr(x) for x in got["TOOL5"]))
w("    static final String[] ARMOR5 = {%s};\n" % ", ".join(jstr(x) for x in got["ARMOR5"]))
w("}\n")

open(sys.argv[2], "w", encoding="utf-8").write(out.getvalue())
print("生成 %s: %d 字节" % (sys.argv[2], len(out.getvalue())))
print("block=%d item=%d variants=%d" % (len(got["BLOCK_ALIAS"]), len(got["ITEM_ALIAS"]), len(got["VARIANTS"])))
