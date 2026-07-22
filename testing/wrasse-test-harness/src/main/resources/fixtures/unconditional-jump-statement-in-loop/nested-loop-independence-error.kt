package sample

fun foo() {
    for (i in 1..2) {
        for (j in 1..2) break
    }
}

// expect-error 5:9 unconditional-jump-statement-in-loop "This loop contains an unconditional break or return; the loop body will only ever execute once"
