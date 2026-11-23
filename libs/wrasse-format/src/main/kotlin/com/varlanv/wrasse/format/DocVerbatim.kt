package com.varlanv.wrasse.format

import com.varlanv.wrasse.lang.StringSlice
import com.varlanv.wrasse.model.FormatRegions

/**
 * Rewrites a [Doc] tree so every [FormatRegions.Region] renders as its exact source text.
 *
 * Each leaf lying inside a region becomes empty, and the first leaf reaching a region carries the
 * region's whole source text as one [Doc.Text]. A leaf that straddles a region's start — the
 * whitespace before the `fmt:off` line, whose indentation belongs to the region — keeps what
 * precedes the region (a `HARD` [Doc.Break] its [Doc.Break.literal], any other leaf its source
 * text) and then carries the region. Everything outside every region is left to [Layout].
 */
internal object DocVerbatim {
    fun apply(
        doc: Doc,
        regions: FormatRegions,
        source: CharSequence,
    ): Doc {
        val all = regions.all()
        if (all.isEmpty()) return doc
        return Rewrite(all, source, BooleanArray(all.size)).node(doc)
    }

    private class Rewrite(
        private val regions: List<FormatRegions.Region>,
        private val source: CharSequence,
        private val emitted: BooleanArray,
    ) {
        fun node(doc: Doc): Doc = when (doc) {
            is Doc.Text -> leaf(doc, doc.start, doc.end, precedingText = null)
            is Doc.Break -> leaf(
                doc,
                doc.start,
                doc.end,
                precedingText = doc.literal.takeIf { doc.kind == BreakKind.HARD },
            )
            is Doc.TrailingComma -> leaf(doc, doc.start, doc.end, precedingText = null)
            is Doc.Indent -> Doc.Indent(node(doc.body))
            is Doc.Group -> doc.withBody(node(doc.body))
            is Doc.Concat -> Doc.Concat(doc.parts.map { node(it) }, doc.start, doc.end)
        }

        private fun leaf(
            doc: Doc,
            start: Int,
            end: Int,
            precedingText: CharSequence?,
        ): Doc {
            val index = regions.indexOfFirst { region -> touches(region, start, end) }
            if (index < 0) return doc
            val region = regions[index]
            val regionEnd = minOf(region.end, source.length)
            val prefix: CharSequence? = when {
                start >= region.start -> null
                precedingText != null -> precedingText
                else -> slice(start, region.start)
            }
            val suffix = if (end > regionEnd) slice(regionEnd, end) else null
            val body = if (emitted[index]) null else slice(region.start, regionEnd).also { emitted[index] = true }
            val parts = listOfNotNull(prefix, body, suffix).map { Doc.Text(it, start, end) }
            return when (parts.size) {
                0 -> Doc.Text("", start, end)
                1 -> parts[0]
                else -> Doc.Concat(parts, start, end)
            }
        }

        private fun touches(
            region: FormatRegions.Region,
            start: Int,
            end: Int,
        ): Boolean =
            if (start == end) region.start <= start && start < region.end else start < region.end && region.start < end

        private fun slice(start: Int, end: Int): CharSequence = StringSlice(source, start, end)
    }
}
