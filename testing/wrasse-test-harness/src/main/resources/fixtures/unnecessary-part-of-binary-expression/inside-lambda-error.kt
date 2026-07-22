package sample

fun bar() {
    val list = listOf<Int>()

    list.filter { it > 1 || it > 1 }
}

// expect-error 6:19 unnecessary-part-of-binary-expression "This binary expression repeats one of its own operands unnecessarily"
