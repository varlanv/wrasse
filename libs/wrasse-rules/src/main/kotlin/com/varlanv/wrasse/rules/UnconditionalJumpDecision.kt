package com.varlanv.wrasse.rules

/**
 * A loop body consisting of exactly one statement that is itself a bare `break`, or a bare
 * `return` whose own value is not an `<expr> ?: break`/`<expr> ?: continue` elvis fallback
 * (that idiom always exits the loop by one path or another regardless, a common defensive
 * pattern this decision leaves alone), makes the loop execute at most once. A bare `continue`
 * never qualifies: it does not exit the loop, so a lone unconditional `continue` does not make
 * the loop "run once" the way `break`/`return` do.
 */
object UnconditionalJumpDecision {
    const val MESSAGE =
        "This loop contains an unconditional break or return; the loop body will only ever execute once"

    private val ELVIS_JUMP_SUFFIX = Regex("""\?:\s*(break|continue)\s*$""")

    fun decideBreak(): String = MESSAGE

    fun decideReturn(returnText: CharSequence): String? =
        if (ELVIS_JUMP_SUFFIX.containsMatchIn(returnText)) null else MESSAGE
}
