package sample

class Factory {
    fun make(vararg xs: Int) = xs
}

val f = Factory()

// wrasse matrix:2
val t = f.make(1, 2, 3, 4)

// expect-error 1:1 format "File is not wrasse-formatted"
