package sample

fun foo(@Suppress("no-semicolons") cb: () -> Unit = { println("x"); }) {
    cb()
}

// expect-clean
