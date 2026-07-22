package sample

fun bar() {
    val foo = 0
    val bar = 1
    if (foo > bar || foo > 1) {
        println("work")
    }
}

// expect-clean
