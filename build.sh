#!/bin/bash
# Java-only launcher APK (no .so). Installs on Redmi 9A armeabi-v7a 32-bit.
# Works locally and in GitHub Actions when ANDROID_HOME has platform 29 + build-tools.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="${GI_BUILD_DIR:-/tmp/gi-build}"

find_bt() {
  if [ -n "${ANDROID_BUILD_TOOLS:-}" ] && [ -x "${ANDROID_BUILD_TOOLS}/aapt2" ]; then
    echo "$ANDROID_BUILD_TOOLS"
    return
  fi
  if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/build-tools" ]; then
    find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1
    return
  fi
  if [ -x /tmp/android-sdk/android-11/aapt2 ]; then
    echo /tmp/android-sdk/android-11
    return
  fi
  echo ""
}

find_jar() {
  if [ -n "${ANDROID_JAR:-}" ] && [ -f "$ANDROID_JAR" ]; then
    echo "$ANDROID_JAR"
    return
  fi
  if [ -n "${ANDROID_HOME:-}" ] && [ -f "$ANDROID_HOME/platforms/android-29/android.jar" ]; then
    echo "$ANDROID_HOME/platforms/android-29/android.jar"
    return
  fi
  if [ -f /tmp/platform-29/android-10/android.jar ]; then
    echo /tmp/platform-29/android-10/android.jar
    return
  fi
  echo ""
}

BT="$(find_bt)"
JAR="$(find_jar)"
if [ -z "$BT" ] || [ ! -x "$BT/aapt2" ]; then
  echo "aapt2 not found. Set ANDROID_HOME or ANDROID_BUILD_TOOLS." >&2
  exit 1
fi
if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then
  echo "android.jar (API 29) not found. Set ANDROID_HOME or ANDROID_JAR." >&2
  exit 1
fi

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$ROOT/assets/fonts" "$ROOT/dist"

decode_b64() {
  local out="$1"
  local b64="$2"
  if [ ! -f "$out" ] && [ -f "$b64" ]; then
    base64 -d "$b64" > "$out"
  fi
}
decode_b64 "$ROOT/gi.keystore" "$ROOT/gi.keystore.b64"
decode_b64 "$ROOT/assets/fonts/Orbitron.ttf" "$ROOT/assets/fonts/Orbitron.ttf.b64"

FONT="$ROOT/assets/fonts/Orbitron.ttf"
if [ ! -f "$FONT" ] && [ -f /workspace/public/fonts/Orbitron.ttf ]; then
  cp -f /workspace/public/fonts/Orbitron.ttf "$FONT"
fi
if [ ! -f "$FONT" ]; then
  echo "Missing $FONT" >&2
  exit 1
fi

python3 "$ROOT/make_icon.py"

"$BT/aapt2" compile --dir "$ROOT/res" -o "$OUT/res.zip"
"$BT/aapt2" link -I "$JAR" \
  --manifest "$ROOT/AndroidManifest.xml" \
  -A "$ROOT/assets" \
  -o "$OUT/unsigned.apk" \
  --min-sdk-version 21 \
  --target-sdk-version 29 \
  --version-code 2 \
  --version-name 1.0.1 \
  "$OUT/res.zip"

javac --release 8 -encoding UTF-8 -classpath "$JAR" -d "$OUT/classes" \
  $(find "$ROOT/src" -name '*.java')

"$BT/d8" --min-api 21 --lib "$JAR" --output "$OUT" $(find "$OUT/classes" -name '*.class')
( cd "$OUT" && jar uf unsigned.apk classes.dex )

"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

KS="$ROOT/gi.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias gi -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass gilauncher -keypass gilauncher \
    -dname "CN=G.I Launcher, OU=I ko ko, O=GI, C=US"
fi
"$BT/apksigner" sign \
  --ks "$KS" --ks-pass pass:gilauncher --key-pass pass:gilauncher \
  --out "$ROOT/dist/GILauncher.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify "$ROOT/dist/GILauncher.apk"

if [ -d /workspace/public ]; then
  cp -f "$ROOT/dist/GILauncher.apk" /workspace/public/GILauncher.apk
fi
if [ -d /workspace/artifacts ]; then
  cp -f "$ROOT/dist/GILauncher.apk" /workspace/artifacts/GILauncher-redmi9a-armeabi-v7a.apk
fi

ls -lh "$ROOT/dist/GILauncher.apk"
if [ -x "$BT/aapt" ]; then
  "$BT/aapt" dump badging "$ROOT/dist/GILauncher.apk" | head -20
fi
echo "---- native (must be empty) ----"
unzip -l "$ROOT/dist/GILauncher.apk" | grep -E '\.so' || echo "NO_NATIVE_LIBS"
