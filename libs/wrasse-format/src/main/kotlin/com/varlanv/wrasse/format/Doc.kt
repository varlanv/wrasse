package com.varlanv.wrasse.format

import com.varlanv.wrasse.lang.indexOfChar
import com.varlanv.wrasse.lang.lastIndexOfChar

/**
 * The printer's layout vocabulary. A `Doc` tree is built once per file by [DocBuilder] and
 * rendered once by [Layout]; nothing in this package re-parses or re-walks source text.
 *
 * Every node carries [start]/[end], the original source span it was built from; [DocSplicer] is
 * the sole reader of these, [Layout] never touches them. [Text]/[Break] take theirs from the
 * compiler leaf they were built from; [Indent]/[Group] forward their single [body]'s span
 * unchanged; [Concat] takes its span as an explicit constructor argument, never inferred from
 * [parts]. [start]/[end] default to `0` for hand-built `Doc` trees that address no real span.
 */
sealed interface Doc {
    val start: Int
    val end: Int

    /** A run of literal text with no embedded line-break decision — a token, verbatim. */
    class Text(
        val value: CharSequence,
        override val start: Int = 0,
        override val end: Int = 0,
    ) : Doc {
        private var firstNewlineCache: Int = WIDTH_UNSET
        private var lastNewlineCache: Int = WIDTH_UNSET

        /** Index of the first `\n` in [value], or -1; scanned once. */
        fun firstNewline(): Int {
            var cached = firstNewlineCache
            if (cached == WIDTH_UNSET) {
                cached = value.indexOfChar('\n')
                firstNewlineCache = cached
            }
            return cached
        }

        /** Index of the last `\n` in [value], or -1; scanned once. */
        fun lastNewline(): Int {
            var cached = lastNewlineCache
            if (cached == WIDTH_UNSET) {
                cached = value.lastIndexOfChar('\n')
                lastNewlineCache = cached
            }
            return cached
        }
    }

    /**
     * A point where a line break may go. [kind] decides whether [Layout] ever has a choice:
     *
     * - [BreakKind.HARD] — always renders as a break; the enclosing [Group] (if any) can never
     *   render flat while a `HARD` break is inside it. [literal] is the exact original text up to
     *   and including the final `\n` it replaces (so blank-line count and any interior trailing
     *   whitespace on those blank lines survive untouched); [Layout] appends the synthesized
     *   indent for the upcoming line after it.
     * - [BreakKind.SOFT] — renders as [flat] if the enclosing `Group` fits flat, or as a newline
     *   plus the synthesized indent otherwise; inside a [GroupKind.FILL] group it is decided on
     *   its own, against the column it stands at.
     *
     * [end] is the original whitespace leaf's true end, which may lie past [start] + [literal]'s
     * length: the trailing run of indentation spaces/tabs after the final `\n` is elided from
     * [literal] (`Layout` regenerates it from the ambient [Indent] depth instead), so it is never
     * addressable content — [DocSplicer] must bail rather than cut inside that elided tail.
     */
    class Break(
        val kind: BreakKind,
        val literal: CharSequence = "\n",
        val flat: String = " ",
        override val start: Int = 0,
        override val end: Int = 0,
    ) : Doc

    /** Increases the ambient indent depth by one [FormatStyle.indentWidth] for `HARD`/broken breaks inside [body]. */
    class Indent(val body: Doc) : Doc {
        override val start: Int get() = body.start
        override val end: Int get() = body.end
    }

    /**
     * Renders [body] flat (every `SOFT` break inside becomes its [Break.flat] text) if that flat
     * form fits within the remaining line width; otherwise renders [body] broken. A `HARD` break
     * anywhere inside forces the broken form regardless of width. Nested groups decide
     * independently, top-down, once the enclosing group's mode is known. [kind] selects how this
     * group measures its own fit — see [GroupKind]; [indentWhenBroken] renders [body] one indent
     * level deeper when the group ends up broken (and at the ambient depth when flat).
     * [forceBreak] applies to [GroupKind.ARGUMENTS] only: the group renders broken regardless of
     * width — no enclosing group can render flat around it — and so does every argument list
     * nested in it, except a [singleArgument] list, which stays a width decision and passes the
     * forcing on to the lists inside it only when it ends up broken itself and
     * [forceNestedWhenBroken] is set (it nests a call with arguments).
     */
    class Group(
        val body: Doc,
        val kind: GroupKind = GroupKind.DEFAULT,
        val indentWhenBroken: Boolean = false,
        val forceBreak: Boolean = false,
        val singleArgument: Boolean = false,
        val forceNestedWhenBroken: Boolean = false,
    ) : Doc {
        fun withBody(
            body: Doc,
        ): Group = Group(body, kind, indentWhenBroken, forceBreak, singleArgument, forceNestedWhenBroken)

        override val start: Int get() = body.start
        override val end: Int get() = body.end
        internal var flatWidthCache: Int = WIDTH_UNSET
        internal var lastLineWidthCache: Int = WIDTH_UNSET
    }

