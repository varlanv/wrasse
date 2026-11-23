package sample

class Holder {
    fun make(): IntArray {
        if (true) {
            // wrasse matrix:3
            return intArrayOf(1, 2, 3, 4)
        }
        return intArrayOf()
    }
}

// expect-error 6:13 format-matrix "'// wrasse matrix:3' cannot split the 4 elements of the call below into rows of 3: 4 is not divisible by 3"
