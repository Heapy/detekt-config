package io.heapy.detekt.plugin

import org.jetbrains.amper.plugins.Configurable
import java.nio.file.Path

@Configurable
interface HeapyDetektSettings {
    /** Overrides the project-root detekt.yml. */
    val configFile: Path?
}
