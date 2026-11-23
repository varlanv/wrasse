package com.varlanv.wrasse.rules

/**
 * The offset of the start of the line [offset] sits on when only spaces and tabs precede it there,
 * `null` when anything else does.
 */
internal fun lineStartIfOnlyIndentBefore(source: CharSequence, offset: Int): Int? {
    var index = offset
    while (index > 0 && (source[index - 1] == ' ' || source[index - 1] == '\t')) index--
    return if (index == 0 || source[index - 1] == '\n') index else null
}
