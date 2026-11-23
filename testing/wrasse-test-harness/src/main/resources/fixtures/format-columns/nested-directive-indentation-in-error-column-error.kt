package sample

class Holder {
    fun make(): IntArray {
        if (true) {
            // wrasse columns:1
            return intArrayOf(1, 2, 3, 4)
        }
        return intArrayOf()
    }
}

// expect-error 6:13 format-columns "size 1 in '// wrasse columns:1' is below the minimum of 2: a row needs at least 2 elements"
