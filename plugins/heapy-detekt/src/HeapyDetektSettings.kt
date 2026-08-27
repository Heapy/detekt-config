package io.heapy.detekt.plugin

import org.jetbrains.amper.plugins.Configurable
import java.nio.file.Path

@Configurable
interface HeapyDetektSettings {
    /**
     * Tag of the Heapy/detekt-config repository to download detekt.yml from.
     */
    val configTag: String get() = "2.0.0-alpha.6-1"

    /**
     * Local detekt config file. When set, [configTag] is ignored.
     */
    val configFile: Path?
}
