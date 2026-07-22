package sample

fun foo(x: Int) {
    when (x) {
        1 -> println("one")
        else -> println("other")
    }
}

// expect-clean
