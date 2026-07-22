package sample

fun foo(a: Boolean, b: Boolean) {
    if (a) {
        /**
         * Some comments
         */
        if (b) {
            println("both")
        }
    }
}

// expect-clean
