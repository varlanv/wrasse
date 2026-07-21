package com.varlanv.wrasse.format

import com.varlanv.wrasse.model.FormatStyle

/**
 * The single deterministic pass over a [Doc] tree: threads the current column and the ambient
 * indent depth top-down; for each [Doc.Group], decides flat-vs-broken once by checking whether the
 * group's flat width fits in the remaining line width, then renders its body in that mode. Nested
 * groups decide independently once their enclosing group's mode is known. Never re-derives tokens.
 */
object Layout {
    private enum class Mode {
        FLAT,
        BROKEN,
    }

    fun render(doc: Doc, style: FormatStyle): String {
        val sb = StringBuilder()
        renderNode(sb, doc, indentDepth = 0, column = 0, mode = Mode.BROKEN, style = style)
        return sb.toString()
    }

    private fun renderNode(sb: StringBuilder, doc: Doc, indentDepth: Int, column: Int, mode: Mode, style: FormatStyle): Int =
    when (doc) {
        is Doc.Text -> {
            sb.append(doc.value)
            advanceColumn(column, doc.value)
        }

        is Doc.Concat -> {
            var col = column
            for (part in doc.parts) {
                col = renderNode(sb, part, indentDepth, col, mode, style)
            }
            col
        }

        is Doc.Indent -> renderNode(sb, doc.body, indentDepth + 1, column, mode, style)

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
            val flatWidth = flatWidth(doc.body)
            val chosenMode = if (flatWidth != null && column + flatWidth <= style.maxLineLength) Mode.FLAT else Mode.BROKEN
            renderNode(sb, doc.body, indentDepth, column, chosenMode, style)
        }
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

    /**
     * Total rendered width of [doc] if it were laid out fully flat (every `SOFT` break as its
     * [Doc.Break.flat] text), or `null` if it can never be flat — a `HARD` break inside, or a
     * [Doc.Text] leaf carrying an embedded newline (a multiline string/KDoc token spliced in
     * verbatim), forces the enclosing group broken rather than pretending it fits one line.
     */
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
