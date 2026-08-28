#!/usr/bin/env bash
# Builds a Maven Central deployment bundle for io.heapy.detekt:the-config: publishes it
# into the local Maven repository, stages that tree in Maven layout, adds checksums and
# PGP signatures, and zips the result into build/the-config-<version>.zip.
#
# The zip is only produced, never uploaded. Upload it in the Central Portal UI, or pass
# --upload to POST it as a USER_MANAGED deployment (Central validates it and waits for
# you to release it manually — nothing goes public until you press the button).
#
# The toolchain can upload to Central on its own (`mavenCentral: enabled` + `./kotlin
# publish mavenCentral`), but that combination forces `signArtifacts: true` onto *every*
# publication of the module, including the `publish mavenLocal` that the local
# verification loop depends on. Signing here instead keeps a PGP key out of the
# everyday build.
#
# Environment:
#   GPG_KEY_ID       key id or fingerprint to sign with (required)
#   GPG_PASSPHRASE   passphrase for that key (optional if gpg-agent already holds it)
#   CENTRAL_TOKEN    "<username>:<password>" Central Portal user token (for --upload)
#
# Credentials belong in the git-ignored ./publish.sh wrapper, which exports them and
# calls this.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_root"

module="the-config"
group="io.heapy.detekt"
group_path="io/heapy/detekt"
version="$(sed -n 's/^ *version: *//p' "$module/module.yaml" | head -1)"
[ -n "$version" ] || { echo "cannot read version from $module/module.yaml" >&2; exit 1; }

maven_local="${MAVEN_LOCAL_REPO:-$HOME/.m2/repository}"
stage="$repo_root/build/staging-deploy"
bundle="$repo_root/build/$module-$version.zip"

upload=0
[ "${1:-}" = "--upload" ] && upload=1

: "${GPG_KEY_ID:?set GPG_KEY_ID to the key you sign releases with}"
if [ "$upload" = 1 ]; then
  : "${CENTRAL_TOKEN:?set CENTRAL_TOKEN to '<username>:<password>' from the Central Portal}"
fi

echo "==> publishing $group:$module:$version to $maven_local"
# A stale artifact of the same version would silently end up in the bundle, so drop the
# previous staging of this version first. Only our own coordinates are touched.
rm -rf "${maven_local:?}/$group_path/$module/$version"
./kotlin publish mavenLocal -m "$module"

echo "==> staging $stage"
rm -rf "$stage" "$bundle"
target="$stage/$group_path/$module/$version"
mkdir -p "$target"
# _remote.repositories and maven-metadata-local.xml are local-repo bookkeeping, not
# artifacts.
find "$maven_local/$group_path/$module/$version" -maxdepth 1 -type f \
  ! -name '_remote.repositories' ! -name 'maven-metadata-local.xml' \
  ! -name '*.asc' ! -name '*.md5' ! -name '*.sha1' \
  -exec cp {} "$target/" \;

echo "==> signing and checksumming"
gpg_args=(--yes --armor --detach-sign --local-user "$GPG_KEY_ID")
if [ -n "${GPG_PASSPHRASE:-}" ]; then
  # Non-interactive: the passphrase goes straight to gpg, no agent prompt involved.
  gpg_args=(--batch --pinentry-mode loopback --passphrase "$GPG_PASSPHRASE" "${gpg_args[@]}")
else
  # Interactive: pinentry asks once and gpg-agent caches it for the remaining files.
  # Without GPG_TTY it cannot find a terminal and fails with "Inappropriate ioctl for
  # device"; with --batch it would not be allowed to ask at all, hence neither is used.
  tty -s || { echo "no terminal for the gpg passphrase prompt: set GPG_PASSPHRASE" >&2; exit 1; }
  GPG_TTY="$(tty)"
  export GPG_TTY
fi

count=0
while IFS= read -r file; do
  gpg "${gpg_args[@]}" --output "$file.asc" "$file"
  # Central requires md5 and sha1 next to every artifact; signatures need none.
  md5 -q "$file" > "$file.md5" 2>/dev/null || md5sum "$file" | cut -d' ' -f1 > "$file.md5"
  shasum -a 1 "$file" | cut -d' ' -f1 > "$file.sha1"
  count=$((count + 1))
done < <(find "$stage" -type f)

echo "==> zipping $bundle"
(cd "$stage" && zip -qr "$bundle" io)

echo
echo "bundle:    $bundle"
echo "artifacts: $count signed files, $(find "$stage" -type f | wc -l | tr -d ' ') files total"

if [ "$upload" = 0 ]; then
  echo
  echo "to upload as a manually-released deployment:"
  echo "  CENTRAL_TOKEN='<user>:<pass>' ./publish.sh --upload"
  exit 0
fi

echo
echo "==> uploading to Central Portal"
deployment_id=$(curl -s --fail-with-body \
  --header "Authorization: Bearer $(printf '%s' "$CENTRAL_TOKEN" | base64)" \
  --form "bundle=@$bundle" \
  "https://central.sonatype.com/api/v1/publisher/upload?publishingType=USER_MANAGED&name=$module-$version")

echo "deployment id: $deployment_id"
echo "Central is validating it now. Release it at https://central.sonatype.com/publishing/deployments"
