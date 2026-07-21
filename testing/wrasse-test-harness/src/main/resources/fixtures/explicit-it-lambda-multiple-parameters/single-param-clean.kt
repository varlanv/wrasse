package sample

fun demo(list: List<Int>) {
    list.map { it * 2 }
}

// expect-clean
