package sample

class Holder {
    fun make(): IntArray {
        if (true) {
            // wrasse columns:0
            return intArrayOf(1, 2, 3, 4)
        }
        return intArrayOf()
    }
}

// expect-error 6:13 format-columns "size 0 in '// wrasse columns:0' is below the minimum of 1: a row needs at least 1 element"
