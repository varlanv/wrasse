package sample

fun loops() {
    val items = mutableListOf<Int>()
    for (i in 1..10) {
        items.add(i)
    }
}

// expect-clean
