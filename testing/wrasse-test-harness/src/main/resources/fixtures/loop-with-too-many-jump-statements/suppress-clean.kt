package sample

@Suppress("loop-with-too-many-jump-statements")
fun foo(items: List<Int>) {
    for (item in items) {
        if (item == 1) {
            break
        }
        if (item == 2) {
            continue
        }
    }
}

// expect-clean
