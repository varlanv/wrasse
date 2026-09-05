package com.varlanv.wrasse.model

import com.varlanv.wrasse.lang.StringSlice

/**
 * Buffer of direct children collected by the framework for [WBufferedNodeRule]s.
 *
 * Filled automatically between enterNode and exitNode for rules that need subtree access
 * (wrapping rules, argument-list inspection, etc.). Each entry records a direct child's
 * type, offsets, and leaf text (null for interior children). Interior children appear as
 * single entries with their span — use [textSpan] with the source text to read their content.
 *
 * Backed by parallel primitive arrays; no per-child object allocation.
 */
class ChildBuffer {
    private var types = IntArray(16)
    private var startOffsets = IntArray(16)
    private var endOffsets = IntArray(16)
    private var leafTexts: Array<CharSequence?> = arrayOfNulls(16)
    private var _size = 0

    val size: Int get() = _size

    fun type(i: Int): WNodeType = WNodeType.VALUES[types[i]]

    fun startOffset(i: Int): Int = startOffsets[i]

    fun endOffset(i: Int): Int = endOffsets[i]

    fun leafText(i: Int): CharSequence? = leafTexts[i]

    /** Character length of child i (endOffset - startOffset). */
    fun length(i: Int): Int = endOffsets[i] - startOffsets[i]

    /** Child i's full source span, read from [sourceText] (e.g. [WContext.sourceText]). */
    fun textSpan(
        i: Int,
        sourceText: CharSequence,
    ): CharSequence = StringSlice(sourceText, startOffsets[i], endOffsets[i])

    fun hasChildOfType(type: WNodeType): Boolean {
        val ord = type.ordinal
        for (i in 0 until _size) if (types[i] == ord) return true
        return false
    }

    fun firstChildOfType(type: WNodeType): Int {
        val ord = type.ordinal
        for (i in 0 until _size) if (types[i] == ord) return i
        return -1
    }

    fun clear() {
        for (i in 0 until _size) leafTexts[i] = null
        _size = 0
    }

    fun add(
        type: WNodeType,
        start: Int,
        end: Int,
        text: CharSequence?,
    ) {
        if (_size == types.size) {
            grow()
        }
        types[_size] = type.ordinal
        startOffsets[_size] = start
        endOffsets[_size] = end
        leafTexts[_size] = text
        _size++
    }

    private fun grow() {
        val newSize = types.size * 2
        types = types.copyOf(newSize)
        startOffsets = startOffsets.copyOf(newSize)
        endOffsets = endOffsets.copyOf(newSize)
        leafTexts = leafTexts.copyOf(newSize)
    }
}
