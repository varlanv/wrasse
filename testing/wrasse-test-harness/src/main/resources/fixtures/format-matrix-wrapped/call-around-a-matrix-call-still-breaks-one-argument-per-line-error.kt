package sample

fun use(a: IntArray, b: IntArray) = 1

val n = use(
    // wrasse matrix:2
    intArrayOf(1, 2, 3, 4),
    intArrayOf(5, 6, 7),
)

// expect-error 1:1 format "File is not wrasse-formatted"
