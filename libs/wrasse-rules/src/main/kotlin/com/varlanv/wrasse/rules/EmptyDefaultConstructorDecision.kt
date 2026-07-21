package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.WNodeType

/** `null` means the constructor is out of this rule's scope entirely — no report at all. */
class EmptyDefaultConstructorVerdict(val edits: List<WEdit>)

/**
 * Verdict logic for a class's own empty primary constructor, compiler-free so it is
 * unit-testable without a kotlinc dependency.
 *
 * [decide] returns `null` (no report) when the constructor has a value parameter, an annotation,
 * a non-public visibility modifier, the containing class carries `expect`/`actual`, or a sibling
 * secondary constructor delegates to this one via a zero-argument `this()` (removing the empty
 * parameter list would then delete the only constructor that delegation call could still resolve
 * to). Otherwise the constructor is reported; [edits] stays empty — fix declined for this
 * occurrence — when the constructor spells its own `constructor` keyword (deleting only the
 * parameter list would leave that keyword dangling with nothing to attach to) or when a comment
 * sits inside the otherwise-empty parameter list.
 */
object EmptyDefaultConstructorDecision {
    const val MESSAGE = "Empty default constructor"

    fun decide(
        hasValueParameter: Boolean,
        hasAnnotation: Boolean,
        visibility: WNodeType?,
        isExpectOrActual: Boolean,
        calledWithEmptyThis: Boolean,
        hasKeyword: Boolean,
        hasCommentInParens: Boolean,
        vpStart: Int,
        vpEnd: Int,
    ): EmptyDefaultConstructorVerdict? {
        if (hasValueParameter || hasAnnotation) return null
        if (visibility != null && visibility != WNodeType.KW_PUBLIC) return null
        if (isExpectOrActual || calledWithEmptyThis) return null
        val edits = if (hasKeyword || hasCommentInParens) emptyList() else listOf(WEdit(vpStart, vpEnd, ""))
        return EmptyDefaultConstructorVerdict(edits)
    }
}
