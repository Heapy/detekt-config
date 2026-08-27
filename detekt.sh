#!/usr/bin/env bash
# Heapy detekt runner.
# Downloads the pinned detekt CLI (cached), resolves the shared config, runs detekt.
# Exit code is non-zero when detekt finds issues — usable as a CI gate as-is.
#
# Usage in a consumer repository: copy this file, adjust CONFIG_TAG when upgrading.
# Extra arguments are passed to detekt-cli verbatim (e.g. --input src).
set -euo pipefail

DETEKT_VERSION="2.0.0-alpha.6"
CONFIG_TAG="2.0.0-alpha.6-1"
CONFIG_REPO="Heapy/detekt-config"

CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/heapy-detekt"
CLI_HOME="$CACHE_DIR/detekt-cli-$DETEKT_VERSION"
CLI_BIN="$CLI_HOME/detekt-cli-$DETEKT_VERSION/bin/detekt-cli"

if [ ! -x "$CLI_BIN" ]; then
  echo "Downloading detekt-cli $DETEKT_VERSION..." >&2
  mkdir -p "$CLI_HOME"
  curl -fsSL -o "$CLI_HOME/cli.zip" \
    "https://github.com/detekt/detekt/releases/download/v$DETEKT_VERSION/detekt-cli-$DETEKT_VERSION.zip"
  unzip -q -o "$CLI_HOME/cli.zip" -d "$CLI_HOME"
  rm "$CLI_HOME/cli.zip"
fi

# Config resolution: a detekt.yml next to this script wins (the config repo itself),
# otherwise the tagged config is downloaded from GitHub and cached.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG="$SCRIPT_DIR/detekt.yml"
if [ ! -f "$CONFIG" ]; then
  CONFIG="$CACHE_DIR/detekt-$CONFIG_TAG.yml"
  if [ ! -f "$CONFIG" ]; then
    echo "Downloading Heapy detekt config $CONFIG_TAG..." >&2
    curl -fsSL -o "$CONFIG" \
      "https://raw.githubusercontent.com/$CONFIG_REPO/$CONFIG_TAG/detekt.yml"
  fi
fi

exec "$CLI_BIN" \
  --config "$CONFIG" \
  --excludes "**/build/**,**/.git/**,**/.gradle/**" \
  "$@"
