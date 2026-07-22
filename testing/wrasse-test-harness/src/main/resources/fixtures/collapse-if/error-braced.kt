package sample

fun foo(a: Boolean, b: Boolean) {
    if (a) {
        if (b) {
            println("both")
        }
    }
}

// expect-error 5:9 collapse-if "Nested if-statement could be collapsed into its own enclosing condition"
