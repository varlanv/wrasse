package sample

@Suppress("too-generic-exception-thrown")
fun foo() {
    throw RuntimeException()
}

// expect-clean
