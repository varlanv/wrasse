package sample

fun use() {
    listOf(1).map { it.plus(1) }
    listOf(listOf(1)).flatMap { it }
}

// expect-clean
