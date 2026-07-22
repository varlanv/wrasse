package sample

fun check(s: String?): String? {
    val x = if (s == null) {
        println("foo")
        null
    } else {
        "x"
    }
    return x
}

// expect-clean
