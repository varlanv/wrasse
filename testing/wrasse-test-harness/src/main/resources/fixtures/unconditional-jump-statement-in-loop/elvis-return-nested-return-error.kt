package sample

fun foo(): Int {
    fun compute(i: Int): Int? = null
    for (i in 1..5)
        return compute(i) ?: return 0
    return 0
}

// expect-error 5:5 unconditional-jump-statement-in-loop "This loop contains an unconditional break or return; the loop body will only ever execute once"
