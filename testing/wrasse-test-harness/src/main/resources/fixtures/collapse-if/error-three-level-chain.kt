package sample

fun foo(a: Boolean, b: Boolean, c: Boolean) {
    if (a) {
        if (b) {
            if (c) {
                println("all three")
            }
        }
    }
}

// expect-error 5:9 collapse-if "Nested if-statement could be collapsed into its own enclosing condition"
// expect-error 6:13 collapse-if "Nested if-statement could be collapsed into its own enclosing condition"
