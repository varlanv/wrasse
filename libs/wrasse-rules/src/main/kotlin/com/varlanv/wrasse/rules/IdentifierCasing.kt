package com.varlanv.wrasse.rules

/**
 * Casing predicates shared by every naming-family decision object, plus backtick handling.
 * Unicode-aware via [Char.isUpperCase]/[Char.isLowerCase] rather than an ASCII regex, so
 * accented/non-Latin letters classify the same way the Kotlin lexer accepts them.
 */
object IdentifierCasing {
    private val KEYWORDS = setOf(
        "as",
        "break",
        "class",
        "continue",
        "do",
        "else",
        "false",
        "for",
        "fun",
        "if",
        "in",
        "interface",
        "is",
        "null",
        "object",
        "package",
        "return",
        "super",
        "this",
        "throw",
        "true",
        "try",
        "typealias",
        "typeof",
        "val",
        "var",
        "when",
        "while",
        "by",
        "catch",
        "constructor",
        "delegate",
        "dynamic",
        "field",
        "file",
        "finally",
        "get",
        "import",
        "init",
        "param",
        "property",
        "receiver",
        "set",
        "setparam",
        "where",
        "actual",
        "abstract",
        "annotation",
        "companion",
        "const",
        "crossinline",
        "data",
        "enum",
        "expect",
        "external",
        "final",
        "infix",
        "inline",
        "inner",
        "internal",
        "lateinit",
        "noinline",
        "open",
        "operator",
        "out",
        "override",
        "private",
        "protected",
        "public",
        "reified",
        "sealed",
        "suspend",
        "tailrec",
        "vararg",
    )

    fun unquote(text: CharSequence): String {
        val s = text.toString()
        return if (s.length >= 2 && s.first() == '`' && s.last() == '`') s.substring(1, s.length - 1) else s
    }

    fun isBacktickWrapped(text: CharSequence): Boolean = text.length >= 2 && text.first() == '`' && text.last() == '`'

    fun isBacktickKeyword(text: CharSequence): Boolean = isBacktickWrapped(text) && unquote(text) in KEYWORDS

    fun isPascalCase(text: String): Boolean {
        if (text.isEmpty() || !text[0].isUpperCase()) return false
        return text.drop(1).all { it.isLetterOrDigit() }
    }

    fun isLowerCamelCase(text: String): Boolean {
        if (text.isEmpty() || !text[0].isLowerCase()) return false
        return text.drop(1).all { it.isLetterOrDigit() }
    }

    fun isScreamingSnakeCase(text: String): Boolean {
        if (text.isEmpty() || !text[0].isUpperCase()) return false
        return text.drop(1).all { it.isUpperCase() || it.isDigit() || it == '_' }
    }

    fun isLowerDottedSegment(text: String): Boolean {
        if (text.isEmpty() || !text[0].isLowerCase()) return false
        return text.drop(1).all { it.isLetterOrDigit() }
    }
}
