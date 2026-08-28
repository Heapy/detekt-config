package io.heapy.detekt

import dev.detekt.api.Config
import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val REASON = "The vendor API returns a raw type."

private fun findings(
    code: String,
    config: Config = Config.empty,
): List<String> {
    val reported = SuppressWithoutApproval(config).lint(code)
    return reported.map { it.message }
}

class SuppressWithoutApprovalTest {
    @Test
    fun bareSuppressIsReported() = assertEquals(
        expected = 1,
        actual = findings(
            """
            import io.heapy.detekt.HeapySuppress

            @Suppress("MagicNumber")
            fun f() = 42
            """.trimIndent(),
        ).size,
    )

    @Test
    fun approvalWithLongEnoughReasonClearsIt() = assertEquals(
        expected = emptyList(),
        actual = findings(
            """
            import io.heapy.detekt.HeapySuppress

            @HeapySuppress("$REASON")
            @Suppress("MagicNumber")
            fun f() = 42
            """.trimIndent(),
        ),
    )

    @Test
    fun shortReasonIsReported() = assertTrue(
        findings(
            """
            import io.heapy.detekt.HeapySuppress

            @HeapySuppress("short")
            @Suppress("MagicNumber")
            fun f() = 42
            """.trimIndent(),
        ).single().contains("characters"),
    )

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
    fun approvalOnTheEnclosingClassDoesNotClearTheMember() = assertEquals(
        expected = 1,
        actual = findings(
            """
            import io.heapy.detekt.HeapySuppress

            @HeapySuppress("$REASON")
            class Holder {
                @Suppress("MagicNumber")
                fun f() = 42
            }
            """.trimIndent(),
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

    @Test
    fun bracketedAnnotationFormIsUnderstood() = assertEquals(
        expected = emptyList(),
        actual = findings(
            """
            import io.heapy.detekt.HeapySuppress

            @[HeapySuppress("$REASON") Suppress("MagicNumber")]
            fun f() = 42
            """.trimIndent(),
        ),
    )

    @Test
    fun unimportedApprovalIsReported() = assertTrue(
        findings(
            """
            @HeapySuppress("$REASON")
            @Suppress("MagicNumber")
            fun f() = 42
            """.trimIndent(),
        ).single().contains("import"),
    )

    /**
     * The floor is `style > ForbiddenSuppress`, which detekt hard-codes as
     * un-suppressible. This rule is an ordinary one, so it can be silenced. The test
     * records that on purpose: it is why the floor has to stay.
     */
    @Test
    fun theRuleItselfCanBeSuppressed() = assertEquals(
        expected = emptyList(),
        actual = findings(
            """
            @Suppress("SuppressWithoutApproval")
            @Suppress("MagicNumber")
            fun f() = 42
            """.trimIndent(),
        ),
    )

    /**
     * `RuleSet` builds every rule with `Config.empty` just to read its name, so a
     * constructor that touches the config would break rule-set creation.
     */
    @Test
    fun theRuleSetCanBeBuilt() = assertEquals(
        expected = 1,
        actual = RuleSet(RuleSetId("heapy"), listOf(::SuppressWithoutApproval)).rules.size,
    )
}
