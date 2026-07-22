package sample

fun foo() {
    throw Error()
}

fun bar() {
    throw Throwable()
}

// expect-error 4:5 too-generic-exception-thrown "Error is a too generic Exception. Prefer throwing specific exceptions that indicate a specific error case."
// expect-error 8:5 too-generic-exception-thrown "Throwable is a too generic Exception. Prefer throwing specific exceptions that indicate a specific error case."
