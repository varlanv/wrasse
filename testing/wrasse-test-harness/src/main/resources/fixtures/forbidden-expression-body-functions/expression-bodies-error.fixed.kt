package sample

class Box(val value: Int) {
    fun single(): Int {
        return value + 1
    }

    fun unit(): Unit {
        println(value)
    }

    fun fail(): Nothing {
        throw Throwable("boom")
    }

    fun inferred() = value * 2

    fun multi(): Int =
        value +
            3

    fun block(): Int {
        return value
    }
}

fun String.tail() = drop(1)

fun String.head(): Char {
    return first()
}