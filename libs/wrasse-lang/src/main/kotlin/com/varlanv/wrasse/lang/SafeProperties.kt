package com.varlanv.wrasse.lang

import kotlin.reflect.KType
import kotlin.reflect.typeOf

class SafeProperties(private val map: Map<String, *>) {

    private fun <T> getProp(key: String, propType: PropType<T>): Property<T> {
        val res = map[key]?: return Property.Missing<T>(key)
        val actualType = res::class.java
        if (actualType != propType.javaType) {
            return Property.TypeMismatch(key = key, expectedType = propType.javaType, actualType = actualType)
        }
        when (propType) {
            PropType.LongArrayType -> TODO()
            PropType.LongType -> TODO()
            PropType.NestedArrayType -> TODO()
            PropType.NestedType -> TODO()
            PropType.StringArrayType -> TODO()
            PropType.StringType -> TODO()
        }
    }
}

sealed interface PropType<T> {
    val javaType: Class<T>

    data object LongType : PropType<Long> {
        override val javaType = Long::class.java
    }

    data object StringType : PropType<String> {
        override val javaType = String::class.java
    }

    data object LongArrayType : PropType<Array<Long>> {
        override val javaType = Array<Long>::class.java
    }

    data object StringArrayType : PropType<Array<Long>> {
        override val javaType = Array<Long>::class.java
    }

    data object NestedType : PropType<SafeProperties> {
        override val javaType = SafeProperties::class.java
    }

    data object NestedArrayType : PropType<Array<SafeProperties>> {
        override val javaType = Array<SafeProperties>::class.java
    }
}


sealed interface Property<T> {

    @JvmInline
    value class Val<T>(val value: T) : Property<T>

    @JvmInline
    value class Missing<T>(val key: String):Property<T>

    data class TypeMismatch<T>(val key: String, val expectedType: Class<T>, val actualType: Class<Any>): Property<T>
}
