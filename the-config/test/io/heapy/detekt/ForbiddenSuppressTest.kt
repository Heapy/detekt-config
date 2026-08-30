package io.heapy.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal const val REASON = "The vendor API returns a raw type."

internal fun findings(
    code: String,
    config: Config = Config.empty,
): List<String> {
    val reported = ForbiddenSuppress(config).lint(code)
    return reported.map { it.message }
}

class ForbiddenSuppressTest {
    @Test
    fun bareSuppressNamesTheRequiredApproval() = assertTrue(
        findings(
            """
            @Suppress("MagicNumber")
            fun f() = 42
            """.trimIndent(),
        ).single().contains("@AllowSuppress"),
    )

    @Test
    fun approvalWithLongEnoughReasonClearsIt() = assertEquals(
        expected = emptyList(),
        actual = findings(
            """
            import io.heapy.detekt.AllowSuppress

            @AllowSuppress("$REASON")
            @Suppress("MagicNumber")
            fun f() = 42
            """.trimIndent(),
        ),
    )

    @Test
    fun shortReasonIsReported() = assertTrue(
        findings(
            """
            import io.heapy.detekt.AllowSuppress

            @AllowSuppress("short")
            @Suppress("MagicNumber")
            fun f() = 42
            """.trimIndent(),
        ).single().contains("characters"),
    )

    @Test
    fun approvalOnTheEnclosingClassDoesNotClearTheMember() = assertEquals(
        expected = 1,
        actual = findings(
            """
            import io.heapy.detekt.AllowSuppress

            @AllowSuppress("$REASON")
            class Holder {
                @Suppress("MagicNumber")
                fun f() = 42
            }
            """.trimIndent(),
        ).size,
    )

    @Test
    fun bracketedAnnotationFormIsUnderstood() = assertEquals(
        expected = emptyList(),
        actual = findings(
            """
            import io.heapy.detekt.AllowSuppress

            @[AllowSuppress("$REASON") Suppress("MagicNumber")]
            fun f() = 42
            """.trimIndent(),
        ),
    )

    @Test
    fun unimportedApprovalIsReported() = assertTrue(
        findings(
            """
            @AllowSuppress("$REASON")
            @Suppress("MagicNumber")
            fun f() = 42
            """.trimIndent(),
        ).single().contains("import"),
    )

    /**
     * The whole gate rests on this: detekt hard-codes every rule named
     * `ForbiddenSuppress` as un-silenceable, whatever rule set it belongs to. If a
     * detekt upgrade drops that, this test fails and the gate is open again.
     */
    @Test
    fun theRuleItselfCannotBeSuppressed() = assertEquals(
        expected = 2,
        actual = findings(
            """
            @Suppress("ForbiddenSuppress")
            @Suppress("MagicNumber")
            fun f() = 42
            """.trimIndent(),
        ).size,
    )
}
