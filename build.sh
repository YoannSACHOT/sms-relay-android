#!/bin/bash
set -e

# Defaults: customize via env vars if needed
SDK="${ANDROID_SDK:-$HOME/android-sdk}"
BT_VER="${BUILD_TOOLS_VERSION:-36.0.0}"
PLATFORM_VER="${PLATFORM_VERSION:-34}"

BT="$SDK/build-tools/$BT_VER"
ANDROID_JAR="$SDK/platforms/android-$PLATFORM_VER/android.jar"

[ -d "$BT" ] || { echo "ERROR: build-tools not found at $BT"; exit 1; }
[ -f "$ANDROID_JAR" ] || { echo "ERROR: android.jar not found at $ANDROID_JAR"; exit 1; }

mkdir -p build/{classes,dex}

echo "[1/5] aapt2 link manifest"
"$BT/aapt2" link -o build/app-unsigned.apk -I "$ANDROID_JAR" \
  --manifest AndroidManifest.xml \
  --min-sdk-version 24 --target-sdk-version 34

echo "[2/5] javac"
javac --release 11 -d build/classes -classpath "$ANDROID_JAR" \
  -sourcepath src src/io/github/yoannsachot/smsrelay/*.java

echo "[3/5] d8 (Java -> dex)"
"$BT/d8" --output build/dex --lib "$ANDROID_JAR" build/classes/io/github/yoannsachot/smsrelay/*.class

echo "[4/5] zip dex into APK + zipalign"
cd build && zip -j app-unsigned.apk dex/classes.dex >/dev/null && cd ..
"$BT/zipalign" -f 4 build/app-unsigned.apk build/app-aligned.apk

echo "[5/5] sign"
if [ ! -f build/debug.keystore ]; then
  echo "  generating dev keystore"
  keytool -genkey -v -keystore build/debug.keystore -alias smsrelay \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass smsrelay -keypass smsrelay \
    -dname "CN=SmsRelay,O=SmsRelay,C=FR" 2>/dev/null
fi
"$BT/apksigner" sign --ks build/debug.keystore \
  --ks-pass pass:smsrelay --key-pass pass:smsrelay \
  --out build/SmsRelay-signed.apk build/app-aligned.apk 2>&1 | grep -v WARNING || true

echo ""
echo "Built: $(ls -la build/SmsRelay-signed.apk | awk '{print $5}') bytes"
echo "Verify: $BT/apksigner verify build/SmsRelay-signed.apk"
