package sample

@Suppress("no-such-rule")
fun foo() {
    try {
    } finally {
        println("cleanup")
    }
}

// expect-error 5:9 empty-try-block "Empty try block detected. Empty blocks of code serve no purpose and should be removed"
