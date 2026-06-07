package com.varlanv.wrasse.lang

/**
 * Type-safe access to parsed config values.
 * Parser returns ConfigValue (e.g. ConfigValue.Obj for an object root)
 */
sealed interface ConfigValue {
    @JvmInline
    value class Str(val value: String) : ConfigValue
    @JvmInline
    value class Num(val value: Long) : ConfigValue
    @JvmInline
    value class Obj(val value: SafeProperties) : ConfigValue
    @JvmInline
    value class StrArr(val value: List<String>) : ConfigValue
    @JvmInline
    value class NumArr(val value: List<Long>) : ConfigValue
    @JvmInline
    value class ObjArr(val value: List<SafeProperties>) : ConfigValue
    @JvmInline
    value class Bool(val value: Boolean) : ConfigValue
    @JvmInline
    value class Dbl(val value: Double) : ConfigValue
    @JvmInline
    value class Arr(val value: List<ConfigValue>) : ConfigValue
    data object Null : ConfigValue
}

/**
 * Navigates object nodes.
 */
class SafeProperties(@PublishedApi internal val map: Map<String, ConfigValue>) {
    inline fun <reified V : ConfigValue> get(key: String): Property<V> {
        val value = map[key] ?: return Property.Missing(key)
        if (value !is V) return Property.TypeMismatch(key, actual = value)
        return Property.Val(value)
    }
}

/**
 * Property access result
 */
sealed interface Property<out T> {
    @JvmInline
    value class Val<T>(val value: T) : Property<T>
    @JvmInline
    value class Missing(val key: String) : Property<Nothing>
    data class TypeMismatch(val key: String, val actual: ConfigValue) : Property<Nothing>
}
