package sample

class Holder {
    // wrasse matrix:2
    val a = intArrayOf(1, 2, 3, 4)

    fun make(): IntArray {
        // wrasse matrix:3
        val b = intArrayOf(1, 20, 3, 40, 5, 60)
        return b
    }
}

// expect-error 1:1 format "File is not wrasse-formatted"
