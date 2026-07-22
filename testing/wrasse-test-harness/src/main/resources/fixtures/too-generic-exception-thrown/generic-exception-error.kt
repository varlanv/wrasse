package sample

fun foo(bar: Int) {
    if (bar < 1) {
        throw Exception()
    }
}

// expect-error 5:9 too-generic-exception-thrown "Exception is a too generic Exception. Prefer throwing specific exceptions that indicate a specific error case."
