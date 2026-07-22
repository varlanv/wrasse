package sample

fun f() {
    for (i in 2..2) {
    }
    for (i in 2 downTo 2) {
    }
    for (i in 2 until 3) {
    }
    for (i in 2..<3) {
    }
    for (i in 2 until 4 step 2) {
    }
    for (i in (1 + 1)..3) {
    }
}

// expect-clean
