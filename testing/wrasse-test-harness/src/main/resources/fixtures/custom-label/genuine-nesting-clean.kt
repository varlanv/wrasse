package sample

fun foo(items: List<Int>) {
    qq@ for (item in items) {
        for (sub in items) {
            break@qq
        }
    }
}

// expect-clean
