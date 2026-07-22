package sample

fun foo(x: Int) {
}

@Suppress("magic-number")
fun run() {
    foo(42)
}

// expect-clean
