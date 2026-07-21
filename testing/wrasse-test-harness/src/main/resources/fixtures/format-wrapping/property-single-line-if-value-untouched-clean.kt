package sample

fun example(cond: Boolean): Int {
    val x = if (cond) 1 else 2
    return x
}

// expect-clean
