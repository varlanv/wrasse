package sample

fun foo() {
    try {
        println("work")
    } finally {
    }
}

// expect-error 6:15 empty-finally-block "Empty finally block detected. Empty blocks of code serve no purpose and should be removed"
