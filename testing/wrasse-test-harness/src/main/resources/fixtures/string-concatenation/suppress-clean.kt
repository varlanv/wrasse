package sample

@Suppress("string-concatenation")
fun foo(x: Int) {
    val s = "value: " + x
}

// expect-clean
