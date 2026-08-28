package io.heapy.detekt

import dev.detekt.api.Config
import dev.detekt.api.Configuration
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.parents

private const val MIN_REASON_LENGTH = 10
private const val APPROVAL_NAME = "HeapySuppress"
private const val APPROVAL_PACKAGE = "io.heapy.detekt"
private const val APPROVAL_IMPORT = "io.heapy.detekt.HeapySuppress"

private val SUPPRESS_NAMES = setOf("Suppress", "SuppressWarnings")

private const val MISSING = "@Suppress needs @$APPROVAL_NAME on the same declaration."
private const val SHORT_REASON =
    "The @$APPROVAL_NAME reason must be at least $MIN_REASON_LENGTH characters."
private const val FILE_LEVEL =
    "@file:Suppress hides a rule for a whole file and is not approvable."
private const val UNIMPORTED =
    "@$APPROVAL_NAME is not the Heapy one: import $APPROVAL_IMPORT."

/**
 * Reports a `@Suppress` that carries no [HeapySuppress] approval with a reason.
 *
 * `ForbiddenSuppress` with `ignoreAnnotated` already makes the approval mandatory and
 * cannot itself be silenced. This rule adds the strictness that one lacks: the approval
 * must sit on the same declaration, not on an enclosing one.
 */
class SuppressWithoutApproval(
    config: Config,
) : Rule(
    config,
    "A @Suppress needs @HeapySuppress with a reason on the same declaration.",
) {
    @Configuration("suppression strings allowed without an approval")
    private val allowedSuppressions: List<String> by config(emptyList<String>())

    @Configuration("allow @file:Suppress when it carries an approval")
    private val allowFileLevel: Boolean by config(false)

    override fun visitAnnotationEntry(entry: KtAnnotationEntry) {
        super.visitAnnotationEntry(entry)
        verdictFor(entry)?.let { message ->
            report(Finding(Entity.from(entry), message))
        }
    }

    private fun verdictFor(entry: KtAnnotationEntry): String? {
        val owner = ownerOf(entry)
        val approval = approvalOn(owner)
        return when {
            shortNameOf(entry) !in SUPPRESS_NAMES -> null
            suppressionsOf(entry).all { it in allowedSuppressions } -> null
            owner is KtFile && !allowFileLevel -> FILE_LEVEL
            approval == null -> MISSING
            !isHeapyApproval(entry) -> UNIMPORTED
            reasonOf(approval).length < MIN_REASON_LENGTH -> SHORT_REASON
            else -> null
        }
    }
}

private fun shortNameOf(entry: KtAnnotationEntry): String? =
    entry.shortName?.asString()

/**
 * `KtAnnotationEntry` is a call element, not a [KtAnnotated], so the annotated
 * declaration is its nearest annotated ancestor. For `@file:Suppress` that is the
 * [KtFile] itself.
 */
private fun ownerOf(entry: KtAnnotationEntry): KtAnnotated? {
    val annotated = entry.parents.filterIsInstance<KtAnnotated>()
    return annotated.firstOrNull()
}

private fun approvalOn(owner: KtAnnotated?): KtAnnotationEntry? {
    val entries = owner?.annotationEntries.orEmpty()
    return entries.firstOrNull { shortNameOf(it) == APPROVAL_NAME }
}

private fun suppressionsOf(entry: KtAnnotationEntry): List<String> {
    val expressions = entry.valueArguments.mapNotNull { it.getArgumentExpression() }
    return expressions
        .filterIsInstance<KtStringTemplateExpression>()
        .mapNotNull(::literalOf)
}

private fun reasonOf(approval: KtAnnotationEntry): String {
    val texts = suppressionsOf(approval)
    return texts.firstOrNull()?.trim().orEmpty()
}

private fun literalOf(template: KtStringTemplateExpression): String? {
    val single = template.entries.singleOrNull()
    return (single as? KtLiteralStringTemplateEntry)?.text
}

/**
 * Type resolution is not available here, so the approval is recognised by name plus
 * evidence that the name can only mean the Heapy annotation in this file.
 */
private fun isHeapyApproval(entry: KtAnnotationEntry): Boolean {
    val file = entry.containingKtFile
    val imported = file.importDirectives.any(::importsApproval)
    return imported || file.packageFqName.asString() == APPROVAL_PACKAGE
}

private fun importsApproval(directive: KtImportDirective): Boolean {
    val name = directive.importedFqName?.asString()
    return if (directive.isAllUnder) name == APPROVAL_PACKAGE else name == APPROVAL_IMPORT
}
