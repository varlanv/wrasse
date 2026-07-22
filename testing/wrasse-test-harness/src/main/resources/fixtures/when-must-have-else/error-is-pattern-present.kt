package sample

fun foo(x: Any) {
    when (x) {
        is String -> println("string")
        is Int -> println("int")
    }
}

// expect-error 4:5 when-must-have-else "'when' used as a statement should have an 'else' branch"
