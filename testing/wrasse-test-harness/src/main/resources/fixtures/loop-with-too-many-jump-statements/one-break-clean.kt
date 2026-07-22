package sample

fun foo(items: List<Int>) {
    for (item in items) {
        if (item == 1) {
            break
        }
        println(item)
    }
}

// expect-clean
