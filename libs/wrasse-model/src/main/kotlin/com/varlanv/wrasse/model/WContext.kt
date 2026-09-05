package com.varlanv.wrasse.model

import com.varlanv.wrasse.lang.StringSlice
import java.nio.file.Path

/**
 * Mutable traversal context passed to every rule callback during a SAX-style walk.
 *
 * A single instance is created per file and reused across all events — the framework
 * mutates its fields in place before each dispatch. Rules must read from it during their
 * callback and never store a reference to it (the values change on the next event).
 *
 * Provides: the current event's type/offsets/text, an ancestor stack for parent lookups,
 * the previous leaf's data for adjacent-token rules, tracked line position state
 * (column, indent) computed incrementally during the walk with zero backward scanning, a
 * zero-copy-as-possible view of the file's source text, and the per-file [EditPlan].
 */
class WContext(
    /** Absolute path to the source file being walked. */
    val filePath: String,
    /** [filePath] relative to the directory of the effective `wrasse.json`, or [filePath] itself when there is none — what `exclude` globs match against. */
    val configRelativeFilePath: Path = Path.of(filePath),
) {
    /** Full source text of the file, set once by the adapter before the walk begins. */
    var sourceText: CharSequence = ""
        @JvmSynthetic set

    /** Per-file collector of attributed fix edits. Shares this context's per-file lifecycle. */
    val editPlan: EditPlan = EditPlan()

    /**
     * File-level resolution facade, lazily collected by the compiler-plugin host only when a
     * rule needs it or dump mode is on. Null means "not collected for this file".
     */
    var resolvedUsage: WResolvedUsage? = null
        @JvmSynthetic set

    /** Node type of the current event (leaf token or interior node enter/exit). */
    var type: WNodeType = WNodeType.FILE
        @JvmSynthetic set

    /** Byte offset of the first character of the current node in the source file. */
    var startOffset: Int = 0
        @JvmSynthetic set

    /** Byte offset past the last character of the current node in the source file. */
    var endOffset: Int = 0
        @JvmSynthetic set

    /**
     * Token text for leaf nodes; null for interior node events. A zero-copy [StringSlice] of
     * [sourceText], made on first read and cached for the rest of the event.
     */
    var leafText: CharSequence?
        get() {
            if (!isLeaf) return null
            return leafTextCache ?: StringSlice(sourceText, startOffset, endOffset).also { leafTextCache = it }
        }

        @JvmSynthetic set(value) {
            leafTextCache = value
            isLeaf = value != null
        }

    private var leafTextCache: CharSequence? = null

    /** [leafText] as a `String`, copied once per event; for rules that keep or compare the text as a `String` anyway. */
    fun leafString(): String? {
        if (!isLeaf) return null
        val cached = leafTextCache
        if (cached is String) return cached
        return sourceText.subSequence(startOffset, endOffset).toString().also { leafTextCache = it }
    }

    /** Marks the current event as a leaf token whose text is [startOffset]..[endOffset] of [sourceText]. */
    fun enterLeaf() {
        isLeaf = true
        leafTextCache = null
    }

    /** Stack of ancestor node types from root (index 0) to immediate parent (index size-1). */
    val ancestors: WNodeStack = WNodeStack()

    /** Type of the most recently dispatched leaf token, or FILE at start-of-file. */
    var prevLeafType: WNodeType = WNodeType.FILE
        @JvmSynthetic set

    /** Start offset of the previous leaf, or -1 at start-of-file. */
    var prevLeafStart: Int = -1
        @JvmSynthetic set

    /** End offset of the previous leaf, or -1 at start-of-file. */
    var prevLeafEnd: Int = -1
        @JvmSynthetic set

    /** Text of the previous leaf, sliced out of [sourceText] on each read; null at start-of-file. */
    val prevLeafText: CharSequence?
        get() = if (prevLeafStart < 0) null else StringSlice(sourceText, prevLeafStart, prevLeafEnd)

    /**
     * Index of the current node among its parent's direct children (0-based).
     *
     * While a node's children are being visited, this reflects whichever child is
     * currently active. The framework restores it to the node's own index (as seen by
     * its parent) before that node's own `exitNode`/`onChildLeaf`-closing dispatch, so a
     * rule reading it from an exit callback always sees the exiting node's position, not
     * its last child's.
     */
    var childIndex: Int = 0
        @JvmSynthetic set

    /**
     * Offset of the last newline character seen so far in the walk.
     * Initialized to -1 (virtual newline before the first line). Updated by the framework
     * on every leaf whose text contains '\n'. Makes [column] an O(1) subtraction.
     */
    var lastNewlineOffset: Int = -1
        @JvmSynthetic set

    /** Zero-based column of the current node's start position on its line. */
    fun column(): Int = startOffset - lastNewlineOffset - 1

    /** True if any ancestor in the current stack matches the given type. */
    fun hasAncestor(type: WNodeType): Boolean = ancestors.contains(type)

    /** True if the current event is a leaf token (has text), false for interior node events. */
    var isLeaf: Boolean = false
        @JvmSynthetic set
}

/** True for WHITE_SPACE, EOL_COMMENT, BLOCK_COMMENT, and KDOC node types. */
val WNodeType.isWhitespaceOrComment: Boolean
    get() = this == WNodeType.WHITE_SPACE ||
        this == WNodeType.EOL_COMMENT ||
        this == WNodeType.BLOCK_COMMENT ||
        this == WNodeType.KDOC
