package sample

fun demo(list: List<Int>) {
    list.zipWithNext { a, b -> a to b }
}

// expect-clean
