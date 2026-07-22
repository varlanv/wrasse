package sample

@Suppress("no-such-rule")
fun foo(a: Boolean, b: Boolean) {
    if (a) {
        if (b) {
            println("both")
        }
    }
}

// expect-error 6:9 collapse-if "Nested if-statement could be collapsed into its own enclosing condition"
