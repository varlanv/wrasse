package sample

fun foo() {
    try {
        println("work")
    } finally {
        println("cleanup")
    }
}

// expect-clean
