package sample

fun foo(items: List<Int>) {
    for (item in items) {
        if (item == 1) {
            break
        }
        while (item < 3) {
            if (item > 1) {
                continue
            }
        }
    }
}

// expect-clean
