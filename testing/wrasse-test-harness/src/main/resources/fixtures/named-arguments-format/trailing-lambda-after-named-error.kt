package sample

class Rng {
    fun nextInt(from: Int, until: Int): Int = from + until
}

fun build(r: Rng): List<Int> {
    val values = List(r.nextInt(1, 50)) {
        it * 2
    }
    return values
}

// expect-error 1:1 format "File is not wrasse-formatted"
// expect-error 8:22 named-arguments "Positional arguments should be named"
// expect-error 8:32 named-arguments "Positional arguments should be named"
