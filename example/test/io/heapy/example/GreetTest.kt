package io.heapy.example

import kotlin.test.Test
import kotlin.test.assertEquals

class GreetTest {
    @Test
    fun greets() = assertEquals(
        expected = "Hello, world",
        actual = greet("world"),
    )
}
