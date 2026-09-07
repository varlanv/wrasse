package sample

fun p(a: String, b: String): Pair<String, String> = a to b

fun use(): String {
    val (first, second) = p("x", "y")
    return first + second
}

// expect-clean
