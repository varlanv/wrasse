package sample

@Suppress("print-stack-trace")
fun foo() {
    Thread.dumpStack()
}

// expect-clean
