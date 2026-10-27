#!/bin/bash
set -e
ulimit -v unlimited
source /scratch/work/env.sh

export _JAVA_OPTIONS="-Xmx512m -XX:MaxMetaspaceSize=256m -XX:-UseCompressedClassPointers -Xss1m"
export JAVA_TOOL_OPTIONS=""

P=/scratch/work/proj
OUT=/scratch/work/build
BT=$ANDROID_HOME/build-tools/34.0.0
PLATFORM=$ANDROID_HOME/platforms/android-34/android.jar
MIN=24
TARGET=34

rm -rf "$OUT"
mkdir -p "$OUT/compiled" "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "==> aapt2 compile"
"$BT/aapt2" compile --dir "$P/res" -o "$OUT/compiled/res.zip"

echo "==> aapt2 link"
"$BT/aapt2" link \
  -o "$OUT/base.apk" \
  -I "$PLATFORM" \
  --manifest "$P/AndroidManifest.xml" \
  -R "$OUT/compiled/res.zip" \
  -A "$P/assets" \
  --java "$OUT/gen" \
  --min-sdk-version $MIN \
  --target-sdk-version $TARGET \
  --version-code 1 \
  --version-name "1.0" \
  --auto-add-overlay

echo "==> javac"
find "$P/java" "$OUT/gen" -name "*.java" > "$OUT/sources.txt"
javac -source 8 -target 8 -nowarn -encoding UTF-8 \
  -bootclasspath "$PLATFORM" \
  -classpath "$PLATFORM" \
  -d "$OUT/classes" \
  @"$OUT/sources.txt" 2>&1 | grep -v "^Note:" || true

if [ ! -f "$OUT/classes/com/liquid/reminders/MainActivity.class" ]; then
  echo "COMPILE FAILED"; exit 1
fi

echo "==> d8"
find "$OUT/classes" -name "*.class" > "$OUT/classlist.txt"
"$BT/d8" --release --min-api $MIN --lib "$PLATFORM" \
  --output "$OUT/dex" @"$OUT/classlist.txt"

echo "==> package"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -X "$OUT/unsigned.apk" classes.dex)

echo "==> zipalign"
"$BT/zipalign" -p -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ ! -f /scratch/work/keys/liquid.keystore ]; then
  mkdir -p /scratch/work/keys
  keytool -genkeypair -v \
    -keystore /scratch/work/keys/liquid.keystore \
    -storepass liquidreminders -keypass liquidreminders \
    -alias liquid -keyalg RSA -keysize 2048 -validity 10950 \
    -dname "CN=Liquid Reminders, OU=Apps, O=Liquid, L=Bengaluru, S=Karnataka, C=IN" >/dev/null 2>&1
fi

echo "==> sign"
"$BT/apksigner" sign \
  --ks /scratch/work/keys/liquid.keystore \
  --ks-pass pass:liquidreminders \
  --key-pass pass:liquidreminders \
  --ks-key-alias liquid \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT/Reminders.apk" "$OUT/aligned.apk"

echo "==> verify"
"$BT/apksigner" verify --print-certs "$OUT/Reminders.apk" | head -6
"$BT/aapt2" dump badging "$OUT/Reminders.apk" | grep -E "^package|application-label|sdkVersion|targetSdkVersion|launchable-activity|uses-permission" | head -12
ls -lh "$OUT/Reminders.apk"
