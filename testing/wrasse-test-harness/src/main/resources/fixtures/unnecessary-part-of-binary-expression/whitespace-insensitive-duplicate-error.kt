package sample

fun bar() {
    val foo = 1
    if (foo> 1 && foo >1) {
        println("work")
    }
}

// expect-error 5:9 unnecessary-part-of-binary-expression "This binary expression repeats one of its own operands unnecessarily"
