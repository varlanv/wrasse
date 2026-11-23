package sample

class Holder {
    // wrasse columns:2
    val a = intArrayOf(
        1, 2,
        3, 4,
        5,
    )

    fun make(): IntArray {
        // wrasse columns:3
        val b = intArrayOf(
            1,  20, 3,
            40, 5,  60,
            7,
        )
        return b
    }
}