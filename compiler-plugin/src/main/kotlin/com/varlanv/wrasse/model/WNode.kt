package com.varlanv.wrasse.model

/**
 * A node in wrasse's concrete syntax tree. Built from kotlinc's LightTree by the adapter.
 * No kotlinc types in the public API — rules depend only on this class.
 *
 * The tree preserves everything the source file contains: whitespace, comments, punctuation,
 * keywords — all as individual nodes. This is a CST (concrete syntax tree), not an AST.
 *
 * Leaf nodes (tokens) have [leafText]. Composite nodes have [children].
 * To get the source text of a composite node, use [WFile.sourceText] with offsets:
 * `file.sourceText.subSequence(node.startOffset, node.endOffset)`
 */
class WNode(
    /** What kind of syntax element this node represents. */
    val type: WNodeType,

    /** Byte offset of the first character of this node in the source file. */
    val startOffset: Int,

    /** Byte offset past the last character of this node in the source file. */
    val endOffset: Int,

    /**
     * The token text for leaf nodes (IDENTIFIER, WHITE_SPACE, keywords, operators, etc.).
     * Null for composite nodes (FUN, CLASS, BLOCK, etc.) which contain children instead.
     * Backed by kotlinc's internal char buffer — no copy on access.
     */
    val leafText: CharSequence?,

    /** Parent node, or null for the root FILE node. */
    val parent: WNode?,

    /** Direct child nodes. Empty for leaf nodes. */
    val children: List<WNode>,
) {

    /** True if this is a token (leaf) node with no children. */
    val isLeaf: Boolean get() = children.isEmpty()

    /** First direct child, or null if leaf. */
    val firstChild: WNode? get() = children.firstOrNull()

    /** Last direct child, or null if leaf. */
    val lastChild: WNode? get() = children.lastOrNull()

    /**
     * Next sibling in the parent's children list, or null if this is the last child.
     * Computed from parent.children — O(n) on first call for a given parent.
     */
    val nextSibling: WNode? by lazy { siblingAt(1) }

    /**
     * Previous sibling in the parent's children list, or null if this is the first child.
     * Computed from parent.children — O(n) on first call for a given parent.
     */
    val prevSibling: WNode? by lazy { siblingAt(-1) }

    /** All direct children matching [type]. */
    fun childrenOfType(type: WNodeType): List<WNode> =
        children.filter { it.type == type }

    /** First direct child matching [type], or null. */
    fun firstChildOfType(type: WNodeType): WNode? =
        children.firstOrNull { it.type == type }

    /** Last direct child matching [type], or null. */
    fun lastChildOfType(type: WNodeType): WNode? =
        children.lastOrNull { it.type == type }

    /** Walk up the tree to find the nearest ancestor matching [type], or null. */
    fun findParentOfType(type: WNodeType): WNode? {
        var current = parent
        while (current != null) {
            if (current.type == type) return current
            current = current.parent
        }
        return null
    }

    /** True if this node or any ancestor matches [type]. */
    fun isInsideNodeOfType(type: WNodeType): Boolean =
        this.type == type || findParentOfType(type) != null

    /** All descendant nodes, depth-first. */
    fun descendants(): Sequence<WNode> = sequence {
        for (child in children) {
            yield(child)
            yieldAll(child.descendants())
        }
    }

    /** All descendant nodes matching [type], depth-first. */
    fun descendantsOfType(type: WNodeType): Sequence<WNode> =
        descendants().filter { it.type == type }

    /** All leaf (token) nodes under this node, left-to-right. */
    fun leaves(): Sequence<WNode> =
        descendants().filter { it.isLeaf }

    /**
     * Next leaf node in document order (across siblings and parents).
     * Walks right then up until it finds the next leaf.
     */
    fun nextLeaf(): WNode? {
        var current: WNode? = this
        while (current != null) {
            val next = current.nextSibling
            if (next != null) {
                return next.firstLeaf()
            }
            current = current.parent
        }
        return null
    }

    /**
     * Previous leaf node in document order.
     * Walks left then up until it finds the previous leaf.
     */
    fun prevLeaf(): WNode? {
        var current: WNode? = this
        while (current != null) {
            val prev = current.prevSibling
            if (prev != null) {
                return prev.lastLeaf()
            }
            current = current.parent
        }
        return null
    }

    /**
     * Next leaf that is not whitespace or comment.
     * Useful for finding the next meaningful token.
     */
    fun nextCodeLeaf(): WNode? {
        var leaf = nextLeaf()
        while (leaf != null && leaf.isWhitespaceOrComment) leaf = leaf.nextLeaf()
        return leaf
    }

    /**
     * Previous leaf that is not whitespace or comment.
     */
    fun prevCodeLeaf(): WNode? {
        var leaf = prevLeaf()
        while (leaf != null && leaf.isWhitespaceOrComment) leaf = leaf.prevLeaf()
        return leaf
    }

    /**
     * Next sibling that is not whitespace or comment.
     */
    fun nextCodeSibling(): WNode? {
        var node = nextSibling
        while (node != null && node.isWhitespaceOrComment) node = node.nextSibling
        return node
    }

    /**
     * Previous sibling that is not whitespace or comment.
     */
    fun prevCodeSibling(): WNode? {
        var node = prevSibling
        while (node != null && node.isWhitespaceOrComment) node = node.prevSibling
        return node
    }

    /** True if this node is WHITE_SPACE, EOL_COMMENT, BLOCK_COMMENT, or KDOC. */
    val isWhitespaceOrComment: Boolean get() = type == WNodeType.WHITE_SPACE ||
        type == WNodeType.EOL_COMMENT ||
        type == WNodeType.BLOCK_COMMENT ||
        type == WNodeType.KDOC

    /** True if this is a WHITE_SPACE leaf whose text contains a newline. */
    val isNewline: Boolean get() = type == WNodeType.WHITE_SPACE &&
        leafText != null && leafText.contains('\n')

    private fun siblingAt(offset: Int): WNode? {
        val siblings = parent?.children ?: return null
        val idx = siblings.indexOf(this)
        if (idx < 0) return null
        val targetIdx = idx + offset
        return if (targetIdx in siblings.indices) siblings[targetIdx] else null
    }

    private fun firstLeaf(): WNode =
        if (isLeaf) this else children.first().firstLeaf()

    private fun lastLeaf(): WNode =
        if (isLeaf) this else children.last().lastLeaf()

    override fun toString(): String =
        if (isLeaf) "${type}[${startOffset}..${endOffset}] \"${leafText}\""
        else "${type}[${startOffset}..${endOffset}] (${children.size} children)"
}
