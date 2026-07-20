package com.varlanv.wrasse.format

/**
 * The printer's layout vocabulary (§5.3). A `Doc` tree is built once per file by [DocBuilder] and
 * rendered once by [Layout]; nothing in this package ever re-parses or re-walks source text.
 *
 * Every node carries [start]/[end]: the original source span it was built from (§5.3's "doc
 * leaves reference original source spans, so they are addressable by offset"). [DocSplicer] is
 * the sole reader of these; [Layout] never touches them. [Text]/[Break] get theirs from the
 * compiler leaf they were built from; [Indent]/[Group] forward their single [body]'s span
 * unchanged; [Concat] takes its span as an explicit constructor argument (its own node's bounds
 * for a whole resolved subtree, or the union of a synthetic slice's real parts — never inferred
 * from [parts], since an empty or degenerate first/last part would make inference lie).
 * [start]/[end] default to `0` so hand-built `Doc` trees that never exercise splicing (existing
 * [Layout]/[DocBuilder] unit tests) need no changes.
 */
sealed interface Doc {

    val start: Int
    val end: Int

    /** A run of literal text with no embedded line-break decision — a token, verbatim. */
    class Text(val value: String, override val start: Int = 0, override val end: Int = 0) : Doc

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
     *
     * [end] is the original whitespace leaf's true end, which may lie past [start] + [literal]'s
     * length: the trailing run of indentation spaces/tabs after the final `\n` is elided from
     * [literal] (`Layout` regenerates it from the ambient [Indent] depth instead), so it is never
     * addressable content — [DocSplicer] must bail rather than cut inside that elided tail.
     */
    class Break(val kind: BreakKind, val literal: String = "\n", val flat: String = " ", override val start: Int = 0, override val end: Int = 0) : Doc

    /** Increases the ambient indent depth by one [FormatStyle.indentWidth] for `HARD`/broken breaks inside [body]. */
    class Indent(val body: Doc) : Doc {
        override val start: Int get() = body.start
        override val end: Int get() = body.end
    }

    /**
     * Renders [body] flat (every `SOFT` break inside becomes its [Break.flat] text) if that flat
     * form fits within the remaining line width; otherwise renders [body] broken. A `HARD` break
     * anywhere inside forces the broken form regardless of width. Nested groups decide
     * independently, top-down, once the enclosing group's mode is known.
     */
    class Group(val body: Doc) : Doc {
        override val start: Int get() = body.start
        override val end: Int get() = body.end
    }

    /** Sequences [parts] with no layout decision of its own. */
    class Concat(val parts: List<Doc>, override val start: Int = 0, override val end: Int = 0) : Doc
}

enum class BreakKind {
    HARD,
    SOFT,
}
