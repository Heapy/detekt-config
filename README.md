# Heapy Detekt Configuration

Shared [detekt](https://detekt.dev) configuration for all Heapy repositories.

- Target detekt version: **2.0.0-alpha.6**
- [`detekt.yml`](detekt.yml) — the config. Self-contained, based on the generated
  default config of the target detekt version. Every deviation from the default is
  marked with a `# HEAPY:` comment.
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

The plugin analyzes main JVM sources (`module.kotlinJavaSources`). Test sources are
not covered yet.

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

Both runners analyze without a compiler classpath (`light` mode). Rules that need
type resolution — `SuspendFunSwallowedCancellation`, `SuspendFunInFinallySection`,
`DataClassShouldBeImmutable`, `VarCouldBeVal` and others — are silently skipped. They
apply only when detekt runs with compiler information.
