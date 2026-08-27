# Heapy Detekt Configuration

Shared [detekt](https://detekt.dev) configuration for all Heapy repositories.

- Target detekt version: **2.0.0-alpha.6**
- [`detekt.yml`](detekt.yml) — the config. Self-contained, based on the generated
  default config of the target detekt version, plus the `ktlint` formatting rules.
  Every deviation from the default is marked with a `# HEAPY:` comment.
- [`plugins/heapy-detekt`](plugins/heapy-detekt) — Kotlin Toolchain plugin. Registers
  a `detekt` check.
- [`detekt.sh`](detekt.sh) — standalone runner for repositories without the toolchain.
- [`install.sh`](install.sh) — copies the three into a repository.

## Install

```sh
curl -fsSL https://raw.githubusercontent.com/Heapy/detekt-config/main/install.sh | bash
```

Run it from the root of the target repository, or pass the directory:
`./install.sh path/to/repo`.

It installs `detekt.yml`, `detekt.sh`, `plugins/heapy-detekt/`, and a
`.detekt-config-version` stamp naming the commit the files came from.

Re-run the same command to update. Installed files are overwritten, so do not edit
them in the consumer repository — change them here and re-install.

## Versioning

By commit. The toolchain does not support publishing plugins
([reference](https://github.com/JetBrains/kotlin-toolchain/blob/main/docs/src/reference/project.md):
*"only dependencies on local plugin modules are supported"*), so the plugin has to
live inside each repository. The config is copied along with it, and
`.detekt-config-version` records which commit of this repository the copy came from.

`install.sh` resolves the commit before downloading, so an install never picks up a
half-pushed branch state.

## Kotlin Toolchain repositories

After `install.sh`, add to `project.yaml`:

```yaml
modules:
  - //plugins/heapy-detekt
plugins:
  - //plugins/heapy-detekt
```

And to every `module.yaml` that needs the check:

```yaml
plugins:
  heapy-detekt: enabled
```

Run `./kotlin check detekt`, or plain `./kotlin check` to run it with the tests.

The check compiles the module first, because full analysis needs its compile
classpath. See [Analysis mode](#analysis-mode).

The plugin reads `detekt.yml` from the project root. Point it elsewhere with the
`configFile` setting:

```yaml
plugins:
  heapy-detekt:
    enabled: true
    configFile: //config/detekt.yml
```

The plugin targets toolchain **0.12.x**. The `kotlin` / `kotlin.bat` wrappers in this
repository pin 0.12.0. The wrapper carries a checksum of the distribution, so upgrade
it with `./kotlin update` — never by editing the version by hand.

### What the plugin sees

Source directories come from two places:

1. `module.kotlinJavaSources` — the toolchain's own answer. It respects the module
   layout (`amper` and `maven-like` both work) and covers main common and JVM
   sources. Generated sources are not included, which is what a linter wants.
2. Every `src@*` directory in the module root. `kotlinJavaSources` does not report
   native, JS or Wasm fragments, and 0.12.0 exposes no reference that does, so the
   plugin picks them up by name.

The two are merged and de-duplicated, so a fragment reported by both is analyzed
once. The toolchain docs list multiplatform source directories in `ModuleSources` as
"coming soon"; when that lands, step 2 stops finding anything new and can be dropped.

Test sources are not covered. `src@*` never matches `test`, `test@jvm` or
`src/test/kotlin`.

## Other repositories

Run `./detekt.sh`. It downloads the pinned detekt CLI (cached in
`~/.cache/heapy-detekt`) and runs it against the `detekt.yml` next to the script.
Extra arguments go to `detekt-cli` verbatim, e.g. `./detekt.sh --input src`.

Such repositories can delete `plugins/heapy-detekt` after installing.

> **Note:** SDKMAN (`sdk install detekt`) currently ships only detekt 1.x, so
> `detekt.sh` fetches the CLI from GitHub releases itself.

## CI gate

Both runners exit non-zero when detekt reports an issue
(`warningsAsErrors: true` in the config), so no wrapper logic is needed.

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
      - run: ./detekt.sh          # or: ./kotlin check detekt
```

## Analysis mode

The two runners differ here.

**The plugin runs full analysis.** It passes the module's compile classpath to
detekt, so rules that need type resolution — `VarCouldBeVal`,
`UnnecessarySafeCall`, `SuspendFunSwallowedCancellation`, `DataClassShouldBeImmutable`
and others — actually report. In `light` mode they silently find nothing.

Full analysis starts the Kotlin compiler frontend, which leaves a non-daemon thread
behind. Running that inside the toolchain JVM hangs the build forever after the
check passes, so the plugin starts detekt as a **separate process**. That is why
`plugin.yaml` resolves `dev.detekt:detekt-cli` itself instead of the plugin module
depending on it.

**`detekt.sh` still runs light analysis**, because it has no way to know the
classpath. It forwards arguments verbatim, so full analysis is available by hand:

```sh
./detekt.sh --input src --analysis-mode full --classpath "$(cat classpath.txt)"
```

> The detekt version is pinned in two files: `DETEKT_VERSION` in `detekt.sh`, and the
> `dev.detekt:*` coordinates in `plugins/heapy-detekt/plugin.yaml`. Keep them in sync.

## Formatting rules

`detekt.yml` configures the `ktlint` rule set (called `formatting` in detekt 1.x),
which comes from `dev.detekt:detekt-rules-ktlint-wrapper`. Both runners load it: the
plugin resolves it onto detekt's classpath, `detekt.sh` downloads the jar and passes
`--plugins`.

The jar is not optional. Config validation rejects the whole `ktlint` section as an
unknown property when it is missing, and the run fails before analyzing anything.

Two rules are off, marked `# HEAPY:` in the config:

- `FunctionSignature`
- `ClassSignature`

Both force a particular way of wrapping signatures across lines. On a real
hand-written module they produced 86 of 99 findings — the rest of the set produced 13.

Expect a large number of findings on generated code (one generated-heavy module
produced over 13000). Exclude such directories rather than fixing them:

```sh
./detekt.sh --input src --excludes "**/generated/**"
```

Most of these rules can fix themselves — detekt supports `--auto-correct`. Neither
runner passes it: both are gates, and a check that rewrites files is a surprise.