    /** Sequences [parts] with no layout decision of its own. */
    class Concat(
        val parts: List<Doc>,
        override val start: Int = 0,
        override val end: Int = 0,
    ) : Doc {
        internal var flatWidthCache: Int = WIDTH_UNSET
        internal var lastLineWidthCache: Int = WIDTH_UNSET
    }

    /**
     * A trailing comma candidate: renders `,` when the enclosing [Group] chooses broken mode,
     * nothing when it renders flat. [start]/[end] address the original comma's span when the
     * source already had one at this position, or a zero-width point right after the last element
     * otherwise; contributes zero width to [Layout]'s flat-fit measurement either way, since a
     * flat render never emits it.
     */
    class TrailingComma(
        override val start: Int = 0,
        override val end: Int = 0,
    ) : Doc
}

internal const val WIDTH_UNSET = Int.MIN_VALUE

enum class BreakKind {
    HARD,
    SOFT,
}

/**
 * - [DEFAULT] — fits iff its whole flat width plus the tail fits; a `HARD` break anywhere inside,
 *   nested groups included, forces it broken. Counted flat, in full, as part of a preceding
 *   group's tail.
 * - [FLUID] — an assigned value whose body starts with the `SOFT` break after the operator: fits
 *   iff the content up to the first break opportunity inside it (the first break inside any
 *   nested group, or the first `HARD` break) fits.
 * - [ARGUMENTS] — a call's parenthesized argument list: fits like [DEFAULT], but renders broken
 *   when its own [Group.forceBreak] is set or when an enclosing [ARGUMENTS] group broke for that
 *   reason — the forcing reaches every argument list nested through plain parts, [DEFAULT] and
 *   [FLUID] groups, and stops at a [LAMBDA], [CONTINUATION], [CHAIN] or [BARRIER] group.
 * - [TEMPLATE] — a `${...}` string-template entry: fits like [DEFAULT]; stops the forcing an
 *   enclosing [ARGUMENTS] group would otherwise push into argument lists written inside the
 *   template.
 * - [LAMBDA] — a lambda literal: fits iff its own flat width fits, ignoring the tail after its
 *   closing `}` — whatever follows a lambda has its own break opportunities (or none worth
 *   breaking the lambda for). Also counted specially by an enclosing [CONTINUATION] group, below.
 * - [CONTINUATION] — the links after a dot/safe-access chain's or binary expression's first
 *   operand: a `HARD` break anywhere inside forces it broken, except inside a nested [LAMBDA]
 *   group or inside the body's last top-level part, where it merely ends the measurement — a
 *   multi-line trailing lambda, or a multi-line argument list closing the chain, never breaks the
 *   chain around it; a multi-line argument list in the middle does. Fits iff the flat width up to
 *   that point (plus the tail, if nothing ended it) fits.
 * - [CHAIN] — the links after a dot/safe-access chain's first operand: measured like
 *   [CONTINUATION], except that a nested [LAMBDA] group ends the measurement only in the body's
 *   last top-level part, so a multi-line lambda in a non-final link forces every link onto its own
 *   line instead of leaving `}` joined to the `.` after it. Counted in a preceding group's tail
 *   only up to its own opening break, so a call standing at the head of a chain keeps its argument
 *   list flat and lets the links break instead.
 * - [FILL] — a comma-separated list that packs as many elements onto a line as fit (a `when`
 *   entry's condition list): it takes no whole-group flat-vs-broken decision at all. [Layout]
 *   walks its parts left to right and decides each `SOFT` break on its own, against the column
 *   that break stands at and the width of the segment following it, so one broken list still
 *   holds several elements per line. The last segment is measured together with the tail, which
 *   is how the `->` after a condition list moves down with the condition it follows.
 * - [BARRIER] — an `if`/`when`/`try`/object expression: makes no layout decision of its own
 *   (renders in the enclosing mode, at the ambient depth) and only stops the forcing an enclosing
 *   [ARGUMENTS] group would otherwise push into the argument lists written inside it.
 */
enum class GroupKind {
    DEFAULT,
    ARGUMENTS,
    TEMPLATE,
    FLUID,
    LAMBDA,
    CONTINUATION,
    CHAIN,
    FILL,
    BARRIER,
}
