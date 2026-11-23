package sample

fun use(a: IntArray, b: Int) {}

fun main() {
    use(
        // wrasse columns:2
        intArrayOf(1, 2, 3, 4),
        5,
    )
}

// expect-error 1:1 format "File is not wrasse-formatted"
