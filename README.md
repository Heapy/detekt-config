# Heapy Detekt Configuration

Shared [detekt](https://detekt.dev) configuration for all Heapy repositories.

- Target detekt version: **2.0.0-alpha.6**
- [`detekt.yml`](detekt.yml) — the config. Self-contained, based on the generated
  default config of the target detekt version, plus the `ktlint` formatting rules.
  Every deviation from the default is marked with a `# HEAPY:` comment.
- [`plugins/heapy-detekt`](plugins/heapy-detekt) — Kotlin Toolchain plugin. Registers
  a `detekt` check.
- [`install.sh`](install.sh) — copies these into a repository.

Heapy uses two build systems, and both run detekt through a plugin: Gradle through
detekt's own Gradle plugin, the Kotlin Toolchain through ours. There is no
standalone runner script.

## Install

```sh
curl -fsSL https://raw.githubusercontent.com/Heapy/detekt-config/main/install.sh | bash
```

Run it from the root of the target repository, or pass the directory:
`./install.sh path/to/repo`.

It installs `detekt.yml` and a `.detekt-config-version` stamp naming the commit the
files came from. In a Kotlin Toolchain repository it also installs
`plugins/heapy-detekt/`; a Gradle repository does not need it.

The build system is detected from the target directory — `project.yaml` or
`module.yaml` means toolchain, `build.gradle.kts` and friends mean Gradle. Override
it with `DETEKT_CONFIG_KIND=ktc` or `DETEKT_CONFIG_KIND=gradle`. Either way the
script prints the wiring for what it found.

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

## Gradle repositories

Detekt ships its own Gradle plugin, so nothing is needed from this repository except
`detekt.yml`. Add to `build.gradle.kts`:

```kotlin
plugins {
    id("dev.detekt") version "2.0.0-alpha.6"
}

dependencies {
    // Mandatory: detekt.yml configures the ktlint rules, and config validation
    // fails when the rule set is not on the classpath.
    detektPlugins("dev.detekt:detekt-rules-ktlint-wrapper:2.0.0-alpha.6")
}

detekt {
    config.setFrom(file("detekt.yml"))
}

tasks.check {
    dependsOn("detektMain")
}
```

Run `./gradlew detektMain`. Use `detektMain`, not `detekt`: the `detektMain` task
compiles first and hands detekt the classpath, which is what turns on the rules that
need type resolution.

## CI gate

Detekt fails the build when it reports an issue (`warningsAsErrors: true` in the
config), so no wrapper logic is needed.

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
      - run: ./gradlew detektMain      # or: ./kotlin check detekt
```

## Analysis mode

Both build systems run **full analysis**, where detekt gets the module's compile
classpath. Rules that need type resolution — `VarCouldBeVal`, `UnnecessarySafeCall`,
`SuspendFunSwallowedCancellation`, `DataClassShouldBeImmutable` and others — only
report in this mode. In `light` mode they silently find nothing.

Gradle gets this from the `detektMain` task. The toolchain plugin passes
`module.compileClasspath` itself.

Full analysis starts the Kotlin compiler frontend, which leaves a non-daemon thread
behind. Running that inside the toolchain JVM hangs the build forever after the
check passes, so the plugin starts detekt as a **separate process**. That is why
`plugin.yaml` resolves `dev.detekt:detekt-cli` itself instead of the plugin module
depending on it.

> The detekt version is pinned in three places: the `dev.detekt:*` coordinates in
> `plugins/heapy-detekt/plugin.yaml`, `DETEKT_VERSION` in `install.sh` (it feeds the
> Gradle snippet the installer prints), and each Gradle build file. Keep them in sync.

## Formatting rules

`detekt.yml` configures the `ktlint` rule set (called `formatting` in detekt 1.x),
which comes from `dev.detekt:detekt-rules-ktlint-wrapper`. Both build systems load it:
the toolchain plugin resolves it onto detekt's classpath, Gradle takes it through
`detektPlugins`.

The jar is not optional. Config validation rejects the whole `ktlint` section as an
unknown property when it is missing, and the run fails before analyzing anything.

`FunctionSignature` and `ClassSignature` are both on, both with
`forceMultilineWhenParameterCountGreaterOrEqualThan` set to 1. Every function and
primary-constructor parameter goes on its own line, including the only parameter of
a one-parameter declaration. On a real hand-written module the pair produced 86 of
99 findings — the rest of the set produced 13. Expect them to rewrite most
signatures in an existing codebase.

The two rules take that option in different types: `FunctionSignature` wants a
number, `ClassSignature` wants a string. Quote the `ClassSignature` value or detekt
aborts the run.

Expect a large number of findings on generated code (one generated-heavy module
produced over 13000). Exclude such directories in the config rather than fixing them,
by adding the path to the `excludes` of the noisy rules.

Most of these rules can fix themselves — detekt supports `--auto-correct`. Neither
runner passes it: both are gates, and a check that rewrites files is a surprise.
