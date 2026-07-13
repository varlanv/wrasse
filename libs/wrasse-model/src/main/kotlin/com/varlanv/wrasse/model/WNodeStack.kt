package com.varlanv.wrasse.model

/**
 * Lightweight ancestor stack used by [WContext] to track the current node's parent chain.
 *
 * Stores type ordinals and offsets in parallel primitive IntArrays — no boxing, no object
 * allocation per push. Index 0 is the root (FILE), index size-1 is the immediate parent.
 * Maximum depth for Kotlin files is typically 20-30.
 */
class WNodeStack {
    private var types = IntArray(32)
    private var startOffsets = IntArray(32)
    private var endOffsets = IntArray(32)
    private val counts = IntArray(WNodeType.SIZE)
    private var _size = 0

    val size: Int get() = _size
    val isEmpty: Boolean get() = _size == 0

    fun push(type: WNodeType, startOffset: Int, endOffset: Int) {
        if (_size == types.size) {
            grow()
        }
        val ordinal = type.ordinal
        types[_size] = ordinal
        startOffsets[_size] = startOffset
        endOffsets[_size] = endOffset
        counts[ordinal]++
        _size++
    }

    fun pop() {
        _size--
        counts[types[_size]]--
    }

    fun peekType(): WNodeType = WNodeType.VALUES[types[_size - 1]]
    fun peekStartOffset(): Int = startOffsets[_size - 1]
    fun peekEndOffset(): Int = endOffsets[_size - 1]

    fun contains(type: WNodeType): Boolean = counts[type.ordinal] > 0

    fun typeAt(i: Int): WNodeType = WNodeType.VALUES[types[i]]
    fun startOffsetAt(i: Int): Int = startOffsets[i]
    fun endOffsetAt(i: Int): Int = endOffsets[i]

    fun clear() {
        _size = 0
    }

    private fun grow() {
        val newSize = types.size * 2
        types = types.copyOf(newSize)
        startOffsets = startOffsets.copyOf(newSize)
        endOffsets = endOffsets.copyOf(newSize)
    }
}
