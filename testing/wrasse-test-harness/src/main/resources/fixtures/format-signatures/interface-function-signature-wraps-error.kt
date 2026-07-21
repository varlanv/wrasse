package sample

interface Calculator {
    fun sum(a: Int, b: Int, c: Int): Int
}

// expect-error 1:1 format "File is not wrasse-formatted"
