package com.varlanv.wrasse.format

import com.varlanv.wrasse.model.LayoutDirective

/** The message of every reason a `// wrasse matrix:<N>` or `// wrasse columns:<N>` directive is rejected. */
internal object LayoutDirectiveMessages {
    fun problemOf(directive: LayoutDirective): String? =
        sizeProblem(directive) ?: directive.failure ?: noCall(directive).takeIf { !directive.claimed }

    fun sizeProblem(directive: LayoutDirective): String? {
        val size = directive.size
        val minimum = directive.kind.minSize
        return when {
            size == null -> "invalid size '${directive.sizeText}' in '${directive.text}': " +
                "the size must be a whole number of at least $minimum"

            size < minimum -> "size $size in '${directive.text}' is below the minimum of $minimum: " +
                "a row needs at least $minimum ${if (minimum == 1) "element" else "elements"}"

            else -> null
        }
    }

    fun noCall(directive: LayoutDirective): String =
        "'${directive.text}' has no call to lay out: no call with a parenthesized argument list starts on the " +
            "first line below it that is neither an annotation-only line nor another layout directive " +
            "(a function, constructor or class declaration is not a call)"

    fun unresolvedCallee(directive: LayoutDirective): String =
        "'${directive.text}' targets a call whose callee the compiler could not resolve"

    fun notSingleVarargCallee(directive: LayoutDirective, callee: String): String =
        "'${directive.text}' targets a call to '$callee', which does not declare exactly one parameter that is a vararg"

    fun trailingLambda(directive: LayoutDirective): String =
        "'${directive.text}' does not support a call with a trailing lambda"

    fun comment(directive: LayoutDirective): String =
        "'${directive.text}' does not support a comment inside the argument list of the call below"

    fun spreadArgument(directive: LayoutDirective): String =
        "'${directive.text}' does not support a spread argument in the call below"

    fun namedArgument(directive: LayoutDirective): String =
        "'${directive.text}' does not support a named argument in the call below"

    fun multilineElement(directive: LayoutDirective): String =
        "'${directive.text}' needs every element on one line, but an element of the call below must stay on " +
            "several lines"

    fun sizeExceedsCount(
        directive: LayoutDirective,
        size: Int,
        count: Int,
    ): String =
        "'${directive.text}' asks for rows of $size elements but the call below has only $count"

    fun countNotDivisible(
        directive: LayoutDirective,
        size: Int,
        count: Int,
    ): String =
        "'${directive.text}' cannot split the $count elements of the call below into rows of $size: " +
            "$count is not divisible by $size"

    fun clash(directives: List<LayoutDirective>): String {
        val named = directives.map { "'${it.text}' (line ${it.line})" }
        val subject = if (named.size == 2) {
            "${named.first()} and ${named.last()} both"
        } else {
            "${named.dropLast(1).joinToString(", ")} and ${named.last()} all"
        }
        return "$subject target the call below; keep only one"
    }
}
