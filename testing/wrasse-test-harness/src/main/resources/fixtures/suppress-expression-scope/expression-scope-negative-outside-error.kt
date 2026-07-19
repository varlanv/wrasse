package sample

fun foo() {
    val x = @Suppress("no-semicolons") run { 1 }
    val y = 2;
    println(x + y)
}

// expect-error 5:14 no-semicolons "Unnecessary semicolon"
