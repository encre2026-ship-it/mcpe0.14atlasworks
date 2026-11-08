#!/bin/sh
set -e
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}
export PATH="$JAVA_HOME/bin:$PATH"

PROJ=$(cd "$(dirname "$0")" && pwd)
OUT=${OUT:-/tmp/atlas-build}
KS=${KS:-$HOME/.android/debug.keystore}
APK=${APK:-$PROJ/图集工坊.apk}

has_api29() {
  [ -f "$1" ] || return 1
  unzip -l "$1" 2>/dev/null | grep -q 'android/provider/MediaStore\$Downloads\.class'
}

AJAR=""
for c in ${AJAR:-} \
         "$ANDROID_JAR" \
         /tmp/p32/android-12/android.jar \
         /root/android/platforms/android-35/android.jar \
         /tmp/p35/android-35/android.jar \
         /root/dl/android.jar \
         "$ANDROID_HOME/platforms/android-34/android.jar" \
         "$ANDROID_HOME/platforms/android-33/android.jar" \
         /usr/local/android-sdk/platforms/android-34/android.jar; do
  [ -n "$c" ] || continue
  if has_api29 "$c"; then AJAR=$c; break; fi
done

if [ -z "$AJAR" ]; then
  cat >&2 <<'EOF'
找不到可用的 android.jar（需要 API 29 以上，本工程用了 MediaStore.Downloads）。

  curl -LO https://dl.google.com/android/repository/platform-32_r01.zip
  unzip -q platform-32_r01.zip -d /tmp/p32      # 解出 /tmp/p32/android-12/android.jar
  sh build.sh                                   # 会自动找到它

注：文件夹名必须是 android-12，aapt2 2.19 读不了 API 35 的 resources.arsc。
EOF
  exit 1
fi
echo "android.jar = $AJAR"

cd "$PROJ"

if [ ! -f app/assets/terrain-atlas.tga ] || [ ! -f app/assets/items-opaque.tga ] \
   || [ ! -f app/assets/terrain.meta ] || [ ! -f app/assets/items.meta ]; then
  cat >&2 <<'EOF2'
缺少 app/assets/ 里的参考图集（terrain/items + 两个 .meta）。

本仓库不附带 Mojang 的美术资源，请用你自己的 0.14.x APK 生成一次：

    python3 tools/extract_refs.py /path/to/minecraft-0.14.3.apk

EOF2
  exit 1
fi

rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "[1/6] aapt2 compile"
aapt2 compile --dir app/res -o "$OUT/res.zip"

echo "[2/6] aapt2 link"
aapt2 link -o "$OUT/base.apk" -I "$AJAR" \
  --manifest app/AndroidManifest.xml \
  --java "$OUT/gen" -R "$OUT/res.zip" \
  -A app/assets --auto-add-overlay

echo "[3/6] javac"
find core app/java "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -source 8 -target 8 -encoding UTF-8 \
  -classpath "$AJAR" \
  -d "$OUT/classes" @"$OUT/sources.txt" 2>&1 | grep -v 'bootstrap class path' || true
test -f "$OUT/classes/com/minis/atlas/ui/MainActivity.class"

echo "[4/6] d8"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
d8 --release --min-api 24 --lib "$AJAR" --output "$OUT/dex" @"$OUT/classes.txt"

echo "[5/6] pack"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -X -j "$OUT/unsigned.apk" classes.dex)

if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" \
    -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi

echo "[6/6] sign"
apksigner sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey --out "$APK" "$OUT/unsigned.apk"
apksigner verify --print-certs "$APK" | head -4

ls -l "$APK"
echo "MD5 $(md5sum "$APK" | cut -d' ' -f1)"
