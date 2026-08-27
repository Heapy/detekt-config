#!/usr/bin/env bash
# Heapy detekt runner for repositories that do not use the Kotlin Toolchain.
# Downloads the pinned detekt CLI (cached) and runs it with the detekt.yml
# sitting next to this script. Both files are installed by install.sh.
#
# Exit code is non-zero when detekt finds issues — usable as a CI gate as-is.
# Extra arguments are passed to detekt-cli verbatim (e.g. --input src).
set -euo pipefail

DETEKT_VERSION="2.0.0-alpha.6"

CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/heapy-detekt"
CLI_HOME="$CACHE_DIR/detekt-cli-$DETEKT_VERSION"
CLI_BIN="$CLI_HOME/detekt-cli-$DETEKT_VERSION/bin/detekt-cli"

if [ ! -x "$CLI_BIN" ]; then
    echo "Downloading detekt-cli $DETEKT_VERSION..." >&2
    mkdir -p "$CLI_HOME"
    # Download to a temporary name so an interrupted download cannot leave a
    # truncated archive that later runs would treat as complete.
    curl -fsSL -o "$CLI_HOME/cli.zip.part" \
        "https://github.com/detekt/detekt/releases/download/v$DETEKT_VERSION/detekt-cli-$DETEKT_VERSION.zip"
    mv "$CLI_HOME/cli.zip.part" "$CLI_HOME/cli.zip"
    unzip -q -o "$CLI_HOME/cli.zip" -d "$CLI_HOME"
    rm "$CLI_HOME/cli.zip"
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG="$SCRIPT_DIR/detekt.yml"
if [ ! -f "$CONFIG" ]; then
    echo "ERROR: $CONFIG not found. Run install.sh to get it." >&2
    exit 1
fi

exec "$CLI_BIN" \
    --config "$CONFIG" \
    --excludes "**/build/**,**/.git/**,**/.gradle/**" \
    "$@"
