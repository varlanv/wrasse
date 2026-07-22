package sample

fun foo(items: List<Int>) {
    loop@ for (item in items) {
        break@loop
    }
}

// expect-clean
