package sample

fun foo(): Int {
    fun compute(i: Int): Int? = null
    for (i in 1..5) {
        compute(i) ?: return 0
    }
    return 0
}

// expect-clean
