package sample

fun use() {
    listOf(1).map { value -> value.plus(1) }
}

// expect-clean
