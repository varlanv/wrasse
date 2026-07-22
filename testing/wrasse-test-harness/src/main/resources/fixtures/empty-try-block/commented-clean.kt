package sample

fun foo() {
    try {
        // no-op
    } finally {
        println("cleanup")
    }
}

// expect-clean
