package com.varlanv.wrasse.model

/**
 * The source spans of the annotation entries of one file, filled by the walk in source order as
 * the entries are met. A span lying inside the previous one is dropped, so the kept spans are
 * disjoint.
 */
class AnnotationSpans {
    private var starts = IntArray(INITIAL_CAPACITY)
    private var ends = IntArray(INITIAL_CAPACITY)
    private var size = 0

    fun add(start: Int, end: Int) {
        if (size > 0 && start < ends[size - 1]) return
        if (size == starts.size) {
            starts = starts.copyOf(size * 2)
            ends = ends.copyOf(size * 2)
        }
        starts[size] = start
        ends[size] = end
        size++
    }

    /**
     * Whether the line `[lineStart, lineEnd)` of [source] holds nothing but annotations: it is not
     * blank, and every non-blank character of it lies inside a span.
     */
    fun onlyOnLine(
        source: CharSequence,
        lineStart: Int,
        lineEnd: Int,
    ): Boolean {
        var position = skipBlank(source, lineStart, lineEnd)
        if (position == lineEnd) return false
        while (position < lineEnd) {
            val index = spanContaining(position)
            if (index < 0) return false
            if (ends[index] >= lineEnd) return true
            position = skipBlank(source, ends[index], lineEnd)
        }
        return true
    }

    private fun spanContaining(position: Int): Int {
        var low = 0
        var high = size - 1
        var found = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (starts[middle] <= position) {
                found = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return if (found >= 0 && ends[found] > position) found else -1
    }

    private fun skipBlank(
        source: CharSequence,
        from: Int,
        to: Int,
    ): Int {
        var position = from
        while (position < to && (source[position] == ' ' || source[position] == '\t' || source[position] == '\r')) {
            position++
        }
        return position
    }

    private companion object {
        const val INITIAL_CAPACITY = 8
    }
}
