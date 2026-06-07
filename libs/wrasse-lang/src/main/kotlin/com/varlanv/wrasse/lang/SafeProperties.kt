package com.varlanv.wrasse.lang

/**
 * Type-safe access to parsed config values.
 * Parser returns ConfigValue (e.g. ConfigValue.Obj for an object root)
 */
sealed interface ConfigValue {

    fun typeName() : String

    @JvmInline
    value class Str(val value: String) : ConfigValue {
        override fun typeName() ="string"
    }

    @JvmInline
    value class Num(val value: Long) : ConfigValue {
        override fun typeName() = "number"
    }

    @JvmInline
    value class Obj(val value: SafeProperties) : ConfigValue {
        override fun typeName() = "object"
    }
    @JvmInline
    value class StrArr(val value: List<String>) : ConfigValue {
        override fun typeName() = "string[]"
    }
    @JvmInline
    value class NumArr(val value: List<Long>) : ConfigValue {
        override fun typeName() = "number[]"
    }
    @JvmInline
    value class ObjArr(val value: List<SafeProperties>) : ConfigValue {
        override fun typeName() = "object[]"
    }
    @JvmInline
    value class Bool(val value: Boolean) : ConfigValue {
        override fun typeName() = "boolean"
    }
    @JvmInline
    value class BoolArr(val value: List<Boolean>) : ConfigValue {
        override fun typeName() = "boolean[]"
    }
    @JvmInline
    value class Dbl(val value: Double) : ConfigValue {
        override fun typeName() = "number"
    }
    @JvmInline
    value class DblArr(val value: List<Double>) : ConfigValue {
        override fun typeName() = "number[]"
    }
    @JvmInline
    value class NullArr(val value: List<ConfigValue.Null>) : ConfigValue {
        override fun typeName() = "null[]"
    }
    data object Null : ConfigValue {
        override fun typeName() = "null"
    }
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
