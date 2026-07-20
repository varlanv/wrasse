package sample

class Box<
T,
R,
>(val first: T, val second: R)

class Point(val x: Int, val y: Int)

fun describe(
    nameParameter: String,
    ageParameter: Int,
): String {
    return "$nameParameter is $ageParameter"
}

fun add(a: Int, b: Int): Int = a + b

// expect-clean
