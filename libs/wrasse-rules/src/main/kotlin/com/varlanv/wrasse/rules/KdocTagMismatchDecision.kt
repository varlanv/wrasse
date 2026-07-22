package com.varlanv.wrasse.rules

/**
 * Verdict logic for a KDoc's own `@param`/`@property` tags against the declaration's own value
 * parameters, ported from the upstream rule this derives from at its own default configuration
 * (`matchDeclarationsOrder = true`, `exhaustive = true`, `allowParamOnConstructorProperties =
 * false`) — compiler-free so it is unit-testable without a kotlinc dependency. Type parameters are
 * dropped entirely from this batch's scope (`matchTypeParameters` effectively always `false`): a
 * generic bound like `<T : List<String>>` cannot be split into individual type-parameter names by
 * a naive top-level comma split, and this batch doesn't attempt anything more elaborate.
 *
 * Never invoked at all when [docDeclarations] is empty — a KDoc with no `@param`/`@property` tags
 * (this repo's own KDoc-contract-only style, among others) is never a mismatch candidate.
 */
object KdocTagMismatchDecision {
    fun decide(docDeclarations: List<KdocDeclaration>, elementDeclarations: List<KdocDeclaration>): String? {
        if (docDeclarations.isEmpty()) return null

        val invalidDocs = docDeclarations.filter { doc -> elementDeclarations.none { matches(doc, it) } }
        if (invalidDocs.isNotEmpty()) {
            val names = invalidDocs.joinToString { "'${it.name}'" }
            return "documented parameters $names are not present in the declaration"
        }

        val orderMismatch =
        docDeclarations.map { doc -> elementDeclarations.indexOfFirst { matches(doc, it) } }.zipWithNext().any { (a, b) -> a >= b }
        if (orderMismatch) {
            return "order of documented parameters does not match the declaration order"
        }

        if (docDeclarations.size != elementDeclarations.size) {
            val undocumented = elementDeclarations.filter { element -> docDeclarations.none { matches(it, element) } }
            if (undocumented.isNotEmpty()) {
                val names = undocumented.joinToString { "'${it.name}'" }
                return "parameters $names are not documented"
            }
        }

        return null
    }

    private fun matches(doc: KdocDeclaration, element: KdocDeclaration): Boolean =
    element.name == doc.name && (element.kind == KdocDeclarationKind.ANY || element.kind == doc.kind)
}
