#!/bin/sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
OUT="$ROOT/.test-classes"
mkdir -p "$OUT"
javac -d "$OUT" "$ROOT/app/src/main/java/com/riderspay/autodriver/FareEngine.java" "$ROOT/app/src/main/java/com/riderspay/autodriver/GeoMath.java" "$ROOT/app/src/test-lite/FareEngineTest.java"
java -cp "$OUT" FareEngineTest
