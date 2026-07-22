package sample

@Suppress("no-semicolons")
fun foo() {
    throw RuntimeException()
}

// expect-error 5:5 too-generic-exception-thrown "RuntimeException is a too generic Exception. Prefer throwing specific exceptions that indicate a specific error case."
