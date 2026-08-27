package io.heapy.detekt.plugin

import org.jetbrains.amper.plugins.Configurable
import java.nio.file.Path

@Configurable
interface HeapyDetektSettings {
    /**
     * Detekt config file. Defaults to detekt.yml in the project root,
     * which is where install.sh puts it.
     */
    val configFile: Path?
}
