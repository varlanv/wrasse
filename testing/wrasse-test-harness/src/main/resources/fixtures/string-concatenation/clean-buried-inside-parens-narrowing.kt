package sample

fun foo(x: String, y: String) {
    val s = x + ("prefix " + y)
}

// expect-clean
