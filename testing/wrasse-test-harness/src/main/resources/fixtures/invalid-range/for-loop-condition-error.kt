package sample

fun f() {
    for (i in 2..1) {
    }
}

// expect-error 4:15 invalid-range "This loop will never be executed due to its expression"
