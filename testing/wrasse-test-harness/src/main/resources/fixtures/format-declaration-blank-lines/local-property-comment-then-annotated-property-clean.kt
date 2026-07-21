package sample

annotation class Ann1

fun foo() {
    val a = 1

    // hello
    @Ann1
    val b = 2
}

// expect-clean
