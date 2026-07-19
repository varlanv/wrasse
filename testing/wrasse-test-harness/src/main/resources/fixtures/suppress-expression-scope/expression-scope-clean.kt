package sample

fun foo() {
    val x = @Suppress("no-semicolons") run { println("x"); 1 }
    println(x)
}

// expect-clean
