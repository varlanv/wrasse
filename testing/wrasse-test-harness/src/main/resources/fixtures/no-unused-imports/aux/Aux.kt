package sample.aux

class Outer {
    class Nested

    companion object {
        val value: Int = 1
    }
}

enum class Color {
    RED,
    GREEN,
}

fun auxTopLevelFun(): Int = 1

fun `weird fun`(): Int = 1
