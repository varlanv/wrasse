package sample

fun foo() {
    Thread.dumpStack()
}

// expect-error 4:5 print-stack-trace "Do not print a stack trace. These debug statements should be removed or replaced with a logger."
