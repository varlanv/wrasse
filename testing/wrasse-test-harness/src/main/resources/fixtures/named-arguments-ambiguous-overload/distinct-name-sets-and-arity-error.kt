package sample

class Box<T>(val value: T)

fun <T> pick(first: Box<T>, second: String): Box<T> = first
fun <T> pick(left: Box<T>, right: Int): Box<T> = left

fun <T> wrap(value: Box<T>): Box<T> = value
fun <T> wrap(value: Box<T>, fallback: Box<T>): Box<T> = value

fun demo(): Box<Int> {
    val a = pick(Box(1), "x")
    val b = wrap(Box(3), Box(4))
    return if (a.value == 1) b else a
}

// expect-error 12:17 named-arguments "Positional arguments should be named"
// expect-error 13:17 named-arguments "Positional arguments should be named"
