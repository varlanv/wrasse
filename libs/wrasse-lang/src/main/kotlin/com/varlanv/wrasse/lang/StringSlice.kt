package com.varlanv.wrasse.lang

/**
 * A zero-copy window `[start, end)` over [source], usually a whole file's text. [subSequence]
 * returns another window over the same [source]; [toString] is the only operation that copies.
 * Equality and hashing are content-based against any [CharSequence] when the slice is the
 * receiver — `slice == "x"` holds, `"x" == slice` does not, so compare with the slice on the left
 * or use `contentEquals`.
 */
class StringSlice(
    val source: CharSequence,
    val start: Int,
    val end: Int,
) : CharSequence {
    init {
        require(start in 0..end && end <= source.length) { "Slice $start..$end outside 0..${source.length}" }
    }

    override val length: Int get() = end - start

    override fun get(index: Int): Char {
        if (index < 0 || index >= length) throw IndexOutOfBoundsException("Index $index, length $length")
        return source[start + index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): StringSlice {
        if (startIndex < 0 || endIndex > length || startIndex > endIndex) {
            throw IndexOutOfBoundsException("Range $startIndex..$endIndex, length $length")
        }
        return StringSlice(source, start + startIndex, start + endIndex)
    }

    fun indexOf(ch: Char): Int {
        val found = source.indexOfChar(ch, start)
        return if (found < 0 || found >= end) -1 else found - start
    }

    fun lastIndexOf(ch: Char): Int {
        var i = end - 1
        while (i >= start) {
            if (source[i] == ch) return i - start
            i--
        }
        return -1
    }

    override fun toString(): String = source.subSequence(start, end).toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CharSequence || other.length != length) return false
        for (i in 0 until length) if (source[start + i] != other[i]) return false
        return true
    }

    override fun hashCode(): Int {
        var h = 0
        for (i in start until end) h = 31 * h + source[i].code
        return h
    }
}

/** Index of [ch] at or after [from], without the char-array allocation the generic `indexOf` does for non-`String` receivers. */
fun CharSequence.indexOfChar(ch: Char, from: Int = 0): Int =
    when (this) {
        is String -> indexOf(ch, from)
        is StringSlice -> {
            val found = source.indexOfChar(ch, start + from)
            if (found < 0 || found >= end) -1 else found - start
        }

        else -> {
            var i = from
            while (i < length) {
                if (this[i] == ch) return i
                i++
            }
            -1
        }
    }

fun CharSequence.lastIndexOfChar(ch: Char): Int =
    when (this) {
        is String -> lastIndexOf(ch)
        is StringSlice -> lastIndexOf(ch)
        else -> {
            var i = length - 1
            while (i >= 0) {
                if (this[i] == ch) return i
                i--
            }
            -1
        }
    }

fun CharSequence.containsChar(ch: Char): Boolean = indexOfChar(ch) >= 0
