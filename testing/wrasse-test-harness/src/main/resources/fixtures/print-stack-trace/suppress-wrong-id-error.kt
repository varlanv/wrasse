package sample

@Suppress("no-semicolons")
fun foo() {
    Thread.dumpStack()
}

// expect-error 5:5 print-stack-trace "Do not print a stack trace. These debug statements should be removed or replaced with a logger."
