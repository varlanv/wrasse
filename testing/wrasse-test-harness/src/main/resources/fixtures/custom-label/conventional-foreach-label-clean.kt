package sample

fun foo(items: List<Int>) {
    items.forEach {
        return@forEach
    }
}

// expect-clean
