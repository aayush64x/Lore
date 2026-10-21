#!/usr/bin/env bash
# Installs the `lore` command so it works from any folder.
#
#   ./scripts/install.sh              build (if needed) and install from this repo
#   ./scripts/install.sh --jar FILE   install a JAR you downloaded
#
# The JAR goes to ~/.lore/lib/lore.jar and the command to ~/.local/bin/lore.
# Set LORE_BIN_DIR to put the command somewhere else.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$HOME/.lore/lib"
BIN_DIR="${LORE_BIN_DIR:-$HOME/.local/bin}"

JAR=""
if [ "${1:-}" = "--jar" ]; then
  JAR="${2:-}"
  if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then
    echo "Error: JAR file not found: ${JAR:-<missing>}" >&2
    exit 1
  fi
elif [ -n "${1:-}" ]; then
  echo "Usage: $0 [--jar FILE]" >&2
  exit 1
fi

if ! command -v java &>/dev/null; then
  echo "Error: Java not found. Install Java 21 or newer first." >&2
  exit 1
fi
JAVA_MAJOR="$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
if [ "$JAVA_MAJOR" -lt 21 ] 2>/dev/null; then
  echo "Error: Java $JAVA_MAJOR found, but Lore needs Java 21 or newer." >&2
  exit 1
fi

if ! command -v git &>/dev/null; then
  echo "Warning: git not found. Lore needs git to record sessions." >&2
fi

find_built_jar() {
  find "$SCRIPT_DIR/../backend/target" -maxdepth 1 -name 'lore-*.jar' -not -name '*-plain.jar' 2>/dev/null | head -1
}

if [ -z "$JAR" ]; then
  JAR="$(find_built_jar)"
  if [ -z "$JAR" ]; then
    if ! command -v mvn &>/dev/null; then
      echo "Error: no built JAR and Maven not found. Install Maven, or use --jar FILE." >&2
      exit 1
    fi
    echo "Building Lore (this takes a minute)..."
    mvn -q -f "$SCRIPT_DIR/../backend/pom.xml" package -DskipTests
    JAR="$(find_built_jar)"
  fi
fi

mkdir -p "$LIB_DIR" "$BIN_DIR"

# Never write into the files a running Lore is using. A running Java program keeps
# reading classes from its JAR; overwriting it in place made `lore ui` crash on
# Ctrl+C (NoClassDefFoundError) and once lost data. So: write a temp file in the
# same folder, then rename it over the old one. The rename swaps which file the
# name points to; a running Lore keeps the old file open until it exits.
JAR_TMP="$LIB_DIR/.lore.jar.tmp.$$"
BIN_TMP="$BIN_DIR/.lore.tmp.$$"
trap 'rm -f "$JAR_TMP" "$BIN_TMP"' EXIT

RUNNING=""
if command -v pgrep &>/dev/null && pgrep -f "$LIB_DIR/lore.jar" &>/dev/null; then
  RUNNING=1
fi

cp "$JAR" "$JAR_TMP"
mv -f "$JAR_TMP" "$LIB_DIR/lore.jar"

cat > "$BIN_TMP" <<EOF
#!/usr/bin/env bash
if ! command -v java &>/dev/null; then
  echo "Error: Java not found. Java 21+ is required." >&2
  exit 1
fi
exec java -jar "$LIB_DIR/lore.jar" "\$@"
EOF
chmod +x "$BIN_TMP"
mv -f "$BIN_TMP" "$BIN_DIR/lore"

echo "Installed: $BIN_DIR/lore"
if [ -n "$RUNNING" ]; then
  echo "Lore is running. It keeps using the old version until you stop it and start it again."
fi

case ":$PATH:" in
  *":$BIN_DIR:"*)
    echo "Done. Run 'lore' from any git project."
    ;;
  *)
    echo
    echo "$BIN_DIR is not on your PATH yet. Add this line to ~/.zshrc or ~/.bashrc:"
    echo
    echo "  export PATH=\"$BIN_DIR:\$PATH\""
    echo
    echo "Then open a new terminal and run 'lore'."
    ;;
esac

echo "To uninstall: rm $BIN_DIR/lore $LIB_DIR/lore.jar"
