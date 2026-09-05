package com.varlanv.wrasse.format

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
        val value: String,
        override val start: Int = 0,
        override val end: Int = 0,
    ) : Doc

    /**
     * A point where a line break may go. [kind] decides whether [Layout] ever has a choice:
     *
     * - [BreakKind.HARD] — always renders as a break; the enclosing [Group] (if any) can never
     *   render flat while a `HARD` break is inside it. [literal] is the exact original text up to
     *   and including the final `\n` it replaces (so blank-line count and any interior trailing
     *   whitespace on those blank lines survive untouched); [Layout] appends the synthesized
     *   indent for the upcoming line after it.
     * - [BreakKind.SOFT] — renders as [flat] if the enclosing `Group` fits flat, or as a newline
     *   plus the synthesized indent otherwise.
     *
     * [end] is the original whitespace leaf's true end, which may lie past [start] + [literal]'s
     * length: the trailing run of indentation spaces/tabs after the final `\n` is elided from
     * [literal] (`Layout` regenerates it from the ambient [Indent] depth instead), so it is never
     * addressable content — [DocSplicer] must bail rather than cut inside that elided tail.
     */
    class Break(
        val kind: BreakKind,
        val literal: String = "\n",
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
     * width, and so does every argument list nested in it.
     */
    class Group(
        val body: Doc,
        val kind: GroupKind = GroupKind.DEFAULT,
        val indentWhenBroken: Boolean = false,
        val forceBreak: Boolean = false,
    ) : Doc {
        override val start: Int get() = body.start
        override val end: Int get() = body.end
    }

    /** Sequences [parts] with no layout decision of its own. */
    class Concat(
        val parts: List<Doc>,
        override val start: Int = 0,
        override val end: Int = 0,
    ) : Doc

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
 *   [FLUID] groups, and stops at a [LAMBDA] or [CONTINUATION] group.
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
 */
enum class GroupKind {
    DEFAULT,
    ARGUMENTS,
    TEMPLATE,
    FLUID,
    LAMBDA,
    CONTINUATION,
}
