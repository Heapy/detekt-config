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
private const val APPROVAL_NAME = "AllowSuppress"
private const val APPROVAL_PACKAGE = "io.heapy.detekt"
private const val APPROVAL_IMPORT = "io.heapy.detekt.AllowSuppress"

private val SUPPRESS_NAMES = setOf("Suppress", "SuppressWarnings")

private const val MISSING = "@Suppress needs @$APPROVAL_NAME on the same declaration."
private const val SHORT_REASON =
    "The @$APPROVAL_NAME reason must be at least $MIN_REASON_LENGTH characters."
private const val FILE_LEVEL =
    "@file:Suppress hides a rule for a whole file and is not approvable."
private const val UNIMPORTED =
    "@$APPROVAL_NAME is not imported from $APPROVAL_PACKAGE: import $APPROVAL_IMPORT."

/**
 * Reports a `@Suppress` that carries no [AllowSuppress] approval with a reason.
 *
 * `ForbiddenSuppress` with `ignoreAnnotated` already makes the approval mandatory and
 * cannot itself be silenced. This rule adds the strictness that one lacks: the approval
 * must sit on the same declaration, not on an enclosing one.
 */
class ForbiddenSuppress(
    config: Config,
) : Rule(
    config,
    "A @Suppress needs @AllowSuppress with a reason on the same declaration.",
) {
    @Configuration("suppression strings allowed without an approval")
    private val allowedSuppressions: List<String> by config(emptyList<String>())

    @Configuration("allow @file:Suppress when it carries an approval")
    private val allowFileLevel: Boolean by config(false)

    override fun visitAnnotationEntry(
        entry: KtAnnotationEntry,
    ) {
        super.visitAnnotationEntry(entry)
        verdictFor(entry)?.let { message ->
            report(Finding(Entity.from(entry), message))
        }
    }

    private fun verdictFor(
        entry: KtAnnotationEntry,
    ): String? {
        val owner = ownerOf(entry)
        val approval = approvalOn(owner)
        return when {
            shortNameOf(entry) !in SUPPRESS_NAMES -> null
            isFullyAllowed(entry) -> null
            owner is KtFile && !allowFileLevel -> FILE_LEVEL
            approval == null -> MISSING
            !isHeapyApproval(entry) -> UNIMPORTED
            reasonOf(approval).length < MIN_REASON_LENGTH -> SHORT_REASON
            else -> null
        }
    }

    /**
     * An argument that is not a string literal cannot be read here, so it is never
     * allowed. Without the size check `@Suppress(CONSTANT, "AllowedOne")` would pass.
     */
    private fun isFullyAllowed(
        entry: KtAnnotationEntry,
    ): Boolean {
        val strings = suppressionsOf(entry)
        val arguments = entry.valueArguments
        val complete = strings.size == arguments.size
        return complete && strings.all { it in allowedSuppressions }
    }
}

private fun shortNameOf(
    entry: KtAnnotationEntry,
): String? {
    val name = entry.shortName
    return name?.asString()
}

/**
 * `KtAnnotationEntry` is a call element, not a [KtAnnotated], so the annotated
 * declaration is its nearest annotated ancestor. For `@file:Suppress` that is the
 * [KtFile] itself.
 */
private fun ownerOf(
    entry: KtAnnotationEntry,
): KtAnnotated? {
    val ancestors = entry.parents
    val annotated = ancestors.filterIsInstance<KtAnnotated>()
    return annotated.firstOrNull()
}

private fun approvalOn(
    owner: KtAnnotated?,
): KtAnnotationEntry? {
    val declared = owner?.annotationEntries
    val entries = declared.orEmpty()
    return entries.firstOrNull { shortNameOf(it) == APPROVAL_NAME }
}

private fun suppressionsOf(
    entry: KtAnnotationEntry,
): List<String> {
    val arguments = entry.valueArguments
    val expressions = arguments.mapNotNull { it.getArgumentExpression() }
    val templates = expressions.filterIsInstance<KtStringTemplateExpression>()
    return templates.mapNotNull(::literalOf)
}

private fun reasonOf(
    approval: KtAnnotationEntry,
): String {
    val texts = suppressionsOf(approval)
    val first = texts.firstOrNull()
    val trimmed = first?.trim()
    return trimmed.orEmpty()
}

private fun literalOf(
    template: KtStringTemplateExpression,
): String? {
    val parts = template.entries
    val single = parts.singleOrNull()
    return (single as? KtLiteralStringTemplateEntry)?.text
}

/**
 * Type resolution is not available here, so the approval is recognised by name plus
 * evidence that the name can only mean the Heapy annotation in this file.
 */
private fun isHeapyApproval(
    entry: KtAnnotationEntry,
): Boolean {
    val file = entry.containingKtFile
    val imports = file.importDirectives
    val imported = imports.any(::importsApproval)
    val ownPackage = file.packageFqName
    return imported || ownPackage.asString() == APPROVAL_PACKAGE
}

private fun importsApproval(
    directive: KtImportDirective,
): Boolean {
    val fqName = directive.importedFqName
    val name = fqName?.asString()
    return if (directive.isAllUnder) name == APPROVAL_PACKAGE else name == APPROVAL_IMPORT
}
