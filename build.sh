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
rm -rf "$BUILD/res-staged" "$BUILD/classes" "$BUILD/dex" "$BUILD/generated"
mkdir -p "$BUILD/res-staged" "$BUILD/classes" "$BUILD/dex"
python3 - "$BUILD/res-staged" <<'PYRES'
import sys, shutil
from pathlib import Path
out=Path(sys.argv[1])
for p in Path('res').rglob('*'):
    if p.is_file() and p.suffix != '.b64':
        target=out/p.relative_to('res'); target.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(p,target)
# Optional personal API identity is injected only into the local APK, never source control.
identity=Path('private/zerochan-username.txt')
if identity.exists():
    import html
    user=identity.read_text().strip()
    if not user.isascii() or not 1<=len(user)<=80 or any(c in user for c in ['\r','\n',"'",'"','\\']):
        raise SystemExit('Invalid Zerochan username in private build input')
    (out/'values/zerochan_identity.xml').write_text('<?xml version="1.0" encoding="utf-8"?><resources><string name="zerochan_username" translatable="false">'+html.escape(user)+'</string></resources>')
PYRES
"$BT/aapt2" compile --dir "$BUILD/res-staged" -o "$BUILD/resources.zip"
"$BT/aapt2" link -o "$BUILD/resources.apk" -I "$ANDROID_JAR" --manifest AndroidManifest.xml --java "$BUILD/generated" "$BUILD/resources.zip"
find src "$BUILD/generated" -name '*.java' > "$BUILD/sources.list"
java -jar "$ECJ" -8 -proc:none -nowarn -classpath "$ANDROID_JAR" -d "$BUILD/classes" @"$BUILD/sources.list"
rm -f "$BUILD/classes.jar"
(cd "$BUILD/classes"; zip -q -r ../classes.jar .)
"$BT/d8" --lib "$ANDROID_JAR" --min-api 29 --output "$BUILD/dex" "$BUILD/classes.jar"
cp "$BUILD/resources.apk" "$BUILD/unsigned.apk"
(cd "$BUILD/dex"; zip -q -j ../unsigned.apk classes*.dex)
"$BT/zipalign" -f -p 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
if [[ ! -f private/release.jks || ! -f private/signing.env ]]; then echo 'Dedicated release signing key missing.' >&2; exit 2; fi
set -a; source private/signing.env; set +a
"$BT/apksigner" sign --ks private/release.jks --ks-key-alias nagitv --ks-pass env:NAGI_KEY_PASSWORD --key-pass env:NAGI_KEY_PASSWORD --out "$BUILD/Nagi-TV-v0.2.0.apk" "$BUILD/aligned.apk"
"$BT/apksigner" verify --verbose --print-certs "$BUILD/Nagi-TV-v0.2.0.apk" > "$BUILD/signature-report.txt"
sha256sum "$BUILD/Nagi-TV-v0.2.0.apk"
