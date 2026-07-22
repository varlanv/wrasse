package sample

fun foo() {
    for (i in 1..2) {
        try {
            break
        } finally {
            println("cleanup")
        }
    }
}

// expect-clean
