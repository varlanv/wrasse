package com.varlanv.wrasse.format

import com.varlanv.wrasse.lang.StringSlice
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
    ): Int = when (doc) {
        is Doc.Text -> {
            appendText(sb, doc.value)
            val lastNewline = doc.lastNewline()
            if (lastNewline < 0) column + doc.value.length else doc.value.length - lastNewline - 1
        }

        is Doc.Concat -> {
            var col = column
            val parts = doc.parts
            val last = parts.size - 1
            val ownTail = if (last > 0) Tail(parts, 1, mode, tail) else null
            for (i in 0..last) {
                val partTail = if (i < last) {
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

        is Doc.TrailingComma -> when (mode) {
            Mode.FLAT -> column
            Mode.BROKEN -> {
                sb.append(',')
                column + 1
            }
        }

        is Doc.Group -> if (doc.kind == GroupKind.BARRIER) {
            renderNode(sb, doc.body, indentDepth, column, mode, style, tail, forceArguments = false)
        } else if (doc.kind == GroupKind.FILL) {
            renderFill(sb, doc, indentDepth, column, style, tail)
        } else {
            val forced = doc.kind == GroupKind.ARGUMENTS && (doc.forceBreak || (forceArguments && !doc.singleArgument))
            val chosenMode = if (!forced && groupFits(doc, indentDepth, column, style, tail)) Mode.FLAT else Mode.BROKEN
            val bodyDepth = if (doc.indentWhenBroken && chosenMode == Mode.BROKEN) indentDepth + 1 else indentDepth
            val bodyForce = when (doc.kind) {
                GroupKind.ARGUMENTS -> forced || (doc.forceNestedWhenBroken && chosenMode == Mode.BROKEN)
                GroupKind.LAMBDA, GroupKind.CONTINUATION, GroupKind.CHAIN, GroupKind.TEMPLATE, GroupKind.FILL,
                GroupKind.BARRIER -> false

                GroupKind.DEFAULT, GroupKind.FLUID -> forceArguments
            }
            renderNode(sb, doc.body, bodyDepth, column, chosenMode, style, tail, bodyForce)
        }
    }

    /**
     * Renders a [GroupKind.FILL] group: its parts are laid out left to right and every `SOFT`
     * break decides on its own whether the segment after it — the parts up to the next `SOFT`
     * break — still fits on the current line, so the group packs as many segments per line as fit
     * and breaks only before the one that would overflow. Every other part renders in `BROKEN`
     * mode, with the parts up to the next break as its own tail, so a group nested in a segment
     * still decides for itself.
     */
    private fun renderFill(
        sb: StringBuilder,
        group: Doc.Group,
        indentDepth: Int,
        column: Int,
        style: FormatStyle,
        tail: Tail?,
    ): Int {
        val body = group.body
        val parts = if (body is Doc.Concat) body.parts else listOf(body)
        val last = parts.size - 1
        val ownTail = if (last > 0) Tail(parts, 1, Mode.BROKEN, tail) else null
        var col = column
        for (i in 0..last) {
            val part = parts[i]
            if (part is Doc.Break && part.kind == BreakKind.SOFT) {
                if (fillSegmentFits(parts, i + 1, col + part.flat.length, style, tail)) {
                    sb.append(part.flat)
                    col += part.flat.length
                } else {
                    sb.append('\n')
                    col = appendIndent(sb, indentDepth, style)
                }
            } else {
                val partTail = if (i < last) {
                    ownTail!!.from = i + 1
                    ownTail
                } else {
                    tail
                }
                col = renderNode(sb, part, indentDepth, col, Mode.BROKEN, style, partTail, forceArguments = false)
            }
        }
        return col
    }

    /**
     * Whether the fill segment starting at [from] — the [parts] up to the next `SOFT` break —
     * fits flat when placed at [column]. The last segment carries [fillTailWidth] with it, so
     * whatever follows the group with no break of its own (a `when` entry's `->`) moves down
     * together with that segment. A segment with no flat form of its own never fits.
     */
    private fun fillSegmentFits(
        parts: List<Doc>,
        from: Int,
        column: Int,
        style: FormatStyle,
        tail: Tail?,
    ): Boolean {
        var width = 0
        var i = from
        while (i < parts.size) {
            val part = parts[i]
            if (part is Doc.Break && part.kind == BreakKind.SOFT) break
            val partWidth = flatWidth(part)
            if (partWidth < 0) return false
            width += partWidth
            i++
        }
        val rest = if (i < parts.size) 0 else fillTailWidth(tail)
        return column + width + rest <= style.maxLineLength
    }

    /**
     * Width of [tail] up to its own first break opportunity, a `SOFT` break nested inside a group
     * included — unlike [tailWidth], which counts such a group at its full flat width. Only what
     * cannot break away from a fill group's last segment shares that segment's line; the rest of
     * the tail breaks for itself and takes the overflow first.
     */
    private fun fillTailWidth(tail: Tail?): Int {
        val acc = IntArray(1)
        var current: Tail? = tail
        while (current != null) {
            for (i in current.from until current.parts.size) {
                if (!measureUntilBreak(current.parts[i], acc)) return acc[0]
            }
            current = current.outer
        }
        return acc[0]
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
            GroupKind.BARRIER, GroupKind.FILL -> true

            GroupKind.LAMBDA -> {
                val width = flatWidth(group.body)
                width >= 0 && column + width <= max
            }

            GroupKind.DEFAULT, GroupKind.ARGUMENTS, GroupKind.TEMPLATE -> {
                val width = flatWidth(group.body)
                if (width < 0) return false
                val stopAtChain = group.kind == GroupKind.ARGUMENTS && group.singleArgument
                column +
                    width +
                    tailWidth(
                        tail,
                        column + width,
                        brokenEnd(group, indentDepth, style),
                        style,
                        stopAtChain,
                        stopAtContinuation = group.kind == GroupKind.ARGUMENTS,
                    ) <= max
            }

            GroupKind.FLUID -> {
                val acc = IntArray(1)
                val complete = measureFluid(group.body, acc, nested = false)
                val rest =
                    if (complete) tailWidth(tail, column + acc[0], brokenEnd(group, indentDepth, style), style) else 0
                column + acc[0] + rest <= max
            }

            GroupKind.CONTINUATION, GroupKind.CHAIN -> {
                val acc = IntArray(1)
                val body = group.body
                val lambdaEndsAnywhere = group.kind == GroupKind.CONTINUATION
                var outcome = MEASURE_COMPLETE
                if (body is Doc.Concat) {
                    val parts = body.parts
                    for (i in 0 until parts.size) {
                        outcome = measureContinuation(parts[i], acc, i == parts.size - 1, lambdaEndsAnywhere)
                        if (outcome != MEASURE_COMPLETE) break
                    }
                } else {
                    outcome = measureContinuation(body, acc, softHard = true, lambdaEndsAnywhere = lambdaEndsAnywhere)
                }
                when (outcome) {
                    MEASURE_FORCED -> false
                    MEASURE_ENDED -> column + acc[0] <= max
                    else -> column +
                        acc[0] +
                        tailWidth(
                            tail,
                            column + acc[0],
                            brokenEnd(group, indentDepth, style),
                            style,
                            stopAtChain = group.operand,
                            stopAtContinuation = true,
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
     * counts; otherwise its whole flat width does, letting the measured group break first. With
     * [stopAtChain] (the measured group holds one argument, or is an operator chain nested as an
     * operand of another) a [GroupKind.CHAIN] group in the tail counts only up to its own opening
     * break, so a one-argument call standing at the head of a chain keeps its argument list flat
     * and lets the links after it break instead, and a nested operand never breaks for a chain
     * that follows the expression it belongs to. With
     * [stopAtContinuation] (the measured group is an argument list, a chain or an operator chain,
     * never a declaration's own parameter list or value) a [GroupKind.CONTINUATION] group in the
     * tail counts only up to its first break, so the operator chain breaks before anything
     * standing in its first operand does.
     */
    private fun tailWidth(
        tail: Tail?,
        afterColumn: Int,
        brokenEnd: Int,
        style: FormatStyle,
        stopAtChain: Boolean = false,
        stopAtContinuation: Boolean = false,
    ): Int {
        if (tail == null) return 0
        val acc = IntArray(1)
        var current: Tail? = tail
        while (current != null) {
            for (i in current.from until current.parts.size) {
                val part = current.parts[i]
                if (part is Doc.Break ||
                    !measureTail(
                        part,
                        acc,
                        current.mode,
                        afterColumn,
                        brokenEnd,
                        style,
                        stopAtChain,
                        stopAtContinuation,
                    )) {
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
        stopAtChain: Boolean,
        stopAtContinuation: Boolean,
    ): Boolean = when (doc) {
        is Doc.Text -> measureText(doc, acc)
        is Doc.Break -> if (doc.kind == BreakKind.SOFT) {
            acc[0] += doc.flat.length
            true
        } else {
            false
        }

        is Doc.TrailingComma -> {
            if (mode == Mode.BROKEN) acc[0] += 1
            true
        }

        is Doc.Indent -> measureTail(
            doc.body,
            acc,
            mode,
            afterColumn,
            brokenEnd,
            style,
            stopAtChain,
            stopAtContinuation,
        )
        is Doc.Group -> when {
            doc.kind == GroupKind.LAMBDA -> measureLambdaInTail(doc, acc, afterColumn, brokenEnd, style)
            (stopAtChain && doc.kind == GroupKind.CHAIN) ||
                (stopAtContinuation && doc.kind == GroupKind.CONTINUATION) ||
                doc.forceBreak -> measureUntilBreak(doc.body, acc)
            else -> {
                val bodyMode = if (doc.kind == GroupKind.BARRIER) mode else Mode.FLAT
                measureTail(doc.body, acc, bodyMode, afterColumn, brokenEnd, style, stopAtChain, stopAtContinuation)
            }
        }

        is Doc.Concat -> measureParts(doc.parts) {
            measureTail(it, acc, mode, afterColumn, brokenEnd, style, stopAtChain, stopAtContinuation)
        }
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
                val newline = doc.lastNewline()
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
        lambdaEndsAnywhere: Boolean,
    ): Int = when (doc) {
        is Doc.Text -> if (measureText(doc, acc)) MEASURE_COMPLETE else if (softHard) MEASURE_ENDED else MEASURE_FORCED
        is Doc.Break -> if (doc.kind == BreakKind.SOFT) {
            acc[0] += doc.flat.length
            MEASURE_COMPLETE
        } else if (softHard) {
            MEASURE_ENDED
        } else {
            MEASURE_FORCED
        }

        is Doc.TrailingComma -> MEASURE_COMPLETE
        is Doc.Indent -> measureContinuation(doc.body, acc, softHard, lambdaEndsAnywhere)
        is Doc.Group -> when {
            !doc.forceBreak -> measureContinuation(
                doc.body,
                acc,
                softHard || (lambdaEndsAnywhere && doc.kind == GroupKind.LAMBDA),
                lambdaEndsAnywhere,
            )

            measureUntilBreak(doc.body, acc) -> MEASURE_COMPLETE
            softHard -> MEASURE_ENDED
            else -> MEASURE_FORCED
        }
        is Doc.Concat -> {
            var outcome = MEASURE_COMPLETE
            val parts = doc.parts
            for (i in 0 until parts.size) {
                outcome = measureContinuation(parts[i], acc, softHard, lambdaEndsAnywhere)
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
        is Doc.Break -> if (!nested && doc.kind == BreakKind.SOFT) {
            acc[0] += doc.flat.length
            true
        } else {
            false
        }

        is Doc.TrailingComma -> true
        is Doc.Indent -> measureFluid(doc.body, acc, nested)
        is Doc.Group -> measureFluid(doc.body, acc, nested = nested || doc.kind != GroupKind.BARRIER)
        is Doc.Concat -> measureParts(doc.parts) { measureFluid(it, acc, nested) }
    }

    private fun measureText(doc: Doc.Text, acc: IntArray): Boolean {
        val newline = doc.firstNewline()
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
    ): Int = when (doc.kind) {
        BreakKind.HARD -> {
            appendText(sb, doc.literal)
            appendIndent(sb, indentDepth, style)
        }

        BreakKind.SOFT -> when (mode) {
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

    private fun appendText(sb: StringBuilder, text: CharSequence) {
        if (text is StringSlice) sb.append(text.source, text.start, text.end) else sb.append(text)
    }

    private fun flatWidth(doc: Doc): Int = when (doc) {
        is Doc.Text -> if (doc.firstNewline() >= 0) NO_FLAT_WIDTH else doc.value.length
        is Doc.Break -> if (doc.kind == BreakKind.HARD) NO_FLAT_WIDTH else doc.flat.length
        is Doc.TrailingComma -> 0
        is Doc.Indent -> flatWidth(doc.body)
        is Doc.Group -> if (doc.forceBreak) {
            NO_FLAT_WIDTH
        } else {
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
