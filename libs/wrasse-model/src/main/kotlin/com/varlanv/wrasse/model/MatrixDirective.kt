package com.varlanv.wrasse.model

import com.varlanv.wrasse.lang.indexOfChar

/**
 * A `// wrasse matrix:<N>` directive: an end-of-line comment alone on its line whose [text] starts
 * with [PREFIX]. [sizeText] is what follows the prefix and [size] its value, `null` unless it is a
 * whole number written as `-?[0-9]+` that fits an [Int]. The call it applies to starts on the line
 * `[targetLineStart, targetLineEnd)` directly below; both are `-1` when no line follows.
 * [claimed] is set once a call's argument list is matched to it, [failure] once the printer
 * rejects that call.
 */
class MatrixDirective internal constructor(
    val text: String,
    val commentStart: Int,
    val commentEnd: Int,
    val lineStart: Int,
    val targetLineStart: Int,
    val targetLineEnd: Int,
) {
    val sizeText: String = text.substring(PREFIX.length)
    val size: Int? = parseSize(sizeText)

    var claimed: Boolean = false
        private set

    var failure: String? = null
        private set

    internal fun claim() {
        claimed = true
    }

    fun fail(message: String) {
        if (failure == null) failure = message
    }

    fun targetsLineOf(offset: Int): Boolean = targetLineStart <= offset && offset < targetLineEnd

    fun isInside(regions: FormatRegions): Boolean = regions.contains(lineStart) || regions.contains(targetLineStart)

    companion object {
        const val PREFIX: String = "// wrasse matrix:"
        const val MIN_SIZE: Int = 2

        fun isDirectiveText(text: CharSequence): Boolean = text.startsWith(PREFIX)

        private fun parseSize(text: String): Int? {
            val digitsFrom = if (text.startsWith('-')) 1 else 0
            if (digitsFrom == text.length || (digitsFrom until text.length).any { text[it] !in '0'..'9' }) return null
            return text.toIntOrNull()
        }
    }
}

/**
 * The per-file [MatrixDirective]s in source order, filled by the walk as the directives are met.
 */
class MatrixDirectives {
    private val directives = ArrayList<MatrixDirective>()

    val isEmpty: Boolean get() = directives.isEmpty()

    fun all(): List<MatrixDirective> = directives

    fun add(
        text: String,
        commentStart: Int,
        commentEnd: Int,
        lineStart: Int,
        source: CharSequence,
    ) {
        val lineBreak = source.indexOfChar('\n', commentEnd)
        val targetStart = if (lineBreak < 0) NO_LINE else lineBreak + 1
        val targetEnd = when {
            targetStart == NO_LINE -> NO_LINE
            else -> source.indexOfChar('\n', targetStart).let { if (it < 0) source.length else it }
        }
        directives.add(MatrixDirective(text, commentStart, commentEnd, lineStart, targetStart, targetEnd))
    }

    /**
     * The first not yet claimed directive whose line below holds [offset], now claimed; `null` when
     * there is none.
     */
    fun claim(offset: Int): MatrixDirective? {
        val directive = directives.firstOrNull { !it.claimed && it.targetsLineOf(offset) } ?: return null
        directive.claim()
        return directive
    }

    private companion object {
        const val NO_LINE = -1
    }
}
