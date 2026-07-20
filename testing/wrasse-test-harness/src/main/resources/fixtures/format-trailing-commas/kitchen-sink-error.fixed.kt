package sample

class Box<
T,
R,
>(val first: T, val second: R)

class Point(val x: Int, val y: Int)

fun f(a: String, b: String) {}

fun classify(x: Int): String {
    return when (x) {
        1,
        2, -> "small"
        else -> "large"
    }
}

fun demo() {
    f(
        "first argument here",
        "second argument here",
    )
}