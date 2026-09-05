package sample

class Rng {
    fun nextInt(from: Int, until: Int): Int = from + until
}

fun build(r: Rng): List<Int> {
    val values = List(
        size = r.nextInt(
            from = 1,
            until = 50,
        ),
    ) {
        it * 2
    }
    return values
}