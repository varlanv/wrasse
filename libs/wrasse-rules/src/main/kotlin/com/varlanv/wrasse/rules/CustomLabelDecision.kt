package com.varlanv.wrasse.rules

/**
 * A `return@x`/`break@x`/`continue@x` naming a label other than the conventional `@loop` (the
 * project-convention name for a manually-labeled loop, upstream's own hardcoded allowance) or a
 * label matching the name of the call it actually belongs to (Kotlin gives every trailing-lambda
 * call an implicit label equal to its own name — `@forEach`, `@runCatching`, `@let`, ... — writing
 * that same name back out is never "custom", it is just naming the implicit label; generalized
 * here beyond upstream's own narrower `@forEach`/`@forEachIndexed`-only allowance after finding a
 * false positive on `runCatching` in this project's own codebase) is reported when exactly one
 * enclosing loop or matching-named call surrounds it — meaning the custom name was never needed
 * to disambiguate between nested loops in the first place.
 */
object CustomLabelDecision {
    private const val LOOP_LABEL = "@loop"

    fun decide(
        labelText: String,
        matchesEnclosingCallName: Boolean,
        enclosingLoopOrForEachCount: Int,
    ): String? {
        if (labelText == LOOP_LABEL || matchesEnclosingCallName) return null
        if (enclosingLoopOrForEachCount != 1) return null
        return "Custom label $labelText is unnecessary; there is no nested loop or forEach for it to disambiguate"
    }
}
