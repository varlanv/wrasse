package sample

fun foo(items: List<Int?>): Int {
    for (item in items) {
        return item ?: continue
    }
    return 0
}

// expect-clean
