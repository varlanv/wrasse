package sample

class Box<T>(val value: T)

fun <T> pick(first: Box<T>, second: String): Box<T> = first
fun <T> pick(left: Box<T>, right: Int): Box<T> = left

fun <T> wrap(value: Box<T>): Box<T> = value
fun <T> wrap(value: Box<T>, fallback: Box<T>): Box<T> = value

fun demo(): Box<Int> {
    val a = pick(first = Box(1), second = "x")
    val b = wrap(value = Box(3), fallback = Box(4))
    return if (a.value == 1) b else a
}