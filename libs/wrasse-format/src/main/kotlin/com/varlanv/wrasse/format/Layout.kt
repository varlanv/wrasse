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
                val partTail = if (i < doc.parts.size - 1) computeTailWidth(doc.parts, i + 1, tailWidth) else tailWidth
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
            val flatW = flatWidth(doc.body)
            val chosenMode = if (flatW != null && column + flatW + tailWidth <= style.maxLineLength) Mode.FLAT else Mode.BROKEN
            renderNode(sb, doc.body, indentDepth, column, chosenMode, style, tailWidth)
        }
    }

    private fun computeTailWidth(parts: List<Doc>, fromIndex: Int, outerTailWidth: Int): Int {
        var total = 0
        for (i in fromIndex until parts.size) {
            val part = parts[i]
            if (part is Doc.Break) return total
            val w = flatWidth(part) ?: return total
            total += w
        }
        return total + outerTailWidth
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
