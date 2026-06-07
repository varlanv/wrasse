package com.varlanv.wrasse.sample.util

fun add(a: Int, b: Int): Int {
    return a + b;
}

fun multiply(a: Int, b: Int): Int {
    return a * b
}

val x = 1; val y = 2

enum class Color {
    RED,
    GREEN;

    fun label(): String = name.lowercase()
}

fun loopExample() {
    for (i in 0..10) {
        if (i > 5) break
    }
}
