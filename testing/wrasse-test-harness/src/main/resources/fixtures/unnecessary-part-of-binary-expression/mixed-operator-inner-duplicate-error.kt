package sample

fun bar() {
    val foo = true
    val baz = false
    if (foo || baz && baz) {
        println("work")
    }
}

// expect-error 6:16 unnecessary-part-of-binary-expression "This binary expression repeats one of its own operands unnecessarily"
