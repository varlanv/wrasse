package sample

fun foo() {
    try {
        println("work")
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// expect-error 7:9 print-stack-trace "Do not print a stack trace. These debug statements should be removed or replaced with a logger."
