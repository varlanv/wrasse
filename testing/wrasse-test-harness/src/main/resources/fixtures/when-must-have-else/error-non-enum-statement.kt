package sample

fun foo(x: Int) {
    when (x) {
        1 -> println("one")
        2 -> println("two")
    }
}

// expect-error 4:5 when-must-have-else "'when' used as a statement should have an 'else' branch"
