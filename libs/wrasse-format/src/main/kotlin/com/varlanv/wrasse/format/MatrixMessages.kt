package com.varlanv.wrasse.format

import com.varlanv.wrasse.model.MatrixDirective

/** The message of every reason a `// wrasse matrix:<N>` directive is rejected. */
internal object MatrixMessages {
    fun problemOf(directive: MatrixDirective): String? =
        sizeProblem(directive) ?: directive.failure ?: noCall(directive).takeIf { !directive.claimed }

    fun sizeProblem(directive: MatrixDirective): String? {
        val size = directive.size
        return when {
            size == null -> "invalid size '${directive.sizeText}' in '${directive.text}': " +
                "the size must be a whole number of at least ${MatrixDirective.MIN_SIZE}"

            size < MatrixDirective.MIN_SIZE -> "size $size in '${directive.text}' is below the minimum of " +
                "${MatrixDirective.MIN_SIZE}: a row needs at least ${MatrixDirective.MIN_SIZE} elements"

            else -> null
        }
    }

    fun noCall(directive: MatrixDirective): String =
        "'${directive.text}' has no call to lay out: no call with a parenthesized argument list starts on the " +
            "line directly below it (a function, constructor or class declaration is not a call)"

    fun unresolvedCallee(directive: MatrixDirective): String =
        "'${directive.text}' targets a call whose callee the compiler could not resolve"

    fun notSingleVarargCallee(directive: MatrixDirective, callee: String): String =
        "'${directive.text}' targets a call to '$callee', which does not declare exactly one parameter that is a vararg"

    fun trailingLambda(directive: MatrixDirective): String =
        "'${directive.text}' does not support a call with a trailing lambda"

    fun comment(directive: MatrixDirective): String =
        "'${directive.text}' does not support a comment inside the argument list of the call below"

    fun spreadArgument(directive: MatrixDirective): String =
        "'${directive.text}' does not support a spread argument in the call below"

    fun namedArgument(directive: MatrixDirective): String =
        "'${directive.text}' does not support a named argument in the call below"

    fun multilineElement(directive: MatrixDirective): String =
        "'${directive.text}' needs every element on one line, but an element of the call below must stay on " +
            "several lines"

    fun sizeExceedsCount(
        directive: MatrixDirective,
        size: Int,
        count: Int,
    ): String =
        "'${directive.text}' asks for rows of $size elements but the call below has only $count"

    fun countNotDivisible(
        directive: MatrixDirective,
        size: Int,
        count: Int,
    ): String =
        "'${directive.text}' cannot split the $count elements of the call below into rows of $size: " +
            "$count is not divisible by $size"
}
