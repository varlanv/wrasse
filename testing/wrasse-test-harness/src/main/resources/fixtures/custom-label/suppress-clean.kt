package sample

@Suppress("custom-label")
fun foo(items: List<Int>) {
    qq@ for (item in items) {
        break@qq
    }
}

// expect-clean
