package sample

fun foo(a: Boolean, b: Boolean) {
    if (a) {
        // leading comment
        if (b) {
            println("both")
        }
        // trailing comment
    }
}

// expect-error 6:9 collapse-if "Nested if-statement could be collapsed into its own enclosing condition"
