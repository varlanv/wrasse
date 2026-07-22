package sample

fun foo(x: Boolean): Int {
    do {
        return 1
    } while (x)
    return 0
}

// expect-error 4:5 unconditional-jump-statement-in-loop "This loop contains an unconditional break or return; the loop body will only ever execute once"
