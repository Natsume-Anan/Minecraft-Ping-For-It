#!/usr/bin/env bash
# Local javac check for the two source sets (no Gradle, no network).
# Rebuilt 2026-10-29 after the workspace wipe: MC jars come from
# D:/Gradle/caches/fabric-loom/26.3, every dependency jar from
# D:/Gradle/caches/modules-2 (Fabric Loader / Fabric API modules + libs).
#
# Output (identical to compile-check.ps1):
#   [main] sources=N / errors=N / classes=N / log=...
#   --- first errors (up to 40) ---
#   [client] ...
#   [summary] main errors=N classes=N | client errors=N classes=N
# Exit: 0 no errors, 1 errors, 2 setup problem.

set -u

if [ -n "${COMPILE_CHECK_ROOT:-}" ]; then
  T="$(cd "$COMPILE_CHECK_ROOT" && pwd)"
else
  T="$(cd "$(dirname "$0")/.." && pwd)"
fi
cd "$T" || { echo "compile-check: cannot cd to project root" >&2; exit 2; }
if [ ! -f build.gradle ]; then
  echo "compile-check: not the project root (no build.gradle): $T" >&2
  exit 2
fi
TW="$(pwd -W 2>/dev/null || pwd)"

# --- toolchain ----------------------------------------------------------
JAVAC=""
JAVAC_VER=""
CANDIDATES=()
if [ -n "${JAVA_HOME:-}" ]; then CANDIDATES+=("$JAVA_HOME/bin/javac.exe"); fi
CANDIDATES+=("/c/Program Files/Java/jdk-25/bin/javac.exe")
if command -v javac >/dev/null 2>&1; then CANDIDATES+=("javac"); fi
for cand in "${CANDIDATES[@]}"; do
  if [ "$cand" != "javac" ] && [ ! -x "$cand" ]; then continue; fi
  ver="$("$cand" -version 2>&1 | head -n 1)"
  case "$ver" in
    *25*) JAVAC="$cand"; JAVAC_VER="$ver"; break ;;
  esac
done
if [ -z "$JAVAC" ]; then
  echo "compile-check: JDK 25 javac not found (set JAVA_HOME to a JDK 25)" >&2
  exit 2
fi

# --- classpath ----------------------------------------------------------
OUT_MSYS="tools/.compile-check"
OUT_WIN="$TW/tools/.compile-check"

CP_JARS=()
for mc in minecraft-common.jar minecraft-client.jar minecraft-client-only.jar; do
  if [ -f "/d/Gradle/caches/fabric-loom/26.3/$mc" ]; then
    CP_JARS+=("D:/Gradle/caches/fabric-loom/26.3/$mc")
  else
    echo "compile-check: missing Minecraft jar: D:/Gradle/caches/fabric-loom/26.3/$mc" >&2
    exit 2
  fi
done
dep_count=0
while IFS= read -r jar; do
  CP_JARS+=("$jar")
  dep_count=$((dep_count + 1))
done < <(find /d/Gradle/caches/modules-2/files-2.1 -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" 2>/dev/null | sort | sed "s#^/d/#D:/#")
if [ "$dep_count" -lt 10 ]; then
  echo "compile-check: dependency cache looks empty under D:/Gradle/caches/modules-2" >&2
  exit 2
fi
CP_BASE="$(IFS=";"; echo "${CP_JARS[*]}")"

# --- pass runner --------------------------------------------------------
LAST_ERRORS=0
LAST_CLASSES=0
run_pass() {
  name="$1"; srcdir="$2"; cp="$3"; outdir="$4"; log="$5"
  mkdir -p "$outdir"
  find "$srcdir" -name "*.java" 2>/dev/null | sort > "$OUT_MSYS/src-$name.txt"
  sources="$(wc -l < "$OUT_MSYS/src-$name.txt" | tr -d " ")"
  printf -- "-cp %s\n" "$cp" > "$OUT_MSYS/cp-$name.txt"
  : > "$log"
  echo "[$name] sources=$sources"
  if [ "$sources" -gt 0 ]; then
    JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US" \
      "$JAVAC" --release 25 -proc:none -nowarn -encoding UTF-8 -Xmaxerrs 20000 \
      "@$OUT_WIN/cp-$name.txt" "@$OUT_WIN/src-$name.txt" -d "$outdir" > "$log" 2>&1
    echo "[$name] exit=$?"
  fi
  errors="$(grep -c ": error:" "$log" 2>/dev/null || true)"
  errors="${errors:-0}"
  classes="$(find "$outdir" -name "*.class" 2>/dev/null | wc -l | tr -d " ")"
  echo "[$name] errors=$errors"
  echo "[$name] classes=$classes"
  echo "[$name] log=$TW/$log"
  if [ "$errors" -gt 0 ]; then
    echo "--- first errors (up to 40) ---"
    grep ": error:" "$log" | head -n 40 || true
  fi
  LAST_ERRORS="$errors"
  LAST_CLASSES="$classes"
}

rm -rf "$OUT_MSYS/main" "$OUT_MSYS/client"
mkdir -p "$OUT_MSYS/main" "$OUT_MSYS/client"

JAVAC_SHOW="$(cygpath -w "$JAVAC" 2>/dev/null || echo "$JAVAC")"
echo "compile-check: javac=$JAVAC_SHOW ($JAVAC_VER)"
echo "compile-check: classpath jars=${#CP_JARS[@]} (MC 3 + modules-2 cache)"

run_pass "main" "src/main/java" "$CP_BASE" "$OUT_WIN/main" "$OUT_MSYS/javac-main.log"
MAIN_ERRORS="$LAST_ERRORS"; MAIN_CLASSES="$LAST_CLASSES"

CLIENT_CP="$OUT_WIN/main;$CP_BASE"
run_pass "client" "src/client/java" "$CLIENT_CP" "$OUT_WIN/client" "$OUT_MSYS/javac-client.log"
CLIENT_ERRORS="$LAST_ERRORS"; CLIENT_CLASSES="$LAST_CLASSES"

echo "[summary] main errors=$MAIN_ERRORS classes=$MAIN_CLASSES | client errors=$CLIENT_ERRORS classes=$CLIENT_CLASSES"
if [ "$MAIN_ERRORS" -eq 0 ] && [ "$CLIENT_ERRORS" -eq 0 ]; then
  exit 0
fi
exit 1
