package com.varlanv.wrasse.format

import com.varlanv.wrasse.model.FormatStyle

object Layout {
    private enum class Mode {
        FLAT,
        BROKEN,
    }

    /**
     * What still follows the node being laid out on its own line: [parts] from index [from],
     * rendered in [mode], then whatever [outer] holds. Measured lazily by [tailWidth], so a group
     * deciding its fit can account for where that tail would start.
     */
    private class Tail(
        val parts: List<Doc>,
        var from: Int,
        val mode: Mode,
        val outer: Tail?,
    )

    fun render(doc: Doc, style: FormatStyle): String {
        val sb = StringBuilder()
        renderNode(
            sb,
            doc,
            indentDepth = 0,
            column = 0,
            mode = Mode.BROKEN,
            style = style,
            tail = null,
            forceArguments = false,
        )
        return sb.toString()
    }

    private fun renderNode(
        sb: StringBuilder,
        doc: Doc,
        indentDepth: Int,
        column: Int,
        mode: Mode,
        style: FormatStyle,
        tail: Tail?,
        forceArguments: Boolean,
    ): Int =
        when (doc) {
            is Doc.Text -> {
                sb.append(doc.value)
                advanceColumn(column, doc.value)
            }

            is Doc.Concat -> {
                var col = column
                val parts = doc.parts
                val last = parts.size - 1
                val ownTail = if (last > 0) Tail(parts, 1, mode, tail) else null
                for (i in 0..last) {
                    val partTail =
                        if (i < last) {
                            ownTail!!.from = i + 1
                            ownTail
                        } else {
                            tail
                        }
                    col = renderNode(sb, parts[i], indentDepth, col, mode, style, partTail, forceArguments)
                }
                col
            }

            is Doc.Indent -> renderNode(sb, doc.body, indentDepth + 1, column, mode, style, tail, forceArguments)

            is Doc.Break -> renderBreak(sb, doc, indentDepth, column, mode, style)

            is Doc.TrailingComma ->
                when (mode) {
                    Mode.FLAT -> column
                    Mode.BROKEN -> {
                        sb.append(',')
                        column + 1
                    }
                }

            is Doc.Group -> {
                val forced = doc.kind == GroupKind.ARGUMENTS && (doc.forceBreak || forceArguments)
                val chosenMode =
                    if (!forced && groupFits(doc, indentDepth, column, style, tail)) Mode.FLAT else Mode.BROKEN
                val bodyDepth = if (doc.indentWhenBroken && chosenMode == Mode.BROKEN) indentDepth + 1 else indentDepth
                val bodyForce =
                    when (doc.kind) {
                        GroupKind.ARGUMENTS -> forced
                        GroupKind.LAMBDA, GroupKind.CONTINUATION, GroupKind.TEMPLATE -> false
                        GroupKind.DEFAULT, GroupKind.FLUID -> forceArguments
                    }
                renderNode(sb, doc.body, bodyDepth, column, chosenMode, style, tail, bodyForce)
            }
        }

    private fun groupFits(
        group: Doc.Group,
        indentDepth: Int,
        column: Int,
        style: FormatStyle,
        tail: Tail?,
    ): Boolean {
        val max = style.maxLineLength
        return when (group.kind) {
            GroupKind.LAMBDA -> {
                val width = flatWidth(group.body)
                width >= 0 && column + width <= max
            }

            GroupKind.DEFAULT, GroupKind.ARGUMENTS, GroupKind.TEMPLATE -> {
                val width = flatWidth(group.body)
                if (width < 0) return false
                column + width + tailWidth(tail, column + width, brokenEnd(group, indentDepth, style), style) <= max
            }

            GroupKind.FLUID -> {
                val acc = IntArray(1)
                val complete = measureFluid(group.body, acc, nested = false)
                val rest =
                    if (complete) tailWidth(tail, column + acc[0], brokenEnd(group, indentDepth, style), style) else 0
                column + acc[0] + rest <= max
            }

            GroupKind.CONTINUATION -> {
                val acc = IntArray(1)
                val body = group.body
                var outcome = MEASURE_COMPLETE
                if (body is Doc.Concat) {
                    val parts = body.parts
                    for (i in 0 until parts.size) {
                        outcome = measureContinuation(parts[i], acc, softHard = i == parts.size - 1)
                        if (outcome != MEASURE_COMPLETE) break
                    }
                } else {
                    outcome = measureContinuation(body, acc, softHard = true)
                }
                when (outcome) {
                    MEASURE_FORCED -> false
                    MEASURE_ENDED -> column + acc[0] <= max
                    else -> column + acc[0] + tailWidth(
                        tail,
                        column + acc[0],
                        brokenEnd(group, indentDepth, style),
                        style,
                    ) <= max
                }
            }
        }
    }

