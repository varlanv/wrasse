package sample

fun f() {
    for (i in 2..2) {
        for (j in 2..1) {
        }
    }
}

// expect-error 5:19 invalid-range "This loop will never be executed due to its expression"
