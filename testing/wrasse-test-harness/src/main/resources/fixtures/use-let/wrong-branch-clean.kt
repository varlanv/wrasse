package sample

fun transform(x: Int): Int = x

fun check(x: Int?): Int {
    return if (x != null) { transform(x) } else 5
}

// expect-clean
