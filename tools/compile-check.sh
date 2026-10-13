#!/usr/bin/env bash
# Local CI-equivalent compile check for AnormalClient (Minecraft 1.21.11 / Yarn).
# Compiles every source file against the real merged Minecraft jar using the
# same dependency set Gradle would resolve, so CI breakage is caught locally.
set -u

cd "$(dirname "$0")/.." || exit 1
ROOT="$(pwd)"

# ---------------------------------------------------------------------------
# Guard: duplicate simple class names across packages.
# ModuleManager uses wildcard imports, so two classes with the same simple name
# in different packages make `new Foo()` fail with "reference is ambiguous".
# This happens when a renamed/moved file is copied into a checkout instead of
# the old one being deleted - exactly how a green local build turned into a red
# CI build once already.
# ---------------------------------------------------------------------------
echo "=== duplicate simple class names ==="
python - <<'PY'
import os, collections
names = collections.defaultdict(list)
for root, _dirs, files in os.walk("src/main/java"):
    for f in files:
        if f.endswith(".java"):
            names[f[:-5]].append(os.path.join(root, f).replace("\\", "/"))
dups = {k: v for k, v in names.items() if len(v) > 1}
if not dups:
    print("none")
for k, v in sorted(dups.items()):
    print(f"  !! {k} defined in {len(v)} places:")
    for p in v:
        print("       ", p)
PY
echo ""

JAVAC="/n/Microsoft/jdk-21.0.11.10-hotspot/bin/javac"
MODS="C:/Users/ANORMAL444/.gradle/caches/modules-2/files-2.1"
MCDIR="C:/Users/ANORMAL444/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft"
MCVER="1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2"
MCJAR="$MCDIR/minecraft-merged/$MCVER/minecraft-merged-$MCVER.jar"
MCINT="$MCDIR/minecraft-merged-intermediary/$MCVER/minecraft-merged-intermediary-$MCVER.jar"

pick() { find "$MODS/$1" -name "$2" 2>/dev/null | grep -v sources | grep -v javadoc | sort | tail -1; }

LOADER=$(pick "net.fabricmc/fabric-loader" "fabric-loader-0.18.1.jar")
MIXIN=$(pick "net.fabricmc/sponge-mixin" "sponge-mixin-0.16.5*.jar")
LWJGL=$(pick "org.lwjgl/lwjgl" "lwjgl-3.3.3.jar")
GLFW=$(pick "org.lwjgl/lwjgl-glfw" "lwjgl-glfw-3.3.3.jar")
BRIG=$(pick "com.mojang/brigadier" "*.jar")
DFU=$(pick "com.mojang/datafixerupper" "*.jar")
AUTH=$(pick "com.mojang/authlib" "*.jar")
GSON=$(pick "com.google.code.gson/gson" "*.jar")
GUAVA=$(pick "com.google.guava/guava" "*.jar")
LANG=$(pick "org.apache.commons/commons-lang3" "*.jar")
FAST=$(pick "it.unimi.dsi/fastutil" "*.jar")
SLF=$(pick "org.slf4j/slf4j-api" "*.jar")
LOG=$(pick "com.mojang/logging" "*.jar")
JOML=$(pick "org.joml/joml" "joml-1.10.8.jar")
NETTY=$(find "$MODS/io.netty" -path "*4.2.7.Final*" -name "*.jar" 2>/dev/null | grep -v sources | grep -v javadoc | tr '\n' ';')
FAPI=$(find "$MODS/net.fabricmc.fabric-api" -name "*.jar" 2>/dev/null | grep -v sources | grep -v javadoc | grep -v "1.21.1" | tr '\n' ';')

OUT="$ROOT/build/tmp/compilecheck"
rm -rf "$OUT"; mkdir -p "$OUT"
ARGF="$ROOT/build/tmp/compilecheck_args.txt"
{
  printf -- "-d %s\n-Xmaxerrs 3000\n-nowarn\n-proc:none\n-encoding\nUTF-8\n" "$OUT"
  printf -- "-cp \"%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s\"\n" \
    "$MCJAR" "$MCINT" "$LOADER" "$MIXIN" "$LWJGL" "$GLFW" "$BRIG" "$DFU" "$AUTH" \
    "$GSON" "$GUAVA" "$LANG" "$FAST" "$SLF" "$LOG;$FAPI" "$JOML" "$NETTY"
  find src/main/java -name "*.java"
} > "$ARGF"

"$JAVAC" "@$ARGF" > "$ROOT/build/tmp/compilecheck.log" 2>&1
EXIT=$?

LOG="$ROOT/build/tmp/compilecheck.log"
echo "=== files with errors ==="
grep "error:" "$LOG" | sed 's/:.*//' | sort -u
echo "=== error count ==="
grep -c "error:" "$LOG"
echo "=== non-AnormalClient errors ==="
grep "error:" "$LOG" | grep -v "AnormalClient" || echo "NONE"
echo "=== details (non-AnormalClient) ==="
grep -A4 "error:" "$LOG" | grep -v AnormalClient | head -60
exit $EXIT
