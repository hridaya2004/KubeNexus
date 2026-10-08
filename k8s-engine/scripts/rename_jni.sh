#!/usr/bin/env bash
set -euo pipefail

AAR_FILE="$1"
LIB_NAME="$2"
SOURCES_JAR="${3:-${AAR_FILE%.aar}-sources.jar}"

die() {
    echo "Error: $*" >&2
    exit 1
}

[ -f "$AAR_FILE" ] || die "$AAR_FILE not found"
[ -f "$SOURCES_JAR" ] || die "$SOURCES_JAR not found; it is needed to rebuild go/Seq.class"

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

unzip -q "$AAR_FILE" -d "$TMP_DIR/aar"

RENAMED=0
for so in "$TMP_DIR"/aar/jni/*/libgojni.so; do
    if [ -f "$so" ]; then
        dir=$(dirname "$so")
        mv "$so" "$dir/lib${LIB_NAME}.so"
        RENAMED=$((RENAMED + 1))
    fi
done
[ "$RENAMED" -gt 0 ] || die "no jni/*/libgojni.so found in $AAR_FILE"

mkdir -p "$TMP_DIR/seq"
unzip -q "$SOURCES_JAR" 'go/Seq.java' -d "$TMP_DIR/seq" || die "go/Seq.java missing from $SOURCES_JAR"
SEQ_JAVA="$TMP_DIR/seq/go/Seq.java"

grep -q 'System.loadLibrary("gojni")' "$SEQ_JAVA" || die "go/Seq.java no longer calls System.loadLibrary(\"gojni\")"
sed "s/System.loadLibrary(\"gojni\")/System.loadLibrary(\"${LIB_NAME}\")/" "$SEQ_JAVA" >"$SEQ_JAVA.patched"
mv "$SEQ_JAVA.patched" "$SEQ_JAVA"
grep -q "System.loadLibrary(\"${LIB_NAME}\")" "$SEQ_JAVA" || die "patching go/Seq.java failed"

ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
ANDROID_JAR=$(find "$ANDROID_HOME/platforms" -name "android.jar" 2>/dev/null | sort -V | tail -n 1)
[ -n "$ANDROID_JAR" ] || die "no android.jar under $ANDROID_HOME/platforms"

javac -nowarn --release 8 -Xlint:-options -cp "$TMP_DIR/aar/classes.jar:${ANDROID_JAR}" "$SEQ_JAVA"
(cd "$TMP_DIR/seq" && jar uf "$TMP_DIR/aar/classes.jar" go/Seq*.class)

(cd "$TMP_DIR/aar" && zip -q -r "$TMP_DIR/repacked.aar" .)
mv "$TMP_DIR/repacked.aar" "$AAR_FILE"
echo "Renamed libgojni.so to lib${LIB_NAME}.so for $RENAMED ABIs inside $AAR_FILE"