    private fun brokenEnd(
        group: Doc.Group,
        indentDepth: Int,
        style: FormatStyle,
    ): Int =
        indentDepth * style.indentWidth + lastLineWidth(group.body)

    /**
     * Width of [tail] as it would render on the current line, starting at [afterColumn] when the
     * measured group stays flat; stops at the first break. A lambda in the tail that could not
     * stay flat either there or at [brokenEnd] (where the tail would start if the measured group
     * broke instead) is going to break anyway, so only its content up to its own first break
     * counts; otherwise its whole flat width does, letting the measured group break first.
     */
    private fun tailWidth(
        tail: Tail?,
        afterColumn: Int,
        brokenEnd: Int,
        style: FormatStyle,
    ): Int {
        if (tail == null) return 0
        val acc = IntArray(1)
        var current: Tail? = tail
        while (current != null) {
            for (i in current.from until current.parts.size) {
                val part = current.parts[i]
                if (part is Doc.Break || !measureTail(part, acc, current.mode, afterColumn, brokenEnd, style)) {
                    return acc[0]
                }
            }
            current = current.outer
        }
        return acc[0]
    }

    private fun measureTail(
        doc: Doc,
        acc: IntArray,
        mode: Mode,
        afterColumn: Int,
        brokenEnd: Int,
        style: FormatStyle,
    ): Boolean = when (doc) {
        is Doc.Text -> measureText(doc, acc)
        is Doc.Break ->
            if (doc.kind == BreakKind.SOFT) {
                acc[0] += doc.flat.length
                true
            } else {
                false
            }

        is Doc.TrailingComma -> {
            if (mode == Mode.BROKEN) acc[0] += 1
            true
        }

        is Doc.Indent -> measureTail(doc.body, acc, mode, afterColumn, brokenEnd, style)
        is Doc.Group ->
            if (doc.kind == GroupKind.LAMBDA) {
                measureLambdaInTail(doc, acc, afterColumn, brokenEnd, style)
            } else {
                measureTail(doc.body, acc, Mode.FLAT, afterColumn, brokenEnd, style)
            }

        is Doc.Concat -> measureParts(doc.parts) { measureTail(it, acc, mode, afterColumn, brokenEnd, style) }
    }

    private fun measureLambdaInTail(
        lambda: Doc.Group,
        acc: IntArray,
        afterColumn: Int,
        brokenEnd: Int,
        style: FormatStyle,
    ): Boolean {
        val width = flatWidth(lambda.body)
        if (width >= 0 &&
            (afterColumn + acc[0] + width <= style.maxLineLength ||
                brokenEnd + acc[0] + width <= style.maxLineLength)) {
            acc[0] += width
            return true
        }
        return measureUntilBreak(lambda.body, acc)
    }

    private fun measureUntilBreak(doc: Doc, acc: IntArray): Boolean = when (doc) {
        is Doc.Text -> measureText(doc, acc)
        is Doc.Break -> false
        is Doc.TrailingComma -> true
        is Doc.Indent -> measureUntilBreak(doc.body, acc)
        is Doc.Group -> measureUntilBreak(doc.body, acc)
        is Doc.Concat -> measureParts(doc.parts) { measureUntilBreak(it, acc) }
    }

    /** Width of the text after the last break anywhere inside [doc] — the width of its final line once broken. */
    private fun lastLineWidth(doc: Doc): Int {
        when (doc) {
            is Doc.Concat -> if (doc.lastLineWidthCache != WIDTH_UNSET) return doc.lastLineWidthCache
            is Doc.Group -> if (doc.lastLineWidthCache != WIDTH_UNSET) return doc.lastLineWidthCache
            else -> {}
        }
        val acc = IntArray(1)
        lastLineWidthInto(doc, acc)
        when (doc) {
            is Doc.Concat -> doc.lastLineWidthCache = acc[0]
            is Doc.Group -> doc.lastLineWidthCache = acc[0]
            else -> {}
        }
        return acc[0]
    }

    private fun lastLineWidthInto(doc: Doc, acc: IntArray) {
        when (doc) {
            is Doc.Text -> {
                val newline = doc.value.lastIndexOf('\n')
                if (newline < 0) acc[0] += doc.value.length else acc[0] = doc.value.length - newline - 1
            }

            is Doc.Break -> acc[0] = 0
            is Doc.TrailingComma -> {}
            is Doc.Indent -> lastLineWidthInto(doc.body, acc)
            is Doc.Group -> lastLineWidthInto(doc.body, acc)
            is Doc.Concat -> {
                val parts = doc.parts
                for (i in 0 until parts.size) lastLineWidthInto(parts[i], acc)
            }
        }
    }

