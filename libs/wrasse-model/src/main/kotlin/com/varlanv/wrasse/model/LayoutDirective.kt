package com.varlanv.wrasse.model

/**
 * The kinds of `// wrasse <keyword>:<N>` layout directive. [prefix] is the exact start of a
 * directive comment, [ruleId] the rule id its rejection is reported under, and [wholeRows] whether
 * the call must hold a whole number of `N`-wide rows: a [MATRIX] call does, a [COLUMNS] call may
 * end on a shorter row.
 */
enum class LayoutDirectiveKind(
    val keyword: String,
    val ruleId: String,
    val wholeRows: Boolean,
) {
    MATRIX("matrix", "format-matrix", wholeRows = true),
    COLUMNS("columns", "format-columns", wholeRows = false),
    ;

    val prefix: String = "// wrasse $keyword:"

    companion object {
        fun ofText(text: CharSequence): LayoutDirectiveKind? = entries.firstOrNull { text.startsWith(it.prefix) }
    }
}

/**
 * A layout directive: an end-of-line comment alone on its line whose [text] starts with its
 * [kind]'s prefix. [sizeText] is what follows the prefix and [size] its value, `null` unless it is
 * a whole number written as `-?[0-9]+` that fits an [Int]; [columns] is [size] when it is at least
 * [MIN_SIZE], else `null`. [line] is the 1-based line of the comment. [claimed] is set once a
 * call's argument list is matched to it, [failure] once the printer rejects that call.
 */
class LayoutDirective internal constructor(
    val kind: LayoutDirectiveKind,
    val text: String,
    val commentStart: Int,
    val commentEnd: Int,
    val lineStart: Int,
    val line: Int,
) {
    val sizeText: String = text.substring(kind.prefix.length)
    val size: Int? = parseSize(sizeText)
    val columns: Int? = size?.takeIf { it >= MIN_SIZE }

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

    companion object {
        const val MIN_SIZE: Int = 2

        fun isDirectiveText(text: CharSequence): Boolean = LayoutDirectiveKind.ofText(text) != null

        private fun parseSize(text: String): Int? {
            val digitsFrom = if (text.startsWith('-')) 1 else 0
            if (digitsFrom == text.length || (digitsFrom until text.length).any { text[it] !in '0'..'9' }) return null
            return text.toIntOrNull()
        }
    }
}

/**
 * What the directives claiming one call's argument list ask of it: a [Single] directive with a
 * usable size, a [Clash] of several such directives, or [Invalid] when every claiming directive
 * has a size that cannot be laid out.
 */
sealed interface LayoutClaim {
    class Single(val directive: LayoutDirective, val columns: Int) : LayoutClaim

    class Clash(val directives: List<LayoutDirective>) : LayoutClaim

    data object Invalid : LayoutClaim
}

/**
 * The per-file [LayoutDirective]s in source order, filled by the walk as the directives are met.
 *
 * A directive applies to the call whose argument list opens on its target line: the first line
 * below it that is neither the line of another directive nor made of [annotations] only. Every
 * directive of the run of such skipped lines directly above a target line shares that line's
 * first call.
 */
class LayoutDirectives(private val annotations: AnnotationSpans) {
    private val directives = ArrayList<LayoutDirective>()
    private val byLineStart = HashMap<Int, LayoutDirective>()
    private var lastClaimedLine = NO_LINE
    private var countedTo = 0
    private var countedLines = 1

    val isEmpty: Boolean get() = directives.isEmpty()

    fun all(): List<LayoutDirective> = directives

    fun add(
        kind: LayoutDirectiveKind,
        text: String,
        commentStart: Int,
        commentEnd: Int,
        lineStart: Int,
        source: CharSequence,
    ) {
        while (countedTo < commentStart) if (source[countedTo++] == '\n') countedLines++
        val directive = LayoutDirective(kind, text, commentStart, commentEnd, lineStart, countedLines)
        directives.add(directive)
        byLineStart[lineStart] = directive
    }

    /**
     * The start of the target line of [directive] in [source], [NO_LINE] when no line follows it.
     */
    fun targetLineStart(directive: LayoutDirective, source: CharSequence): Int {
        var start = lineStartAfter(source, directive.commentEnd)
        while (start != NO_LINE && skips(start, source)) start = lineStartAfter(source, start)
        return start
    }

    /**
     * Whether [directive] is ignored: its own line or its target line starts inside a region of
     * [regions].
     */
    fun isInsideRegion(
        directive: LayoutDirective,
        source: CharSequence,
        regions: FormatRegions,
    ): Boolean = regions.contains(directive.lineStart) || regions.contains(targetLineStart(directive, source))

    /**
     * The claim of the call whose argument list opens at [callOffset], now claimed by every
     * directive above it that targets its line outside [regions]; `null` when the line was already
     * handled by an earlier call, is itself skipped, or no such directive targets it.
     */
    fun claim(
        callOffset: Int,
        source: CharSequence,
        regions: FormatRegions,
    ): LayoutClaim? {
        if (directives.isEmpty()) return null
        val line = lineStartOf(source, callOffset)
        if (line == lastClaimedLine) return null
        lastClaimedLine = line
        if (skips(line, source)) return null
        val claiming = directivesAbove(line, source).filter { !isInsideRegion(it, source, regions) }
        if (claiming.isEmpty()) return null
        claiming.forEach { it.claim() }
        val sized = claiming.mapNotNull { directive -> directive.columns?.let { directive to it } }
        return when (sized.size) {
            0 -> LayoutClaim.Invalid
            1 -> sized.single().let { (directive, columns) -> LayoutClaim.Single(directive, columns) }
            else -> LayoutClaim.Clash(sized.map { it.first })
        }
    }

    private fun directivesAbove(line: Int, source: CharSequence): List<LayoutDirective> {
        val above = ArrayList<LayoutDirective>()
        var start = lineStartBefore(source, line)
        while (start != NO_LINE && skips(start, source)) {
            byLineStart[start]?.let { above.add(it) }
            start = lineStartBefore(source, start)
        }
        above.reverse()
        return above
    }

    private fun skips(lineStart: Int, source: CharSequence): Boolean =
        byLineStart.containsKey(lineStart) || annotations.onlyOnLine(source, lineStart, lineEndOf(source, lineStart))

    private fun lineStartOf(source: CharSequence, offset: Int): Int {
        var start = offset
        while (start > 0 && source[start - 1] != '\n') start--
        return start
    }

    private fun lineEndOf(source: CharSequence, lineStart: Int): Int {
        var end = lineStart
        while (end < source.length && source[end] != '\n') end++
        return end
    }

    private fun lineStartAfter(source: CharSequence, offset: Int): Int {
        val lineEnd = lineEndOf(source, offset)
        return if (lineEnd >= source.length) NO_LINE else lineEnd + 1
    }

    private fun lineStartBefore(source: CharSequence, lineStart: Int): Int =
        if (lineStart == 0) NO_LINE else lineStartOf(source, lineStart - 1)

    companion object {
        const val NO_LINE: Int = -1
    }
}
