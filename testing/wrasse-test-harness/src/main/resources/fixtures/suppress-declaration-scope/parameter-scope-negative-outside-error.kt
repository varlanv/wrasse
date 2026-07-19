package sample

fun foo(@Suppress("no-semicolons") cb: () -> Unit = {}) {
    val x = 1;
    cb()
}

// expect-error 4:14 no-semicolons "Unnecessary semicolon"
