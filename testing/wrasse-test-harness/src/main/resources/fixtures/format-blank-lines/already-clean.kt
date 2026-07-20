package sample

import kotlin.math.PI

class Circle(private val radius: Double) {
    fun area(): Double {
        val doubled = radius * 2

        return PI * doubled * doubled / 4
    }
}

fun classify(x: Int): String = when (x) {
    1 -> "one"

    else -> "other"
}

// expect-clean
