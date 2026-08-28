package io.heapy.detekt.plugin

import org.jetbrains.amper.plugins.Configurable
import java.nio.file.Path

@Configurable
interface HeapyDetektSettings {
    /** Merged on top of the config shipped in io.heapy.detekt:the-config. */
    val configOverride: Path?
}
