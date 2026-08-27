#!/usr/bin/env bash
# Installs the Heapy detekt setup into a repository.
#
#   ./install.sh [target-dir]        # target-dir defaults to the current directory
#
# Or straight from GitHub:
#
#   curl -fsSL https://raw.githubusercontent.com/Heapy/detekt-config/main/install.sh | bash
#
# Always installed:
#   detekt.yml                 the shared config
#   .detekt-config-version     commit this install came from
#
# Installed for Kotlin Toolchain repositories only:
#   plugins/heapy-detekt/      the toolchain plugin, registers the 'detekt' check
#
# Gradle repositories need no plugin from us — detekt ships its own Gradle plugin.
# The build system is detected from the files in the target directory; override it
# with DETEKT_CONFIG_KIND=ktc or DETEKT_CONFIG_KIND=gradle.
#
# Re-run it to update. Installed files are overwritten, so keep local changes
# out of them — send changes to the detekt-config repository instead.
#
# DETEKT_CONFIG_SRC=<dir> installs from a local checkout instead of GitHub.
set -euo pipefail

REPO="${DETEKT_CONFIG_REPO:-Heapy/detekt-config}"
REF="${DETEKT_CONFIG_REF:-main}"
DETEKT_VERSION="2.0.0-alpha.6"

TARGET="${1:-$PWD}"
if [ ! -d "$TARGET" ]; then
    echo "ERROR: target directory does not exist: $TARGET" >&2
    exit 1
fi
TARGET="$(cd "$TARGET" && pwd)"

KIND="${DETEKT_CONFIG_KIND:-}"
if [ -z "$KIND" ]; then
    if [ -f "$TARGET/project.yaml" ] || [ -f "$TARGET/module.yaml" ]; then
        KIND=ktc
    elif [ -f "$TARGET/settings.gradle.kts" ] || [ -f "$TARGET/settings.gradle" ] \
        || [ -f "$TARGET/build.gradle.kts" ] || [ -f "$TARGET/build.gradle" ]; then
        KIND=gradle
    else
        KIND=unknown
    fi
fi
case "$KIND" in
    ktc | gradle | unknown) ;;
    *)
        echo "ERROR: DETEKT_CONFIG_KIND must be 'ktc' or 'gradle', got: $KIND" >&2
        exit 1
        ;;
esac

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

for f in detekt.yml plugins/heapy-detekt; do
    if [ ! -e "$SRC/$f" ]; then
        echo "ERROR: $f missing in the source tree, install aborted" >&2
        exit 1
    fi
done

install -m 644 "$SRC/detekt.yml" "$TARGET/detekt.yml"
INSTALLED="  detekt.yml"

if [ "$KIND" = ktc ]; then
    mkdir -p "$TARGET/plugins"
    rm -rf "$TARGET/plugins/heapy-detekt"
    cp -R "$SRC/plugins/heapy-detekt" "$TARGET/plugins/heapy-detekt"
    INSTALLED="$INSTALLED
  plugins/heapy-detekt/"
fi

cat > "$TARGET/.detekt-config-version" <<EOF
repository=$ORIGIN
ref=$REF
commit=$SHA
kind=$KIND
EOF
INSTALLED="$INSTALLED
  .detekt-config-version"

echo
echo "Installed into $TARGET at commit $SHORT ($KIND)"
echo "$INSTALLED"
echo

if [ "$KIND" != gradle ]; then
    cat <<EOF
Kotlin Toolchain, add to project.yaml:

  modules:
    - //plugins/heapy-detekt
  plugins:
    - //plugins/heapy-detekt

and to every module.yaml that needs the check:

  plugins:
    heapy-detekt: enabled

Then run: ./kotlin check detekt

EOF
fi

if [ "$KIND" != ktc ]; then
    cat <<EOF
Gradle, add to build.gradle.kts:

  plugins {
      id("dev.detekt") version "$DETEKT_VERSION"
  }

  dependencies {
      // Mandatory: detekt.yml configures the ktlint rules, and config
      // validation fails when the rule set is not on the classpath.
      detektPlugins("dev.detekt:detekt-rules-ktlint-wrapper:$DETEKT_VERSION")
  }

  detekt {
      config.setFrom(file("detekt.yml"))
  }

  tasks.check {
      dependsOn("detektMain")
  }

Then run: ./gradlew detektMain

EOF
fi

if [ "$KIND" = unknown ]; then
    echo "Could not detect the build system, so no plugin was installed."
    echo "Re-run with DETEKT_CONFIG_KIND=ktc to also install plugins/heapy-detekt."
fi
