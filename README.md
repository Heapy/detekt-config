# Heapy Detekt Configuration

Shared [detekt](https://detekt.dev) configuration for all Heapy repositories.

- Target detekt version: **2.0.0-alpha.6**
- [`the-config`](the-config) — published as `io.heapy.detekt:the-config`. Carries the
  config, the `heapy` rule set and the `@HeapySuppress` annotation.
- [`the-config/resources/heapy/detekt.yml`](the-config/resources/heapy/detekt.yml) —
  the config. Self-contained, based on the generated default config of the target
  detekt version, plus the `ktlint` formatting rules. Every deviation from the default
  is marked with a `# HEAPY:` comment.
- [`plugins/heapy-detekt`](plugins/heapy-detekt) — Kotlin Toolchain plugin. Registers
  a `detekt` check.
- [`install.sh`](install.sh) — installs the plugin and prints the wiring.

Heapy uses two build systems, and both run detekt through a plugin: Gradle through
detekt's own Gradle plugin, the Kotlin Toolchain through ours. There is no
standalone runner script.

The config is **not** copied into consumer repositories any more. It travels inside
`io.heapy.detekt:the-config`, read from the jar at `heapy/detekt.yml`.

## Install

```sh
curl -fsSL https://raw.githubusercontent.com/Heapy/detekt-config/main/install.sh | bash
```

Run it from the root of the target repository, or pass the directory:
`./install.sh path/to/repo`.

In a Kotlin Toolchain repository it installs `plugins/heapy-detekt/` and a
`.detekt-config-version` stamp naming the commit it came from. In a Gradle repository
it installs nothing at all and only prints the build snippet: the Gradle plugin and
the config both come from Maven Central.

The build system is detected from the target directory — `project.yaml` or
`module.yaml` means toolchain, `build.gradle.kts` and friends mean Gradle. Override
it with `DETEKT_CONFIG_KIND=ktc` or `DETEKT_CONFIG_KIND=gradle`. Either way the
script prints the wiring for what it found.

Re-run the same command to update. Installed files are overwritten, so do not edit
them in the consumer repository — change them here and re-install.

## Versioning

Two things version separately.

The **config and rules** are `io.heapy.detekt:the-config`, an ordinary Maven Central
dependency with a semantic version. Bump it like any other dependency.

The **toolchain plugin** is versioned by commit. The toolchain does not support
publishing plugins
([reference](https://github.com/JetBrains/kotlin-toolchain/blob/main/docs/src/reference/project.md):
*"only dependencies on local plugin modules are supported"*), so it has to live
inside each repository. `.detekt-config-version` records which commit it came from
and which artifact version it resolves.

`install.sh` resolves the commit before downloading, so an install never picks up a
half-pushed branch state. It also refuses to run when its own version numbers no
longer match what `plugin.yaml` resolves.

## Kotlin Toolchain repositories

After `install.sh`, add to `project.yaml`:

```yaml
modules:
  - ./plugins/heapy-detekt
plugins:
  - ./plugins/heapy-detekt
```

And to every `module.yaml` that needs the check:

```yaml
plugins:
  heapy-detekt: enabled
```

Run `./kotlin check detekt`, or plain `./kotlin check` to run it with the tests.

The check compiles the module first, because full analysis needs its compile
classpath. See [Analysis mode](#analysis-mode).

The plugin reads the config out of the jar. To change a few keys, point
`configOverride` at a partial config; detekt merges it on top, last file wins:

```yaml
plugins:
  heapy-detekt:
    enabled: true
    configOverride: //config/detekt-override.yml
```

> **Breaking change.** The old setting was `configFile`, and it *replaced* the shared
> config. That is gone. A repository that still sets `configFile:` fails with a
> settings error on the next build; rename it and keep only the keys you change.

`--config` makes detekt ignore `--config-resource` entirely, so with an override the
plugin extracts the base config from the jar into the task output directory first and
passes two `--config` files.

To write `@HeapySuppress` in production code, add the artifact to that module:

```yaml
dependencies:
  - io.heapy.detekt:the-config:0.1.0: compile-only
```

The plugin targets toolchain **0.12.x**. The `kotlin` / `kotlin.bat` wrappers in this
repository pin 0.12.0. The wrapper carries a checksum of the distribution, so upgrade
it with `./kotlin update` — never by editing the version by hand.

### What the plugin sees

Source directories come from three places:

1. `module.kotlinJavaSources` — the toolchain's own answer. It respects the module
   layout (`amper` and `maven-like` both work) and covers main common and JVM
   sources. Generated sources are not included, which is what a linter wants.
2. Every `src@*` directory in the module root. `kotlinJavaSources` does not report
   native, JS or Wasm fragments, and 0.12.0 exposes no reference that does, so the
   plugin picks them up by name.
3. Test sources by name: `test`, every `test@*` fragment, and `src/test/kotlin` and
   `src/test/java` for the `maven-like` layout. `testResources` is not matched.

The three are merged and de-duplicated, so a fragment reported by more than one is
analyzed once.

### Test sources are analyzed with the main classpath

Kotlin Toolchain 0.12.0 exposes no test classpath, so the plugin hands detekt the
main one for the whole module.

The consequence is a line on every run of a module that has tests:

```
There were N compiler errors found during analysis. This affects accuracy of reporting.
```

Those errors are the test framework: `kotlin.test`, JUnit and any test-only
dependency do not resolve. What this costs:

- Rules that do not need types work normally on test code. `ForbiddenMethodCall`,
  `MagicNumber` and the formatting rules all report.
- Rules that need types work where the type resolves from the main classpath or the
  stdlib. `UnsafeCallOnNullableType` reports a `!!` on a local `String?` in a test.
- Where a type comes from the test framework, a type-resolution rule finds nothing
  and says nothing. Do not read a clean test file as a checked test file.

Gradle does not have this problem: `detektTest` is a separate task with the test
compile classpath.

## Gradle repositories

Detekt ships its own Gradle plugin, so nothing is needed from this repository at all.
Add to `build.gradle.kts`:

```kotlin
plugins {
    id("dev.detekt") version "2.0.0-alpha.6"
}

// Exactly one file may be read from this configuration, hence non-transitive.
val detektConfig: Configuration by configurations.creating { isTransitive = false }

dependencies {
    // Mandatory: the config names the ktlint and heapy rule sets, and config
    // validation fails when a rule set is not on the classpath.
    detektPlugins("dev.detekt:detekt-rules-ktlint-wrapper:2.0.0-alpha.6")
    detektPlugins("io.heapy.detekt:the-config:0.1.0")
    detektConfig("io.heapy.detekt:the-config:0.1.0")
    // Only if production code uses @HeapySuppress:
    compileOnly("io.heapy.detekt:the-config:0.1.0")
}

detekt {
    // The Gradle plugin has no --config-resource, so the YAML is pulled out of the
    // jar into a file.
    config.setFrom(
        resources.text.fromArchiveEntry(detektConfig, "heapy/detekt.yml").asFile(),
    )
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

> Versions are pinned in three places: the coordinates in
> `plugins/heapy-detekt/plugin.yaml`, `DETEKT_VERSION` and `THE_CONFIG_VERSION` in
> `install.sh` (they feed the Gradle snippet it prints), and each Gradle build file.
> `install.sh` checks itself against `plugin.yaml` and refuses to run when they drift.

## No escape hatches

`@Suppress("SomeRule")` turns a rule off for one declaration, and nothing in the
config sees it happen. This is the one thing the whole repository exists to close.

A list of forbidden suppression strings cannot close it. detekt strips the `detekt`
prefix with `Regex("detekt[.:]", IGNORE_CASE)`, replacing **every** occurrence, then
compares what is left. So `all`, `ALL`, `DETEKT:all`, `Detekt.ALL` and
`detekt:detekt:ReturnCount` all work, and the set of working spellings is infinite.
`style > ForbiddenSuppress` is therefore turned **off** in this config.

What closes it is a rule that looks at the **annotation type**, not the string:

```kotlin
@HeapySuppress("Third-party API returns a raw type.")
@Suppress("UNCHECKED_CAST")
fun <T> cast(
    value: Any,
): T = value as T
```

`heapy > ForbiddenSuppress` reports every `@Suppress` that has no `@HeapySuppress`
**on the same declaration**, with a reason of at least 10 characters. The reason
length is not configurable.

Two settings:

| key | default | meaning |
|---|---|---|
| `allowedSuppressions` | `['NOTHING_TO_INLINE']` | strings allowed with no approval |
| `allowFileLevel` | `false` | whether `@file:Suppress` is approvable at all |

`allowedSuppressions` clears an annotation only when **every** argument is an allowed
string literal. A non-literal argument is never allowed.

### Why the rule is called ForbiddenSuppress

Because detekt hard-codes it. `isSuppressedBy` starts with
`if (isForbiddenSuppressById(id)) return false`, and that check extracts the **rule
name** and compares it to `ForbiddenSuppress` — the rule set is not part of the
comparison. So any rule with that name is un-silenceable, whatever set it ships in.

That is what makes the gate hold: no `@Suppress` string, `DETEKT:all` included, can
turn this rule off. Verified by running detekt, not by reading docs.

> This is an undocumented detail of detekt 2.0.0-alpha.6. `ForbiddenSuppressTest`
> pins it: if an upgrade drops the hard-coding, that test fails and the gate is open
> again.

The trade-off is the name clash in reports: both this rule and the disabled
`style > ForbiddenSuppress` print as `[ForbiddenSuppress]`. Only one of them runs.

### The artifact is mandatory

**As soon as `detekt.yml` names the `heapy` section, `io.heapy.detekt:the-config`
must be on detekt's classpath in every consumer, Gradle included.** Without it,
config validation rejects `heapy` as an unknown property and the run dies before
analyzing anything. Exactly the same trap this README describes for the ktlint
wrapper.

`config > checkExhaustiveness` is `true`, so a rule that a detekt upgrade adds fails
the run until it is configured here.

## Banned APIs

`ForbiddenMethodCall` and `ForbiddenImport` ban calls that read hidden global state.
A test cannot control them, so it cannot pin the behaviour that depends on them.

- **The clock.** `System.currentTimeMillis`, `System.nanoTime`, every `now()` on a
  `java.time` type, and `Date()`. Inject a `kotlin.time.Clock` and call `now()` on
  it — that call resolves to the injected instance and is not banned.
- **Randomness.** `Math.random`, `java.util.Random()`, `UUID.randomUUID`, and the
  nine `kotlin.random.Random.Default` methods. Inject a `Random` and seed it in the
  test.
- **Blocking and I/O.** `Thread.sleep`, `print`, `println`.
- **Locale.** `String.format`, and the `java.util.Date` / `Calendar` /
  `SimpleDateFormat` imports.

The bans are written against the no-arg overload where a seeded one exists, so
`Date(0L)`, `java.util.Random(42L)` and `Random(42).nextInt()` all pass. `Math.random`
has no seeded form and is banned outright.

Deliberately not banned: `java.time.Clock.systemUTC()` and friends. Something has to
build the clock at the composition root. Everything else on the list can be allowed
one call at a time with `@HeapySuppress` plus `@Suppress`.

Environment defaults — `TimeZone.getDefault`, `ZoneId.systemDefault`,
`Locale.getDefault`, `Charset.defaultCharset` — and `System.getenv` /
`System.getProperty` / `readln` all match if you want them; they are left out on
purpose. Add them to the `methods` list with a `reason`.

## Line length must stay synchronized

`MaxLineLength` in the `style` rule set and every `ktlint` rule with its own
`maxLineLength` are set to 100. They are separate properties: leave one at the
default and the wrapping rules disagree with the line-length rule about where a
line is too long.

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
a one-parameter declaration. Expect them to rewrite most signatures in an existing
codebase.

The two rules take that option in different types: `FunctionSignature` wants a
number, `ClassSignature` wants a string. Quote the `ClassSignature` value or detekt
aborts the run.

Expect a large number of findings on generated code. Exclude such directories in the
config rather than fixing them, by adding the path to the `excludes` of the noisy
rules.

Most of these rules can fix themselves — detekt supports `--auto-correct`. Neither
runner passes it: both are gates, and a check that rewrites files is a surprise.
