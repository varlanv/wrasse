package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WNodeType

/**
 * A `BLOCK`'s own direct children are nothing but its braces and whitespace: no statement,
 * no comment, no KDoc. Any comment or KDoc inside makes the block non-empty, shared by every
 * empty-block rule so a `// no-op` explanation is never itself flagged as removable.
 */
object EmptyBlockCheck {
    fun isEmpty(children: ChildBuffer): Boolean {
        for (i in 0 until children.size) {
            when (children.type(i)) {
                WNodeType.LBRACE, WNodeType.RBRACE, WNodeType.WHITE_SPACE -> {}
                else -> return false
            }
        }
        return true
    }

    /** Same verdict as [isEmpty], read directly from a `BLOCK`'s own span when only one level of buffering is available. */
    fun isEmptySpan(
        sourceText: CharSequence,
        blockStart: Int,
        blockEnd: Int,
    ): Boolean =
        blockEnd - blockStart >= 2 && sourceText.subSequence(blockStart + 1, blockEnd - 1).isBlank()
}
