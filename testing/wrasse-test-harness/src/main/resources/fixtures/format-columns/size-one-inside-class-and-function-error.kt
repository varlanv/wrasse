package sample

class Holder {
    // wrasse columns:1
    val a = intArrayOf(1, 2)

    fun make(): IntArray {
        // wrasse columns:1
        val b = intArrayOf(10, 2, 30)
        return b
    }
}

// expect-error 1:1 format "File is not wrasse-formatted"
