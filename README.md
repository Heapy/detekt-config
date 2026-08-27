# Heapy Detekt Configuration

Shared [detekt](https://detekt.dev) configuration for all Heapy repositories.

- Target detekt version: **2.0.0-alpha.6**
- Config: [`detekt.yml`](detekt.yml) — self-contained, based on the generated default
  config of the target detekt version. Every deviation from the default is marked
  with a `# HEAPY:` comment.
- Runner: [`detekt.sh`](detekt.sh) — downloads the pinned CLI (cached in
  `~/.cache/heapy-detekt`), resolves the config, runs detekt.

## Versioning

Git tags: `<detekt-version>-<config-revision>`.

Examples: `2.0.0-alpha.6-1`, `2.0.0-alpha.6-2`.

A tag pins both the config content and the detekt version its keys are valid for.
Consumers reference the config by tag:

```
https://raw.githubusercontent.com/Heapy/detekt-config/<tag>/detekt.yml
```

Tags are immutable. A rule change is a new revision tag. A detekt upgrade is a new
version prefix: regenerate the default config (`detekt-cli --generate-config`),
re-apply the `# HEAPY:` deviations, tag.

## Usage in a repository

Copy `detekt.sh` into the repository root. The pinned `CONFIG_TAG` inside the script
is the version knob. Then:

```sh
./detekt.sh
```

The script exits non-zero when detekt finds issues.
Extra arguments go to `detekt-cli` verbatim, e.g. `./detekt.sh --input src`.

> **Analysis mode:** `detekt.sh` runs detekt in `light` analysis mode (no compiler
> classpath). Rules that need type resolution — e.g. `SuspendFunSwallowedCancellation`,
> `SuspendFunInFinallySection`, `DataClassShouldBeImmutable`, `VarCouldBeVal` — are
> silently skipped in this mode. They apply only when detekt runs with compiler
> information (`--analysis-mode full` with classpath, or the Gradle plugin).

> **Note:** SDKMAN (`sdk install detekt`) currently ships only detekt 1.x.
> Until 2.0 is released there, `detekt.sh` downloads the CLI from GitHub releases
> itself and caches it. Once SDKMAN has 2.x, an installed `detekt-cli` can be used
> directly: `detekt-cli --config detekt.yml`.

## CI gate (GitHub Actions)

```yaml
jobs:
  detekt:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - run: ./detekt.sh
```

The job fails when detekt reports any issue (`warningsAsErrors: true` in the config).
