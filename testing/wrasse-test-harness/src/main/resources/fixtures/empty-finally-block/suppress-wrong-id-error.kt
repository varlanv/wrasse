package sample

@Suppress("no-such-rule")
fun foo() {
    try {
        println("work")
    } finally {
    }
}

// expect-error 7:15 empty-finally-block "Empty finally block detected. Empty blocks of code serve no purpose and should be removed"
