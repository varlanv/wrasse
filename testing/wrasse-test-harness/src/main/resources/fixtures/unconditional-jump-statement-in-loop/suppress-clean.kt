package sample

@Suppress("unconditional-jump-statement-in-loop")
fun foo() {
    for (i in 1..2) break
}

// expect-clean
