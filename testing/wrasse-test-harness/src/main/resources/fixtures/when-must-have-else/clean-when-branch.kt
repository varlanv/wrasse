package sample

fun foo(x: Int, flag: Boolean) {
    when (x) {
        1 -> when (flag) {
            true -> println("yes")
            false -> println("no")
        }
        else -> println("other")
    }
}

// expect-clean
