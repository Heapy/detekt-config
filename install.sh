#!/usr/bin/env bash
# Installs the Heapy detekt setup into a repository.
#
#   ./install.sh [target-dir]        # target-dir defaults to the current directory
#
# Or straight from GitHub:
#
#   curl -fsSL https://raw.githubusercontent.com/Heapy/detekt-config/main/install.sh | bash
#
# Installed files:
#   detekt.yml                 the shared config
#   plugins/heapy-detekt/      Kotlin Toolchain plugin, registers the 'detekt' check
#   detekt.sh                  standalone runner for repositories without the toolchain
#   .detekt-config-version     commit this install came from
#
# Re-run it to update. Installed files are overwritten, so keep local changes
# out of them — send changes to the detekt-config repository instead.
#
# DETEKT_CONFIG_SRC=<dir> installs from a local checkout instead of GitHub.
set -euo pipefail

REPO="${DETEKT_CONFIG_REPO:-Heapy/detekt-config}"
REF="${DETEKT_CONFIG_REF:-main}"

TARGET="${1:-$PWD}"
if [ ! -d "$TARGET" ]; then
    echo "ERROR: target directory does not exist: $TARGET" >&2
    exit 1
fi
TARGET="$(cd "$TARGET" && pwd)"

TMP=""
cleanup() { [ -n "$TMP" ] && rm -rf "$TMP"; }
trap cleanup EXIT

if [ -n "${DETEKT_CONFIG_SRC:-}" ]; then
    SRC="$(cd "$DETEKT_CONFIG_SRC" && pwd)"
    SHA="$(git -C "$SRC" rev-parse HEAD)"
    # A local checkout can hold uncommitted work, so say so in the stamp.
    git -C "$SRC" diff --quiet HEAD || SHA="$SHA-dirty"
    ORIGIN="$SRC"
else
    # Resolve the commit first, then download that exact commit. Downloading the
    # branch directly would race with anyone pushing to it mid-install.
    SHA="$(git ls-remote "https://github.com/$REPO.git" "$REF" | awk '{print $1}')"
    if [ -z "$SHA" ]; then
        echo "ERROR: cannot resolve $REF in https://github.com/$REPO.git" >&2
        exit 1
    fi
    TMP="$(mktemp -d)"
    SRC="$TMP"
    ORIGIN="https://github.com/$REPO"
    echo "Downloading $REPO at ${SHA:0:12}..."
    curl -fsSL "https://codeload.github.com/$REPO/tar.gz/$SHA" \
        | tar -xzf - -C "$SRC" --strip-components=1
fi

SHORT="${SHA:0:12}"
case "$SHA" in *-dirty) SHORT="$SHORT-dirty" ;; esac

for f in detekt.yml detekt.sh plugins/heapy-detekt; do
    if [ ! -e "$SRC/$f" ]; then
        echo "ERROR: $f missing in the source tree, install aborted" >&2
        exit 1
    fi
done

install -m 644 "$SRC/detekt.yml" "$TARGET/detekt.yml"
install -m 755 "$SRC/detekt.sh" "$TARGET/detekt.sh"

mkdir -p "$TARGET/plugins"
rm -rf "$TARGET/plugins/heapy-detekt"
cp -R "$SRC/plugins/heapy-detekt" "$TARGET/plugins/heapy-detekt"

cat > "$TARGET/.detekt-config-version" <<EOF
repository=$ORIGIN
ref=$REF
commit=$SHA
EOF

cat <<EOF

Installed into $TARGET at commit $SHORT
  detekt.yml
  detekt.sh
  plugins/heapy-detekt/
  .detekt-config-version

Kotlin Toolchain repositories, add to project.yaml:

  modules:
    - //plugins/heapy-detekt
  plugins:
    - //plugins/heapy-detekt

and to every module.yaml that needs the check:

  plugins:
    heapy-detekt: enabled

Then run: ./kotlin check detekt

Other repositories run ./detekt.sh instead, and can delete plugins/heapy-detekt.
EOF
