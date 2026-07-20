package sample

fun classify(x: Int) {
    when (x) {
        1 -> {
            println("one")
            println("more")
        }
        2 ->
            println("two")
        else -> println("other")
    }
}

// expect-error 10:13 when-entry-bracing "Missing braces on when-entry body"
// expect-error 11:17 when-entry-bracing "Missing braces on when-entry body"