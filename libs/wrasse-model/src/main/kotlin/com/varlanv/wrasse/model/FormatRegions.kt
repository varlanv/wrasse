package com.varlanv.wrasse.model

/**
 * A `// wrasse fmt:off` or `// wrasse fmt:on` directive. [parse] accepts exactly the directive
 * text, nothing before or after it.
 */
enum class FormatDirective(val text: String) {
    OFF("// wrasse fmt:off"),
    ON("// wrasse fmt:on"),
    ;

    companion object {
        fun parse(text: CharSequence): FormatDirective? = entries.firstOrNull { text.contentEquals(it.text) }
    }
}

/**
 * The per-file source spans wrasse must leave byte-for-byte untouched, collected in source order
 * from the directives the walk meets. A [Region] runs from the start of its `fmt:off` line to the
 * end of its `fmt:on` comment; regions are flat — an `fmt:off` inside an open region and an
 * `fmt:on` with none open are ignored — and an `fmt:off` never closed makes an open-ended region
 * ending at [OPEN_END].
 */
class FormatRegions {
    class Region internal constructor(val start: Int, end: Int) {
        var end: Int = end
            internal set
    }

    private val regions = ArrayList<Region>()
    private var open: Region? = null

    val isEmpty: Boolean get() = regions.isEmpty()

    fun all(): List<Region> = regions

    fun contains(offset: Int): Boolean = regions.any { it.start <= offset && offset < it.end }

    fun add(
        directive: FormatDirective,
        lineStart: Int,
        commentEnd: Int,
    ) {
        when (directive) {
            FormatDirective.OFF -> if (open == null) {
                open = Region(lineStart, OPEN_END).also { regions.add(it) }
            }

            FormatDirective.ON -> open?.let {
                it.end = commentEnd
                open = null
            }
        }
    }

    /**
     * Whether the edit span `[start, end)` touches a region: a zero-width insertion at `start`
     * touches it when it lands inside the region (start inclusive, end exclusive), any other span
     * when it shares at least one offset with it.
     */
    fun intersects(start: Int, end: Int): Boolean = regions.any { region ->
        if (start == end) region.start <= start && start < region.end else start < region.end && region.start < end
    }

    /**
     * Adds the closed region `[start, end)` unless it touches an existing one, which already keeps
     * its part of the span verbatim.
     */
    fun addSpan(start: Int, end: Int) {
        if (!intersects(start, end)) regions.add(Region(start, end))
    }

    companion object {
        const val OPEN_END: Int = Int.MAX_VALUE
    }
}
