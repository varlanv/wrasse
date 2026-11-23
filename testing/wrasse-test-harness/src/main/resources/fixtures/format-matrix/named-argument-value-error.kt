package sample

fun use(t: IntArray, o: Int) = o

val n = use(
    // wrasse matrix:2
    t = intArrayOf(1, 2, 3, 4),
    o = 1,
)

// expect-error 1:1 format "File is not wrasse-formatted"
