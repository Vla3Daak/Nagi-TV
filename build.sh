#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
TOOLS="${TOOLS:-../tools}"
BT="${ANDROID_BUILD_TOOLS:-$TOOLS/android-15}"
ANDROID_JAR="${ANDROID_JAR:-$TOOLS/android-11/android.jar}"
ECJ="${ECJ_JAR:-$TOOLS/ecj.jar}"
BUILD="${BUILD_DIR:-build}"
python3 tools/materialize.py
mkdir -p "$BUILD/classes" "$BUILD/dex"
"$BT/aapt2" compile --dir res -o "$BUILD/resources.zip"
"$BT/aapt2" link -o "$BUILD/resources.apk" -I "$ANDROID_JAR" --manifest AndroidManifest.xml --java "$BUILD/generated" "$BUILD/resources.zip"
find src "$BUILD/generated" -name '*.java' > "$BUILD/sources.list"
java -jar "$ECJ" -8 -proc:none -nowarn -classpath "$ANDROID_JAR" -d "$BUILD/classes" @"$BUILD/sources.list"
(cd "$BUILD/classes"; zip -q -r ../classes.jar .)
"$BT/d8" --lib "$ANDROID_JAR" --min-api 29 --output "$BUILD/dex" "$BUILD/classes.jar"
cp "$BUILD/resources.apk" "$BUILD/unsigned.apk"
(cd "$BUILD/dex"; zip -q -j ../unsigned.apk classes*.dex)
"$BT/zipalign" -f -p 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
if [[ ! -f private/release.jks || ! -f private/signing.env ]]; then echo 'Dedicated release signing key missing.' >&2; exit 2; fi
set -a; source private/signing.env; set +a
"$BT/apksigner" sign --ks private/release.jks --ks-key-alias nagitv --ks-pass env:NAGI_KEY_PASSWORD --key-pass env:NAGI_KEY_PASSWORD --out "$BUILD/Nagi-TV-v0.1.0.apk" "$BUILD/aligned.apk"
"$BT/apksigner" verify --verbose --print-certs "$BUILD/Nagi-TV-v0.1.0.apk" > "$BUILD/signature-report.txt"
sha256sum "$BUILD/Nagi-TV-v0.1.0.apk"
