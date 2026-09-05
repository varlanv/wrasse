package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType

/**
 * Verdict logic for where a KDoc may structurally sit, compiler-free so it is unit-testable
 * without a kotlinc dependency. A KDoc whose immediate parent is one of the eight declaration
 * kinds that legally carry documentation must be that parent's own first child; anywhere else, a
 * top-level dangling KDoc and a KDoc nested inside any other node kind are both disallowed.
 */
object KdocPlacementDecision {
    private val DOCUMENTABLE_PARENTS = setOf(
        WNodeType.CLASS,
        WNodeType.ENUM_ENTRY,
        WNodeType.FUN,
        WNodeType.OBJECT_DECLARATION,
        WNodeType.PROPERTY,
        WNodeType.SECONDARY_CONSTRUCTOR,
        WNodeType.TYPEALIAS,
        WNodeType.VALUE_PARAMETER,
    )

    fun decide(parent: WNodeType, childIndex: Int): String? = when {
        parent in
            DOCUMENTABLE_PARENTS ->
            if (childIndex == 0) null else "A KDoc is allowed only at the start of a '${parent.name.lowercase()}'"
        parent == WNodeType.FILE -> "A dangling top-level KDoc is not allowed"
        else -> "A KDoc is not allowed inside a '${parent.name.lowercase()}'"
    }
}
