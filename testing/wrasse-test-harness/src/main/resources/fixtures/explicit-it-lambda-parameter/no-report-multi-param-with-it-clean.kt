package sample

fun use() {
    val combine: (Int, Int) -> Int = { it, other -> it + other }
    combine(1, 2)
}

// expect-clean
