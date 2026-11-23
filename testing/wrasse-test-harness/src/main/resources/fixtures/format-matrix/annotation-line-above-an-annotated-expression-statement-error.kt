package sample

fun make() {
    // wrasse matrix:2
    @Suppress("UNUSED_EXPRESSION")
    intArrayOf(1, 22, 3, 4)
}

// expect-error 1:1 format "File is not wrasse-formatted"
