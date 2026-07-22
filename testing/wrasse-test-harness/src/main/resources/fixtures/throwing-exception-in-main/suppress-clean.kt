package sample

@Suppress("throwing-exception-in-main")
fun main() {
    throw IllegalStateException("boom")
}

// expect-clean
