package sample

@Suppress("empty-try-block")
fun foo() {
    try {
    } finally {
        println("cleanup")
    }
}

// expect-clean
