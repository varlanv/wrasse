package com.varlanv.wrasse.format

import com.varlanv.wrasse.model.FormatStyle

object Layout {
    private enum class Mode {
        FLAT,
        BROKEN,
    }

    fun render(doc: Doc, style: FormatStyle): String {
        val sb = StringBuilder()
        renderNode(sb, doc, indentDepth = 0, column = 0, mode = Mode.BROKEN, style = style, tailWidth = 0)
        return sb.toString()
    }

    private fun renderNode(
        sb: StringBuilder,
        doc: Doc,
        indentDepth: Int,
        column: Int,
        mode: Mode,
        style: FormatStyle,
        tailWidth: Int,
    ): Int =
    when (doc) {
        is Doc.Text -> {
            sb.append(doc.value)
            advanceColumn(column, doc.value)
        }

        is Doc.Concat -> {
            var col = column
            for ((i, part) in doc.parts.withIndex()) {
                val partTail = if (i < doc.parts.size - 1) computeTailWidth(doc.parts, i + 1, tailWidth, mode) else tailWidth
                col = renderNode(sb, part, indentDepth, col, mode, style, partTail)
            }
            col
        }

        is Doc.Indent -> renderNode(sb, doc.body, indentDepth + 1, column, mode, style, tailWidth)

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
            val fits =
                when (doc.kind) {
                    GroupKind.FLUID -> fluidFits(doc.body, column, tailWidth, style)
                    GroupKind.CONTINUATION -> continuationFits(doc.body, column, tailWidth, style)
                    GroupKind.LAMBDA -> {
                        val flatW = flatWidth(doc.body)
                        flatW != null && column + flatW <= style.maxLineLength
                    }

                    GroupKind.DEFAULT -> {
                        val flatW = flatWidth(doc.body)
                        flatW != null && column + flatW + tailWidth <= style.maxLineLength
                    }
                }
            val chosenMode = if (fits) Mode.FLAT else Mode.BROKEN
            val bodyDepth = if (doc.indentWhenBroken && chosenMode == Mode.BROKEN) indentDepth + 1 else indentDepth
            renderNode(sb, doc.body, bodyDepth, column, chosenMode, style, tailWidth)
        }
    }

    private fun continuationFits(body: Doc, column: Int, tailWidth: Int, style: FormatStyle): Boolean {
        val acc = IntArray(1)
        val parts = if (body is Doc.Concat) body.parts else listOf(body)
        var outcome = MEASURE_COMPLETE
        for ((i, part) in parts.withIndex()) {
            outcome = measureContinuation(part, acc, softHard = i == parts.size - 1)
            if (outcome != MEASURE_COMPLETE) break
        }
        return when (outcome) {
            MEASURE_FORCED -> false
            MEASURE_ENDED -> column + acc[0] <= style.maxLineLength
            else -> column + acc[0] + tailWidth <= style.maxLineLength
        }
    }

    private fun measureContinuation(doc: Doc, acc: IntArray, softHard: Boolean): Int = when (doc) {
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
            for (part in doc.parts) {
                outcome = measureContinuation(part, acc, softHard)
                if (outcome != MEASURE_COMPLETE) break
            }
            outcome
        }
    }

    private const val MEASURE_COMPLETE = 0
    private const val MEASURE_ENDED = 1
    private const val MEASURE_FORCED = 2

    private fun fluidFits(body: Doc, column: Int, tailWidth: Int, style: FormatStyle): Boolean {
        val acc = IntArray(1)
        val complete = measureFluid(body, acc, nested = false)
        val rest = if (complete) tailWidth else 0
        return column + acc[0] + rest <= style.maxLineLength
    }

    private fun measureFluid(doc: Doc, acc: IntArray, nested: Boolean): Boolean = when (doc) {
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
        is Doc.Concat -> measureParts(doc.parts, acc) { measureFluid(it, acc, nested) }
    }

    private fun computeTailWidth(parts: List<Doc>, fromIndex: Int, outerTailWidth: Int, mode: Mode): Int {
        val acc = IntArray(1)
        for (i in fromIndex until parts.size) {
            val part = parts[i]
            if (part is Doc.Break || !measureTail(part, acc, mode)) return acc[0]
        }
        return acc[0] + outerTailWidth
    }

    private fun measureTail(doc: Doc, acc: IntArray, mode: Mode): Boolean = when (doc) {
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

        is Doc.Indent -> measureTail(doc.body, acc, mode)
        is Doc.Group -> measureTail(doc.body, acc, Mode.FLAT)
        is Doc.Concat -> measureParts(doc.parts, acc) { measureTail(it, acc, mode) }
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

    private inline fun measureParts(parts: List<Doc>, acc: IntArray, measure: (Doc) -> Boolean): Boolean {
        for (part in parts) {
            if (!measure(part)) return false
        }
        return true
    }

    private fun renderBreak(sb: StringBuilder, doc: Doc.Break, indentDepth: Int, column: Int, mode: Mode, style: FormatStyle): Int =
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

    private fun appendIndent(sb: StringBuilder, indentDepth: Int, style: FormatStyle): Int {
        val width = indentDepth * style.indentWidth
        repeat(width) { sb.append(' ') }
        return width
    }

    private fun advanceColumn(column: Int, text: String): Int {
        val lastNewline = text.lastIndexOf('\n')
        return if (lastNewline < 0) column + text.length else text.length - lastNewline - 1
    }

    private fun flatWidth(doc: Doc): Int? = when (doc) {
        is Doc.Text -> if (doc.value.contains('\n')) null else doc.value.length
        is Doc.Break -> if (doc.kind == BreakKind.HARD) null else doc.flat.length
        is Doc.TrailingComma -> 0
        is Doc.Indent -> flatWidth(doc.body)
        is Doc.Group -> flatWidth(doc.body)
        is Doc.Concat -> {
            var total = 0
            for (part in doc.parts) {
                total += flatWidth(part) ?: return null
            }
            total
        }
    }
}
