package com.varlanv.wrasse.model

/**
 * One parameter a rule accepts under its own `wrasse.json` entry, next to `level` and `exclude`.
 * [name] is the JSON key. A [Required] option must be present whenever the rule is on; an
 * [Optional] one falls back to its [Optional.default] when absent — `null` meaning the option is
 * simply absent from [WRuleOptions] in that case.
 */
sealed interface WRuleOptionSpec {
    val name: String
    val type: WRuleOptionType
    val description: String

    class Required(override val name: String, override val type: WRuleOptionType, override val description: String) : WRuleOptionSpec

    class Optional(
        override val name: String,
        override val type: WRuleOptionType,
        override val description: String,
        val default: WRuleOptionValue?,
    ) : WRuleOptionSpec
}

enum class WRuleOptionType(val jsonName: String) {
    BOOLEAN("boolean"),
    INTEGER("integer"),
    STRING("string"),
    STRING_LIST("string array"),
}

sealed interface WRuleOptionValue {
    class Bool(val value: Boolean) : WRuleOptionValue

    class Num(val value: Long) : WRuleOptionValue

    class Str(val value: String) : WRuleOptionValue

    class StrList(val value: List<String>) : WRuleOptionValue
}

/**
 * A rule's effective options, validated against its [WRuleOptionSpec]s by [WConfig]: every
 * required option and every optional one with a default is present, so the non-null accessors
 * are safe for those; the `OrNull` accessors serve optional options without a default.
 */
class WRuleOptions(private val values: Map<String, WRuleOptionValue>) {
    fun boolean(name: String): Boolean = booleanOrNull(name) ?: missing(name)

    fun booleanOrNull(name: String): Boolean? = (values[name] as WRuleOptionValue.Bool?)?.value

    fun integer(name: String): Long = integerOrNull(name) ?: missing(name)

    fun integerOrNull(name: String): Long? = (values[name] as WRuleOptionValue.Num?)?.value

    fun string(name: String): String = stringOrNull(name) ?: missing(name)

    fun stringOrNull(name: String): String? = (values[name] as WRuleOptionValue.Str?)?.value

    fun stringList(name: String): List<String> = stringListOrNull(name) ?: missing(name)

    fun stringListOrNull(name: String): List<String>? = (values[name] as WRuleOptionValue.StrList?)?.value

    private fun missing(name: String): Nothing = throw IllegalStateException("Rule option '$name' is not set")

    companion object {
        val EMPTY = WRuleOptions(emptyMap())
    }
}
