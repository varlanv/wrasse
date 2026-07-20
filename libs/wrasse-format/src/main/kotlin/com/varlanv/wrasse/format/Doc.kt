package com.varlanv.wrasse.format

/**
 * The printer's layout vocabulary (§5.3). A `Doc` tree is built once per file by [DocBuilder] and
 * rendered once by [Layout]; nothing in this package ever re-parses or re-walks source text.
 */
sealed interface Doc {

    /** A run of literal text with no embedded line-break decision — a token, verbatim. */
    class Text(val value: String) : Doc

    /**
     * A point where a line break may go. [kind] decides whether [Layout] ever has a choice:
     *
     * - [BreakKind.HARD] — always renders as a break; the enclosing [Group] (if any) can never
     *   render flat while a `HARD` break is inside it. [literal] is the exact original text up to
     *   and including the final `\n` it replaces (so blank-line count and any interior trailing
     *   whitespace on those blank lines survive untouched); [Layout] appends the synthesized
     *   indent for the upcoming line after it.
     * - [BreakKind.SOFT] — renders as [flat] if the enclosing `Group` fits flat, or as a newline
     *   plus the synthesized indent otherwise. Not emitted by [DocBuilder] in the Phase C.1
     *   foundation slice (every real newline becomes a `HARD` break); the foundation harness
     *   exercises `SOFT`/[Group] composition directly against [Layout] to prove the mechanism
     *   ahead of the F-bucket work that will actually emit it.
     */
    class Break(val kind: BreakKind, val literal: String = "\n", val flat: String = " ") : Doc

    /** Increases the ambient indent depth by one [FormatStyle.indentWidth] for `HARD`/broken breaks inside [body]. */
    class Indent(val body: Doc) : Doc

    /**
     * Renders [body] flat (every `SOFT` break inside becomes its [Break.flat] text) if that flat
     * form fits within the remaining line width; otherwise renders [body] broken. A `HARD` break
     * anywhere inside forces the broken form regardless of width. Nested groups decide
     * independently, top-down, once the enclosing group's mode is known.
     */
    class Group(val body: Doc) : Doc

    /** Sequences [parts] with no layout decision of its own. */
    class Concat(val parts: List<Doc>) : Doc {
        companion object {
            val EMPTY: Concat = Concat(emptyList())
        }
    }
}

enum class BreakKind {
    HARD,
    SOFT,
}
