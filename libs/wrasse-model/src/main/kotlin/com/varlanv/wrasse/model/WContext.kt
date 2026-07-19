package com.varlanv.wrasse.model

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
) {

    /** Full source text of the file, set once by the adapter before the walk begins. */
    var sourceText: CharSequence = ""
        @JvmSynthetic set

    /** Per-file collector of attributed fix edits (D18). Shares this context's per-file lifecycle. */
    val editPlan: EditPlan = EditPlan()

    /** Node type of the current event (leaf token or interior node enter/exit). */
    var type: WNodeType = WNodeType.FILE
        @JvmSynthetic set
    /** Byte offset of the first character of the current node in the source file. */
    var startOffset: Int = 0
        @JvmSynthetic set
    /** Byte offset past the last character of the current node in the source file. */
    var endOffset: Int = 0
        @JvmSynthetic set
    /** Token text for leaf nodes; null for interior node events. Backed by the compiler's buffer. */
    var leafText: CharSequence? = null
        @JvmSynthetic set

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
    /** Text of the previous leaf, or null at start-of-file. */
    var prevLeafText: CharSequence? = null
        @JvmSynthetic set

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
    val isLeaf: Boolean get() = leafText != null
}

/** True for WHITE_SPACE, EOL_COMMENT, BLOCK_COMMENT, and KDOC node types. */
val WNodeType.isWhitespaceOrComment: Boolean
    get() = this == WNodeType.WHITE_SPACE ||
        this == WNodeType.EOL_COMMENT ||
        this == WNodeType.BLOCK_COMMENT ||
        this == WNodeType.KDOC
