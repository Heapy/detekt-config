package io.heapy.example

import io.heapy.detekt.AllowSuppress

@AllowSuppress("Third-party API returns a raw type.")
@Suppress("UNCHECKED_CAST")
fun <T> cast(
    value: Any,
): T = value as T
