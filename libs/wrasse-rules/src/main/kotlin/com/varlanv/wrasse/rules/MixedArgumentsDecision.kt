package com.varlanv.wrasse.rules

/**
 * Pure verdict logic for `no-mixed-named-positional-arguments`: [isNamedArgument] recognizes an
 * argument written as `name = value` (an identifier, plain or backticked, then `=` that is not
 * `==`); [mixesNamedAndPositional] is true when a list has at least one argument of each kind.
 */
object MixedArgumentsDecision {
    fun isNamedArgument(sourceText: CharSequence, start: Int, end: Int): Boolean {
        var i = start
        while (i < end && sourceText[i].isWhitespace()) i++
        if (i >= end) return false
        if (sourceText[i] == '`') {
            i++
            while (i < end && sourceText[i] != '`') i++
            if (i >= end) return false
            i++
        } else {
            if (!sourceText[i].isJavaIdentifierStart()) return false
            while (i < end && sourceText[i].isJavaIdentifierPart()) i++
        }
        while (i < end && sourceText[i].isWhitespace()) i++
        if (i >= end || sourceText[i] != '=') return false
        return i + 1 >= end || sourceText[i + 1] != '='
    }

    fun mixesNamedAndPositional(named: Int, positional: Int): Boolean = named > 0 && positional > 0
}
