package io.heapy.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.test.TestConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForbiddenSuppressConfigTest {
    @Test
    fun allowedSuppressionNeedsNoApproval() = assertEquals(
        expected = emptyList(),
        actual = findings(
            code = """
            @Suppress("UNCHECKED_CAST")
            fun f() = 42
            """.trimIndent(),
            config = TestConfig("allowedSuppressions" to listOf("UNCHECKED_CAST")),
        ),
    )

    @Test
    fun oneAllowedSuppressionDoesNotClearTheOthers() = assertEquals(
        expected = 1,
        actual = findings(
            code = """
            @Suppress("UNCHECKED_CAST", "MagicNumber")
            fun f() = 42
            """.trimIndent(),
            config = TestConfig("allowedSuppressions" to listOf("UNCHECKED_CAST")),
        ).size,
    )

    @Test
    fun aNonLiteralArgumentIsNeverAllowed() = assertEquals(
        expected = 1,
        actual = findings(
            code = """
            const val NAME = "UNCHECKED_CAST"

            @Suppress(NAME, "UNCHECKED_CAST")
            fun f() = 42
            """.trimIndent(),
            config = TestConfig("allowedSuppressions" to listOf("UNCHECKED_CAST")),
        ).size,
    )

    @Test
    fun fileLevelSuppressIsReportedEvenWithAnApproval() = assertTrue(
        findings(
            """
            @file:HeapySuppress("$REASON")
            @file:Suppress("MagicNumber")

            import io.heapy.detekt.HeapySuppress

            fun f() = 42
            """.trimIndent(),
        ).single().contains("whole file"),
    )

    @Test
    fun fileLevelSuppressIsAllowedWhenApprovedAndConfigured() = assertEquals(
        expected = emptyList(),
        actual = findings(
            code = """
            @file:HeapySuppress("$REASON")
            @file:Suppress("MagicNumber")

            import io.heapy.detekt.HeapySuppress

            fun f() = 42
            """.trimIndent(),
            config = TestConfig("allowFileLevel" to true),
        ),
    )

    /**
     * `RuleSet` builds every rule with `Config.empty` just to read its name, so a
     * constructor that touches the config would break rule-set creation.
     */
    @Test
    fun theRuleSetCanBeBuilt() {
        val set = RuleSet(RuleSetId("heapy"), listOf(::ForbiddenSuppress))
        val rules = set.rules
        assertEquals(
            expected = 1,
            actual = rules.size,
        )
    }
}
