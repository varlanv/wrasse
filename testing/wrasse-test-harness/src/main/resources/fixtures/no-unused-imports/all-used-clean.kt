package sample

import kotlin.text.Regex
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.collections.plus
import kotlin.math.cbrt

fun sample(pattern: Regex): Int {
    val a = abs(-1)
    val b = (-2).absoluteValue
    val list = listOf(1) + listOf(2)
    val ref: (Double) -> Double = ::cbrt
    return a + b + list.size + ref(0.0).toInt() + pattern.toString().length
}

// expect-clean
