package sample

fun foo() {
    try {
        println("work")
    } finally {
        // no-op
    }
}

// expect-clean
