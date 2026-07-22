package sample

fun foo() {
    try {
    } finally {
        println("cleanup")
    }
}

// expect-error 4:9 empty-try-block "Empty try block detected. Empty blocks of code serve no purpose and should be removed"
