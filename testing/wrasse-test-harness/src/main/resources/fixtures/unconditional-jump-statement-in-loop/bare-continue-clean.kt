package sample

fun foo() {
    for (i in 1..2) continue
}

// expect-clean
