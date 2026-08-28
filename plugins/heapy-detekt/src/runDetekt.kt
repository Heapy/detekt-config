package io.heapy.detekt.plugin

import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.ModuleSources
import org.jetbrains.amper.plugins.TaskAction
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

private const val ISSUES_FOUND = 2

@TaskAction
fun runDetekt(
    @Input sources: ModuleSources,
    @Input compileClasspath: Classpath,
    @Input detektClasspath: Classpath,
    @Input(inferTaskDependency = false) moduleRootDir: Path,
    @Input(inferTaskDependency = false) configFile: Path?,
    @Input(inferTaskDependency = false) defaultConfigFile: Path,
    jvmTarget: String?,
) {
    val inputDirs = buildSet {
        sources.sourceDirectories.forEach { if (it.isDirectory()) add(it.toRealPath()) }
        // ModuleSources 0.12.0 omits native, JS and Wasm fragments.
        moduleRootDir.listDirectoryEntries("src@*")
            .forEach { if (it.isDirectory()) add(it.toRealPath()) }
        // ModuleDataForPlugin 0.12.0 exposes no test-source or test-classpath
        // reference, so discover test directories by convention.
        listOf("test", "test@*").forEach { glob ->
            moduleRootDir.listDirectoryEntries(glob)
                .forEach { if (it.isDirectory()) add(it.toRealPath()) }
        }
        listOf("kotlin", "java").forEach {
            val dir = moduleRootDir / "src" / "test" / it
            if (dir.isDirectory()) add(dir.toRealPath())
        }
    }
    if (inputDirs.isEmpty()) {
        println("No source directories, skipping detekt")
        return
    }

    val config = configFile ?: defaultConfigFile
    if (!config.isRegularFile()) {
        error("Detekt config $config not found. Run install.sh to get it.")
    }

    // Detekt runs in its own process on purpose. Full analysis starts the Kotlin
    // compiler frontend, which leaves a non-daemon thread behind, and running that
    // in the toolchain JVM stops the build from ever finishing.
    val command = buildList {
        add((Path(System.getProperty("java.home")) / "bin" / "java").absolutePathString())
        add("-cp")
        add(detektClasspath.resolvedFiles.joinToString(File.pathSeparator) { it.absolutePathString() })
        add("dev.detekt.cli.Main")

        add("--config")
        add(config.absolutePathString())
        // One --input per directory: detekt does not split a joined path list here.
        inputDirs.forEach {
            add("--input")
            add(it.absolutePathString())
        }
        // Without the classpath detekt falls back to 'light' analysis, where every
        // rule that needs type information reports nothing at all.
        add("--analysis-mode")
        add("full")
        compileClasspath.resolvedFiles.forEach {
            add("--classpath")
            add(it.absolutePathString())
        }
        if (jvmTarget != null) {
            add("--jvm-target")
            add(jvmTarget)
        }
    }

    val process = ProcessBuilder(command)
        .redirectErrorStream(true)
        .start()
    // Drain the pipe before waiting: a full pipe would block detekt forever.
    process.inputStream.bufferedReader().forEachLine(::println)

    when (val exitCode = process.waitFor()) {
        0 -> Unit
        ISSUES_FOUND -> error("Detekt found issues")
        else -> error("Detekt failed with exit code $exitCode")
    }
}
