package sample

fun foo(x: Boolean): Int {
    while (x) {
        return 1
    }
    return 0
}

// expect-error 4:5 unconditional-jump-statement-in-loop "This loop contains an unconditional break or return; the loop body will only ever execute once"
