package sample

fun bar() {
    val foo = true
    when {
        foo || foo -> {
            println("work")
        }
    }
}

// expect-error 6:9 unnecessary-part-of-binary-expression "This binary expression repeats one of its own operands unnecessarily"
