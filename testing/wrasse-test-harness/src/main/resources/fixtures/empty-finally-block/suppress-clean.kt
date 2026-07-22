package sample

@Suppress("empty-finally-block")
fun foo() {
    try {
        println("work")
    } finally {
    }
}

// expect-clean
