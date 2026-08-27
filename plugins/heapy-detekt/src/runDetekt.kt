package io.heapy.detekt.plugin

import dev.detekt.cli.CliRunner
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.ModuleSources
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream

@TaskAction
fun runDetekt(
    @Input sources: ModuleSources,
    configTag: String,
    @Input(inferTaskDependency = false) configFile: Path?,
    @Output workDir: Path,
) {
    val inputDirs = sources.sourceDirectories.filter { it.exists() }
    if (inputDirs.isEmpty()) {
        println("No source directories, skipping detekt")
        return
    }

    val config = configFile ?: downloadConfig(configTag, workDir)
    val args = arrayOf(
        "--config", config.absolutePathString(),
        "--input", inputDirs.joinToString(",") { it.absolutePathString() },
    )
    val result = CliRunner().run(args, System.out, System.err)
    result.error?.let { throw it }
}

private fun downloadConfig(tag: String, workDir: Path): Path {
    val target = workDir / "detekt-$tag.yml"
    if (!target.isRegularFile()) {
        workDir.createDirectories()
        println("Downloading Heapy detekt config $tag")
        val url = "https://raw.githubusercontent.com/Heapy/detekt-config/$tag/detekt.yml"
        URI(url).toURL().openStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }
    return target
}
