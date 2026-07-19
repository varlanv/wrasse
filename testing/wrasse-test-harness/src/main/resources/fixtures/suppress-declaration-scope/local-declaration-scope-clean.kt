package sample

fun foo() {
    @Suppress("no-semicolons")
    val greet: () -> Unit = { println("hi"); }
    greet()
}

// expect-clean
