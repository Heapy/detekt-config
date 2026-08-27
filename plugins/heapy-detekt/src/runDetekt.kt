package io.heapy.detekt.plugin

import dev.detekt.cli.CliRunner
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.ModuleSources
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

@TaskAction
fun runDetekt(
    @Input sources: ModuleSources,
    @Input(inferTaskDependency = false) configFile: Path?,
    @Input(inferTaskDependency = false) defaultConfigFile: Path,
) {
    val inputDirs = sources.sourceDirectories.filter { it.exists() }
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