    private fun measureContinuation(
        doc: Doc,
        acc: IntArray,
        softHard: Boolean,
    ): Int = when (doc) {
        is Doc.Text -> if (measureText(doc, acc)) MEASURE_COMPLETE else if (softHard) MEASURE_ENDED else MEASURE_FORCED
        is Doc.Break ->
            if (doc.kind == BreakKind.SOFT) {
                acc[0] += doc.flat.length
                MEASURE_COMPLETE
            } else if (softHard) {
                MEASURE_ENDED
            } else {
                MEASURE_FORCED
            }

        is Doc.TrailingComma -> MEASURE_COMPLETE
        is Doc.Indent -> measureContinuation(doc.body, acc, softHard)
        is Doc.Group -> measureContinuation(doc.body, acc, softHard || doc.kind == GroupKind.LAMBDA)
        is Doc.Concat -> {
            var outcome = MEASURE_COMPLETE
            val parts = doc.parts
            for (i in 0 until parts.size) {
                outcome = measureContinuation(parts[i], acc, softHard)
                if (outcome != MEASURE_COMPLETE) break
            }
            outcome
        }
    }

    private const val MEASURE_COMPLETE = 0
    private const val MEASURE_ENDED = 1
    private const val MEASURE_FORCED = 2

    private fun measureFluid(
        doc: Doc,
        acc: IntArray,
        nested: Boolean,
    ): Boolean = when (doc) {
        is Doc.Text -> measureText(doc, acc)
        is Doc.Break ->
            if (!nested && doc.kind == BreakKind.SOFT) {
                acc[0] += doc.flat.length
                true
            } else {
                false
            }

        is Doc.TrailingComma -> true
        is Doc.Indent -> measureFluid(doc.body, acc, nested)
        is Doc.Group -> measureFluid(doc.body, acc, nested = true)
        is Doc.Concat -> measureParts(doc.parts) { measureFluid(it, acc, nested) }
    }

    private fun measureText(doc: Doc.Text, acc: IntArray): Boolean {
        val newline = doc.value.indexOf('\n')
        return if (newline < 0) {
            acc[0] += doc.value.length
            true
        } else {
            acc[0] += newline
            false
        }
    }

    private inline fun measureParts(parts: List<Doc>, measure: (Doc) -> Boolean): Boolean {
        for (i in 0 until parts.size) {
            if (!measure(parts[i])) return false
        }
        return true
    }

    private fun renderBreak(
        sb: StringBuilder,
        doc: Doc.Break,
        indentDepth: Int,
        column: Int,
        mode: Mode,
        style: FormatStyle,
    ): Int =
        when (doc.kind) {
            BreakKind.HARD -> {
                sb.append(doc.literal)
                appendIndent(sb, indentDepth, style)
            }

            BreakKind.SOFT ->
                when (mode) {
                    Mode.FLAT -> {
                        sb.append(doc.flat)
                        column + doc.flat.length
                    }

                    Mode.BROKEN -> {
                        sb.append('\n')
                        appendIndent(sb, indentDepth, style)
                    }
                }
        }

    private fun appendIndent(
        sb: StringBuilder,
        indentDepth: Int,
        style: FormatStyle,
    ): Int {
        val width = indentDepth * style.indentWidth
        var remaining = width
        while (remaining > 0) {
            val chunk = minOf(remaining, SPACES.length)
            sb.append(SPACES, 0, chunk)
            remaining -= chunk
        }
        return width
    }

    private val SPACES = " ".repeat(128)

    private fun advanceColumn(column: Int, text: String): Int {
        val lastNewline = text.lastIndexOf('\n')
        return if (lastNewline < 0) column + text.length else text.length - lastNewline - 1
    }

    private fun flatWidth(doc: Doc): Int = when (doc) {
        is Doc.Text -> if (doc.value.indexOf('\n') >= 0) NO_FLAT_WIDTH else doc.value.length
        is Doc.Break -> if (doc.kind == BreakKind.HARD) NO_FLAT_WIDTH else doc.flat.length
        is Doc.TrailingComma -> 0
        is Doc.Indent -> flatWidth(doc.body)
        is Doc.Group -> {
            val cached = doc.flatWidthCache
            if (cached != WIDTH_UNSET) cached else flatWidth(doc.body).also { doc.flatWidthCache = it }
        }

        is Doc.Concat -> {
            val cached = doc.flatWidthCache
            if (cached != WIDTH_UNSET) {
                cached
            } else {
                var total = 0
                val parts = doc.parts
                for (i in 0 until parts.size) {
                    val width = flatWidth(parts[i])
                    if (width < 0) {
                        total = NO_FLAT_WIDTH
                        break
                    }
                    total += width
                }
                doc.flatWidthCache = total
                total
            }
        }
    }

    private const val NO_FLAT_WIDTH = -1
}
