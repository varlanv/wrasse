package io.kotest.matchers.types

inline fun <reified T : Any> Any?.shouldBeInstanceOf(): T = this as T
