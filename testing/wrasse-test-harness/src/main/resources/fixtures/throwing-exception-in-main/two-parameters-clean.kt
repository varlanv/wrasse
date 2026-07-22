package sample

fun main(a: String, b: String) {
    throw IllegalStateException("boom")
}

// expect-clean
