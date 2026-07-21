package sample

fun check(x: Int?) {
    if (x != null) {
        println(x)
    }
}

// expect-clean
