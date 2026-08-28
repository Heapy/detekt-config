package io.heapy.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/**
 * Registered through META-INF/services, so detekt finds the rule set by putting this
 * artifact on its classpath. No --plugins flag is involved.
 */
class HeapyRuleSetProvider : RuleSetProvider {
    override val ruleSetId = RuleSetId("heapy")

    override fun instance() = RuleSet(ruleSetId, listOf(::SuppressWithoutApproval))
}
