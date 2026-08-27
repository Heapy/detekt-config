package io.heapy.detekt.plugin

import dev.detekt.cli.CliRunner
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.ModuleSources
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

@TaskAction
fun runDetekt(
    @Input sources: ModuleSources,
    @Input(inferTaskDependency = false) moduleRootDir: Path,
    @Input(inferTaskDependency = false) configFile: Path?,
    @Input(inferTaskDependency = false) defaultConfigFile: Path,
) {
    val inputDirs = buildSet {
        sources.sourceDirectories.forEach { if (it.isDirectory()) add(it.toRealPath()) }
        // Platform fragments of a multiplatform module. ModuleSources reports the
        // common and JVM ones, but not native/JS/Wasm, and 0.12.0 exposes no
        // reference for those. Once it does, these paths are already in the set
        // and this loop adds nothing.
        moduleRootDir.listDirectoryEntries("src@*")
            .forEach { if (it.isDirectory()) add(it.toRealPath()) }
    }
    if (inputDirs.isEmpty()) {
        println("No source directories, skipping detekt")
        return
    }

    val config = configFile ?: defaultConfigFile
    if (!config.isRegularFile()) {
        error("Detekt config $config not found. Run install.sh to get it.")
    }

    // One --input per directory: detekt does not split a joined path list here.
    val args = buildList {
        add("--config")
        add(config.absolutePathString())
        inputDirs.forEach {
            add("--input")
            add(it.absolutePathString())
        }
    }.toTypedArray()

    val result = CliRunner().run(args, System.out, System.err)
    result.error?.let { throw it }
}
