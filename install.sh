#!/usr/bin/env bash
# Usage: ./install.sh [target-dir]
# DETEKT_CONFIG_KIND overrides build-system detection; DETEKT_CONFIG_SRC installs
# from a local checkout. Existing installed files are overwritten.
set -euo pipefail

REPO="${DETEKT_CONFIG_REPO:-Heapy/detekt-config}"
REF="${DETEKT_CONFIG_REF:-main}"
DETEKT_VERSION="2.0.0-alpha.6"
THE_CONFIG_VERSION="0.1.0"

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

PLUGIN_YAML="$SRC/plugins/heapy-detekt/plugin.yaml"
if [ ! -e "$PLUGIN_YAML" ]; then
    echo "ERROR: plugins/heapy-detekt missing in the source tree, install aborted" >&2
    exit 1
fi

# The versions printed below must match what the plugin resolves, or a Gradle
# consumer and a toolchain consumer end up on different rule sets. Both files come
# from the same tarball, so the check is free.
for coordinate in "dev.detekt:detekt-cli:$DETEKT_VERSION" \
    "dev.detekt:detekt-rules-ktlint-wrapper:$DETEKT_VERSION" \
    "io.heapy.detekt:the-config:$THE_CONFIG_VERSION"; do
    if ! grep -qF "$coordinate" "$PLUGIN_YAML"; then
        echo "ERROR: plugin.yaml does not resolve $coordinate; this script is stale" >&2
        exit 1
    fi
done

# A leftover detekt.yml can silently keep a consumer on a frozen config.
REMOVED=""
if [ -f "$TARGET/detekt.yml" ]; then
    rm -f "$TARGET/detekt.yml"
    REMOVED="  detekt.yml (the config is a dependency now)"
fi
if [ -f "$TARGET/.detekt-config-version" ]; then
    rm -f "$TARGET/.detekt-config-version"
    REMOVED="$REMOVED
  .detekt-config-version (legacy root stamp)"
fi

INSTALLED=""
if [ "$KIND" = ktc ]; then
    mkdir -p "$TARGET/plugins"
    rm -rf "$TARGET/plugins/heapy-detekt"
    cp -R "$SRC/plugins/heapy-detekt" "$TARGET/plugins/heapy-detekt"
    cat > "$TARGET/plugins/heapy-detekt/.detekt-config-version" <<EOF
repository=$ORIGIN
ref=$REF
commit=$SHA
EOF
    INSTALLED="  plugins/heapy-detekt/"
fi

echo
if [ -n "$INSTALLED" ]; then
    echo "Installed into $TARGET at commit $SHORT ($KIND)"
    echo "$INSTALLED"
else
    echo "Nothing to install into $TARGET ($KIND)"
    echo "  The config is a dependency now: io.heapy.detekt:the-config"
fi
if [ -n "$REMOVED" ]; then
    echo
    echo "Removed:"
    echo "$REMOVED"
fi
echo

if [ "$KIND" != gradle ]; then
    cat <<EOF
Kotlin Toolchain, add to project.yaml:

  modules:
    - ./plugins/heapy-detekt
  plugins:
    - ./plugins/heapy-detekt

and to every module.yaml that needs the check:

  plugins:
    heapy-detekt: enabled

Then run: ./kotlin check detekt

To write @HeapySuppress in a module, add to its module.yaml:

  dependencies:
    - io.heapy.detekt:the-config:$THE_CONFIG_VERSION: compile-only

EOF
fi

if [ "$KIND" != ktc ]; then
    cat <<EOF
Gradle, add to build.gradle.kts:

  plugins {
      id("dev.detekt") version "$DETEKT_VERSION"
  }

  // Exactly one file may be read from this configuration, hence non-transitive.
  val detektConfig: Configuration by configurations.creating { isTransitive = false }

  dependencies {
      // Mandatory: the config names the ktlint and heapy rule sets, and config
      // validation fails when a rule set is not on the classpath.
      detektPlugins("dev.detekt:detekt-rules-ktlint-wrapper:$DETEKT_VERSION")
      detektPlugins("io.heapy.detekt:the-config:$THE_CONFIG_VERSION")
      detektConfig("io.heapy.detekt:the-config:$THE_CONFIG_VERSION")
      // Only if production code uses @HeapySuppress:
      compileOnly("io.heapy.detekt:the-config:$THE_CONFIG_VERSION")
  }

  detekt {
      // The Gradle plugin has no --config-resource, so the YAML is pulled out of
      // the jar into a file.
      config.setFrom(
          resources.text.fromArchiveEntry(detektConfig, "heapy/detekt.yml").asFile(),
      )
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
