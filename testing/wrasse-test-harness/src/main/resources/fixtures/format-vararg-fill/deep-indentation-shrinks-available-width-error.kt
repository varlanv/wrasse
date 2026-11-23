package sample

class Outer {
    class Inner {
        fun table(): IntArray {
            if (true) {
                return intArrayOf(100, 200, 300, 400, 500, 600, 700, 800, 900)
            }
            return intArrayOf()
        }
    }
}

// expect-error 1:1 format "File is not wrasse-formatted"
