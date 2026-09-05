package com.varlanv.wrasse.rules

/**
 * Extracts `@param`/`@property` tags, in the order they occur in the raw KDoc text, compiler-free
 * so it is unit-testable without a kotlinc dependency. Every other KDoc tag (`@return`, `@throws`,
 * `@see`, ...) is ignored — [KdocTagMismatchDecision] only ever matches parameter/property
 * declarations, the same scope the upstream rule this derives from checks by default
 * (`matchTypeParameters` aside, dropped entirely in this batch — see [KdocDeclaration]).
 */
object KdocTagParser {
    private val TAG_PATTERN = Regex("""@(param|property)\s+(`[^`]+`|[^\s*]+)""")

    fun parseTags(kdocText: CharSequence): List<KdocDeclaration> = TAG_PATTERN.findAll(kdocText).map { match ->
        val kind = if (match.groupValues[1] == "param") KdocDeclarationKind.PARAM else KdocDeclarationKind.PROPERTY
        KdocDeclaration(unquote(match.groupValues[2]), kind)
    }.toList()

    private fun unquote(
        name: String,
    ): String = if (name.startsWith("`") && name.endsWith("`")) name.substring(1, name.length - 1) else name
}

enum class KdocDeclarationKind {
    PARAM,
    PROPERTY,
    ANY,
}

class KdocDeclaration(val name: String, val kind: KdocDeclarationKind)
