package sample

fun use(a: IntArray, b: Int) = b

val n = use(
    // wrasse matrix:2
    intArrayOf(
        1, 2,
        3, 4,
    ),
    1,
)

// fixture-option: trailing-newline
// expect-clean
