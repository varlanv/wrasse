package sample

private fun main(args: Array<String>) {
    throw IllegalStateException("boom")
}

// expect-clean
